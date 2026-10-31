package com.codehealth.agent.validation;

import com.codehealth.agent.config.CodeHealthProperties;
import com.codehealth.agent.domain.Enums.ValidationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
 *       Only N consecutive green runs give PASSED.</li>
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

    private ProcessResult execute(Path workDir, String testClassName) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>();
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        if (windows) {
            command.addAll(List.of("cmd.exe", "/c"));
        } else {
            command.addAll(List.of("sh", "-c"));
        }
        String testFilter = testClassName == null ? "" : " -Dtest=" + testClassName.replaceAll("[^A-Za-z0-9_.#]", "");
        command.add(properties.validation().testCommand() + testFilter);

        Process process = new ProcessBuilder(command).directory(workDir.toFile()).redirectErrorStream(true).start();
        // Drain output on a virtual thread so the timeout below still fires if the build hangs.
        CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> {
            try {
                return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException ex) {
                return "Could not read output: " + ex.getMessage();
            }
        }, OUTPUT_READERS);
        if (!process.waitFor(properties.validation().timeoutSeconds(), TimeUnit.SECONDS)) {
            process.destroyForcibly();
            return new ProcessResult(124, "Timed out after " + properties.validation().timeoutSeconds() + "s");
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
