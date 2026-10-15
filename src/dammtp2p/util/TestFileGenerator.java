package dammtp2p.util;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.util.Locale;

/**
 * Utility for generating test files with real 64-bit length (1 MB, 100 MB, 1 GB, 5 GB, 10 GB+).
 * Supports both streaming physical patterned bytes and fast sparse allocation on NTFS/ext4.
 *
 * <p>Never allocates the full file size into RAM.
 */
public final class TestFileGenerator {

    private TestFileGenerator() {
        // Prevent instantiation
    }

    /**
     * Generates a test file of the specified size.
     *
     * @param targetFile File destination to create
     * @param sizeBytes  Size in bytes (64-bit long)
     * @param sparse     If true, uses instant filesystem sparse allocation; if false, writes patterned bytes
     * @return Generated File reference
     * @throws IOException on I/O error
     */
    public static File generateTestFile(File targetFile, long sizeBytes, boolean sparse) throws IOException {
        if (targetFile.getParentFile() != null && !targetFile.getParentFile().exists()) {
            targetFile.getParentFile().mkdirs();
        }

        if (sparse) {
            try (RandomAccessFile raf = new RandomAccessFile(targetFile, "rw")) {
                raf.setLength(sizeBytes);
            }
            return targetFile;
        }

        // Stream patterned chunks to avoid RAM allocation
        byte[] buffer = new byte[Constants.DEFAULT_BUFFER_SIZE];
        for (int i = 0; i < buffer.length; i++) {
            buffer[i] = (byte) ((i % 94) + 33); // Printable ASCII pattern
        }

        try (OutputStream out = new BufferedOutputStream(new FileOutputStream(targetFile), Constants.DEFAULT_BUFFER_SIZE)) {
            long remaining = sizeBytes;
            while (remaining > 0) {
                int toWrite = (int) Math.min(buffer.length, remaining);
                out.write(buffer, 0, toWrite);
                remaining -= toWrite;
            }
            out.flush();
        }

        return targetFile;
    }

    /**
     * Parses human-readable size strings such as "10M", "1G", "500K", "1024" into bytes.
     *
     * @param str Size string
     * @return Size in bytes as 64-bit long
     * @throws IllegalArgumentException on parse failure
     */
    public static long parseSize(String str) {
        if (str == null || str.trim().isEmpty()) {
            throw new IllegalArgumentException("Size string cannot be empty");
        }
        String s = str.trim().toUpperCase(Locale.ROOT);
        long multiplier = 1L;
        if (s.endsWith("TB") || s.endsWith("T")) {
            multiplier = 1024L * 1024L * 1024L * 1024L;
            s = s.substring(0, s.endsWith("TB") ? s.length() - 2 : s.length() - 1).trim();
        } else if (s.endsWith("GB") || s.endsWith("G")) {
            multiplier = 1024L * 1024L * 1024L;
            s = s.substring(0, s.endsWith("GB") ? s.length() - 2 : s.length() - 1).trim();
        } else if (s.endsWith("MB") || s.endsWith("M")) {
            multiplier = 1024L * 1024L;
            s = s.substring(0, s.endsWith("MB") ? s.length() - 2 : s.length() - 1).trim();
        } else if (s.endsWith("KB") || s.endsWith("K")) {
            multiplier = 1024L;
            s = s.substring(0, s.endsWith("KB") ? s.length() - 2 : s.length() - 1).trim();
        } else if (s.endsWith("B")) {
            s = s.substring(0, s.length() - 1).trim();
        }

        double val = Double.parseDouble(s);
        return (long) (val * multiplier);
    }
}
