package dammtp2p.test;

import dammtp2p.core.model.ChatMessage;
import dammtp2p.server.ServerCore;
import dammtp2p.server.ServerFileRelayManager;
import dammtp2p.server.StorageEngine;
import java.io.File;
import java.util.List;
import java.util.UUID;

/**
 * Phase 4 Automated Test Suite:
 * 1. StorageEngine 64-bit file transfer persistence & restoration across restart.
 * 2. Chat history persistence & room/private query filtering.
 * 3. ServerFileRelayManager resume negotiation recovery from persistent storage.
 */
public class PersistentStorageAndResumeTest {

    public static void main(String[] args) {
        boolean ok = runAllTests();
        System.exit(ok ? 0 : 1);
    }

    public static boolean runAllTests() {
        System.out.println("==================================================================");
        System.out.println("  DAMMTP2P — PHASE 4: STORAGE ENGINE & PERSISTENT RESUME TEST");
        System.out.println("==================================================================");

        boolean passPersistence = testStorageEnginePersistenceAcrossRestart();
        boolean passChatHistory = testChatHistoryPersistenceAndRetrieval();
        boolean passRelayRecovery = testRelayManagerResumeRecovery();

        System.out.println("------------------------------------------------------------------");
        boolean allPass = passPersistence && passChatHistory && passRelayRecovery;
        System.out.println("  PHASE 4 TEST RESULT: " + (allPass ? "ALL TESTS PASSED" : "FAILED"));
        System.out.println("==================================================================");
        return allPass;
    }

    private static boolean testStorageEnginePersistenceAcrossRestart() {
        System.out.print("[TEST 1/3] StorageEngine 64-Bit File Transfer Persistence & Restart: ");
        File tempDir = new File("build/test_storage_" + UUID.randomUUID());
        tempDir.mkdirs();

        try {
            StorageEngine engine1 = new StorageEngine(tempDir);
            String transferId = "xfer-" + UUID.randomUUID();
            String fileName = "big_archive_20gb.iso";
            long fileSize = 21_474_836_480L; // 20 GiB (exceeds 32-bit integer limit)
            long offset1 = 5_368_709_120L;   // 5 GiB transferred

            engine1.recordFileTransfer(transferId, fileName, fileSize, "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                    offset1, "alice", "bob", null, false, "TRANSFERRING");

            // Verify stored in memory
            StorageEngine.FileTransferRecord rec1 = engine1.getFileTransfer(transferId);
            if (rec1 == null || rec1.getOffsetBytes() != offset1) {
                return fail("Memory record missing or offset mismatch");
            }

            // Simulate progress update
            long offset2 = 10_737_418_240L; // 10 GiB
            engine1.updateTransferOffset(transferId, offset2, "INTERRUPTED");

            // SIMULATE SERVER RESTART: Create a brand new StorageEngine pointing to the same folder
            StorageEngine engine2 = new StorageEngine(tempDir);
            StorageEngine.FileTransferRecord restored = engine2.getFileTransfer(transferId);

            if (restored == null) {
                return fail("Record failed to restore from transfers.json after restart");
            }
            if (!fileName.equals(restored.getFileName())) {
                return fail("File name restored incorrectly: " + restored.getFileName());
            }
            if (restored.getFileSize() != fileSize) {
                return fail("File size restored incorrectly: " + restored.getFileSize());
            }
            if (restored.getOffsetBytes() != offset2) {
                return fail("Offset restored incorrectly: " + restored.getOffsetBytes() + " (expected " + offset2 + ")");
            }
            if (!"INTERRUPTED".equals(restored.getStatus())) {
                return fail("Status restored incorrectly: " + restored.getStatus());
            }

            System.out.println("PASS (Restored 64-bit offset " + restored.getOffsetBytes() + " bytes for 20 GiB ISO)");
            return true;
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private static boolean testChatHistoryPersistenceAndRetrieval() {
        System.out.print("[TEST 2/3] Chat History Multi-Room & 1-to-1 Persistence: ");
        File tempDir = new File("build/test_storage_" + UUID.randomUUID());
        tempDir.mkdirs();

        try {
            StorageEngine engine = new StorageEngine(tempDir);

            // Add room messages
            long now = System.currentTimeMillis();
            ChatMessage roomMsg1 = new ChatMessage("msg-1", "alice", null, "General", "Hello Room 1", now, false, false, null);
            ChatMessage roomMsg2 = new ChatMessage("msg-2", "bob", null, "General", "Hello Room 2", now + 10, false, false, null);
            ChatMessage otherRoom = new ChatMessage("msg-3", "charlie", null, "Voice_Lounge", "Voice chat topic", now + 20, false, false, null);

            // Add private messages
            ChatMessage priv1 = new ChatMessage("msg-4", "alice", "bob", null, "Secret 1-to-1", now + 30, false, true, "iv-mock-1");
            ChatMessage priv2 = new ChatMessage("msg-5", "bob", "alice", null, "Secret response", now + 40, false, true, "iv-mock-2");
            ChatMessage privOther = new ChatMessage("msg-6", "alice", "charlie", null, "Hi Charlie", now + 50, false, false, null);

            engine.recordChatMessage(roomMsg1);
            engine.recordChatMessage(roomMsg2);
            engine.recordChatMessage(otherRoom);
            engine.recordChatMessage(priv1);
            engine.recordChatMessage(priv2);
            engine.recordChatMessage(privOther);

            // Restart StorageEngine
            StorageEngine engineRestarted = new StorageEngine(tempDir);

            List<StorageEngine.ChatMessageRecord> generalMsgs = engineRestarted.getRecentRoomMessages("General", 10);
            if (generalMsgs.size() != 2) {
                return fail("Expected 2 messages in General room, got: " + generalMsgs.size());
            }
            if (!"msg-1".equals(generalMsgs.get(0).getMessageId()) || !"msg-2".equals(generalMsgs.get(1).getMessageId())) {
                return fail("Message order or ID mismatch in General room");
            }

            List<StorageEngine.ChatMessageRecord> privateAliceBob = engineRestarted.getRecentPrivateMessages("alice", "bob", 10);
            if (privateAliceBob.size() != 2) {
                return fail("Expected 2 private messages between alice and bob, got: " + privateAliceBob.size());
            }

            // Verify audit log
            engineRestarted.appendAuditLog("TEST_EVENT", "alice", "127.0.0.1", "User logged in");
            File auditFile = new File(tempDir, "audit.log");
            if (!auditFile.exists() || auditFile.length() == 0) {
                return fail("Audit log file was not generated");
            }

            System.out.println("PASS (Room & 1-to-1 filtered correctly, audit log verified)");
            return true;
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        } finally {
            deleteRecursively(tempDir);
        }
    }

    private static boolean testRelayManagerResumeRecovery() {
        System.out.print("[TEST 3/3] ServerFileRelayManager Persistent Resume Offset Recovery: ");
        try {
            ServerCore server = new ServerCore(19877, 19878, 19879);
            ServerFileRelayManager relayManager = server.getFileRelayManager();

            String transferId = "relay-xfer-" + UUID.randomUUID();
            String fileName = "project_dataset.zip";
            long fileSize = 500_000_000L; // 500 MB
            long interruptedOffset = 180_000_000L; // 180 MB reached before interruption

            // Record previous transfer in StorageEngine
            StorageEngine.getInstance().recordFileTransfer(
                    transferId, fileName, fileSize, "mock-sha", interruptedOffset,
                    "alice", "bob", null, false, "INTERRUPTED"
            );

            // Now, client renegotiates with startOffset = 0 (client asks server for state)
            relayManager.registerTransferNegotiation(transferId, "alice", "bob", fileName, fileSize, 0L);

            // Check if relayManager restored the effective offset
            ServerFileRelayManager.ActiveTransferInfo info = relayManager.getTransferInfo(transferId);
            if (info == null) {
                return fail("Transfer negotiation was not registered");
            }
            if (info.getStartOffset() != interruptedOffset) {
                return fail("RelayManager did not restore stored offset! Got " + info.getStartOffset() + ", expected " + interruptedOffset);
            }

            System.out.println("PASS (Restored effective resume offset " + info.getStartOffset() + " bytes from StorageEngine)");
            return true;
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private static boolean fail(String reason) {
        System.out.println("FAIL (" + reason + ")");
        return false;
    }

    private static void deleteRecursively(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] files = f.listFiles();
            if (files != null) {
                for (File child : files) deleteRecursively(child);
            }
        }
        f.delete();
    }
}