package dammtp2p.test;

import dammtp2p.client.ui.ConversationSidebar;
import dammtp2p.ui.theme.ThemeManager;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

public class GenerateUpdatedVisualScreenshot {

    public static void main(String[] args) throws Exception {
        ThemeManager.setupTheme();

        File dir = new File("screenshots");
        if (!dir.exists()) dir.mkdirs();

        SwingUtilities.invokeAndWait(() -> {
            try {
                ConversationSidebar.SidebarListener dummyListener = new ConversationSidebar.SidebarListener() {
                    @Override public void onRoomSelected(String roomName) {}
                    @Override public void onVoiceRoomSelected(String roomName) {}
                    @Override public void onUserSelected(String username) {}
                    @Override public void onCreateRoomRequested() {}
                    @Override public void onVoiceLeaveRequested() {}
                    @Override public void onMicToggled() {}
                    @Override public void onDeafenToggled() {}
                    @Override public void onVolumeChanged(double volume) {}
                };

                // 1. Capture Rooms Tab with Aligned Text Channels & Real-time Voice Room Roster
                javax.swing.JFrame frame = new javax.swing.JFrame();
                frame.setUndecorated(true);
                ConversationSidebar sidebar = new ConversationSidebar(dummyListener);
                frame.getContentPane().add(sidebar);
                sidebar.setUsername("DuongThai");
                sidebar.updateRooms(Arrays.asList("General", "Lobby", "MMT_NHOM05", "Voice_Lounge"));
                sidebar.selectRoom("Lobby");

                // Populate voice members and speaking user
                sidebar.setVoiceParticipants("Voice_Lounge", Arrays.asList("alice", "bob"));
                sidebar.setSpeakingUsers(new HashSet<>(Collections.singletonList("alice")));

                // Simulate incoming unread messages from charlie
                sidebar.incrementUnreadCount("charlie");
                sidebar.incrementUnreadCount("charlie");
                sidebar.incrementUnreadCount("charlie");

                int w = 285;
                int h = 720;
                frame.setSize(w, h);
                frame.addNotify();
                frame.validate();
                sidebar.setSize(w, h);
                sidebar.validate();
                sidebar.repaint();

                BufferedImage imgRooms = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                Graphics2D g2 = imgRooms.createGraphics();
                g2.setColor(ThemeManager.BG_SIDEBAR);
                g2.fillRect(0, 0, w, h);
                sidebar.printAll(g2);
                g2.dispose();

                File destRooms = new File(dir, "sidebar_rooms_and_voice.png");
                ImageIO.write(imgRooms, "PNG", destRooms);
                System.out.println("[SAVED SCREENSHOT 1] " + destRooms.getAbsolutePath());

                // 2. Switch to Friends / DMs Tab to showcase Chat Bubble on Avatar with Unread Count
                sidebar.updateOnlineUsers(Arrays.asList("DuongThai", "alice", "bob", "charlie"));
                sidebar.switchTab(ConversationSidebar.TAB_DMS);
                sidebar.validate();
                sidebar.repaint();

                BufferedImage imgDms = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                Graphics2D g2Dms = imgDms.createGraphics();
                g2Dms.setColor(ThemeManager.BG_SIDEBAR);
                g2Dms.fillRect(0, 0, w, h);
                sidebar.printAll(g2Dms);
                g2Dms.dispose();

                File destDms = new File(dir, "sidebar_friends_chat_bubble.png");
                ImageIO.write(imgDms, "PNG", destDms);
                System.out.println("[SAVED SCREENSHOT 2] " + destDms.getAbsolutePath());

                frame.dispose();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    private static void layoutRecursively(Component comp) {
        comp.doLayout();
        if (comp instanceof javax.swing.JComponent) {
            ((javax.swing.JComponent) comp).revalidate();
        }
        if (comp instanceof java.awt.Container) {
            for (Component child : ((java.awt.Container) comp).getComponents()) {
                layoutRecursively(child);
            }
        }
    }
}
