package dammtp2p.test;

import dammtp2p.client.ClientCore;
import dammtp2p.client.FriendsManager;
import dammtp2p.core.model.ChatMessage;
import dammtp2p.server.ServerCore;
import java.io.File;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Automated Verification Suite for Phase 3:
 * 1. FriendsManager reciprocal addition, online/offline separation, and removal.
 * 2. Socket-level friend request exchange between Alice and Bob via Central Server.
 * 3. 1-to-1 End-to-End Encrypted (AES-128-GCM) private chat messaging.
 */
public class FriendsAndPrivateChatTest {

    public static void main(String[] args) {
        boolean ok = runAllTests();
        System.exit(ok ? 0 : 1);
    }

    public static boolean runAllTests() {
        System.out.println("==================================================================");
        System.out.println("  DAMMTP2P — PHASE 3: FRIENDS & 1-TO-1 PRIVATE CHAT TEST SUITE");
        System.out.println("==================================================================");

        boolean passUnit = testFriendsManagerUnit();
        boolean passNetwork = testFriendsAndPrivateChatOverNetwork();

        System.out.println("------------------------------------------------------------------");
        boolean allPass = passUnit && passNetwork;
        System.out.println("  PHASE 3 TEST RESULT: " + (allPass ? "ALL TESTS PASSED" : "FAILED"));
        System.out.println("==================================================================");
        return allPass;
    }

    private static boolean testFriendsManagerUnit() {
        System.out.print("[TEST 1/2] FriendsManager Reciprocal Addition, Removal & State: ");
        try {
            File tempFile = new File("build", "test_friends_" + System.currentTimeMillis() + ".json");
            FriendsManager fm = new FriendsManager(tempFile);

            String u1 = "test_user_a";
            String u2 = "test_user_b";

            // Initially not friends
            if (fm.isFriend(u1, u2)) return fail("Initially should not be friends");

            // Add friend
            boolean added = fm.addFriend(u1, u2);
            if (!added) return fail("addFriend should return true");
            if (!fm.isFriend(u1, u2)) return fail("u1 should have u2 as friend");
            if (!fm.isFriend(u2, u1)) return fail("u2 should have u1 as friend (reciprocal)");

            // Duplicate add returns false
            if (fm.addFriend(u1, u2)) return fail("Duplicate add should return false");

            // Categorization test
            List<String> onlinePeers = List.of(u2);
            List<String> onlineFriends = fm.getOnlineFriends(u1, onlinePeers);
            List<String> offlineFriends = fm.getOfflineFriends(u1, onlinePeers);

            if (onlineFriends.size() != 1 || !onlineFriends.contains(u2)) {
                return fail("u2 should be in online friends");
            }
            if (!offlineFriends.isEmpty()) {
                return fail("offline friends should be empty");
            }

            // Remove friend
            boolean removed = fm.removeFriend(u1, u2);
            if (!removed) return fail("removeFriend should return true");
            if (fm.isFriend(u1, u2) || fm.isFriend(u2, u1)) {
                return fail("Should no longer be friends after remove");
            }

            if (tempFile.exists()) tempFile.delete();

            System.out.println("PASS");
            return true;
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private static boolean testFriendsAndPrivateChatOverNetwork() {
        System.out.print("[TEST 2/2] Real-time Socket Friend Notification & AES-128 Private Chat: ");
        ServerCore server = null;
        ClientCore alice = null;
        ClientCore bob = null;
        try {
            int port = 19777;
            int filePort = 19778;
            int voicePort = 19779;

            server = new ServerCore(port, filePort, voicePort);
            server.start();

            CountDownLatch aliceLoginLatch = new CountDownLatch(1);
            CountDownLatch bobLoginLatch = new CountDownLatch(1);

            alice = new ClientCore("AliceApp");
            bob = new ClientCore("BobApp");

            alice.connectToServer("127.0.0.1", port);
            bob.connectToServer("127.0.0.1", port);

            alice.addListener(new ClientCore.ClientListener() {
                @Override public void onConnected(String serverIp, int p) {}
                @Override public void onDisconnected(String reason) {}
                @Override public void onLoginSuccess(String user, List<String> online, List<String> rooms) {
                    aliceLoginLatch.countDown();
                }
                @Override public void onLoginFailed(String reason) {}
                @Override public void onOnlineUsersUpdated(List<String> users) {}
                @Override public void onRoomsUpdated(List<String> rooms) {}
                @Override public void onChatMessage(ChatMessage message) {}
                @Override public void onIncomingFileRequest(dammtp2p.core.model.FileMetadata m) {}
                @Override public void onFileTransferProgress(dammtp2p.core.model.FileMetadata m, long tr, long tot, double sp) {}
                @Override public void onFileTransferStateChanged(dammtp2p.core.model.FileMetadata m, dammtp2p.core.model.TransferState st, String msg) {}
            });

            bob.addListener(new ClientCore.ClientListener() {
                @Override public void onConnected(String serverIp, int p) {}
                @Override public void onDisconnected(String reason) {}
                @Override public void onLoginSuccess(String user, List<String> online, List<String> rooms) {
                    bobLoginLatch.countDown();
                }
                @Override public void onLoginFailed(String reason) {}
                @Override public void onOnlineUsersUpdated(List<String> users) {}
                @Override public void onRoomsUpdated(List<String> rooms) {}
                @Override public void onChatMessage(ChatMessage message) {}
                @Override public void onIncomingFileRequest(dammtp2p.core.model.FileMetadata m) {}
                @Override public void onFileTransferProgress(dammtp2p.core.model.FileMetadata m, long tr, long tot, double sp) {}
                @Override public void onFileTransferStateChanged(dammtp2p.core.model.FileMetadata m, dammtp2p.core.model.TransferState st, String msg) {}
            });

            alice.login("alice", "password123", 5000L);
            bob.login("bob", "password123", 5000L);

            if (!aliceLoginLatch.await(3000, TimeUnit.MILLISECONDS)) return fail("Alice failed to login");
            if (!bobLoginLatch.await(3000, TimeUnit.MILLISECONDS)) return fail("Bob failed to login");

            // 1. Test friend notification via socket
            CountDownLatch bobFriendNotifyLatch = new CountDownLatch(1);
            AtomicReference<String> notifyMsgRef = new AtomicReference<>();

            bob.addListener(new ClientCore.ClientListener() {
                @Override public void onConnected(String s, int p) {}
                @Override public void onDisconnected(String r) {}
                @Override public void onLoginSuccess(String u, List<String> onl, List<String> r) {}
                @Override public void onLoginFailed(String r) {}
                @Override public void onOnlineUsersUpdated(List<String> u) {}
                @Override public void onRoomsUpdated(List<String> r) {}
                @Override public void onChatMessage(ChatMessage m) {}
                @Override public void onIncomingFileRequest(dammtp2p.core.model.FileMetadata m) {}
                @Override public void onFileTransferProgress(dammtp2p.core.model.FileMetadata m, long tr, long tot, double sp) {}
                @Override public void onFileTransferStateChanged(dammtp2p.core.model.FileMetadata m, dammtp2p.core.model.TransferState st, String msg) {}
                @Override public void onFriendStatusChanged(String target, boolean success, String message) {
                    if (success) {
                        notifyMsgRef.set(message);
                        bobFriendNotifyLatch.countDown();
                    }
                }
            });

            alice.requestAddFriend("bob");
            boolean gotFriendNotify = bobFriendNotifyLatch.await(3000, TimeUnit.MILLISECONDS);
            if (!gotFriendNotify) return fail("Bob did not receive real-time friend notification packet");

            // 2. Test 1-to-1 encrypted private chat
            CountDownLatch bobChatLatch = new CountDownLatch(1);
            AtomicReference<String> receivedText = new AtomicReference<>();
            AtomicBoolean isMsgEncrypted = new AtomicBoolean(false);

            bob.addListener(new ClientCore.ClientListener() {
                @Override public void onConnected(String s, int p) {}
                @Override public void onDisconnected(String r) {}
                @Override public void onLoginSuccess(String u, List<String> onl, List<String> r) {}
                @Override public void onLoginFailed(String r) {}
                @Override public void onOnlineUsersUpdated(List<String> u) {}
                @Override public void onRoomsUpdated(List<String> r) {}
                @Override public void onChatMessage(ChatMessage message) {
                    if ("alice".equalsIgnoreCase(message.getSender()) && message.isPrivateMessage()) {
                        receivedText.set(message.getContent());
                        isMsgEncrypted.set(message.isEncrypted());
                        bobChatLatch.countDown();
                    }
                }
                @Override public void onIncomingFileRequest(dammtp2p.core.model.FileMetadata m) {}
                @Override public void onFileTransferProgress(dammtp2p.core.model.FileMetadata m, long tr, long tot, double sp) {}
                @Override public void onFileTransferStateChanged(dammtp2p.core.model.FileMetadata m, dammtp2p.core.model.TransferState st, String msg) {}
            });

            String secretMessage = "Xin chao Bob! Day la tin nhan rieng tu duoc ma hoa AES-128-GCM.";
            alice.sendPrivateMessage("bob", secretMessage);

            boolean gotChat = bobChatLatch.await(3000, TimeUnit.MILLISECONDS);
            if (!gotChat) return fail("Bob did not receive private message");
            if (!secretMessage.equals(receivedText.get())) {
                return fail("Message decrypted content mismatch: " + receivedText.get());
            }
            if (!isMsgEncrypted.get()) {
                return fail("Message was not marked as encrypted");
            }

            System.out.println("PASS (Friend notify + AES-128-GCM private message verified)");
            return true;
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        } finally {
            if (alice != null) alice.disconnect("Test finished");
            if (bob != null) bob.disconnect("Test finished");
            if (server != null) server.stop();
        }
    }

    private static boolean fail(String reason) {
        System.out.println("FAILED (" + reason + ")");
        return false;
    }
}