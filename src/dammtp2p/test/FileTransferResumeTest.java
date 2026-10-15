package dammtp2p.test;

import dammtp2p.core.model.FileMetadata;
import dammtp2p.core.model.TransferState;
import dammtp2p.core.transfer.DefaultTransferTask;
import dammtp2p.core.transfer.FileDataHandshake;
import dammtp2p.core.transfer.FileNegotiationPayload;
import dammtp2p.core.transfer.FileResumePayload;
import dammtp2p.core.transfer.HashVerifier;
import dammtp2p.core.transfer.TransferTask;
import dammtp2p.util.Constants;
import dammtp2p.util.FormatUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;

/**
 * Unit tests verifying protocol data structures, transfer task lifecycle,
 * and resume negotiation for Issue #3.
 */
public class FileTransferResumeTest {

    public static void main(String[] args) {
        System.out.println("=== RUNNING ISSUE #3 VERIFICATION SUITE ===");
        int passed = 0;
        int failed = 0;

        try {
            testFileDataHandshakeSerialization();
            System.out.println("[PASS] testFileDataHandshakeSerialization");
            passed++;
        } catch (Throwable t) {
            System.err.println("[FAIL] testFileDataHandshakeSerialization: " + t.getMessage());
            failed++;
        }

        try {
            testFileResumePayloadNegotiation();
            System.out.println("[PASS] testFileResumePayloadNegotiation");
            passed++;
        } catch (Throwable t) {
            System.err.println("[FAIL] testFileResumePayloadNegotiation: " + t.getMessage());
            failed++;
        }

        try {
            testTransferStateTransitions();
            System.out.println("[PASS] testTransferStateTransitions");
            passed++;
        } catch (Throwable t) {
            System.err.println("[FAIL] testTransferStateTransitions: " + t.getMessage());
            failed++;
        }

        try {
            testHashVerifierStreaming();
            System.out.println("[PASS] testHashVerifierStreaming");
            passed++;
        } catch (Throwable t) {
            System.err.println("[FAIL] testHashVerifierStreaming: " + t.getMessage());
            failed++;
        }

        try {
            testDefaultTransferTaskLifecycle();
            System.out.println("[PASS] testDefaultTransferTaskLifecycle");
            passed++;
        } catch (Throwable t) {
            System.err.println("[FAIL] testDefaultTransferTaskLifecycle: " + t.getMessage());
            failed++;
        }

        try {
            testFormatUtils64BitSupport();
            System.out.println("[PASS] testFormatUtils64BitSupport");
            passed++;
        } catch (Throwable t) {
            System.err.println("[FAIL] testFormatUtils64BitSupport: " + t.getMessage());
            failed++;
        }

        System.out.println(String.format("\nResult: %d passed, %d failed.", passed, failed));
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void testFileDataHandshakeSerialization() throws Exception {
        long largeSize = 5L * 1024L * 1024L * 1024L; // 5 GiB
        long resumeOffset = 1024L * 1024L * 500L;    // 500 MB
        String transferId = "TX-TEST-2380600401";

        FileDataHandshake original = new FileDataHandshake(
                FileDataHandshake.VERSION,
                FileDataHandshake.ROLE_SENDER,
                transferId,
                largeSize,
                resumeOffset
        );

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        original.writeTo(dos);

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        DataInputStream dis = new DataInputStream(bais);
        FileDataHandshake restored = FileDataHandshake.readFrom(dis);

        if (!transferId.equals(restored.getTransferId())) {
            throw new AssertionError("Transfer ID mismatch");
        }
        if (restored.getFileSize() != largeSize) {
            throw new AssertionError("File size 64-bit mismatch: " + restored.getFileSize());
        }
        if (restored.getStartingOffset() != resumeOffset) {
            throw new AssertionError("Resume offset mismatch: " + restored.getStartingOffset());
        }
    }

    private static void testFileResumePayloadNegotiation() {
        String transferId = "RESUME-999";
        String fileName = "sample_dataset.iso";
        long size = 10L * 1024L * 1024L * 1024L;
        long offset = 2L * 1024L * 1024L * 1024L;

        FileResumePayload ack = FileResumePayload.createAck(transferId, fileName, size, offset, "Alice", "Bob");
        String json = ack.toJson();
        FileResumePayload parsed = FileResumePayload.fromJson(json);

        if (!parsed.isAccepted()) {
            throw new AssertionError("Resume status should be accepted");
        }
        if (parsed.getResumeOffset() != offset) {
            throw new AssertionError("Parsed offset mismatch: " + parsed.getResumeOffset());
        }
        if (!"Bob".equals(parsed.getReceiver())) {
            throw new AssertionError("Receiver mismatch: " + parsed.getReceiver());
        }
    }

    private static void testTransferStateTransitions() {
        if (!TransferState.PENDING.canTransitionTo(TransferState.ACCEPTED)) {
            throw new AssertionError("PENDING should transition to ACCEPTED");
        }
        if (!TransferState.TRANSFERRING.canTransitionTo(TransferState.INTERRUPTED)) {
            throw new AssertionError("TRANSFERRING should transition to INTERRUPTED");
        }
        if (!TransferState.INTERRUPTED.canTransitionTo(TransferState.RESUMING)) {
            throw new AssertionError("INTERRUPTED should transition to RESUMING");
        }
        if (!TransferState.RESUMING.canTransitionTo(TransferState.COMPLETED)) {
            throw new AssertionError("RESUMING should transition to COMPLETED");
        }
    }

    private static void testHashVerifierStreaming() throws Exception {
        File temp = File.createTempFile("hash_test_", ".bin");
        temp.deleteOnExit();
        try (FileOutputStream fos = new FileOutputStream(temp)) {
            byte[] data = "DAMMTP2P_STREAMING_HASH_VERIFICATION_2380600401".getBytes("UTF-8");
            fos.write(data);
        }

        String hash = HashVerifier.calculateSha256(temp);
        if (!HashVerifier.isValidSha256(hash)) {
            throw new AssertionError("Invalid SHA-256 output: " + hash);
        }
    }

    private static void testDefaultTransferTaskLifecycle() {
        long fileSize = 2L * 1024L * 1024L * 1024L; // 2 GiB
        long offset = 512L * 1024L * 1024L;        // 512 MB
        FileMetadata meta = new FileMetadata("task-test-01", "backup.tar.gz", fileSize,
                "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90",
                offset, "UserA", "UserB", System.currentTimeMillis());

        DefaultTransferTask task = new DefaultTransferTask(meta, TransferState.RESUMING);
        if (task.getTransferredBytes() != offset) {
            throw new AssertionError("Initial transferred bytes must equal resume offset");
        }
        if (task.getRemainingBytes() != (fileSize - offset)) {
            throw new AssertionError("Remaining bytes incorrect: " + task.getRemainingBytes());
        }

        task.pause();
        if (!task.isPaused()) {
            throw new AssertionError("Task should be paused");
        }

        task.resume();
        if (task.isPaused()) {
            throw new AssertionError("Task should be unpaused after resume");
        }

        task.cancel();
        if (!task.isCancelled() || task.getState() != TransferState.CANCELED) {
            throw new AssertionError("Task cancellation failed");
        }
    }

    private static void testFormatUtils64BitSupport() {
        long fiveGiB = 5L * 1024L * 1024L * 1024L;
        String formatted = FormatUtils.formatFileSize(fiveGiB);
        if (!"5.0 GB".equals(formatted)) {
            throw new AssertionError("Expected 5.0 GB but got " + formatted);
        }

        double percent = FormatUtils.calculatePercentage(500, 1000);
        if (Math.abs(percent - 50.0) > 0.01) {
            throw new AssertionError("Percentage mismatch: " + percent);
        }
    }
}
