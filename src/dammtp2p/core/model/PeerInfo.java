package dammtp2p.core.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Encapsulates identity and network endpoint details of a P2P node.
 * Since DAMMTP2P is purely serverless, each peer maintains its own PeerInfo
 * and exchanges it during connection handshake.
 */
public class PeerInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String ipAddress;
    private final int controlPort;
    private final int filePort;
    private final String displayName;

    public PeerInfo(String ipAddress, int controlPort, int filePort, String displayName) {
        this.ipAddress = Objects.requireNonNull(ipAddress, "ipAddress must not be null");
        this.controlPort = controlPort;
        this.filePort = filePort;
        this.displayName = displayName != null && !displayName.trim().isEmpty() ? displayName.trim() : "Peer-" + ipAddress;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public int getControlPort() {
        return controlPort;
    }

    public int getFilePort() {
        return filePort;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PeerInfo peerInfo = (PeerInfo) o;
        return controlPort == peerInfo.controlPort &&
                filePort == peerInfo.filePort &&
                ipAddress.equals(peerInfo.ipAddress);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ipAddress, controlPort, filePort);
    }

    @Override
    public String toString() {
        return String.format("%s (%s:%d [Control] / %d [File])",
                displayName, ipAddress, controlPort, filePort);
    }
}
