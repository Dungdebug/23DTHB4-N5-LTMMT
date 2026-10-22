package dammtp2p.test;

import dammtp2p.client.ClientCore;
import dammtp2p.client.ui.ClientMainFrame;
import dammtp2p.client.ui.ConversationTimeline;
import dammtp2p.client.ui.ChatMessageView;
import dammtp2p.client.ui.TransferCenterDialog;
import dammtp2p.core.model.ChatMessage;
import dammtp2p.core.model.FileMetadata;
import dammtp2p.ui.theme.ThemeManager;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/**
 * Visual verification utility for capturing high-fidelity screenshots of the
 * DAMMTP2P Mesh Workspace UI.
 */
public class MeshWorkspaceVisualTest {

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "false");
        ThemeManager.setupTheme();

        String outputDir = (args.length > 0) ? args[0] : "screenshots";
        File dir = new File(outputDir);
        if (!dir.exists()) dir.mkdirs();

        SwingUtilities.invokeAndWait(() -> {
            try {
                ClientCore dummyClient = new ClientCore("DuongThai");
                ClientMainFrame frame = new ClientMainFrame(dummyClient, "DuongThai", false);

                // 1A. Capture DAMMTP2P Auth & Connection Gateway Screen at 1280x720
                frame.setSize(1280, 720);
                frame.doLayout();
                layoutRecursively(frame);
                captureComponent(frame, 1280, 720, new File(dir, "damm_auth_gateway.png"));
                captureComponent(frame, 1280, 720, new File(dir, "damm_auth_gateway_1280x720.png"));
                System.out.println("[CAPTURED] damm_auth_gateway.png (1280x720)");

                // 1B. Capture DAMMTP2P Auth & Connection Gateway Screen at 1067x800
                frame.setSize(1067, 800);
                frame.doLayout();
                layoutRecursively(frame);
                captureComponent(frame, 1067, 800, new File(dir, "damm_auth_gateway_1067x800.png"));
                System.out.println("[CAPTURED] damm_auth_gateway_1067x800.png (1067x800)");

                // 2. Simulate Authentication Success & Populate Workspace
                frame.setupWorkspaceState("DuongThai",
                        Arrays.asList("DuongThai", "alice", "bob", "charlie"),
                        Arrays.asList("Lobby", "ThaoLuan_MMT", "AmNhac_Voice"));

                // Add sample chat messages to Lobby timeline (testing multiline wrapping and URLs)
                long now = System.currentTimeMillis();
                frame.postChatMessageDirectly(new ChatMessage(UUID.randomUUID().toString(), "alice", null, "Lobby",
                        "Chào cả nhóm! Giao diện DAMMTP2P Mesh Workspace mới nhìn cực kỳ đẳng cấp và mang bản sắc riêng biệt! Tham khảo mã nguồn dự án tại: https://github.com/Dungdebug/23DTHB4-N5-LTMMT/releases", now - 45000, false, true, null));
                frame.postChatMessageDirectly(new ChatMessage(UUID.randomUUID().toString(), "bob", null, "Lobby",
                        "Mình vừa đính kèm file báo cáo đồ án Mạng Máy Tính lên phòng, mọi người tải về test tốc độ truyền qua Port 8889 nhé!", now - 20000, false, true, null));

                // Add sample File Attachment Card
                FileMetadata docMeta = new FileMetadata(UUID.randomUUID().toString(), "BaoCao_DAMMTP2P_Nhom05.pdf",
                        18454930L, "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                        0L, "bob", "Lobby", now - 15000, true, null, null);

                // Add attachment to timeline
                ConversationTimeline timeline = frame.getTimelineForTesting("room:lobby");
                if (timeline != null) {
                    timeline.addFileTransfer(docMeta, true, new ChatMessageView.FileActionListener() {
                        @Override public void onAcceptFile(FileMetadata m) {}
                        @Override public void onRejectFile(FileMetadata m) {}
                        @Override public void onOpenFile(File f) {}
                    });
                }

                // Add response message from local user
                frame.postChatMessageDirectly(new ChatMessage(UUID.randomUUID().toString(), "DuongThai", null, "Lobby",
                        "Tuyệt vời! Thanh điều hướng segmented, top application bar và drawer thành viên hoạt động rất mượt mà. Hệ thống truyền tệp đã được tách ra trung tâm riêng biệt, khung chat hoàn toàn thoáng đãng.", now - 5000, true, true, null));

                // Activate Voice RTC dock in sidebar to test voice UI state
                if (frame.getSidebarForTesting() != null) {
                    frame.getSidebarForTesting().setVoiceState(true, "Phòng Thảo Luận 01");
                }

                frame.setSize(1280, 720);
                frame.doLayout();
                layoutRecursively(frame);
                if (timeline != null) {
                    timeline.validate();
                    javax.swing.JScrollBar sb = timeline.getScrollPane().getVerticalScrollBar();
                    if (sb != null) {
                        sb.setValue(0);
                    }
                }
                frame.doLayout();
                layoutRecursively(frame);

                // 2A. Capture Main Workspace Shell at 1280x720
                captureComponent(frame, 1280, 720, new File(dir, "mesh_main_workspace.png"));
                captureComponent(frame, 1280, 720, new File(dir, "workspace_main_chat.png"));
                captureComponent(frame, 1280, 720, new File(dir, "mesh_main_workspace_1280x720.png"));
                System.out.println("[CAPTURED] mesh_main_workspace.png (1280x720)");

                // 2B. Capture Main Workspace Shell at 1067x800
                frame.setSize(1067, 800);
                frame.doLayout();
                layoutRecursively(frame);
                if (timeline != null) {
                    timeline.validate();
                    javax.swing.JScrollBar sb = timeline.getScrollPane().getVerticalScrollBar();
                    if (sb != null) {
                        sb.setValue(0);
                    }
                }
                captureComponent(frame, 1067, 800, new File(dir, "mesh_main_workspace_1067x800.png"));
                System.out.println("[CAPTURED] mesh_main_workspace_1067x800.png (1067x800)");

                // 3. Capture Dedicated Transfers Center Dialog
                TransferCenterDialog transfersDialog = new TransferCenterDialog(frame, null);
                transfersDialog.setSize(860, 560);
                transfersDialog.updateTelemetry(10737418240L, 21474836480L, 26843545.6, "Chờ nhận đủ byte (50%)");
                transfersDialog.addOrUpdateTransferRow("91d8b9f7", "BaoCao_DAMMTP2P_Nhom05.pdf", 18454930L, "bob", 100, "Hoàn tất ✓ (Đã xác minh SHA-256)");
                transfersDialog.addOrUpdateTransferRow("4a2e8c11", "Database_MMT_Backup.tar.gz", 21474836480L, "charlie", 50, "Đang truyền 50% (25.6 MB/s)");
                transfersDialog.doLayout();
                layoutRecursively(transfersDialog);

                captureComponent(transfersDialog, 860, 560, new File(dir, "mesh_transfers_center.png"));
                captureComponent(transfersDialog, 860, 560, new File(dir, "workspace_transfers_dialog.png"));
                System.out.println("[CAPTURED] mesh_transfers_center.png & workspace_transfers_dialog.png");

                frame.dispose();
                transfersDialog.dispose();
            } catch (Exception e) {
                e.printStackTrace();
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

    private static void captureComponent(Component comp, int width, int height, File dest) throws Exception {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = img.createGraphics();
        g2.setColor(ThemeManager.BG_DARK);
        g2.fillRect(0, 0, width, height);

        if (comp instanceof javax.swing.JFrame) {
            javax.swing.JFrame f = (javax.swing.JFrame) comp;
            f.addNotify();
            f.setSize(width, height);
            f.validate();
            f.getRootPane().setBounds(0, 0, width, height);
            f.getRootPane().validate();
            f.getRootPane().printAll(g2);
        } else if (comp instanceof javax.swing.JDialog) {
            javax.swing.JDialog d = (javax.swing.JDialog) comp;
            d.addNotify();
            d.setSize(width, height);
            d.validate();
            d.getRootPane().setBounds(0, 0, width, height);
            d.getRootPane().validate();
            d.getRootPane().printAll(g2);
        } else {
            comp.setSize(width, height);
            comp.validate();
            comp.printAll(g2);
        }
        g2.dispose();
        ImageIO.write(img, "PNG", dest);
    }
}