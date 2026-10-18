package dammtp2p.ui.components;

import dammtp2p.ui.theme.AppIcons;
import dammtp2p.ui.theme.ThemeManager;
import dammtp2p.util.Constants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * Commercial-grade Settings Dialog for controlling ports, download directory, and UI theme.
 * Formatted with Obsidian Midnight styling and custom validation.
 */
public class SettingsDialog extends JDialog {

    private final JTextField txtControlPort;
    private final JTextField txtFilePort;
    private final JTextField txtDownloadDir;
    private final JComboBox<String> cmbTheme;
    private final JCheckBox chkAutoAccept;

    public SettingsDialog(JFrame parent) {
        super(parent, "Cài Đặt Hệ Thống — " + Constants.APP_NAME, true);
        setLayout(new BorderLayout(0, 0));
        setResizable(false);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(ThemeManager.BG_PANEL);
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(18, 22, 16, 22)
        ));

        // Header
        JPanel header = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        header.setOpaque(false);
        header.add(new JLabel(AppIcons.createSettingsIcon(22, ThemeManager.ACCENT_PRIMARY)));

        JLabel lblTitle = new JLabel("CÀI ĐẶT THÔNG SỐ HỆ THỐNG");
        lblTitle.setFont(ThemeManager.FONT_TITLE);
        lblTitle.setForeground(ThemeManager.TEXT_PRIMARY);
        header.add(lblTitle);

        root.add(header, BorderLayout.NORTH);

        // Center Form
        JPanel formCard = new JPanel(new GridBagLayout());
        formCard.setBackground(ThemeManager.BG_INPUT);
        formCard.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.BORDER_SUBTLE, 1),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(6, 6, 6, 6);

        // Control Port
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.38;
        JLabel lblCtrl = new JLabel("Cổng điều khiển (Control Port):");
        lblCtrl.setFont(ThemeManager.FONT_BODY);
        lblCtrl.setForeground(ThemeManager.TEXT_SECONDARY);
        formCard.add(lblCtrl, gbc);

        gbc.gridx = 1; gbc.gridy = 0; gbc.weightx = 0.62;
        txtControlPort = new JTextField(String.valueOf(dammtp2p.util.AppConfig.getInstance().getControlPort()), 10);
        txtControlPort.setFont(ThemeManager.FONT_MONO);
        txtControlPort.setBackground(ThemeManager.BG_PANEL);
        txtControlPort.setForeground(ThemeManager.TEXT_PRIMARY);
        txtControlPort.setCaretColor(Color.WHITE);
        formCard.add(txtControlPort, gbc);

        // File Transfer Port
        gbc.gridx = 0; gbc.gridy = 1;
        JLabel lblFile = new JLabel("Cổng truyền tệp (File Relay Port):");
        lblFile.setFont(ThemeManager.FONT_BODY);
        lblFile.setForeground(ThemeManager.TEXT_SECONDARY);
        formCard.add(lblFile, gbc);

        gbc.gridx = 1; gbc.gridy = 1;
        txtFilePort = new JTextField(String.valueOf(dammtp2p.util.AppConfig.getInstance().getFilePort()), 10);
        txtFilePort.setFont(ThemeManager.FONT_MONO);
        txtFilePort.setBackground(ThemeManager.BG_PANEL);
        txtFilePort.setForeground(ThemeManager.TEXT_PRIMARY);
        txtFilePort.setCaretColor(Color.WHITE);
        formCard.add(txtFilePort, gbc);

        // Download Directory
        gbc.gridx = 0; gbc.gridy = 2;
        JLabel lblDir = new JLabel("Thư mục lưu trữ mặc định:");
        lblDir.setFont(ThemeManager.FONT_BODY);
        lblDir.setForeground(ThemeManager.TEXT_SECONDARY);
        formCard.add(lblDir, gbc);

        gbc.gridx = 1; gbc.gridy = 2;
        JPanel dirPanel = new JPanel(new BorderLayout(6, 0));
        dirPanel.setOpaque(false);

        txtDownloadDir = new JTextField(new File(dammtp2p.util.AppConfig.getInstance().getDownloadDir()).getAbsolutePath(), 16);
        txtDownloadDir.setFont(ThemeManager.FONT_BODY);
        txtDownloadDir.setBackground(ThemeManager.BG_PANEL);
        txtDownloadDir.setForeground(ThemeManager.TEXT_PRIMARY);
        txtDownloadDir.setCaretColor(Color.WHITE);

        JButton btnBrowse = new JButton("Duyệt...");
        btnBrowse.setFont(ThemeManager.FONT_SMALL);
        btnBrowse.setFocusPainted(false);
        btnBrowse.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnBrowse.addActionListener(e -> browseDownloadDirectory());

        dirPanel.add(txtDownloadDir, BorderLayout.CENTER);
        dirPanel.add(btnBrowse, BorderLayout.EAST);
        formCard.add(dirPanel, gbc);

        // Theme switch
        gbc.gridx = 0; gbc.gridy = 3;
        JLabel lblTheme = new JLabel("Giao diện người dùng (Theme):");
        lblTheme.setFont(ThemeManager.FONT_BODY);
        lblTheme.setForeground(ThemeManager.TEXT_SECONDARY);
        formCard.add(lblTheme, gbc);

        gbc.gridx = 1; gbc.gridy = 3;
        cmbTheme = new JComboBox<>(new String[]{"Dark Mode (Obsidian Midnight)", "Light Mode (Sáng)"});
        cmbTheme.setFont(ThemeManager.FONT_BODY);
        cmbTheme.setSelectedIndex(ThemeManager.isDarkMode() ? 0 : 1);
        cmbTheme.addActionListener(e -> {
            boolean wantDark = cmbTheme.getSelectedIndex() == 0;
            if (wantDark != ThemeManager.isDarkMode()) {
                ThemeManager.toggleTheme();
            }
        });
        formCard.add(cmbTheme, gbc);

        // Buffer info
        gbc.gridx = 0; gbc.gridy = 4;
        JLabel lblBuf = new JLabel("Bộ đệm truyền tệp (TCP Buffer):");
        lblBuf.setFont(ThemeManager.FONT_BODY);
        lblBuf.setForeground(ThemeManager.TEXT_SECONDARY);
        formCard.add(lblBuf, gbc);

        gbc.gridx = 1; gbc.gridy = 4;
        JLabel lblBufVal = new JLabel("64 KB (65,536 bytes) • Tối ưu hóa ZeroTier LAN");
        lblBufVal.setFont(ThemeManager.FONT_SMALL);
        lblBufVal.setForeground(ThemeManager.SUCCESS);
        formCard.add(lblBufVal, gbc);

        // Auto Accept row
        gbc.gridx = 0; gbc.gridy = 5;
        JLabel lblAuto = new JLabel("Tự động nhận tệp (Auto-Accept):");
        lblAuto.setFont(ThemeManager.FONT_BODY);
        lblAuto.setForeground(ThemeManager.TEXT_SECONDARY);
        formCard.add(lblAuto, gbc);

        gbc.gridx = 1; gbc.gridy = 5;
        chkAutoAccept = new JCheckBox("Tự động chấp nhận và tải tệp từ bạn bè hoặc phòng nhóm", dammtp2p.util.AppConfig.getInstance().isAutoAcceptFiles());
        chkAutoAccept.setFont(ThemeManager.FONT_BODY);
        chkAutoAccept.setForeground(ThemeManager.ACCENT_ELECTRIC_CYAN);
        chkAutoAccept.setOpaque(false);
        formCard.add(chkAutoAccept, gbc);

        root.add(formCard, BorderLayout.CENTER);

        // Bottom Action Buttons
        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttonBar.setOpaque(false);

        JButton btnCancel = new JButton("ĐÓNG");
        btnCancel.setFont(ThemeManager.FONT_BODY);
        btnCancel.setFocusPainted(false);
        btnCancel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnCancel.addActionListener(e -> dispose());

        JButton btnSave = new JButton("LƯU CẤU HÌNH");
        btnSave.setFont(ThemeManager.FONT_BODY_BOLD);
        btnSave.setBackground(ThemeManager.ACCENT_PRIMARY);
        btnSave.setForeground(Color.WHITE);
        btnSave.setFocusPainted(false);
        btnSave.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnSave.setPreferredSize(new Dimension(130, 34));
        btnSave.addActionListener(e -> onSave());

        buttonBar.add(btnCancel);
        buttonBar.add(btnSave);
        root.add(buttonBar, BorderLayout.SOUTH);

        setContentPane(root);
        pack();
        setLocationRelativeTo(parent);
    }

    private void browseDownloadDirectory() {
        JFileChooser chooser = new JFileChooser(txtDownloadDir.getText().trim());
        chooser.setDialogTitle("Chọn Thư Mục Tải Về Mặc Định");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
            txtDownloadDir.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private void onSave() {
        int ctrlPort;
        int filePort;
        try {
            ctrlPort = Integer.parseInt(txtControlPort.getText().trim());
            filePort = Integer.parseInt(txtFilePort.getText().trim());
        } catch (NumberFormatException e) {
            ModernDialog.showError(this, "Lỗi Nhập Liệu", "Số cổng phải là số nguyên hợp lệ (1024 - 65535)!");
            return;
        }

        if (ctrlPort < 1024 || ctrlPort > 65535 || filePort < 1024 || filePort > 65535) {
            ModernDialog.showError(this, "Lỗi Số Cổng", "Số cổng phải nằm trong dải cho phép (1024 - 65535)!");
            return;
        }

        if (ctrlPort == filePort) {
            ModernDialog.showError(this, "Xung Đột Cổng", "Cổng điều khiển và Cổng truyền tệp không được trùng nhau!");
            return;
        }

        File dir = new File(txtDownloadDir.getText().trim());
        if (!dir.exists()) {
            dir.mkdirs();
        }

        dammtp2p.util.AppConfig cfg = dammtp2p.util.AppConfig.getInstance();
        cfg.setControlPort(ctrlPort);
        cfg.setFilePort(filePort);
        cfg.setDownloadDir(dir.getAbsolutePath());
        cfg.setAutoAcceptFiles(chkAutoAccept.isSelected());
        cfg.save();

        ModernDialog.showSuccess(this, "Cài Đặt", "Đã lưu cài đặt cấu hình thành công vào client_config.json!");
        dispose();
    }
}