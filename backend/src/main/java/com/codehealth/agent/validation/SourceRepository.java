package com.codehealth.agent.validation;

import com.codehealth.agent.config.CodeHealthProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Read-only access to the local checkout of the monitored repository. Every path is resolved inside the
 * configured root; anything escaping it is refused.
 */
@Component
public class SourceRepository {

    private static final long MAX_FILE_BYTES = 200_000;

    private final Path root;

    public SourceRepository(CodeHealthProperties properties) {
        String configured = properties.validation().repoPath();
        this.root = configured == null || configured.isBlank() ? null : Path.of(configured).toAbsolutePath().normalize();
    }

    public boolean isConfigured() {
        return root != null && Files.isDirectory(root);
    }

    public Path root() {
        return root;
    }

    public Optional<String> read(String relativePath) {
        if (!isConfigured() || relativePath == null) {
            return Optional.empty();
        }
        Path file = resolve(relativePath);
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > MAX_FILE_BYTES) {
                return Optional.empty();
            }
            return Optional.of(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public Path resolve(String relativePath) {
        Path file = root.resolve(relativePath).normalize();
        if (!file.startsWith(root)) {
            throw new IllegalArgumentException("Path escapes repository root: " + relativePath);
        }
        return file;
    }
}
