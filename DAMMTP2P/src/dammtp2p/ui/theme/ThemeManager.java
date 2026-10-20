package dammtp2p.ui.theme;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.Border;

/**
 * Manages desktop Look & Feel, color palette, dynamic multi-tone gradients,
 * and high-end component styling for DAMMTP2P.
 * Leverages FlatLaf (FlatDarkLaf / FlatLightLaf) with clean fallback to Nimbus/System.
 */
public final class ThemeManager {

    private static boolean isDark = true;

    // Theme Color Palette (DAMMTP2P "Connected Workspace" Palette - Distinctive Dark Slate & Graphite)
    public static final Color BG_DARK = new Color(0x13, 0x16, 0x1E);            // Main chat background (#13161E Deep Slate)
    public static final Color BG_SIDEBAR = new Color(0x18, 0x1C, 0x26);         // Workspace sidebar background (#181C26)
    public static final Color BG_NAV = new Color(0x0F, 0x11, 0x17);             // Brand bar / compact rail (#0F1117)
    public static final Color BG_PANEL = new Color(0x18, 0x1C, 0x26);           // Panel background (#181C26)
    public static final Color BG_CARD = new Color(0x1E, 0x23, 0x30);            // Card background (#1E2330)
    public static final Color BG_ELEVATED = new Color(0x23, 0x29, 0x38);        // Elevated surface / Composer (#232938)
    public static final Color BG_INPUT = new Color(0x18, 0x1C, 0x26);           // Input container (#181C26)
    public static final Color BG_HOVER = new Color(0x25, 0x2B, 0x3B);           // Hover state (#252B3B)
    public static final Color BG_ACTIVE = new Color(0x15, 0x38, 0x38);          // Active item subtle teal tint (#153838)
    public static final Color BORDER_COLOR = new Color(0x26, 0x2E, 0x3D);       // Border (#262E3D)
    public static final Color BORDER_SUBTLE = new Color(0x1E, 0x24, 0x30);      // Very subtle border (#1E2430)
    public static final Color BORDER_FOCUS = new Color(0x0D, 0x94, 0x88);       // Focused border Teal (#0D9488)
    public static final Color BG_CANVAS = BG_DARK;
    public static final Color BORDER_DEFAULT = BORDER_COLOR;

    // Brand & Accent Colors (DAMMTP2P Teal & Cyan Signature)
    public static final Color ACCENT_PRIMARY = new Color(0x0D, 0x94, 0x88);    // Signature Teal 600 (#0D9488)
    public static final Color ACCENT = ACCENT_PRIMARY;
    public static final Color ACCENT_HOVER = new Color(0x0F, 0x76, 0x6E);      // Deep Teal 700 (#0F766E)
    public static final Color ACCENT_CYAN = new Color(0x06, 0xB6, 0xD4);       // Cyan 500 (#06B6D4)
    public static final Color ACCENT_GRADIENT = new Color(0x0D, 0x94, 0x88);
    public static final Color ACCENT_GRADIENT_START = new Color(0x0D, 0x94, 0x88);
    public static final Color ACCENT_GRADIENT_END = new Color(0x14, 0xB8, 0xA6);
    public static final Color SUCCESS = new Color(0x10, 0xB9, 0x81);           // Emerald (#10B981)
    public static final Color SUCCESS_BG = new Color(0x06, 0x4E, 0x3B);
    public static final Color WARNING = new Color(0xF5, 0x9E, 0x0B);           // Amber (#F59E0B)
    public static final Color WARNING_BG = new Color(0x45, 0x2C, 0x07);
    public static final Color DANGER = new Color(0xEF, 0x44, 0x44);            // Rose/Red (#EF4444)
    public static final Color DANGER_BG = new Color(0x4C, 0x1D, 0x1D);
    public static final Color INFO = new Color(0x06, 0xB6, 0xD4);              // Cyan (#06B6D4)
    public static final Color STATUS_CONNECTED = SUCCESS;
    public static final Color STATUS_DISCONNECTED = DANGER;
    public static final Color STATUS_ERROR = DANGER;

    // Vibrant Modern Cyber Accents
    public static final Color ACCENT_ELECTRIC_CYAN = new Color(0x00, 0xF2, 0xFE);
    public static final Color ACCENT_TEAL_LIGHT = new Color(0x2D, 0xD4, 0xBF);
    public static final Color ACCENT_PURPLE = new Color(0xA8, 0x55, 0xF7);
    public static final Color ACCENT_VIOLET = new Color(0x63, 0x66, 0xF1);
    public static final Color ACCENT_PINK = new Color(0xEC, 0x48, 0x99);
    public static final Color ACCENT_AMBER = new Color(0xF5, 0x9E, 0x0B);

    // Text Colors
    public static final Color TEXT_PRIMARY = new Color(0xF8, 0xFA, 0xFC);     // Slate 50 (#F8FAFC)
    public static final Color TEXT_SECONDARY = new Color(0xCB, 0xD5, 0xE1);   // Slate 300 (#CBD5E1)
    public static final Color TEXT_MUTED = new Color(0x94, 0xA3, 0xB8);       // Slate 400 (#94A3B8)

    // Chat Bubble Colors
    public static final Color BUBBLE_LOCAL = new Color(0x0D, 0x94, 0x88);      // Teal bubble
    public static final Color BUBBLE_LOCAL_GRAD_END = new Color(0x14, 0xB8, 0xA6);
    public static final Color BUBBLE_REMOTE = new Color(0x1E, 0x23, 0x30);     // Slate card
    public static final Color BUBBLE_REMOTE_BORDER = new Color(0x26, 0x2E, 0x3D);
    public static final Color BUBBLE_SYSTEM = new Color(0x1E, 0x23, 0x30);

    // Dynamic Multi-Tone Avatar Gradients
    public static final Color[][] AVATAR_GRADIENTS = {
        { new Color(59, 130, 246), new Color(99, 102, 241) },   // Blue -> Indigo
        { new Color(16, 185, 129), new Color(13, 148, 136) },  // Emerald -> Teal
        { new Color(139, 92, 246), new Color(217, 70, 239) },  // Violet -> Fuchsia
        { new Color(245, 158, 11), new Color(234, 88, 12) },   // Amber -> Orange
        { new Color(244, 63, 94), new Color(225, 29, 72) },    // Rose -> Pink
        { new Color(6, 182, 212), new Color(59, 130, 246) },   // Cyan -> Blue
        { new Color(168, 85, 247), new Color(99, 102, 241) }   // Purple -> Indigo
    };

    // Standard Typography Hierarchy
    public static final Font FONT_DISPLAY = new Font("Segoe UI", Font.BOLD, 18);
    public static final Font FONT_TITLE = new Font("Segoe UI", Font.BOLD, 15);
    public static final Font FONT_HEADER = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font FONT_BODY = new Font("Segoe UI", Font.PLAIN, 13);
    public static final Font FONT_BODY_BOLD = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font FONT_SMALL = new Font("Segoe UI", Font.PLAIN, 11);
    public static final Font FONT_SMALL_BOLD = new Font("Segoe UI", Font.BOLD, 11);
    public static final Font FONT_MONO = new Font("Consolas", Font.PLAIN, 12);
    public static final Font FONT_MONO_BOLD = new Font("Consolas", Font.BOLD, 12);

    // Spacing System Tokens (Consistent 4, 8, 12, 16, 24, 32 px scale)
    public static final int SPACE_XS = 4;
    public static final int SPACE_SM = 8;
    public static final int SPACE_MD = 12;
    public static final int SPACE_LG = 16;
    public static final int SPACE_XL = 24;
    public static final int SPACE_2XL = 32;

    // Component Dimensions & Height Standards
    public static final int BTN_HEIGHT_SM = 28;
    public static final int BTN_HEIGHT_MD = 36;
    public static final int BTN_HEIGHT_LG = 42;
    public static final int INPUT_HEIGHT = 38;
    public static final int HEADER_HEIGHT = 52;
    public static final int SIDEBAR_WIDTH = 240;
    public static final int NAV_RAIL_WIDTH = 56;
    public static final int MEMBER_DRAWER_WIDTH = 220;
    public static final int CORNER_RADIUS = 12;
    public static final int CORNER_RADIUS_SM = 8;

    public static Color getUserColor(String name) {
        if (name == null || name.trim().isEmpty()) return TEXT_PRIMARY;
        int hash = Math.abs(name.hashCode());
        int paletteIdx = hash % AVATAR_GRADIENTS.length;
        return AVATAR_GRADIENTS[paletteIdx][0];
    }

    private ThemeManager() {
        // Prevent instantiation
    }

    /**
     * Initializes the application Look and Feel with modern styling.
     */
    public static void initializeTheme() {
        setupTheme();
    }

    public static void setupTheme() {
        try {
            // Try loading FlatDarkLaf
            Class<?> flatLafClass = Class.forName("com.formdev.flatlaf.FlatDarkLaf");
            java.lang.reflect.Method setupMethod = flatLafClass.getMethod("setup");
            setupMethod.invoke(null);
            isDark = true;

            // Configure FlatLaf UI properties for modern rounded geometry & high contrast
            UIManager.put("Button.arc", 12);
            UIManager.put("Component.arc", 12);
            UIManager.put("ProgressBar.arc", 14);
            UIManager.put("TextComponent.arc", 10);
            UIManager.put("ScrollBar.thumbArc", 8);
            UIManager.put("ScrollBar.width", 10);
            UIManager.put("Component.focusWidth", 1);
            UIManager.put("TabbedPane.showTabSeparators", true);
            UIManager.put("TabbedPane.tabHeight", 38);
            UIManager.put("TabbedPane.tabInsets", new Insets(6, 16, 6, 16));
            UIManager.put("TabbedPane.selectedBackground", BG_PANEL);
            UIManager.put("TabbedPane.underlineColor", ACCENT_PRIMARY);
            UIManager.put("Table.rowHeight", 32);
            UIManager.put("TableHeader.height", 36);
            UIManager.put("Panel.background", BG_PANEL);
            UIManager.put("SplitPane.dividerSize", 6);
            return;
        } catch (Throwable t) {
            // FlatLaf not found or failed, fallback to Nimbus or System
        }

        try {
            for (UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ex) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Toggles between Dark and Light mode if FlatLaf is available.
     */
    public static void toggleTheme() {
        try {
            String themeClassName = isDark
                    ? "com.formdev.flatlaf.FlatLightLaf"
                    : "com.formdev.flatlaf.FlatDarkLaf";
            Class<?> lafClass = Class.forName(themeClassName);
            java.lang.reflect.Method setupMethod = lafClass.getMethod("setup");
            setupMethod.invoke(null);
            isDark = !isDark;

            // Re-apply component arcs
            UIManager.put("Button.arc", 12);
            UIManager.put("Component.arc", 12);
            UIManager.put("ProgressBar.arc", 14);
            UIManager.put("TextComponent.arc", 10);
            UIManager.put("ScrollBar.thumbArc", 8);

            for (java.awt.Window window : java.awt.Window.getWindows()) {
                javax.swing.SwingUtilities.updateComponentTreeUI(window);
            }
        } catch (Throwable ignored) {
        }
    }

    public static boolean isDarkMode() {
        return isDark;
    }

    /**
     * Creates a card border with subtle outline and standard internal padding.
     */
    public static Border createCardBorder(int top, int left, int bottom, int right) {
        return BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER_COLOR, 1),
                BorderFactory.createEmptyBorder(top, left, bottom, right)
        );
    }

    /**
     * Creates a dashed border (e.g. for drag-and-drop zones).
     */
    public static Border createDashedBorder(Color color, int strokeWidth, float dashLength) {
        return BorderFactory.createDashedBorder(color, strokeWidth, dashLength, dashLength, true);
    }

    /**
     * Creates a pill badge label with rounded background.
     */
    public static JLabel createBadge(String text, Color fgColor, Color bgColor) {
        JLabel lbl = new JLabel(text, SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bgColor);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), getHeight(), getHeight());
                g2.setColor(new Color(fgColor.getRed(), fgColor.getGreen(), fgColor.getBlue(), 90));
                g2.setStroke(new BasicStroke(1.0f));
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, getHeight(), getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        lbl.setOpaque(false);
        lbl.setFont(FONT_SMALL);
        lbl.setForeground(fgColor);
        lbl.setBorder(BorderFactory.createEmptyBorder(3, 8, 3, 8));
        return lbl;
    }

    /**
     * Creates a dynamic multi-hue gradient avatar circle icon with user initial and optional online badge.
     */
    public static Icon createAvatarIcon(String name, int size, boolean isOnline) {
        return createAvatarIcon(name, size, isOnline, false);
    }

    public static Icon createAvatarIcon(String name, int size, boolean isOnline, boolean isSpeaking) {
        final String initial = (name != null && !name.trim().isEmpty())
                ? name.trim().substring(0, 1).toUpperCase() : "?";

        // Deterministic gradient selection based on name hash
        int hash = (name != null) ? Math.abs(name.hashCode()) : 0;
        int paletteIdx = hash % AVATAR_GRADIENTS.length;
        final Color startColor = AVATAR_GRADIENTS[paletteIdx][0];
        final Color endColor = AVATAR_GRADIENTS[paletteIdx][1];

        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

                // Speaking indicator glow / outer emerald ring
                if (isSpeaking) {
                    g2.setColor(SUCCESS);
                    g2.setStroke(new BasicStroke(2.5f));
                    g2.drawOval(x - 1, y - 1, size + 1, size + 1);
                }

                // Multi-tone 45-degree linear gradient fill
                LinearGradientPaint gradient = new LinearGradientPaint(
                        x, y, x + size, y + size,
                        new float[]{0.0f, 1.0f},
                        new Color[]{startColor, endColor}
                );
                g2.setPaint(gradient);
                g2.fillOval(x, y, size, size);

                // Inner rim lighting (subtle 1px translucent ring)
                g2.setColor(new Color(255, 255, 255, 45));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawOval(x + 1, y + 1, size - 2, size - 2);

                // Initial text
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Segoe UI", Font.BOLD, (int) (size * 0.52)));
                FontMetrics fm = g2.getFontMetrics();
                int tx = x + (size - fm.stringWidth(initial)) / 2;
                int ty = y + (size - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(initial, tx, ty);

                // Online status indicator ring at bottom right
                if (isOnline) {
                    int dotSize = Math.max(6, (int) (size * 0.32));
                    int dotX = x + size - dotSize;
                    int dotY = y + size - dotSize;

                    // Dark rim around dot
                    g2.setColor(BG_PANEL);
                    g2.fillOval(dotX - 1, dotY - 1, dotSize + 2, dotSize + 2);

                    // Emerald dot
                    g2.setColor(SUCCESS);
                    g2.fillOval(dotX, dotY, dotSize, dotSize);
                }

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
     * Overload: creates avatar icon without status dot.
     */
    public static Icon createAvatarIcon(String name, int size) {
        return createAvatarIcon(name, size, false);
    }

    /**
     * Backward-compatible avatar icon with fixed background color.
     */
    public static Icon createAvatarIcon(String name, int size, Color bgColor) {
        final String initial = (name != null && !name.trim().isEmpty())
                ? name.trim().substring(0, 1).toUpperCase() : "?";
        return new Icon() {
            @Override
            public void paintIcon(Component c, Graphics g, int x, int y) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g2.setColor(bgColor);
                g2.fillOval(x, y, size, size);

                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Segoe UI", Font.BOLD, (int) (size * 0.55)));
                FontMetrics fm = g2.getFontMetrics();
                int tx = x + (size - fm.stringWidth(initial)) / 2;
                int ty = y + (size - fm.getHeight()) / 2 + fm.getAscent();
                g2.drawString(initial, tx, ty);
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
     * Paints an ambient cyber-mesh dark background with subtle glowing gradient orbs.
     */
    public static void paintMeshBackground(Graphics2D g2, int width, int height) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        // Base dark slate gradient
        LinearGradientPaint baseGrad = new LinearGradientPaint(
                0, 0, 0, height,
                new float[]{0.0f, 1.0f},
                new Color[]{new Color(0x0E, 0x11, 0x18), new Color(0x13, 0x17, 0x22)}
        );
        g2.setPaint(baseGrad);
        g2.fillRect(0, 0, width, height);

        // Top-left ambient Teal glow
        int r1 = Math.max(300, (int) (width * 0.45));
        java.awt.RadialGradientPaint tealGlow = new java.awt.RadialGradientPaint(
                0, 0, r1,
                new float[]{0.0f, 0.7f, 1.0f},
                new Color[]{new Color(13, 148, 136, 45), new Color(13, 148, 136, 12), new Color(13, 148, 136, 0)}
        );
        g2.setPaint(tealGlow);
        g2.fillRect(0, 0, width, height);

        // Bottom-right ambient Indigo/Violet glow
        int r2 = Math.max(320, (int) (width * 0.48));
        java.awt.RadialGradientPaint violetGlow = new java.awt.RadialGradientPaint(
                width, height, r2,
                new float[]{0.0f, 0.7f, 1.0f},
                new Color[]{new Color(99, 102, 241, 40), new Color(99, 102, 241, 10), new Color(99, 102, 241, 0)}
        );
        g2.setPaint(violetGlow);
        g2.fillRect(0, 0, width, height);

        // Delicate grid lines with faint alpha
        g2.setColor(new Color(255, 255, 255, 6));
        int step = 32;
        for (int x = 0; x < width; x += step) {
            g2.drawLine(x, 0, x, height);
        }
        for (int y = 0; y < height; y += step) {
            g2.drawLine(0, y, width, y);
        }
    }

    /**
     * Creates a high-end rounded gradient button with rollover glow.
     */
    public static javax.swing.JButton createGradientButton(String text, Color start, Color end, Color textColor) {
        javax.swing.JButton btn = new javax.swing.JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean hover = getModel().isRollover();
                boolean pressed = getModel().isPressed();

                Color s = pressed ? start.darker() : (hover ? start.brighter() : start);
                Color e = pressed ? end.darker() : (hover ? end.brighter() : end);

                LinearGradientPaint gp = new LinearGradientPaint(
                        0, 0, getWidth(), getHeight(),
                        new float[]{0.0f, 1.0f},
                        new Color[]{s, e}
                );
                g2.setPaint(gp);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 12, 12);

                // Subtle inner top highlight
                g2.setColor(new Color(255, 255, 255, 50));
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 10, 10);

                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(FONT_BODY_BOLD);
        btn.setForeground(textColor);
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
        return btn;
    }
}
