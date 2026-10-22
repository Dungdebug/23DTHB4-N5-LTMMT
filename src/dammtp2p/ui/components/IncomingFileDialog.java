package dammtp2p.ui.components;

import dammtp2p.core.model.FileMetadata;
import dammtp2p.ui.theme.AppIcons;
import dammtp2p.ui.theme.ThemeManager;
import dammtp2p.util.Constants;
import dammtp2p.util.FormatUtils;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

/**
 * Commercial-grade dialog presented to the receiver when a remote peer sends an incoming FILE_REQUEST.
 * Allows recipient to choose destination folder (custom directory, computer's standard Downloads, or app downloads).
 */
public class IncomingFileDialog extends JDialog {

    @FunctionalInterface
    public interface FileAcceptCallback {
        void onAccept(File targetDirectory);
    }

    private final FileMetadata metadata;
    private final FileAcceptCallback onAcceptWithDirCallback;
    private final Runnable onAcceptCallback;
    private final Runnable onRejectCallback;
    private boolean decisionMade = false;

    private JTextField txtSaveDir;

    public IncomingFileDialog(Frame owner, FileMetadata metadata, FileAcceptCallback onAccept, Runnable onReject) {
        super(owner, "Yêu Cầu Nhận Tệp Tin — " + Constants.APP_NAME, true);
        this.metadata = metadata;
        this.onAcceptWithDirCallback = onAccept;
        this.onAcceptCallback = null;
        this.onRejectCallback = onReject;
        initUI();
    }

    public IncomingFileDialog(Frame owner, FileMetadata metadata, Runnable onAccept, Runnable onReject) {
        super(owner, "Yêu Cầu Nhận Tệp Tin — " + Constants.APP_NAME, true);
        this.metadata = metadata;
        this.onAcceptWithDirCallback = (dir) -> { if (onAccept != null) onAccept.run(); };
        this.onAcceptCallback = onAccept;
        this.onRejectCallback = onReject;
        initUI();
    }

    private void initUI() {
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(0, 0));
        setResizable(false);

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(18, 22, 16, 22));
        mainPanel.setBackground(ThemeManager.BG_PANEL);

        // Header Title with file icon
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        headerPanel.setOpaque(false);
        headerPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblIcon = new JLabel(AppIcons.createFileIcon(24, ThemeManager.ACCENT_PRIMARY));
        headerPanel.add(lblIcon);

        JLabel titleLabel = new JLabel("Yêu cầu nhận tệp tin từ @" + metadata.getSender());
        titleLabel.setFont(ThemeManager.FONT_TITLE);
        titleLabel.setForeground(ThemeManager.TEXT_PRIMARY);
        headerPanel.add(titleLabel);

        mainPanel.add(headerPanel);
        mainPanel.add(Box.createVerticalStrut(14));

        // Details Panel Card
        JPanel detailsPanel = new JPanel(new GridBagLayout());
        detailsPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.BORDER_SUBTLE, 1),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)
        ));
        detailsPanel.setBackground(ThemeManager.BG_INPUT);
        detailsPanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(5, 4, 5, 14);

        // Row 0: Sender
        gbc.gridx = 0; gbc.gridy = 0;
        JLabel lblSender = new JLabel("Người gửi (Sender):");
        lblSender.setFont(ThemeManager.FONT_BODY);
        lblSender.setForeground(ThemeManager.TEXT_SECONDARY);
        detailsPanel.add(lblSender, gbc);

        gbc.gridx = 1;
        JLabel valSender = new JLabel("@" + metadata.getSender());
        valSender.setFont(ThemeManager.FONT_BODY_BOLD);
        valSender.setForeground(ThemeManager.TEXT_PRIMARY);
        detailsPanel.add(valSender, gbc);

        // Row 1: File Name
        gbc.gridx = 0; gbc.gridy = 1;
        JLabel lblFile = new JLabel("Tên tệp (File Name):");
        lblFile.setFont(ThemeManager.FONT_BODY);
        lblFile.setForeground(ThemeManager.TEXT_SECONDARY);
        detailsPanel.add(lblFile, gbc);

        gbc.gridx = 1;
        JLabel valFile = new JLabel(metadata.getFileName());
        valFile.setFont(ThemeManager.FONT_BODY_BOLD);
        valFile.setForeground(ThemeManager.ACCENT_PRIMARY);
        detailsPanel.add(valFile, gbc);

        // Row 2: File Size
        gbc.gridx = 0; gbc.gridy = 2;
        JLabel lblSize = new JLabel("Dung lượng (Size):");
        lblSize.setFont(ThemeManager.FONT_BODY);
        lblSize.setForeground(ThemeManager.TEXT_SECONDARY);
        detailsPanel.add(lblSize, gbc);

        gbc.gridx = 1;
        JLabel valSize = new JLabel(FormatUtils.formatFileSize(metadata.getFileSize()) + " (" + metadata.getFileSize() + " bytes) / Tối đa: 20 GiB");
        valSize.setFont(ThemeManager.FONT_BODY_BOLD);
        valSize.setForeground(ThemeManager.SUCCESS);
        detailsPanel.add(valSize, gbc);

        // Row 3: Expected SHA-256 Checksum
        gbc.gridx = 0; gbc.gridy = 3;
        JLabel lblSha = new JLabel("Mã SHA-256:");
        lblSha.setFont(ThemeManager.FONT_BODY);
        lblSha.setForeground(ThemeManager.TEXT_SECONDARY);
        detailsPanel.add(lblSha, gbc);

        gbc.gridx = 1;
        JPanel shaPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        shaPanel.setOpaque(false);

        String sha = metadata.getSha256();
        String displaySha = (sha != null && !sha.isEmpty()) ? (sha.substring(0, Math.min(18, sha.length())) + "...") : "(Chưa cung cấp)";
        JLabel valSha = new JLabel(displaySha);
        valSha.setFont(ThemeManager.FONT_MONO);
        valSha.setForeground(ThemeManager.TEXT_MUTED);
        shaPanel.add(valSha);

        if (sha != null && !sha.isEmpty()) {
            JButton btnCopySha = new JButton("Copy SHA");
            btnCopySha.setFont(ThemeManager.FONT_SMALL);
            btnCopySha.setFocusPainted(false);
            btnCopySha.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btnCopySha.addActionListener(e -> Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sha), null));
            shaPanel.add(btnCopySha);
        }
        detailsPanel.add(shaPanel, gbc);

        mainPanel.add(detailsPanel);
        mainPanel.add(Box.createVerticalStrut(14));

        // Save Location Selection Card
        JPanel savePanel = new JPanel();
        savePanel.setLayout(new BoxLayout(savePanel, BoxLayout.Y_AXIS));
        savePanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.BORDER_SUBTLE, 1),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)
        ));
        savePanel.setBackground(ThemeManager.BG_INPUT);
        savePanel.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblSaveTitle = new JLabel("📁 NƠI LƯU TỆP TIN TẢI VỀ (DESTINATION):");
        lblSaveTitle.setFont(ThemeManager.FONT_SMALL_BOLD);
        lblSaveTitle.setForeground(ThemeManager.ACCENT_PRIMARY);
        lblSaveTitle.setAlignmentX(Component.LEFT_ALIGNMENT);
        savePanel.add(lblSaveTitle);
        savePanel.add(Box.createVerticalStrut(8));

        // Default: System Downloads folder if exists, else ./downloads
        File sysDownloads = new File(System.getProperty("user.home"), "Downloads");
        String defaultSavePath = sysDownloads.exists() ? sysDownloads.getAbsolutePath() : new File(Constants.DEFAULT_DOWNLOAD_DIR).getAbsolutePath();

        txtSaveDir = new JTextField(defaultSavePath, 34);
        txtSaveDir.setFont(ThemeManager.FONT_BODY);
        txtSaveDir.setBackground(ThemeManager.BG_PANEL);
        txtSaveDir.setForeground(ThemeManager.TEXT_PRIMARY);
        txtSaveDir.setCaretColor(Color.WHITE);
        txtSaveDir.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        txtSaveDir.setAlignmentX(Component.LEFT_ALIGNMENT);
        savePanel.add(txtSaveDir);
        savePanel.add(Box.createVerticalStrut(10));

        // Quick Pick Buttons Row
        JPanel quickBtnRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        quickBtnRow.setOpaque(false);
        quickBtnRow.setAlignmentX(Component.LEFT_ALIGNMENT);

        JButton btnSysDownloads = new JButton("📥 Downloads Máy Tính");
        btnSysDownloads.setFont(ThemeManager.FONT_SMALL);
        btnSysDownloads.setFocusPainted(false);
        btnSysDownloads.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnSysDownloads.addActionListener(e -> {
            File f = new File(System.getProperty("user.home"), "Downloads");
            if (!f.exists()) f.mkdirs();
            txtSaveDir.setText(f.getAbsolutePath());
        });

        JButton btnAppDownloads = new JButton("📂 Downloads Thư Mục App");
        btnAppDownloads.setFont(ThemeManager.FONT_SMALL);
        btnAppDownloads.setFocusPainted(false);
        btnAppDownloads.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnAppDownloads.addActionListener(e -> {
            File f = new File(Constants.DEFAULT_DOWNLOAD_DIR);
            if (!f.exists()) f.mkdirs();
            txtSaveDir.setText(f.getAbsolutePath());
        });

        JButton btnChooseFolder = new JButton("📁 Chọn Thư Mục Khác...");
        btnChooseFolder.setFont(ThemeManager.FONT_SMALL);
        btnChooseFolder.setFocusPainted(false);
        btnChooseFolder.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnChooseFolder.addActionListener(e -> chooseCustomFolder());

        quickBtnRow.add(btnSysDownloads);
        quickBtnRow.add(btnAppDownloads);
        quickBtnRow.add(btnChooseFolder);
        savePanel.add(quickBtnRow);

        mainPanel.add(savePanel);
        mainPanel.add(Box.createVerticalStrut(14));

        add(mainPanel, BorderLayout.CENTER);

        // Buttons Panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 12));
        buttonPanel.setBackground(ThemeManager.BG_PANEL);
        buttonPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, ThemeManager.BORDER_COLOR));

        JButton btnReject = new JButton("✕ TỪ CHỐI (REJECT)");
        btnReject.setFont(ThemeManager.FONT_BODY);
        btnReject.setBackground(ThemeManager.BG_INPUT);
        btnReject.setForeground(ThemeManager.DANGER);
        btnReject.setFocusPainted(false);
        btnReject.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnReject.setPreferredSize(new Dimension(160, 36));

        JButton btnAccept = new JButton("✓ CHẤP NHẬN TẢI VỀ");
        btnAccept.setFont(ThemeManager.FONT_BODY_BOLD);
        btnAccept.setBackground(ThemeManager.SUCCESS);
        btnAccept.setForeground(Color.WHITE);
        btnAccept.setIcon(AppIcons.createCheckIcon(14, Color.WHITE));
        btnAccept.setFocusPainted(false);
        btnAccept.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnAccept.setPreferredSize(new Dimension(210, 36));

        btnAccept.addActionListener(e -> {
            if (metadata.getFileSize() > Constants.MAX_FILE_SIZE_BYTES || metadata.getFileSize() < 0L) {
                ModernDialog.showWarning(this, "Tệp Quá Giới Hạn 20 GiB", Constants.OVERSIZED_FILE_ERROR_MESSAGE);
                decisionMade = true;
                if (onRejectCallback != null) {
                    onRejectCallback.run();
                }
                dispose();
                return;
            }
            decisionMade = true;
            File targetDir = getSelectedDirectory();
            if (onAcceptWithDirCallback != null) {
                onAcceptWithDirCallback.onAccept(targetDir);
            } else if (onAcceptCallback != null) {
                onAcceptCallback.run();
            }
            dispose();
        });

        btnReject.addActionListener(e -> {
            decisionMade = true;
            if (onRejectCallback != null) {
                onRejectCallback.run();
            }
            dispose();
        });

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (!decisionMade) {
                    if (onRejectCallback != null) {
                        onRejectCallback.run();
                    }
                }
            }
        });

        buttonPanel.add(btnReject);
        buttonPanel.add(btnAccept);
        add(buttonPanel, BorderLayout.SOUTH);

        pack();
        setLocationRelativeTo(getOwner());
    }

    private void chooseCustomFolder() {
        JFileChooser chooser = new JFileChooser(txtSaveDir.getText().trim());
        chooser.setDialogTitle("Chọn Thư Mục Để Lưu Tệp Tin");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);

        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
            txtSaveDir.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }

    private File getSelectedDirectory() {
        String path = txtSaveDir.getText().trim();
        if (path.isEmpty()) {
            path = Constants.DEFAULT_DOWNLOAD_DIR;
        }
        File dir = new File(path);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }
}