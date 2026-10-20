package dammtp2p.ui.components;

import dammtp2p.ui.theme.AppIcons;
import dammtp2p.ui.theme.ThemeManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.KeyboardFocusManager;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * Commercial-grade modal dialog replacement for standard Swing JOptionPane.
 * Strictly adheres to the Obsidian Midnight design system with crisp vector icons,
 * high-contrast typography, and smooth action buttons.
 */
public final class ModernDialog extends JDialog {

    public enum DialogType {
        INFO(ThemeManager.ACCENT_PRIMARY),
        SUCCESS(ThemeManager.SUCCESS),
        WARNING(ThemeManager.WARNING),
        ERROR(ThemeManager.DANGER);

        final Color accentColor;
        DialogType(Color c) { this.accentColor = c; }
    }

    private boolean confirmed = false;
    private String inputResult = null;
    private JTextField txtInput;

    private ModernDialog(Frame parent, String title, String message, DialogType type,
                         String confirmText, String cancelText, String defaultInput) {
        super(parent, title, true);
        setResizable(false);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(0, 0));
        root.setBackground(ThemeManager.BG_PANEL);
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(18, 20, 16, 20)
        ));

        // Top Row: Icon + Title
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        header.setOpaque(false);

        JLabel iconLabel = new JLabel();
        switch (type) {
            case SUCCESS:
                iconLabel.setIcon(AppIcons.createCheckIcon(24, ThemeManager.SUCCESS));
                break;
            case WARNING:
                iconLabel.setIcon(AppIcons.createAlertIcon(24, ThemeManager.WARNING));
                break;
            case ERROR:
                iconLabel.setIcon(AppIcons.createAlertIcon(24, ThemeManager.DANGER));
                break;
            case INFO:
            default:
                iconLabel.setIcon(AppIcons.createNetworkIconSafe(24, ThemeManager.ACCENT_PRIMARY));
                break;
        }

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(ThemeManager.FONT_TITLE);
        lblTitle.setForeground(ThemeManager.TEXT_PRIMARY);

        header.add(iconLabel);
        header.add(lblTitle);

        // Center: Message & optional text field
        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setOpaque(false);
        center.setBorder(BorderFactory.createEmptyBorder(12, 34, 14, 10));

        JLabel lblMessage = new JLabel("<html><body style='width: 340px; font-family: Segoe UI; font-size: 11px; color: #CBD5E1;'>"
                + escapeHtml(message) + "</body></html>");
        lblMessage.setFont(ThemeManager.FONT_BODY);
        center.add(lblMessage);

        if (defaultInput != null) {
            center.add(Box.createVerticalStrut(10));
            txtInput = new JTextField(defaultInput, 20);
            txtInput.setFont(ThemeManager.FONT_BODY);
            txtInput.setBackground(ThemeManager.BG_INPUT);
            txtInput.setForeground(ThemeManager.TEXT_PRIMARY);
            txtInput.setCaretColor(Color.WHITE);
            txtInput.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(ThemeManager.BORDER_COLOR, 1),
                    BorderFactory.createEmptyBorder(6, 10, 6, 10)
            ));
            txtInput.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                        confirmed = true;
                        inputResult = txtInput.getText().trim();
                        dispose();
                    }
                }
            });
            center.add(txtInput);
        }

        // Bottom: Action Buttons
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonRow.setOpaque(false);
        buttonRow.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        if (cancelText != null) {
            JButton btnCancel = new JButton(cancelText);
            btnCancel.setFont(ThemeManager.FONT_BODY);
            btnCancel.setBackground(ThemeManager.BG_INPUT);
            btnCancel.setForeground(ThemeManager.TEXT_SECONDARY);
            btnCancel.setFocusPainted(false);
            btnCancel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btnCancel.addActionListener(e -> {
                confirmed = false;
                inputResult = null;
                dispose();
            });
            buttonRow.add(btnCancel);
        }

        JButton btnConfirm = new JButton(confirmText != null ? confirmText : "ĐỒNG Ý");
        btnConfirm.setFont(ThemeManager.FONT_BODY_BOLD);
        btnConfirm.setBackground(type.accentColor);
        btnConfirm.setForeground(Color.WHITE);
        btnConfirm.setFocusPainted(false);
        btnConfirm.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnConfirm.setPreferredSize(new Dimension(cancelText != null ? 110 : 95, 32));
        btnConfirm.addActionListener(e -> {
            confirmed = true;
            if (txtInput != null) {
                inputResult = txtInput.getText().trim();
            }
            dispose();
        });
        buttonRow.add(btnConfirm);

        // Escape to close
        root.registerKeyboardAction(e -> dispose(),
                javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JPanel.WHEN_IN_FOCUSED_WINDOW);

        root.add(header, BorderLayout.NORTH);
        root.add(center, BorderLayout.CENTER);
        root.add(buttonRow, BorderLayout.SOUTH);

        setContentPane(root);
        pack();
        setLocationRelativeTo(parent);
    }

    private static String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\n", "<br/>");
    }

    public static void showInfo(Component parent, String title, String message) {
        Frame f = getParentFrame(parent);
        ModernDialog dialog = new ModernDialog(f, title, message, DialogType.INFO, "ĐÓNG", null, null);
        dialog.setVisible(true);
    }

    public static void showSuccess(Component parent, String title, String message) {
        Frame f = getParentFrame(parent);
        ModernDialog dialog = new ModernDialog(f, title, message, DialogType.SUCCESS, "HOÀN TẤT", null, null);
        dialog.setVisible(true);
    }

    public static void showWarning(Component parent, String title, String message) {
        Frame f = getParentFrame(parent);
        ModernDialog dialog = new ModernDialog(f, title, message, DialogType.WARNING, "ĐÃ HIỂU", null, null);
        dialog.setVisible(true);
    }

    public static void showError(Component parent, String title, String message) {
        Frame f = getParentFrame(parent);
        ModernDialog dialog = new ModernDialog(f, title, message, DialogType.ERROR, "ĐÓNG", null, null);
        dialog.setVisible(true);
    }

    public static boolean showConfirm(Component parent, String title, String message, String confirmText, String cancelText) {
        Frame f = getParentFrame(parent);
        ModernDialog dialog = new ModernDialog(f, title, message, DialogType.WARNING, confirmText, cancelText, null);
        dialog.setVisible(true);
        return dialog.confirmed;
    }

    public static boolean showConfirm(Component parent, String title, String message) {
        return showConfirm(parent, title, message, "XÁC NHẬN", "HỦY");
    }

    public static String showInput(Component parent, String title, String message, String defaultValue) {
        Frame f = getParentFrame(parent);
        ModernDialog dialog = new ModernDialog(f, title, message, DialogType.INFO, "XÁC NHẬN", "HỦY", defaultValue != null ? defaultValue : "");
        dialog.setVisible(true);
        return dialog.confirmed ? dialog.inputResult : null;
    }

    public static String showInput(Component parent, String title, String message) {
        return showInput(parent, title, message, "");
    }

    private static Frame getParentFrame(Component c) {
        if (c == null) return null;
        if (c instanceof Frame) return (Frame) c;
        return (Frame) SwingUtilities.getAncestorOfClass(Frame.class, c);
    }
}
