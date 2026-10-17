package dammtp2p.server.ui;

import dammtp2p.server.ServerCore;
import dammtp2p.server.ServerFileRelayManager;
import dammtp2p.ui.theme.AppIcons;
import dammtp2p.ui.theme.ThemeManager;
import dammtp2p.util.Constants;
import dammtp2p.util.FormatUtils;
import dammtp2p.util.NetworkUtils;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.util.List;
import java.util.Optional;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;

/**
 * Reusable Server Mission Control Dashboard Panel.
 * Can be embedded directly inside the unified ClientMainFrame or used inside standalone ServerFrame.
 */
public class ServerDashboardPanel extends JPanel implements ServerCore.ServerEventListener {

    private ServerCore server;

    private JLabel lblStatus;
    private JLabel lblZeroTierIp;
    private JLabel lblControlPort;
    private JLabel lblFilePort;
    private JLabel lblOnlineCount;

    private DefaultTableModel userTableModel;
    private JTable userTable;
    private DefaultTableModel transferTableModel;
    private JTable transferTable;
    private JTextArea logTextArea;

    private JButton btnCopyIp;
    private JButton btnClearLog;

    public ServerDashboardPanel(ServerCore server) {
        this.server = server;
        initComponents();
        if (server != null) {
            bindServer(server);
        } else {
            updateRunningState(false);
        }
    }

    public ServerDashboardPanel() {
        this(null);
    }

    private void initComponents() {
        setLayout(new BorderLayout(0, 10));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        // 1. TOP HEADER & KPI METRICS
        JPanel northContainer = new JPanel();
        northContainer.setLayout(new BoxLayout(northContainer, BoxLayout.Y_AXIS));
        northContainer.setOpaque(false);

        // Top Toolbar inside dashboard
        JPanel topToolbar = new JPanel(new BorderLayout(10, 0));
        topToolbar.setBackground(ThemeManager.BG_PANEL);
        topToolbar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(8, 14, 8, 14)
        ));

        JPanel leftTitle = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        leftTitle.setOpaque(false);
        JLabel iconLogo = new JLabel(AppIcons.createNetworkIconSafe(20, ThemeManager.SUCCESS));
        JLabel lblTitle = new JLabel("BẢNG ĐIỀU KHIỂN MÁY CHỦ (MISSION CONTROL)");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblTitle.setForeground(ThemeManager.TEXT_PRIMARY);

        JLabel versionChip = ThemeManager.createBadge("TCP SERVER", ThemeManager.TEXT_MUTED, ThemeManager.BG_INPUT);
        leftTitle.add(iconLogo);
        leftTitle.add(lblTitle);
        leftTitle.add(versionChip);

        JPanel rightControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightControls.setOpaque(false);

        btnCopyIp = new JButton("📋 Sao Chép IP ZeroTier");
        btnCopyIp.setFont(ThemeManager.FONT_SMALL);
        btnCopyIp.setFocusPainted(false);
        btnCopyIp.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnCopyIp.addActionListener(e -> copyZeroTierIp());

        btnClearLog = new JButton("🗑 Xóa Log");
        btnClearLog.setFont(ThemeManager.FONT_SMALL);
        btnClearLog.setFocusPainted(false);
        btnClearLog.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnClearLog.addActionListener(e -> {
            if (logTextArea != null) logTextArea.setText("");
        });

        rightControls.add(btnCopyIp);
        rightControls.add(btnClearLog);

        topToolbar.add(leftTitle, BorderLayout.WEST);
        topToolbar.add(rightControls, BorderLayout.EAST);

        // KPI Cards Panel (5 Metrics)
        JPanel statsPanel = new JPanel(new GridLayout(1, 5, 8, 0));
        statsPanel.setOpaque(false);
        statsPanel.setPreferredSize(new Dimension(800, 72));

        lblStatus = new JLabel("● CHỜ KÍCH HOẠT", SwingConstants.CENTER);
        lblZeroTierIp = new JLabel("127.0.0.1", SwingConstants.CENTER);
        lblControlPort = new JLabel(String.valueOf(Constants.DEFAULT_CONTROL_PORT), SwingConstants.CENTER);
        lblFilePort = new JLabel(String.valueOf(Constants.DEFAULT_FILE_TRANSFER_PORT), SwingConstants.CENTER);
        lblOnlineCount = new JLabel("0", SwingConstants.CENTER);

        statsPanel.add(createKpiCard("TRẠNG THÁI", lblStatus, ThemeManager.DANGER));
        statsPanel.add(createKpiCard("IP ZEROTIER", lblZeroTierIp, ThemeManager.ACCENT_PRIMARY));
        statsPanel.add(createKpiCard("CỔNG LỆNH (CHAT)", lblControlPort, ThemeManager.TEXT_PRIMARY));
        statsPanel.add(createKpiCard("CỔNG TỆP (RELAY)", lblFilePort, ThemeManager.TEXT_PRIMARY));
        statsPanel.add(createKpiCard("ONLINE USERS", lblOnlineCount, ThemeManager.SUCCESS));

        northContainer.add(topToolbar);
        northContainer.add(Box.createVerticalStrut(8));
        northContainer.add(statsPanel);
        add(northContainer, BorderLayout.NORTH);

        // 2. CENTER TABS: Sessions, Relay Transfers, Console Logs
        JTabbedPane tabbedPane = new JTabbedPane();

        // Tab 1: Real-time Event Logs Console
        JPanel logTab = new JPanel(new BorderLayout(0, 4));
        logTab.setOpaque(false);

        logTextArea = new JTextArea();
        logTextArea.setEditable(false);
        logTextArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logTextArea.setBackground(ThemeManager.BG_INPUT);
        logTextArea.setForeground(new Color(226, 232, 240));
        logTextArea.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        JScrollPane scrollLog = new JScrollPane(logTextArea);
        scrollLog.setBorder(ThemeManager.createCardBorder(0, 0, 0, 0));
        logTab.add(scrollLog, BorderLayout.CENTER);
        tabbedPane.addTab("📋 Nhật Ký Hoạt Động (Console Logs)", logTab);

        // Tab 2: Online Users Table
        JPanel usersTab = new JPanel(new BorderLayout(0, 6));
        usersTab.setOpaque(false);

        userTableModel = new DefaultTableModel(new String[]{"STT", "Tên Người Dùng", "Quyền Hạn", "Trạng Thái"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        userTable = new JTable(userTableModel);
        userTable.setBackground(ThemeManager.BG_INPUT);
        userTable.setForeground(ThemeManager.TEXT_PRIMARY);
        userTable.setFillsViewportHeight(true);
        userTable.setShowGrid(true);
        userTable.setGridColor(ThemeManager.BORDER_COLOR);
        userTable.setRowHeight(28);

        DefaultTableCellRenderer centerRenderer = new DefaultTableCellRenderer();
        centerRenderer.setHorizontalAlignment(SwingConstants.CENTER);
        userTable.getColumnModel().getColumn(0).setCellRenderer(centerRenderer);
        userTable.getColumnModel().getColumn(3).setCellRenderer(new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
                JLabel lbl = (JLabel) super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                lbl.setForeground(ThemeManager.SUCCESS);
                lbl.setHorizontalAlignment(SwingConstants.CENTER);
                lbl.setFont(ThemeManager.FONT_BODY_BOLD);
                return lbl;
            }
        });

        JScrollPane scrollUsers = new JScrollPane(userTable);
        scrollUsers.setBorder(ThemeManager.createCardBorder(0, 0, 0, 0));
        usersTab.add(scrollUsers, BorderLayout.CENTER);
        tabbedPane.addTab("👥 Người Dùng Trực Tuyến (Online Sessions)", usersTab);

        // Tab 3: Active File Relay Sessions Table
        JPanel transfersTab = new JPanel(new BorderLayout(0, 6));
        transfersTab.setOpaque(false);

        transferTableModel = new DefaultTableModel(new String[]{
            "Mã Phiên (ID)", "Bên Gửi", "Bên Nhận", "Tên Tệp", "Kích Thước", "Tiến Độ", "Tốc Độ", "Trạng Thái"
        }, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        transferTable = new JTable(transferTableModel);
        transferTable.setBackground(ThemeManager.BG_INPUT);
        transferTable.setForeground(ThemeManager.TEXT_PRIMARY);
        transferTable.setFillsViewportHeight(true);
        transferTable.setShowGrid(true);
        transferTable.setGridColor(ThemeManager.BORDER_COLOR);
        transferTable.setRowHeight(28);

        transferTable.getColumnModel().getColumn(5).setCellRenderer(new ProgressCellRenderer());

        JScrollPane scrollTransfers = new JScrollPane(transferTable);
        scrollTransfers.setBorder(ThemeManager.createCardBorder(0, 0, 0, 0));
        transfersTab.add(scrollTransfers, BorderLayout.CENTER);
        tabbedPane.addTab("⚡ Tiếp Sức Truyền Tệp (Port 8889 Relays)", transfersTab);

        add(tabbedPane, BorderLayout.CENTER);

        updateNetworkInfo();
    }

    private JPanel createKpiCard(String title, JLabel valLabel, Color valColor) {
        JPanel card = new JPanel(new BorderLayout(4, 4)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0x16, 0x1B, 0x27, 240));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                // Top accent bar
                g2.setColor(valColor);
                g2.fillRoundRect(0, 0, getWidth() - 1, 3, 3, 3);
                // Subtle border
                g2.setColor(new Color(valColor.getRed(), valColor.getGreen(), valColor.getBlue(), 50));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

        JLabel lblTitle = new JLabel(title, SwingConstants.CENTER);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 10));
        lblTitle.setForeground(ThemeManager.TEXT_MUTED);

        valLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        valLabel.setForeground(valColor);

        card.add(lblTitle, BorderLayout.NORTH);
        card.add(valLabel, BorderLayout.CENTER);
        return card;
    }

    public synchronized void bindServer(ServerCore serverCore) {
        if (this.server != null) {
            this.server.removeListener(this);
        }
        this.server = serverCore;
        if (this.server != null) {
            this.server.addListener(this);
            lblControlPort.setText(String.valueOf(this.server.getControlPort()));
            lblFilePort.setText(String.valueOf(this.server.getFilePort()));
            updateRunningState(this.server.isRunning());
            lblOnlineCount.setText(String.valueOf(this.server.getSessionManager().getOnlineUsernames().size()));

            logTextArea.setText("");
            for (String ev : this.server.getRecentEvents()) {
                logTextArea.append(ev + "\n");
            }
            if (logTextArea.getDocument().getLength() > 0) {
                logTextArea.setCaretPosition(logTextArea.getDocument().getLength());
            }
            refreshLists();
        } else {
            updateRunningState(false);
        }
        updateNetworkInfo();
    }

    public synchronized void unbindServer() {
        if (this.server != null) {
            this.server.removeListener(this);
            this.server = null;
        }
        updateRunningState(false);
    }

    public void updateNetworkInfo() {
        Optional<NetworkUtils.NetworkCandidate> zt = NetworkUtils.findPrimaryZeroTierAddress();
        if (zt.isPresent()) {
            lblZeroTierIp.setText(zt.get().getIpAddress());
        } else {
            List<NetworkUtils.NetworkCandidate> list = NetworkUtils.getCandidateAddresses();
            if (!list.isEmpty()) {
                lblZeroTierIp.setText(list.get(0).getIpAddress());
            } else {
                lblZeroTierIp.setText("127.0.0.1");
            }
        }
    }

    public void updateRunningState(boolean running) {
        lblStatus.setText(running ? "● ĐANG CHẠY" : "● CHỜ KÍCH HOẠT");
        lblStatus.setForeground(running ? ThemeManager.SUCCESS : ThemeManager.DANGER);
    }

    private void copyZeroTierIp() {
        String ip = lblZeroTierIp.getText();
        if (ip != null && !ip.trim().isEmpty()) {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(ip.trim()), null);
            if (server != null) {
                server.logEvent("[INFO] Đã sao chép IP Máy Chủ: " + ip);
            }
        }
    }

    @Override
    public void onEvent(String message) {
        SwingUtilities.invokeLater(() -> {
            logTextArea.append(message + "\n");
            logTextArea.setCaretPosition(logTextArea.getDocument().getLength());
            refreshLists();
        });
    }

    @Override
    public void onStateChanged(boolean running) {
        SwingUtilities.invokeLater(() -> {
            updateRunningState(running);
            refreshLists();
        });
    }

    @Override
    public void onUsersChanged(int count) {
        SwingUtilities.invokeLater(() -> {
            lblOnlineCount.setText(String.valueOf(count));
            refreshLists();
        });
    }

    @Override
    public void onTransfersChanged(int count) {
        SwingUtilities.invokeLater(this::refreshLists);
    }

    public void refreshLists() {
        if (server == null) {
            userTableModel.setRowCount(0);
            transferTableModel.setRowCount(0);
            return;
        }

        // Refresh users
        userTableModel.setRowCount(0);
        int idx = 1;
        for (String user : server.getSessionManager().getOnlineUsernames()) {
            userTableModel.addRow(new Object[]{
                String.valueOf(idx++),
                user,
                "User (Authenticated)",
                "● Online"
            });
        }

        // Refresh transfers
        transferTableModel.setRowCount(0);
        for (ServerFileRelayManager.ActiveTransferInfo info : server.getFileRelayManager().getActiveTransfers()) {
            transferTableModel.addRow(new Object[]{
                info.getTransferId().substring(0, Math.min(8, info.getTransferId().length())) + "...",
                info.getSender(),
                info.getReceiver(),
                info.getFileName(),
                FormatUtils.formatFileSize(info.getFileSize()),
                (int) Math.round(info.getPercent()),
                FormatUtils.formatSpeed(info.getSpeedBps()),
                info.getStatus()
            });
        }
    }

    private static class ProgressCellRenderer extends JProgressBar implements TableCellRenderer {
        public ProgressCellRenderer() {
            super(0, 100);
            setStringPainted(true);
            setBorderPainted(false);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
            int val = 0;
            if (value instanceof Integer) val = (Integer) value;
            else if (value instanceof Number) val = ((Number) value).intValue();
            setValue(val);
            return this;
        }
    }
}
