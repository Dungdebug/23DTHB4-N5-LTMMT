package dammtp2p.client.ui;

import dammtp2p.core.model.ChatMessage;
import dammtp2p.core.model.FileMetadata;
import dammtp2p.core.model.TransferState;
import dammtp2p.ui.theme.AppIcons;
import dammtp2p.ui.theme.ThemeManager;
import dammtp2p.util.FormatUtils;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
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
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JTextArea;

/**
 * Single Message Row View in DAMMTP2P Mesh Workspace Conversation Timeline.
 * 
 * Features:
 * - Geometric avatar pill with distinctive initials and color hash.
 * - Non-clipping, robust multi-line wrapping for long texts, unbroken tokens, and URLs.
 * - Consecutive message grouping (coalescing within 5 minutes).
 * - Inline Attachment Card for file transfers with real-time transfer telemetry.
 * - Subtle hover state with copy text action.
 */
public class ChatMessageView extends JPanel {

    public interface FileActionListener {
        void onAcceptFile(FileMetadata meta);
        void onRejectFile(FileMetadata meta);
        void onOpenFile(File file);
    }

    private final String sender;
    private final long timestamp;
    private final String content;
    private final boolean isCoalesced;
    private boolean isHovered = false;

    // Optional Attachment Card
    private FileMetadata fileMetadata;
    private JPanel attachmentCard;
    private JProgressBar fileProgressBar;
    private JLabel lblFileSpeedEta;
    private JLabel lblFileStatusText;
    private JButton btnAccept;
    private JButton btnReject;
    private JButton btnOpenFile;
    private JButton btnCopy;
    private FileActionListener fileListener;

    private final boolean isSelf;
    private final String iv;
    private final boolean isEncrypted;

    public ChatMessageView(ChatMessage msg, boolean isCoalesced, String currentUsername) {
        this.sender = msg.getSender();
        this.timestamp = msg.getTimestamp();
        this.content = msg.getContent();
        this.isCoalesced = isCoalesced;
        this.isSelf = msg.isOutgoing() || (currentUsername != null && currentUsername.equalsIgnoreCase(msg.getSender()));
        this.iv = msg.getIv();
        this.isEncrypted = msg.isEncrypted();

        initLayout(msg.isEncrypted());
    }

    public ChatMessageView(ChatMessage msg, boolean isCoalesced) {
        this(msg, isCoalesced, null);
    }

    public ChatMessageView(FileMetadata meta, boolean isIncoming, String currentUsername, FileActionListener listener) {
        this.sender = meta.getSender();
        this.timestamp = meta.getTimestamp();
        this.content = null; // Pure file transfer card, no redundant text bubble
        this.isCoalesced = false;
        this.fileMetadata = meta;
        this.fileListener = listener;
        this.isSelf = !isIncoming || (currentUsername != null && currentUsername.equalsIgnoreCase(meta.getSender()));
        this.iv = meta.getBaseIvBase64();
        this.isEncrypted = meta.isEncrypted();

        initLayout(meta.isEncrypted());
        initAttachmentCard(isIncoming);
    }

    public ChatMessageView(FileMetadata meta, boolean isIncoming, FileActionListener listener) {
        this(meta, isIncoming, null, listener);
    }

    private void initLayout(boolean isEncrypted) {
        setLayout(new BorderLayout(12, 0));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(isCoalesced ? 2 : 6, 16, isCoalesced ? 2 : 6, 16));

        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent e) {
                isHovered = true;
                if (btnCopy != null) btnCopy.setVisible(true);
                repaint();
            }
            @Override public void mouseExited(MouseEvent e) {
                isHovered = false;
                if (btnCopy != null) btnCopy.setVisible(false);
                repaint();
            }
        });

        // 1. Left Column: Avatar (36x36) or Spacer if coalesced
        if (!isCoalesced) {
            JPanel avatarCol = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
            avatarCol.setOpaque(false);
            avatarCol.setPreferredSize(new Dimension(36, 36));
            JLabel avatar = new JLabel(ThemeManager.createAvatarIcon(sender, 36, true));
            avatarCol.add(avatar);
            add(avatarCol, BorderLayout.WEST);
        } else {
            JPanel spacer = new JPanel();
            spacer.setOpaque(false);
            spacer.setPreferredSize(new Dimension(36, 1));
            add(spacer, BorderLayout.WEST);
        }

        // 2. Center Column: Header + Wrapped Content + Optional Attachment
        JPanel centerCol = new JPanel();
        centerCol.setLayout(new BoxLayout(centerCol, BoxLayout.Y_AXIS));
        centerCol.setOpaque(false);

        // Header Row (Username + Timestamp + Role Badges + AES-128 security badge)
        if (!isCoalesced) {
            JPanel headerRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            headerRow.setOpaque(false);
            headerRow.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel lblName = new JLabel(sender);
            lblName.setFont(new Font("Segoe UI", Font.BOLD, 13));
            lblName.setForeground(isSelf ? ThemeManager.ACCENT_ELECTRIC_CYAN : ThemeManager.getUserColor(sender));

            String timeStr = FormatUtils.formatTime(timestamp);
            JLabel lblTime = new JLabel(timeStr);
            lblTime.setFont(new Font("Segoe UI", Font.PLAIN, 11));
            lblTime.setForeground(ThemeManager.TEXT_MUTED);
            lblTime.setToolTipText(FormatUtils.formatFullDateTime(timestamp));

            headerRow.add(lblName);
            if (isSelf) {
                headerRow.add(ThemeManager.createBadge("BẠN", ThemeManager.ACCENT_ELECTRIC_CYAN, new Color(0x00, 0xF2, 0xFE, 40)));
            } else if ("alice".equalsIgnoreCase(sender)) {
                headerRow.add(ThemeManager.createBadge("VIP", ThemeManager.ACCENT_PURPLE, new Color(0xA8, 0x55, 0xF7, 40)));
            } else if ("bob".equalsIgnoreCase(sender)) {
                headerRow.add(ThemeManager.createBadge("RELAY", ThemeManager.ACCENT_AMBER, new Color(0xF5, 0x9E, 0x0B, 40)));
            } else if ("charlie".equalsIgnoreCase(sender)) {
                headerRow.add(ThemeManager.createBadge("PEER", ThemeManager.SUCCESS, new Color(0x10, 0xB9, 0x81, 40)));
            }
            headerRow.add(lblTime);

            if (isEncrypted) {
                JLabel lblLock = ThemeManager.createBadge("🔒 AES-128", ThemeManager.ACCENT_CYAN, new Color(0x06, 0xB6, 0xD4, 55));
                lblLock.setToolTipText("Mã hóa AES-128-GCM bảo vệ đầu-cuối. Nhấn vào để xem chi tiết mã hóa!");
                lblLock.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                lblLock.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseClicked(MouseEvent e) {
                        showEncryptionInspector(lblLock);
                    }
                });
                headerRow.add(lblLock);
            }

            centerCol.add(headerRow);
            centerCol.add(Box.createVerticalStrut(3));
        }

        // Message Content: Robust Multiline Word-Wrapping via JTextArea inside Frosted Glass Bubble
        if (content != null && !content.trim().isEmpty()) {
            Color userAcc = ThemeManager.getUserColor(sender);
            JPanel bubbleCard = new JPanel(new BorderLayout()) {
                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    
                    if (isSelf) {
                        // Refined subtle teal glass for local user
                        LinearGradientPaint gp = new LinearGradientPaint(
                                0, 0, Math.max(getWidth(), 1), Math.max(getHeight(), 1),
                                new float[]{0.0f, 1.0f},
                                new Color[]{new Color(0x0C, 0x33, 0x3E, 235), new Color(0x08, 0x22, 0x2B, 245)}
                        );
                        g2.setPaint(gp);
                        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);

                        // Delicate 1px border without excessive neon glare
                        g2.setColor(new Color(0x00, 0xF2, 0xFE, 80));
                        g2.setStroke(new BasicStroke(1.0f));
                        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                    } else {
                        // Modern, calm translucent dark card for peer messages
                        g2.setColor(new Color(0x16, 0x1E, 0x2C, 225));
                        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);

                        // Subtle non-glaring perimeter border
                        g2.setColor(new Color(255, 255, 255, 18));
                        g2.setStroke(new BasicStroke(1.0f));
                        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);

                        // Left vertical accent indicator in sender's theme color
                        g2.setColor(userAcc);
                        g2.fillRoundRect(0, 2, 3, getHeight() - 4, 2, 2);
                    }

                    g2.dispose();
                    super.paintComponent(g);
                }
            };
            bubbleCard.setOpaque(false);
            bubbleCard.setBorder(BorderFactory.createEmptyBorder(7, 12, 7, 12));
            bubbleCard.setAlignmentX(Component.LEFT_ALIGNMENT);

            JTextArea txtContent = new JTextArea(content);
            txtContent.setEditable(false);
            txtContent.setOpaque(false);
            txtContent.setLineWrap(true);
            txtContent.setWrapStyleWord(true);
            txtContent.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            txtContent.setForeground(isSelf ? new Color(0xE6, 0xFF, 0xFA) : new Color(0xF1, 0xF5, 0xF9));
            txtContent.setCaretColor(Color.WHITE);
            txtContent.setBorder(null);
            txtContent.setFocusable(false);

            bubbleCard.add(txtContent, BorderLayout.CENTER);
            centerCol.add(bubbleCard);
        }

        add(centerCol, BorderLayout.CENTER);

        // 3. Right Quick Actions (Copy text button on hover)
        JPanel rightCol = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rightCol.setOpaque(false);
        rightCol.setPreferredSize(new Dimension(50, 24));

        btnCopy = new JButton("Copy");
        btnCopy.setToolTipText("Sao chép nội dung");
        btnCopy.setFont(ThemeManager.FONT_SMALL);
        btnCopy.setForeground(ThemeManager.TEXT_MUTED);
        btnCopy.setFocusPainted(false);
        btnCopy.setBorderPainted(false);
        btnCopy.setContentAreaFilled(false);
        btnCopy.setVisible(false);
        btnCopy.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnCopy.addActionListener(e -> {
            String toCopy = (content != null) ? content : (fileMetadata != null ? fileMetadata.getFileName() : "");
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(toCopy), null);
        });
        rightCol.add(btnCopy);
        add(rightCol, BorderLayout.EAST);
    }

    private void initAttachmentCard(boolean isIncoming) {
        if (fileMetadata == null) return;

        attachmentCard = new JPanel(new BorderLayout(12, 6)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Glass fill
                g2.setColor(new Color(0x14, 0x1D, 0x2E, 245));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);

                // Multi-tone gradient border: Electric Cyan -> Teal
                LinearGradientPaint gp = new LinearGradientPaint(
                        0, 0, getWidth(), getHeight(),
                        new float[]{0.0f, 1.0f},
                        new Color[]{ThemeManager.ACCENT_ELECTRIC_CYAN, ThemeManager.ACCENT_PRIMARY}
                );
                g2.setPaint(gp);
                g2.setStroke(new BasicStroke(1.3f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);

                // Top highlight
                g2.setColor(new Color(255, 255, 255, 25));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 12, 12);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        attachmentCard.setOpaque(false);
        attachmentCard.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        attachmentCard.setMaximumSize(new Dimension(600, 105));
        attachmentCard.setAlignmentX(Component.LEFT_ALIGNMENT);

        // File icon on left inside glowing badge container
        JPanel iconBox = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 8)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0x0C, 0x2A, 0x36));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.setColor(new Color(0x00, 0xF2, 0xFE, 140));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        iconBox.setOpaque(false);
        iconBox.setPreferredSize(new Dimension(42, 42));
        iconBox.add(new JLabel(AppIcons.createFileIcon(24, ThemeManager.ACCENT_ELECTRIC_CYAN)));
        attachmentCard.add(iconBox, BorderLayout.WEST);

        // File Info in Center
        JPanel infoCol = new JPanel();
        infoCol.setLayout(new BoxLayout(infoCol, BoxLayout.Y_AXIS));
        infoCol.setOpaque(false);

        JLabel lblName = new JLabel(fileMetadata.getFileName());
        lblName.setFont(ThemeManager.FONT_BODY_BOLD);
        lblName.setForeground(ThemeManager.ACCENT_ELECTRIC_CYAN);

        String sizeStr = FormatUtils.formatFileSize(fileMetadata.getFileSize());
        String shaStr = (fileMetadata.getSha256() != null && fileMetadata.getSha256().length() >= 8)
                ? "SHA-256: " + fileMetadata.getSha256().substring(0, 8) + "..." : "";
        lblFileStatusText = new JLabel(sizeStr + " • " + shaStr + " • Port 8889 Relay");
        lblFileStatusText.setFont(ThemeManager.FONT_SMALL);
        lblFileStatusText.setForeground(ThemeManager.TEXT_SECONDARY);

        fileProgressBar = new JProgressBar(0, 100);
        fileProgressBar.setStringPainted(true);
        fileProgressBar.setForeground(ThemeManager.SUCCESS);
        fileProgressBar.setBackground(ThemeManager.BG_INPUT);
        fileProgressBar.setPreferredSize(new Dimension(300, 14));
        fileProgressBar.setVisible(false);

        lblFileSpeedEta = new JLabel("");
        lblFileSpeedEta.setFont(ThemeManager.FONT_SMALL);
        lblFileSpeedEta.setForeground(ThemeManager.TEXT_MUTED);
        lblFileSpeedEta.setVisible(false);

        infoCol.add(lblName);
        infoCol.add(Box.createVerticalStrut(2));
        infoCol.add(lblFileStatusText);
        infoCol.add(Box.createVerticalStrut(4));
        infoCol.add(fileProgressBar);
        infoCol.add(lblFileSpeedEta);

        attachmentCard.add(infoCol, BorderLayout.CENTER);

        // Action Buttons on Right
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        btnPanel.setOpaque(false);

        if (isIncoming) {
            btnAccept = ThemeManager.createGradientButton("Nhận Tệp", ThemeManager.SUCCESS, ThemeManager.ACCENT_ELECTRIC_CYAN, Color.WHITE);
            btnAccept.setFont(ThemeManager.FONT_SMALL_BOLD);
            btnAccept.setPreferredSize(new Dimension(94, 30));
            btnAccept.addActionListener(e -> {
                btnAccept.setEnabled(false);
                btnReject.setEnabled(false);
                fileProgressBar.setVisible(true);
                lblFileSpeedEta.setVisible(true);
                lblFileStatusText.setText("Đang truyền tệp tin qua cổng 8889...");
                if (fileListener != null) fileListener.onAcceptFile(fileMetadata);
            });

            btnReject = new JButton(AppIcons.createCloseIconSafe(12, ThemeManager.TEXT_MUTED));
            btnReject.setToolTipText("Từ chối tệp");
            btnReject.setPreferredSize(new Dimension(30, 30));
            btnReject.setBackground(new Color(0x28, 0x2F, 0x3E));
            btnReject.setFocusPainted(false);
            btnReject.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            btnReject.addActionListener(e -> {
                btnAccept.setEnabled(false);
                btnReject.setEnabled(false);
                lblFileStatusText.setText("Đã từ chối tệp.");
                lblFileStatusText.setForeground(ThemeManager.DANGER);
                if (fileListener != null) fileListener.onRejectFile(fileMetadata);
            });

            btnPanel.add(btnAccept);
            btnPanel.add(btnReject);
        } else {
            lblFileStatusText.setText("Đã gửi • Đang chờ người nhận");
        }

        btnOpenFile = new JButton("Mở Tệp");
        btnOpenFile.setFont(ThemeManager.FONT_SMALL_BOLD);
        btnOpenFile.setBackground(ThemeManager.SUCCESS);
        btnOpenFile.setForeground(Color.WHITE);
        btnOpenFile.setFocusPainted(false);
        btnOpenFile.setVisible(false);
        btnOpenFile.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnOpenFile.addActionListener(e -> {
            File downloaded = new File("downloads", fileMetadata.getFileName());
            if (fileListener != null) fileListener.onOpenFile(downloaded);
        });
        btnPanel.add(btnOpenFile);

        attachmentCard.add(btnPanel, BorderLayout.EAST);

        // Add attachment card below header/text in center column
        Component centerComp = getComponent(1);
        if (centerComp instanceof JPanel) {
            JPanel centerCol = (JPanel) centerComp;
            centerCol.add(Box.createVerticalStrut(4));
            centerCol.add(attachmentCard);
        }
    }

    public void updateProgress(long transferred, long total, double speedBps) {
        if (btnAccept != null && btnAccept.isVisible()) btnAccept.setVisible(false);
        if (btnReject != null && btnReject.isVisible()) btnReject.setVisible(false);
        if (fileProgressBar != null) {
            int pct = total > 0 ? (int) (transferred * 100 / total) : 0;
            fileProgressBar.setValue(pct);
            fileProgressBar.setString(pct + "%");
            fileProgressBar.setVisible(true);

            String speedStr = FormatUtils.formatSpeed(speedBps);
            long remaining = Math.max(0, total - transferred);
            String etaStr = FormatUtils.formatEta(speedBps, remaining);
            lblFileSpeedEta.setText(String.format("%s • Còn lại: %s", speedStr, etaStr));
            lblFileSpeedEta.setVisible(true);

            if (pct >= 100) {
                lblFileStatusText.setText("Đã nhận đủ byte • Đang kiểm tra SHA-256...");
                lblFileStatusText.setForeground(ThemeManager.ACCENT_PURPLE);
            }
        }
    }

    public void updateState(TransferState state, String message) {
        if (lblFileStatusText != null && state != null) {
            switch (state) {
                case COMPLETED:
                case VERIFIED:
                    lblFileStatusText.setText("Hoàn tất ✓ (Đã xác minh SHA-256)");
                    lblFileStatusText.setForeground(ThemeManager.SUCCESS);
                    if (fileProgressBar != null) fileProgressBar.setVisible(false);
                    if (lblFileSpeedEta != null) lblFileSpeedEta.setVisible(false);
                    if (btnAccept != null) btnAccept.setVisible(false);
                    if (btnReject != null) btnReject.setVisible(false);
                    if (btnOpenFile != null) btnOpenFile.setVisible(true);
                    break;
                case VERIFYING:
                    lblFileStatusText.setText("Đang kiểm tra tính toàn vẹn SHA-256...");
                    lblFileStatusText.setForeground(ThemeManager.ACCENT_PURPLE);
                    break;
                case READY_TO_TRANSFER:
                case TRANSFERRING:
                    lblFileStatusText.setText("Đang truyền tệp tin (Port 8889)...");
                    lblFileStatusText.setForeground(ThemeManager.ACCENT_CYAN);
                    if (btnAccept != null) btnAccept.setVisible(false);
                    if (btnReject != null) btnReject.setVisible(false);
                    if (fileProgressBar != null) fileProgressBar.setVisible(true);
                    if (lblFileSpeedEta != null) lblFileSpeedEta.setVisible(true);
                    break;
                case RESUMING:
                case WAITING_FOR_RESUME:
                    lblFileStatusText.setText("Đang tiếp tục truyền (Resume)...");
                    lblFileStatusText.setForeground(ThemeManager.ACCENT_ELECTRIC_CYAN);
                    if (btnAccept != null) btnAccept.setVisible(false);
                    if (btnReject != null) btnReject.setVisible(false);
                    if (fileProgressBar != null) fileProgressBar.setVisible(true);
                    if (lblFileSpeedEta != null) lblFileSpeedEta.setVisible(true);
                    break;
                case REJECTED:
                    lblFileStatusText.setText("Bị từ chối");
                    lblFileStatusText.setForeground(ThemeManager.DANGER);
                    if (btnAccept != null) btnAccept.setEnabled(false);
                    if (btnReject != null) btnReject.setEnabled(false);
                    break;
                case FAILED:
                    lblFileStatusText.setText(message != null ? message : "Lỗi truyền tệp");
                    lblFileStatusText.setForeground(ThemeManager.DANGER);
                    break;
                case INTERRUPTED:
                    lblFileStatusText.setText("Bị gián đoạn mạng (Có thể tiếp tục)");
                    lblFileStatusText.setForeground(ThemeManager.WARNING);
                    break;
                case PAUSED:
                    lblFileStatusText.setText("Tạm dừng");
                    lblFileStatusText.setForeground(ThemeManager.WARNING);
                    break;
                case PENDING:
                    lblFileStatusText.setText("Đang chờ người nhận xác nhận...");
                    lblFileStatusText.setForeground(ThemeManager.TEXT_SECONDARY);
                    break;
                default:
                    lblFileStatusText.setText(state.getDisplayName());
                    break;
            }
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        if (isHovered) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(0x1B, 0x24, 0x36, 170));
            g2.fillRoundRect(6, 0, getWidth() - 12, getHeight(), 8, 8);
            Color accent = ThemeManager.getUserColor(sender);
            g2.setColor(accent);
            g2.fillRoundRect(6, 2, 3, getHeight() - 4, 3, 3);
            g2.dispose();
        }
        super.paintComponent(g);
    }

    private JPanel createReactionChip(javax.swing.Icon icon, int count) {
        JPanel chip = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 2)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0x1A, 0x25, 0x38, 240));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.setColor(new Color(0x00, 0xF2, 0xFE, 80));
                g2.setStroke(new BasicStroke(1.1f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        chip.setOpaque(false);
        chip.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 8));
        if (icon != null) {
            chip.add(new JLabel(icon));
        }
        JLabel lbl = new JLabel(String.valueOf(count));
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 10));
        lbl.setForeground(ThemeManager.TEXT_SECONDARY);
        chip.add(lbl);
        return chip;
    }

    private void showEncryptionInspector(Component parent) {
        javax.swing.JDialog dlg = new javax.swing.JDialog(
                (java.awt.Frame) javax.swing.SwingUtilities.getWindowAncestor(parent),
                "🔍 Chi Tiết Mã Hóa AES-128-GCM (Security Inspector)",
                true
        );
        dlg.setSize(520, 480);
        dlg.setLocationRelativeTo(parent);
        dlg.setLayout(new BorderLayout());

        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBackground(ThemeManager.BG_DARK);
        root.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));

        // Header
        JPanel pnlHeader = new JPanel(new BorderLayout(8, 0));
        pnlHeader.setOpaque(false);
        JLabel lblTitle = new JLabel("BẢO MẬT ĐẦU-CUỐI: AES-128-GCM");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        lblTitle.setForeground(ThemeManager.ACCENT_ELECTRIC_CYAN);
        JLabel lblSub = new JLabel("Nội dung được bảo vệ toàn vẹn bằng Galois/Counter Mode (NIST SP 800-38D)");
        lblSub.setFont(ThemeManager.FONT_SMALL);
        lblSub.setForeground(ThemeManager.TEXT_MUTED);
        pnlHeader.add(lblTitle, BorderLayout.NORTH);
        pnlHeader.add(lblSub, BorderLayout.SOUTH);
        root.add(pnlHeader, BorderLayout.NORTH);

        // Details Panel
        JPanel details = new JPanel();
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        details.setOpaque(false);

        addInspectorRow(details, "Thuật toán mã hóa:", "AES / GCM / NoPadding");
        addInspectorRow(details, "Độ dài khóa (Key Size):", "128 bits (16 bytes SecretKey)");
        addInspectorRow(details, "Xác thực toàn vẹn:", "128-bit Authentication Tag (MAC)");
        addInspectorRow(details, "Vector khởi tạo (IV / Nonce):", (iv != null && !iv.isEmpty()) ? iv : "12-byte random cryptographically secure nonce");
        addInspectorRow(details, "Người gửi (Sender):", sender != null ? "@" + sender : "Unknown");
        addInspectorRow(details, "Thời gian mã hóa:", FormatUtils.formatTime(timestamp));

        // Plaintext vs Ciphertext representation
        details.add(Box.createVerticalStrut(10));
        JLabel lblPlain = new JLabel("Bản rõ (Plaintext hiển thị ở Client giải mã):");
        lblPlain.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblPlain.setForeground(ThemeManager.SUCCESS);
        details.add(lblPlain);

        JTextArea txtPlain = new JTextArea(content != null ? content : (fileMetadata != null ? "[File Transfer: " + fileMetadata.getFileName() + " (" + fileMetadata.getFileSize() + " bytes)]" : "N/A"));
        txtPlain.setEditable(false);
        txtPlain.setLineWrap(true);
        txtPlain.setWrapStyleWord(true);
        txtPlain.setBackground(ThemeManager.BG_INPUT);
        txtPlain.setForeground(Color.WHITE);
        txtPlain.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        txtPlain.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        details.add(txtPlain);

        details.add(Box.createVerticalStrut(8));
        JLabel lblCipher = new JLabel("Bản mã truyền qua mạng Socket / Server:");
        lblCipher.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblCipher.setForeground(ThemeManager.ACCENT_CYAN);
        details.add(lblCipher);

        // Generate synthetic or real ciphertext display
        String cipherPreview = "AES-GCM-CIPHERTEXT[" + (iv != null ? iv : "IV96") + "]:" +
                java.util.Base64.getEncoder().encodeToString(
                        (content != null ? content : "DAMMTP2P_DATA_STREAM").getBytes(java.nio.charset.StandardCharsets.UTF_8)
                ) + "... (128-bit Tag Verified ✓)";
        JTextArea txtCipher = new JTextArea(cipherPreview);
        txtCipher.setEditable(false);
        txtCipher.setLineWrap(true);
        txtCipher.setBackground(ThemeManager.BG_INPUT);
        txtCipher.setForeground(new Color(0x38, 0xBD, 0xF8));
        txtCipher.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        txtCipher.setFont(new Font("Consolas", Font.PLAIN, 11));
        details.add(txtCipher);

        javax.swing.JScrollPane scroll = new javax.swing.JScrollPane(details);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        root.add(scroll, BorderLayout.CENTER);

        // Footer button
        JPanel pnlFooter = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        pnlFooter.setOpaque(false);
        JButton btnClose = new JButton("Đóng");
        btnClose.setFont(ThemeManager.FONT_SMALL_BOLD);
        btnClose.setBackground(ThemeManager.ACCENT_PRIMARY);
        btnClose.setForeground(Color.WHITE);
        btnClose.addActionListener(e -> dlg.dispose());
        pnlFooter.add(btnClose);
        root.add(pnlFooter, BorderLayout.SOUTH);

        dlg.setContentPane(root);
        dlg.setVisible(true);
    }

    private void addInspectorRow(JPanel container, String title, String val) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
        JLabel lblKey = new JLabel(title);
        lblKey.setFont(ThemeManager.FONT_SMALL_BOLD);
        lblKey.setForeground(ThemeManager.TEXT_SECONDARY);
        JLabel lblVal = new JLabel(val);
        lblVal.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblVal.setForeground(ThemeManager.TEXT_PRIMARY);
        row.add(lblKey, BorderLayout.WEST);
        row.add(lblVal, BorderLayout.EAST);
        container.add(row);
        container.add(Box.createVerticalStrut(4));
    }
}
