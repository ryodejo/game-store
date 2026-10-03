package gamestore.client.ui;

import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.Locale;
import javax.swing.*;

final class AvatarView extends JComponent {
    private BufferedImage image;
    private String initials = "GS";
    AvatarView() { setPreferredSize(new Dimension(Ui.scale(80),Ui.scale(80))); }
    void display(BufferedImage image,String name) {
        this.image = image; String[] parts = name.strip().split("\\s+");
        initials = first(parts[0]) + (parts.length > 1 ? first(parts[parts.length - 1]) : ""); initials = initials.toUpperCase(Locale.ROOT); repaint();
    }
    private static String first(String text) { return text.isEmpty() ? "" : new String(Character.toChars(text.codePointAt(0))); }
    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D)graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON); int size = Math.min(getWidth(),getHeight());
            g.setColor(new Color(0x35404D)); g.fillOval(0,0,size,size);
            if (image != null) { g.clip(new Ellipse2D.Double(0,0,size,size)); g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC); g.drawImage(image,0,0,size,size,null); }
            else { g.setColor(Ui.TEXT); g.setFont(new Font("Segoe UI",Font.PLAIN,Ui.scale(27))); FontMetrics metrics = g.getFontMetrics(); g.drawString(initials,(size - metrics.stringWidth(initials)) / 2,(size - metrics.getHeight()) / 2 + metrics.getAscent()); }
        } finally { g.dispose(); }
    }
}
