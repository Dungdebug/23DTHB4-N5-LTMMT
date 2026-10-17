package dammtp2p.client.ui;

import dammtp2p.ui.theme.AppIcons;
import dammtp2p.ui.theme.ThemeManager;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

/**
 * Main Workspace Navigation & Conversation Sidebar in DAMMTP2P Mesh Workspace.
 * 
 * Features:
 * - Segmented Navigation Tabs: "Hội thoại" (1-1 DMs), "Nhóm" (Rooms), "Phòng thoại" (Voice RTC), "Truyền tệp".
 * - Category-specific views with quick action buttons (+ Tạo nhóm, + Nhắn tin mới).
 * - Real-time Voice RTC active connection dock with mute/deafen and disconnect controls.
 * - Profile and audio controller footer.
 */
public class ConversationSidebar extends JPanel {

    public interface SidebarListener {
        void onRoomSelected(String roomName);
        void onVoiceRoomSelected(String roomName);
        void onUserSelected(String username);
        void onCreateRoomRequested();
        void onVoiceLeaveRequested();
        void onMicToggled();
        void onDeafenToggled();
        void onVolumeChanged(double volume);
        default void onSettingsRequested() {}
        default void onTransfersRequested() {}
        default void onLogoutRequested() {}
        default void onFriendAddRequested(String username) {}
        default void onFriendRemoveRequested(String username) {}
    }

    private final SidebarListener listener;

    // Segmented Navigation
    private JButton btnTabDms;
    private JButton btnTabRooms;
    private JButton btnTabVoice;
    private CardLayout contentCardLayout;
    private JPanel contentCards;
    private static final String TAB_DMS = "TAB_DMS";
    private static final String TAB_ROOMS = "TAB_ROOMS";
    private static final String TAB_VOICE = "TAB_VOICE";

    // Models & Lists
    private final DefaultListModel<String> textChannelsModel = new DefaultListModel<>();
    private JList<String> textChannelsList;

    private final DefaultListModel<String> voiceChannelsModel = new DefaultListModel<>();
    private JList<String> voiceChannelsList;

    private final DefaultListModel<String> directMessagesModel = new DefaultListModel<>();
    private JList<String> directMessagesList;
    private JLabel lblEmptyDmsHint;

    // Friends lists models & components
    private final DefaultListModel<String> friendsOnlineModel = new DefaultListModel<>();
    private JList<String> friendsOnlineList;
    private final DefaultListModel<String> friendsOfflineModel = new DefaultListModel<>();
    private JList<String> friendsOfflineList;
    private final DefaultListModel<String> otherOnlineModel = new DefaultListModel<>();
    private JList<String> otherOnlineList;

    private JLabel lblFriendsOnlineHeader;
    private JLabel lblFriendsOfflineHeader;
    private JLabel lblOtherOnlineHeader;

    // Voice participants & speaking tracker
    private final Map<String, List<String>> voiceParticipants = new HashMap<>();
    private final Set<String> speakingUsers = new HashSet<>();

    // Voice RTC Dock
    private JPanel voiceDockPanel;
    private JLabel lblVoiceRoomName;
    private JLabel lblVoiceRtcStatus;
    private JButton btnDisconnectVoice;

    // User Profile Bar
    private JLabel lblUserAvatar;
    private JLabel lblUsername;
    private JButton btnMic;
    private JButton btnDeafen;
    private JButton btnLogout;

    private String currentUsername = "User";
    private boolean isVoiceConnected = false;
    private List<String> cachedOnlineUsers = new ArrayList<>();
    private boolean isMicMuted = false;
    private boolean isDeafened = false;
    private double currentVolume = 1.0;

    public ConversationSidebar(SidebarListener listener) {
        this.listener = listener;
        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(280, Integer.MAX_VALUE));
        setBackground(ThemeManager.BG_SIDEBAR);
        setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, ThemeManager.BORDER_SUBTLE));

        // 1. Top Segmented Navigation Tabs (Hội thoại, Nhóm, Thoại, Tệp)
        JPanel topNav = createSegmentedNav();
        add(topNav, BorderLayout.NORTH);

        // 2. Center Content Cards
        contentCardLayout = new CardLayout();
        contentCards = new JPanel(contentCardLayout);
        contentCards.setOpaque(false);

        contentCards.add(createDmsPanel(), TAB_DMS);
        contentCards.add(createRoomsPanel(), TAB_ROOMS);
        contentCards.add(createVoicePanel(), TAB_VOICE);

        add(contentCards, BorderLayout.CENTER);

        // 3. Bottom Panel (Voice dock + User profile footer)
        JPanel bottom = createBottomPanel();
        add(bottom, BorderLayout.SOUTH);

        // Default tab: Rooms (Nhóm)
        switchTab(TAB_ROOMS);
    }

    private JPanel createSegmentedNav() {
        JPanel navWrapper = new JPanel(new BorderLayout());
        navWrapper.setOpaque(false);
        navWrapper.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ThemeManager.BORDER_SUBTLE),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        JPanel segGrid = new JPanel(new GridLayout(1, 3, 6, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(ThemeManager.BG_NAV);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();
            }
        };
        segGrid.setOpaque(false);
        segGrid.setPreferredSize(new Dimension(240, 32));

        btnTabRooms = createNavTab("Nhóm", true);
        btnTabDms = createNavTab("Bạn bè", false);
        btnTabVoice = createNavTab("Thoại", false);

        btnTabRooms.addActionListener(e -> switchTab(TAB_ROOMS));
        btnTabDms.addActionListener(e -> switchTab(TAB_DMS));
        btnTabVoice.addActionListener(e -> switchTab(TAB_VOICE));

        segGrid.add(btnTabRooms);
        segGrid.add(btnTabDms);
        segGrid.add(btnTabVoice);

        navWrapper.add(segGrid, BorderLayout.CENTER);
        return navWrapper;
    }

    private JButton createNavTab(String title, boolean active) {
        JButton btn = new JButton(title) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean isActive = Boolean.TRUE.equals(getClientProperty("tab.active"));
                boolean hover = getModel().isRollover();

                if (isActive) {
                    LinearGradientPaint gp = new LinearGradientPaint(
                            0, 0, getWidth(), getHeight(),
                            new float[]{0.0f, 1.0f},
                            new Color[]{ThemeManager.ACCENT_PRIMARY, ThemeManager.ACCENT_CYAN}
                    );
                    g2.setPaint(gp);
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                    g2.setColor(new Color(255, 255, 255, 60));
                    g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
                } else if (hover) {
                    g2.setColor(new Color(255, 255, 255, 20));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("Segoe UI", Font.BOLD, 11));
        btn.setFocusPainted(false);
        btn.setBorder(null);
        btn.setContentAreaFilled(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        updateTabStyle(btn, active);
        return btn;
    }

    private void updateTabStyle(JButton btn, boolean active) {
        btn.putClientProperty("tab.active", active);
        btn.setForeground(active ? Color.WHITE : ThemeManager.TEXT_MUTED);
        btn.repaint();
    }

    public void switchTab(String tabId) {
        contentCardLayout.show(contentCards, tabId);
        updateTabStyle(btnTabRooms, TAB_ROOMS.equals(tabId));
        updateTabStyle(btnTabDms, TAB_DMS.equals(tabId));
        updateTabStyle(btnTabVoice, TAB_VOICE.equals(tabId));
    }

    private JPanel createRoomsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        JPanel container = new JPanel();
        container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
        container.setOpaque(false);

        // Top: Workspace Overview Card
        JPanel wsCard = new JPanel(new BorderLayout(8, 0)) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0x13, 0x1A, 0x28, 240));
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.setColor(new Color(0x00, 0xF2, 0xFE, 60));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 12, 12);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        wsCard.setOpaque(false);
        wsCard.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        wsCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));

        JLabel wsIcon = new JLabel(AppIcons.createNetworkIconSafe(16, ThemeManager.ACCENT_ELECTRIC_CYAN));
        JPanel wsInfo = new JPanel(new GridLayout(2, 1, 0, 1));
        wsInfo.setOpaque(false);
        JLabel wsTitle = new JLabel("DAMMTP2P LAN MESH");
        wsTitle.setFont(new Font("Segoe UI", Font.BOLD, 11));
        wsTitle.setForeground(ThemeManager.TEXT_PRIMARY);
        JLabel wsSub = new JLabel("● 4 Peers Trực Tuyến • 10.250.x.x");
        wsSub.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        wsSub.setForeground(ThemeManager.ACCENT_TEAL_LIGHT);
        wsInfo.add(wsTitle);
        wsInfo.add(wsSub);

        wsCard.add(wsIcon, BorderLayout.WEST);
        wsCard.add(wsInfo, BorderLayout.CENTER);
        container.add(wsCard);
        container.add(Box.createVerticalStrut(10));

        // --- Category 1: Text Channels ---
        JPanel headerText = new JPanel(new BorderLayout());
        headerText.setOpaque(false);
        headerText.setBorder(BorderFactory.createEmptyBorder(0, 4, 6, 4));
        headerText.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

        JLabel titleText = new JLabel("CÁC KÊNH VĂN BẢN");
        titleText.setFont(new Font("Segoe UI", Font.BOLD, 10));
        titleText.setForeground(ThemeManager.TEXT_MUTED);

        JButton btnAdd = new JButton("+ Tạo Nhóm");
        btnAdd.setFont(new Font("Segoe UI", Font.BOLD, 10));
        btnAdd.setForeground(ThemeManager.ACCENT_CYAN);
        btnAdd.setContentAreaFilled(false);
        btnAdd.setBorderPainted(false);
        btnAdd.setFocusPainted(false);
        btnAdd.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnAdd.addActionListener(e -> {
            if (listener != null) listener.onCreateRoomRequested();
        });

        headerText.add(titleText, BorderLayout.WEST);
        headerText.add(btnAdd, BorderLayout.EAST);
        container.add(headerText);

        textChannelsList = new JList<>(textChannelsModel);
        styleList(textChannelsList);
        textChannelsList.setCellRenderer(new TextChannelRenderer());
        textChannelsList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && textChannelsList.getSelectedValue() != null) {
                if (listener != null) listener.onRoomSelected(textChannelsList.getSelectedValue());
            }
        });
        container.add(textChannelsList);

        container.add(Box.createVerticalStrut(14));

        // --- Category 2: Voice RTC Channels ---
        JPanel headerVoice = new JPanel(new BorderLayout());
        headerVoice.setOpaque(false);
        headerVoice.setBorder(BorderFactory.createEmptyBorder(0, 4, 6, 4));
        headerVoice.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));

        JLabel titleVoice = new JLabel("KÊNH THOẠI REALTIME (RTC)");
        titleVoice.setFont(new Font("Segoe UI", Font.BOLD, 10));
        titleVoice.setForeground(ThemeManager.TEXT_MUTED);
        headerVoice.add(titleVoice, BorderLayout.WEST);
        container.add(headerVoice);

        voiceChannelsList = new JList<>(voiceChannelsModel);
        styleList(voiceChannelsList);
        voiceChannelsList.setCellRenderer(new VoiceChannelRenderer());
        voiceChannelsList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && voiceChannelsList.getSelectedValue() != null) {
                if (listener != null) listener.onVoiceRoomSelected(voiceChannelsList.getSelectedValue());
            }
        });
        container.add(voiceChannelsList);

        JScrollPane scroll = new JScrollPane(container);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    private JPanel createDmsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(0, 4, 8, 4));

        JLabel title = new JLabel("DANH SÁCH BẠN BÈ & CHAT");
        title.setFont(new Font("Segoe UI", Font.BOLD, 10));
        title.setForeground(ThemeManager.TEXT_MUTED);

        JButton btnAddFriend = new JButton("+ Kết Bạn");
        btnAddFriend.setFont(new Font("Segoe UI", Font.BOLD, 10));
        btnAddFriend.setForeground(ThemeManager.ACCENT_CYAN);
        btnAddFriend.setContentAreaFilled(false);
        btnAddFriend.setBorderPainted(false);
        btnAddFriend.setFocusPainted(false);
        btnAddFriend.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnAddFriend.addActionListener(e -> showAddFriendDialog(btnAddFriend));

        header.add(title, BorderLayout.WEST);
        header.add(btnAddFriend, BorderLayout.EAST);
        panel.add(header, BorderLayout.NORTH);

        JPanel listContainer = new JPanel();
        listContainer.setLayout(new BoxLayout(listContainer, BoxLayout.Y_AXIS));
        listContainer.setOpaque(false);

        // 1. Bạn bè trực tuyến (Online Friends)
        lblFriendsOnlineHeader = new JLabel("BẠN BÈ TRỰC TUYẾN (0)");
        lblFriendsOnlineHeader.setFont(new Font("Segoe UI", Font.BOLD, 10));
        lblFriendsOnlineHeader.setForeground(ThemeManager.SUCCESS);
        lblFriendsOnlineHeader.setBorder(BorderFactory.createEmptyBorder(6, 4, 4, 4));
        lblFriendsOnlineHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        listContainer.add(lblFriendsOnlineHeader);

        friendsOnlineList = new JList<>(friendsOnlineModel);
        styleList(friendsOnlineList);
        friendsOnlineList.setFixedCellHeight(46);
        friendsOnlineList.setCellRenderer(new FriendUserRenderer(true, true));
        attachFriendListListeners(friendsOnlineList, true, true);
        listContainer.add(friendsOnlineList);

        listContainer.add(Box.createVerticalStrut(8));

        // 2. Bạn bè ngoại tuyến (Offline Friends)
        lblFriendsOfflineHeader = new JLabel("BẠN BÈ NGOẠI TUYẾN (0)");
        lblFriendsOfflineHeader.setFont(new Font("Segoe UI", Font.BOLD, 10));
        lblFriendsOfflineHeader.setForeground(ThemeManager.TEXT_MUTED);
        lblFriendsOfflineHeader.setBorder(BorderFactory.createEmptyBorder(6, 4, 4, 4));
        lblFriendsOfflineHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        listContainer.add(lblFriendsOfflineHeader);

        friendsOfflineList = new JList<>(friendsOfflineModel);
        styleList(friendsOfflineList);
        friendsOfflineList.setFixedCellHeight(46);
        friendsOfflineList.setCellRenderer(new FriendUserRenderer(true, false));
        attachFriendListListeners(friendsOfflineList, true, false);
        listContainer.add(friendsOfflineList);

        listContainer.add(Box.createVerticalStrut(8));

        // 3. Người dùng khác trong mạng đang Online
        lblOtherOnlineHeader = new JLabel("NGƯỜI DÙNG KHÁC ONLINE (0)");
        lblOtherOnlineHeader.setFont(new Font("Segoe UI", Font.BOLD, 10));
        lblOtherOnlineHeader.setForeground(ThemeManager.ACCENT_CYAN);
        lblOtherOnlineHeader.setBorder(BorderFactory.createEmptyBorder(6, 4, 4, 4));
        lblOtherOnlineHeader.setAlignmentX(Component.LEFT_ALIGNMENT);
        listContainer.add(lblOtherOnlineHeader);

        otherOnlineList = new JList<>(otherOnlineModel);
        styleList(otherOnlineList);
        otherOnlineList.setFixedCellHeight(46);
        otherOnlineList.setCellRenderer(new FriendUserRenderer(false, true));
        attachFriendListListeners(otherOnlineList, false, true);
        listContainer.add(otherOnlineList);

        lblEmptyDmsHint = new JLabel("Chưa có bạn bè hay thành viên nào");
        lblEmptyDmsHint.setFont(ThemeManager.FONT_SMALL);
        lblEmptyDmsHint.setForeground(new Color(0x64, 0x74, 0x8B));
        lblEmptyDmsHint.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 0));
        lblEmptyDmsHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        lblEmptyDmsHint.setVisible(false);
        listContainer.add(lblEmptyDmsHint);

        JScrollPane scroll = new JScrollPane(listContainer);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    private void attachFriendListListeners(JList<String> list, boolean isFriend, boolean isOnline) {
        list.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && list.getSelectedValue() != null) {
                if (listener != null) listener.onUserSelected(list.getSelectedValue());
            }
        });
        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { checkPopup(e); }
            @Override
            public void mouseReleased(MouseEvent e) { checkPopup(e); }
            private void checkPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int idx = list.locationToIndex(e.getPoint());
                    if (idx >= 0) {
                        list.setSelectedIndex(idx);
                        String target = list.getSelectedValue();
                        if (target != null && !target.equalsIgnoreCase(currentUsername)) {
                            JPopupMenu menu = new JPopupMenu();
                            JMenuItem itemChat = new JMenuItem("💬 Nhắn tin với @" + target);
                            itemChat.addActionListener(ev -> {
                                if (listener != null) listener.onUserSelected(target);
                            });
                            menu.add(itemChat);

                            if (isFriend) {
                                JMenuItem itemRemove = new JMenuItem("❌ Hủy kết bạn");
                                itemRemove.addActionListener(ev -> {
                                    if (listener != null) listener.onFriendRemoveRequested(target);
                                });
                                menu.add(itemRemove);
                            } else {
                                JMenuItem itemAdd = new JMenuItem("➕ Kết bạn với @" + target);
                                itemAdd.addActionListener(ev -> {
                                    if (listener != null) listener.onFriendAddRequested(target);
                                });
                                menu.add(itemAdd);
                            }
                            menu.show(e.getComponent(), e.getX(), e.getY());
                        }
                    }
                }
            }
        });
    }

    private void showAddFriendDialog(Component parent) {
        String input = javax.swing.JOptionPane.showInputDialog(parent,
                "Nhập tên tài khoản (username) bạn muốn kết bạn:",
                "Kết bạn mới",
                javax.swing.JOptionPane.QUESTION_MESSAGE);
        if (input != null && !input.trim().isEmpty()) {
            String target = input.trim();
            if (target.equalsIgnoreCase(currentUsername)) {
                javax.swing.JOptionPane.showMessageDialog(parent, "Bạn không thể tự kết bạn với chính mình!", "Thông báo", javax.swing.JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (listener != null) {
                listener.onFriendAddRequested(target);
            }
        }
    }

    private JPanel createVoicePanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        header.setBorder(BorderFactory.createEmptyBorder(0, 4, 8, 4));

        JLabel title = new JLabel("PHÒNG THOẠI VOICE RTC");
        title.setFont(new Font("Segoe UI", Font.BOLD, 10));
        title.setForeground(ThemeManager.TEXT_MUTED);
        header.add(title, BorderLayout.WEST);
        panel.add(header, BorderLayout.NORTH);

        JList<String> standaloneVoiceList = new JList<>(voiceChannelsModel);
        styleList(standaloneVoiceList);
        standaloneVoiceList.setCellRenderer(new VoiceChannelRenderer());
        standaloneVoiceList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && standaloneVoiceList.getSelectedValue() != null) {
                if (listener != null) listener.onVoiceRoomSelected(standaloneVoiceList.getSelectedValue());
            }
        });

        JScrollPane scroll = new JScrollPane(standaloneVoiceList);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        panel.add(scroll, BorderLayout.CENTER);

        return panel;
    }

    private void styleList(JList<?> list) {
        list.setBackground(ThemeManager.BG_SIDEBAR);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setOpaque(false);
        list.setFixedCellHeight(34);
        list.setAlignmentX(Component.LEFT_ALIGNMENT);
    }

    private JPanel createBottomPanel() {
        JPanel bottom = new JPanel();
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        bottom.setOpaque(false);

        // 1. Voice Connected Dock (Visible ONLY when active in real voice room)
        voiceDockPanel = new JPanel(new BorderLayout(8, 0));
        voiceDockPanel.setBackground(new Color(0x10, 0x38, 0x2E));
        voiceDockPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 1, 0, new Color(0x10, 0xB9, 0x81, 100)),
                BorderFactory.createEmptyBorder(6, 12, 6, 12)
        ));
        voiceDockPanel.setVisible(false);

        JPanel voiceInfo = new JPanel();
        voiceInfo.setLayout(new BoxLayout(voiceInfo, BoxLayout.Y_AXIS));
        voiceInfo.setOpaque(false);

        lblVoiceRtcStatus = new JLabel("● Đang kết nối thoại");
        lblVoiceRtcStatus.setFont(ThemeManager.FONT_SMALL_BOLD);
        lblVoiceRtcStatus.setForeground(ThemeManager.SUCCESS);
        lblVoiceRtcStatus.setToolTipText("Kênh âm thanh thời gian thực qua UDP");

        lblVoiceRoomName = new JLabel("Phòng Thoại", AppIcons.createVoiceWaveIcon(14, ThemeManager.TEXT_PRIMARY), JLabel.LEFT);
        lblVoiceRoomName.setFont(ThemeManager.FONT_SMALL);
        lblVoiceRoomName.setForeground(ThemeManager.TEXT_PRIMARY);

        voiceInfo.add(lblVoiceRtcStatus);
        voiceInfo.add(lblVoiceRoomName);

        btnDisconnectVoice = new JButton("Rời");
        btnDisconnectVoice.setToolTipText("Rời khỏi phòng thoại");
        btnDisconnectVoice.setFont(ThemeManager.FONT_SMALL_BOLD);
        btnDisconnectVoice.setBackground(ThemeManager.DANGER);
        btnDisconnectVoice.setForeground(Color.WHITE);
        btnDisconnectVoice.setFocusPainted(false);
        btnDisconnectVoice.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnDisconnectVoice.addActionListener(e -> {
            if (listener != null) listener.onVoiceLeaveRequested();
        });

        voiceDockPanel.add(voiceInfo, BorderLayout.CENTER);
        voiceDockPanel.add(btnDisconnectVoice, BorderLayout.EAST);

        // 2. User Profile Footer
        JPanel userBar = new JPanel(new BorderLayout(8, 0));
        userBar.setBackground(ThemeManager.BG_NAV);
        userBar.setPreferredSize(new Dimension(260, 52));
        userBar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, ThemeManager.BORDER_SUBTLE),
                BorderFactory.createEmptyBorder(4, 10, 4, 10)
        ));

        JPanel userLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        userLeft.setOpaque(false);

        lblUserAvatar = new JLabel(ThemeManager.createAvatarIcon(currentUsername, 30, true));
        JPanel nameBox = new JPanel();
        nameBox.setLayout(new BoxLayout(nameBox, BoxLayout.Y_AXIS));
        nameBox.setOpaque(false);

        lblUsername = new JLabel("@" + currentUsername);
        lblUsername.setFont(ThemeManager.FONT_BODY_BOLD);
        lblUsername.setForeground(ThemeManager.TEXT_PRIMARY);

        JLabel lblStatus = new JLabel("Trực tuyến");
        lblStatus.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        lblStatus.setForeground(ThemeManager.SUCCESS);

        nameBox.add(lblUsername);
        nameBox.add(lblStatus);

        userLeft.add(lblUserAvatar);
        userLeft.add(nameBox);

        JPanel controlsRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 8));
        controlsRight.setOpaque(false);

        btnMic = createIconButton(AppIcons.createMicIcon(16, ThemeManager.TEXT_SECONDARY, isMicMuted), "Bật/Tắt Microphone");
        btnMic.addActionListener(e -> {
            if (listener != null) listener.onMicToggled();
        });

        btnDeafen = createIconButton(AppIcons.createHeadphonesIcon(16, ThemeManager.TEXT_SECONDARY, isDeafened), "Bật/Tắt Tai Nghe");
        btnDeafen.addActionListener(e -> {
            if (listener != null) listener.onDeafenToggled();
        });

        btnLogout = createIconButton(AppIcons.createCloseIcon(16, ThemeManager.TEXT_MUTED), "Đăng xuất tài khoản");
        btnLogout.addActionListener(e -> {
            if (listener != null) listener.onLogoutRequested();
        });

        controlsRight.add(btnMic);
        controlsRight.add(btnDeafen);
        controlsRight.add(btnLogout);

        userBar.add(userLeft, BorderLayout.CENTER);
        userBar.add(controlsRight, BorderLayout.EAST);

        bottom.add(voiceDockPanel);
        bottom.add(userBar);
        return bottom;
    }

    private JButton createIconButton(Icon icon, String tooltip) {
        JButton btn = new JButton(icon);
        btn.setToolTipText(tooltip);
        btn.setPreferredSize(new Dimension(28, 28));
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    public void setUsername(String username) {
        this.currentUsername = (username != null && !username.trim().isEmpty()) ? username.trim() : "User";
        lblUsername.setText("@" + currentUsername);
        lblUserAvatar.setIcon(ThemeManager.createAvatarIcon(currentUsername, 30, true));
    }

    public void setVoiceState(boolean connected, String roomName) {
        this.isVoiceConnected = connected;
        if (voiceDockPanel != null) {
            voiceDockPanel.setVisible(connected);
            if (connected && roomName != null) {
                lblVoiceRoomName.setText(roomName);
            }
        }
        repaint();
    }

    public void setMicMuted(boolean muted) {
        this.isMicMuted = muted;
        if (btnMic != null) {
            btnMic.setIcon(AppIcons.createMicIcon(16, ThemeManager.TEXT_SECONDARY, muted));
        }
    }

    public void setDeafened(boolean deafened) {
        this.isDeafened = deafened;
        if (btnDeafen != null) {
            btnDeafen.setIcon(AppIcons.createHeadphonesIcon(16, ThemeManager.TEXT_SECONDARY, deafened));
        }
    }

    public void setVolume(double vol) {
        this.currentVolume = vol;
    }

    public void updateRooms(List<String> rooms) {
        textChannelsModel.clear();
        voiceChannelsModel.clear();
        for (String r : rooms) {
            String lower = r.toLowerCase();
            if (lower.contains("voice") || lower.contains("thoai") || lower.contains("audio")) {
                voiceChannelsModel.addElement(r);
            } else {
                textChannelsModel.addElement(r);
            }
        }
        if (voiceChannelsModel.isEmpty()) {
            voiceChannelsModel.addElement("Phòng Thảo Luận 01");
            voiceChannelsModel.addElement("Báo Cáo Nhóm (RTC)");
        }
        if (textChannelsModel.isEmpty()) {
            textChannelsModel.addElement("Lobby");
            textChannelsModel.addElement("ThaoLuan_MMT");
            textChannelsModel.addElement("ChiaSe_TaiLieu");
        }
    }

    public void updateOnlineUsers(List<String> users) {
        this.cachedOnlineUsers = (users != null) ? new ArrayList<>(users) : new ArrayList<>();
        refreshFriendsAndUsers();
    }

    public void refreshFriendsAndUsers() {
        friendsOnlineModel.clear();
        friendsOfflineModel.clear();
        otherOnlineModel.clear();
        directMessagesModel.clear();

        dammtp2p.client.FriendsManager fm = dammtp2p.client.FriendsManager.getInstance();
        List<String> onlineFriends = fm.getOnlineFriends(currentUsername, cachedOnlineUsers);
        List<String> offlineFriends = fm.getOfflineFriends(currentUsername, cachedOnlineUsers);

        for (String f : onlineFriends) {
            friendsOnlineModel.addElement(f);
            directMessagesModel.addElement(f);
        }
        for (String f : offlineFriends) {
            friendsOfflineModel.addElement(f);
            directMessagesModel.addElement(f);
        }

        // Other online users not yet friended
        for (String u : cachedOnlineUsers) {
            if (!u.equalsIgnoreCase(currentUsername) && !fm.isFriend(currentUsername, u)) {
                otherOnlineModel.addElement(u);
                if (!directMessagesModel.contains(u)) {
                    directMessagesModel.addElement(u);
                }
            }
        }

        if (lblFriendsOnlineHeader != null) {
            lblFriendsOnlineHeader.setText("BẠN BÈ TRỰC TUYẾN (" + friendsOnlineModel.size() + ")");
        }
        if (lblFriendsOfflineHeader != null) {
            lblFriendsOfflineHeader.setText("BẠN BÈ NGOẠI TUYẾN (" + friendsOfflineModel.size() + ")");
        }
        if (lblOtherOnlineHeader != null) {
            lblOtherOnlineHeader.setText("NGƯỜI DÙNG KHÁC ONLINE (" + otherOnlineModel.size() + ")");
        }

        boolean isEmpty = friendsOnlineModel.isEmpty() && friendsOfflineModel.isEmpty() && otherOnlineModel.isEmpty();
        if (lblEmptyDmsHint != null) {
            lblEmptyDmsHint.setVisible(isEmpty);
        }
    }

    private void showNewDmPopup(Component invoker) {
        JPopupMenu menu = new JPopupMenu();
        menu.setBackground(ThemeManager.BG_CARD);
        menu.setBorder(BorderFactory.createLineBorder(ThemeManager.BORDER_COLOR, 1));

        int added = 0;
        for (String u : cachedOnlineUsers) {
            if (!u.equalsIgnoreCase(currentUsername)) {
                JMenuItem item = new JMenuItem("Nhắn tin với @" + u, ThemeManager.createAvatarIcon(u, 18, true));
                item.addActionListener(ev -> {
                    if (listener != null) listener.onUserSelected(u);
                    selectDirectMessage(u);
                });
                menu.add(item);
                added++;
            }
        }
        if (added == 0) {
            JMenuItem empty = new JMenuItem("Chưa có thành viên nào khác online");
            empty.setEnabled(false);
            menu.add(empty);
        }
        menu.show(invoker, 0, invoker.getHeight());
    }

    public void selectRoom(String roomName) {
        switchTab(TAB_ROOMS);
        for (int i = 0; i < textChannelsModel.size(); i++) {
            if (textChannelsModel.get(i).equalsIgnoreCase(roomName)) {
                textChannelsList.setSelectedIndex(i);
                break;
            }
        }
    }

    public void selectDirectMessage(String username) {
        switchTab(TAB_DMS);
        if (friendsOnlineList != null) friendsOnlineList.clearSelection();
        if (friendsOfflineList != null) friendsOfflineList.clearSelection();
        if (otherOnlineList != null) otherOnlineList.clearSelection();

        for (int i = 0; i < friendsOnlineModel.size(); i++) {
            if (friendsOnlineModel.get(i).equalsIgnoreCase(username)) {
                friendsOnlineList.setSelectedIndex(i);
                return;
            }
        }
        for (int i = 0; i < friendsOfflineModel.size(); i++) {
            if (friendsOfflineModel.get(i).equalsIgnoreCase(username)) {
                friendsOfflineList.setSelectedIndex(i);
                return;
            }
        }
        for (int i = 0; i < otherOnlineModel.size(); i++) {
            if (otherOnlineModel.get(i).equalsIgnoreCase(username)) {
                otherOnlineList.setSelectedIndex(i);
                return;
            }
        }
    }

    public void setSpeakingUsers(Set<String> speakers) {
        this.speakingUsers.clear();
        this.speakingUsers.addAll(speakers);
        voiceChannelsList.repaint();
    }

    private static class TextChannelRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JPanel p = new JPanel(new BorderLayout(8, 0)) {
                @Override
                protected void paintComponent(Graphics g) {
                    if (isSelected) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        LinearGradientPaint gp = new LinearGradientPaint(
                                0, 0, Math.max(getWidth(), 1), 0,
                                new float[]{0.0f, 1.0f},
                                new Color[]{new Color(0x0E, 0x38, 0x44, 230), new Color(0x08, 0x24, 0x2C, 245)}
                        );
                        g2.setPaint(gp);
                        g2.fillRoundRect(4, 2, getWidth() - 8, getHeight() - 4, 10, 10);
                        g2.setColor(new Color(0x00, 0xF2, 0xFE, 160));
                        g2.setStroke(new BasicStroke(1.2f));
                        g2.drawRoundRect(4, 2, getWidth() - 8, getHeight() - 4, 10, 10);

                        // Vertical neon indicator bar on left edge
                        g2.setColor(ThemeManager.ACCENT_ELECTRIC_CYAN);
                        g2.fillRoundRect(4, 7, 3, getHeight() - 14, 2, 2);
                        g2.dispose();
                    }
                    super.paintComponent(g);
                }
            };
            p.setOpaque(false);
            p.setBorder(BorderFactory.createEmptyBorder(2, 10, 2, 10));

            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
            left.setOpaque(false);

            JLabel icon = new JLabel("#");
            icon.setFont(new Font("Segoe UI", Font.BOLD, 14));
            icon.setForeground(isSelected ? ThemeManager.ACCENT_ELECTRIC_CYAN : ThemeManager.TEXT_MUTED);

            JLabel label = new JLabel(value.toString());
            label.setFont(isSelected ? ThemeManager.FONT_BODY_BOLD : ThemeManager.FONT_BODY);
            label.setForeground(isSelected ? Color.WHITE : ThemeManager.TEXT_SECONDARY);

            left.add(icon);
            left.add(label);
            p.add(left, BorderLayout.WEST);

            if ("Lobby".equalsIgnoreCase(value.toString())) {
                JLabel liveBadge = ThemeManager.createBadge("LIVE", ThemeManager.SUCCESS, new Color(0x10, 0xB9, 0x81, 35));
                liveBadge.setFont(new Font("Segoe UI", Font.BOLD, 9));
                p.add(liveBadge, BorderLayout.EAST);
            }

            return p;
        }
    }

    private class VoiceChannelRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JPanel p = new JPanel(new BorderLayout(8, 0)) {
                @Override
                protected void paintComponent(Graphics g) {
                    if (isSelected) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        LinearGradientPaint gp = new LinearGradientPaint(
                                0, 0, Math.max(getWidth(), 1), 0,
                                new float[]{0.0f, 1.0f},
                                new Color[]{new Color(0x0D, 0x48, 0x38, 230), new Color(0x08, 0x2E, 0x28, 245)}
                        );
                        g2.setPaint(gp);
                        g2.fillRoundRect(4, 2, getWidth() - 8, getHeight() - 4, 10, 10);
                        g2.setColor(new Color(0x34, 0xD3, 0x99, 140));
                        g2.setStroke(new BasicStroke(1.2f));
                        g2.drawRoundRect(4, 2, getWidth() - 8, getHeight() - 4, 10, 10);

                        // Vertical neon indicator bar on left edge
                        g2.setColor(ThemeManager.SUCCESS);
                        g2.fillRoundRect(4, 7, 3, getHeight() - 14, 2, 2);
                        g2.dispose();
                    }
                    super.paintComponent(g);
                }
            };
            p.setOpaque(false);
            p.setBorder(BorderFactory.createEmptyBorder(2, 10, 2, 10));

            JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 2));
            left.setOpaque(false);

            JLabel icon = new JLabel(AppIcons.createVoiceWaveIcon(15, isSelected ? Color.WHITE : ThemeManager.SUCCESS));
            JLabel label = new JLabel(value.toString());
            label.setFont(isSelected ? ThemeManager.FONT_BODY_BOLD : ThemeManager.FONT_BODY);
            label.setForeground(isSelected ? Color.WHITE : ThemeManager.TEXT_SECONDARY);

            left.add(icon);
            left.add(label);
            p.add(left, BorderLayout.WEST);

            if (value.toString().toLowerCase().contains("amnhac") || value.toString().toLowerCase().contains("voice")) {
                JLabel audioBadge = ThemeManager.createBadge("16kHz", ThemeManager.ACCENT_CYAN, new Color(0x00, 0xF2, 0xFE, 30));
                audioBadge.setFont(new Font("Segoe UI", Font.BOLD, 9));
                p.add(audioBadge, BorderLayout.EAST);
            }

            return p;
        }
    }

    private static class DirectMessageRenderer extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4)) {
                @Override
                protected void paintComponent(Graphics g) {
                    if (isSelected) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        LinearGradientPaint gp = new LinearGradientPaint(
                                0, 0, Math.max(getWidth(), 1), 0,
                                new float[]{0.0f, 1.0f},
                                new Color[]{new Color(0x63, 0x66, 0xF1, 190), new Color(0x8B, 0x5C, 0xF6, 150)}
                        );
                        g2.setPaint(gp);
                        g2.fillRoundRect(4, 2, getWidth() - 8, getHeight() - 4, 10, 10);
                        g2.setColor(new Color(0xC0, 0x84, 0xFC, 120));
                        g2.drawRoundRect(4, 2, getWidth() - 8, getHeight() - 4, 10, 10);
                        g2.dispose();
                    }
                    super.paintComponent(g);
                }
            };
            p.setOpaque(false);
            p.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));

            String user = value.toString();
            JLabel avatar = new JLabel(ThemeManager.createAvatarIcon(user, 20, true));
            JLabel label = new JLabel("@" + user);
            label.setFont(isSelected ? ThemeManager.FONT_BODY_BOLD : ThemeManager.FONT_BODY);
            label.setForeground(isSelected ? Color.WHITE : ThemeManager.getUserColor(user));

            p.add(avatar);
            p.add(label);
            return p;
        }
    }

    private static class FriendUserRenderer extends DefaultListCellRenderer {
        private final boolean isFriend;
        private final boolean isOnline;

        public FriendUserRenderer(boolean isFriend, boolean isOnline) {
            this.isFriend = isFriend;
            this.isOnline = isOnline;
        }

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
            JPanel p = new JPanel(new BorderLayout(8, 0)) {
                @Override
                protected void paintComponent(Graphics g) {
                    if (isSelected) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        LinearGradientPaint gp = new LinearGradientPaint(
                                0, 0, Math.max(getWidth(), 1), 0,
                                new float[]{0.0f, 1.0f},
                                new Color[]{new Color(0x63, 0x66, 0xF1, 190), new Color(0x8B, 0x5C, 0xF6, 150)}
                        );
                        g2.setPaint(gp);
                        g2.fillRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 10, 10);
                        g2.setColor(new Color(0xC0, 0x84, 0xFC, 120));
                        g2.drawRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 10, 10);
                        g2.dispose();
                    }
                    super.paintComponent(g);
                }
            };
            p.setOpaque(false);
            p.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));

            String user = value.toString();
            JLabel avatar = new JLabel(ThemeManager.createAvatarIcon(user, 28, isOnline));

            JPanel center = new JPanel();
            center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
            center.setOpaque(false);
            center.setAlignmentX(Component.LEFT_ALIGNMENT);

            JPanel rowTop = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
            rowTop.setOpaque(false);
            rowTop.setAlignmentX(Component.LEFT_ALIGNMENT);

            JLabel label = new JLabel("@" + user);
            label.setFont(isSelected ? ThemeManager.FONT_BODY_BOLD : ThemeManager.FONT_BODY);
            label.setForeground(isOnline ? Color.WHITE : new Color(0x94, 0xA3, 0xB8));
            rowTop.add(label);

            if (isFriend) {
                rowTop.add(ThemeManager.createBadge("BẠN BÈ", isOnline ? ThemeManager.ACCENT_PRIMARY : new Color(0x64, 0x74, 0x8B), new Color(0x63, 0x66, 0xF1, 35)));
            }

            JLabel statusLabel = new JLabel(isOnline ? "● Trực tuyến" : "○ Ngoại tuyến");
            statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            statusLabel.setForeground(isOnline ? ThemeManager.SUCCESS : new Color(0x64, 0x74, 0x8B));
            statusLabel.setBorder(BorderFactory.createEmptyBorder(1, 4, 0, 0));
            statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);

            center.add(rowTop);
            center.add(statusLabel);

            p.add(avatar, BorderLayout.WEST);
            p.add(center, BorderLayout.CENTER);
            return p;
        }
    }
}
