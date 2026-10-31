package com.codehealth.agent.validation;

import com.codehealth.agent.config.CodeHealthProperties;
import com.codehealth.agent.domain.Enums.ValidationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Validator step.
 * <ul>
 *   <li>static: the patch must parse and apply cleanly to the current source. Tests are not run, so the
 *       result is NOT_EXECUTED and the risk policy can never auto-apply it.</li>
 *   <li>local: copy the repository to a temp directory, apply the patch, and run the affected test N times.
 *       Only N consecutive green runs give PASSED. The tests may contain model-written code, so they run in a
 *       throwaway Docker container by default, or on the host with the agent's secrets stripped from the
 *       environment when {@code codehealth.validation.isolation=env}.</li>
 * </ul>
 */
@Component
public class PatchValidator {

    private static final Logger log = LoggerFactory.getLogger(PatchValidator.class);
    private static final int MAX_LOG_CHARS = 20_000;
    private static final ExecutorService OUTPUT_READERS = Executors.newVirtualThreadPerTaskExecutor();

    private final CodeHealthProperties properties;
    private final SourceRepository source;

    public PatchValidator(CodeHealthProperties properties, SourceRepository source) {
        this.properties = properties;
        this.source = source;
    }

    public record Result(ValidationStatus status, boolean applies, int greenRuns, String log,
                         List<UnifiedDiff.FilePatch> patches) {
    }

    public Result validate(String patchDiff, String testClassName) {
        List<UnifiedDiff.FilePatch> patches;
        try {
            patches = UnifiedDiff.parse(patchDiff);
        } catch (UnifiedDiff.PatchException ex) {
            return new Result(ValidationStatus.FAILED, false, 0, "Patch rejected: " + ex.getMessage(), List.of());
        }
        if (!source.isConfigured()) {
            return new Result(ValidationStatus.NOT_EXECUTED, false, 0,
                    "Patch parsed. No repository configured (codehealth.validation.repo-path), so it was not applied.",
                    patches);
        }

        StringBuilder report = new StringBuilder();
        try {
            for (UnifiedDiff.FilePatch patch : patches) {
                String original = source.read(patch.path())
                        .orElseThrow(() -> new UnifiedDiff.PatchException("File not found: " + patch.path()));
                UnifiedDiff.apply(original, patch);
                report.append("Applies cleanly: ").append(patch.path()).append('\n');
            }
        } catch (UnifiedDiff.PatchException ex) {
            return new Result(ValidationStatus.FAILED, false, 0, report + "Patch does not apply: " + ex.getMessage(),
                    patches);
        }

        if (!properties.validation().isLocal()) {
            report.append("Static mode: tests not executed.");
            return new Result(ValidationStatus.NOT_EXECUTED, true, 0, report.toString(), patches);
        }
        return runTests(patches, testClassName, report);
    }

    private Result runTests(List<UnifiedDiff.FilePatch> patches, String testClassName, StringBuilder report) {
        int required = properties.policy().requiredGreenRuns();
        Path workDir = null;
        try {
            workDir = Files.createTempDirectory("codehealth-validate-");
            copyTree(source.root(), workDir);
            for (UnifiedDiff.FilePatch patch : patches) {
                Path file = workDir.resolve(patch.path()).normalize();
                if (!file.startsWith(workDir)) {
                    throw new UnifiedDiff.PatchException("Unsafe path " + patch.path());
                }
                Files.writeString(file, UnifiedDiff.apply(Files.readString(file, StandardCharsets.UTF_8), patch),
                        StandardCharsets.UTF_8);
            }

            int green = 0;
            for (int run = 1; run <= required; run++) {
                ProcessResult result = execute(workDir, testClassName);
                report.append("Run ").append(run).append('/').append(required).append(": exit ")
                        .append(result.exitCode()).append('\n');
                if (result.exitCode() != 0) {
                    report.append(tail(result.output()));
                    return new Result(ValidationStatus.FAILED, true, green, truncate(report), patches);
                }
                green++;
            }
            return new Result(ValidationStatus.PASSED, true, green, truncate(report), patches);
        } catch (Exception ex) {
            log.warn("Local validation error: {}", ex.getMessage());
            report.append("Validation error: ").append(ex.getMessage());
            return new Result(ValidationStatus.FAILED, true, 0, truncate(report), patches);
        } finally {
            deleteQuietly(workDir);
        }
    }

    private record ProcessResult(int exitCode, String output) {
    }

    /**
     * Environment variables a build needs. Everything else (GITHUB_TOKEN, AI_API_KEY, DB_PASSWORD, ...) is
     * dropped, because the tests being run were written by a model and must not see the agent's secrets.
     */
    static final Set<String> SAFE_ENV = Set.of(
            "PATH", "PATHEXT", "HOME", "USER", "USERNAME", "USERPROFILE", "LANG", "LC_ALL", "TZ",
            "TEMP", "TMP", "TMPDIR", "JAVA_HOME", "JAVA_TOOL_OPTIONS", "MAVEN_HOME", "M2_HOME", "MAVEN_OPTS",
            "SYSTEMROOT", "SYSTEMDRIVE", "WINDIR", "COMSPEC", "APPDATA", "LOCALAPPDATA", "PROGRAMDATA",
            "PROGRAMFILES", "PROGRAMFILES(X86)", "PROCESSOR_ARCHITECTURE", "NUMBER_OF_PROCESSORS",
            "HTTP_PROXY", "HTTPS_PROXY", "NO_PROXY", "DOCKER_HOST", "DOCKER_CONFIG", "DOCKER_CERT_PATH",
            "DOCKER_TLS_VERIFY", "DOCKER_CONTEXT");

    static Map<String, String> safeEnvironment(Map<String, String> env) {
        Map<String, String> safe = new HashMap<>();
        env.forEach((name, value) -> {
            if (SAFE_ENV.contains(name.toUpperCase(Locale.ROOT))) {
                safe.put(name, value);
            }
        });
        return safe;
    }

    private String testCommand(String testClassName) {
        String testFilter = testClassName == null ? "" : " -Dtest=" + testClassName.replaceAll("[^A-Za-z0-9_.#]", "");
        return properties.validation().testCommand() + testFilter;
    }

    private ProcessResult execute(Path workDir, String testClassName) throws IOException, InterruptedException {
        String command = testCommand(testClassName);
        if (properties.validation().isDockerIsolation()) {
            return executeInContainer(workDir, command);
        }
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        List<String> shell = windows ? List.of("cmd.exe", "/c", command) : List.of("sh", "-c", command);
        return run(shell, workDir, properties.validation().timeoutSeconds());
    }

    /**
     * Runs the tests in a fresh container: the patched copy is copied in (no bind mount of host folders), the
     * container gets no environment from the agent, no capabilities, and capped memory, CPU and processes.
     * Only the dedicated dependency cache volume is shared between runs.
     */
    private ProcessResult executeInContainer(Path workDir, String command) throws IOException, InterruptedException {
        String name = "codehealth-validate-" + UUID.randomUUID();
        int timeout = properties.validation().timeoutSeconds();
        try {
            ProcessResult created = run(dockerCreate(name, properties.validation(), command), null, 120);
            if (created.exitCode() != 0) {
                return new ProcessResult(created.exitCode(), "docker create failed (is Docker running?): "
                        + created.output());
            }
            ProcessResult copied = run(List.of("docker", "cp", workDir.toAbsolutePath() + File.separator + ".",
                    name + ":/workspace"), null, 300);
            if (copied.exitCode() != 0) {
                return new ProcessResult(copied.exitCode(), "docker cp failed: " + copied.output());
            }
            return run(List.of("docker", "start", "--attach", name), null, timeout);
        } finally {
            try {
                run(List.of("docker", "rm", "--force", name), null, 60);
            } catch (IOException | RuntimeException ex) {
                log.warn("Could not remove validation container {}: {}", name, ex.getMessage());
            }
        }
    }

    static List<String> dockerCreate(String name, CodeHealthProperties.Validation validation, String command) {
        List<String> args = new ArrayList<>(List.of("docker", "create",
                "--name", name,
                "--network", validation.sandboxNetwork(),
                "--cap-drop", "ALL",
                "--security-opt", "no-new-privileges",
                "--memory", "2g",
                "--cpus", "2",
                "--pids-limit", "512",
                "--workdir", "/workspace"));
        if (validation.sandboxCacheVolume() != null && !validation.sandboxCacheVolume().isBlank()) {
            args.addAll(List.of("--volume", validation.sandboxCacheVolume() + ":/root/.m2"));
        }
        args.addAll(List.of(validation.sandboxImage(), "sh", "-c", command));
        return args;
    }

    private ProcessResult run(List<String> command, Path workDir, int timeoutSeconds)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        if (workDir != null) {
            builder.directory(workDir.toFile());
        }
        Map<String, String> env = builder.environment();
        Map<String, String> safe = safeEnvironment(env);
        env.clear();
        env.putAll(safe);

        Process process = builder.start();
        // Drain output on a virtual thread so the timeout below still fires if the build hangs.
        CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> {
            try {
                return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                return "Could not read output: " + ex.getMessage();
            }
        }, OUTPUT_READERS);
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            return new ProcessResult(124, "Timed out after " + timeoutSeconds + "s");
        }
        return new ProcessResult(process.exitValue(), output.join());
    }

    private static void copyTree(Path from, Path to) throws IOException {
        Files.walkFileTree(from, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                if (!dir.equals(from) && (name.equals("target") || name.equals(".git") || name.equals("node_modules"))) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.createDirectories(to.resolve(from.relativize(dir).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.copy(file, to.resolve(from.relativize(file).toString()), StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (var paths = Files.walk(dir)) {
            paths.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(p -> p.toFile().delete());
        } catch (IOException ex) {
            log.debug("Could not delete {}: {}", dir, ex.getMessage());
        }
    }

    private static String tail(String output) {
        return output.length() <= 4000 ? output : output.substring(output.length() - 4000);
    }

    private static String truncate(StringBuilder report) {
        return report.length() <= MAX_LOG_CHARS ? report.toString() : report.substring(0, MAX_LOG_CHARS);
    }
}
