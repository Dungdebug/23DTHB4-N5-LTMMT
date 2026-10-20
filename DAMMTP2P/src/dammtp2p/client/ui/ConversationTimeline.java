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
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.Scrollable;
import javax.swing.SwingUtilities;

/**
 * Conversation Timeline Container in DAMMTP2P Mesh Workspace.
 * 
 * Features:
 * - Scrollable container enforcing full viewport width matching to guarantee word wrapping.
 * - Dynamic Welcome/Empty state displayed when 0 messages exist, disappearing on arrival of first message.
 * - Message coalescing (grouping consecutive messages from the same sender within 5 mins).
 * - Deduplication guard preventing double rendering.
 * - Non-disruptive scroll stabilization: preserves reading position when user is reading older messages,
 *   with an interactive "↓ Tin nhắn mới nhất" pill indicator.
 */
public class ConversationTimeline extends JPanel {

    private final String channelName;
    private final boolean isDirectMessage;
    private final ScrollablePanel messagesListPanel;
    private final JScrollPane scrollPane;
    private final JButton btnScrollDown;
    private JPanel welcomePanel;

    private String lastSender = null;
    private long lastMessageTime = 0;
    private static final long COALESCE_THRESHOLD_MS = 5 * 60 * 1000; // 5 minutes

    private int messageCount = 0;
    private final Set<String> processedMessageIds = new HashSet<>();
    private final Map<String, ChatMessageView> fileTransferItems = new HashMap<>();

    public ConversationTimeline(String channelName, boolean isDirectMessage) {
        this.channelName = channelName;
        this.isDirectMessage = isDirectMessage;

        setLayout(new BorderLayout());
        setOpaque(false);

        messagesListPanel = new ScrollablePanel();
        messagesListPanel.setLayout(new BoxLayout(messagesListPanel, BoxLayout.Y_AXIS));
        messagesListPanel.setOpaque(false);
        messagesListPanel.setBorder(BorderFactory.createEmptyBorder(6, 0, 24, 0));

        // Add Welcome / Empty Banner
        initWelcomeBanner();

        scrollPane = new JScrollPane(messagesListPanel);
        scrollPane.setBorder(null);
        scrollPane.setOpaque(false);
        scrollPane.getViewport().setOpaque(false);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        // Floating bottom bar for "Scroll to bottom / New messages" pill
        btnScrollDown = new JButton("↓ Tin nhắn mới nhất");
        btnScrollDown.setFont(ThemeManager.FONT_SMALL_BOLD);
        btnScrollDown.setBackground(ThemeManager.ACCENT_PRIMARY);
        btnScrollDown.setForeground(Color.WHITE);
        btnScrollDown.setFocusPainted(false);
        btnScrollDown.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnScrollDown.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.ACCENT_CYAN, 1),
                BorderFactory.createEmptyBorder(4, 14, 4, 14)
        ));
        btnScrollDown.setVisible(false);
        btnScrollDown.addActionListener(e -> {
            scrollToBottom();
            btnScrollDown.setVisible(false);
        });

        // Scroll listener to hide "new messages" button when user reaches bottom
        scrollPane.getVerticalScrollBar().addAdjustmentListener(e -> {
            if (isAtBottom()) {
                btnScrollDown.setVisible(false);
            }
        });

        JPanel bottomOverlay = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 4));
        bottomOverlay.setOpaque(false);
        bottomOverlay.add(btnScrollDown);

        JPanel centerWrapper = new JPanel(new BorderLayout());
        centerWrapper.setOpaque(false);
        centerWrapper.add(scrollPane, BorderLayout.CENTER);
        centerWrapper.add(bottomOverlay, BorderLayout.SOUTH);

        add(centerWrapper, BorderLayout.CENTER);
    }

    private void initWelcomeBanner() {
        welcomePanel = new JPanel();
        welcomePanel.setLayout(new BoxLayout(welcomePanel, BoxLayout.Y_AXIS));
        welcomePanel.setOpaque(false);
        welcomePanel.setBorder(BorderFactory.createEmptyBorder(10, 16, 8, 16));

        // High-end Cyber Glassmorphic Hero Banner Card
        JPanel heroCard = new JPanel(new BorderLayout(14, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                
                // Deep glass background
                g2.setColor(new Color(0x13, 0x1B, 0x2A, 245));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);

                // Multi-tone holographic border: Electric Cyan -> Signature Teal -> Violet
                LinearGradientPaint borderGrad = new LinearGradientPaint(
                        0, 0, getWidth(), getHeight(),
                        new float[]{0.0f, 0.5f, 1.0f},
                        new Color[]{ThemeManager.ACCENT_ELECTRIC_CYAN, ThemeManager.ACCENT_PRIMARY, ThemeManager.ACCENT_VIOLET}
                );
                g2.setPaint(borderGrad);
                g2.setStroke(new BasicStroke(1.3f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 14, 14);

                // Subtle inner top highlight
                g2.setColor(new Color(255, 255, 255, 25));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 12, 12);

                g2.dispose();
                super.paintComponent(g);
            }
        };
        heroCard.setOpaque(false);
        heroCard.setBorder(BorderFactory.createEmptyBorder(8, 14, 8, 14));
        heroCard.setMaximumSize(new Dimension(860, 96));
        heroCard.setAlignmentX(Component.CENTER_ALIGNMENT);

        // Left Emblem: 44px Holographic Mesh Icon
        JPanel emblemPill = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // Glowing outer halo
                java.awt.RadialGradientPaint halo = new java.awt.RadialGradientPaint(
                        22, 22, 22,
                        new float[]{0.0f, 0.7f, 1.0f},
                        new Color[]{new Color(0x00, 0xF2, 0xFE, 80), new Color(0x0D, 0x94, 0x88, 30), new Color(0, 0, 0, 0)}
                );
                g2.setPaint(halo);
                g2.fillOval(0, 0, 44, 44);

                // Orbit ring
                g2.setColor(new Color(0x00, 0xF2, 0xFE, 160));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawOval(3, 3, 38, 38);

                // Inner core
                g2.setColor(new Color(0x0A, 0x14, 0x22));
                g2.fillOval(7, 7, 30, 30);

                g2.dispose();
                super.paintComponent(g);
            }
        };
        emblemPill.setOpaque(false);
        emblemPill.setPreferredSize(new Dimension(44, 44));
        emblemPill.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 8));
        JLabel iconLbl = new JLabel(isDirectMessage
                ? AppIcons.createChatBubbleIcon(24, ThemeManager.ACCENT_ELECTRIC_CYAN)
                : AppIcons.createMeshLogo(24, ThemeManager.ACCENT_PRIMARY, ThemeManager.ACCENT_ELECTRIC_CYAN));
        emblemPill.add(iconLbl);

        // Center Content: Title, Badges, Tagline, Telemetry Pills
        JPanel contentCol = new JPanel();
        contentCol.setLayout(new BoxLayout(contentCol, BoxLayout.Y_AXIS));
        contentCol.setOpaque(false);

        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        titleRow.setOpaque(false);
        titleRow.setAlignmentX(0.0f);

        JLabel lblTitle = new JLabel(isDirectMessage
                ? ("Bắt đầu hội thoại trực tiếp với @" + channelName)
                : ("Chào mừng bạn đến với #" + channelName));
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 16));
        lblTitle.setForeground(ThemeManager.TEXT_PRIMARY);

        titleRow.add(lblTitle);
        if (!isDirectMessage) {
            titleRow.add(ThemeManager.createBadge("KÊNH CHÍNH MESH", ThemeManager.ACCENT_ELECTRIC_CYAN, new Color(0x00, 0xF2, 0xFE, 40)));
            titleRow.add(ThemeManager.createBadge("● LIVE", ThemeManager.SUCCESS, new Color(0x10, 0xB9, 0x81, 40)));
        }
        contentCol.add(titleRow);
        contentCol.add(Box.createVerticalStrut(2));

        JLabel lblSub = new JLabel(isDirectMessage
                ? "Cuộc hội thoại bảo mật 1–1 qua mạng riêng ZeroTier / LAN. Khóa mã hóa thỏa thuận qua Diffie-Hellman."
                : "Không gian thảo luận chính của DAMMTP2P Mesh Network. Bảo mật đầu-cuối AES-128-GCM với IV ngẫu nhiên.");
        lblSub.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        lblSub.setForeground(ThemeManager.TEXT_SECONDARY);
        lblSub.setAlignmentX(0.0f);
        contentCol.add(lblSub);
        contentCol.add(Box.createVerticalStrut(6));

        // 3 High-tech feature badges
        JPanel pillsRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        pillsRow.setOpaque(false);
        pillsRow.setAlignmentX(0.0f);

        pillsRow.add(ThemeManager.createBadge("● ZeroTier P2P LAN", ThemeManager.ACCENT_ELECTRIC_CYAN, new Color(0x00, 0xF2, 0xFE, 30)));
        pillsRow.add(ThemeManager.createBadge("● AES-128-GCM E2EE", ThemeManager.ACCENT_VIOLET, new Color(0x63, 0x66, 0xF1, 30)));
        pillsRow.add(ThemeManager.createBadge("● Relay Tệp 20 GiB", ThemeManager.ACCENT_AMBER, new Color(0xF5, 0x9E, 0x0B, 30)));
        contentCol.add(pillsRow);

        heroCard.add(emblemPill, BorderLayout.WEST);
        heroCard.add(contentCol, BorderLayout.CENTER);
        welcomePanel.add(heroCard);
        welcomePanel.add(Box.createVerticalStrut(8));

        // Date separator row with gradient line
        JPanel dateRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                int midY = getHeight() / 2;
                g2.setColor(new Color(255, 255, 255, 25));
                g2.drawLine(32, midY, getWidth() - 32, midY);
                g2.dispose();
            }
        };
        dateRow.setOpaque(false);
        JLabel dateBadge = ThemeManager.createBadge(FormatUtils.formatTodayBanner(), ThemeManager.ACCENT_ELECTRIC_CYAN, new Color(0x13, 0x1C, 0x2E, 245));
        dateBadge.setFont(ThemeManager.FONT_SMALL_BOLD);
        dateRow.add(dateBadge);
        welcomePanel.add(dateRow);
        welcomePanel.add(Box.createVerticalStrut(4));

        messagesListPanel.add(welcomePanel);
    }

    public synchronized void addChatMessage(ChatMessage msg, boolean isLocal) {
        addChatMessage(msg, isLocal, null);
    }

    public synchronized void addChatMessage(ChatMessage msg, boolean isLocal, String currentUsername) {
        if (msg.getMessageId() != null && !msg.getMessageId().isEmpty()) {
            if (!processedMessageIds.add(msg.getMessageId())) {
                return; // Ignore duplicate
            }
        }

        boolean canCoalesce = false;
        if (lastSender != null && lastSender.equalsIgnoreCase(msg.getSender())) {
            long diff = Math.abs(msg.getTimestamp() - lastMessageTime);
            if (diff < COALESCE_THRESHOLD_MS) {
                canCoalesce = true;
            }
        }

        ChatMessageView item = new ChatMessageView(msg, canCoalesce, currentUsername);
        messagesListPanel.add(item);
        messageCount++;

        lastSender = msg.getSender();
        lastMessageTime = msg.getTimestamp();

        boolean wasAtBottom = isAtBottom();
        messagesListPanel.revalidate();

        if (wasAtBottom || isLocal) {
            scrollToBottom();
        } else {
            btnScrollDown.setVisible(true);
        }
    }

    public synchronized void addFileTransfer(FileMetadata meta, boolean isIncoming, ChatMessageView.FileActionListener listener) {
        addFileTransfer(meta, isIncoming, null, listener);
    }

    public synchronized void addFileTransfer(FileMetadata meta, boolean isIncoming, String currentUsername, ChatMessageView.FileActionListener listener) {
        if (meta.getTransferId() != null) {
            if (!processedMessageIds.add(meta.getTransferId())) {
                return; // Ignore duplicate
            }
        }

        ChatMessageView item = new ChatMessageView(meta, isIncoming, currentUsername, listener);
        messagesListPanel.add(item);
        messageCount++;

        if (meta.getTransferId() != null) {
            fileTransferItems.put(meta.getTransferId(), item);
        }

        lastSender = null; // Do not coalesce after an attachment

        boolean wasAtBottom = isAtBottom();
        messagesListPanel.revalidate();

        if (wasAtBottom) {
            scrollToBottom();
        } else {
            btnScrollDown.setVisible(true);
        }
    }

    public synchronized void updateFileProgress(String transferId, long transferred, long total, double speed) {
        ChatMessageView item = fileTransferItems.get(transferId);
        if (item != null) {
            item.updateProgress(transferred, total, speed);
        }
    }

    public synchronized void updateFileState(String transferId, TransferState state, String message) {
        ChatMessageView item = fileTransferItems.get(transferId);
        if (item != null) {
            item.updateState(state, message);
        }
    }

    public synchronized void clearTimeline() {
        messagesListPanel.removeAll();
        fileTransferItems.clear();
        processedMessageIds.clear();
        lastSender = null;
        messageCount = 0;
        btnScrollDown.setVisible(false);

        if (welcomePanel != null) {
            welcomePanel.setVisible(true);
            messagesListPanel.add(welcomePanel);
        }

        messagesListPanel.revalidate();
        messagesListPanel.repaint();
    }

    private boolean isAtBottom() {
        JScrollBar vertical = scrollPane.getVerticalScrollBar();
        int val = vertical.getValue();
        int extent = vertical.getModel().getExtent();
        int max = vertical.getMaximum();
        return (val + extent >= max - 48);
    }

    public void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            messagesListPanel.revalidate();
            SwingUtilities.invokeLater(this::scrollToBottomDirectly);
        });
    }

    public void scrollToBottomDirectly() {
        JScrollBar vertical = scrollPane.getVerticalScrollBar();
        if (vertical != null) {
            vertical.setValue(vertical.getMaximum());
        }
    }

    public JScrollPane getScrollPane() {
        return scrollPane;
    }

    private static class ScrollablePanel extends JPanel implements Scrollable {
        public ScrollablePanel() {
            super();
        }

        @Override
        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        @Override
        public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 16;
        }

        @Override
        public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
            return 64;
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public boolean getScrollableTracksViewportHeight() {
            return false;
        }
    }
}
