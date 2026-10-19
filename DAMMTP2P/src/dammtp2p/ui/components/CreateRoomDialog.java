package dammtp2p.ui.components;

import dammtp2p.ui.theme.AppIcons;
import dammtp2p.ui.theme.ThemeManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;

/**
 * Modern dialog for creating new Group Chat rooms in DAMMTP2P over ZeroTier.
 * Provides room naming, topic, channel type, and member invitation selections.
 */
public class CreateRoomDialog extends JDialog {

    public static class RoomCreationResult {
        public final String roomId;
        public final String displayName;
        public final String topic;
        public final String channelType;
        public final List<String> invitedUsers;

        public RoomCreationResult(String roomId, String displayName, String topic, String channelType, List<String> invitedUsers) {
            this.roomId = roomId;
            this.displayName = displayName;
            this.topic = topic;
            this.channelType = channelType;
            this.invitedUsers = invitedUsers;
        }
    }

    private boolean confirmed = false;
    private RoomCreationResult result = null;

    private JTextField txtRoomId;
    private JTextField txtDisplayName;
    private JTextField txtTopic;
    private JComboBox<String> cmbChannelType;
    private final List<JCheckBox> memberCheckboxes = new ArrayList<>();

    public CreateRoomDialog(Frame parent, List<String> onlineUsers) {
        super(parent, "Tạo Phòng Trò Chuyện Nhóm Mới", true);
        setSize(480, 520);
        setResizable(false);
        setLocationRelativeTo(parent);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        JPanel root = new JPanel(new BorderLayout(0, 12));
        root.setBackground(ThemeManager.BG_DARK);
        root.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(16, 20, 16, 20)
        ));

        // Header Panel
        JPanel headerPanel = new JPanel(new BorderLayout(10, 0));
        headerPanel.setOpaque(false);

        JLabel lblIcon = new JLabel(AppIcons.createNetworkIconSafe(28, ThemeManager.ACCENT_PRIMARY));
        JPanel titleBox = new JPanel();
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        titleBox.setOpaque(false);

        JLabel lblTitle = new JLabel("TẠO PHÒNG TRÒ CHUYỆN NHÓM");
        lblTitle.setFont(new Font("Segoe UI", Font.BOLD, 15));
        lblTitle.setForeground(ThemeManager.TEXT_PRIMARY);

        JLabel lblSub = new JLabel("Hệ thống Group Chat mã hóa AES-128 & Broadcast ZeroTier LAN");
        lblSub.setFont(ThemeManager.FONT_SMALL);
        lblSub.setForeground(ThemeManager.TEXT_MUTED);

        titleBox.add(lblTitle);
        titleBox.add(Box.createVerticalStrut(2));
        titleBox.add(lblSub);

        headerPanel.add(lblIcon, BorderLayout.WEST);
        headerPanel.add(titleBox, BorderLayout.CENTER);
        root.add(headerPanel, BorderLayout.NORTH);

        // Center Form
        JPanel formPanel = new JPanel();
        formPanel.setLayout(new BoxLayout(formPanel, BoxLayout.Y_AXIS));
        formPanel.setOpaque(false);

        // Field 1: Room ID / Code
        formPanel.add(createFieldLabel("MÃ PHÒNG (ROOM ID - KHÔNG DẤU):"));
        txtRoomId = new JTextField("MMT_NHOM05");
        txtRoomId.setFont(ThemeManager.FONT_BODY);
        txtRoomId.setBackground(ThemeManager.BG_INPUT);
        txtRoomId.setForeground(ThemeManager.TEXT_PRIMARY);
        txtRoomId.setCaretColor(ThemeManager.ACCENT_PRIMARY);
        txtRoomId.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        formPanel.add(txtRoomId);
        formPanel.add(Box.createVerticalStrut(10));

        // Field 2: Display Name
        formPanel.add(createFieldLabel("TÊN HIỂN THỊ PHÒNG:"));
        txtDisplayName = new JTextField("Nhóm 05 - Lập Trình Mạng");
        txtDisplayName.setFont(ThemeManager.FONT_BODY);
        txtDisplayName.setBackground(ThemeManager.BG_INPUT);
        txtDisplayName.setForeground(ThemeManager.TEXT_PRIMARY);
        txtDisplayName.setCaretColor(ThemeManager.ACCENT_PRIMARY);
        txtDisplayName.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        formPanel.add(txtDisplayName);
        formPanel.add(Box.createVerticalStrut(10));

        // Field 3: Topic / Description
        formPanel.add(createFieldLabel("CHỦ ĐỀ THẢO LUẬN (TOPIC):"));
        txtTopic = new JTextField("Thảo luận đồ án MMT & truyền tệp tiếp sức qua ZeroTier");
        txtTopic.setFont(ThemeManager.FONT_BODY);
        txtTopic.setBackground(ThemeManager.BG_INPUT);
        txtTopic.setForeground(ThemeManager.TEXT_PRIMARY);
        txtTopic.setCaretColor(ThemeManager.ACCENT_PRIMARY);
        txtTopic.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)
        ));
        formPanel.add(txtTopic);
        formPanel.add(Box.createVerticalStrut(10));

        // Field 4: Channel Type
        formPanel.add(createFieldLabel("LOẠI KÊNH:"));
        cmbChannelType = new JComboBox<>(new String[]{"# Kênh Văn Bản Chung (Text Channel)", "🔒 Nhóm Học Tập Bảo Mật (Private Study)"});
        cmbChannelType.setFont(ThemeManager.FONT_BODY);
        cmbChannelType.setBackground(ThemeManager.BG_INPUT);
        cmbChannelType.setForeground(ThemeManager.TEXT_PRIMARY);
        formPanel.add(cmbChannelType);
        formPanel.add(Box.createVerticalStrut(10));

        // Field 5: Select Online Members to Invite
        formPanel.add(createFieldLabel("MỜI THÀNH VIÊN ĐANG ONLINE:"));
        JPanel membersPanel = new JPanel();
        membersPanel.setLayout(new BoxLayout(membersPanel, BoxLayout.Y_AXIS));
        membersPanel.setBackground(ThemeManager.BG_INPUT);
        membersPanel.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        if (onlineUsers != null && !onlineUsers.isEmpty()) {
            for (String u : onlineUsers) {
                JCheckBox cb = new JCheckBox("👤 @" + u, true);
                cb.setFont(ThemeManager.FONT_SMALL);
                cb.setForeground(ThemeManager.TEXT_SECONDARY);
                cb.setOpaque(false);
                memberCheckboxes.add(cb);
                membersPanel.add(cb);
            }
        } else {
            JLabel lblNone = new JLabel("(Chưa có người dùng khác online trên ZeroTier)");
            lblNone.setFont(ThemeManager.FONT_SMALL);
            lblNone.setForeground(ThemeManager.TEXT_MUTED);
            membersPanel.add(lblNone);
        }

        JScrollPane scrollMembers = new JScrollPane(membersPanel);
        scrollMembers.setPreferredSize(new Dimension(420, 80));
        scrollMembers.setBorder(BorderFactory.createLineBorder(ThemeManager.BORDER_COLOR, 1));
        formPanel.add(scrollMembers);

        root.add(formPanel, BorderLayout.CENTER);

        // Bottom Action Buttons
        JPanel actionPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        actionPanel.setOpaque(false);

        JButton btnCancel = new JButton("Hủy");
        btnCancel.setFont(ThemeManager.FONT_BODY);
        btnCancel.setFocusPainted(false);
        btnCancel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnCancel.addActionListener(e -> dispose());

        JButton btnSubmit = new JButton("✨ Tạo Phòng Ngay");
        btnSubmit.setFont(ThemeManager.FONT_BODY_BOLD);
        btnSubmit.setBackground(ThemeManager.ACCENT_PRIMARY);
        btnSubmit.setForeground(Color.WHITE);
        btnSubmit.setFocusPainted(false);
        btnSubmit.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btnSubmit.addActionListener(e -> {
            String rid = txtRoomId.getText().trim().replaceAll("\\s+", "_");
            String dname = txtDisplayName.getText().trim();
            if (rid.isEmpty()) {
                txtRoomId.requestFocus();
                return;
            }
            if (dname.isEmpty()) {
                dname = rid;
            }

            List<String> invited = new ArrayList<>();
            for (JCheckBox cb : memberCheckboxes) {
                if (cb.isSelected()) {
                    invited.add(cb.getText().replace("👤 @", "").trim());
                }
            }

            result = new RoomCreationResult(rid, dname, txtTopic.getText().trim(),
                    (String) cmbChannelType.getSelectedItem(), invited);
            confirmed = true;
            dispose();
        });

        actionPanel.add(btnCancel);
        actionPanel.add(btnSubmit);
        root.add(actionPanel, BorderLayout.SOUTH);

        setContentPane(root);
    }

    private JLabel createFieldLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(new Font("Segoe UI", Font.BOLD, 10));
        lbl.setForeground(ThemeManager.TEXT_MUTED);
        lbl.setBorder(BorderFactory.createEmptyBorder(2, 0, 4, 0));
        return lbl;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public RoomCreationResult getResult() {
        return result;
    }
}