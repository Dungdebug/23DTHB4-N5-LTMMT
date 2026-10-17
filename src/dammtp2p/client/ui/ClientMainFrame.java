package dammtp2p.client.ui;

import dammtp2p.client.ClientCore;
import dammtp2p.core.model.ChatMessage;
import dammtp2p.core.model.FileMetadata;
import dammtp2p.core.model.TransferState;
import dammtp2p.server.ServerCore;
import dammtp2p.ui.components.AboutDialog;
import dammtp2p.ui.components.CreateRoomDialog;
import dammtp2p.ui.components.FileDropTarget;
import dammtp2p.ui.components.SettingsDialog;
import dammtp2p.ui.components.ToastNotification;
import dammtp2p.ui.theme.AppIcons;
import dammtp2p.ui.theme.ThemeManager;
import dammtp2p.util.AppConfig;
import dammtp2p.util.Constants;
import dammtp2p.util.FormatUtils;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * Main Application Frame for DAMMTP2P Mesh Workspace.
 * 
 * Distinctive Architecture:
 * - Top Application Bar: Brand node logo, app title, quick jump filter, connection status, global actions.
 * - Left Navigation Sidebar: Segmented tabs (Nhóm, Tin nhắn, Thoại, Tệp), rooms, contacts, voice dock, audio controls.
 * - Center Conversation View: Clean header with verified AES-128-GCM security badges, conversation timeline, non-clipping text wrapping, attachment cards, multi-line composer.
 * - Right Contextual Drawer: Collapsible participant list with real connected count and local user highlighting.
 * - Dedicated File Transfer Center Dialog: 4 KPI telemetry gauges, 20 GiB transfers, SHA-256 integrity, cancel/resume.
 */
public class ClientMainFrame extends JFrame implements ClientCore.ClientListener,
        WorkspaceNavRail.NavRailListener, ConversationSidebar.SidebarListener,
        ParticipantListPanel.MemberActionListener, TransferCenterDialog.TransferActionCallback,
        AuthenticationPanel.AuthListener {

    private final ClientCore client;
    private final String initialUsername;

    // Top Level Card Layout
    private CardLayout rootCardLayout;
    private JPanel rootCardPanel;
    private static final String CARD_AUTH = "CARD_AUTH";
    private static final String CARD_APP = "CARD_APP";

    // Sub-components
    private AuthenticationPanel authPanel;
    private WorkspaceNavRail navRail;
    private ConversationSidebar sidebar;
    private ParticipantListPanel memberListPanel;
    private TransferCenterDialog transfersDialog;
    private WorkspaceServerDialog serverDialog;

    // Top Application Bar
    private JLabel lblConnectionStatus;

    // Center Chat Area
    private JPanel centerChatArea;
    private JLabel lblChatHeaderTitle;
    private JLabel lblChatHeaderSubtitle;
    private JButton btnHeaderTransfers;
    private JButton btnToggleMemberList;
    private CardLayout timelineCardLayout;
    private JPanel timelineContainer;
    private final Map<String, ConversationTimeline> chatTimelines = new HashMap<>();
    private String currentActiveChatKey = "room:lobby";

    // Staging & Composer
    private JPanel fileStagingBanner;
    private JLabel lblStagedFileInfo;
    private JCheckBox chkStagedEncrypt;
    private File currentStagedFile;
    private JTextArea txtComposerInput;
    private JButton btnComposerSend;
    private JButton btnComposerAttach;

    // Embedded Server Host
    private ServerCore embeddedServer;

    // Voice & Presence state
    private final Set<String> activeSpeakingUsers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Map<String, List<String>> voiceRoomParticipants = new ConcurrentHashMap<>();
    private List<String> currentOnlineUsers = new ArrayList<>();
    private List<String> currentRooms = new ArrayList<>();
    private int activeTransfersCount = 0;

    public ClientMainFrame(ClientCore client, String defaultUser, boolean autoHost) {
        this.client = client;
        this.initialUsername = defaultUser;
        this.client.addListener(this);

        String userSuffix = (defaultUser != null && !defaultUser.trim().isEmpty()) ? " [" + defaultUser.trim() + "]" : "";
        setTitle("DAMMTP2P Mesh Workspace v" + Constants.APP_VERSION + userSuffix);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1280, 800);
        setMinimumSize(new Dimension(1040, 680));
        setLocationRelativeTo(null);

        initComponents();
        setupDragAndDrop();

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                stopEmbeddedServer();
                if (client != null && client.isConnected()) {
                    client.disconnect("Application exit");
                }
            }
        });

        if (autoHost) {
            SwingUtilities.invokeLater(this::startEmbeddedServer);
        }
    }

    public ClientMainFrame(ClientCore client) {
        this(client, null, false);
    }

    private void initComponents() {
        rootCardLayout = new CardLayout();
        rootCardPanel = new JPanel(rootCardLayout);

        // 1. Auth Card
        authPanel = new AuthenticationPanel(this, initialUsername);
        rootCardPanel.add(authPanel, CARD_AUTH);

        // 2. Main App Card (DAMMTP2P Mesh Workspace Shell)
        JPanel appShell = new JPanel(new BorderLayout());
        appShell.setBackground(ThemeManager.BG_DARK);

        // Top Application Bar (46px)
        JPanel topBar = createTopApplicationBar();
        appShell.add(topBar, BorderLayout.NORTH);

        // Conversation Sidebar (260px)
        sidebar = new ConversationSidebar(this);

        // Main Content Area
        JPanel mainContent = createMainContent();

        // Contextual Participant Drawer (220px - Collapsible)
        memberListPanel = new ParticipantListPanel(this);

        // Workspace Container combining Sidebar, Main Content, and Participant Drawer
        JPanel workspaceContainer = new JPanel(new BorderLayout());
        workspaceContainer.setOpaque(false);
        workspaceContainer.add(sidebar, BorderLayout.WEST);
        workspaceContainer.add(mainContent, BorderLayout.CENTER);
        workspaceContainer.add(memberListPanel, BorderLayout.EAST);

        appShell.add(workspaceContainer, BorderLayout.CENTER);

        rootCardPanel.add(appShell, CARD_APP);
        setContentPane(rootCardPanel);

        // Dedicated Transfers Dialog
        transfersDialog = new TransferCenterDialog(this, this);

        // Default: Show Auth Card until login
        rootCardLayout.show(rootCardPanel, CARD_AUTH);
    }

    private JPanel createTopApplicationBar() {
        JPanel bar = new JPanel(new BorderLayout(12, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                LinearGradientPaint gp = new LinearGradientPaint(
                        0, getHeight() - 1, getWidth(), getHeight() - 1,
                        new float[]{0.0f, 0.35f, 0.7f, 1.0f},
                        new Color[]{
                            new Color(0x00, 0xF2, 0xFE, 120),
                            new Color(0x0D, 0x94, 0x88, 160),
                            new Color(0x63, 0x66, 0xF1, 140),
                            new Color(0x00, 0xF2, 0xFE, 80)
                        }
                );
                g2.setPaint(gp);
                g2.fillRect(0, getHeight() - 1, getWidth(), 1);
                g2.dispose();
            }
        };
        bar.setBackground(ThemeManager.BG_NAV);
        bar.setPreferredSize(new Dimension(Integer.MAX_VALUE, 46));
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ThemeManager.BORDER_SUBTLE),
                BorderFactory.createEmptyBorder(0, 14, 0, 14)
        ));

        // Left Brand: Node logo + Application title
        JPanel leftBrand = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        leftBrand.setOpaque(false);
        JLabel logoLbl = new JLabel(AppIcons.createMeshLogo(22, ThemeManager.ACCENT_PRIMARY, ThemeManager.ACCENT_ELECTRIC_CYAN));
        JLabel titleLbl = new JLabel("DAMMTP2P");
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 14));
        titleLbl.setForeground(ThemeManager.ACCENT_ELECTRIC_CYAN);
        JLabel subTitleLbl = new JLabel("Mesh Workspace");
        subTitleLbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        subTitleLbl.setForeground(ThemeManager.TEXT_SECONDARY);
        JLabel versionBadge = ThemeManager.createBadge("v" + Constants.APP_VERSION, ThemeManager.ACCENT_CYAN, new Color(0x00, 0xF2, 0xFE, 30));
        leftBrand.add(logoLbl);
        leftBrand.add(titleLbl);
        leftBrand.add(subTitleLbl);
        leftBrand.add(versionBadge);

        // Center: Quick filter / jump field
        JPanel centerSearch = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 7));
        centerSearch.setOpaque(false);
        JTextField txtSearch = new JTextField(24);
        txtSearch.setPreferredSize(new Dimension(280, 28));
        txtSearch.setFont(ThemeManager.FONT_SMALL);
        txtSearch.setBackground(ThemeManager.BG_CARD);
        txtSearch.setForeground(ThemeManager.TEXT_PRIMARY);
        txtSearch.setCaretColor(Color.WHITE);
        txtSearch.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(2, 10, 2, 10)
        ));
        txtSearch.putClientProperty("JTextField.placeholderText", "Tìm nhanh phòng hoặc bạn bè... (Ctrl+K)");
        txtSearch.putClientProperty("JTextField.leadingIcon", AppIcons.createSearchIcon(14, ThemeManager.TEXT_MUTED));
        txtSearch.putClientProperty("Component.arc", 16);
        txtSearch.addKeyListener(new KeyAdapter() {
            @Override public void keyReleased(KeyEvent e) {
                String q = txtSearch.getText().trim().toLowerCase();
                if (!q.isEmpty()) {
                    for (String r : currentRooms) {
                        if (r.toLowerCase().contains(q)) {
                            sidebar.selectRoom(r);
                            break;
                        }
                    }
                }
            }
        });
        centerSearch.add(txtSearch);

        // Right Actions: Connection Indicator, Quick Buttons, Settings
        JPanel rightActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        rightActions.setOpaque(false);

        lblConnectionStatus = ThemeManager.createBadge("● P2P MESH ONLINE", ThemeManager.SUCCESS, new Color(0x10, 0xB9, 0x81, 40));
        lblConnectionStatus.setFont(ThemeManager.FONT_SMALL_BOLD);
        lblConnectionStatus.setToolTipText("ZeroTier Direct P2P Mesh • Mã hóa: AES-128-GCM");

        JButton btnTopTransfers = new JButton("Quản Lý Tệp", AppIcons.createTransfersIcon(14, ThemeManager.ACCENT_CYAN));
        btnTopTransfers.setFont(ThemeManager.FONT_SMALL_BOLD);
        btnTopTransfers.setForeground(ThemeManager.TEXT_PRIMARY);
        btnTopTransfers.setBackground(ThemeManager.BG_CARD);
        btnTopTransfers.setFocusPainted(false);
        btnTopTransfers.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnTopTransfers.setToolTipText("Mở trung tâm quản lý truyền tệp (F3)");
        btnTopTransfers.addActionListener(e -> onTransfersClicked());

        JButton btnTopMembers = new JButton("Thành Viên", AppIcons.createUsersIcon(14, ThemeManager.TEXT_PRIMARY));
        btnTopMembers.setFont(ThemeManager.FONT_SMALL_BOLD);
        btnTopMembers.setForeground(ThemeManager.TEXT_PRIMARY);
        btnTopMembers.setBackground(ThemeManager.BG_CARD);
        btnTopMembers.setFocusPainted(false);
        btnTopMembers.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnTopMembers.setToolTipText("Ẩn / Hiện danh sách thành viên trực tuyến");
        btnTopMembers.addActionListener(e -> {
            memberListPanel.setVisible(!memberListPanel.isVisible());
        });

        JButton btnTopServer = new JButton("Máy Chủ", AppIcons.createNetworkIconSafe(14, ThemeManager.ACCENT_CYAN));
        btnTopServer.setFont(ThemeManager.FONT_SMALL_BOLD);
        btnTopServer.setForeground(ThemeManager.TEXT_PRIMARY);
        btnTopServer.setBackground(ThemeManager.BG_CARD);
        btnTopServer.setFocusPainted(false);
        btnTopServer.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnTopServer.setToolTipText("Mở bảng điều khiển Máy Chủ trung tâm");
        btnTopServer.addActionListener(e -> onServerAdminClicked());

        JButton btnTopSettings = new JButton(AppIcons.createSettingsIcon(14, ThemeManager.TEXT_MUTED));
        btnTopSettings.setToolTipText("Cài đặt hệ thống");
        btnTopSettings.setPreferredSize(new Dimension(30, 28));
        btnTopSettings.setBackground(ThemeManager.BG_CARD);
        btnTopSettings.setFocusPainted(false);
        btnTopSettings.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnTopSettings.addActionListener(e -> onSettingsClicked());

        rightActions.add(lblConnectionStatus);
        rightActions.add(btnTopTransfers);
        rightActions.add(btnTopMembers);
        rightActions.add(btnTopServer);
        rightActions.add(btnTopSettings);

        bar.add(leftBrand, BorderLayout.WEST);
        bar.add(centerSearch, BorderLayout.CENTER);
        bar.add(rightActions, BorderLayout.EAST);
        return bar;
    }

    private JPanel createMainContent() {
        centerChatArea = new JPanel(new BorderLayout()) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();

                // 1. Deep space base
                g2.setColor(new Color(0x0A, 0x0E, 0x18));
                g2.fillRect(0, 0, w, h);

                if (w > 0 && h > 0) {
                    // 2. Ambient top-center Cyan-Teal aurora (electric vibrant)
                    java.awt.RadialGradientPaint topCyan = new java.awt.RadialGradientPaint(
                            w * 0.55f, 0, Math.max(w, h) * 0.70f,
                            new float[]{0.0f, 0.5f, 1.0f},
                            new Color[]{new Color(0x00, 0xF2, 0xFE, 65), new Color(0x0D, 0x94, 0x88, 28), new Color(0, 0, 0, 0)}
                    );
                    g2.setPaint(topCyan);
                    g2.fillRect(0, 0, w, h);

                    // 3. Ambient bottom-left Neon Violet/Purple aura
                    java.awt.RadialGradientPaint bottomViolet = new java.awt.RadialGradientPaint(
                            w * 0.20f, h * 0.95f, Math.max(w, h) * 0.65f,
                            new float[]{0.0f, 0.55f, 1.0f},
                            new Color[]{new Color(0x8B, 0x5C, 0xF6, 55), new Color(0x63, 0x66, 0xF1, 20), new Color(0, 0, 0, 0)}
                    );
                    g2.setPaint(bottomViolet);
                    g2.fillRect(0, 0, w, h);

                    // 4. Subtle center-right Emerald pulse
                    java.awt.RadialGradientPaint rightEmerald = new java.awt.RadialGradientPaint(
                            w * 0.90f, h * 0.45f, Math.max(w, h) * 0.45f,
                            new float[]{0.0f, 0.7f, 1.0f},
                            new Color[]{new Color(0x10, 0xB9, 0x81, 35), new Color(0x05, 0x96, 0x69, 12), new Color(0, 0, 0, 0)}
                    );
                    g2.setPaint(rightEmerald);
                    g2.fillRect(0, 0, w, h);

                    // 5. Subtle cyber micro-dots grid
                    g2.setColor(new Color(0x00, 0xF2, 0xFE, 14));
                    int step = 28;
                    for (int x = 14; x < w; x += step) {
                        for (int y = 14; y < h; y += step) {
                            g2.fillRect(x, y, 1, 1);
                        }
                    }
                }
                g2.dispose();
            }
        };

        // 1. Channel Header Bar (48px)
        JPanel headerBar = new JPanel(new BorderLayout(12, 0));
        headerBar.setBackground(ThemeManager.BG_DARK);
        headerBar.setPreferredSize(new Dimension(Integer.MAX_VALUE, 48));
        headerBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ThemeManager.BORDER_SUBTLE),
                BorderFactory.createEmptyBorder(0, 16, 0, 14)
        ));

        JPanel headerLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 10));
        headerLeft.setOpaque(false);

        lblChatHeaderTitle = new JLabel("# Lobby");
        lblChatHeaderTitle.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblChatHeaderTitle.setForeground(ThemeManager.TEXT_PRIMARY);

        JPanel vSep = new JPanel();
        vSep.setBackground(ThemeManager.BORDER_SUBTLE);
        vSep.setPreferredSize(new Dimension(1, 16));

        lblChatHeaderSubtitle = ThemeManager.createBadge("● AES-128-GCM", ThemeManager.ACCENT_ELECTRIC_CYAN, new Color(0x00, 0xF2, 0xFE, 35));
        lblChatHeaderSubtitle.setToolTipText("Mã hóa đối xứng AES-128-GCM với IV ngẫu nhiên");

        headerLeft.add(lblChatHeaderTitle);
        headerLeft.add(vSep);
        headerLeft.add(lblChatHeaderSubtitle);

        JPanel headerRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 8));
        headerRight.setOpaque(false);

        JButton btnQuickVoice = new JButton("Vào Thoại", AppIcons.createVoiceWaveIcon(13, ThemeManager.SUCCESS));
        btnQuickVoice.setFont(ThemeManager.FONT_SMALL_BOLD);
        btnQuickVoice.setForeground(ThemeManager.SUCCESS);
        btnQuickVoice.setBackground(ThemeManager.BG_CARD);
        btnQuickVoice.setFocusPainted(false);
        btnQuickVoice.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnQuickVoice.setToolTipText("Tham gia kênh âm thanh thời gian thực (RTC)");
        btnQuickVoice.addActionListener(e -> onVoiceRoomSelected("Phòng Thảo Luận 01"));

        JButton btnMore = new JButton(AppIcons.createMoreDotsIcon(14, ThemeManager.TEXT_MUTED));
        btnMore.setPreferredSize(new Dimension(30, 28));
        btnMore.setBackground(ThemeManager.BG_CARD);
        btnMore.setFocusPainted(false);
        btnMore.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnMore.setToolTipText("Tùy chọn bổ sung (Mạng, Xóa chat)");
        btnMore.addActionListener(e -> {
            JPopupMenu menu = new JPopupMenu();
            menu.setBackground(ThemeManager.BG_CARD);
            menu.setBorder(BorderFactory.createLineBorder(ThemeManager.BORDER_COLOR, 1));

            JMenuItem miDiag = new JMenuItem("Chi Tiết Mạng (Diagnostics)");
            miDiag.addActionListener(ev -> showNetworkDiagnostics());

            JMenuItem miClear = new JMenuItem("Xóa Màn Hình Chat");
            miClear.addActionListener(ev -> clearCurrentChat());

            menu.add(miDiag);
            menu.add(miClear);
            menu.show(btnMore, 0, btnMore.getHeight());
        });

        headerRight.add(btnQuickVoice);
        headerRight.add(btnMore);

        headerBar.add(headerLeft, BorderLayout.WEST);
        headerBar.add(headerRight, BorderLayout.EAST);
        centerChatArea.add(headerBar, BorderLayout.NORTH);

        // 2. Timeline Container
        timelineCardLayout = new CardLayout();
        timelineContainer = new JPanel(timelineCardLayout);
        timelineContainer.setOpaque(false);

        openOrCreateTimeline("room:lobby", "Lobby", false);
        centerChatArea.add(timelineContainer, BorderLayout.CENTER);

        // 3. Bottom Composer & Staging Section
        JPanel bottomComposerWrapper = new JPanel();
        bottomComposerWrapper.setLayout(new BoxLayout(bottomComposerWrapper, BoxLayout.Y_AXIS));
        bottomComposerWrapper.setOpaque(false);
        bottomComposerWrapper.setBorder(BorderFactory.createEmptyBorder(0, 16, 12, 16));

        // 3A. Staged File Banner
        fileStagingBanner = new JPanel(new BorderLayout(8, 0));
        fileStagingBanner.setBackground(ThemeManager.BG_CARD);
        fileStagingBanner.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.ACCENT_PRIMARY, 1),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));
        fileStagingBanner.setVisible(false);

        lblStagedFileInfo = new JLabel("📁 Tệp sẵn sàng gửi");
        lblStagedFileInfo.setFont(ThemeManager.FONT_SMALL_BOLD);
        lblStagedFileInfo.setForeground(ThemeManager.ACCENT_CYAN);

        JPanel bannerRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        bannerRight.setOpaque(false);

        chkStagedEncrypt = new JCheckBox("Mã hóa AES-128", true);
        chkStagedEncrypt.setFont(ThemeManager.FONT_SMALL);
        chkStagedEncrypt.setForeground(ThemeManager.TEXT_SECONDARY);
        chkStagedEncrypt.setOpaque(false);

        JButton btnCancelStaged = new JButton("Hủy");
        btnCancelStaged.setFont(ThemeManager.FONT_SMALL);
        btnCancelStaged.setForeground(ThemeManager.DANGER);
        btnCancelStaged.setContentAreaFilled(false);
        btnCancelStaged.setBorderPainted(false);
        btnCancelStaged.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnCancelStaged.addActionListener(e -> cancelStagedFile());

        bannerRight.add(chkStagedEncrypt);
        bannerRight.add(btnCancelStaged);

        fileStagingBanner.add(lblStagedFileInfo, BorderLayout.WEST);
        fileStagingBanner.add(bannerRight, BorderLayout.EAST);

        // 3B. Floating Luminous Composer Capsule
        JPanel composerCapsule = new JPanel(new BorderLayout(10, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // Deep frosted glass
                g2.setColor(new Color(0x16, 0x1F, 0x2F, 245));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);

                // Multi-tone neon border: Electric Cyan -> Teal
                LinearGradientPaint borderGrad = new LinearGradientPaint(
                        0, 0, getWidth(), getHeight(),
                        new float[]{0.0f, 1.0f},
                        new Color[]{ThemeManager.ACCENT_ELECTRIC_CYAN, ThemeManager.ACCENT_PRIMARY}
                );
                g2.setPaint(borderGrad);
                g2.setStroke(new BasicStroke(1.3f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);

                // Inner top rim
                g2.setColor(new Color(255, 255, 255, 22));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 14, 14);

                g2.dispose();
                super.paintComponent(g);
            }
        };
        composerCapsule.setOpaque(false);
        composerCapsule.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 8));

        btnComposerAttach = new JButton(AppIcons.createFolderIcon(18, ThemeManager.ACCENT_ELECTRIC_CYAN));
        btnComposerAttach.setToolTipText("Đính kèm tệp tin (Kéo thả hoặc nhấn để chọn, hỗ trợ đến 20 GiB)");
        btnComposerAttach.setFocusPainted(false);
        btnComposerAttach.setContentAreaFilled(false);
        btnComposerAttach.setBorderPainted(false);
        btnComposerAttach.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnComposerAttach.addActionListener(e -> chooseLocalFile());

        txtComposerInput = new JTextArea(1, 20);
        txtComposerInput.setFont(ThemeManager.FONT_BODY);
        txtComposerInput.setOpaque(false);
        txtComposerInput.setForeground(ThemeManager.TEXT_PRIMARY);
        txtComposerInput.setCaretColor(Color.WHITE);
        txtComposerInput.setBorder(null);
        txtComposerInput.setLineWrap(true);
        txtComposerInput.setWrapStyleWord(true);
        txtComposerInput.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (e.isShiftDown()) {
                        txtComposerInput.append("\n");
                    } else {
                        e.consume();
                        sendCurrentChatMessage();
                    }
                }
            }
        });

        btnComposerSend = new JButton(AppIcons.createSendIcon(14, Color.WHITE)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                LinearGradientPaint gp = new LinearGradientPaint(
                        0, 0, getWidth(), getHeight(),
                        new float[]{0.0f, 1.0f},
                        new Color[]{ThemeManager.ACCENT_ELECTRIC_CYAN, ThemeManager.ACCENT_PRIMARY}
                );
                g2.setPaint(gp);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btnComposerSend.setToolTipText("Gửi tin nhắn (Enter)");
        btnComposerSend.setPreferredSize(new Dimension(32, 28));
        btnComposerSend.setFocusPainted(false);
        btnComposerSend.setContentAreaFilled(false);
        btnComposerSend.setBorderPainted(false);
        btnComposerSend.setOpaque(false);
        btnComposerSend.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnComposerSend.addActionListener(e -> sendCurrentChatMessage());

        composerCapsule.add(btnComposerAttach, BorderLayout.WEST);
        composerCapsule.add(txtComposerInput, BorderLayout.CENTER);
        composerCapsule.add(btnComposerSend, BorderLayout.EAST);

        JPanel footerRow = new JPanel(new BorderLayout());
        footerRow.setOpaque(false);
        footerRow.setBorder(BorderFactory.createEmptyBorder(4, 6, 0, 6));

        JLabel lblHint = new JLabel("• Nhấn Enter để gửi • Shift+Enter để xuống dòng");
        lblHint.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        lblHint.setForeground(new Color(0x64, 0x74, 0x8B));

        JLabel lblSec = new JLabel("• AES-128-GCM E2EE", JLabel.RIGHT);
        lblSec.setFont(new Font("Segoe UI", Font.BOLD, 10));
        lblSec.setForeground(ThemeManager.ACCENT_ELECTRIC_CYAN);

        footerRow.add(lblHint, BorderLayout.WEST);
        footerRow.add(lblSec, BorderLayout.EAST);

        bottomComposerWrapper.add(fileStagingBanner);
        bottomComposerWrapper.add(Box.createVerticalStrut(4));
        bottomComposerWrapper.add(composerCapsule);
        bottomComposerWrapper.add(footerRow);

        centerChatArea.add(bottomComposerWrapper, BorderLayout.SOUTH);
        return centerChatArea;
    }

    private ConversationTimeline openOrCreateTimeline(String key, String displayName, boolean isDirectMessage) {
        ConversationTimeline timeline = chatTimelines.get(key);
        if (timeline == null) {
            timeline = new ConversationTimeline(displayName, isDirectMessage);
            chatTimelines.put(key, timeline);
            timelineContainer.add(timeline, key);
        }
        timelineCardLayout.show(timelineContainer, key);
        currentActiveChatKey = key;
        updateChatHeader();
        return timeline;
    }

    private void updateChatHeader() {
        if (currentActiveChatKey.startsWith("room:")) {
            String room = currentActiveChatKey.substring(5);
            lblChatHeaderTitle.setText("# " + room);
            lblChatHeaderSubtitle.setText("● Kênh Nhóm • AES-128-GCM");
            lblChatHeaderSubtitle.setToolTipText("Kênh trò chuyện nhóm, mã hóa đối xứng AES-128-GCM");
        } else if (currentActiveChatKey.startsWith("user:")) {
            String user = currentActiveChatKey.substring(5);
            lblChatHeaderTitle.setText("@ " + user);
            lblChatHeaderSubtitle.setText("● Tin Nhắn Riêng 1–1 • AES-128-GCM");
            lblChatHeaderSubtitle.setToolTipText("Hội thoại riêng tư 1–1 bảo mật đầu-cuối AES-128-GCM");
        }
    }

    private void setupDragAndDrop() {
        new FileDropTarget(getRootPane(), file -> {
            if (file != null) {
                stageSelectedFile(file);
            }
        });
    }

    private void stageSelectedFile(File file) {
        if (file == null) return;
        this.currentStagedFile = file;
        String sizeStr = FormatUtils.formatFileSize(file.length());
        lblStagedFileInfo.setText("📁 Tệp sẵn sàng gửi: " + file.getName() + " (" + sizeStr + ")");
        fileStagingBanner.setVisible(true);
        ToastNotification.show(this, "Đã chọn tệp: " + file.getName() + " (" + sizeStr + "). Bấm Enter để gửi!", ToastNotification.Type.INFO);
    }

    private void cancelStagedFile() {
        this.currentStagedFile = null;
        fileStagingBanner.setVisible(false);
    }

    private void chooseLocalFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Chọn tệp cần gửi (Hỗ trợ tối đa 20 GiB)");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            stageSelectedFile(chooser.getSelectedFile());
        }
    }

    private void sendCurrentChatMessage() {
        String text = txtComposerInput.getText().trim();
        File fileToSend = currentStagedFile;
        boolean encrypt = chkStagedEncrypt.isSelected();

        if (fileToSend != null) {
            cancelStagedFile();
            new Thread(() -> {
                try {
                    boolean isRoom = currentActiveChatKey.startsWith("room:")
                            || (client != null && client.getAvailableRooms().stream().anyMatch(r -> r.equalsIgnoreCase(currentActiveChatKey)));
                    String target = currentActiveChatKey.startsWith("room:")
                            ? currentActiveChatKey.substring(5)
                            : (currentActiveChatKey.startsWith("user:") ? currentActiveChatKey.substring(5) : currentActiveChatKey);
                    FileMetadata meta;
                    if (isRoom) {
                        meta = client.initiateGroupFileSend(target, fileToSend, encrypt, null);
                    } else {
                        meta = client.initiateFileSend(target, fileToSend, encrypt, null);
                    }

                    ConversationTimeline timeline = chatTimelines.get(currentActiveChatKey);
                    if (timeline != null) {
                        timeline.addFileTransfer(meta, false, client.getAuthenticatedUser(), new ChatMessageView.FileActionListener() {
                            @Override public void onAcceptFile(FileMetadata m) {}
                            @Override public void onRejectFile(FileMetadata m) {}
                            @Override public void onOpenFile(File f) { openFileInDesktop(f); }
                        });
                    }
                    activeTransfersCount++;
                    String partner = isRoom ? "[Nhóm] #" + target : target;
                    transfersDialog.addOrUpdateTransferRow(meta.getTransferId(), meta.getFileName(), meta.getFileSize(), partner, 0, "Đang gửi đề nghị...");
                    ToastNotification.show(ClientMainFrame.this, "Đã bắt đầu truyền tệp: " + fileToSend.getName(), ToastNotification.Type.SUCCESS);
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> ToastNotification.show(ClientMainFrame.this, "Lỗi gửi tệp: " + ex.getMessage(), ToastNotification.Type.ERROR));
                }
            }).start();
        }

        if (!text.isEmpty()) {
            txtComposerInput.setText("");
            new Thread(() -> {
                try {
                    if (currentActiveChatKey.startsWith("room:")) {
                        client.sendRoomMessage(currentActiveChatKey.substring(5), text);
                    } else if (currentActiveChatKey.startsWith("user:")) {
                        client.sendPrivateMessage(currentActiveChatKey.substring(5), text);
                    }
                } catch (Exception ex) {
                    SwingUtilities.invokeLater(() -> ToastNotification.show(ClientMainFrame.this, "Không thể gửi tin nhắn: " + ex.getMessage(), ToastNotification.Type.ERROR));
                }
            }).start();
        }
    }

    private void showNetworkDiagnostics() {
        String info = String.format("Trạng thái Kết nối:\n- User: %s\n- Server Host: %s:%d\n- Active Chat: %s\n- Connected Peers: %d\n- Active Voice Room: %s",
                client.getAuthenticatedUser(), client.getConnectedServerIp(), client.getConnectedServerPort(), currentActiveChatKey,
                currentOnlineUsers.size(), client.getCurrentVoiceRoom() != null ? client.getCurrentVoiceRoom() : "Không");
        JOptionPane.showMessageDialog(this, info, "Chi Tiết Kết Nối Mạng (Network Diagnostics)", JOptionPane.INFORMATION_MESSAGE);
    }

    private void clearCurrentChat() {
        ConversationTimeline t = chatTimelines.get(currentActiveChatKey);
        if (t != null) {
            t.clearTimeline();
            ToastNotification.show(this, "Đã xóa màn hình chat hiện tại.", ToastNotification.Type.INFO);
        }
    }

    // =========================================================================
    //  AUTHENTICATION PANEL CALLBACKS
    // =========================================================================

    @Override
    public void onLogin(String serverIp, int port, String username, String password) {
        if (username.isEmpty() || password.isEmpty()) {
            ToastNotification.show(this, "Vui lòng nhập tên đăng nhập và mật khẩu!", ToastNotification.Type.WARNING);
            return;
        }
        new Thread(() -> {
            try {
                if (!client.isConnected()) {
                    client.connectToServer(serverIp, port);
                }
                client.login(username, password, 4000);
            } catch (Exception ex) {
                String errMsg = ex.getMessage() != null ? ex.getMessage() : ex.toString();
                if (errMsg.contains("Connection refused") || errMsg.contains("timed out") || errMsg.contains("ConnectException")) {
                    errMsg = "Không thể kết nối đến Máy Chủ (" + serverIp + ":" + port + "). Vui lòng kiểm tra IP hoặc đảm bảo Máy Chủ đã bật.";
                }
                final String msg = errMsg;
                SwingUtilities.invokeLater(() -> {
                    authPanel.setStatus("Lỗi: " + msg, AuthenticationPanel.StatusType.ERROR);
                    ToastNotification.show(this, "Lỗi đăng nhập: " + msg, ToastNotification.Type.ERROR);
                });
            }
        }).start();
    }

    @Override
    public void onRegister(String serverIp, int port, String username, String password) {
        if (username.isEmpty() || password.isEmpty()) {
            ToastNotification.show(this, "Vui lòng nhập tên đăng nhập và mật khẩu!", ToastNotification.Type.WARNING);
            return;
        }
        new Thread(() -> {
            try {
                if (!client.isConnected()) {
                    client.connectToServer(serverIp, port);
                }
                client.register(username, password, 4000);
            } catch (Exception ex) {
                String errMsg = ex.getMessage() != null ? ex.getMessage() : ex.toString();
                if (errMsg.contains("Connection refused") || errMsg.contains("timed out") || errMsg.contains("ConnectException")) {
                    errMsg = "Không thể kết nối đến Máy Chủ (" + serverIp + ":" + port + "). Vui lòng kiểm tra IP hoặc đảm bảo Máy Chủ đã bật.";
                }
                final String msg = errMsg;
                SwingUtilities.invokeLater(() -> {
                    authPanel.setStatus("Lỗi: " + msg, AuthenticationPanel.StatusType.ERROR);
                    ToastNotification.show(this, "Lỗi đăng ký: " + msg, ToastNotification.Type.ERROR);
                });
            }
        }).start();
    }

    @Override
    public void onToggleHostServer() {
        if (embeddedServer != null && embeddedServer.isRunning()) {
            stopEmbeddedServer();
        } else {
            startEmbeddedServer();
        }
    }

    private void startEmbeddedServer() {
        try {
            if (embeddedServer == null) {
                embeddedServer = new ServerCore();
            }
            if (!embeddedServer.isRunning()) {
                embeddedServer.start();
                authPanel.setHostStatus(true);
                ToastNotification.show(this, "Đã khởi động Máy Chủ trung tâm thành công!", ToastNotification.Type.SUCCESS);
            }
        } catch (Exception e) {
            ToastNotification.show(this, "Không thể khởi động Server: " + e.getMessage(), ToastNotification.Type.ERROR);
        }
    }

    private void stopEmbeddedServer() {
        if (embeddedServer != null && embeddedServer.isRunning()) {
            embeddedServer.stop();
            authPanel.setHostStatus(false);
            ToastNotification.show(this, "Đã tắt Máy Chủ trung tâm.", ToastNotification.Type.INFO);
        }
    }

    // =========================================================================
    //  CLIENT CORE EVENT CALLBACKS
    // =========================================================================

    @Override public void onConnected(String serverIp, int port) {
        SwingUtilities.invokeLater(() -> {
            if (lblConnectionStatus != null) {
                lblConnectionStatus.setText("● Đã kết nối");
                lblConnectionStatus.setForeground(ThemeManager.SUCCESS);
            }
        });
    }

    @Override public void onDisconnected(String reason) {
        SwingUtilities.invokeLater(() -> {
            if (lblConnectionStatus != null) {
                lblConnectionStatus.setText("✕ Mất kết nối");
                lblConnectionStatus.setForeground(ThemeManager.DANGER);
            }
            rootCardLayout.show(rootCardPanel, CARD_AUTH);
            ToastNotification.show(this, "Đã ngắt kết nối: " + reason, ToastNotification.Type.WARNING);
        });
    }

    public void setupWorkspaceState(String username, List<String> onlineUsers, List<String> rooms) {
        rootCardLayout.show(rootCardPanel, CARD_APP);
        sidebar.setUsername(username);
        this.currentOnlineUsers = onlineUsers != null ? onlineUsers : new ArrayList<>();
        this.currentRooms = rooms != null ? rooms : new ArrayList<>();

        sidebar.updateRooms(currentRooms);
        sidebar.updateOnlineUsers(currentOnlineUsers);
        memberListPanel.updateMembers(currentOnlineUsers, username);
        transfersDialog.updateTargets(currentOnlineUsers, currentRooms);

        if (lblConnectionStatus != null) {
            lblConnectionStatus.setText("● P2P MESH ONLINE");
            lblConnectionStatus.setForeground(ThemeManager.SUCCESS);
        }

        // Auto-join Lobby
        openOrCreateTimeline("room:lobby", "Lobby", false);
        sidebar.selectRoom("Lobby");
    }

    @Override
    public void onLoginSuccess(String username, List<String> onlineUsers, List<String> rooms) {
        SwingUtilities.invokeLater(() -> {
            setupWorkspaceState(username, onlineUsers, rooms);
            ToastNotification.show(this, "Đăng nhập thành công! Chào mừng @" + username, ToastNotification.Type.SUCCESS);
        });
    }

    @Override
    public void onLoginFailed(String reason) {
        SwingUtilities.invokeLater(() -> {
            authPanel.setStatus("Đăng nhập thất bại: " + reason, AuthenticationPanel.StatusType.ERROR);
            ToastNotification.show(this, "Đăng nhập thất bại: " + reason, ToastNotification.Type.ERROR);
        });
    }

    @Override
    public void onOnlineUsersUpdated(List<String> users) {
        SwingUtilities.invokeLater(() -> {
            this.currentOnlineUsers = users;
            sidebar.updateOnlineUsers(users);
            memberListPanel.updateMembers(users, client.getAuthenticatedUser());
            transfersDialog.updateTargets(users, currentRooms);
        });
    }

    @Override
    public void onRoomsUpdated(List<String> rooms) {
        SwingUtilities.invokeLater(() -> {
            this.currentRooms = rooms;
            sidebar.updateRooms(rooms);
            transfersDialog.updateTargets(currentOnlineUsers, rooms);
        });
    }

    private String resolveTransferPartner(FileMetadata metadata) {
        if (metadata == null) return "Unknown";
        String myUser = client != null ? client.getAuthenticatedUser() : null;
        if (myUser != null && myUser.equalsIgnoreCase(metadata.getSender())) {
            String r = metadata.getReceiver();
            if (r != null && r.startsWith("room:")) {
                return "[Nhóm] #" + r.substring(5);
            }
            return r != null ? r : "Nhóm";
        } else {
            return metadata.getSender();
        }
    }

    public void postChatMessageDirectly(ChatMessage message) {
        if (message == null || message.getContent() == null || message.getContent().trim().isEmpty()) {
            return;
        }
        String key;
        if (message.isRoomMessage()) {
            key = "room:" + message.getRoomId().toLowerCase();
            ConversationTimeline t = openOrCreateTimeline(key, message.getRoomId(), false);
            t.addChatMessage(message, message.isOutgoing(), client.getAuthenticatedUser());
        } else {
            String partner = message.isOutgoing() ? message.getReceiver() : message.getSender();
            if (partner == null || partner.trim().isEmpty() || partner.equalsIgnoreCase("Peer") || partner.equalsIgnoreCase("unknown")) {
                return;
            }
            key = "user:" + partner.toLowerCase();
            ConversationTimeline t = openOrCreateTimeline(key, partner, true);
            t.addChatMessage(message, message.isOutgoing(), client.getAuthenticatedUser());
        }
    }

    @Override
    public void onSystemError(String message) {
        SwingUtilities.invokeLater(() -> {
            ToastNotification.show(this, message, ToastNotification.Type.WARNING);
        });
    }

    @Override
    public void onChatMessage(ChatMessage message) {
        SwingUtilities.invokeLater(() -> postChatMessageDirectly(message));
    }

    @Override
    public void onIncomingFileRequest(FileMetadata metadata) {
        SwingUtilities.invokeLater(() -> {
            if (metadata == null) return;
            if (metadata.getFileSize() > Constants.MAX_FILE_SIZE_BYTES || metadata.getFileSize() < 0L) {
                ToastNotification.show(this, "Đã từ chối tệp vượt quá 20 GiB từ @" + metadata.getSender(), ToastNotification.Type.WARNING);
                try { client.rejectIncomingFile(metadata, Constants.OVERSIZED_FILE_ERROR_MESSAGE); } catch (Exception ignored) {}
                return;
            }

            boolean isGroup = metadata.getReceiver() != null &&
                    (metadata.getReceiver().startsWith("room:") || metadata.getReceiver().startsWith("[Nhóm]"));
            String roomName = isGroup
                    ? metadata.getReceiver().replace("room:", "").replace("[Nhóm] # ", "").replace("[Nhóm]", "").trim()
                    : null;
            String key = isGroup
                    ? "room:" + roomName.toLowerCase()
                    : "user:" + metadata.getSender().toLowerCase();

            String title = isGroup ? roomName : metadata.getSender();
            ConversationTimeline timeline = openOrCreateTimeline(key, title, !isGroup);
            timeline.addFileTransfer(metadata, true, client.getAuthenticatedUser(), new ChatMessageView.FileActionListener() {
                @Override
                public void onAcceptFile(FileMetadata meta) {
                    try {
                        File downloadDir = new File(AppConfig.getInstance().getDownloadDir());
                        if (!downloadDir.exists()) downloadDir.mkdirs();
                        client.acceptIncomingFile(meta, downloadDir);
                        activeTransfersCount++;
                        ToastNotification.show(ClientMainFrame.this, "Đang tải tệp về: " + downloadDir.getName() + "/" + meta.getFileName(), ToastNotification.Type.SUCCESS);
                    } catch (Exception ex) {
                        ToastNotification.show(ClientMainFrame.this, "Lỗi nhận tệp: " + ex.getMessage(), ToastNotification.Type.ERROR);
                    }
                }

                @Override
                public void onRejectFile(FileMetadata meta) {
                    try {
                        client.rejectIncomingFile(meta, "Người nhận từ chối");
                    } catch (Exception ignored) {}
                }

                @Override
                public void onOpenFile(File file) {
                    openFileInDesktop(file);
                }
            });

            String partner = resolveTransferPartner(metadata);
            if (AppConfig.getInstance().isAutoAcceptFiles()) {
                try {
                    File downloadDir = new File(AppConfig.getInstance().getDownloadDir());
                    if (!downloadDir.exists()) downloadDir.mkdirs();
                    client.acceptIncomingFile(metadata, downloadDir);
                    activeTransfersCount++;
                    transfersDialog.addOrUpdateTransferRow(metadata.getTransferId(), metadata.getFileName(), metadata.getFileSize(), partner, 0, "Tự động nhận (Đang kết nối...)");
                    ToastNotification.show(this, "Tự động nhận tệp từ @" + metadata.getSender() + ": " + metadata.getFileName(), ToastNotification.Type.SUCCESS);
                } catch (Exception ex) {
                    transfersDialog.addOrUpdateTransferRow(metadata.getTransferId(), metadata.getFileName(), metadata.getFileSize(), partner, 0, "Chờ chấp nhận");
                    ToastNotification.show(this, "Tệp mới từ @" + metadata.getSender() + ": " + metadata.getFileName(), ToastNotification.Type.INFO);
                }
            } else {
                transfersDialog.addOrUpdateTransferRow(metadata.getTransferId(), metadata.getFileName(), metadata.getFileSize(), partner, 0, "Chờ chấp nhận");
                ToastNotification.show(this, "Tệp mới từ @" + metadata.getSender() + ": " + metadata.getFileName(), ToastNotification.Type.INFO);
            }
        });
    }

    @Override
    public void onFileTransferProgress(FileMetadata metadata, long transferred, long total, double speedBps) {
        SwingUtilities.invokeLater(() -> {
            if (metadata == null) return;
            for (ConversationTimeline t : chatTimelines.values()) {
                t.updateFileProgress(metadata.getTransferId(), transferred, total, speedBps);
            }
            int pct = total > 0 ? (int) (transferred * 100 / total) : 0;
            transfersDialog.updateTelemetry(transferred, total, speedBps, "Đang truyền tải (" + pct + "%)");
            String partner = resolveTransferPartner(metadata);
            transfersDialog.addOrUpdateTransferRow(metadata.getTransferId(), metadata.getFileName(), metadata.getFileSize(), partner, pct, "Đang truyền (" + pct + "%)");
        });
    }

    @Override
    public void onFileTransferStateChanged(FileMetadata metadata, TransferState state, String message) {
        SwingUtilities.invokeLater(() -> {
            if (metadata == null) return;
            for (ConversationTimeline t : chatTimelines.values()) {
                t.updateFileState(metadata.getTransferId(), state, message);
            }
            String partner = resolveTransferPartner(metadata);
            if (state == TransferState.COMPLETED) {
                if (activeTransfersCount > 0) activeTransfersCount--;
                transfersDialog.addOrUpdateTransferRow(metadata.getTransferId(), metadata.getFileName(), metadata.getFileSize(), partner, 100, "Hoàn tất ✓ (SHA-256 Khớp)");
                transfersDialog.updateTelemetry(metadata.getFileSize(), metadata.getFileSize(), 0, "Khớp 100% SHA-256 ✓");
                ToastNotification.show(this, "Truyền tệp hoàn tất: " + metadata.getFileName(), ToastNotification.Type.SUCCESS);
            } else if (state == TransferState.VERIFYING) {
                transfersDialog.addOrUpdateTransferRow(metadata.getTransferId(), metadata.getFileName(), metadata.getFileSize(), partner, 100, "Đang xác thực SHA-256...");
                transfersDialog.updateTelemetry(metadata.getFileSize(), metadata.getFileSize(), 0, "Đang kiểm tra SHA-256...");
            } else if (state == TransferState.INTERRUPTED) {
                if (activeTransfersCount > 0) activeTransfersCount--;
                transfersDialog.addOrUpdateTransferRow(metadata.getTransferId(), metadata.getFileName(), metadata.getFileSize(), partner, 0, "Bị gián đoạn");
                transfersDialog.updateTelemetry(metadata.getOffset(), metadata.getFileSize(), 0, "Gián đoạn kết nối");
                ToastNotification.show(this, "Truyền tệp gián đoạn: " + message, ToastNotification.Type.WARNING);
            } else if (state == TransferState.RESUMING) {
                int pct = metadata.getFileSize() > 0 ? (int) (metadata.getOffset() * 100 / metadata.getFileSize()) : 0;
                transfersDialog.addOrUpdateTransferRow(metadata.getTransferId(), metadata.getFileName(), metadata.getFileSize(), partner, pct, "Đang tiếp tục (Resume)...");
                transfersDialog.updateTelemetry(metadata.getOffset(), metadata.getFileSize(), 0, "Đang chuẩn bị tiếp tục...");
            } else if (state == TransferState.FAILED || state == TransferState.REJECTED) {
                if (activeTransfersCount > 0) activeTransfersCount--;
                transfersDialog.addOrUpdateTransferRow(metadata.getTransferId(), metadata.getFileName(), metadata.getFileSize(), partner, 0, "Thất bại: " + message);
                ToastNotification.show(this, "Truyền tệp lỗi: " + message, ToastNotification.Type.ERROR);
            }
        });
    }

    @Override
    public void onUserSpeaking(String username, boolean isSpeaking) {
        SwingUtilities.invokeLater(() -> {
            if (username == null) return;
            if (isSpeaking) {
                activeSpeakingUsers.add(username.toLowerCase());
            } else {
                activeSpeakingUsers.remove(username.toLowerCase());
            }
            sidebar.setSpeakingUsers(activeSpeakingUsers);
            memberListPanel.setSpeakingUsers(activeSpeakingUsers);
        });
    }

    @Override
    public void onVoiceMembersUpdated(String roomId, List<String> members) {
        SwingUtilities.invokeLater(() -> {
            if (roomId != null && members != null) {
                voiceRoomParticipants.put(roomId, members);
                sidebar.repaint();
            }
        });
    }

    // =========================================================================
    //  SIDEBAR / TOP ACTIONS ROUTING
    // =========================================================================

    @Override public void onHomeClicked() { openOrCreateTimeline("room:lobby", "Lobby", false); }
    @Override public void onChannelsClicked() {
        if (!currentRooms.isEmpty()) openOrCreateTimeline("room:" + currentRooms.get(0).toLowerCase(), currentRooms.get(0), false);
    }
    @Override public void onDmsClicked() {
        if (!currentOnlineUsers.isEmpty()) {
            for (String u : currentOnlineUsers) {
                if (!u.equalsIgnoreCase(client.getAuthenticatedUser())) {
                    openOrCreateTimeline("user:" + u.toLowerCase(), u, true);
                    break;
                }
            }
        }
    }
    @Override public void onAddChannelClicked() { onCreateRoomRequested(); }
    @Override public void onTransfersClicked() { transfersDialog.setVisible(true); }

    @Override
    public void onServerAdminClicked() {
        if (embeddedServer == null) {
            startEmbeddedServer();
        }
        if (serverDialog == null) {
            serverDialog = new WorkspaceServerDialog(this, embeddedServer);
        }
        serverDialog.setVisible(true);
    }

    @Override public void onSettingsClicked() { new SettingsDialog(this).setVisible(true); }
    @Override public void onAboutClicked() { new AboutDialog(this).setVisible(true); }

    @Override
    public void onLogoutClicked() {
        if (client.isAuthenticated()) {
            client.disconnect("Người dùng đăng xuất");
        }
        rootCardLayout.show(rootCardPanel, CARD_AUTH);
    }

    @Override public void onSettingsRequested() { onSettingsClicked(); }
    @Override public void onTransfersRequested() { onTransfersClicked(); }
    @Override public void onLogoutRequested() { onLogoutClicked(); }

    @Override
    public void onRoomSelected(String roomName) {
        openOrCreateTimeline("room:" + roomName.toLowerCase(), roomName, false);
    }

    @Override
    public void onVoiceRoomSelected(String roomName) {
        if (client.isAuthenticated()) {
            client.joinVoiceRoom(roomName);
            sidebar.setVoiceState(true, roomName);
            ToastNotification.show(this, "Đã tham gia phòng thoại: " + roomName, ToastNotification.Type.SUCCESS);
        } else {
            ToastNotification.show(this, "Vui lòng đăng nhập trước khi tham gia thoại!", ToastNotification.Type.WARNING);
        }
    }

    @Override
    public void onUserSelected(String username) {
        openOrCreateTimeline("user:" + username.toLowerCase(), username, true);
    }

    @Override
    public void onAddFriendRequested(String username) {
        handleFriendAdd(username);
    }

    @Override
    public void onRemoveFriendRequested(String username) {
        handleFriendRemove(username);
    }

    @Override
    public void onFriendAddRequested(String username) {
        handleFriendAdd(username);
    }

    @Override
    public void onFriendRemoveRequested(String username) {
        handleFriendRemove(username);
    }

    @Override
    public void onFriendStatusChanged(String target, boolean success, String message) {
        SwingUtilities.invokeLater(() -> {
            String me = client != null ? client.getAuthenticatedUser() : initialUsername;
            sidebar.updateOnlineUsers(currentOnlineUsers);
            memberListPanel.updateMembers(currentOnlineUsers, me);
            if (message != null && !message.isEmpty()) {
                ToastNotification.show(this, message, success ? ToastNotification.Type.SUCCESS : ToastNotification.Type.INFO);
            }
        });
    }

    private void handleFriendAdd(String targetUser) {
        String me = client != null ? client.getAuthenticatedUser() : initialUsername;
        if (me == null || targetUser == null) return;
        boolean added = dammtp2p.client.FriendsManager.getInstance().addFriend(me, targetUser);
        if (added) {
            ToastNotification.show(this, "Đã kết bạn thành công với @" + targetUser + "!", ToastNotification.Type.SUCCESS);
            sidebar.updateOnlineUsers(currentOnlineUsers);
            memberListPanel.updateMembers(currentOnlineUsers, me);
            if (client != null && client.isAuthenticated()) {
                try {
                    client.requestAddFriend(targetUser);
                } catch (Exception ignored) {}
            }
        } else {
            ToastNotification.show(this, "@" + targetUser + " đã có trong danh sách bạn bè!", ToastNotification.Type.WARNING);
        }
    }

    private void handleFriendRemove(String targetUser) {
        String me = client != null ? client.getAuthenticatedUser() : initialUsername;
        if (me == null || targetUser == null) return;
        boolean removed = dammtp2p.client.FriendsManager.getInstance().removeFriend(me, targetUser);
        if (removed) {
            ToastNotification.show(this, "Đã hủy kết bạn với @" + targetUser + ".", ToastNotification.Type.INFO);
            sidebar.updateOnlineUsers(currentOnlineUsers);
            memberListPanel.updateMembers(currentOnlineUsers, me);
            if (client != null && client.isAuthenticated()) {
                try {
                    client.requestRemoveFriend(targetUser);
                } catch (Exception ignored) {}
            }
        }
    }

    @Override
    public void onCreateRoomRequested() {
        CreateRoomDialog dlg = new CreateRoomDialog(this, new ArrayList<>(currentOnlineUsers));
        dlg.setVisible(true);
        if (dlg.isConfirmed()) {
            CreateRoomDialog.RoomCreationResult res = dlg.getResult();
            if (res != null && res.roomId != null && !res.roomId.trim().isEmpty()) {
                try {
                    client.createRoom(res.roomId, res.displayName);
                    ToastNotification.show(this, "Đã gửi yêu cầu tạo phòng: #" + res.displayName, ToastNotification.Type.SUCCESS);
                } catch (Exception ex) {
                    ToastNotification.show(this, "Lỗi tạo phòng: " + ex.getMessage(), ToastNotification.Type.ERROR);
                }
            }
        }
    }

    @Override
    public void onVoiceLeaveRequested() {
        client.leaveVoiceRoom();
        sidebar.setVoiceState(false, null);
        ToastNotification.show(this, "Đã rời phòng thoại.", ToastNotification.Type.INFO);
    }

    @Override
    public void onMicToggled() {
        boolean next = !client.isVoiceMuted();
        client.setVoiceMuted(next);
        sidebar.setMicMuted(next);
        AppConfig.getInstance().setVoiceMuted(next);
        AppConfig.getInstance().save();
        ToastNotification.show(this, next ? "Microphone: ĐÃ TẮT (Muted)" : "Microphone: ĐÃ BẬT (Live)",
                next ? ToastNotification.Type.WARNING : ToastNotification.Type.SUCCESS);
    }

    @Override
    public void onDeafenToggled() {
        boolean next = !client.isVoiceDeafened();
        client.setVoiceDeafened(next);
        sidebar.setDeafened(next);
        AppConfig.getInstance().setVoiceDeafened(next);
        AppConfig.getInstance().save();
        ToastNotification.show(this, next ? "Tai nghe: ĐÃ TẮT TIẾNG (Deafened)" : "Tai nghe: ĐÃ BẬT TIẾNG",
                next ? ToastNotification.Type.WARNING : ToastNotification.Type.SUCCESS);
    }

    @Override
    public void onVolumeChanged(double volume) {
        client.setVoiceVolume(volume);
        AppConfig.getInstance().setVoiceVolume(volume);
        AppConfig.getInstance().save();
    }

    @Override
    public void onOpenDirectMessage(String username) {
        openOrCreateTimeline("user:" + username.toLowerCase(), username, true);
        sidebar.selectDirectMessage(username);
    }

    @Override
    public void onCloseDrawerRequested() {
        // Handled internally in ParticipantListPanel
    }

    @Override
    public void onSendFile(String receiverOrRoom, File file, boolean encrypt) {
        if (file == null || !file.exists()) return;
        new Thread(() -> {
            try {
                boolean isRoom = receiverOrRoom.startsWith("[Nhóm]") || receiverOrRoom.startsWith("room:")
                        || (client != null && client.getAvailableRooms().stream().anyMatch(r -> r.equalsIgnoreCase(receiverOrRoom.replace("[Nhóm] # ", "").trim())));
                String target = receiverOrRoom.replace("[Nhóm] # ", "").replace("[Nhóm]", "").replace("room:", "").trim();
                FileMetadata meta;
                if (isRoom) {
                    meta = client.initiateGroupFileSend(target, file, encrypt, null);
                } else {
                    meta = client.initiateFileSend(target, file, encrypt, null);
                }

                String activeKey = isRoom ? "room:" + target.toLowerCase() : "user:" + target.toLowerCase();
                ConversationTimeline timeline = openOrCreateTimeline(activeKey, target, !isRoom);
                if (timeline != null) {
                    timeline.addFileTransfer(meta, false, client.getAuthenticatedUser(), new ChatMessageView.FileActionListener() {
                        @Override public void onAcceptFile(FileMetadata m) {}
                        @Override public void onRejectFile(FileMetadata m) {}
                        @Override public void onOpenFile(File f) { openFileInDesktop(f); }
                    });
                }
                activeTransfersCount++;
                String partner = isRoom ? "[Nhóm] #" + target : target;
                transfersDialog.addOrUpdateTransferRow(meta.getTransferId(), meta.getFileName(), meta.getFileSize(), partner, 0, "Đang gửi đề nghị...");
                ToastNotification.show(this, "Đã bắt đầu truyền tệp: " + file.getName(), ToastNotification.Type.SUCCESS);
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> ToastNotification.show(this, "Lỗi gửi tệp: " + ex.getMessage(), ToastNotification.Type.ERROR));
            }
        }).start();
    }

    @Override
    public void onCancelTransfer(String transferId) {
        try {
            client.cancelTransfer(transferId);
            ToastNotification.show(this, "Đã gửi yêu cầu hủy truyền tệp.", ToastNotification.Type.INFO);
        } catch (Exception ex) {
            ToastNotification.show(this, "Lỗi hủy truyền: " + ex.getMessage(), ToastNotification.Type.ERROR);
        }
    }

    private void openFileInDesktop(File file) {
        try {
            if (file != null && file.exists()) {
                Desktop.getDesktop().open(file);
            } else {
                Desktop.getDesktop().open(new File("downloads"));
            }
        } catch (Exception ex) {
            ToastNotification.show(this, "Không thể mở tệp: " + ex.getMessage(), ToastNotification.Type.WARNING);
        }
    }

    public void setServerIp(String serverIp) {
        if (authPanel != null && serverIp != null) {
            authPanel.setServerIp(serverIp);
        }
    }

    public ConversationTimeline getTimelineForTesting(String key) {
        return chatTimelines.get(key);
    }

    public ConversationSidebar getSidebarForTesting() {
        return sidebar;
    }
}
