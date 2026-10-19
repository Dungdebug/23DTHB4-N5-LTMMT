package dammtp2p.util;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Utility class for inspecting local network interfaces and identifying
 * usable IPv4 addresses, with specialized heuristics for ZeroTier virtual adapters.
 *
 * <p>ZeroTier Identification Approach:
 * <ol>
 *   <li>Only active, non-loopback network interfaces ({@code isUp() && !isLoopback()}) are inspected.</li>
 *   <li>Only valid IPv4 addresses are considered, strictly excluding loopback ({@code 127.0.0.1}),
 *       unbound ({@code 0.0.0.0}), and link-local autoconfiguration addresses ({@code 169.254.0.0/16}).</li>
 *   <li>ZeroTier virtual adapters are heuristically identified across OS platforms (Windows, Linux, macOS)
 *       by checking if the interface name or display name matches known ZeroTier patterns
 *       (e.g., "zerotier", "zt", "zt0", "ztnet").</li>
 *   <li>If an interface matches ZeroTier naming and possesses a valid assigned IPv4 address
 *       (typically 10.x.x.x, 172.x.x.x, or 192.168.x.x), it is prioritized as a primary ZeroTier candidate.</li>
 *   <li>If no adapter explicitly matches the ZeroTier naming convention, a prioritized list of all usable
 *       private/site-local IPv4 candidates is returned as fallbacks, allowing the user to select their IP.</li>
 * </ol>
 */
public final class NetworkUtils {

    private NetworkUtils() {
        // Prevent instantiation
    }

    /**
     * Represents a candidate local IPv4 address discovered on the system.
     */
    public static class NetworkCandidate {
        private final String interfaceName;
        private final String displayName;
        private final String ipAddress;
        private final boolean zeroTierLikely;
        private final boolean siteLocal;

        public NetworkCandidate(String interfaceName, String displayName, String ipAddress,
                                boolean zeroTierLikely, boolean siteLocal) {
            this.interfaceName = interfaceName;
            this.displayName = displayName;
            this.ipAddress = ipAddress;
            this.zeroTierLikely = zeroTierLikely;
            this.siteLocal = siteLocal;
        }

        public String getInterfaceName() {
            return interfaceName;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getIpAddress() {
            return ipAddress;
        }

        public boolean isZeroTierLikely() {
            return zeroTierLikely;
        }

        public boolean isSiteLocal() {
            return siteLocal;
        }

        @Override
        public String toString() {
            return String.format("[%s] %s (%s)%s",
                    ipAddress,
                    displayName,
                    interfaceName,
                    zeroTierLikely ? " -> [ZeroTier Detected]" : (siteLocal ? " -> [Private LAN]" : ""));
        }
    }

    /**
     * Inspects all active network interfaces and returns a prioritized list of candidate IPv4 addresses.
     * ZeroTier candidates are placed at the beginning of the list, followed by standard LAN addresses.
     *
     * @return List of discovered network candidates (never null, may be empty)
     */
    public static List<NetworkCandidate> getCandidateAddresses() {
        List<NetworkCandidate> candidates = new ArrayList<>();

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return candidates;
            }

            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                try {
                    if (!iface.isUp() || iface.isLoopback()) {
                        continue;
                    }
                } catch (SocketException ignored) {
                    // Ignore interfaces that cannot report status
                    continue;
                }

                String name = iface.getName() != null ? iface.getName() : "";
                String displayName = iface.getDisplayName() != null ? iface.getDisplayName() : "";
                boolean matchesZeroTierName = isZeroTierAdapterName(name, displayName);

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();

                    if (isValidCandidateIpv4(addr)) {
                        boolean isSiteLocal = addr.isSiteLocalAddress();
                        boolean isZeroTier = matchesZeroTierName;

                        candidates.add(new NetworkCandidate(
                                name,
                                displayName,
                                addr.getHostAddress(),
                                isZeroTier,
                                isSiteLocal
                        ));
                    }
                }
            }
        } catch (SocketException e) {
            System.err.println("Warning: Unable to enumerate network interfaces: " + e.getMessage());
        }

        // Sort candidates: ZeroTier candidates first, then site-local, then others
        candidates.sort((c1, c2) -> {
            if (c1.isZeroTierLikely() != c2.isZeroTierLikely()) {
                return c1.isZeroTierLikely() ? -1 : 1;
            }
            if (c1.isSiteLocal() != c2.isSiteLocal()) {
                return c1.isSiteLocal() ? -1 : 1;
            }
            return c1.getIpAddress().compareTo(c2.getIpAddress());
        });

        return Collections.unmodifiableList(candidates);
    }

    /**
     * Attempts to find the primary ZeroTier IPv4 candidate.
     *
     * @return Optional containing the best ZeroTier candidate, or empty if none found
     */
    public static Optional<NetworkCandidate> findPrimaryZeroTierAddress() {
        return getCandidateAddresses().stream()
                .filter(NetworkCandidate::isZeroTierLikely)
                .findFirst();
    }

    /**
     * Returns a list of all usable candidate IPv4 address strings.
     *
     * @return List of IP address strings
     */
    public static List<String> getUsableIpv4Addresses() {
        List<NetworkCandidate> candidates = getCandidateAddresses();
        List<String> ips = new ArrayList<>(candidates.size());
        for (NetworkCandidate candidate : candidates) {
            ips.add(candidate.getIpAddress());
        }
        return Collections.unmodifiableList(ips);
    }

    /**
     * Checks if an interface name or display name matches common ZeroTier patterns across OSes.
     */
    private static boolean isZeroTierAdapterName(String name, String displayName) {
        String lowerName = name.toLowerCase(Locale.ROOT);
        String lowerDisplay = displayName.toLowerCase(Locale.ROOT);

        // Windows display name often contains "ZeroTier One"
        // Linux/macOS interface names commonly start with "zt" or contain "zerotier"
        return lowerName.contains("zerotier")
                || lowerDisplay.contains("zerotier")
                || lowerName.startsWith("zt")
                || lowerDisplay.startsWith("zt");
    }

    /**
     * Validates whether an address is a usable IPv4 address (non-loopback, non-link-local, non-wildcard).
     */
    private static boolean isValidCandidateIpv4(InetAddress addr) {
        if (!(addr instanceof Inet4Address)) {
            return false;
        }
        if (addr.isLoopbackAddress() || addr.isAnyLocalAddress()) {
            return false;
        }
        // Link-local addresses (169.254.x.x) are unconfigured/APIPA, unsuitable for ZeroTier P2P
        if (addr.isLinkLocalAddress()) {
            return false;
        }
        return true;
    }
}