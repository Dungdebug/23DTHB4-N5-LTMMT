package dammtp2p.server.ui;

import dammtp2p.server.ServerCore;
import dammtp2p.ui.theme.ThemeManager;
import dammtp2p.util.Constants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;

/**
 * Standalone Server Administration Window (used when launched with --server).
 * Embeds the reusable ServerDashboardPanel.
 */
public class ServerFrame extends JFrame {

    private final ServerCore server;
    private final ServerDashboardPanel dashboardPanel;
    private JButton btnStart;
    private JButton btnStop;
    private JButton btnThemeToggle;

    public ServerFrame(ServerCore server) {
        this.server = server;

        setTitle(Constants.APP_NAME + " — MÁY CHỦ TRUNG TÂM (CENTRAL SERVER) • MISSION CONTROL");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1020, 720);
        setMinimumSize(new Dimension(860, 580));
        setLocationRelativeTo(null);

        dashboardPanel = new ServerDashboardPanel(server);

        initComponents();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (server != null && server.isRunning()) {
                    server.stop();
                }
            }
        });
    }

    private void initComponents() {
        JPanel contentPane = new JPanel(new BorderLayout(0, 8));
        contentPane.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        contentPane.setBackground(ThemeManager.BG_DARK);

        // Standalone Server Control Toolbar (Start / Stop)
        JPanel controlToolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        controlToolbar.setOpaque(false);

        btnStart = new JButton("▶ Khởi Động Server");
        btnStart.setFont(ThemeManager.FONT_BODY_BOLD);
        btnStart.setBackground(ThemeManager.SUCCESS);
        btnStart.setForeground(Color.WHITE);
        btnStart.setFocusPainted(false);
        btnStart.setEnabled(!server.isRunning());
        btnStart.addActionListener(e -> {
            try {
                server.start();
                updateState();
            } catch (Exception ex) {
                server.logEvent("[ERROR] Lỗi khởi động: " + ex.getMessage());
            }
        });

        btnStop = new JButton("⏹ Dừng Server");
        btnStop.setFont(ThemeManager.FONT_BODY_BOLD);
        btnStop.setBackground(ThemeManager.DANGER);
        btnStop.setForeground(Color.WHITE);
        btnStop.setFocusPainted(false);
        btnStop.setEnabled(server.isRunning());
        btnStop.addActionListener(e -> {
            server.stop();
            updateState();
        });

        btnThemeToggle = new JButton("🌓");
        btnThemeToggle.setToolTipText("Chuyển đổi giao diện Sáng / Tối");
        btnThemeToggle.setFocusPainted(false);
        btnThemeToggle.addActionListener(e -> ThemeManager.toggleTheme());

        controlToolbar.add(btnStart);
        controlToolbar.add(btnStop);
        controlToolbar.add(btnThemeToggle);

        contentPane.add(controlToolbar, BorderLayout.NORTH);
        contentPane.add(dashboardPanel, BorderLayout.CENTER);

        setContentPane(contentPane);
    }

    private void updateState() {
        boolean running = server.isRunning();
        btnStart.setEnabled(!running);
        btnStop.setEnabled(running);
    }
}