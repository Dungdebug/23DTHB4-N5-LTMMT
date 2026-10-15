package dammtp2p.client.ui;

import dammtp2p.ui.theme.AppIcons;
import dammtp2p.ui.theme.ThemeManager;
import dammtp2p.util.AppConfig;
import dammtp2p.util.NetworkUtils;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.ConnectException;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * Authentication and Server Connection Gateway for DAMMTP2P Mesh Workspace.
 * 
 * Distinctive Architecture:
 * - Two-column split layout or responsive card grid:
 *   Left column: DAMMTP2P Mesh branding, node telemetry (ZeroTier IP), and server hosting option.
 *   Right column: Connection configuration (IP, Port, Ping status), User Credentials, and Authentication triggers.
 * - Non-blocking asynchronous connection verification.
 * - No auto-fill or accidental auto-login.
 */
public class AuthenticationPanel extends JPanel {

    public interface AuthListener {
        void onLogin(String serverIp, int port, String username, String password);
        void onRegister(String serverIp, int port, String username, String password);
        void onToggleHostServer();
    }

    public enum StatusType {
        INFO(ThemeManager.ACCENT_CYAN),
        SUCCESS(ThemeManager.SUCCESS),
        ERROR(ThemeManager.DANGER);

        final Color color;
        StatusType(Color c) { this.color = c; }
    }

    private final AuthListener listener;
    private JTextField txtServerIp;
    private JTextField txtServerPort;
    private JButton btnTestConnection;
    private JTextField txtUsername;
    private JPasswordField txtPassword;
    private JButton btnTogglePassword;
    private boolean isPasswordVisible = false;

    private JButton btnLogin;
    private JButton btnRegister;
    private JButton btnToggleHostServer;
    private JLabel lblHostState;

    private JPanel statusBanner;
    private JLabel lblStatusText;
    private JButton btnToggleDevMode;
    private JPanel demoChipsContainer;

    private String localZeroTierIp = "127.0.0.1";

    public AuthenticationPanel(AuthListener listener, String initialUsername) {
        this.listener = listener;
        setLayout(new BorderLayout());
        setBackground(new Color(0x0A, 0x0E, 0x17));

        resolveLocalIp();
        initLayout(initialUsername);
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // 1. Deep space base
        g2.setColor(new Color(0x0A, 0x0E, 0x17));
        g2.fillRect(0, 0, w, h);

        // 2. Cyber radial glow top-left (Electric Cyan / Teal)
        if (w > 0 && h > 0) {
            java.awt.RadialGradientPaint cyanGlow = new java.awt.RadialGradientPaint(
                    w * 0.25f, h * 0.25f, Math.max(w, h) * 0.55f,
                    new float[]{0.0f, 0.45f, 1.0f},
                    new Color[]{new Color(0x00, 0xF2, 0xFE, 85), new Color(0x0D, 0x94, 0x88, 30), new Color(0, 0, 0, 0)}
            );
            g2.setPaint(cyanGlow);
            g2.fillRect(0, 0, w, h);

            // 3. Cyber radial glow bottom-right (Deep Violet / Indigo)
            java.awt.RadialGradientPaint violetGlow = new java.awt.RadialGradientPaint(
                    w * 0.78f, h * 0.75f, Math.max(w, h) * 0.6f,
                    new float[]{0.0f, 0.5f, 1.0f},
                    new Color[]{new Color(0x63, 0x66, 0xF1, 75), new Color(0x3B, 0x82, 0xF6, 25), new Color(0, 0, 0, 0)}
            );
            g2.setPaint(violetGlow);
            g2.fillRect(0, 0, w, h);

            // 4. Subtle cyber grid dots
            g2.setColor(new Color(255, 255, 255, 18));
            int step = 28;
            for (int x = 14; x < w; x += step) {
                for (int y = 14; y < h; y += step) {
                    g2.fillRect(x, y, 1, 1);
                }
            }
        }

        g2.dispose();
    }

    private void resolveLocalIp() {
        try {
            java.util.Optional<NetworkUtils.NetworkCandidate> zt = NetworkUtils.findPrimaryZeroTierAddress();
            if (zt.isPresent()) {
                localZeroTierIp = zt.get().getIpAddress();
            } else {
                java.util.List<String> usable = NetworkUtils.getUsableIpv4Addresses();
                if (!usable.isEmpty()) {
                    localZeroTierIp = usable.get(0);
                } else {
                    localZeroTierIp = "127.0.0.1";
                }
            }
        } catch (Exception e) {
            localZeroTierIp = "127.0.0.1";
        }
    }

    private void initLayout(String initialUsername) {
        // Outer wrapper with scroll support for smaller displays
        JPanel centerWrapper = new JPanel(new GridBagLayout());
        centerWrapper.setOpaque(false);

        // Main Dual-Card Container (840 x 480) - Balanced height without redundant bottom gap
        JPanel splitContainer = new JPanel(new GridLayout(1, 2, 2, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // Deep ambient shadow
                g2.setColor(new Color(0, 0, 0, 90));
                g2.fillRoundRect(3, 3, getWidth() - 6, getHeight() - 6, 20, 20);

                // Glassmorphism card body (deep slate translucent)
                g2.setColor(new Color(0x16, 0x1B, 0x27, 245));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);

                // Shimmering multi-tone border: Electric Cyan -> Signature Teal -> Violet
                LinearGradientPaint borderGrad = new LinearGradientPaint(
                        0, 0, getWidth(), getHeight(),
                        new float[]{0.0f, 0.45f, 1.0f},
                        new Color[]{ThemeManager.ACCENT_ELECTRIC_CYAN, ThemeManager.ACCENT_PRIMARY, ThemeManager.ACCENT_VIOLET}
                );
                g2.setPaint(borderGrad);
                g2.setStroke(new BasicStroke(1.5f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
                g2.dispose();
            }
        };
        splitContainer.setPreferredSize(new Dimension(860, 500));
        splitContainer.setOpaque(false);

        // =====================================================================
        //  LEFT COLUMN: Brand identity, Mesh Overview, Node IP, Host Server
        // =====================================================================
        JPanel leftBrandCard = new JPanel();
        leftBrandCard.setLayout(new BoxLayout(leftBrandCard, BoxLayout.Y_AXIS));
        leftBrandCard.setOpaque(false);
        leftBrandCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 0, 1, ThemeManager.BORDER_SUBTLE),
                BorderFactory.createEmptyBorder(22, 26, 22, 26)
        ));

        // Brand Icon (50px) with pulsar orbits & glowing core
        JPanel brandIcon = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Glowing outer halo
                java.awt.RadialGradientPaint halo = new java.awt.RadialGradientPaint(
                        25, 25, 25,
                        new float[]{0.0f, 0.7f, 1.0f},
                        new Color[]{new Color(0x06, 0xB6, 0xD4, 70), new Color(0x0D, 0x94, 0x88, 25), new Color(0, 0, 0, 0)}
                );
                g2.setPaint(halo);
                g2.fillOval(0, 0, 50, 50);

                // Orbit ring
                g2.setColor(new Color(0x06, 0xB6, 0xD4, 130));
                g2.setStroke(new BasicStroke(1.4f));
                g2.drawOval(4, 4, 42, 42);

                // Diamond constellation mesh lines
                g2.setColor(ThemeManager.ACCENT_ELECTRIC_CYAN);
                g2.setStroke(new BasicStroke(1.6f));
                g2.drawLine(25, 8, 42, 25);
                g2.drawLine(42, 25, 25, 42);
                g2.drawLine(25, 42, 8, 25);
                g2.drawLine(8, 25, 25, 8);

                // Cross connections
                g2.setColor(new Color(0x2D, 0xD4, 0xBF, 160));
                g2.drawLine(25, 8, 25, 42);
                g2.drawLine(8, 25, 42, 25);

                // Satellite Nodes
                g2.setColor(ThemeManager.ACCENT_PRIMARY);
                g2.fillOval(22, 5, 6, 6);
                g2.fillOval(39, 22, 6, 6);
                g2.fillOval(22, 39, 6, 6);
                g2.fillOval(5, 22, 6, 6);

                // Radiant center core
                g2.setColor(ThemeManager.ACCENT_ELECTRIC_CYAN);
                g2.fillOval(20, 20, 10, 10);
                g2.setColor(Color.WHITE);
                g2.fillOval(23, 23, 4, 4);

                g2.dispose();
            }
        };
        brandIcon.setPreferredSize(new Dimension(50, 50));
        brandIcon.setMaximumSize(new Dimension(50, 50));
        brandIcon.setAlignmentX(0.0f);

        JLabel lblTitle = new JLabel("DAMMTP2P");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 20));
        lblTitle.setForeground(ThemeManager.TEXT_PRIMARY);
        lblTitle.setAlignmentX(0.0f);

        JLabel lblSub = new JLabel("Mesh Workspace & P2P Data Hub");
        lblSub.setFont(ThemeManager.FONT_SMALL_BOLD);
        lblSub.setForeground(ThemeManager.ACCENT_CYAN);
        lblSub.setAlignmentX(0.0f);

        JLabel lblDesc = new JLabel("<html>Giao tiếp nhóm phân tán, bảo mật AES-128-GCM<br/>và truyền tệp tới 20 GiB qua ZeroTier / LAN.</html>");
        lblDesc.setFont(ThemeManager.FONT_SMALL);
        lblDesc.setForeground(ThemeManager.TEXT_MUTED);
        lblDesc.setAlignmentX(0.0f);
        lblDesc.setMaximumSize(new Dimension(380, 52));

        leftBrandCard.add(brandIcon);
        leftBrandCard.add(Box.createVerticalStrut(10));
        leftBrandCard.add(lblTitle);
        leftBrandCard.add(lblSub);
        leftBrandCard.add(Box.createVerticalStrut(6));
        leftBrandCard.add(lblDesc);
        leftBrandCard.add(Box.createVerticalStrut(10));

        // 4 High-tech Feature Pills Matrix
        JPanel featureGrid = new JPanel(new GridLayout(2, 2, 8, 6));
        featureGrid.setOpaque(false);
        featureGrid.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
        featureGrid.setAlignmentX(0.0f);

        featureGrid.add(ThemeManager.createBadge("● ZeroTier P2P LAN", ThemeManager.ACCENT_ELECTRIC_CYAN, new Color(0x00, 0xF2, 0xFE, 35)));
        featureGrid.add(ThemeManager.createBadge("● AES-128-GCM E2EE", ThemeManager.ACCENT_VIOLET, new Color(0x63, 0x66, 0xF1, 35)));
        featureGrid.add(ThemeManager.createBadge("● Relay Tệp 20 GiB", ThemeManager.ACCENT_AMBER, new Color(0xF5, 0x9E, 0x0B, 35)));
        featureGrid.add(ThemeManager.createBadge("● Voice RTC 16kHz", ThemeManager.SUCCESS, new Color(0x10, 0xB9, 0x81, 35)));

        leftBrandCard.add(featureGrid);
        leftBrandCard.add(Box.createVerticalStrut(12));

        // Node IP Pill
        JPanel ipPill = new JPanel(new BorderLayout(8, 0));
        ipPill.setBackground(ThemeManager.BG_INPUT);
        ipPill.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(5, 10, 5, 8)
        ));
        ipPill.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
        ipPill.setAlignmentX(0.0f);

        JLabel lblIpInfo = new JLabel("IP máy này: " + localZeroTierIp);
        lblIpInfo.setFont(ThemeManager.FONT_SMALL);
        lblIpInfo.setForeground(ThemeManager.TEXT_SECONDARY);

        JButton btnCopyIp = new JButton("Sao chép");
        btnCopyIp.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        btnCopyIp.setForeground(ThemeManager.ACCENT_CYAN);
        btnCopyIp.setBackground(ThemeManager.BG_CARD);
        btnCopyIp.setFocusPainted(false);
        btnCopyIp.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnCopyIp.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(localZeroTierIp), null);
            setStatus("Đã sao chép IP máy này (" + localZeroTierIp + ") vào bộ nhớ tạm!", StatusType.INFO);
        });

        ipPill.add(lblIpInfo, BorderLayout.CENTER);
        ipPill.add(btnCopyIp, BorderLayout.EAST);
        leftBrandCard.add(ipPill);
        leftBrandCard.add(Box.createVerticalStrut(12));

        // Server Host Section (For machine running the central node)
        JPanel hostBox = new JPanel();
        hostBox.setLayout(new BoxLayout(hostBox, BoxLayout.Y_AXIS));
        hostBox.setBackground(ThemeManager.BG_INPUT);
        hostBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.BORDER_SUBTLE, 1),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        hostBox.setMaximumSize(new Dimension(Integer.MAX_VALUE, 94));
        hostBox.setAlignmentX(0.0f);

        JLabel lblHostTitle = new JLabel("Chế Độ Máy Chủ Cục Bộ");
        lblHostTitle.setFont(ThemeManager.FONT_SMALL_BOLD);
        lblHostTitle.setForeground(ThemeManager.TEXT_PRIMARY);

        JLabel lblHostHelp = new JLabel("Chạy Relay nội bộ (Cổng 8888 & 8889)");
        lblHostHelp.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        lblHostHelp.setForeground(ThemeManager.TEXT_MUTED);

        JPanel hostRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        hostRow.setOpaque(false);
        btnToggleHostServer = new JButton("Khởi Động Server", AppIcons.createStatusDot(8, ThemeManager.SUCCESS));
        btnToggleHostServer.setFont(ThemeManager.FONT_SMALL_BOLD);
        btnToggleHostServer.setForeground(Color.WHITE);
        btnToggleHostServer.setBackground(ThemeManager.ACCENT_PRIMARY);
        btnToggleHostServer.setFocusPainted(false);
        btnToggleHostServer.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnToggleHostServer.addActionListener(e -> {
            if (listener != null) listener.onToggleHostServer();
        });

        lblHostState = new JLabel("Trạng thái: Chưa chạy");
        lblHostState.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        lblHostState.setForeground(ThemeManager.TEXT_MUTED);

        hostRow.add(btnToggleHostServer);
        hostRow.add(lblHostState);

        hostBox.add(lblHostTitle);
        hostBox.add(Box.createVerticalStrut(2));
        hostBox.add(lblHostHelp);
        hostBox.add(Box.createVerticalStrut(2));
        hostBox.add(hostRow);

        leftBrandCard.add(hostBox);
        leftBrandCard.add(Box.createVerticalGlue());

        // =====================================================================
        //  RIGHT COLUMN: Connection Parameters & Authentication Form
        // =====================================================================
        JPanel rightFormCard = new JPanel();
        rightFormCard.setLayout(new BoxLayout(rightFormCard, BoxLayout.Y_AXIS));
        rightFormCard.setOpaque(false);
        rightFormCard.setBorder(BorderFactory.createEmptyBorder(22, 26, 22, 26));

        JLabel lblConnectTitle = new JLabel("Đăng Nhập Không Gian Làm Việc");
        lblConnectTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblConnectTitle.setForeground(ThemeManager.TEXT_PRIMARY);
        lblConnectTitle.setAlignmentX(0.0f);
        rightFormCard.add(lblConnectTitle);
        rightFormCard.add(Box.createVerticalStrut(10));

        // 1. Server Address & Port Row (Clearly differentiating 127.0.0.1 vs ZeroTier IP)
        AppConfig cfg = AppConfig.getInstance();
        String defaultServerIp = "127.0.0.1";
        if (cfg.getServerIp() != null && !cfg.getServerIp().trim().isEmpty()) {
            defaultServerIp = cfg.getServerIp().trim();
        }
        String initUser = (initialUsername != null && !initialUsername.trim().isEmpty())
                ? initialUsername.trim()
                : (!cfg.getLastUsername().isEmpty() ? cfg.getLastUsername() : "alice");

        JPanel connRow = new JPanel(new GridLayout(1, 2, 8, 0));
        connRow.setOpaque(false);
        connRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
        connRow.setAlignmentX(0.0f);

        txtServerIp = new JTextField(defaultServerIp);
        txtServerIp.setToolTipText("127.0.0.1 (chạy cùng máy) hoặc IP ZeroTier (kết nối máy khác)");
        styleInput(txtServerIp);
        JPanel pIp = createFormField("ĐỊA CHỈ IP MÁY CHỦ", txtServerIp);

        txtServerPort = new JTextField(String.valueOf(cfg.getControlPort() > 0 ? cfg.getControlPort() : 8888));
        txtServerPort.setToolTipText("Cổng điều khiển TCP (mặc định: 8888)");
        styleInput(txtServerPort);
        JPanel pPort = createFormField("CỔNG (PORT)", txtServerPort);

        connRow.add(pIp);
        connRow.add(pPort);
        rightFormCard.add(connRow);
        rightFormCard.add(Box.createVerticalStrut(3));

        // IP Helper Tip & Ping test button row
        JPanel ipTipRow = new JPanel(new BorderLayout());
        ipTipRow.setOpaque(false);
        ipTipRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
        ipTipRow.setAlignmentX(0.0f);

        JLabel lblIpTip = new JLabel("• 127.0.0.1 (máy này) hoặc IP ZeroTier");
        lblIpTip.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        lblIpTip.setForeground(ThemeManager.TEXT_MUTED);

        btnTestConnection = new JButton("Kiểm tra kết nối Server (Ping)", AppIcons.createStatusDot(7, ThemeManager.ACCENT_CYAN));
        btnTestConnection.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnTestConnection.setForeground(ThemeManager.ACCENT_CYAN);
        btnTestConnection.setFocusPainted(false);
        btnTestConnection.setContentAreaFilled(false);
        btnTestConnection.setBorderPainted(false);
        btnTestConnection.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnTestConnection.addActionListener(e -> testServerConnectionAsync());

        ipTipRow.add(lblIpTip, BorderLayout.WEST);
        ipTipRow.add(btnTestConnection, BorderLayout.EAST);
        rightFormCard.add(ipTipRow);
        rightFormCard.add(Box.createVerticalStrut(6));

        // 2. Username
        txtUsername = new JTextField(initUser);
        styleInput(txtUsername);
        JPanel pUser = createFormField("TÊN ĐĂNG NHẬP", txtUsername);
        pUser.setAlignmentX(0.0f);
        rightFormCard.add(pUser);
        rightFormCard.add(Box.createVerticalStrut(8));

        // 3. Password
        txtPassword = new JPasswordField("");
        styleInput(txtPassword);
        txtPassword.addActionListener(e -> triggerLogin());

        JPanel passwordWrapper = new JPanel(new BorderLayout(4, 0));
        passwordWrapper.setOpaque(false);
        passwordWrapper.setPreferredSize(new Dimension(Integer.MAX_VALUE, 38));
        passwordWrapper.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
        passwordWrapper.add(txtPassword, BorderLayout.CENTER);

        btnTogglePassword = new JButton("Hiện");
        btnTogglePassword.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnTogglePassword.setForeground(ThemeManager.TEXT_MUTED);
        btnTogglePassword.setContentAreaFilled(false);
        btnTogglePassword.setBorderPainted(false);
        btnTogglePassword.setFocusPainted(false);
        btnTogglePassword.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnTogglePassword.addActionListener(e -> {
            isPasswordVisible = !isPasswordVisible;
            if (isPasswordVisible) {
                txtPassword.setEchoChar((char) 0);
                btnTogglePassword.setText("Ẩn");
            } else {
                txtPassword.setEchoChar('\u2022');
                btnTogglePassword.setText("Hiện");
            }
        });
        passwordWrapper.add(btnTogglePassword, BorderLayout.EAST);

        JPanel pPass = createFormField("MẬT KHẨU", passwordWrapper);
        pPass.setAlignmentX(0.0f);
        rightFormCard.add(pPass);
        rightFormCard.add(Box.createVerticalStrut(8));

        // 4. Action Buttons (Login / Register)
        btnLogin = ThemeManager.createGradientButton("Đăng Nhập Không Gian Làm Việc →", ThemeManager.ACCENT_PRIMARY, ThemeManager.ACCENT_ELECTRIC_CYAN, Color.WHITE);
        btnLogin.setFont(new Font("Segoe UI", Font.BOLD, 13));
        btnLogin.setPreferredSize(new Dimension(Integer.MAX_VALUE, 42));
        btnLogin.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        btnLogin.setAlignmentX(0.0f);
        btnLogin.addActionListener(e -> triggerLogin());
        rightFormCard.add(btnLogin);
        rightFormCard.add(Box.createVerticalStrut(6));

        btnRegister = new JButton("Đăng ký tài khoản mới");
        btnRegister.setFont(ThemeManager.FONT_SMALL);
        btnRegister.setForeground(ThemeManager.TEXT_SECONDARY);
        btnRegister.setContentAreaFilled(false);
        btnRegister.setBorderPainted(false);
        btnRegister.setFocusPainted(false);
        btnRegister.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnRegister.setAlignmentX(0.0f);
        btnRegister.addActionListener(e -> triggerRegister());
        rightFormCard.add(btnRegister);
        rightFormCard.add(Box.createVerticalStrut(6));

        // 5. Development Mode: Collapsible Demo Test Accounts (Hidden by default for production)
        btnToggleDevMode = new JButton("[+] Chế độ phát triển (Tài khoản mẫu)");
        btnToggleDevMode.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btnToggleDevMode.setForeground(new Color(0x64, 0x74, 0x8B));
        btnToggleDevMode.setContentAreaFilled(false);
        btnToggleDevMode.setBorderPainted(false);
        btnToggleDevMode.setFocusPainted(false);
        btnToggleDevMode.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnToggleDevMode.setAlignmentX(0.0f);
        btnToggleDevMode.addActionListener(e -> {
            boolean show = !demoChipsContainer.isVisible();
            demoChipsContainer.setVisible(show);
            btnToggleDevMode.setText(show ? "[-] Thu gọn tài khoản mẫu" : "[+] Chế độ phát triển (Tài khoản mẫu)");
            btnToggleDevMode.setForeground(show ? ThemeManager.ACCENT_CYAN : new Color(0x64, 0x74, 0x8B));
            revalidate();
            repaint();
        });
        rightFormCard.add(btnToggleDevMode);
        rightFormCard.add(Box.createVerticalStrut(4));

        demoChipsContainer = new JPanel(new GridLayout(1, 4, 6, 0));
        demoChipsContainer.setOpaque(false);
        demoChipsContainer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        demoChipsContainer.setAlignmentX(0.0f);
        demoChipsContainer.setVisible(false); // Collapsed by default on standard release

        Color[] chipColors = {
            new Color(0x38, 0xBD, 0xF8), // Alice - Electric Blue
            new Color(0x34, 0xD3, 0x99), // Bob - Emerald
            new Color(0xFB, 0xBF, 0x24), // Charlie - Amber
            new Color(0xC0, 0x84, 0xFC)  // Admin - Purple
        };
        String[] demoNames = {"alice", "bob", "charlie", "admin"};
        for (int i = 0; i < demoNames.length; i++) {
            final String testAcc = demoNames[i];
            final Color accColor = chipColors[i];
            JButton chip = new JButton(testAcc) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    boolean hover = getModel().isRollover();
                    Color bg = hover ? new Color(accColor.getRed(), accColor.getGreen(), accColor.getBlue(), 55)
                                     : new Color(accColor.getRed(), accColor.getGreen(), accColor.getBlue(), 25);
                    g2.setColor(bg);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                    g2.setColor(hover ? accColor : new Color(accColor.getRed(), accColor.getGreen(), accColor.getBlue(), 130));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            chip.setFont(ThemeManager.FONT_SMALL_BOLD);
            chip.setForeground(accColor);
            chip.setContentAreaFilled(false);
            chip.setBorderPainted(false);
            chip.setFocusPainted(false);
            chip.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            chip.addActionListener(e -> {
                txtUsername.setText(testAcc);
                txtPassword.setText("password123");
                setStatus("Đã nạp tài khoản demo @" + testAcc + " (Mật khẩu: password123)", StatusType.INFO);
            });
            demoChipsContainer.add(chip);
        }
        rightFormCard.add(demoChipsContainer);
        rightFormCard.add(Box.createVerticalStrut(6));

        // 6. Inline Status Banner
        statusBanner = new JPanel(new BorderLayout(8, 0));
        statusBanner.setBackground(ThemeManager.BG_INPUT);
        statusBanner.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        statusBanner.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        statusBanner.setAlignmentX(0.0f);
        statusBanner.setVisible(false);

        lblStatusText = new JLabel("");
        lblStatusText.setFont(ThemeManager.FONT_SMALL);
        statusBanner.add(lblStatusText, BorderLayout.CENTER);
        rightFormCard.add(statusBanner);
        rightFormCard.add(Box.createVerticalGlue());

        // Combine left & right cards
        splitContainer.add(leftBrandCard);
        splitContainer.add(rightFormCard);

        centerWrapper.add(splitContainer, new GridBagConstraints());

        JScrollPane scrollPane = new JScrollPane(centerWrapper);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        add(scrollPane, BorderLayout.CENTER);
    }

    private JPanel createFormField(String label, JPanel field) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
        p.setAlignmentX(0.0f);

        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 10));
        lbl.setForeground(ThemeManager.TEXT_MUTED);
        lbl.setAlignmentX(0.0f);

        field.setAlignmentX(0.0f);

        p.add(lbl);
        p.add(Box.createVerticalStrut(4));
        p.add(field);
        return p;
    }

    private JPanel createFormField(String label, JTextField field) {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 58));
        p.setAlignmentX(0.0f);

        JLabel lbl = new JLabel(label);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 10));
        lbl.setForeground(ThemeManager.TEXT_MUTED);
        lbl.setAlignmentX(0.0f);

        field.setAlignmentX(0.0f);

        p.add(lbl);
        p.add(Box.createVerticalStrut(4));
        p.add(field);
        return p;
    }

    private void styleInput(JTextField f) {
        f.setFont(ThemeManager.FONT_BODY);
        f.setBackground(ThemeManager.BG_INPUT);
        f.setForeground(ThemeManager.TEXT_PRIMARY);
        f.setCaretColor(Color.WHITE);
        f.putClientProperty("Component.arc", 10);
        f.putClientProperty("Component.focusWidth", 2);
        f.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));
        f.setPreferredSize(new Dimension(Integer.MAX_VALUE, 38));
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 38));
    }

    public void setStatus(String message, StatusType type) {
        if (message == null || message.trim().isEmpty()) {
            statusBanner.setVisible(false);
        } else {
            lblStatusText.setText(message);
            lblStatusText.setForeground(type.color);
            statusBanner.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(type.color, 1),
                    BorderFactory.createEmptyBorder(6, 12, 6, 12)
            ));
            statusBanner.setVisible(true);
        }
        revalidate();
        repaint();
    }

    private void testServerConnectionAsync() {
        String ip = txtServerIp.getText().trim();
        int port = 8888;
        try { port = Integer.parseInt(txtServerPort.getText().trim()); } catch (Exception ignored) {}
        final int targetPort = port;

        btnTestConnection.setEnabled(false);
        setStatus("Đang kiểm tra kết nối tới " + ip + ":" + targetPort + "...", StatusType.INFO);

        new Thread(() -> {
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress(ip, targetPort), 2500);
                SwingUtilities.invokeLater(() -> {
                    btnTestConnection.setEnabled(true);
                    setStatus("✓ Máy chủ đang trực tuyến tại " + ip + ":" + targetPort + "!", StatusType.SUCCESS);
                });
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    btnTestConnection.setEnabled(true);
                    String reason = ex.getMessage();
                    if (ex instanceof ConnectException) {
                        reason = "Server chưa khởi động hoặc cổng bị tường lửa chặn";
                    } else if (ex instanceof SocketTimeoutException) {
                        reason = "Hết thời gian chờ phản hồi (Timeout > 2.5s)";
                    }
                    setStatus("✕ Không thể kết nối tới " + ip + ":" + targetPort + " (" + reason + ")", StatusType.ERROR);
                });
            }
        }, "ping-server-worker").start();
    }

    private void triggerLogin() {
        if (listener != null) {
            String ip = txtServerIp.getText().trim();
            int port = 8888;
            try { port = Integer.parseInt(txtServerPort.getText().trim()); } catch (Exception ignored) {}
            String user = txtUsername.getText().trim();
            String pass = new String(txtPassword.getPassword());

            if (user.isEmpty() || pass.isEmpty()) {
                setStatus("Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu!", StatusType.ERROR);
                return;
            }

            setStatus("Đang kết nối và đăng nhập tới máy chủ " + ip + ":" + port + "...", StatusType.INFO);
            listener.onLogin(ip, port, user, pass);
        }
    }

    private void triggerRegister() {
        if (listener != null) {
            String ip = txtServerIp.getText().trim();
            int port = 8888;
            try { port = Integer.parseInt(txtServerPort.getText().trim()); } catch (Exception ignored) {}
            String user = txtUsername.getText().trim();
            String pass = new String(txtPassword.getPassword());

            if (user.isEmpty() || pass.isEmpty()) {
                setStatus("Vui lòng nhập đầy đủ tên đăng nhập và mật khẩu để đăng ký!", StatusType.ERROR);
                return;
            }

            setStatus("Đang gửi yêu cầu đăng ký tới máy chủ " + ip + ":" + port + "...", StatusType.INFO);
            listener.onRegister(ip, port, user, pass);
        }
    }

    public void setHostStatus(boolean isRunning) {
        if (btnToggleHostServer != null) {
            if (isRunning) {
                btnToggleHostServer.setText("Dừng Server");
                btnToggleHostServer.setBackground(ThemeManager.DANGER);
                lblHostState.setText("Trạng thái: Đang chạy (Port 8888 & 8889)");
                lblHostState.setForeground(ThemeManager.SUCCESS);
            } else {
                btnToggleHostServer.setText("Khởi Động Server");
                btnToggleHostServer.setBackground(ThemeManager.ACCENT_PRIMARY);
                lblHostState.setText("Trạng thái: Chưa chạy");
                lblHostState.setForeground(ThemeManager.TEXT_MUTED);
            }
        }
    }

    public void setServerIp(String ip) {
        if (txtServerIp != null && ip != null && !ip.trim().isEmpty()) {
            txtServerIp.setText(ip.trim());
        }
    }

    public void setDemoAccountsVisible(boolean visible) {
        if (demoChipsContainer != null) {
            demoChipsContainer.setVisible(visible);
            if (btnToggleDevMode != null) {
                btnToggleDevMode.setText(visible ? "[-] Thu gọn tài khoản mẫu" : "[+] Chế độ phát triển (Tài khoản mẫu)");
                btnToggleDevMode.setForeground(visible ? ThemeManager.ACCENT_CYAN : new Color(0x64, 0x74, 0x8B));
            }
            revalidate();
            repaint();
        }
    }
}
