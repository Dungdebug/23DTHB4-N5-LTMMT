package dammtp2p.core.network;

import java.io.IOException;

/**
 * Thrown when a remote peer sends an incompatible protocol version or unknown application identity.
 */
public class IncompatibleProtocolException extends IOException {

    private static final long serialVersionUID = 1L;

    public IncompatibleProtocolException(String message) {
        super(message);
    }

    public IncompatibleProtocolException(String message, Throwable cause) {
        super(message, cause);
    }
}