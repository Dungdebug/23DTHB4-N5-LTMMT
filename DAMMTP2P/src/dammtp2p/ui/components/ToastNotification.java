package dammtp2p.ui.components;

import dammtp2p.ui.theme.AppIcons;
import dammtp2p.ui.theme.ThemeManager;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.Icon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * Modern non-intrusive floating in-app toast notification system.
 * Overlays sleek Obsidian Midnight cards in the bottom-right corner of the parent frame.
 */
public final class ToastNotification {

    public enum ToastType {
        INFO(ThemeManager.ACCENT_PRIMARY),
        SUCCESS(ThemeManager.SUCCESS),
        WARNING(ThemeManager.WARNING),
        ERROR(ThemeManager.DANGER);

        private final Color accentColor;

        ToastType(Color color) {
            this.accentColor = color;
        }

        public Color getAccentColor() {
            return accentColor;
        }
    }

    public enum Type {
        INFO(ThemeManager.ACCENT_PRIMARY),
        SUCCESS(ThemeManager.SUCCESS),
        WARNING(ThemeManager.WARNING),
        ERROR(ThemeManager.DANGER);

        private final Color accentColor;

        Type(Color color) {
            this.accentColor = color;
        }

        public Color getAccentColor() {
            return accentColor;
        }

        public ToastType toToastType() {
            return ToastType.valueOf(this.name());
        }
    }

    private ToastNotification() {
    }

    public static void show(JFrame frame, String title, String message, ToastType type) {
        if (frame == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> displayToast(frame, title, message, type));
    }

    public static void show(JFrame frame, String message, ToastType type) {
        show(frame, type.name(), message, type);
    }

    public static void show(JFrame frame, String message, Type type) {
        show(frame, type.name(), message, type.toToastType());
    }

    public static void show(JFrame frame, String title, String message, Type type) {
        show(frame, title, message, type.toToastType());
    }

    private static void displayToast(JFrame frame, String title, String message, ToastType type) {
        JLayeredPane layeredPane = frame.getLayeredPane();
        if (layeredPane == null) {
            return;
        }

        JPanel toast = new JPanel(new BorderLayout(10, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Background
                g2.setColor(ThemeManager.BG_PANEL);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);

                // Left accent bar
                g2.setColor(type.getAccentColor());
                g2.fillRoundRect(0, 0, 5, getHeight(), 6, 6);

                // Subtle border
                g2.setColor(ThemeManager.BORDER_COLOR);
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        toast.setOpaque(false);
        toast.setBorder(BorderFactory.createEmptyBorder(10, 16, 10, 16));

        // Left Icon
        JLabel iconLabel = new JLabel();
        switch (type) {
            case SUCCESS:
                iconLabel.setIcon(AppIcons.createCheckIcon(18, ThemeManager.SUCCESS));
                break;
            case WARNING:
                iconLabel.setIcon(AppIcons.createAlertIcon(18, ThemeManager.WARNING));
                break;
            case ERROR:
                iconLabel.setIcon(AppIcons.createAlertIcon(18, ThemeManager.DANGER));
                break;
            case INFO:
            default:
                iconLabel.setIcon(AppIcons.createNetworkIconSafe(18, ThemeManager.ACCENT_PRIMARY));
                break;
        }
        toast.add(iconLabel, BorderLayout.WEST);

        // Center: Text
        JPanel textPanel = new JPanel();
        textPanel.setOpaque(false);
        textPanel.setLayout(new BoxLayout(textPanel, BoxLayout.Y_AXIS));

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(ThemeManager.FONT_BODY_BOLD);
        lblTitle.setForeground(type.getAccentColor());
        textPanel.add(lblTitle);

        if (message != null && !message.isEmpty()) {
            textPanel.add(Box.createVerticalStrut(2));
            JLabel lblMsg = new JLabel("<html><body style='width: 250px; font-family: Segoe UI; font-size: 11px; color: #CBD5E1;'>"
                    + escapeHtml(message) + "</body></html>");
            lblMsg.setFont(ThemeManager.FONT_SMALL);
            lblMsg.setForeground(ThemeManager.TEXT_SECONDARY);
            textPanel.add(lblMsg);
        }

        toast.add(textPanel, BorderLayout.CENTER);

        int toastWidth = 340;
        int toastHeight = (message != null && message.length() > 35) ? 66 : 54;
        toast.setSize(new Dimension(toastWidth, toastHeight));

        int x = frame.getWidth() - toastWidth - 25;
        int y = frame.getHeight() - toastHeight - 45;
        toast.setLocation(Math.max(10, x), Math.max(10, y));

        layeredPane.add(toast, JLayeredPane.POPUP_LAYER);
        layeredPane.revalidate();
        layeredPane.repaint();

        // Auto dismiss after 3.8 seconds
        Timer timer = new Timer(3800, e -> {
            layeredPane.remove(toast);
            layeredPane.revalidate();
            layeredPane.repaint();
        });
        timer.setRepeats(false);
        timer.start();
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br/>");
    }
}