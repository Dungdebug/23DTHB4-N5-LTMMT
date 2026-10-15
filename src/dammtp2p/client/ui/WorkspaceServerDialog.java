package dammtp2p.client.ui;

import dammtp2p.server.ServerCore;
import dammtp2p.server.ui.ServerDashboardPanel;
import java.awt.Frame;
import javax.swing.JDialog;

/**
 * Server Administration & Relay Mission Control Dialog for DAMMTP2P Mesh.
 * Allows launching, monitoring, and inspecting the local central node.
 */
public class WorkspaceServerDialog extends JDialog {

    private final ServerDashboardPanel dashboardPanel;

    public WorkspaceServerDialog(Frame owner, ServerCore server) {
        super(owner, "🛰️ QUẢN TRỊ MÁY CHỦ TRUNG TÂM (SERVER MISSION CONTROL)", false);
        setSize(980, 680);
        setLocationRelativeTo(owner);

        dashboardPanel = new ServerDashboardPanel(server);
        setContentPane(dashboardPanel);
    }

    public ServerDashboardPanel getDashboardPanel() {
        return dashboardPanel;
    }
}
