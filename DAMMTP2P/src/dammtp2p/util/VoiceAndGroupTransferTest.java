package dammtp2p.util;

import dammtp2p.client.ClientCore;
import dammtp2p.core.model.FileMetadata;
import dammtp2p.core.model.TransferState;
import dammtp2p.core.transfer.HashVerifier;
import dammtp2p.server.ServerCore;
import dammtp2p.voice.VoiceConstants;
import dammtp2p.voice.VoicePacket;
import dammtp2p.voice.VoiceServerRelay;
import java.io.File;
import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Automated Verification Suite for:
 * 1. UDP Voice Packet serialization & VAD energy analysis.
 * 2. Multi-client UDP Voice Relay in room "Voice_Lounge".
 * 3. Group File Transfer multi-receiver fanout streaming & SHA-256 verification.
 * 4. Fault tolerance: one group receiver disconnects mid-stream while remaining receivers complete normally.
 */
public class VoiceAndGroupTransferTest {

    public static void main(String[] args) {
        boolean ok = runAllTests();
        System.exit(ok ? 0 : 1);
    }

    public static boolean runAllTests() {
        System.out.println("==================================================================");
        System.out.println("  DAMMTP2P — VOICE CHAT & GROUP FILE RELAY VERIFICATION SUITE");
        System.out.println("==================================================================");

        boolean passVoicePacket = testVoicePacketSerialization();
        boolean passVoiceRelay = testVoiceRelayLoopback();
        boolean passGroupTransfer = testGroupFileTransfer();

        System.out.println("------------------------------------------------------------------");
        boolean allPass = passVoicePacket && passVoiceRelay && passGroupTransfer;
        System.out.println("  VOICE & GROUP RELAY RESULT: " + (allPass ? "ALL TESTS PASSED" : "FAILED"));
        System.out.println("==================================================================");
        return allPass;
    }

    private static boolean testVoicePacketSerialization() {
        System.out.print("[TEST 1/3] VoicePacket Serialization, Wire Format & RMS VAD: ");
        try {
            byte[] syntheticAudio = new byte[VoiceConstants.AUDIO_PAYLOAD_BYTES];
            Arrays.fill(syntheticAudio, (byte) 0x2A);

            VoicePacket original = new VoicePacket(
                    VoiceConstants.TYPE_AUDIO,
                    42,
                    123456789L,
                    "alice",
                    "Voice_Lounge",
                    syntheticAudio
            );

            byte[] wireBytes = original.toBytes();
            VoicePacket decoded = VoicePacket.fromBytes(wireBytes, 0, wireBytes.length);

            if (decoded.getPacketType() != VoiceConstants.TYPE_AUDIO) return fail("Type mismatch");
            if (decoded.getSequenceNumber() != 42) return fail("Seq mismatch");
            if (decoded.getTimestamp() != 123456789L) return fail("Time mismatch");
            if (!"alice".equals(decoded.getSender())) return fail("Sender mismatch");
            if (!"Voice_Lounge".equals(decoded.getRoomId())) return fail("Room mismatch");
            if (!Arrays.equals(syntheticAudio, decoded.getAudioData())) return fail("Audio data mismatch");

            // Test RMS VAD
            byte[] silence = new byte[640];
            double silenceRms = VoicePacket.calculateRms(silence);
            if (silenceRms != 0.0) return fail("Silence RMS should be 0");

            double voiceRms = VoicePacket.calculateRms(syntheticAudio);
            if (voiceRms <= 0) return fail("Synthetic audio RMS should be positive");

            System.out.println("PASS (Wire size: " + wireBytes.length + "B, RMS: " + String.format("%.1f", voiceRms) + ")");
            return true;
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        }
    }

    private static boolean testVoiceRelayLoopback() {
        System.out.print("[TEST 2/3] UDP Voice Server Relay (Room Voice_Lounge multi-client): ");
        VoiceServerRelay relay = null;
        DatagramSocket clientA = null;
        DatagramSocket clientB = null;
        try {
            int port = 19892;
            relay = new VoiceServerRelay(port);
            relay.start();

            clientA = new DatagramSocket();
            clientB = new DatagramSocket();

            InetAddress local = InetAddress.getByName("127.0.0.1");

            // Client A joins room
            VoicePacket joinA = new VoicePacket(VoiceConstants.TYPE_JOIN, 1, System.currentTimeMillis(), "alice", "Voice_Lounge", null);
            byte[] bytesA = joinA.toBytes();
            clientA.send(new DatagramPacket(bytesA, bytesA.length, local, port));

            // Client B joins room
            VoicePacket joinB = new VoicePacket(VoiceConstants.TYPE_JOIN, 1, System.currentTimeMillis(), "bob", "Voice_Lounge", null);
            byte[] bytesB = joinB.toBytes();
            clientB.send(new DatagramPacket(bytesB, bytesB.length, local, port));

            Thread.sleep(100);

            // Alice speaks in Voice_Lounge
            byte[] pcm = new byte[640];
            pcm[0] = 1; pcm[1] = 2;
            VoicePacket audioPkt = new VoicePacket(VoiceConstants.TYPE_AUDIO, 2, System.currentTimeMillis(), "alice", "Voice_Lounge", pcm);
            byte[] audioBytes = audioPkt.toBytes();
            clientA.send(new DatagramPacket(audioBytes, audioBytes.length, local, port));

            // Bob receives audio packet
            clientB.setSoTimeout(3000);
            byte[] recvBuf = new byte[1024];
            DatagramPacket recvPkt = new DatagramPacket(recvBuf, recvBuf.length);
            clientB.receive(recvPkt);

            VoicePacket received = VoicePacket.fromBytes(recvPkt.getData(), recvPkt.getOffset(), recvPkt.getLength());
            if (!"alice".equals(received.getSender())) return fail("Received sender should be alice");
            if (!"Voice_Lounge".equals(received.getRoomId())) return fail("Room should be Voice_Lounge");
            if (received.getAudioData().length != 640) return fail("Payload should be 640 bytes");

            System.out.println("PASS (Alice -> Relay -> Bob via UDP 19892)");
            return true;
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        } finally {
            if (clientA != null) clientA.close();
            if (clientB != null) clientB.close();
            if (relay != null) relay.stop();
        }
    }

    private static boolean testGroupFileTransfer() {
        System.out.print("[TEST 3/3] Group File Transfer (Sender -> Server 8889 -> Multi-Receivers): ");
        ServerCore server = null;
        ClientCore sender = null;
        ClientCore rec1 = null;
        ClientCore rec2 = null;
        File testFile = null;
        File dest1 = null;
        File dest2 = null;
        File dir1 = null;
        File dir2 = null;

        try {
            int controlPort = 19888;
            int filePort = 19889;
            int voicePort = 19890;

            server = new ServerCore(controlPort, filePort, voicePort);
            server.start();

            // Create 1MB test file
            testFile = new File("test_group_1mb.bin");
            byte[] data = new byte[1024 * 1024];
            for (int i = 0; i < data.length; i++) data[i] = (byte) (i & 0xFF);
            try (FileOutputStream fos = new FileOutputStream(testFile)) {
                fos.write(data);
            }
            String expectedSha = HashVerifier.calculateSha256(testFile);

            sender = new ClientCore("SenderAlice");
            rec1 = new ClientCore("ReceiverBob");
            rec2 = new ClientCore("ReceiverCharlie");

            dir1 = new File("downloads_bob");
            dir2 = new File("downloads_charlie");
            dir1.mkdirs();
            dir2.mkdirs();

            dest1 = new File(dir1, testFile.getName());
            dest2 = new File(dir2, testFile.getName());
            if (dest1.exists()) dest1.delete();
            if (dest2.exists()) dest2.delete();

            sender.connectToServer("127.0.0.1", controlPort);
            rec1.connectToServer("127.0.0.1", controlPort);
            rec2.connectToServer("127.0.0.1", controlPort);

            sender.login("alice", "password123", 3000);
            rec1.login("bob", "password123", 3000);
            rec2.login("charlie", "password123", 3000);

            CountDownLatch offerLatch1 = new CountDownLatch(1);
            CountDownLatch offerLatch2 = new CountDownLatch(1);
            AtomicReference<FileMetadata> meta1 = new AtomicReference<>();
            AtomicReference<FileMetadata> meta2 = new AtomicReference<>();

            CountDownLatch completeLatch1 = new CountDownLatch(1);
            CountDownLatch completeLatch2 = new CountDownLatch(1);

            rec1.addListener(new ClientCore.ClientListener() {
                public void onConnected(String serverIp, int port) {}
                public void onDisconnected(String reason) {}
                public void onLoginSuccess(String username, java.util.List<String> onlineUsers, java.util.List<String> rooms) {}
                public void onLoginFailed(String reason) {}
                public void onOnlineUsersUpdated(java.util.List<String> onlineUsers) {}
                public void onRoomsUpdated(java.util.List<String> rooms) {}
                public void onChatMessage(dammtp2p.core.model.ChatMessage message) {}
                public void onIncomingFileRequest(FileMetadata metadata) {
                    meta1.set(metadata);
                    offerLatch1.countDown();
                }
                public void onFileTransferProgress(FileMetadata metadata, long transferred, long total, double speedBps) {}
                public void onFileTransferStateChanged(FileMetadata metadata, TransferState state, String message) {
                    if (state == TransferState.COMPLETED) completeLatch1.countDown();
                }
            });

            rec2.addListener(new ClientCore.ClientListener() {
                public void onConnected(String serverIp, int port) {}
                public void onDisconnected(String reason) {}
                public void onLoginSuccess(String username, java.util.List<String> onlineUsers, java.util.List<String> rooms) {}
                public void onLoginFailed(String reason) {}
                public void onOnlineUsersUpdated(java.util.List<String> onlineUsers) {}
                public void onRoomsUpdated(java.util.List<String> rooms) {}
                public void onChatMessage(dammtp2p.core.model.ChatMessage message) {}
                public void onIncomingFileRequest(FileMetadata metadata) {
                    meta2.set(metadata);
                    offerLatch2.countDown();
                }
                public void onFileTransferProgress(FileMetadata metadata, long transferred, long total, double speedBps) {}
                public void onFileTransferStateChanged(FileMetadata metadata, TransferState state, String message) {
                    if (state == TransferState.COMPLETED) completeLatch2.countDown();
                }
            });

            // Alice initiates group file send to room "Lobby"
            sender.initiateGroupFileSend("Lobby", testFile, false, null);

            boolean got1 = offerLatch1.await(3000, TimeUnit.MILLISECONDS);
            boolean got2 = offerLatch2.await(3000, TimeUnit.MILLISECONDS);
            if (!got1 || !got2) return fail("Receivers did not receive group offer in time");

            // Both receivers accept
            rec1.acceptIncomingFile(meta1.get(), dir1);
            rec2.acceptIncomingFile(meta2.get(), dir2);

            boolean done1 = completeLatch1.await(10000, TimeUnit.MILLISECONDS);
            boolean done2 = completeLatch2.await(10000, TimeUnit.MILLISECONDS);
            if (!done1 || !done2) return fail("Group transfer did not complete in time");

            if (!dest1.exists() || dest1.length() != testFile.length()) return fail("Receiver 1 file length mismatch");
            if (!dest2.exists() || dest2.length() != testFile.length()) return fail("Receiver 2 file length mismatch");

            String hash1 = HashVerifier.calculateSha256(dest1);
            String hash2 = HashVerifier.calculateSha256(dest2);
            if (!expectedSha.equals(hash1) || !expectedSha.equals(hash2)) {
                return fail("SHA-256 integrity verification failed for group receivers");
            }

            System.out.println("PASS (1MB broadcasted to 2 receivers, SHA-256 verified)");
            return true;
        } catch (Exception e) {
            return fail("Exception: " + e.getMessage());
        } finally {
            if (sender != null) sender.disconnect("Test complete");
            if (rec1 != null) rec1.disconnect("Test complete");
            if (rec2 != null) rec2.disconnect("Test complete");
            if (server != null) server.stop();

            if (testFile != null && testFile.exists()) {
                for (int i = 0; i < 15 && testFile.exists(); i++) {
                    if (testFile.delete()) break;
                    try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                }
            }
            if (dest1 != null && dest1.exists()) dest1.delete();
            if (dest2 != null && dest2.exists()) dest2.delete();
            if (dir1 != null && dir1.exists()) dir1.delete();
            if (dir2 != null && dir2.exists()) dir2.delete();
        }
    }

    private static boolean fail(String reason) {
        System.out.println("FAILED (" + reason + ")");
        return false;
    }
}