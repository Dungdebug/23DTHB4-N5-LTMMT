package dammtp2p.ui.components;

import dammtp2p.core.chat.ChatMessageListener;
import dammtp2p.core.chat.DefaultChatService;
import dammtp2p.core.model.ChatMessage;
import dammtp2p.core.network.DefaultConnectionManager;
import dammtp2p.ui.theme.AppIcons;
import dammtp2p.ui.theme.ThemeManager;
import dammtp2p.util.FormatUtils;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
 * Modern chat panel featuring left/right chat bubbles, Vietnamese Unicode rendering,
 * right-click copy menu, multiline input (Enter=Send, Shift+Enter=Newline), and sleek styling.
 */
public class ChatPanel extends JPanel implements ChatMessageListener {

    private final DefaultConnectionManager connectionManager;
    private final DefaultChatService chatService;

    private JPanel messagesContainer;
    private JScrollPane scrollPane;
    private JTextArea inputArea;
    private JButton btnSend;
    private JLabel lblChatHeader;

    public ChatPanel(DefaultConnectionManager connectionManager, DefaultChatService chatService) {
        this.connectionManager = connectionManager;
        this.chatService = chatService;
        this.chatService.addMessageListener(this);

        initUI();
        updateConnectionState();
    }

    private void initUI() {
        setLayout(new BorderLayout(0, 6));
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(
                        BorderFactory.createLineBorder(ThemeManager.BORDER_COLOR, 1),
                        "Trò Chuyện Ngang Hàng (1-to-1 P2P Chat)",
                        0, 0, ThemeManager.FONT_BODY_BOLD,
                        ThemeManager.isDarkMode() ? ThemeManager.TEXT_PRIMARY : Color.DARK_GRAY
                ),
                BorderFactory.createEmptyBorder(4, 8, 8, 8)
        ));

        // 1. Top Subheader: active peer indicator
        JPanel headerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        headerPanel.setOpaque(false);
        lblChatHeader = new JLabel("Chưa kết nối tới bạn bè");
        lblChatHeader.setFont(ThemeManager.FONT_SMALL);
        lblChatHeader.setForeground(ThemeManager.TEXT_MUTED);
        headerPanel.add(lblChatHeader);
        add(headerPanel, BorderLayout.NORTH);

        // 2. Center: Chat Bubbles Container inside ScrollPane
        messagesContainer = new JPanel();
        messagesContainer.setLayout(new BoxLayout(messagesContainer, BoxLayout.Y_AXIS));
        messagesContainer.setBackground(ThemeManager.isDarkMode() ? ThemeManager.BG_DARK : new Color(248, 250, 252));
        messagesContainer.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        scrollPane = new JScrollPane(messagesContainer);
        scrollPane.setBorder(BorderFactory.createLineBorder(ThemeManager.BORDER_COLOR, 1));
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setPreferredSize(new Dimension(400, 260));
        add(scrollPane, BorderLayout.CENTER);

        // 3. Bottom: Composer panel
        JPanel composerPanel = new JPanel(new BorderLayout(8, 0));
        composerPanel.setOpaque(false);
        composerPanel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));

        inputArea = new JTextArea(2, 25);
        inputArea.setFont(ThemeManager.FONT_BODY);
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(6, 8, 6, 8)
        ));
        inputArea.setBackground(ThemeManager.isDarkMode() ? ThemeManager.BG_INPUT : Color.WHITE);
        inputArea.setForeground(ThemeManager.isDarkMode() ? ThemeManager.TEXT_PRIMARY : Color.BLACK);

        inputArea.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    if (e.isShiftDown()) {
                        // Insert newline on Shift+Enter
                        inputArea.append("\n");
                    } else {
                        // Send message on Enter
                        e.consume();
                        sendCurrentMessage();
                    }
                }
            }
        });

        JScrollPane inputScroll = new JScrollPane(inputArea);
        inputScroll.setBorder(BorderFactory.createEmptyBorder());
        inputScroll.setPreferredSize(new Dimension(300, 48));

        btnSend = new JButton("GỬI (SEND)");
        btnSend.setFont(ThemeManager.FONT_BODY_BOLD);
        btnSend.setBackground(ThemeManager.ACCENT_PRIMARY);
        btnSend.setForeground(Color.WHITE);
        btnSend.setIcon(AppIcons.createSendIcon(14, Color.WHITE));
        btnSend.setFocusPainted(false);
        btnSend.setPreferredSize(new Dimension(120, 48));
        btnSend.addActionListener(e -> sendCurrentMessage());

        composerPanel.add(inputScroll, BorderLayout.CENTER);
        composerPanel.add(btnSend, BorderLayout.EAST);

        add(composerPanel, BorderLayout.SOUTH);
    }

    private void sendCurrentMessage() {
        String text = inputArea.getText().trim();
        if (text.isEmpty() || !connectionManager.isConnected()) {
            return;
        }

        try {
            chatService.sendMessage(text);
            inputArea.setText("");
            inputArea.requestFocusInWindow();
        } catch (Exception ex) {
            appendSystemPill("Lỗi gửi tin: " + ex.getMessage());
        }
    }

    public void updateConnectionState() {
        SwingUtilities.invokeLater(() -> {
            boolean connected = connectionManager.isConnected();
            inputArea.setEnabled(connected);
            btnSend.setEnabled(connected);
            if (connected) {
                dammtp2p.core.model.PeerInfo p = connectionManager.getRemotePeerInfo();
                String peerName = p != null ? p.getDisplayName() + " (" + p.getIpAddress() + ")" : "Đã kết nối";
                lblChatHeader.setText("Đang trò chuyện với: " + peerName);
                lblChatHeader.setForeground(ThemeManager.SUCCESS);
                inputArea.setToolTipText("Nhập tin nhắn (Enter để gửi, Shift+Enter để xuống dòng)...");
            } else {
                lblChatHeader.setText("Chưa kết nối tới bạn bè");
                lblChatHeader.setForeground(ThemeManager.TEXT_MUTED);
                inputArea.setToolTipText("Chưa kết nối tới bạn bè.");
            }
        });
    }

    @Override
    public void onMessageReceived(ChatMessage message) {
        SwingUtilities.invokeLater(() -> {
            addMessageBubble(message, false);
            scrollToBottom();
        });
    }

    @Override
    public void onMessageSent(ChatMessage message) {
        SwingUtilities.invokeLater(() -> {
            addMessageBubble(message, true);
            scrollToBottom();
        });
    }

    private void addMessageBubble(ChatMessage msg, boolean isLocal) {
        JPanel row = new JPanel(new FlowLayout(isLocal ? FlowLayout.RIGHT : FlowLayout.LEFT, 4, 3));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));

        Color bubbleBg = isLocal ? ThemeManager.BUBBLE_LOCAL : ThemeManager.BUBBLE_REMOTE;
        Color textColor = Color.WHITE;
        Color timeColor = isLocal ? new Color(191, 219, 254) : ThemeManager.TEXT_MUTED;

        JPanel bubble = new JPanel(new BorderLayout(4, 2)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bubbleBg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 14, 14);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        bubble.setOpaque(false);
        bubble.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));

        // Sender & Time label on top of bubble
        String timeStr = FormatUtils.formatTime(msg.getTimestamp());
        String senderStr = isLocal ? "Bạn (You)" : msg.getSender();
        JLabel lblHeader = new JLabel(senderStr + " • " + timeStr);
        lblHeader.setFont(ThemeManager.FONT_SMALL);
        lblHeader.setForeground(timeColor);
        bubble.add(lblHeader, BorderLayout.NORTH);

        // Content
        JLabel lblText = new JLabel("<html><body style='width: 240px; word-wrap: break-word;'>"
                + escapeHtml(msg.getContent()) + "</body></html>");
        lblText.setFont(ThemeManager.FONT_BODY);
        lblText.setForeground(textColor);
        bubble.add(lblText, BorderLayout.CENTER);

        // Right click context menu to copy message
        JPopupMenu popup = new JPopupMenu();
        JMenuItem itemCopy = new JMenuItem("Sao chép tin nhắn (Copy Text)");
        itemCopy.addActionListener(e -> {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(msg.getContent()), null);
        });
        popup.add(itemCopy);

        bubble.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) showMenu(e);
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) showMenu(e);
            }
            private void showMenu(MouseEvent e) {
                popup.show(e.getComponent(), e.getX(), e.getY());
            }
        });

        row.add(bubble);
        messagesContainer.add(row);
        messagesContainer.revalidate();
    }

    private void appendSystemPill(String msg) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 4, 3));
        row.setOpaque(false);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));

        JPanel pill = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 4)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(ThemeManager.BUBBLE_SYSTEM);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        pill.setOpaque(false);

        JLabel lbl = new JLabel(msg);
        lbl.setFont(ThemeManager.FONT_SMALL);
        lbl.setForeground(ThemeManager.TEXT_SECONDARY);
        pill.add(lbl);

        row.add(pill);
        messagesContainer.add(row);
        messagesContainer.revalidate();
        scrollToBottom();
    }

    private void scrollToBottom() {
        SwingUtilities.invokeLater(() -> {
            JScrollBar bar = scrollPane.getVerticalScrollBar();
            bar.setValue(bar.getMaximum());
        });
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\n", "<br/>");
    }
}
