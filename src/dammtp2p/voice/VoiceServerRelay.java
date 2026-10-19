package dammtp2p.voice;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Server-side UDP Voice Relay listening on Port 8890.
 *
 * <p>Receives low-latency VoIP packets from clients, tracks room membership,
 * and relays audio packets to all other participants in the same voice room.
 */
public class VoiceServerRelay {

    public static class VoiceEndpoint {
        private final String username;
        private final InetAddress address;
        private final int port;
        private volatile long lastSeen;

        public VoiceEndpoint(String username, InetAddress address, int port) {
            this.username = username;
            this.address = address;
            this.port = port;
            this.lastSeen = System.currentTimeMillis();
        }

        public String getUsername() {
            return username;
        }

        public InetAddress getAddress() {
            return address;
        }

        public int getPort() {
            return port;
        }

        public long getLastSeen() {
            return lastSeen;
        }

        public void touch() {
            this.lastSeen = System.currentTimeMillis();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            VoiceEndpoint that = (VoiceEndpoint) o;
            return port == that.port && Objects.equals(address, that.address);
        }

        @Override
        public int hashCode() {
            return Objects.hash(address, port);
        }

        @Override
        public String toString() {
            return username + "@" + address.getHostAddress() + ":" + port;
        }
    }

    private final int port;
    private DatagramSocket udpSocket;
    private final ExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    // roomId -> Set of VoiceEndpoints
    private final Map<String, Set<VoiceEndpoint>> roomEndpoints = new ConcurrentHashMap<>();

    public VoiceServerRelay(int port) {
        this.port = port;
        this.executor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "voice-relay-worker");
            t.setDaemon(true);
            return t;
        });
    }

    public VoiceServerRelay() {
        this(VoiceConstants.DEFAULT_VOICE_PORT);
    }

    public synchronized void start() throws SocketException {
        if (running.get()) return;

        udpSocket = new DatagramSocket(port);
        udpSocket.setReuseAddress(true);
        running.set(true);

        executor.submit(this::receiveLoop);
        executor.submit(this::cleanerLoop);
    }

    public synchronized void stop() {
        if (!running.compareAndSet(true, false)) return;
        if (udpSocket != null && !udpSocket.isClosed()) {
            udpSocket.close();
        }
        roomEndpoints.clear();
        executor.shutdownNow();
    }

    private void receiveLoop() {
        byte[] buffer = new byte[2048];
        while (running.get() && udpSocket != null && !udpSocket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                udpSocket.receive(packet);

                VoicePacket voicePacket;
                try {
                    voicePacket = VoicePacket.fromBytes(packet.getData(), packet.getOffset(), packet.getLength());
                } catch (Exception e) {
                    continue; // Skip malformed packet
                }

                handlePacket(voicePacket, packet.getAddress(), packet.getPort());
            } catch (SocketException e) {
                if (!running.get()) break;
            } catch (IOException e) {
                if (!running.get()) break;
            }
        }
    }

    private void handlePacket(VoicePacket packet, InetAddress remoteAddr, int remotePort) {
        String roomId = packet.getRoomId();
        if (roomId == null || roomId.trim().isEmpty()) return;
        String cleanRoom = roomId.trim().toLowerCase();

        Set<VoiceEndpoint> endpoints = roomEndpoints.computeIfAbsent(cleanRoom, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()));
        VoiceEndpoint senderEndpoint = new VoiceEndpoint(packet.getSender(), remoteAddr, remotePort);

        switch (packet.getPacketType()) {
            case VoiceConstants.TYPE_JOIN:
                endpoints.add(senderEndpoint);
                senderEndpoint.touch();
                break;

            case VoiceConstants.TYPE_LEAVE:
                endpoints.remove(senderEndpoint);
                break;

            case VoiceConstants.TYPE_PING:
                for (VoiceEndpoint ep : endpoints) {
                    if (ep.equals(senderEndpoint)) {
                        ep.touch();
                        break;
                    }
                }
                break;

            case VoiceConstants.TYPE_AUDIO:
            case VoiceConstants.TYPE_SPEAKING:
                // Ensure sender endpoint is registered and touched
                boolean found = false;
                for (VoiceEndpoint ep : endpoints) {
                    if (ep.equals(senderEndpoint)) {
                        ep.touch();
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    endpoints.add(senderEndpoint);
                }

                // Relay audio or speaking indicator to all other participants in the room
                byte[] rawPacketBytes = packet.toBytes();
                for (VoiceEndpoint ep : endpoints) {
                    // Do not echo packet back to the sender
                    if (ep.getPort() == remotePort && ep.getAddress().equals(remoteAddr)) {
                        continue;
                    }
                    try {
                        DatagramPacket forward = new DatagramPacket(
                                rawPacketBytes, rawPacketBytes.length, ep.getAddress(), ep.getPort());
                        udpSocket.send(forward);
                    } catch (IOException ignored) {}
                }
                break;

            default:
                break;
        }
    }

    private void cleanerLoop() {
        while (running.get()) {
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                break;
            }
            long now = System.currentTimeMillis();
            for (Map.Entry<String, Set<VoiceEndpoint>> entry : roomEndpoints.entrySet()) {
                Set<VoiceEndpoint> set = entry.getValue();
                for (Iterator<VoiceEndpoint> it = set.iterator(); it.hasNext(); ) {
                    VoiceEndpoint ep = it.next();
                    if (now - ep.getLastSeen() > VoiceConstants.ENDPOINT_TIMEOUT_MS) {
                        it.remove();
                    }
                }
            }
        }
    }

    public List<String> getRoomParticipants(String roomId) {
        if (roomId == null) return Collections.emptyList();
        Set<VoiceEndpoint> set = roomEndpoints.get(roomId.trim().toLowerCase());
        if (set == null) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        for (VoiceEndpoint ep : set) {
            result.add(ep.getUsername());
        }
        return result;
    }

    public int getTotalVoiceUsers() {
        int count = 0;
        for (Set<VoiceEndpoint> set : roomEndpoints.values()) {
            count += set.size();
        }
        return count;
    }

    public int getPort() {
        return port;
    }

    public boolean isRunning() {
        return running.get();
    }
}
