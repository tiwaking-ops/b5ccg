package b5ccg.ui.theme;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;

/** Single source of truth for colours, fonts and spacing. Java 6, stdlib only. */
public final class Theme {
    private Theme() { }

    // Surfaces
    public static final Color BG_APP = new Color(12, 16, 24);
    public static final Color BG_PANEL = new Color(20, 27, 38);
    public static final Color BG_RAISED = new Color(32, 43, 58);
    public static final Color LINE = new Color(56, 72, 92);
    public static final Color LINE_BRIGHT = new Color(96, 120, 148);

    // Text
    public static final Color TEXT = new Color(214, 224, 236);
    public static final Color TEXT_DIM = new Color(140, 156, 174);

    // Semantic
    public static final Color ACCENT = new Color(240, 170, 60);
    public static final Color OK = new Color(90, 200, 120);
    public static final Color DANGER = new Color(224, 86, 74);
    public static final Color INFO = new Color(92, 160, 232);
    public static final Color SELECT = new Color(170, 230, 80);

    // Fonts (11pt UI floor, 9pt absolute minimum on cards)
    public static final Font FONT_BASE = new Font("SansSerif", Font.PLAIN, 12);
    public static final Font FONT_BOLD = new Font("SansSerif", Font.BOLD, 12);
    public static final Font FONT_SMALL = new Font("SansSerif", Font.PLAIN, 11);
    public static final Font FONT_SMALL_BOLD = new Font("SansSerif", Font.BOLD, 11);
    public static final Font FONT_TITLE = new Font("SansSerif", Font.BOLD, 14);
    public static final Font FONT_MONO = new Font("Monospaced", Font.PLAIN, 11);

    // Spacing
    public static final int GAP = 6;
    public static final int PAD = 8;
    public static final int MIN_HIT = 24;

    public static Border pad(int n) {
        return BorderFactory.createEmptyBorder(n, n, n, n);
    }

    public static Border panelBorder(String title) {
        TitledBorder tb = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(LINE), title);
        tb.setTitleFont(FONT_SMALL_BOLD);
        tb.setTitleColor(TEXT_DIM);
        return tb;
    }

    /** Card-type header colour. Pair with a text label; never rely on colour alone. */
    public static Color cardTypeColor(String type) {
        if ("CHARACTER".equals(type)) { return new Color(74, 118, 196); }
        if ("FLEET".equals(type)) { return new Color(96, 170, 210); }
        if ("CONFLICT".equals(type)) { return new Color(196, 68, 64); }
        if ("AGENDA".equals(type)) { return new Color(220, 160, 60); }
        if ("AFTERMATH".equals(type)) { return new Color(140, 90, 180); }
        if ("EVENT".equals(type)) { return new Color(80, 160, 90); }
        if ("ENHANCEMENT".equals(type)) { return new Color(170, 170, 70); }
        if ("GROUP".equals(type)) { return new Color(150, 106, 64); }
        if ("LOCATION".equals(type)) { return new Color(60, 160, 140); }
        return LINE_BRIGHT;
    }

    /** Call once at the top of every custom paintComponent. */
    public static void quality(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }
}
