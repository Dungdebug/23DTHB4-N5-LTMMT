package dammtp2p.client.ui;

import dammtp2p.ui.theme.AppIcons;
import dammtp2p.ui.theme.ThemeManager;
import dammtp2p.util.FormatUtils;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

/**
 * Dedicated File Transfer Center in DAMMTP2P Mesh Workspace.
 * 
 * Houses 4 KPI Telemetry Cards:
 * - Speed (MB/s)
 * - Bytes Transferred / Total (up to 20 GiB)
 * - Remaining Time (ETA)
 * - SHA-256 Checksum Validation Status
 * 
 * Also provides an active transfer sessions management table and a fast sender trigger.
 */
public class TransferCenterDialog extends JDialog {

    public interface TransferActionCallback {
        void onSendFile(String receiverOrRoom, File file, boolean encrypt);
        void onCancelTransfer(String transferId);
    }

    private final TransferActionCallback callback;

    // 4 KPI Telemetry Gauges
    private JLabel lblGaugeSpeedVal;
    private JLabel lblGaugeTransferredVal;
    private JLabel lblGaugeEtaVal;
    private JLabel lblGaugeIntegrityVal;
    private JProgressBar overallProgressBar;

    // Active transfers table
    private DefaultTableModel tableModel;
    private JTable transfersTable;

    // Quick Send Panel
    private JComboBox<String> cmbTarget;
    private JLabel lblSelectedFileName;
    private File stagedFile;
    private JCheckBox chkEncrypt;
    private JButton btnSendOffer;

    public TransferCenterDialog(Frame owner, TransferActionCallback callback) {
        super(owner, "Trung Tâm Quản Lý Truyền Tệp — DAMMTP2P Mesh", true);
        this.callback = callback;
        setSize(860, 600);
        setLocationRelativeTo(owner);
        getContentPane().setBackground(ThemeManager.BG_DARK);

        initContent();
    }

    private void initContent() {
        JPanel root = new JPanel(new BorderLayout(0, 14));
        root.setBackground(ThemeManager.BG_DARK);
        root.setBorder(BorderFactory.createEmptyBorder(12, 16, 16, 16));

        // 1. TOP: 4 KPI Telemetry Gauges
        root.add(createTelemetryPanel(), BorderLayout.NORTH);

        // 2. CENTER: Active Transfers Table
        root.add(createTransfersTablePanel(), BorderLayout.CENTER);

        // 3. SOUTH: Quick Send File Section
        root.add(createQuickSendPanel(), BorderLayout.SOUTH);

        setContentPane(root);
    }

    private JPanel createTelemetryPanel() {
        JPanel container = new JPanel(new BorderLayout(0, 8));
        container.setOpaque(false);

        JPanel gaugesGrid = new JPanel(new GridLayout(1, 4, 10, 0));
        gaugesGrid.setOpaque(false);

        lblGaugeSpeedVal = new JLabel("0.0 KB/s");
        gaugesGrid.add(createGaugeCard("TỐC ĐỘ TRUYỀN", lblGaugeSpeedVal, ThemeManager.ACCENT_ELECTRIC_CYAN));

        lblGaugeTransferredVal = new JLabel("0 B / 0 B");
        gaugesGrid.add(createGaugeCard("DỮ LIỆU ĐÃ TRUYỀN", lblGaugeTransferredVal, ThemeManager.SUCCESS));

        lblGaugeEtaVal = new JLabel("--:--");
        gaugesGrid.add(createGaugeCard("THỜI GIAN CÒN LẠI", lblGaugeEtaVal, ThemeManager.ACCENT_AMBER));

        lblGaugeIntegrityVal = new JLabel("SẴN SÀNG");
        gaugesGrid.add(createGaugeCard("KIỂM TRA SHA-256", lblGaugeIntegrityVal, ThemeManager.ACCENT_PURPLE));

        overallProgressBar = new JProgressBar(0, 100) {
            @Override
            protected void paintComponent(Graphics g) {
                if (getPercentComplete() > 0) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(ThemeManager.BG_INPUT);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                    int fillWidth = (int) (getWidth() * getPercentComplete());
                    if (fillWidth > 0) {
                        LinearGradientPaint gp = new LinearGradientPaint(
                                0, 0, Math.max(fillWidth, 1), 0,
                                new float[]{0.0f, 0.5f, 1.0f},
                                new Color[]{ThemeManager.ACCENT_PRIMARY, ThemeManager.ACCENT_CYAN, ThemeManager.ACCENT_ELECTRIC_CYAN}
                        );
                        g2.setPaint(gp);
                        g2.fillRoundRect(0, 0, fillWidth, getHeight(), 10, 10);
                    }
                    if (isStringPainted() && getString() != null) {
                        g2.setFont(getFont());
                        FontMetrics fm = g2.getFontMetrics();
                        int tx = (getWidth() - fm.stringWidth(getString())) / 2;
                        int ty = (getHeight() + fm.getAscent() - fm.getDescent()) / 2;
                        g2.setColor(Color.WHITE);
                        g2.drawString(getString(), tx, ty);
                    }
                    g2.dispose();
                } else {
                    super.paintComponent(g);
                }
            }
        };
        overallProgressBar.setStringPainted(true);
        overallProgressBar.setForeground(ThemeManager.ACCENT_CYAN);
        overallProgressBar.setBackground(ThemeManager.BG_INPUT);
        overallProgressBar.setPreferredSize(new Dimension(800, 22));
        overallProgressBar.setString("Sẵn sàng truyền tệp (Giới hạn tối đa 20 GiB)");

        container.add(gaugesGrid, BorderLayout.CENTER);
        container.add(overallProgressBar, BorderLayout.SOUTH);
        return container;
    }

    private JPanel createGaugeCard(String title, JLabel valueLabel, Color accent) {
        JPanel card = new JPanel(new BorderLayout(4, 4)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Glassy card background
                g2.setColor(new Color(0x16, 0x1B, 0x27, 240));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);

                // Top accent bar
                g2.setColor(accent);
                g2.fillRoundRect(0, 0, getWidth() - 1, 3, 3, 3);

                // Subtle glowing border
                g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 60));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));

        JLabel lblTitle = new JLabel(title);
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 10));
        lblTitle.setForeground(ThemeManager.TEXT_MUTED);

        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 15));
        valueLabel.setForeground(accent);

        card.add(lblTitle, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    private JPanel createTransfersTablePanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setOpaque(false);

        JPanel headerRow = new JPanel(new BorderLayout());
        headerRow.setOpaque(false);

        JLabel lblHeader = new JLabel("DANH SÁCH CÁC PHIÊN TRUYỀN TỆP (TCP PORT 8889)");
        lblHeader.setFont(ThemeManager.FONT_SMALL_BOLD);
        lblHeader.setForeground(ThemeManager.TEXT_MUTED);

        JButton btnOpenFolder = new JButton("Mở Thư Mục Nhận Tệp", AppIcons.createFileIcon(14, ThemeManager.ACCENT_CYAN));
        btnOpenFolder.setFont(ThemeManager.FONT_SMALL);
        btnOpenFolder.setForeground(ThemeManager.ACCENT_CYAN);
        btnOpenFolder.setContentAreaFilled(false);
        btnOpenFolder.setBorderPainted(false);
        btnOpenFolder.setFocusPainted(false);
        btnOpenFolder.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnOpenFolder.addActionListener(e -> {
            try {
                File dl = new File("downloads");
                if (!dl.exists()) dl.mkdirs();
                Desktop.getDesktop().open(dl);
            } catch (Exception ex) {
                // Ignore if not supported on platform
            }
        });

        headerRow.add(lblHeader, BorderLayout.WEST);
        headerRow.add(btnOpenFolder, BorderLayout.EAST);
        panel.add(headerRow, BorderLayout.NORTH);

        String[] cols = {"Mã Phiên", "Tên Tệp", "Dung Lượng", "Đối Tác", "Tiến Trình", "Trạng Thái"};
        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        transfersTable = new JTable(tableModel) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                if (getRowCount() == 0) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setFont(ThemeManager.FONT_BODY);
                    g2.setColor(ThemeManager.TEXT_MUTED);
                    String emptyMsg = "Chưa có phiên truyền tệp nào trong phiên làm việc này.";
                    String subMsg = "Kéo thả tệp vào đây hoặc bấm 'Chọn Tệp...' bên dưới để bắt đầu truyền.";
                    FontMetrics fm = g2.getFontMetrics();
                    int x1 = (getWidth() - fm.stringWidth(emptyMsg)) / 2;
                    int y1 = getHeight() / 2 - 10;
                    g2.drawString(emptyMsg, Math.max(10, x1), y1);

                    g2.setFont(ThemeManager.FONT_SMALL);
                    fm = g2.getFontMetrics();
                    int x2 = (getWidth() - fm.stringWidth(subMsg)) / 2;
                    int y2 = y1 + 22;
                    g2.setColor(new Color(0x64, 0x74, 0x8B));
                    g2.drawString(subMsg, Math.max(10, x2), y2);
                    g2.dispose();
                }
            }
        };
        transfersTable.setBackground(ThemeManager.BG_CARD);
        transfersTable.setForeground(ThemeManager.TEXT_PRIMARY);
        transfersTable.setGridColor(ThemeManager.BORDER_SUBTLE);
        transfersTable.setRowHeight(34);
        transfersTable.setShowVerticalLines(false);
        transfersTable.getTableHeader().setBackground(ThemeManager.BG_NAV);
        transfersTable.getTableHeader().setForeground(ThemeManager.TEXT_SECONDARY);
        transfersTable.getTableHeader().setFont(ThemeManager.FONT_SMALL_BOLD);
        transfersTable.getTableHeader().setPreferredSize(new Dimension(Integer.MAX_VALUE, 30));

        transfersTable.setDefaultRenderer(Object.class, new javax.swing.table.DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int col) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
                setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
                setBackground(isSelected ? ThemeManager.BG_ACTIVE : (row % 2 == 0 ? ThemeManager.BG_CARD : new Color(0x13, 0x18, 0x24)));
                setForeground(ThemeManager.TEXT_PRIMARY);

                String text = value != null ? value.toString() : "";
                if (col == 1) { // Tên Tệp
                    setIcon(AppIcons.createFileIcon(16, ThemeManager.ACCENT_CYAN));
                    setForeground(ThemeManager.ACCENT_CYAN);
                } else {
                    setIcon(null);
                }

                if (col == 4) { // Tiến Trình
                    if (text.contains("100%")) {
                        setForeground(ThemeManager.SUCCESS);
                    } else if (text.contains("%")) {
                        setForeground(ThemeManager.ACCENT_ELECTRIC_CYAN);
                    }
                } else if (col == 5) { // Trạng Thái
                    if (text.toLowerCase().contains("hoàn tất") || text.toLowerCase().contains("xác minh")) {
                        setForeground(ThemeManager.SUCCESS);
                    } else if (text.toLowerCase().contains("đang truyền")) {
                        setForeground(ThemeManager.ACCENT_AMBER);
                    } else if (text.toLowerCase().contains("lỗi") || text.toLowerCase().contains("hủy")) {
                        setForeground(ThemeManager.DANGER);
                    }
                }
                return c;
            }
        });

        // Context Menu on row click
        JPopupMenu popup = new JPopupMenu();
        JMenuItem itemOpenFolderPopup = new JMenuItem("Mở thư mục tải về (Downloads)", AppIcons.createFileIcon(14, ThemeManager.ACCENT_CYAN));
        itemOpenFolderPopup.addActionListener(e -> {
            try {
                File dl = new File("downloads");
                if (!dl.exists()) dl.mkdirs();
                Desktop.getDesktop().open(dl);
            } catch (Exception ex) {
                // ignore
            }
        });
        popup.add(itemOpenFolderPopup);

        JMenuItem itemCopyId = new JMenuItem("Sao chép mã phiên (Transfer ID)");
        itemCopyId.addActionListener(e -> {
            int row = transfersTable.getSelectedRow();
            if (row >= 0) {
                String id = (String) tableModel.getValueAt(row, 0);
                Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(id), null);
            }
        });
        popup.add(itemCopyId);

        JMenuItem itemCancel = new JMenuItem("Hủy phiên truyền này", AppIcons.createStatusDot(8, ThemeManager.DANGER));
        itemCancel.addActionListener(e -> {
            int row = transfersTable.getSelectedRow();
            if (row >= 0 && callback != null) {
                String id = (String) tableModel.getValueAt(row, 0);
                callback.onCancelTransfer(id);
                tableModel.setValueAt("Đã hủy ✕", row, 5);
            }
        });
        popup.add(itemCancel);

        transfersTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowPopup(e);
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowPopup(e);
            }
            private void maybeShowPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int row = transfersTable.rowAtPoint(e.getPoint());
                    if (row >= 0 && row < transfersTable.getRowCount()) {
                        transfersTable.setRowSelectionInterval(row, row);
                    }
                    popup.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });

        JScrollPane scroll = new JScrollPane(transfersTable);
        scroll.setBorder(BorderFactory.createLineBorder(ThemeManager.BORDER_COLOR, 1));
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createQuickSendPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 8));
        panel.setBackground(ThemeManager.BG_CARD);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(10, 14, 10, 14)
        ));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);

        JButton btnChoose = ThemeManager.createGradientButton("Chọn Tệp...", ThemeManager.ACCENT_PRIMARY, ThemeManager.ACCENT_CYAN, Color.WHITE);
        btnChoose.setIcon(AppIcons.createTransfersIcon(16, Color.WHITE));
        btnChoose.setPreferredSize(new Dimension(130, 32));
        btnChoose.addActionListener(e -> chooseFile());

        lblSelectedFileName = new JLabel("Chưa chọn tệp");
        lblSelectedFileName.setFont(ThemeManager.FONT_BODY);
        lblSelectedFileName.setForeground(ThemeManager.TEXT_MUTED);

        left.add(btnChoose);
        left.add(lblSelectedFileName);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);

        JLabel lblTo = new JLabel("Gửi tới:");
        lblTo.setFont(ThemeManager.FONT_BODY);
        lblTo.setForeground(ThemeManager.TEXT_SECONDARY);

        cmbTarget = new JComboBox<>(new String[]{"[Nhóm] # Lobby"});
        cmbTarget.setPreferredSize(new Dimension(160, 28));

        chkEncrypt = new JCheckBox("Mã hóa AES-128", true);
        chkEncrypt.setOpaque(false);
        chkEncrypt.setForeground(ThemeManager.TEXT_SECONDARY);

        JCheckBox chkAutoAcp = new JCheckBox("Tự động nhận", dammtp2p.util.AppConfig.getInstance().isAutoAcceptFiles());
        chkAutoAcp.setOpaque(false);
        chkAutoAcp.setForeground(ThemeManager.ACCENT_CYAN);
        chkAutoAcp.setToolTipText("Tự động chấp nhận khi bạn bè hoặc phòng nhóm gửi tệp đến");
        chkAutoAcp.addActionListener(e -> {
            dammtp2p.util.AppConfig.getInstance().setAutoAcceptFiles(chkAutoAcp.isSelected());
            dammtp2p.util.AppConfig.getInstance().save();
        });

        btnSendOffer = ThemeManager.createGradientButton("Gửi Tệp", ThemeManager.SUCCESS, ThemeManager.ACCENT_PRIMARY, Color.WHITE);
        btnSendOffer.setFont(ThemeManager.FONT_BODY_BOLD);
        btnSendOffer.setPreferredSize(new Dimension(100, 32));
        btnSendOffer.setEnabled(false);
        btnSendOffer.addActionListener(e -> {
            if (stagedFile != null && callback != null) {
                String target = (String) cmbTarget.getSelectedItem();
                callback.onSendFile(target, stagedFile, chkEncrypt.isSelected());
                btnSendOffer.setEnabled(false);
                lblSelectedFileName.setText("Đang gửi đề nghị: " + stagedFile.getName());
            }
        });

        right.add(lblTo);
        right.add(cmbTarget);
        right.add(chkEncrypt);
        right.add(chkAutoAcp);
        right.add(btnSendOffer);

        panel.add(left, BorderLayout.WEST);
        panel.add(right, BorderLayout.EAST);
        return panel;
    }

    private void chooseFile() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Chọn tệp cần gửi (Hỗ trợ tối đa 20 GiB)");
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            setStagedFile(chooser.getSelectedFile());
        }
    }

    public void setStagedFile(File file) {
        this.stagedFile = file;
        if (file != null) {
            String sizeStr = FormatUtils.formatFileSize(file.length());
            lblSelectedFileName.setText(file.getName() + " (" + sizeStr + ")");
            btnSendOffer.setEnabled(true);
        } else {
            lblSelectedFileName.setText("Chưa chọn tệp");
            btnSendOffer.setEnabled(false);
        }
    }

    public void updateTargets(List<String> users, List<String> rooms) {
        cmbTarget.removeAllItems();
        if (rooms != null) {
            for (String r : rooms) cmbTarget.addItem("[Nhóm] # " + r);
        }
        if (users != null) {
            for (String u : users) cmbTarget.addItem(u);
        }
    }

    public void updateTelemetry(long transferred, long total, double speedBytesPerSec, String integrityText) {
        int pct = total > 0 ? (int) ((transferred * 100) / total) : 0;
        overallProgressBar.setValue(pct);
        overallProgressBar.setString(pct + "% (" + FormatUtils.formatFileSize(transferred) + " / " + FormatUtils.formatFileSize(total) + ")");

        lblGaugeSpeedVal.setText(FormatUtils.formatSpeed(speedBytesPerSec));
        lblGaugeTransferredVal.setText(FormatUtils.formatFileSize(transferred) + " / " + FormatUtils.formatFileSize(total));

        long remaining = Math.max(0, total - transferred);
        lblGaugeEtaVal.setText(FormatUtils.formatEta(speedBytesPerSec, remaining));

        if (integrityText != null) {
            if (transferred < total && (integrityText.toLowerCase().contains("validated")
                    || integrityText.toLowerCase().contains("khớp")
                    || integrityText.toLowerCase().contains("xác minh"))) {
                lblGaugeIntegrityVal.setText("Đang truyền (" + pct + "%)");
            } else {
                lblGaugeIntegrityVal.setText(integrityText);
            }
        }
    }

    public void addOrUpdateTransferRow(String transferId, String fileName, long size, String partner, int progress, String status) {
        String shortId = (transferId != null && transferId.length() >= 8) ? transferId.substring(0, 8) : transferId;
        String sizeStr = FormatUtils.formatFileSize(size);

        boolean found = false;
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            if (tableModel.getValueAt(i, 0).equals(shortId)) {
                if (partner != null && !partner.isEmpty() && !partner.equalsIgnoreCase("Unknown")) {
                    tableModel.setValueAt(partner, i, 3);
                }
                tableModel.setValueAt(progress + "%", i, 4);
                tableModel.setValueAt(status, i, 5);
                found = true;
                break;
            }
        }
        if (!found) {
            tableModel.addRow(new Object[]{shortId, fileName, sizeStr, partner, progress + "%", status});
        }
    }
}
