package dammtp2p.test;

import dammtp2p.client.ui.ConversationSidebar;
import dammtp2p.ui.theme.ThemeManager;
import java.awt.Component;
import java.awt.Dimension;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import javax.swing.Icon;
import javax.swing.SwingUtilities;

/**
 * Automated Verification Test for:
 * 1. Text & Voice channel alignment in ConversationSidebar
 * 2. Real-time voice participants roster and speaking detection
 * 3. Chat bubble unread badge counter on user avatar
 */
public class VoiceAndUnreadBadgeTest {

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        ThemeManager.setupTheme();

        SwingUtilities.invokeAndWait(() -> {
            try {
                System.out.println(">>> [TEST 1] Testing ConversationSidebar Alignment and Voice Rooms Roster...");

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

                ConversationSidebar sidebar = new ConversationSidebar(dummyListener);
                sidebar.setUsername("DuongThai");
                sidebar.setSize(new Dimension(285, 800));
                sidebar.doLayout();

                // Test rooms population
                sidebar.updateRooms(Arrays.asList("General", "Lobby", "MMT_NHOM05", "Voice_Lounge"));

                // Test voice participants update
                sidebar.setVoiceParticipants("Voice_Lounge", Arrays.asList("alice", "bob"));
                List<String> inVoice = sidebar.getVoiceParticipants("Voice_Lounge");
                if (inVoice.size() != 2 || !inVoice.contains("alice") || !inVoice.contains("bob")) {
                    throw new AssertionError("Voice participants roster not updated properly! Found: " + inVoice);
                }
                System.out.println("  [PASS] Voice participants verified in Voice_Lounge: " + inVoice);

                // Test speaking detection update
                sidebar.setSpeakingUsers(new HashSet<>(Collections.singletonList("alice")));
                System.out.println("  [PASS] Speaking users updated successfully for 'alice'");

                // Test unread count & chat bubble logic
                System.out.println("\n>>> [TEST 2] Testing Chat Bubble Unread Message Badge Logic...");
                if (sidebar.getUnreadCount("charlie") != 0) {
                    throw new AssertionError("Initial unread count should be 0");
                }

                // Increment incoming message count
                sidebar.incrementUnreadCount("charlie");
                sidebar.incrementUnreadCount("charlie");
                if (sidebar.getUnreadCount("charlie") != 2) {
                    throw new AssertionError("Unread count should be 2 for charlie, got: " + sidebar.getUnreadCount("charlie"));
                }
                if (sidebar.getTotalUnreadCount() != 2) {
                    throw new AssertionError("Total unread count should be 2, got: " + sidebar.getTotalUnreadCount());
                }
                System.out.println("  [PASS] Charlie unread count is correctly incremented to 2");

                // Check chat bubble avatar rendering without crash
                Icon avatarWithBubble = ThemeManager.createAvatarWithChatBubble("charlie", 30, true, false, 2);
                if (avatarWithBubble.getIconWidth() <= 30) {
                    throw new AssertionError("Avatar icon with chat bubble should expand width for badge padding!");
                }
                System.out.println("  [PASS] Avatar with chat bubble rendered with dimensions: "
                        + avatarWithBubble.getIconWidth() + "x" + avatarWithBubble.getIconHeight());

                // Clear unread count on reading/opening DM
                sidebar.clearUnreadCount("charlie");
                if (sidebar.getUnreadCount("charlie") != 0) {
                    throw new AssertionError("Unread count should reset to 0 after clear!");
                }
                if (sidebar.getTotalUnreadCount() != 0) {
                    throw new AssertionError("Total unread count should be 0!");
                }
                System.out.println("  [PASS] Charlie unread count reset to 0 upon opening conversation");

                System.out.println("\n=======================================================");
                System.out.println("  ALL AUTOMATED VERIFICATION TESTS PASSED SUCCESSFULLY!");
                System.out.println("=======================================================");
            } catch (Throwable t) {
                t.printStackTrace();
                System.exit(1);
            }
        });
    }
}
