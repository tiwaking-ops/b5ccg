package b5ccg.ui.theme;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import javax.swing.JButton;

/** Paints itself, so it looks identical under any Look and Feel. */
public class ThemedButton extends JButton {
    public static final int NORMAL = 0;
    public static final int PRIMARY = 1;
    public static final int DANGER = 2;

    private final int kind;

    public ThemedButton(String text, int kind) {
        super(text);
        this.kind = kind;
        setFont(Theme.FONT_BOLD);
        setFocusPainted(false);
        setContentAreaFilled(false);
        setOpaque(false);
        setBorderPainted(false);
        setRolloverEnabled(true);
        setMargin(new Insets(6, 14, 6, 14));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        applyColors();
    }

    private void applyColors() {
        if (!isEnabled()) {
            setForeground(Theme.TEXT_DIM);
        } else if (kind == PRIMARY) {
            setForeground(Theme.BG_APP);
        } else {
            setForeground(Theme.TEXT);
        }
    }

    @Override
    public void setEnabled(boolean b) {
        super.setEnabled(b);
        applyColors();
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        Theme.quality(g2);
        Color base;
        if (kind == PRIMARY) {
            base = Theme.ACCENT;
        } else if (kind == DANGER) {
            base = Theme.DANGER;
        } else {
            base = Theme.BG_RAISED;
        }
        if (!isEnabled()) {
            base = Theme.BG_PANEL;
        } else if (getModel().isPressed()) {
            base = base.darker();
        } else if (getModel().isRollover()) {
            base = base.brighter();
        }
        g2.setColor(base);
        g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
        g2.setColor(isEnabled() ? Theme.LINE_BRIGHT : Theme.LINE);
        g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
        g2.dispose();
        super.paintComponent(g);
    }
}
