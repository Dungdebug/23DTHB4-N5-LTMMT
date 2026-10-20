package dammtp2p.client.ui;

import dammtp2p.ui.theme.AppIcons;
import dammtp2p.ui.theme.ThemeManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

/**
 * Contextual Participant List Drawer (Right Panel) in DAMMTP2P Mesh Workspace.
 * 
 * Features:
 * - Can be toggled open/closed on demand to give full width to chat.
 * - Accurate connected presence count (TRỰC TUYẾN — N).
 * - Highlights local user as "(Bạn)" with Cyan styling.
 * - Green status dot for online members, real-time speaking voice rings.
 * - Double-click / context menu to start 1-1 Direct Message.
 */
public class ParticipantListPanel extends JPanel {

    public interface MemberActionListener {
        void onOpenDirectMessage(String username);
        default void onCloseDrawerRequested() {}
        default void onAddFriendRequested(String username) {}
        default void onRemoveFriendRequested(String username) {}
    }

    private final MemberActionListener listener;
    private final DefaultListModel<String> onlineModel;
    private final JList<String> onlineList;
    private final JLabel lblOnlineHeader;
    private final JLabel lblEmptyHint;
    private final JButton btnCloseDrawer;
    private final Set<String> speakingUsers = new HashSet<>();
    private volatile String currentUsername = null;

    public ParticipantListPanel(MemberActionListener listener) {
        this.listener = listener;
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(220, Integer.MAX_VALUE));
        setBackground(ThemeManager.BG_SIDEBAR);
        setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, ThemeManager.BORDER_SUBTLE));

        // Drawer Header with Title & Close button
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setOpaque(false);
        headerPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ThemeManager.BORDER_SUBTLE),
                BorderFactory.createEmptyBorder(12, 14, 12, 10)
        ));

        lblOnlineHeader = new JLabel("TRỰC TUYẾN — 0");
        lblOnlineHeader.setFont(new Font("Segoe UI", Font.BOLD, 11));
        lblOnlineHeader.setForeground(ThemeManager.TEXT_MUTED);

        btnCloseDrawer = new JButton(AppIcons.createCloseIconSafe(12, ThemeManager.TEXT_MUTED));
        btnCloseDrawer.setPreferredSize(new Dimension(24, 24));
        btnCloseDrawer.setFocusPainted(false);
        btnCloseDrawer.setBorderPainted(false);
        btnCloseDrawer.setContentAreaFilled(false);
        btnCloseDrawer.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnCloseDrawer.setToolTipText("Đóng danh sách thành viên");
        btnCloseDrawer.addActionListener(e -> {
            setVisible(false);
            if (listener != null) listener.onCloseDrawerRequested();
        });

        headerPanel.add(lblOnlineHeader, BorderLayout.CENTER);
        headerPanel.add(btnCloseDrawer, BorderLayout.EAST);
        add(headerPanel, BorderLayout.NORTH);

        // Member list container
        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setOpaque(false);
        container.setBorder(BorderFactory.createEmptyBorder(8, 6, 8, 6));

        onlineModel = new DefaultListModel<>();
        onlineList = new JList<>(onlineModel);
        onlineList.setBackground(ThemeManager.BG_SIDEBAR);
        onlineList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        onlineList.setCellRenderer(new MemberRenderer());
        onlineList.setOpaque(false);
        onlineList.setFixedCellHeight(46);
        onlineList.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        onlineList.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && onlineList.getSelectedValue() != null) {
                    String selected = onlineList.getSelectedValue();
                    if (currentUsername == null || !selected.equalsIgnoreCase(currentUsername)) {
                        if (listener != null) listener.onOpenDirectMessage(selected);
                    }
                }
            }
            @Override
            public void mousePressed(MouseEvent e) { checkPopup(e); }
            @Override
            public void mouseReleased(MouseEvent e) { checkPopup(e); }

            private void checkPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int idx = onlineList.locationToIndex(e.getPoint());
                    if (idx >= 0) {
                        onlineList.setSelectedIndex(idx);
                        String target = onlineList.getSelectedValue();
                        if (target != null && (currentUsername == null || !target.equalsIgnoreCase(currentUsername))) {
                            JPopupMenu menu = new JPopupMenu();
                            JMenuItem item = new JMenuItem("💬 Nhắn tin với @" + target);
                            item.addActionListener(ev -> {
                                if (listener != null) listener.onOpenDirectMessage(target);
                            });
                            menu.add(item);

                            boolean isFriend = dammtp2p.client.FriendsManager.getInstance().isFriend(currentUsername, target);
                            if (!isFriend) {
                                JMenuItem itemAdd = new JMenuItem("➕ Kết bạn với @" + target);
                                itemAdd.addActionListener(ev -> {
                                    if (listener != null) listener.onAddFriendRequested(target);
                                });
                                menu.add(itemAdd);
                            } else {
                                JMenuItem itemRemove = new JMenuItem("➖ Hủy kết bạn với @" + target);
                                itemRemove.addActionListener(ev -> {
                                    if (listener != null) listener.onRemoveFriendRequested(target);
                                });
                                menu.add(itemRemove);
                            }

                            menu.show(e.getComponent(), e.getX(), e.getY());
                        }
                    }
                }
            }
        });

        lblEmptyHint = new JLabel("Chưa có ai trực tuyến");
        lblEmptyHint.setFont(ThemeManager.FONT_SMALL);
        lblEmptyHint.setForeground(new Color(0x64, 0x74, 0x8B));
        lblEmptyHint.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
        lblEmptyHint.setVisible(true);

        container.add(onlineList);
        container.add(lblEmptyHint);

        JScrollPane scroll = new JScrollPane(container);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        add(scroll, BorderLayout.CENTER);
    }

    public void updateMembers(List<String> users, String currentUser) {
        if (currentUser != null && !currentUser.trim().isEmpty()) {
            this.currentUsername = currentUser.trim();
        }
        onlineModel.clear();
        int count = 0;
        if (users != null) {
            // First add current user if present in online roster
            for (String u : users) {
                if (currentUsername != null && u.equalsIgnoreCase(currentUsername)) {
                    onlineModel.addElement(u);
                    count++;
                    break;
                }
            }
            // Then add all other connected peers
            for (String u : users) {
                if (currentUsername == null || !u.equalsIgnoreCase(currentUsername)) {
                    onlineModel.addElement(u);
                    count++;
                }
            }
        }
        lblOnlineHeader.setText("TRỰC TUYẾN — " + count);
        lblEmptyHint.setVisible(count == 0);
    }

    public void updateMembers(List<String> users) {
        updateMembers(users, this.currentUsername);
    }

    public void setSpeakingUsers(Set<String> speakers) {
        speakingUsers.clear();
        if (speakers != null) {
            speakingUsers.addAll(speakers);
        }
        onlineList.repaint();
    }

    private class MemberRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JPanel p = new JPanel(new BorderLayout(8, 0)) {
                @Override
                protected void paintComponent(Graphics g) {
                    if (isSelected) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setColor(ThemeManager.BG_ACTIVE);
                        g2.fillRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 10, 10);
                        g2.setColor(new Color(ThemeManager.ACCENT_PRIMARY.getRed(), ThemeManager.ACCENT_PRIMARY.getGreen(), ThemeManager.ACCENT_PRIMARY.getBlue(), 60));
                        g2.drawRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 10, 10);
                        g2.dispose();
                    }
                    super.paintComponent(g);
                }
            };
            p.setOpaque(false);
            p.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

            String username = value.toString();
            boolean isSelf = (currentUsername != null && username.equalsIgnoreCase(currentUsername));
            boolean isSpeaking = speakingUsers.contains(username.toLowerCase());
            JLabel avatar = new JLabel(ThemeManager.createAvatarIcon(username, 30, true, isSpeaking));

            JPanel infoCol = new JPanel();
            infoCol.setLayout(new BoxLayout(infoCol, BoxLayout.Y_AXIS));
            infoCol.setOpaque(false);

            JPanel topRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            topRow.setOpaque(false);
            topRow.setAlignmentX(0.0f);

            JLabel name = new JLabel(username);
            name.setFont(isSelf ? ThemeManager.FONT_BODY_BOLD : (isSelected ? ThemeManager.FONT_BODY_BOLD : ThemeManager.FONT_BODY));
            name.setForeground(isSpeaking ? ThemeManager.SUCCESS : (isSelf ? ThemeManager.ACCENT_ELECTRIC_CYAN : ThemeManager.getUserColor(username)));
            topRow.add(name);

            if (isSelf) {
                topRow.add(ThemeManager.createBadge("BẠN", ThemeManager.ACCENT_ELECTRIC_CYAN, new Color(0x00, 0xF2, 0xFE, 40)));
                topRow.add(ThemeManager.createBadge("HOST", ThemeManager.ACCENT_AMBER, new Color(0xF5, 0x9E, 0x0B, 40)));
            } else {
                boolean isFriend = dammtp2p.client.FriendsManager.getInstance().isFriend(currentUsername, username);
                if (isFriend) {
                    topRow.add(ThemeManager.createBadge("BẠN BÈ", ThemeManager.ACCENT_PRIMARY, new Color(0x63, 0x66, 0xF1, 40)));
                }
                if ("alice".equalsIgnoreCase(username)) {
                    topRow.add(ThemeManager.createBadge("VIP", ThemeManager.ACCENT_PURPLE, new Color(0xA8, 0x55, 0xF7, 40)));
                } else if ("bob".equalsIgnoreCase(username)) {
                    topRow.add(ThemeManager.createBadge("RELAY", ThemeManager.ACCENT_AMBER, new Color(0xF5, 0x9E, 0x0B, 40)));
                } else if ("charlie".equalsIgnoreCase(username)) {
                    topRow.add(ThemeManager.createBadge("PEER", ThemeManager.SUCCESS, new Color(0x10, 0xB9, 0x81, 40)));
                } else if ("admin".equalsIgnoreCase(username)) {
                    topRow.add(ThemeManager.createBadge("ADMIN", ThemeManager.ACCENT_AMBER, new Color(0xF5, 0x9E, 0x0B, 40)));
                }
            }
            if (isSpeaking) {
                topRow.add(ThemeManager.createBadge("VOICE", ThemeManager.SUCCESS, new Color(0x10, 0xB9, 0x81, 40)));
            }

            JLabel lblSubtitle = new JLabel();
            lblSubtitle.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            lblSubtitle.setAlignmentX(0.0f);
            if (isSpeaking) {
                lblSubtitle.setText("🔊 Đang phát biểu...");
                lblSubtitle.setForeground(ThemeManager.SUCCESS);
            } else if (isSelf) {
                lblSubtitle.setText("● ZeroTier Hub • Sẵn sàng");
                lblSubtitle.setForeground(ThemeManager.ACCENT_CYAN);
            } else if ("alice".equalsIgnoreCase(username)) {
                lblSubtitle.setText("● Đang đọc tài liệu đồ án");
                lblSubtitle.setForeground(ThemeManager.TEXT_MUTED);
            } else if ("bob".equalsIgnoreCase(username)) {
                lblSubtitle.setText("● Đang truyền tệp 20 GiB");
                lblSubtitle.setForeground(ThemeManager.TEXT_MUTED);
            } else if ("charlie".equalsIgnoreCase(username)) {
                lblSubtitle.setText("● Sẵn sàng thảo luận");
                lblSubtitle.setForeground(ThemeManager.TEXT_MUTED);
            } else {
                lblSubtitle.setText("● Trực tuyến");
                lblSubtitle.setForeground(ThemeManager.TEXT_MUTED);
            }

            infoCol.add(topRow);
            infoCol.add(Box.createVerticalStrut(2));
            infoCol.add(lblSubtitle);

            p.add(avatar, BorderLayout.WEST);
            p.add(infoCol, BorderLayout.CENTER);
            return p;
        }
    }
}
