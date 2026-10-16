package b5ccg.util;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class ImageCache {

    private static final int CARD_W = 200;
    private static final int CARD_H = 280;
    private static final ConcurrentHashMap<String, ImageIcon> cache = new ConcurrentHashMap<String, ImageIcon>();

    public static ImageIcon getCardImage(String imageKey) {
        ImageIcon cached = cache.get(imageKey);
        if (cached != null) return cached;
        ImageIcon loaded = load(imageKey);
        cache.put(imageKey, loaded);
        return loaded;
    }

    private static ImageIcon load(String key) {
        String norm = key.toLowerCase()
            .replaceAll("[^a-z0-9]", "_")
            .replaceAll("_+", "_")
            .replaceAll("^_|_$", "");

        for (String ext : new String[]{".jpg", ".png", ".gif"}) {
            URL url = ImageCache.class.getResource("/images/" + norm + ext);
            if (url != null) {
                ImageIcon raw = new ImageIcon(url);
                Image scaled  = raw.getImage()
                    .getScaledInstance(CARD_W, CARD_H, Image.SCALE_SMOOTH);
                return new ImageIcon(scaled);
            }
        }
        return makePlaceholder(key);
    }

    private static ImageIcon makePlaceholder(String key) {
        BufferedImage img = new BufferedImage(CARD_W, CARD_H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.setColor(new Color(25, 25, 55));
        g.fillRoundRect(0, 0, CARD_W, CARD_H, 14, 14);
        g.setColor(new Color(160, 130, 50));
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(1, 1, CARD_W - 2, CARD_H - 2, 14, 14);

        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 11));
        FontMetrics fm = g.getFontMetrics();
        String display = key.replace("_", " ");
        int ty = CARD_H / 2;
        for (String line : wrap(display, fm, CARD_W - 20)) {
            int x = (CARD_W - fm.stringWidth(line)) / 2;
            g.drawString(line, x, ty);
            ty += fm.getHeight();
        }
        g.dispose();
        return new ImageIcon(img);
    }

    private static java.util.List<String> wrap(String text, FontMetrics fm, int maxW) {
        java.util.List<String> lines = new java.util.ArrayList<String>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String test = line.length() == 0 ? word : line + " " + word;
            if (fm.stringWidth(test) <= maxW) {
                line = new StringBuilder(test);
            } else {
                if (line.length() > 0) lines.add(line.toString());
                line = new StringBuilder(word);
            }
        }
        if (line.length() > 0) lines.add(line.toString());
        return lines;
    }
}
