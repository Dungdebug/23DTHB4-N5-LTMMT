package dammtp2p.core.transfer;

import dammtp2p.core.model.FileMetadata;
import dammtp2p.util.Constants;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * High-performance streaming SHA-256 verification and file integrity utility.
 *
 * <p>ARCHITECTURAL SPECIFICATION:
 * - Operates strictly via incremental streaming buffer chunks (64 KB).
 * - NEVER loads the entire file into memory (supports 1 GB, 10 GB, 50 GB, 100 GB+).
 * - Outputs canonical 64-character lowercase hexadecimal digest strings.
 * - Thread-safe and compatible with background worker pools.
 * - Includes safe collision resolution for destination file renaming.
 */
public final class HashVerifier {

    public static final String ALGORITHM = "SHA-256";
    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();
    private static final Pattern HEX_PATTERN = Pattern.compile("^[a-fA-F0-9]{64}$");

    private HashVerifier() {
        // Prevent instantiation
    }

    /**
     * Functional callback interface for reporting hash computation progress.
     */
    @FunctionalInterface
    public interface ProgressCallback {
        /**
         * Invoked periodically during hash calculation.
         *
         * @param bytesHashed Total bytes hashed so far (64-bit long)
         * @param totalBytes  Total file size in bytes (64-bit long)
         */
        void onProgress(long bytesHashed, long totalBytes);
    }

    /**
     * Validates if a string is a valid 64-character hexadecimal SHA-256 digest.
     *
     * @param hash String to test
     * @return true if valid SHA-256 hex string
     */
    public static boolean isValidSha256(String hash) {
        return hash != null && HEX_PATTERN.matcher(hash.trim()).matches();
    }

    /**
     * Computes the SHA-256 checksum of the specified file using streaming chunks.
     *
     * @param file Target file to hash
     * @return 64-character lowercase hexadecimal SHA-256 string
     * @throws IOException on I/O failure or file not found
     */
    public static String computeSha256(File file) throws IOException {
        return computeSha256(file, null);
    }

    public static String calculateSha256(File file, ProgressCallback callback) throws IOException {
        return computeSha256(file, callback);
    }

    public static String calculateSha256(File file) throws IOException {
        return computeSha256(file, null);
    }

    /**
     * Computes the SHA-256 checksum of the specified Path.
     *
     * @param path Target path to hash
     * @return 64-character lowercase hexadecimal SHA-256 string
     * @throws IOException on I/O failure
     */
    public static String computeSha256(Path path) throws IOException {
        Objects.requireNonNull(path, "path must not be null");
        return computeSha256(path.toFile(), null);
    }

    /**
     * Computes the SHA-256 checksum of the specified file with progress callbacks.
     *
     * @param file     Target file to hash
     * @param callback Optional progress callback listener (may be null)
     * @return 64-character lowercase hexadecimal SHA-256 string
     * @throws IOException on I/O failure or file not found
     */
    public static String computeSha256(File file, ProgressCallback callback) throws IOException {
        Objects.requireNonNull(file, "file must not be null");
        if (!file.exists()) {
            throw new FileNotFoundException("Tệp tin không tồn tại để tính SHA-256: " + file.getAbsolutePath());
        }
        if (!file.isFile()) {
            throw new IOException("Đường dẫn không phải là tệp hợp lệ: " + file.getAbsolutePath());
        }
        if (!file.canRead()) {
            throw new IOException("Không có quyền đọc tệp để tính SHA-256: " + file.getName());
        }

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available in Java runtime", e);
        }

        long totalBytes = file.length();
        long hashedBytes = 0L;
        long lastReportTime = System.currentTimeMillis();

        byte[] buffer = new byte[Constants.DEFAULT_BUFFER_SIZE];
        try (InputStream in = new BufferedInputStream(new FileInputStream(file), Constants.DEFAULT_BUFFER_SIZE)) {
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
                hashedBytes += read;

                long now = System.currentTimeMillis();
                if (callback != null && (now - lastReportTime >= 200 || hashedBytes == totalBytes)) {
                    callback.onProgress(hashedBytes, totalBytes);
                    lastReportTime = now;
                }
            }
        }

        // Final callback notification for 100%
        if (callback != null) {
            callback.onProgress(totalBytes, totalBytes);
        }

        byte[] hashBytes = digest.digest();
        return bytesToHex(hashBytes);
    }

    /**
     * Verifies that the specified file matches the expected SHA-256 hash.
     *
     * @param file         Target file to inspect
     * @param expectedHash Expected 64-character hexadecimal SHA-256
     * @return true if calculated hash equals expected hash (case-insensitive)
     * @throws IOException on file read error
     */
    public static boolean verify(File file, String expectedHash) throws IOException {
        return verify(file, expectedHash, null);
    }

    /**
     * Verifies that the specified file matches the expected SHA-256 hash, with progress updates.
     *
     * @param file         Target file to inspect
     * @param expectedHash Expected 64-character hexadecimal SHA-256
     * @param callback     Optional progress callback (may be null)
     * @return true if calculated hash equals expected hash (case-insensitive)
     * @throws IOException on file read error
     */
    public static boolean verify(File file, String expectedHash, ProgressCallback callback) throws IOException {
        if (expectedHash == null || expectedHash.trim().isEmpty()) {
            return false;
        }
        String calculated = computeSha256(file, callback);
        return calculated.equalsIgnoreCase(expectedHash.trim());
    }

    /**
     * Resolves a unique destination file in the specified directory to prevent
     * silently overwriting pre-existing user files.
     *
     * <p>Example:
     * If {@code document.pdf} exists, returns {@code document (1).pdf}.
     * If {@code document (1).pdf} also exists, returns {@code document (2).pdf}.
     *
     * @param directory        Destination folder
     * @param originalFileName Intended file name
     * @return Unique non-colliding File instance
     */
    public static File resolveUniqueDestinationFile(File directory, String originalFileName) {
        if (!directory.exists()) {
            directory.mkdirs();
        }
        String safeName = FileMetadata.sanitizeFileName(originalFileName);
        File candidate = new File(directory, safeName);
        if (!candidate.exists()) {
            return candidate;
        }

        String baseName = safeName;
        String extension = "";
        int dotIndex = safeName.lastIndexOf('.');
        if (dotIndex > 0 && dotIndex < safeName.length() - 1) {
            baseName = safeName.substring(0, dotIndex);
            extension = safeName.substring(dotIndex);
        }

        int counter = 1;
        while (candidate.exists()) {
            String newName = String.format("%s (%d)%s", baseName, counter, extension);
            candidate = new File(directory, newName);
            counter++;
        }
        return candidate;
    }

    /**
     * Converts a byte array into a canonical 64-character lowercase hex string.
     *
     * @param bytes Binary digest array
     * @return Lowercase hex string
     */
    public static String bytesToHex(byte[] bytes) {
        if (bytes == null) {
            return "";
        }
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }
}
