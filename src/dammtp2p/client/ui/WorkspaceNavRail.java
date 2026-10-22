package dammtp2p.client.ui;

import dammtp2p.ui.theme.AppIcons;
import dammtp2p.ui.theme.ThemeManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/**
 * Compact Workspace Navigation Rail for DAMMTP2P Mesh Workspace.
 * Houses workspace icons, transfers badge, and server administration triggers.
 */
public class WorkspaceNavRail extends JPanel {

    public interface NavRailListener {
        void onHomeClicked();
        void onChannelsClicked();
        void onDmsClicked();
        void onAddChannelClicked();
        void onTransfersClicked();
        void onServerAdminClicked();
        void onSettingsClicked();
        void onAboutClicked();
        void onLogoutClicked();
    }

    private final NavRailListener listener;
    private final JPanel iconsContainer;
    private NavButton btnHome;
    private NavButton btnChannels;
    private NavButton btnDms;
    private NavButton btnTransfers;
    private NavButton btnServerAdmin;
    private int activeTransferCount = 0;

    public WorkspaceNavRail(NavRailListener listener) {
        this.listener = listener;
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(64, Integer.MAX_VALUE));
        setBackground(ThemeManager.BG_NAV);
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, ThemeManager.BORDER_SUBTLE));

        iconsContainer = new JPanel();
        iconsContainer.setLayout(new BoxLayout(iconsContainer, BoxLayout.Y_AXIS));
        iconsContainer.setOpaque(false);
        iconsContainer.setBorder(BorderFactory.createEmptyBorder(12, 0, 12, 0));

        initIcons();
        add(iconsContainer, BorderLayout.NORTH);

        JPanel bottomContainer = new JPanel();
        bottomContainer.setLayout(new BoxLayout(bottomContainer, BoxLayout.Y_AXIS));
        bottomContainer.setOpaque(false);
        bottomContainer.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));
        initBottomIcons(bottomContainer);
        add(bottomContainer, BorderLayout.SOUTH);
    }

    private void initIcons() {
        // 1. Home / DAMMTP2P Logo Pill
        btnHome = new NavButton("D", AppIcons.createNetworkIconSafe(24, Color.WHITE), "Trang Chủ DAMMTP2P", true);
        btnHome.addClickListener(() -> {
            setActive(btnHome);
            if (listener != null) listener.onHomeClicked();
        });
        iconsContainer.add(btnHome);
        iconsContainer.add(Box.createVerticalStrut(8));

        iconsContainer.add(createPillSeparator());
        iconsContainer.add(Box.createVerticalStrut(8));

        // 2. Channels
        btnChannels = new NavButton("#", AppIcons.createHashIcon(20, Color.WHITE), "Kênh DAMMTP2P Mesh", false);
        btnChannels.addClickListener(() -> {
            setActive(btnChannels);
            if (listener != null) listener.onChannelsClicked();
        });
        iconsContainer.add(btnChannels);
        iconsContainer.add(Box.createVerticalStrut(6));

        // 3. Direct Messages
        btnDms = new NavButton("@", AppIcons.createChatBubbleIcon(20, Color.WHITE), "Tin Nhắn Riêng (1–1)", false);
        btnDms.addClickListener(() -> {
            setActive(btnDms);
            if (listener != null) listener.onDmsClicked();
        });
        iconsContainer.add(btnDms);
        iconsContainer.add(Box.createVerticalStrut(6));

        // 4. File Transfers Center
        btnTransfers = new NavButton("📁", AppIcons.createTransfersIcon(20, Color.WHITE), "Quản Lý Truyền Tệp (Port 8889)", false);
        btnTransfers.addClickListener(() -> {
            if (listener != null) listener.onTransfersClicked();
        });
        iconsContainer.add(btnTransfers);
        iconsContainer.add(Box.createVerticalStrut(6));

        // 5. Add Channel Button
        NavButton btnAddChannel = new NavButton("+", AppIcons.createPlusIcon(20, ThemeManager.SUCCESS), "Tạo Phòng Mới", false);
        btnAddChannel.addClickListener(() -> {
            if (listener != null) listener.onAddChannelClicked();
        });
        iconsContainer.add(btnAddChannel);
    }

    private void initBottomIcons(JPanel bottomContainer) {
        // Server Admin Launcher
        btnServerAdmin = new NavButton("⚙", AppIcons.createNetworkIconSafe(20, ThemeManager.ACCENT_CYAN), "Quản Trị Máy Chủ Trung Tâm (Relay)", false);
        btnServerAdmin.addClickListener(() -> {
            if (listener != null) listener.onServerAdminClicked();
        });
        bottomContainer.add(btnServerAdmin);
        bottomContainer.add(Box.createVerticalStrut(6));

        // Settings Dialog Button
        NavButton btnSettings = new NavButton("⚙", AppIcons.createSettingsIcon(20, ThemeManager.TEXT_MUTED), "Cài Đặt Hệ Thống", false);
        btnSettings.addClickListener(() -> {
            if (listener != null) listener.onSettingsClicked();
        });
        bottomContainer.add(btnSettings);
    }

    private JPanel createPillSeparator() {
        JPanel sep = new JPanel();
        sep.setBackground(ThemeManager.BORDER_SUBTLE);
        sep.setMaximumSize(new Dimension(32, 2));
        sep.setPreferredSize(new Dimension(32, 2));
        return sep;
    }

    private void setActive(NavButton activeBtn) {
        btnHome.setActive(activeBtn == btnHome);
        btnChannels.setActive(activeBtn == btnChannels);
        btnDms.setActive(activeBtn == btnDms);
        repaint();
    }

    public void updateTransferBadge(int count) {
        this.activeTransferCount = count;
        if (btnTransfers != null) {
            btnTransfers.setBadgeCount(count);
            btnTransfers.repaint();
        }
    }

    public void setServerRunning(boolean running) {
        if (btnServerAdmin != null) {
            btnServerAdmin.setServerRunning(running);
            btnServerAdmin.repaint();
        }
    }

    private static class NavButton extends JPanel {
        private final Icon icon;
        private final String tooltip;
        private boolean isActive;
        private boolean isHovered = false;
        private int badgeCount = 0;
        private boolean isServerRunning = false;
        private Runnable clickListener;

        public NavButton(String textFallback, Icon icon, String tooltip, boolean isActive) {
            this.icon = icon;
            this.tooltip = tooltip;
            this.isActive = isActive;
            setToolTipText(tooltip);
            setPreferredSize(new Dimension(48, 48));
            setMaximumSize(new Dimension(48, 48));
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { isHovered = true; repaint(); }
                @Override public void mouseExited(MouseEvent e) { isHovered = false; repaint(); }
                @Override public void mouseClicked(MouseEvent e) {
                    if (clickListener != null) clickListener.run();
                }
            });
        }

        public void addClickListener(Runnable r) { this.clickListener = r; }
        public void setActive(boolean a) { this.isActive = a; repaint(); }
        public void setBadgeCount(int b) { this.badgeCount = b; repaint(); }
        public void setServerRunning(boolean r) { this.isServerRunning = r; repaint(); }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();

            Color bg;
            if (isActive) {
                bg = ThemeManager.ACCENT_PRIMARY;
            } else if (isHovered) {
                bg = ThemeManager.BG_HOVER;
            } else {
                bg = ThemeManager.BG_CARD;
            }

            int radius = (isActive || isHovered) ? 14 : 24;
            int size = 42;
            int x = (w - size) / 2;
            int y = (h - size) / 2;

            g2.setColor(bg);
            g2.fillRoundRect(x, y, size, size, radius, radius);

            if (icon != null) {
                int ix = x + (size - icon.getIconWidth()) / 2;
                int iy = y + (size - icon.getIconHeight()) / 2;
                icon.paintIcon(this, g2, ix, iy);
            }

            if (badgeCount > 0) {
                g2.setColor(ThemeManager.DANGER);
                int bw = 18;
                int bh = 18;
                int bx = x + size - bw + 2;
                int by = y - 2;
                g2.fillOval(bx, by, bw, bh);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 10));
                String bStr = String.valueOf(badgeCount);
                int tw = g2.getFontMetrics().stringWidth(bStr);
                g2.drawString(bStr, bx + (bw - tw) / 2, by + 13);
            }

            if (isServerRunning) {
                g2.setColor(ThemeManager.SUCCESS);
                g2.fillOval(x + size - 8, y + size - 8, 8, 8);
            }

            g2.dispose();
        }
    }
}
