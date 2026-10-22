package dammtp2p.ui.theme;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.GeneralPath;
import java.awt.geom.RoundRectangle2D;
import javax.swing.Icon;

/**
 * Provides clean, crisp vector-based icons rendered with Java2D anti-aliasing.
 * Automatically scales to high-DPI displays without blurriness.
 */
public final class AppIcons {

    private AppIcons() {
    }

    /**
     * Renders a vector search / magnifying glass icon.
     */
    public static Icon createSearchIcon(int size, Color color) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(1.6f));

                int r = (int) (size * 0.32);
                int cx = x + (int) (size * 0.38);
                int cy = y + (int) (size * 0.38);
                g2.drawOval(cx - r, cy - r, r * 2, r * 2);

                int hx = cx + (int) (r * 0.7);
                int hy = cy + (int) (r * 0.7);
                int ex = x + size - 2;
                int ey = y + size - 2;
                g2.drawLine(hx, hy, ex, ey);

                g2.dispose();
            }

            @Override public int getIconWidth() { return size; }
            @Override public int getIconHeight() { return size; }
        };
    }

    /**
     * Renders a circular status dot (e.g. green for connected, red for disconnected).
     */
    public static Icon createStatusDot(int size, Color color) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.fillOval(x + 1, y + 1, size - 2, size - 2);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

    /**
     * Renders a document/file icon with folded corner.
     */
    public static Icon createFileIcon(int size, Color color) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(1.5f));

                int w = (int) (size * 0.7);
                int h = (int) (size * 0.9);
                int startX = x + (size - w) / 2;
                int startY = y + (size - h) / 2;
                int fold = (int) (w * 0.35);

                GeneralPath path = new GeneralPath();
                path.moveTo(startX, startY);
                path.lineTo(startX + w - fold, startY);
                path.lineTo(startX + w, startY + fold);
                path.lineTo(startX + w, startY + h);
                path.lineTo(startX, startY + h);
                path.closePath();
                g2.draw(path);

                // Fold line
                g2.drawLine(startX + w - fold, startY, startX + w - fold, startY + fold);
                g2.drawLine(startX + w - fold, startY + fold, startX + w, startY + fold);

                // Content lines
                g2.drawLine(startX + 3, startY + h / 2, startX + w - 3, startY + h / 2);
                g2.drawLine(startX + 3, startY + h / 2 + 3, startX + w - 5, startY + h / 2 + 3);

                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

    /**
     * Renders a folder icon.
     */
    public static Icon createFolderIcon(int size, Color color) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(1.5f));

                int w = (int) (size * 0.85);
                int h = (int) (size * 0.65);
                int sx = x + (size - w) / 2;
                int sy = y + (size - h) / 2;

                GeneralPath path = new GeneralPath();
                path.moveTo(sx, sy + 3);
                path.lineTo(sx + w * 0.35f, sy + 3);
                path.lineTo(sx + w * 0.45f, sy + 6);
                path.lineTo(sx + w, sy + 6);
                path.lineTo(sx + w, sy + h);
                path.lineTo(sx, sy + h);
                path.closePath();

                g2.draw(path);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

    /**
     * Renders a paper-plane / send icon.
     */
    public static Icon createSendIcon(int size, Color color) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(1.5f));

                float pad = size * 0.15f;
                GeneralPath path = new GeneralPath();
                path.moveTo(x + pad, y + pad);
                path.lineTo(x + size - pad, y + size / 2.0f);
                path.lineTo(x + pad, y + size - pad);
                path.lineTo(x + pad + size * 0.25f, y + size / 2.0f);
                path.closePath();

                g2.fill(path);
                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

    /**
     * Renders a copy icon (two overlapping rounded rectangles).
     */
    public static Icon createCopyIcon(int size, Color color) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(1.4f));

                int s = (int) (size * 0.6);
                // Back rect
                g2.drawRoundRect(x + size - s - 1, y + 1, s, s, 3, 3);
                // Front rect
                g2.setColor(ThemeManager.isDarkMode() ? ThemeManager.BG_PANEL : Color.WHITE);
                g2.fillRoundRect(x + 1, y + size - s - 1, s, s, 3, 3);
                g2.setColor(color);
                g2.drawRoundRect(x + 1, y + size - s - 1, s, s, 3, 3);

                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

    /**
     * Renders a checkmark icon.
     */
    public static Icon createCheckIcon(int size, Color color) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                int p = (int) (size * 0.2);
                GeneralPath path = new GeneralPath();
                path.moveTo(x + p, y + size * 0.55f);
                path.lineTo(x + size * 0.42f, y + size - p);
                path.lineTo(x + size - p, y + p + 1);
                g2.draw(path);

                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

    /**
     * Renders an alert / warning triangle icon.
     */
    public static Icon createAlertIcon(int size, Color color) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                GeneralPath path = new GeneralPath();
                path.moveTo(x + size / 2.0f, y + 2);
                path.lineTo(x + size - 2, y + size - 2);
                path.lineTo(x + 2, y + size - 2);
                path.closePath();
                g2.draw(path);

                // Exclamation mark
                g2.drawLine(x + size / 2, y + 6, x + size / 2, y + size - 7);
                g2.fillOval(x + size / 2 - 1, y + size - 5, 2, 2);

                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

    /**
     * Renders a gear / settings icon.
     */
    public static Icon createSettingsIcon(int size, Color color) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(1.8f));

                int r = (int) (size * 0.32);
                int cx = x + size / 2;
                int cy = y + size / 2;

                g2.drawOval(cx - r, cy - r, r * 2, r * 2);
                // 4 cogs
                g2.drawLine(cx, cy - r - 2, cx, cy - r + 1);
                g2.drawLine(cx, cy + r - 1, cx, cy + r + 2);
                g2.drawLine(cx - r - 2, cy, cx - r + 1, cy);
                g2.drawLine(cx + r - 1, cy, cx + r + 2, cy);

                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

    /**
     * Renders a network nodes / peer-to-peer mesh icon.
     */
    public static Icon createNetworkIconSafe(int size, Color color) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(1.6f));

                int nodeR = Math.max(2, size / 6);
                int topX = x + size / 2;
                int topY = y + nodeR + 2;
                int leftX = x + nodeR + 2;
                int leftY = y + size - nodeR - 2;
                int rightX = x + size - nodeR - 2;
                int rightY = y + size - nodeR - 2;

                // Connecting lines
                g2.drawLine(topX, topY, leftX, leftY);
                g2.drawLine(topX, topY, rightX, rightY);
                g2.drawLine(leftX, leftY, rightX, rightY);

                // Nodes
                g2.fillOval(topX - nodeR, topY - nodeR, nodeR * 2, nodeR * 2);
                g2.fillOval(leftX - nodeR, leftY - nodeR, nodeR * 2, nodeR * 2);
                g2.fillOval(rightX - nodeR, rightY - nodeR, nodeR * 2, nodeR * 2);

                g2.dispose();
            }

            @Override
            public int getIconWidth() {
                return size;
            }

            @Override
            public int getIconHeight() {
                return size;
            }
        };
    }

    /**
     * Renders DAMMTP2P distinctive Brand Emblem: Diamond Mesh Constellation with central core.
     */
    public static Icon createMeshLogo(int size, Color mainTeal, Color accentCyan) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int cx = x + size / 2;
                int cy = y + size / 2;
                int r = (int) (size * 0.38);

                // Coordinates for 4 outer diamond nodes
                int topX = cx, topY = cy - r;
                int rightX = cx + r, rightY = cy;
                int bottomX = cx, bottomY = cy + r;
                int leftX = cx - r, leftY = cy;

                // Connecting mesh lines
                g2.setColor(new Color(mainTeal.getRed(), mainTeal.getGreen(), mainTeal.getBlue(), 160));
                g2.setStroke(new BasicStroke(1.8f));
                g2.drawLine(topX, topY, rightX, rightY);
                g2.drawLine(rightX, rightY, bottomX, bottomY);
                g2.drawLine(bottomX, bottomY, leftX, leftY);
                g2.drawLine(leftX, leftY, topX, topY);

                // Internal cross-links to center
                g2.setColor(new Color(accentCyan.getRed(), accentCyan.getGreen(), accentCyan.getBlue(), 120));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawLine(cx, cy, topX, topY);
                g2.drawLine(cx, cy, rightX, rightY);
                g2.drawLine(cx, cy, bottomX, bottomY);
                g2.drawLine(cx, cy, leftX, leftY);

                // Outer nodes
                int nodeR = Math.max(3, size / 9);
                g2.setColor(mainTeal);
                g2.fillOval(topX - nodeR, topY - nodeR, nodeR * 2, nodeR * 2);
                g2.fillOval(rightX - nodeR, rightY - nodeR, nodeR * 2, nodeR * 2);
                g2.fillOval(bottomX - nodeR, bottomY - nodeR, nodeR * 2, nodeR * 2);
                g2.fillOval(leftX - nodeR, leftY - nodeR, nodeR * 2, nodeR * 2);

                // Glowing central node
                int coreR = Math.max(4, (int) (size / 7.0));
                g2.setColor(accentCyan);
                g2.fillOval(cx - coreR, cy - coreR, coreR * 2, coreR * 2);

                g2.dispose();
            }

            @Override public int getIconWidth() { return size; }
            @Override public int getIconHeight() { return size; }
        };
    }

    /**
     * Renders a hashtag (#) icon for discussion text channels.
     */
    public static Icon createHashIcon(int size, Color color) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                float pad = size * 0.18f;
                float w = size - pad * 2;
                // Vertical slanted lines
                float x1 = x + pad + w * 0.35f;
                float x2 = x + pad + w * 0.65f;
                g2.drawLine((int) (x1 + 2), (int) (y + pad), (int) (x1 - 2), (int) (y + size - pad));
                g2.drawLine((int) (x2 + 2), (int) (y + pad), (int) (x2 - 2), (int) (y + size - pad));

                // Horizontal lines
                float y1 = y + pad + w * 0.35f;
                float y2 = y + pad + w * 0.65f;
                g2.drawLine((int) (x + pad), (int) y1, (int) (x + size - pad), (int) y1);
                g2.drawLine((int) (x + pad), (int) y2, (int) (x + size - pad), (int) y2);

                g2.dispose();
            }

            @Override public int getIconWidth() { return size; }
            @Override public int getIconHeight() { return size; }
        };
    }

    /**
     * Renders a speaker / voice channel icon with acoustic waves.
     */
    public static Icon createVoiceWaveIcon(int size, Color color) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);

                // Speaker body
                GeneralPath body = new GeneralPath();
                body.moveTo(x + size * 0.15f, y + size * 0.38f);
                body.lineTo(x + size * 0.35f, y + size * 0.38f);
                body.lineTo(x + size * 0.55f, y + size * 0.20f);
                body.lineTo(x + size * 0.55f, y + size * 0.80f);
                body.lineTo(x + size * 0.35f, y + size * 0.62f);
                body.lineTo(x + size * 0.15f, y + size * 0.62f);
                body.closePath();
                g2.fill(body);

                // Sound waves
                g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawArc((int) (x + size * 0.45f), (int) (y + size * 0.32f), (int) (size * 0.28f), (int) (size * 0.36f), -45, 90);
                g2.drawArc((int) (x + size * 0.50f), (int) (y + size * 0.22f), (int) (size * 0.40f), (int) (size * 0.56f), -45, 90);

                g2.dispose();
            }

            @Override public int getIconWidth() { return size; }
            @Override public int getIconHeight() { return size; }
        };
    }

    /**
     * Renders a plus (+) icon.
     */
    public static Icon createPlusIcon(int size, Color color) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                int midX = x + size / 2;
                int midY = y + size / 2;
                int arm = (int) (size * 0.32);
                g2.drawLine(midX - arm, midY, midX + arm, midY);
                g2.drawLine(midX, midY - arm, midX, midY + arm);

                g2.dispose();
            }

            @Override public int getIconWidth() { return size; }
            @Override public int getIconHeight() { return size; }
        };
    }

    /**
     * Renders a chat bubble / direct message icon.
     */
    public static Icon createChatBubbleIcon(int size, Color color) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                int w = (int) (size * 0.75);
                int h = (int) (size * 0.55);
                int sx = x + (size - w) / 2;
                int sy = y + (int) (size * 0.18);

                g2.drawRoundRect(sx, sy, w, h, 8, 8);
                // Tail
                GeneralPath tail = new GeneralPath();
                tail.moveTo(sx + w * 0.25f, sy + h);
                tail.lineTo(sx + w * 0.15f, sy + h + size * 0.18f);
                tail.lineTo(sx + w * 0.45f, sy + h);
                g2.draw(tail);

                g2.dispose();
            }

            @Override public int getIconWidth() { return size; }
            @Override public int getIconHeight() { return size; }
        };
    }

    /**
     * Renders a download / active transfers tray icon.
     */
    public static Icon createTransfersIcon(int size, Color color) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                int midX = x + size / 2;
                // Down arrow
                g2.drawLine(midX, (int) (y + size * 0.15), midX, (int) (y + size * 0.62));
                g2.drawLine((int) (midX - size * 0.2), (int) (y + size * 0.45), midX, (int) (y + size * 0.62));
                g2.drawLine((int) (midX + size * 0.2), (int) (y + size * 0.45), midX, (int) (y + size * 0.62));

                // Tray base
                int bx = (int) (x + size * 0.15);
                int by = (int) (y + size * 0.78);
                int bw = (int) (size * 0.7);
                g2.drawLine(bx, by - (int)(size * 0.12), bx, by);
                g2.drawLine(bx, by, bx + bw, by);
                g2.drawLine(bx + bw, by, bx + bw, by - (int)(size * 0.12));

                g2.dispose();
            }

            @Override public int getIconWidth() { return size; }
            @Override public int getIconHeight() { return size; }
        };
    }

    /**
     * Renders a users / group silhouette icon.
     */
    public static Icon createUsersIcon(int size, Color color) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);

                // Main user head & body
                int headR = (int) (size * 0.18);
                int cx = (int) (x + size * 0.42);
                int cy = (int) (y + size * 0.32);
                g2.fillOval(cx - headR, cy - headR, headR * 2, headR * 2);

                GeneralPath body = new GeneralPath();
                body.moveTo(cx - size * 0.28f, y + size * 0.82f);
                body.curveTo(cx - size * 0.28f, y + size * 0.55f, cx + size * 0.28f, y + size * 0.55f, cx + size * 0.28f, y + size * 0.82f);
                body.closePath();
                g2.fill(body);

                g2.dispose();
            }

            @Override public int getIconWidth() { return size; }
            @Override public int getIconHeight() { return size; }
        };
    }

    /**
     * Renders microphone icon (with strike-through slash if muted).
     */
    public static Icon createMicIcon(int size, Color color, boolean muted) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(muted ? ThemeManager.DANGER : color);

                int cx = x + size / 2;
                int w = (int) (size * 0.28);
                int h = (int) (size * 0.45);
                g2.fillRoundRect(cx - w / 2, (int) (y + size * 0.16), w, h, w, w);

                g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                // U-stand
                g2.drawArc((int) (cx - size * 0.24), (int) (y + size * 0.3), (int) (size * 0.48), (int) (size * 0.38), 0, -180);
                // Base
                g2.drawLine(cx, (int) (y + size * 0.68), cx, (int) (y + size * 0.85));
                g2.drawLine((int) (cx - size * 0.18), (int) (y + size * 0.85), (int) (cx + size * 0.18), (int) (y + size * 0.85));

                if (muted) {
                    g2.setColor(ThemeManager.DANGER);
                    g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawLine((int) (x + size * 0.15), (int) (y + size * 0.15), (int) (x + size * 0.85), (int) (y + size * 0.85));
                }

                g2.dispose();
            }

            @Override public int getIconWidth() { return size; }
            @Override public int getIconHeight() { return size; }
        };
    }

    /**
     * Renders headphone icon (with strike-through slash if deafened).
     */
    public static Icon createHeadphonesIcon(int size, Color color, boolean deafened) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(deafened ? ThemeManager.DANGER : color);
                g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                int cx = x + size / 2;
                int r = (int) (size * 0.32);
                // Band
                g2.drawArc(cx - r, (int) (y + size * 0.16), r * 2, r * 2, 0, 180);

                // Earcups
                int ew = (int) (size * 0.18);
                int eh = (int) (size * 0.32);
                g2.fillRoundRect((int) (cx - r - ew / 2), (int) (y + size * 0.42), ew, eh, 4, 4);
                g2.fillRoundRect((int) (cx + r - ew / 2), (int) (y + size * 0.42), ew, eh, 4, 4);

                if (deafened) {
                    g2.setColor(ThemeManager.DANGER);
                    g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawLine((int) (x + size * 0.15), (int) (y + size * 0.15), (int) (x + size * 0.85), (int) (y + size * 0.85));
                }

                g2.dispose();
            }

            @Override public int getIconWidth() { return size; }
            @Override public int getIconHeight() { return size; }
        };
    }

    /**
     * Renders a close / disconnect (X) icon.
     */
    public static Icon createCloseIcon(int size, Color color) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

                float p = size * 0.25f;
                g2.drawLine((int) (x + p), (int) (y + p), (int) (x + size - p), (int) (y + size - p));
                g2.drawLine((int) (x + size - p), (int) (y + p), (int) (x + p), (int) (y + size - p));

                g2.dispose();
            }

            @Override public int getIconWidth() { return size; }
            @Override public int getIconHeight() { return size; }
        };
    }

    /**
     * Renders an encryption / lock icon.
     */
    public static Icon createLockIcon(int size, Color color) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);

                int cx = x + size / 2;
                int bw = (int) (size * 0.65);
                int bh = (int) (size * 0.48);
                int bx = cx - bw / 2;
                int by = (int) (y + size * 0.44);

                // Body
                g2.fillRoundRect(bx, by, bw, bh, 4, 4);

                // Shackle
                g2.setStroke(new BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int sw = (int) (bw * 0.58);
                int sh = (int) (size * 0.32);
                g2.drawArc(cx - sw / 2, (int) (y + size * 0.16), sw, sh * 2, 0, 180);

                // Keyhole
                g2.setColor(ThemeManager.BG_DARK);
                g2.fillOval(cx - 2, by + bh / 3, 4, 4);
                g2.fillRect(cx - 1, by + bh / 3 + 2, 2, 4);

                g2.dispose();
            }

            @Override public int getIconWidth() { return size; }
            @Override public int getIconHeight() { return size; }
        };
    }

    /**
     * Renders a 3-dots vertical "more options" icon.
     */
    public static Icon createMoreDotsIcon(int size, Color color) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                int cx = x + size / 2;
                int r = Math.max(2, size / 8);
                int gap = (int) (size * 0.32);
                g2.fillOval(cx - r, y + size / 2 - gap - r, r * 2, r * 2);
                g2.fillOval(cx - r, y + size / 2 - r, r * 2, r * 2);
                g2.fillOval(cx - r, y + size / 2 + gap - r, r * 2, r * 2);
                g2.dispose();
            }

            @Override public int getIconWidth() { return size; }
            @Override public int getIconHeight() { return size; }
        };
    }

    /**
     * Renders a crisp vector "X" close icon without relying on Unicode fonts.
     */
    public static Icon createCloseIconSafe(int size, Color color) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                g2.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int pad = (int) (size * 0.25);
                g2.drawLine(x + pad, y + pad, x + size - pad, y + size - pad);
                g2.drawLine(x + size - pad, y + pad, x + pad, y + size - pad);
                g2.dispose();
            }

            @Override public int getIconWidth() { return size; }
            @Override public int getIconHeight() { return size; }
        };
    }

    /**
     * Renders an Admin Crown / Star icon.
     */
    public static Icon createCrownIcon(int size, Color color) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);

                GeneralPath crown = new GeneralPath();
                int pad = (int) (size * 0.15);
                int b = y + size - pad;
                int t = y + pad;
                crown.moveTo(x + pad, b);
                crown.lineTo(x + pad, t + (b - t) / 3);
                crown.lineTo(x + size * 0.35f, t + (b - t) / 1.8f);
                crown.lineTo(x + size / 2.0f, t);
                crown.lineTo(x + size * 0.65f, t + (b - t) / 1.8f);
                crown.lineTo(x + size - pad, t + (b - t) / 3);
                crown.lineTo(x + size - pad, b);
                crown.closePath();
                g2.fill(crown);

                g2.dispose();
            }

            @Override public int getIconWidth() { return size; }
            @Override public int getIconHeight() { return size; }
        };
    }

    /**
     * Renders animated-look audio equalizer bars for active Voice RTC channels.
     */
    public static Icon createAudioBarsIcon(int size, Color color) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                int bw = 2;
                int baseY = y + size - 2;
                // Bar 1
                g2.fillRect(x + 2, baseY - 6, bw, 6);
                // Bar 2
                g2.fillRect(x + 6, baseY - 11, bw, 11);
                // Bar 3
                g2.fillRect(x + 10, baseY - 8, bw, 8);
                // Bar 4
                g2.fillRect(x + 14, baseY - 13, bw, 13);
                g2.dispose();
            }

            @Override public int getIconWidth() { return size; }
            @Override public int getIconHeight() { return size; }
        };
    }

    /**
     * Renders a 5-pointed star vector icon.
     */
    public static Icon createStarIcon(int size, Color color) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                int midX = x + size / 2;
                int midY = y + size / 2;
                int rOut = size / 2 - 1;
                int rIn = Math.max(2, rOut / 2);
                GeneralPath star = new GeneralPath();
                for (int i = 0; i < 10; i++) {
                    double angle = i * Math.PI / 5 - Math.PI / 2;
                    double r = (i % 2 == 0) ? rOut : rIn;
                    double px = midX + r * Math.cos(angle);
                    double py = midY + r * Math.sin(angle);
                    if (i == 0) star.moveTo(px, py);
                    else star.lineTo(px, py);
                }
                star.closePath();
                g2.fill(star);
                g2.dispose();
            }

            @Override public int getIconWidth() { return size; }
            @Override public int getIconHeight() { return size; }
        };
    }

    /**
     * Renders a heart vector icon.
     */
    public static Icon createHeartIcon(int size, Color color) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                int w = size - 2;
                int h = size - 2;
                GeneralPath heart = new GeneralPath();
                heart.moveTo(x + 1 + w / 2f, y + 1 + h / 4f);
                heart.curveTo(x + 1 + w / 2f, y + 1, x + 1, y + 1, x + 1, y + 1 + h / 3f);
                heart.curveTo(x + 1, y + 1 + h * 0.6f, x + 1 + w / 2f, y + 1 + h * 0.85f, x + 1 + w / 2f, y + 1 + h);
                heart.curveTo(x + 1 + w / 2f, y + 1 + h * 0.85f, x + 1 + w, y + 1 + h * 0.6f, x + 1 + w, y + 1 + h / 3f);
                heart.curveTo(x + 1 + w, y + 1, x + 1 + w / 2f, y + 1, x + 1 + w / 2f, y + 1 + h / 4f);
                heart.closePath();
                g2.fill(heart);
                g2.dispose();
            }

            @Override public int getIconWidth() { return size; }
            @Override public int getIconHeight() { return size; }
        };
    }

    /**
     * Renders a lightning bolt vector icon.
     */
    public static Icon createBoltIcon(int size, Color color) {
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(color);
                GeneralPath bolt = new GeneralPath();
                bolt.moveTo(x + size * 0.55f, y + 1);
                bolt.lineTo(x + size * 0.15f, y + size * 0.55f);
                bolt.lineTo(x + size * 0.45f, y + size * 0.55f);
                bolt.lineTo(x + size * 0.35f, y + size - 1);
                bolt.lineTo(x + size * 0.85f, y + size * 0.42f);
                bolt.lineTo(x + size * 0.55f, y + size * 0.42f);
                bolt.closePath();
                g2.fill(bolt);
                g2.dispose();
            }

            @Override public int getIconWidth() { return size; }
            @Override public int getIconHeight() { return size; }
        };
    }
}

