package dammtp2p.ui.components;

import dammtp2p.core.model.PeerInfo;
import dammtp2p.core.network.ConnectionState;
import dammtp2p.core.network.DefaultConnectionManager;
import dammtp2p.core.network.NetworkStateListener;
import dammtp2p.ui.theme.AppIcons;
import dammtp2p.ui.theme.ThemeManager;
import dammtp2p.util.Constants;
import dammtp2p.util.NetworkUtils;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Dedicated panel for managing ZeroTier P2P connection, displaying local IP,
 * entering peer IP/Port, and visualizing connection state.
 */
public class ConnectionPanel extends JPanel implements NetworkStateListener {

    private final DefaultConnectionManager connectionManager;

    private JLabel lblLocalIp;
    private JLabel lblNetworkName;
    private JButton btnCopyIp;

    private JTextField txtRemoteIp;
    private JTextField txtRemotePort;
    private JButton btnConnect;
    private JButton btnDisconnect;

    private JLabel lblStatusDot;
    private JLabel lblStatusText;
    private JLabel lblErrorInfo;

    public ConnectionPanel(DefaultConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
        this.connectionManager.addNetworkStateListener(this);

        initUI();
        refreshNetworkInfo();
    }

    private void initUI() {
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(ThemeManager.BORDER_COLOR, 1),
                        "Kết Nối Ngang Hàng (ZeroTier P2P)",
                        0, 0, ThemeManager.FONT_BODY_BOLD,
                        ThemeManager.isDarkMode() ? ThemeManager.TEXT_PRIMARY : Color.DARK_GRAY
                ),
                BorderFactory.createEmptyBorder(6, 10, 8, 10)
        ));

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

        // 1. Local ZeroTier IP Section Card
        JPanel localCard = new JPanel(new BorderLayout(6, 4));
        localCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));
        localCard.setBackground(ThemeManager.isDarkMode() ? ThemeManager.BG_INPUT : new Color(245, 247, 250));

        JPanel localTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        localTop.setOpaque(false);
        JLabel lblLocalTitle = new JLabel("IP ZeroTier của bạn: ");
        lblLocalTitle.setFont(ThemeManager.FONT_SMALL);
        lblLocalTitle.setForeground(ThemeManager.TEXT_SECONDARY);
        lblLocalTopAdd(localTop, lblLocalTitle);

        lblLocalIp = new JLabel("10.x.x.x");
        lblLocalIp.setFont(ThemeManager.FONT_BODY_BOLD);
        lblLocalIp.setForeground(ThemeManager.ACCENT_PRIMARY);
        localTop.add(lblLocalIp);

        btnCopyIp = new JButton("SAO CHÉP (COPY)");
        btnCopyIp.setFont(ThemeManager.FONT_SMALL);
        btnCopyIp.setIcon(AppIcons.createCopyIcon(12, ThemeManager.TEXT_SECONDARY));
        btnCopyIp.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnCopyIp.setFocusPainted(false);
        btnCopyIp.addActionListener(e -> copyLocalIpToClipboard());

        localCard.add(localTop, BorderLayout.CENTER);
        localCard.add(btnCopyIp, BorderLayout.EAST);

        lblNetworkName = new JLabel("Giao diện: Đang phát hiện...");
        lblNetworkName.setFont(ThemeManager.FONT_SMALL);
        lblNetworkName.setForeground(ThemeManager.TEXT_MUTED);
        localCard.add(lblNetworkName, BorderLayout.SOUTH);

        content.add(localCard);
        content.add(Box.createVerticalStrut(8));

        // 2. Peer IP & Port Inputs Grid
        JPanel inputGrid = new JPanel(new GridBagLayout());
        inputGrid.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(2, 2, 4, 2);

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.7;
        JLabel lblPeerIpTitle = new JLabel("Địa chỉ IP bạn bè (Peer IP):");
        lblPeerIpTitle.setFont(ThemeManager.FONT_SMALL);
        lblPeerIpTitle.setForeground(ThemeManager.TEXT_SECONDARY);
        inputGrid.add(lblPeerIpTitle, gbc);

        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.3;
        JLabel lblPortTitle = new JLabel("Cổng (Port):");
        lblPortTitle.setFont(ThemeManager.FONT_SMALL);
        lblPortTitle.setForeground(ThemeManager.TEXT_SECONDARY);
        inputGrid.add(lblPortTitle, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        txtRemoteIp = new JTextField(12);
        txtRemoteIp.setFont(ThemeManager.FONT_MONO);
        txtRemoteIp.setToolTipText("Nhập địa chỉ IP ZeroTier của máy tính bạn bè (ví dụ: 10.147.20.3)");
        inputGrid.add(txtRemoteIp, gbc);

        gbc.gridx = 1; gbc.gridy = 1;
        txtRemotePort = new JTextField(String.valueOf(Constants.DEFAULT_CONTROL_PORT), 4);
        txtRemotePort.setFont(ThemeManager.FONT_MONO);
        inputGrid.add(txtRemotePort, gbc);

        content.add(inputGrid);
        content.add(Box.createVerticalStrut(6));

        // 3. Action Buttons [ KẾT NỐI ] [ NGẮT ]
        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        btnBar.setOpaque(false);

        btnConnect = new JButton("KẾT NỐI (CONNECT)");
        btnConnect.setFont(ThemeManager.FONT_BODY_BOLD);
        btnConnect.setBackground(ThemeManager.ACCENT_PRIMARY);
        btnConnect.setForeground(Color.WHITE);
        btnConnect.setFocusPainted(false);
        btnConnect.setPreferredSize(new Dimension(145, 32));
        btnConnect.addActionListener(e -> onConnect());

        btnDisconnect = new JButton("NGẮT (DISCONNECT)");
        btnDisconnect.setFont(ThemeManager.FONT_BODY);
        btnDisconnect.setBackground(ThemeManager.isDarkMode() ? ThemeManager.BG_INPUT : Color.LIGHT_GRAY);
        btnDisconnect.setForeground(ThemeManager.TEXT_PRIMARY);
        btnDisconnect.setFocusPainted(false);
        btnDisconnect.setEnabled(false);
        btnDisconnect.setPreferredSize(new Dimension(145, 32));
        btnDisconnect.addActionListener(e -> onDisconnect());

        btnBar.add(btnConnect);
        btnBar.add(btnDisconnect);
        content.add(btnBar);
        content.add(Box.createVerticalStrut(8));

        // 4. Status Indicator Card
        JPanel statusCard = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        statusCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)
        ));
        statusCard.setBackground(ThemeManager.isDarkMode() ? ThemeManager.BG_INPUT : new Color(248, 249, 250));

        lblStatusDot = new JLabel(AppIcons.createStatusDot(10, ThemeManager.DANGER));
        lblStatusText = new JLabel("Chưa kết nối");
        lblStatusText.setFont(ThemeManager.FONT_BODY_BOLD);
        lblStatusText.setForeground(ThemeManager.DANGER);

        statusCard.add(lblStatusDot);
        statusCard.add(lblStatusText);
        content.add(statusCard);

        // 5. Error helper label
        lblErrorInfo = new JLabel(" ");
        lblErrorInfo.setFont(ThemeManager.FONT_SMALL);
        lblErrorInfo.setForeground(ThemeManager.DANGER);
        lblErrorInfo.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(lblErrorInfo);

        add(content, BorderLayout.NORTH);
    }

    private void lblLocalTopAdd(JPanel p, JLabel l) {
        p.add(l);
    }

    public void refreshNetworkInfo() {
        Optional<NetworkUtils.NetworkCandidate> zt = NetworkUtils.findPrimaryZeroTierAddress();
        if (zt.isPresent()) {
            lblLocalIp.setText(zt.get().getIpAddress());
            lblNetworkName.setText("Mạng: " + zt.get().getDisplayName() + " (Đã kết nối ZeroTier)");
            txtRemoteIp.setText(zt.get().getIpAddress()); // Helpful default
        } else {
            lblLocalIp.setText("127.0.0.1");
            lblNetworkName.setText("Mạng: Cục bộ / Chưa phát hiện ZeroTier");
            txtRemoteIp.setText("127.0.0.1");
        }
    }

    private void copyLocalIpToClipboard() {
        String ip = lblLocalIp.getText();
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(ip), null);
        btnCopyIp.setText("ĐÃ SAO CHÉP!");
        Timer t = new Timer(1500, e -> btnCopyIp.setText("SAO CHÉP (COPY)"));
        t.setRepeats(false);
        t.start();
    }

    private void onConnect() {
        String ip = txtRemoteIp.getText().trim();
        String portStr = txtRemotePort.getText().trim();
        lblErrorInfo.setText(" ");

        if (ip.isEmpty()) {
            lblErrorInfo.setText("Vui lòng nhập IP ZeroTier của bạn bè.");
            txtRemoteIp.requestFocusInWindow();
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException ex) {
            lblErrorInfo.setText("Cổng kết nối không hợp lệ (1-65535).");
            txtRemotePort.requestFocusInWindow();
            return;
        }

        try {
            connectionManager.connectToPeer(ip, port);
        } catch (Exception ex) {
            lblErrorInfo.setText("Lỗi: " + ex.getMessage());
        }
    }

    private void onDisconnect() {
        connectionManager.disconnectPeer();
    }

    // === NetworkStateListener Callbacks ===

    @Override
    public void onListeningStarted(int boundPort) {}

    @Override
    public void onListeningStopped() {}

    @Override
    public void onConnecting(String remoteIp, int port) {
        SwingUtilities.invokeLater(() -> {
            lblStatusDot.setIcon(AppIcons.createStatusDot(10, ThemeManager.WARNING));
            lblStatusText.setText("Đang kết nối tới " + remoteIp + "...");
            lblStatusText.setForeground(ThemeManager.WARNING);
            btnConnect.setEnabled(false);
            btnDisconnect.setEnabled(true);
            lblErrorInfo.setText(" ");
        });
    }

    @Override
    public void onConnected(PeerInfo peer) {
        SwingUtilities.invokeLater(() -> {
            lblStatusDot.setIcon(AppIcons.createStatusDot(10, ThemeManager.SUCCESS));
            lblStatusText.setText("Đã kết nối: " + peer.getDisplayName());
            lblStatusText.setForeground(ThemeManager.SUCCESS);
            btnConnect.setEnabled(false);
            btnDisconnect.setEnabled(true);
            txtRemoteIp.setEnabled(false);
            txtRemotePort.setEnabled(false);
            lblErrorInfo.setText(" ");
        });
    }

    @Override
    public void onConnectionFailed(String remoteIp, int port, String reason) {
        SwingUtilities.invokeLater(() -> {
            lblStatusDot.setIcon(AppIcons.createStatusDot(10, ThemeManager.DANGER));
            lblStatusText.setText("Kết nối thất bại");
            lblStatusText.setForeground(ThemeManager.DANGER);
            btnConnect.setEnabled(true);
            btnDisconnect.setEnabled(false);
            txtRemoteIp.setEnabled(true);
            txtRemotePort.setEnabled(true);

            // Friendly human explanation instead of stack traces
            if (reason != null && (reason.contains("refused") || reason.contains("timed out") || reason.contains("unreachable"))) {
                lblErrorInfo.setText("Không thể kết nối. Hãy kiểm tra IP và ứng dụng bạn bè.");
            } else {
                lblErrorInfo.setText(reason != null ? reason : "Không thể kết nối đến máy tính bạn bè.");
            }
        });
    }

    @Override
    public void onDisconnected(PeerInfo peer, String reason) {
        SwingUtilities.invokeLater(() -> {
            lblStatusDot.setIcon(AppIcons.createStatusDot(10, ThemeManager.DANGER));
            lblStatusText.setText("Chưa kết nối");
            lblStatusText.setForeground(ThemeManager.DANGER);
            btnConnect.setEnabled(true);
            btnDisconnect.setEnabled(false);
            txtRemoteIp.setEnabled(true);
            txtRemotePort.setEnabled(true);
            lblErrorInfo.setText(" ");
        });
    }

    @Override
    public void onError(Throwable cause) {
        SwingUtilities.invokeLater(() -> {
            lblErrorInfo.setText("Lỗi mạng: " + (cause.getMessage() != null ? cause.getMessage() : "Unknown"));
        });
    }
}
