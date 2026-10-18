package dammtp2p.util;

import dammtp2p.core.model.FileMetadata;
import dammtp2p.core.transfer.FileDataHandshake;
import dammtp2p.core.transfer.HashVerifier;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;

/**
 * Comprehensive Boundary Verification Suite for the 20 GiB Maximum File Transfer Requirement.
 *
 * <p>Tests all required boundary conditions without creating massive in-memory byte arrays
 * or exhausting physical storage:
 * <ul>
 *   <li>Boundary 1: Empty file (0 bytes)</li>
 *   <li>Boundary 2: Small file (1 KiB)</li>
 *   <li>Boundary 3: File just below configured limit (20 GiB - 1 byte = 21,474,836,479 bytes)</li>
 *   <li>Boundary 4: File exactly at configured limit (20 GiB = 21,474,836,480 bytes)</li>
 *   <li>Boundary 5: File exceeding configured limit by one byte (20 GiB + 1 byte = 21,474,836,481 bytes)</li>
 *   <li>Boundary 6: Interrupted transfer and resume near upper limit (offset near 20 GiB)</li>
 *   <li>Boundary 7: 64-bit chunk index & remaining-bytes calculation past the 2.14 GB int limit</li>
 * </ul>
 */
public final class FileBoundaryTest {

    private FileBoundaryTest() {}

    public static boolean runAllBoundaryTests() {
        System.out.println("==================================================================");
        System.out.println("  DAMMTP2P — 20 GiB MAXIMUM FILE TRANSFER BOUNDARY TEST SUITE");
        System.out.println("  Configured MAX_FILE_SIZE_BYTES = " + Constants.MAX_FILE_SIZE_BYTES + " (" + Constants.MAX_FILE_SIZE_LABEL + ")");
        System.out.println("==================================================================");

        boolean allPassed = true;

        allPassed &= testEmptyFile();
        allPassed &= testSmallFile();
        allPassed &= testFileJustBelowLimit();
        allPassed &= testFileExactlyAtLimit();
        allPassed &= testFileExceedingLimitByOneByte();
        allPassed &= testInterruptedTransferAndResumeNearLimit();
        allPassed &= testDataChannelHandshake64BitLimits();

        System.out.println("------------------------------------------------------------------");
        System.out.printf("  20 GiB BOUNDARY TEST RESULT: %s%n", allPassed ? "ALL TESTS PASSED" : "FAILED");
        System.out.println("==================================================================");
        return allPassed;
    }

    /**
     * Boundary 1: Empty file (0 bytes).
     */
    public static boolean testEmptyFile() {
        File tempFile = null;
        try {
            tempFile = File.createTempFile("boundary_0b_", ".bin");
            tempFile.deleteOnExit();

            if (tempFile.length() != 0L) {
                System.err.println("[BOUNDARY 1/7] Empty file size != 0");
                return false;
            }

            // Verify streaming SHA-256 for empty file
            String hash = HashVerifier.calculateSha256(tempFile);
            String expectedEmptyHash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
            if (!expectedEmptyHash.equalsIgnoreCase(hash)) {
                System.err.printf("[BOUNDARY 1/7] Empty hash mismatch: expected %s, got %s%n", expectedEmptyHash, hash);
                return false;
            }

            // Verify metadata
            FileMetadata meta = new FileMetadata(
                    UUID.randomUUID().toString(),
                    tempFile.getName(),
                    0L,
                    hash,
                    0L,
                    "alice",
                    "bob",
                    System.currentTimeMillis()
            );

            if (!FileMetadata.isSizeValid(0L)) {
                System.err.println("[BOUNDARY 1/7] FileMetadata rejected valid 0-byte size");
                return false;
            }

            // Verify JSON roundtrip
            FileMetadata parsed = FileMetadata.fromJson(meta.toJson());
            if (parsed.getFileSize() != 0L || parsed.getRemainingBytes() != 0L) {
                System.err.println("[BOUNDARY 1/7] Deserialized 0-byte metadata has invalid size or remaining");
                return false;
            }

            System.out.println("[BOUNDARY 1/7] Empty file (0 bytes) validation & SHA-256 hash: PASS");
            return true;
        } catch (Exception e) {
            System.err.println("[BOUNDARY 1/7] Failed with exception: " + e.getMessage());
            return false;
        } finally {
            if (tempFile != null && tempFile.exists()) tempFile.delete();
        }
    }

    /**
     * Boundary 2: Small file (1 KiB / 1024 bytes).
     */
    public static boolean testSmallFile() {
        File tempFile = null;
        try {
            tempFile = File.createTempFile("boundary_1kb_", ".bin");
            tempFile.deleteOnExit();

            byte[] data = new byte[1024];
            for (int i = 0; i < data.length; i++) data[i] = (byte) (i % 127);
            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(data);
            }

            String hash = HashVerifier.calculateSha256(tempFile);
            FileMetadata meta = new FileMetadata(
                    UUID.randomUUID().toString(),
                    tempFile.getName(),
                    1024L,
                    hash,
                    0L,
                    "alice",
                    "bob",
                    System.currentTimeMillis()
            );

            if (!FileMetadata.isSizeValid(1024L) || meta.getFileSize() != 1024L) {
                System.err.println("[BOUNDARY 2/7] Small file size validation failed");
                return false;
            }

            System.out.println("[BOUNDARY 2/7] Small file (1 KiB) validation & streaming SHA-256: PASS");
            return true;
        } catch (Exception e) {
            System.err.println("[BOUNDARY 2/7] Failed with exception: " + e.getMessage());
            return false;
        } finally {
            if (tempFile != null && tempFile.exists()) tempFile.delete();
        }
    }

    /**
     * Boundary 3: File just below the limit (20 GiB - 1 byte = 21,474,836,479 bytes).
     */
    public static boolean testFileJustBelowLimit() {
        try {
            long justBelow = Constants.MAX_FILE_SIZE_BYTES - 1L; // 21,474,836,479 bytes
            if (!FileMetadata.isSizeValid(justBelow)) {
                System.err.println("[BOUNDARY 3/7] isSizeValid rejected size just below 20 GiB");
                return false;
            }

            FileMetadata meta = new FileMetadata(
                    "transfer-just-below",
                    "large_archive.tar",
                    justBelow,
                    "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                    0L,
                    "sender_user",
                    "receiver_user",
                    System.currentTimeMillis()
            );

            if (meta.getFileSize() != justBelow) {
                System.err.println("[BOUNDARY 3/7] Stored file size mismatch");
                return false;
            }

            // Ensure JSON serialization preserves exact 64-bit long
            String json = meta.toJson();
            FileMetadata restored = FileMetadata.fromJson(json);
            if (restored.getFileSize() != justBelow) {
                System.err.printf("[BOUNDARY 3/7] JSON roundtrip lost precision: expected %d, got %d%n", justBelow, restored.getFileSize());
                return false;
            }

            System.out.printf("[BOUNDARY 3/7] File just below limit (20 GiB - 1 byte = %d bytes): PASS%n", justBelow);
            return true;
        } catch (Exception e) {
            System.err.println("[BOUNDARY 3/7] Failed with exception: " + e.getMessage());
            return false;
        }
    }

    /**
     * Boundary 4: File exactly at the configured limit (20 GiB = 21,474,836,480 bytes).
     */
    public static boolean testFileExactlyAtLimit() {
        try {
            long exactLimit = Constants.MAX_FILE_SIZE_BYTES; // 21,474,836,480 bytes
            if (!FileMetadata.isSizeValid(exactLimit)) {
                System.err.println("[BOUNDARY 4/7] isSizeValid rejected exact 20 GiB limit");
                return false;
            }

            FileMetadata meta = new FileMetadata(
                    "transfer-exact-limit",
                    "system_backup_20gb.iso",
                    exactLimit,
                    "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
                    0L,
                    "sender_user",
                    "receiver_user",
                    System.currentTimeMillis()
            );

            if (meta.getFileSize() != exactLimit) {
                System.err.println("[BOUNDARY 4/7] Exact limit size mismatch in constructor");
                return false;
            }

            FileMetadata restored = FileMetadata.fromJson(meta.toJson());
            if (restored.getFileSize() != exactLimit) {
                System.err.println("[BOUNDARY 4/7] JSON roundtrip lost precision for exact limit");
                return false;
            }

            System.out.printf("[BOUNDARY 4/7] File exactly at configured limit (20 GiB = %d bytes): PASS%n", exactLimit);
            return true;
        } catch (Exception e) {
            System.err.println("[BOUNDARY 4/7] Failed with exception: " + e.getMessage());
            return false;
        }
    }

    /**
     * Boundary 5: File exceeding the limit by one byte (20 GiB + 1 byte = 21,474,836,481 bytes).
     */
    public static boolean testFileExceedingLimitByOneByte() {
        try {
            long overByOne = Constants.MAX_FILE_SIZE_BYTES + 1L; // 21,474,836,481 bytes

            // 1. isSizeValid check
            if (FileMetadata.isSizeValid(overByOne)) {
                System.err.println("[BOUNDARY 5/7] isSizeValid accepted file exceeding 20 GiB by 1 byte!");
                return false;
            }

            // 2. FileMetadata constructor rejection
            boolean constructorRejected = false;
            try {
                new FileMetadata(
                        "transfer-oversized",
                        "oversized.bin",
                        overByOne,
                        null,
                        0L,
                        "alice",
                        "bob",
                        System.currentTimeMillis()
                );
            } catch (IllegalArgumentException e) {
                constructorRejected = true;
                if (!e.getMessage().contains("20 GiB")) {
                    System.err.println("[BOUNDARY 5/7] Exception message missing Vietnamese 20 GiB notice: " + e.getMessage());
                }
            }

            if (!constructorRejected) {
                System.err.println("[BOUNDARY 5/7] FileMetadata constructor failed to reject oversized file!");
                return false;
            }

            // 3. FileDataHandshake constructor rejection
            boolean handshakeRejected = false;
            try {
                new FileDataHandshake("transfer-oversized", overByOne, 0L);
            } catch (IllegalArgumentException e) {
                handshakeRejected = true;
            }

            if (!handshakeRejected) {
                System.err.println("[BOUNDARY 5/7] FileDataHandshake constructor failed to reject oversized file!");
                return false;
            }

            // 4. FileDataHandshake wire deserialization rejection
            boolean wireRejected = false;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            dos.writeInt(FileDataHandshake.MAGIC);
            dos.writeByte(FileDataHandshake.VERSION);
            dos.writeByte(FileDataHandshake.ROLE_SENDER);
            dos.writeUTF("transfer-oversized");
            dos.writeLong(overByOne);
            dos.writeLong(0L);
            dos.flush();

            try {
                FileDataHandshake.readFrom(new DataInputStream(new ByteArrayInputStream(baos.toByteArray())));
            } catch (IOException e) {
                wireRejected = true;
            }

            if (!wireRejected) {
                System.err.println("[BOUNDARY 5/7] FileDataHandshake wire parser failed to reject oversized size!");
                return false;
            }

            System.out.printf("[BOUNDARY 5/7] File exceeding limit by 1 byte (%d bytes): REJECTED SAFELY [PASS]%n", overByOne);
            return true;
        } catch (Exception e) {
            System.err.println("[BOUNDARY 5/7] Failed with exception: " + e.getMessage());
            return false;
        }
    }

    /**
     * Boundary 6: Interrupted transfer and resume near upper limit.
     * Evaluates resume offset past 2.14 GB int limit (e.g., at 20 GiB - 128 KiB).
     */
    public static boolean testInterruptedTransferAndResumeNearLimit() {
        try {
            long totalSize = Constants.MAX_FILE_SIZE_BYTES; // 21,474,836,480 bytes
            long resumeOffset = totalSize - (128L * 1024L); // 21,474,705,920 bytes (far exceeds 32-bit int)
            long expectedRemaining = 128L * 1024L;

            if (resumeOffset <= Integer.MAX_VALUE) {
                System.err.println("[BOUNDARY 6/7] Test offset must exceed 32-bit Integer.MAX_VALUE to test 64-bit math");
                return false;
            }

            FileMetadata meta = new FileMetadata(
                    "resume-near-20gb",
                    "huge_stream.dat",
                    totalSize,
                    "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789",
                    resumeOffset,
                    "sender_peer",
                    "receiver_peer",
                    System.currentTimeMillis()
            );

            if (meta.getRemainingBytes() != expectedRemaining) {
                System.err.printf("[BOUNDARY 6/7] Remaining bytes mismatch: expected %d, got %d%n", expectedRemaining, meta.getRemainingBytes());
                return false;
            }

            long chunkIndex = resumeOffset / Constants.DEFAULT_BUFFER_SIZE;
            long expectedChunkIndex = resumeOffset / (64L * 1024L);
            if (chunkIndex != expectedChunkIndex) {
                System.err.println("[BOUNDARY 6/7] Chunk index calculation mismatch");
                return false;
            }

            // Test offset update
            FileMetadata updated = meta.withOffset(totalSize);
            if (!updated.isComplete() || updated.getRemainingBytes() != 0L) {
                System.err.println("[BOUNDARY 6/7] Completed offset state check failed");
                return false;
            }

            // Test invalid resume offset exceeding total size
            boolean offsetOverRejected = false;
            try {
                meta.withOffset(totalSize + 1L);
            } catch (IllegalArgumentException e) {
                offsetOverRejected = true;
            }

            if (!offsetOverRejected) {
                System.err.println("[BOUNDARY 6/7] Failed to reject offset > fileSize");
                return false;
            }

            System.out.printf("[BOUNDARY 6/7] Interrupted transfer & resume at %d bytes (Chunk #%d, Remaining: %d bytes): PASS%n",
                    resumeOffset, chunkIndex, expectedRemaining);
            return true;
        } catch (Exception e) {
            System.err.println("[BOUNDARY 6/7] Failed with exception: " + e.getMessage());
            return false;
        }
    }

    /**
     * Boundary 7: Dedicated Port 8889 Binary Handshake 64-bit serialization & deserialization.
     */
    public static boolean testDataChannelHandshake64BitLimits() {
        try {
            long totalSize = Constants.MAX_FILE_SIZE_BYTES;
            long offset = totalSize - 65536L;

            FileDataHandshake sendHs = new FileDataHandshake(
                    "handshake-boundary-id",
                    totalSize,
                    offset,
                    FileDataHandshake.ROLE_RECEIVER
            );

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            sendHs.writeTo(dos);

            ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
            DataInputStream dis = new DataInputStream(bais);
            FileDataHandshake readHs = FileDataHandshake.readFrom(dis);

            if (readHs.getFileSize() != totalSize) {
                System.err.printf("[BOUNDARY 7/7] Handshake fileSize mismatch: expected %d, got %d%n", totalSize, readHs.getFileSize());
                return false;
            }
            if (readHs.getStartingOffset() != offset) {
                System.err.printf("[BOUNDARY 7/7] Handshake startingOffset mismatch: expected %d, got %d%n", offset, readHs.getStartingOffset());
                return false;
            }
            if (readHs.getRole() != FileDataHandshake.ROLE_RECEIVER) {
                System.err.println("[BOUNDARY 7/7] Handshake role mismatch");
                return false;
            }

            System.out.println("[BOUNDARY 7/7] Port 8889 Binary Data Channel Handshake 64-bit limits: PASS");
            return true;
        } catch (Exception e) {
            System.err.println("[BOUNDARY 7/7] Failed with exception: " + e.getMessage());
            return false;
        }
    }
}