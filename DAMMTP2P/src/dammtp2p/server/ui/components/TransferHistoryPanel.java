package dammtp2p.ui.components;

import dammtp2p.core.model.FileMetadata;
import dammtp2p.core.model.TransferState;
import dammtp2p.ui.theme.AppIcons;
import dammtp2p.ui.theme.ThemeManager;
import dammtp2p.util.Constants;
import dammtp2p.util.FormatUtils;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

/**
 * Lightweight in-memory history panel displaying recently completed, failed,
 * or canceled file transfers with a direct "Open Folder" action.
 */
public class TransferHistoryPanel extends JPanel {

    public static class HistoryEntry {
        public final String fileName;
        public final long fileSize;
        public final TransferState finalState;
        public final long timestamp;
        public final boolean isSender;

        public HistoryEntry(String fileName, long fileSize, TransferState state, long timestamp, boolean isSender) {
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.finalState = state;
            this.timestamp = timestamp;
            this.isSender = isSender;
        }
    }

    private final List<HistoryEntry> entries = new ArrayList<>();
    private final JPanel listContainer;

    public TransferHistoryPanel() {
        setLayout(new BorderLayout(0, 4));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(ThemeManager.BORDER_COLOR, 1),
                        "Lịch Sử Truyền Tệp (Recent Transfers)",
                        0, 0, ThemeManager.FONT_BODY_BOLD,
                        ThemeManager.isDarkMode() ? ThemeManager.TEXT_PRIMARY : Color.DARK_GRAY
                ),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)
        ));

        listContainer = new JPanel();
        listContainer.setLayout(new BoxLayout(listContainer, BoxLayout.Y_AXIS));
        listContainer.setOpaque(false);

        JScrollPane scrollPane = new JScrollPane(listContainer);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.setPreferredSize(new Dimension(280, 140));
        add(scrollPane, BorderLayout.CENTER);

        // Open received directory button
        JButton btnOpenFolder = new JButton("MỞ THƯ MỤC TẢI VỀ");
        btnOpenFolder.setFont(ThemeManager.FONT_SMALL);
        btnOpenFolder.setIcon(AppIcons.createFolderIcon(14, ThemeManager.TEXT_PRIMARY));
        btnOpenFolder.setFocusPainted(false);
        btnOpenFolder.addActionListener(e -> openDownloadsDirectory());
        add(btnOpenFolder, BorderLayout.SOUTH);

        renderEmptyState();
    }

    public synchronized void addEntry(FileMetadata meta, TransferState state, boolean isSender) {
        HistoryEntry entry = new HistoryEntry(
                meta.getFileName(),
                meta.getFileSize(),
                state,
                System.currentTimeMillis(),
                isSender
        );
        entries.add(0, entry); // Most recent on top
        if (entries.size() > 50) {
            entries.remove(entries.size() - 1);
        }
        SwingUtilities.invokeLater(this::rebuildList);
    }

    private void rebuildList() {
        listContainer.removeAll();
        if (entries.isEmpty()) {
            renderEmptyState();
        } else {
            for (HistoryEntry e : entries) {
                listContainer.add(createEntryRow(e));
                listContainer.add(Box.createVerticalStrut(4));
            }
        }
        listContainer.revalidate();
        listContainer.repaint();
    }

    private JPanel createEntryRow(HistoryEntry entry) {
        JPanel row = new JPanel(new BorderLayout(6, 2));
        row.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(4, 6, 4, 6)
        ));
        row.setBackground(ThemeManager.isDarkMode() ? ThemeManager.BG_INPUT : new Color(245, 247, 250));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));

        // Status Icon
        JLabel lblIcon = new JLabel();
        if (entry.finalState == TransferState.COMPLETED || entry.finalState == TransferState.VERIFIED) {
            lblIcon.setIcon(AppIcons.createCheckIcon(14, ThemeManager.SUCCESS));
        } else if (entry.finalState == TransferState.CANCELED) {
            lblIcon.setIcon(AppIcons.createStatusDot(8, ThemeManager.WARNING));
        } else {
            lblIcon.setIcon(AppIcons.createAlertIcon(14, ThemeManager.DANGER));
        }
        row.add(lblIcon, BorderLayout.WEST);

        // Center: File Name + Size + Direction
        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setOpaque(false);

        JLabel lblName = new JLabel(truncate(entry.fileName, 24));
        lblName.setFont(ThemeManager.FONT_BODY_BOLD);
        lblName.setForeground(ThemeManager.TEXT_PRIMARY);
        center.add(lblName);

        String dir = entry.isSender ? "Gửi đi" : "Nhận về";
        String info = String.format("%s • %s • %s", FormatUtils.formatFileSize(entry.fileSize), dir, FormatUtils.formatTime(entry.timestamp));
        JLabel lblInfo = new JLabel(info);
        lblInfo.setFont(ThemeManager.FONT_SMALL);
        lblInfo.setForeground(ThemeManager.TEXT_MUTED);
        center.add(lblInfo);

        row.add(center, BorderLayout.CENTER);

        // Status badge
        JLabel lblState = new JLabel(entry.finalState.name());
        lblState.setFont(ThemeManager.FONT_SMALL);
        if (entry.finalState == TransferState.COMPLETED) {
            lblState.setForeground(ThemeManager.SUCCESS);
        } else if (entry.finalState == TransferState.CANCELED) {
            lblState.setForeground(ThemeManager.WARNING);
        } else {
            lblState.setForeground(ThemeManager.DANGER);
        }
        row.add(lblState, BorderLayout.EAST);

        return row;
    }

    private void renderEmptyState() {
        listContainer.removeAll();
        JLabel lblEmpty = new JLabel("Chưa có giao dịch truyền tệp nào.");
        lblEmpty.setFont(ThemeManager.FONT_SMALL);
        lblEmpty.setForeground(ThemeManager.TEXT_MUTED);
        lblEmpty.setAlignmentX(Component.CENTER_ALIGNMENT);
        listContainer.add(Box.createVerticalStrut(20));
        listContainer.add(lblEmpty);
        listContainer.revalidate();
        listContainer.repaint();
    }

    private void openDownloadsDirectory() {
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

    private String truncate(String text, int max) {
        if (text == null) return "";
        if (text.length() <= max) return text;
        return text.substring(0, max - 3) + "...";
    }
}
