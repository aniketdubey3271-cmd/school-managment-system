package com.sms.common;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * Stores uploaded files on disk under random names (the user's filename is never
 * used as a path). Files are only served through controllers that check access.
 */
@Component
public class FileStorage {

    public static final long MAX_BYTES = 10L * 1024 * 1024;
    public static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "doc", "docx", "txt", "jpg", "jpeg", "png", "zip");

    private final Path root;

    public FileStorage(@Value("${sms.uploads.dir}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
    }

    /**
     * Saves the file and returns its stored name. If the surrounding transaction
     * rolls back, the file is removed again.
     */
    public String store(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FormException("Choose a file to upload");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new FormException("File is too large (max 10 MB)");
        }
        String extension = extensionOf(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new FormException("Allowed file types: " + String.join(", ", ALLOWED_EXTENSIONS.stream().sorted().toList()));
        }
        String storedName = UUID.randomUUID() + "." + extension;
        try (InputStream in = file.getInputStream()) {
            Files.createDirectories(root);
            Files.copy(in, resolve(storedName));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save upload", e);
        }
        afterTransaction(storedName, false);
        return storedName;
    }

    public Resource load(String storedName) {
        Path path = resolve(storedName);
        if (!Files.isRegularFile(path)) {
            throw new NotFoundException("File");
        }
        return new PathResource(path);
    }

    /** Deletes the file once the surrounding transaction commits (immediately if there is none). */
    public void deleteAfterCommit(String storedName) {
        afterTransaction(storedName, true);
    }

    private void afterTransaction(String storedName, boolean deleteOnCommit) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            if (deleteOnCommit) {
                deleteQuietly(storedName);
            }
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                boolean committed = status == STATUS_COMMITTED;
                if (committed == deleteOnCommit) {
                    deleteQuietly(storedName);
                }
            }
        });
    }

    private void deleteQuietly(String storedName) {
        try {
            Files.deleteIfExists(resolve(storedName));
        } catch (IOException ignored) {
            // Leftover file; harmless.
        }
    }

    private Path resolve(String storedName) {
        Path path = root.resolve(storedName).normalize();
        if (!path.getParent().equals(root)) {
            throw new NotFoundException("File");
        }
        return path;
    }

    private static String extensionOf(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        return dot < 0 ? "" : filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
