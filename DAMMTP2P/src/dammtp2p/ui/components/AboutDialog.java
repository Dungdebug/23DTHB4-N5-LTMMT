package dammtp2p.ui.components;

import dammtp2p.ui.theme.AppIcons;
import dammtp2p.ui.theme.ThemeManager;
import dammtp2p.util.Constants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;

/**
 * Clean, modern About dialog presenting enterprise system metadata,
 * technology specifications, and security features.
 */
public class AboutDialog extends JDialog {

    public AboutDialog(JFrame parent) {
        super(parent, "Thông Tin Ứng Dụng — " + Constants.APP_NAME, true);
        setLayout(new BorderLayout(0, 0));
        setResizable(false);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(22, 28, 18, 28));
        mainPanel.setBackground(ThemeManager.BG_PANEL);

        // 1. Header with Icon, Name & Version
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 0));
        headerPanel.setOpaque(false);
        JLabel lblIcon = new JLabel(AppIcons.createNetworkIconSafe(38, ThemeManager.ACCENT_PRIMARY));
        headerPanel.add(lblIcon);

        JPanel titleBlock = new JPanel();
        titleBlock.setLayout(new BoxLayout(titleBlock, BoxLayout.Y_AXIS));
        titleBlock.setOpaque(false);

        JLabel lblName = new JLabel(Constants.APP_NAME + " Enterprise");
        lblName.setFont(ThemeManager.FONT_DISPLAY);
        lblName.setForeground(ThemeManager.TEXT_PRIMARY);

        JLabel lblVer = new JLabel("Phiên bản: " + Constants.APP_VERSION + " • Nhóm 05 Lập Trình Mạng");
        lblVer.setFont(ThemeManager.FONT_SMALL);
        lblVer.setForeground(ThemeManager.TEXT_MUTED);

        titleBlock.add(lblName);
        titleBlock.add(lblVer);
        headerPanel.add(titleBlock);

        mainPanel.add(headerPanel);
        mainPanel.add(Box.createVerticalStrut(14));

        // 2. Short project description
        JLabel lblDesc = new JLabel("<html><center style='color:#CBD5E1;'><b>Hệ thống Trò chuyện & Truyền tệp tin Đa người dùng Tiếp sức</b><br>"
                + "Xây dựng trên nền tảng TCP Socket • Mạng ảo hóa ZeroTier LAN.</center></html>");
        lblDesc.setFont(ThemeManager.FONT_BODY);
        lblDesc.setAlignmentX(CENTER_ALIGNMENT);
        mainPanel.add(lblDesc);

        mainPanel.add(Box.createVerticalStrut(14));
        JSeparator sep = new JSeparator();
        sep.setMaximumSize(new Dimension(500, 2));
        mainPanel.add(sep);
        mainPanel.add(Box.createVerticalStrut(14));

        // 3. Technical Specifications Grid
        JPanel specPanel = new JPanel(new GridBagLayout());
        specPanel.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(4, 8, 4, 8);

        addSpecRow(specPanel, gbc, 0, "Kiến trúc mạng:", "Multi-Client Server + Port 8889 Relay");
        addSpecRow(specPanel, gbc, 1, "Kênh giao tiếp:", "Kênh đôi độc lập (Port 8888 Chat + 8889 File)");
        addSpecRow(specPanel, gbc, 2, "Mạng ảo hóa:", "ZeroTier Virtual LAN (IPv4 Auto-Discovery)");
        addSpecRow(specPanel, gbc, 3, "Bảo mật & Mã hóa:", "PBKDF2-HMAC-SHA256 Auth & AES-128-GCM");
        addSpecRow(specPanel, gbc, 4, "Truyền tệp lớn:", "Streaming 64 KB Buffer (Hỗ trợ tệp > 5 GB)");
        addSpecRow(specPanel, gbc, 5, "Tính năng nổi bật:", "Tự chọn thư mục tải về & Chống ghi đè");
        addSpecRow(specPanel, gbc, 6, "Toàn vẹn tệp tin:", "Kiểm tra mã băm SHA-256 đối chiếu 100%");
        addSpecRow(specPanel, gbc, 7, "Giao diện (UI/UX):", "Java Swing FlatLaf Dark Obsidian & Dynamic Avatars");

        mainPanel.add(specPanel);
        mainPanel.add(Box.createVerticalStrut(18));

        // 4. Footer & Close button
        JPanel btnBar = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
        btnBar.setOpaque(false);
        JButton btnClose = new JButton("ĐÓNG");
        btnClose.setFont(ThemeManager.FONT_BODY_BOLD);
        btnClose.setPreferredSize(new Dimension(120, 34));
        btnClose.setBackground(ThemeManager.ACCENT_PRIMARY);
        btnClose.setForeground(Color.WHITE);
        btnClose.setFocusPainted(false);
        btnClose.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnClose.addActionListener(e -> dispose());
        btnBar.add(btnClose);

        mainPanel.add(btnBar);
        add(mainPanel, BorderLayout.CENTER);

        pack();
        setLocationRelativeTo(parent);
    }

    private void addSpecRow(JPanel p, GridBagConstraints gbc, int row, String label, String value) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0.35;
        JLabel lblL = new JLabel(label);
        lblL.setFont(ThemeManager.FONT_BODY_BOLD);
        lblL.setForeground(ThemeManager.TEXT_SECONDARY);
        p.add(lblL, gbc);

        gbc.gridx = 1;
        gbc.weightx = 0.65;
        JLabel lblV = new JLabel(value);
        lblV.setFont(ThemeManager.FONT_BODY);
        lblV.setForeground(ThemeManager.TEXT_PRIMARY);
        p.add(lblV, gbc);
    }
}