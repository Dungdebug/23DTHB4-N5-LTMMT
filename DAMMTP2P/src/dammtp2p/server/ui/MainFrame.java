package dammtp2p.ui;

import dammtp2p.core.chat.DefaultChatService;
import dammtp2p.core.model.FileMetadata;
import dammtp2p.core.model.PeerInfo;
import dammtp2p.core.model.TransferState;
import dammtp2p.core.network.ConnectionState;
import dammtp2p.core.network.DefaultConnectionManager;
import dammtp2p.core.network.NetworkStateListener;
import dammtp2p.core.transfer.FileTransferListener;
import dammtp2p.core.transfer.FileTransferManager;
import dammtp2p.ui.components.ChatPanel;
import dammtp2p.ui.components.ConnectionPanel;
import dammtp2p.ui.components.FileTransferPanel;
import dammtp2p.ui.components.IncomingFileDialog;
import dammtp2p.ui.components.SettingsDialog;
import dammtp2p.ui.components.ToastNotification;
import dammtp2p.ui.components.TransferHistoryPanel;
import dammtp2p.ui.theme.AppIcons;
import dammtp2p.ui.theme.ThemeManager;
import dammtp2p.util.Constants;
import dammtp2p.util.NetworkUtils;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;

/**
 * Modern, polished desktop application window for DAMMTP2P.
 * Features a streamlined Top Header Bar, Connection Panel, Transfer History,
 * 1-to-1 Chat bubbles, File Transfer dashboard with Drag & Drop, and Toast notifications.
 */
public class MainFrame extends JFrame implements NetworkStateListener, FileTransferListener {

    private final DefaultConnectionManager connectionManager;
    private final DefaultChatService chatService;
    private final FileTransferManager transferManager;

    private JLabel lblHeaderPeerStatus;
    private JLabel lblHeaderZeroTier;
    private JButton btnHeaderCopyZt;
    private JButton btnSettings;
    private JButton btnThemeToggle;

    private ConnectionPanel connectionPanel;
    private TransferHistoryPanel historyPanel;
    private ChatPanel chatPanel;
    private FileTransferPanel fileTransferPanel;

    public MainFrame(DefaultConnectionManager connectionManager,
                     DefaultChatService chatService,
                     FileTransferManager transferManager) {
        super(Constants.APP_NAME + " — P2P Chat & File Transfer");
        this.connectionManager = connectionManager;
        this.chatService = chatService;
        this.transferManager = transferManager;

        this.connectionManager.addNetworkStateListener(this);
        this.transferManager.addTransferListener(this);

        initUI();
    }

    private void initUI() {
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setMinimumSize(new Dimension(880, 640));
        setPreferredSize(new Dimension(1060, 740));
        setLayout(new BorderLayout(0, 0));

        // 1. Top Header Bar
        JPanel topHeader = createTopHeaderBar();
        add(topHeader, BorderLayout.NORTH);

        // 2. Left Sidebar (ConnectionPanel + TransferHistoryPanel)
        connectionPanel = new ConnectionPanel(connectionManager);
        historyPanel = new TransferHistoryPanel();

        JPanel leftSidebar = new JPanel(new BorderLayout(0, 8));
        leftSidebar.setOpaque(false);
        leftSidebar.setPreferredSize(new Dimension(320, 600));
        leftSidebar.setMinimumSize(new Dimension(280, 500));
        leftSidebar.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 4));

        leftSidebar.add(connectionPanel, BorderLayout.NORTH);
        leftSidebar.add(historyPanel, BorderLayout.CENTER);

        // 3. Right Main Area (ChatPanel Top + FileTransferPanel Bottom)
        chatPanel = new ChatPanel(connectionManager, chatService);
        fileTransferPanel = new FileTransferPanel(connectionManager, transferManager);
        fileTransferPanel.setParentFrame(this);
        fileTransferPanel.setHistoryPanel(historyPanel);

        JSplitPane rightSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, chatPanel, fileTransferPanel);
        rightSplit.setResizeWeight(0.50);
        rightSplit.setContinuousLayout(true);
        rightSplit.setBorder(BorderFactory.createEmptyBorder(8, 4, 8, 12));

        // 4. Main Split Pane (Left Sidebar | Right Content)
        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftSidebar, rightSplit);
        mainSplit.setResizeWeight(0.30);
        mainSplit.setContinuousLayout(true);
        mainSplit.setBorder(BorderFactory.createEmptyBorder());
        add(mainSplit, BorderLayout.CENTER);

        // 5. Window close confirmation
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                handleWindowClose();
            }
        });

        pack();
        setLocationRelativeTo(null);
    }

    private JPanel createTopHeaderBar() {
        JPanel header = new JPanel(new BorderLayout(10, 0));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ThemeManager.BORDER_COLOR),
                BorderFactory.createEmptyBorder(10, 16, 10, 16)
        ));
        header.setBackground(ThemeManager.isDarkMode() ? ThemeManager.BG_DARK : new Color(245, 247, 250));

        // Left: Brand Logo & Title
        JPanel brandPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        brandPanel.setOpaque(false);

        JLabel lblLogo = new JLabel(AppIcons.createNetworkIconSafe(22, ThemeManager.ACCENT_PRIMARY));
        brandPanel.add(lblLogo);

        JLabel lblTitle = new JLabel("DAMMTP2P");
        lblTitle.setFont(ThemeManager.FONT_TITLE);
        lblTitle.setForeground(ThemeManager.TEXT_PRIMARY);
        brandPanel.add(lblTitle);

        JLabel lblSub = new JLabel("• P2P Chat & File Transfer");
        lblSub.setFont(ThemeManager.FONT_SMALL);
        lblSub.setForeground(ThemeManager.TEXT_MUTED);
        brandPanel.add(lblSub);

        header.add(brandPanel, BorderLayout.WEST);

        // Center: ZeroTier Quick Info Badge
        JPanel centerBadge = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        centerBadge.setOpaque(false);

        Optional<NetworkUtils.NetworkCandidate> zt = NetworkUtils.findPrimaryZeroTierAddress();
        String ztIp = zt.map(NetworkUtils.NetworkCandidate::getIpAddress).orElse("127.0.0.1");

        lblHeaderZeroTier = new JLabel("ZeroTier: " + ztIp);
        lblHeaderZeroTier.setFont(ThemeManager.FONT_BODY_BOLD);
        lblHeaderZeroTier.setForeground(ThemeManager.ACCENT_PRIMARY);
        centerBadge.add(lblHeaderZeroTier);

        btnHeaderCopyZt = new JButton("Copy IP");
        btnHeaderCopyZt.setFont(ThemeManager.FONT_SMALL);
        btnHeaderCopyZt.setIcon(AppIcons.createCopyIcon(11, ThemeManager.TEXT_SECONDARY));
        btnHeaderCopyZt.setFocusPainted(false);
        btnHeaderCopyZt.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnHeaderCopyZt.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(ztIp), null);
            ToastNotification.show(this, "Đã sao chép IP ZeroTier", ztIp, ToastNotification.ToastType.INFO);
        });
        centerBadge.add(btnHeaderCopyZt);

        header.add(centerBadge, BorderLayout.CENTER);

        // Right: Status badge + Settings + Theme Toggle
        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        rightActions.setOpaque(false);

        lblHeaderPeerStatus = new JLabel("● Chưa kết nối");
        lblHeaderPeerStatus.setFont(ThemeManager.FONT_BODY_BOLD);
        lblHeaderPeerStatus.setForeground(ThemeManager.DANGER);
        rightActions.add(lblHeaderPeerStatus);

        btnThemeToggle = new JButton(ThemeManager.isDarkMode() ? "☀ Sáng" : "🌙 Tối");
        btnThemeToggle.setFont(ThemeManager.FONT_SMALL);
        btnThemeToggle.setFocusPainted(false);
        btnThemeToggle.addActionListener(e -> {
            ThemeManager.toggleTheme();
            btnThemeToggle.setText(ThemeManager.isDarkMode() ? "☀ Sáng" : "🌙 Tối");
        });
        rightActions.add(btnThemeToggle);

        btnSettings = new JButton();
        btnSettings.setIcon(AppIcons.createSettingsIcon(16, ThemeManager.TEXT_PRIMARY));
        btnSettings.setToolTipText("Cài đặt ứng dụng");
        btnSettings.setFocusPainted(false);
        btnSettings.setPreferredSize(new Dimension(32, 28));
        btnSettings.addActionListener(e -> new SettingsDialog(this).setVisible(true));
        rightActions.add(btnSettings);

        JButton btnAbout = new JButton("ℹ");
        btnAbout.setFont(ThemeManager.FONT_BODY_BOLD);
        btnAbout.setToolTipText("Giới thiệu ứng dụng (About)");
        btnAbout.setFocusPainted(false);
        btnAbout.setPreferredSize(new Dimension(32, 28));
        btnAbout.addActionListener(e -> new dammtp2p.ui.components.AboutDialog(this).setVisible(true));
        rightActions.add(btnAbout);

        header.add(rightActions, BorderLayout.EAST);
        return header;
    }

    private void handleWindowClose() {
        TransferState state = transferManager.getCurrentState();
        if (state == TransferState.TRANSFERRING || state == TransferState.VERIFYING || state == TransferState.RESUMING) {
            int confirm = JOptionPane.showConfirmDialog(
                    this,
                    "Đang có tệp đang truyền tải dữ liệu.\nThoát ứng dụng sẽ hủy quá trình truyền tệp này.\nBạn có chắc chắn muốn thoát không?",
                    "Xác nhận thoát",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
            transferManager.cancelTransfer();
        }

        // Graceful disconnect
        try {
            connectionManager.disconnectPeer();
            connectionManager.stopListener();
        } catch (Exception ignored) {
        }

        dispose();
        System.exit(0);
    }

    // === NetworkStateListener Callbacks ===

    @Override
    public void onListeningStarted(int boundPort) {}

    @Override
    public void onListeningStopped() {}

    @Override
    public void onConnecting(String remoteIp, int port) {
        SwingUtilities.invokeLater(() -> {
            lblHeaderPeerStatus.setText("● Đang kết nối...");
            lblHeaderPeerStatus.setForeground(ThemeManager.WARNING);
        });
    }

    @Override
    public void onConnected(PeerInfo peer) {
        SwingUtilities.invokeLater(() -> {
            lblHeaderPeerStatus.setText("● Đã kết nối: " + peer.getDisplayName());
            lblHeaderPeerStatus.setForeground(ThemeManager.SUCCESS);

            chatPanel.updateConnectionState();
            fileTransferPanel.updateUIState();

            ToastNotification.show(this, "Đã kết nối bạn bè P2P",
                    peer.getDisplayName() + " (" + peer.getIpAddress() + ")",
                    ToastNotification.ToastType.SUCCESS);
        });
    }


    @Override
    public void onConnectionFailed(String remoteIp, int port, String reason) {
        SwingUtilities.invokeLater(() -> {
            lblHeaderPeerStatus.setText("● Kết nối thất bại");
            lblHeaderPeerStatus.setForeground(ThemeManager.DANGER);

            chatPanel.updateConnectionState();
            fileTransferPanel.updateUIState();

            String friendlyMsg = (reason != null && (reason.contains("refused") || reason.contains("timed out")))
                    ? "Bạn bè chưa bật ứng dụng hoặc sai IP ZeroTier."
                    : (reason != null ? reason : "Không thể kết nối.");
            ToastNotification.show(this, "Kết nối thất bại", friendlyMsg, ToastNotification.ToastType.ERROR);
        });
    }

    @Override
    public void onDisconnected(PeerInfo peer, String reason) {
        SwingUtilities.invokeLater(() -> {
            lblHeaderPeerStatus.setText("● Chưa kết nối");
            lblHeaderPeerStatus.setForeground(ThemeManager.DANGER);

            chatPanel.updateConnectionState();
            fileTransferPanel.updateUIState();

            ToastNotification.show(this, "Đã ngắt kết nối",
                    reason != null ? reason : "Phiên kết nối P2P đã kết thúc.",
                    ToastNotification.ToastType.WARNING);
        });
    }

    @Override
    public void onError(Throwable cause) {
        SwingUtilities.invokeLater(() -> {
            ToastNotification.show(this, "Lỗi mạng", cause.getMessage(), ToastNotification.ToastType.ERROR);
        });
    }

    // === FileTransferListener Callbacks ===

    @Override
    public void onIncomingFileRequest(FileMetadata metadata) {
        IncomingFileDialog.showDialog(this, metadata, transferManager);
    }

    @Override
    public void onFileRequestAccepted(FileMetadata metadata) {}

    @Override
    public void onFileRequestRejected(FileMetadata metadata, String reason) {}

    @Override
    public void onTransferStateChanged(FileMetadata metadata, TransferState state, String statusMessage) {}
}
