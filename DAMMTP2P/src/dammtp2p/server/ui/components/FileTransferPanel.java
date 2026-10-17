package dammtp2p.ui.components;

import dammtp2p.core.model.FileMetadata;
import dammtp2p.core.model.PeerInfo;
import dammtp2p.core.model.TransferState;
import dammtp2p.core.network.DefaultConnectionManager;
import dammtp2p.core.transfer.FileTransferListener;
import dammtp2p.core.transfer.FileTransferManager;
import dammtp2p.core.transfer.TransferProgressListener;
import dammtp2p.ui.theme.AppIcons;
import dammtp2p.ui.theme.ThemeManager;
import dammtp2p.util.Constants;
import dammtp2p.util.FormatUtils;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;

/**
 * Modern UI Panel for File Transfer with Drag & Drop dropzone, card layout,
 * live throttled progress/speed/ETA, SHA-256 verification indicator, and transfer actions.
 */
public class FileTransferPanel extends JPanel implements FileTransferListener, TransferProgressListener {

    private final DefaultConnectionManager connectionManager;
    private final FileTransferManager transferManager;
    private JFrame parentFrame;
    private TransferHistoryPanel historyPanel;

    private JPanel dropZonePanel;
    private JLabel lblDropZoneText;
    private boolean isDragOver = false;

    private JButton btnChooseFile;
    private JButton btnSendFile;
    private JButton btnResume;
    private JButton btnCancel;
    private JButton btnOpenFolder;

    private JLabel lblFileNameVal;
    private JLabel lblFileSizeVal;
    private JLabel lblDirectionVal;
    private JLabel lblTransferredVal;
    private JLabel lblSpeedVal;
    private JLabel lblEtaVal;
    private JLabel lblStatusVal;
    private JLabel lblSha256Val;
    private JProgressBar progressBar;

    private File currentSelectedFile;
    private boolean isCurrentTransferSender = true;
    private long lastProgressUiUpdateTime = 0L;

    public FileTransferPanel(DefaultConnectionManager connectionManager, FileTransferManager transferManager) {
        this.connectionManager = connectionManager;
        this.transferManager = transferManager;
        this.transferManager.addTransferListener(this);
        this.transferManager.addProgressListener(this);

        initUI();
        updateUIState();
    }

    public void setParentFrame(JFrame frame) {
        this.parentFrame = frame;
    }

    public void setHistoryPanel(TransferHistoryPanel historyPanel) {
        this.historyPanel = historyPanel;
    }

    private void initUI() {
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(ThemeManager.BORDER_COLOR, 1),
                        "Truyền Tệp Tin Ngang Hàng (P2P File Transfer)",
                        0, 0, ThemeManager.FONT_BODY_BOLD,
                        ThemeManager.isDarkMode() ? ThemeManager.TEXT_PRIMARY : Color.DARK_GRAY
                ),
                BorderFactory.createEmptyBorder(6, 10, 8, 10)
        ));

        JPanel contentPanel = new JPanel();
        contentPanel.setLayout(new BoxLayout(contentPanel, BoxLayout.Y_AXIS));

        // 1. Drag & Drop Stylized Dropzone
        dropZonePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                Color bg = isDragOver
                        ? (ThemeManager.isDarkMode() ? new Color(30, 48, 80) : new Color(230, 242, 255))
                        : (ThemeManager.isDarkMode() ? ThemeManager.BG_INPUT : new Color(248, 250, 252));
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);

                Color borderC = isDragOver ? ThemeManager.ACCENT_PRIMARY : ThemeManager.BORDER_COLOR;
                g2.setColor(borderC);
                float[] dash = {6.0f, 4.0f};
                g2.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10.0f, dash, 0.0f));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        dropZonePanel.setOpaque(false);
        dropZonePanel.setPreferredSize(new Dimension(500, 48));
        dropZonePanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        dropZonePanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        lblDropZoneText = new JLabel("Kéo & thả tệp tin vào đây để gửi, hoặc bấm nút [Chọn tệp]");
        lblDropZoneText.setFont(ThemeManager.FONT_BODY);
        lblDropZoneText.setForeground(ThemeManager.TEXT_SECONDARY);
        lblDropZoneText.setIcon(AppIcons.createFileIcon(16, ThemeManager.ACCENT_PRIMARY));
        dropZonePanel.add(lblDropZoneText);

        // Click on dropzone opens file chooser
        dropZonePanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                chooseLocalFile();
            }
        });

        // Register Drop Target
        new FileDropTarget(dropZonePanel, this::handleFileDropped,
                () -> { isDragOver = true; dropZonePanel.repaint(); },
                () -> { isDragOver = false; dropZonePanel.repaint(); });

        contentPanel.add(dropZonePanel);
        contentPanel.add(Box.createVerticalStrut(8));

        // 2. Action Buttons Bar
        JPanel buttonBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        buttonBar.setOpaque(false);

        btnChooseFile = new JButton("CHỌN TỆP (CHOOSE FILE)");
        btnChooseFile.setFont(ThemeManager.FONT_BODY);
        btnChooseFile.setIcon(AppIcons.createFileIcon(14, ThemeManager.TEXT_PRIMARY));
        btnChooseFile.setFocusPainted(false);
        btnChooseFile.addActionListener(e -> chooseLocalFile());

        btnSendFile = new JButton("GỬI YÊU CẦU (SEND FILE)");
        btnSendFile.setFont(ThemeManager.FONT_BODY_BOLD);
        btnSendFile.setBackground(ThemeManager.ACCENT_PRIMARY);
        btnSendFile.setForeground(Color.WHITE);
        btnSendFile.setIcon(AppIcons.createSendIcon(14, Color.WHITE));
        btnSendFile.setFocusPainted(false);
        btnSendFile.setEnabled(false);
        btnSendFile.addActionListener(e -> sendFileRequest());

        btnResume = new JButton("TIẾP TỤC (RESUME)");
        btnResume.setFont(ThemeManager.FONT_BODY_BOLD);
        btnResume.setBackground(ThemeManager.SUCCESS);
        btnResume.setForeground(Color.WHITE);
        btnResume.setFocusPainted(false);
        btnResume.setEnabled(false);
        btnResume.addActionListener(e -> resumeTransfer());

        btnCancel = new JButton("HỦY (CANCEL)");
        btnCancel.setFont(ThemeManager.FONT_BODY);
        btnCancel.setBackground(ThemeManager.DANGER);
        btnCancel.setForeground(Color.WHITE);
        btnCancel.setFocusPainted(false);
        btnCancel.setEnabled(false);
        btnCancel.addActionListener(e -> cancelTransfer());

        btnOpenFolder = new JButton("MỞ THƯ MỤC");
        btnOpenFolder.setFont(ThemeManager.FONT_SMALL);
        btnOpenFolder.setIcon(AppIcons.createFolderIcon(14, ThemeManager.TEXT_PRIMARY));
        btnOpenFolder.setFocusPainted(false);
        btnOpenFolder.addActionListener(e -> openReceivedFolder());

        buttonBar.add(btnChooseFile);
        buttonBar.add(btnSendFile);
        buttonBar.add(btnResume);
        buttonBar.add(btnCancel);
        buttonBar.add(btnOpenFolder);
        buttonBar.setAlignmentX(Component.LEFT_ALIGNMENT);
        contentPanel.add(buttonBar);
        contentPanel.add(Box.createVerticalStrut(8));

        // 3. Active Transfer Details Card
        JPanel detailsGrid = new JPanel(new GridBagLayout());
        detailsGrid.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)
        ));
        detailsGrid.setBackground(ThemeManager.isDarkMode() ? ThemeManager.BG_INPUT : new Color(250, 251, 254));
        detailsGrid.setAlignmentX(Component.LEFT_ALIGNMENT);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.anchor = GridBagConstraints.WEST;
        gbc.insets = new Insets(3, 4, 3, 10);

        // Row 0: File Name & Size & Direction
        gbc.gridx = 0; gbc.gridy = 0;
        JLabel lblFileTag = new JLabel("Tệp tin (File):");
        lblFileTag.setFont(ThemeManager.FONT_BODY);
        lblFileTag.setForeground(ThemeManager.TEXT_SECONDARY);
        detailsGrid.add(lblFileTag, gbc);

        gbc.gridx = 1;
        JPanel fileInfoRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        fileInfoRow.setOpaque(false);

        lblFileNameVal = new JLabel("(Chưa chọn tệp)");
        lblFileNameVal.setFont(ThemeManager.FONT_BODY_BOLD);
        lblFileNameVal.setForeground(ThemeManager.TEXT_PRIMARY);

        lblFileSizeVal = new JLabel("");
        lblFileSizeVal.setFont(ThemeManager.FONT_BODY_BOLD);
        lblFileSizeVal.setForeground(ThemeManager.ACCENT_PRIMARY);

        lblDirectionVal = new JLabel("");
        lblDirectionVal.setFont(ThemeManager.FONT_SMALL);

        fileInfoRow.add(lblFileNameVal);
        fileInfoRow.add(lblFileSizeVal);
        fileInfoRow.add(lblDirectionVal);
        detailsGrid.add(fileInfoRow, gbc);

        // Row 1: Progress Bar
        gbc.gridx = 0; gbc.gridy = 1;
        JLabel lblProgTag = new JLabel("Tiến độ (Progress):");
        lblProgTag.setFont(ThemeManager.FONT_BODY);
        lblProgTag.setForeground(ThemeManager.TEXT_SECONDARY);
        detailsGrid.add(lblProgTag, gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        progressBar = new JProgressBar(0, 100);
        progressBar.setValue(0);
        progressBar.setStringPainted(true);
        progressBar.setString("0%");
        progressBar.setFont(ThemeManager.FONT_BODY_BOLD);
        progressBar.setPreferredSize(new Dimension(320, 22));
        detailsGrid.add(progressBar, gbc);
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0.0;

        // Row 2: Metrics (Transferred, Speed, ETA)
        gbc.gridx = 0; gbc.gridy = 2;
        JLabel lblMetTag = new JLabel("Chỉ số (Metrics):");
        lblMetTag.setFont(ThemeManager.FONT_BODY);
        lblMetTag.setForeground(ThemeManager.TEXT_SECONDARY);
        detailsGrid.add(lblMetTag, gbc);

        gbc.gridx = 1;
        JPanel metricsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
        metricsPanel.setOpaque(false);

        lblTransferredVal = new JLabel("0 B / 0 B");
        lblTransferredVal.setFont(ThemeManager.FONT_SMALL);
        lblTransferredVal.setForeground(ThemeManager.TEXT_PRIMARY);

        lblSpeedVal = new JLabel("0 B/s");
        lblSpeedVal.setFont(ThemeManager.FONT_BODY_BOLD);
        lblSpeedVal.setForeground(ThemeManager.ACCENT_PRIMARY);

        lblEtaVal = new JLabel("ETA: --");
        lblEtaVal.setFont(ThemeManager.FONT_SMALL);
        lblEtaVal.setForeground(ThemeManager.TEXT_MUTED);

        metricsPanel.add(lblTransferredVal);
        metricsPanel.add(lblSpeedVal);
        metricsPanel.add(lblEtaVal);
        detailsGrid.add(metricsPanel, gbc);

        // Row 3: SHA-256 Checksum
        gbc.gridx = 0; gbc.gridy = 3;
        JLabel lblShaTag = new JLabel("Mã SHA-256:");
        lblShaTag.setFont(ThemeManager.FONT_BODY);
        lblShaTag.setForeground(ThemeManager.TEXT_SECONDARY);
        detailsGrid.add(lblShaTag, gbc);

        gbc.gridx = 1;
        JPanel shaRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        shaRow.setOpaque(false);
        lblSha256Val = new JLabel("(Chưa tính / Chưa có)");
        lblSha256Val.setFont(ThemeManager.FONT_MONO);
        lblSha256Val.setForeground(ThemeManager.TEXT_MUTED);
        shaRow.add(lblSha256Val);

        JButton btnCopySha = new JButton();
        btnCopySha.setIcon(AppIcons.createCopyIcon(11, ThemeManager.TEXT_SECONDARY));
        btnCopySha.setToolTipText("Sao chép SHA-256");
        btnCopySha.setPreferredSize(new Dimension(20, 20));
        btnCopySha.addActionListener(e -> {
            String sha = lblSha256Val.getToolTipText();
            if (sha != null && !sha.isEmpty()) {
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sha), null);
            }
        });
        shaRow.add(btnCopySha);
        detailsGrid.add(shaRow, gbc);

        contentPanel.add(detailsGrid);
        contentPanel.add(Box.createVerticalStrut(6));

        // 4. Status Bar
        JPanel statusBox = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        statusBox.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(2, 6, 2, 6)
        ));
        statusBox.setBackground(ThemeManager.isDarkMode() ? ThemeManager.BG_INPUT : new Color(248, 249, 250));
        statusBox.setAlignmentX(Component.LEFT_ALIGNMENT);

        JLabel lblStatusTag = new JLabel("Trạng thái:");
        lblStatusTag.setFont(ThemeManager.FONT_BODY_BOLD);
        lblStatusVal = new JLabel("Sẵn sàng chọn tệp.");
        lblStatusVal.setFont(ThemeManager.FONT_BODY);
        lblStatusVal.setForeground(ThemeManager.TEXT_PRIMARY);

        statusBox.add(lblStatusTag);
        statusBox.add(lblStatusVal);
        contentPanel.add(statusBox);

        add(contentPanel, BorderLayout.CENTER);
    }

    private void handleFileDropped(File file) {
        if (!file.exists() || !file.isFile() || !file.canRead()) {
            JOptionPane.showMessageDialog(this, "Tệp không hợp lệ hoặc không có quyền đọc!", "Lỗi", JOptionPane.WARNING_MESSAGE);
            return;
        }
        selectFileDirectly(file);
    }

    private void chooseLocalFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Chọn tệp tin để gửi tới bạn bè");
        chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            selectFileDirectly(chooser.getSelectedFile());
        }
    }

    private void selectFileDirectly(File file) {
        this.currentSelectedFile = file;
        this.isCurrentTransferSender = true;

        lblFileNameVal.setText(file.getName());
        lblFileSizeVal.setText(FormatUtils.formatFileSize(file.length()));
        lblDirectionVal.setText("[GỬI ĐI]");
        lblDirectionVal.setForeground(ThemeManager.ACCENT_PRIMARY);

        lblTransferredVal.setText("0 B / " + FormatUtils.formatFileSize(file.length()));
        lblSpeedVal.setText("0 B/s");
        lblEtaVal.setText("ETA: --");
        lblSha256Val.setText("(Sẽ tính khi gửi yêu cầu)");
        lblSha256Val.setToolTipText(null);
        progressBar.setValue(0);
        progressBar.setString("0%");

        lblDropZoneText.setText("Đã chọn: " + file.getName() + " (" + FormatUtils.formatFileSize(file.length()) + ")");
        lblStatusVal.setText("Đã chọn tệp. Sẵn sàng gửi yêu cầu.");
        lblStatusVal.setForeground(ThemeManager.TEXT_PRIMARY);

        updateUIState();
    }

    private void sendFileRequest() {
        if (currentSelectedFile == null || !currentSelectedFile.exists()) {
            JOptionPane.showMessageDialog(this, "Vui lòng chọn tệp tin trước!", "Lỗi", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (!connectionManager.isConnected()) {
            JOptionPane.showMessageDialog(this, "Chưa kết nối tới bạn bè qua mạng ZeroTier!", "Thông báo", JOptionPane.WARNING_MESSAGE);
            return;
        }

        btnSendFile.setEnabled(false);
        btnChooseFile.setEnabled(false);
        lblStatusVal.setText("Đang chuẩn bị file: Tính mã băm SHA-256...");
        lblStatusVal.setForeground(ThemeManager.ACCENT_PRIMARY);

        transferManager.sendFileRequestAsync(
                currentSelectedFile,
                (readBytes, totalBytes) -> {
                    SwingUtilities.invokeLater(() -> {
                        int pct = (int) Math.round(FormatUtils.calculatePercentage(readBytes, totalBytes));
                        progressBar.setValue(pct);
                        progressBar.setString("Hashing SHA-256: " + pct + "%");
                        lblTransferredVal.setText("Hashing: " + FormatUtils.formatFileSize(readBytes) + " / " + FormatUtils.formatFileSize(totalBytes));
                    });
                },
                new FileTransferManager.TransferInitiationCallback() {
                    @Override
                    public void onSuccess(FileMetadata metadata) {
                        SwingUtilities.invokeLater(() -> {
                            lblSha256Val.setText(truncate(metadata.getSha256(), 28));
                            lblSha256Val.setToolTipText(metadata.getSha256());
                            lblStatusVal.setText("Đã gửi yêu cầu nhận tệp. Đang chờ bạn bè xác nhận (Accept/Reject)...");
                            lblStatusVal.setForeground(ThemeManager.ACCENT_PRIMARY);
                            progressBar.setString("Đang chờ phản hồi...");
                            btnCancel.setEnabled(true);
                            if (parentFrame != null) {
                                ToastNotification.show(parentFrame, "Đã gửi yêu cầu chuyển tệp", metadata.getFileName() + " • " + FormatUtils.formatFileSize(metadata.getFileSize()), ToastNotification.ToastType.INFO);
                            }
                        });
                    }

                    @Override
                    public void onError(Exception ex) {
                        SwingUtilities.invokeLater(() -> {
                            String errorMessage = ex != null ? ex.getMessage() : "Unknown error";
                            lblStatusVal.setText("Lỗi: " + errorMessage);
                            lblStatusVal.setForeground(ThemeManager.DANGER);
                            btnChooseFile.setEnabled(true);
                            btnSendFile.setEnabled(true);
                            if (parentFrame != null) {
                                ToastNotification.show(parentFrame, "Lỗi tính mã băm SHA-256", errorMessage, ToastNotification.ToastType.ERROR);
                            }
                        });
                    }
                }
        );
    }

    private void resumeTransfer() {
        btnResume.setEnabled(false);
        lblStatusVal.setText("Đang thỏa thuận tiếp tục truyền...");
        lblStatusVal.setForeground(ThemeManager.WARNING);
        try {
            transferManager.requestResume();
        } catch (Exception ex) {
            lblStatusVal.setText("Lỗi tiếp tục truyền: " + ex.getMessage());
            lblStatusVal.setForeground(ThemeManager.DANGER);
        }
    }


    private void cancelTransfer() {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Bạn có chắc chắn muốn hủy quá trình truyền tệp này không?",
                "Xác nhận hủy",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (confirm == JOptionPane.YES_OPTION) {
            btnCancel.setEnabled(false);
            btnResume.setEnabled(false);
            transferManager.cancelTransfer();
            lblStatusVal.setText("Đang yêu cầu hủy truyền tệp...");
            lblStatusVal.setForeground(ThemeManager.DANGER);
        }
    }

    private void openReceivedFolder() {
        File dir = new File(Constants.DEFAULT_DOWNLOAD_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(dir);
            } else {
                JOptionPane.showMessageDialog(this, "Thư mục tải về: " + dir.getAbsolutePath(), "Thông báo", JOptionPane.INFORMATION_MESSAGE);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Không thể mở thư mục: " + ex.getMessage(), "Lỗi", JOptionPane.ERROR_MESSAGE);
        }
    }

    public void updateUIState() {
        SwingUtilities.invokeLater(() -> {
            boolean connected = connectionManager.isConnected();
            boolean fileChosen = currentSelectedFile != null && currentSelectedFile.exists();
            TransferState state = transferManager.getCurrentState();

            boolean isTransferring = (state == TransferState.TRANSFERRING || state == TransferState.RESUMING || state == TransferState.VERIFYING);
            boolean isInterrupted = (state == TransferState.INTERRUPTED);

            btnChooseFile.setEnabled(!isTransferring);
            btnSendFile.setEnabled(connected && fileChosen && !isTransferring && state != TransferState.PENDING && state != TransferState.READY_TO_TRANSFER);
            btnCancel.setEnabled(isTransferring || isInterrupted || state == TransferState.PENDING || state == TransferState.READY_TO_TRANSFER);
            btnResume.setEnabled(connected && isInterrupted);
        });
    }

    // === TransferProgressListener (Throttled for 100 GB files) ===

    @Override
    public void onProgress(FileMetadata metadata, long bytesTransferred, long totalBytes, double speedBps, long etaSeconds) {
        long now = System.currentTimeMillis();
        // Throttle updates to at most 10 times per second (100ms) to keep UI ultra-responsive
        if (now - lastProgressUiUpdateTime < 100 && bytesTransferred < totalBytes && bytesTransferred > 0) {
            return;
        }
        lastProgressUiUpdateTime = now;

        SwingUtilities.invokeLater(() -> {
            int pct = (int) Math.round(FormatUtils.calculatePercentage(bytesTransferred, totalBytes));
            progressBar.setValue(pct);
            progressBar.setString(pct + "%");

            lblTransferredVal.setText(FormatUtils.formatFileSize(bytesTransferred) + " / " + FormatUtils.formatFileSize(totalBytes));
            lblSpeedVal.setText(FormatUtils.formatSpeed(speedBps));
            lblEtaVal.setText("ETA: " + FormatUtils.formatEta(etaSeconds));
        });
    }

    // === FileTransferListener ===

    @Override
    public void onIncomingFileRequest(FileMetadata metadata) {
        SwingUtilities.invokeLater(() -> {
            this.isCurrentTransferSender = false;
            lblFileNameVal.setText(metadata.getFileName());
            lblFileSizeVal.setText(FormatUtils.formatFileSize(metadata.getFileSize()));
            lblDirectionVal.setText("[NHẬN VỀ]");
            lblDirectionVal.setForeground(ThemeManager.SUCCESS);

            lblSha256Val.setText(truncate(metadata.getSha256(), 28));
            lblSha256Val.setToolTipText(metadata.getSha256());
            progressBar.setValue(0);
            progressBar.setString("0%");
            lblTransferredVal.setText("0 B / " + FormatUtils.formatFileSize(metadata.getFileSize()));
            lblStatusVal.setText("Nhận được yêu cầu tệp từ " + metadata.getSender() + ".");
            lblStatusVal.setForeground(ThemeManager.ACCENT_PRIMARY);
            updateUIState();

            if (parentFrame != null) {
                ToastNotification.show(parentFrame, "Yêu cầu tệp đến", metadata.getFileName() + " (" + FormatUtils.formatFileSize(metadata.getFileSize()) + ")", ToastNotification.ToastType.INFO);
            }
        });
    }

    @Override
    public void onFileRequestAccepted(FileMetadata metadata) {
        SwingUtilities.invokeLater(() -> {
            lblStatusVal.setText("Bạn bè đã chấp nhận tệp. Bắt đầu truyền dữ liệu qua Cổng 8889...");
            lblStatusVal.setForeground(ThemeManager.SUCCESS);
            updateUIState();
            if (parentFrame != null) {
                ToastNotification.show(parentFrame, "Tệp được chấp nhận", "Bắt đầu truyền: " + metadata.getFileName(), ToastNotification.ToastType.SUCCESS);
            }
        });
    }

    @Override
    public void onFileRequestRejected(FileMetadata metadata, String reason) {
        SwingUtilities.invokeLater(() -> {
            lblStatusVal.setText("Yêu cầu gửi tệp bị từ chối: " + reason);
            lblStatusVal.setForeground(ThemeManager.DANGER);
            progressBar.setString("Bị từ chối");
            updateUIState();
            if (historyPanel != null) {
                historyPanel.addEntry(metadata, TransferState.REJECTED, isCurrentTransferSender);
            }
            if (parentFrame != null) {
                ToastNotification.show(parentFrame, "Tệp bị từ chối", reason, ToastNotification.ToastType.WARNING);
            }
        });
    }

    @Override
    public void onTransferStateChanged(FileMetadata metadata, TransferState state, String statusMessage) {
        SwingUtilities.invokeLater(() -> {
            lblStatusVal.setText(statusMessage);
            if (state == TransferState.VERIFYING) {
                lblStatusVal.setForeground(ThemeManager.ACCENT_PRIMARY);
                progressBar.setString("Đang kiểm tra SHA-256...");
            } else if (state == TransferState.VERIFIED) {
                lblStatusVal.setForeground(ThemeManager.SUCCESS);
                progressBar.setString("SHA-256 Hợp Lệ!");
            } else if (state == TransferState.FAILED) {
                lblStatusVal.setForeground(ThemeManager.DANGER);
                progressBar.setString("Lỗi");
                if (historyPanel != null && metadata != null) {
                    historyPanel.addEntry(metadata, TransferState.FAILED, isCurrentTransferSender);
                }
                if (parentFrame != null) {
                    ToastNotification.show(parentFrame, "Truyền tệp thất bại", statusMessage, ToastNotification.ToastType.ERROR);
                }
            } else if (state == TransferState.CANCELED) {
                lblStatusVal.setForeground(ThemeManager.WARNING);
                progressBar.setString("Đã hủy");
                if (historyPanel != null && metadata != null) {
                    historyPanel.addEntry(metadata, TransferState.CANCELED, isCurrentTransferSender);
                }
                if (parentFrame != null) {
                    ToastNotification.show(parentFrame, "Đã hủy truyền tệp", metadata != null ? metadata.getFileName() : "", ToastNotification.ToastType.WARNING);
                }
            } else if (state == TransferState.INTERRUPTED) {
                lblStatusVal.setForeground(ThemeManager.WARNING);
                progressBar.setString("Bị gián đoạn");
                if (parentFrame != null) {
                    ToastNotification.show(parentFrame, "Mất kết nối mạng", "Tệp tạm .part đã bảo tồn. Sẵn sàng Resume.", ToastNotification.ToastType.WARNING);
                }
            }
            updateUIState();
        });
    }

    @Override
    public void onProgress(FileMetadata metadata, long bytesTransferred, long totalBytes, double speedBps) {
        onProgress(metadata, bytesTransferred, totalBytes, speedBps, -1);
    }

    @Override
    public void onStateChanged(FileMetadata metadata, TransferState oldState, TransferState newState) {
    }

    @Override
    public void onCompleted(FileMetadata metadata) {
        SwingUtilities.invokeLater(() -> {
            progressBar.setValue(100);
            progressBar.setString("100% Hoàn tất");
            lblStatusVal.setText("✅ SHA-256 verified | ✅ File transfer completed: " + metadata.getFileName());
            lblStatusVal.setForeground(ThemeManager.SUCCESS);

            if (historyPanel != null) {
                historyPanel.addEntry(metadata, TransferState.COMPLETED, isCurrentTransferSender);
            }
            if (parentFrame != null) {
                ToastNotification.show(parentFrame, "Hoàn tất & Xác minh thành công",
                        metadata.getFileName() + " (" + FormatUtils.formatFileSize(metadata.getFileSize()) + ")",
                        ToastNotification.ToastType.SUCCESS);
            }
            updateUIState();
        });
    }

    @Override
    public void onError(FileMetadata metadata, String errorMessage, Throwable cause) {
        SwingUtilities.invokeLater(() -> {
            lblStatusVal.setText("Lỗi: " + errorMessage);
            lblStatusVal.setForeground(ThemeManager.DANGER);
            progressBar.setString("Lỗi");
            if (historyPanel != null && metadata != null) {
                historyPanel.addEntry(metadata, TransferState.FAILED, isCurrentTransferSender);
            }
            if (parentFrame != null) {
                ToastNotification.show(parentFrame, "Truyền tệp thất bại", errorMessage, ToastNotification.ToastType.ERROR);
            }
            updateUIState();
        });
    }


    private String truncate(String text, int max) {
        if (text == null) return "";
        if (text.length() <= max) return text;
        return text.substring(0, max - 3) + "...";
    }
}
