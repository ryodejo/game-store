package gamestore.server;

import gamestore.shared.AvatarImages;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AvatarImagesTest {
    @Test void cropUsesCenteredSquareAndDoesNotStretchWideImage() throws Exception {
        BufferedImage wide = new BufferedImage(600,300,BufferedImage.TYPE_INT_RGB); Graphics2D g = wide.createGraphics();
        try { g.setColor(Color.BLUE); g.fillRect(0,0,600,300); g.setColor(Color.RED); g.fillRect(150,0,300,300); } finally { g.dispose(); }
        ByteArrayOutputStream out = new ByteArrayOutputStream(); ImageIO.write(wide,"png",out);
        BufferedImage cropped = ImageIO.read(new ByteArrayInputStream(AvatarImages.prepare(out.toByteArray())));
        assertEquals(256,cropped.getWidth()); assertEquals(256,cropped.getHeight()); assertEquals(Color.RED.getRGB(),cropped.getRGB(0,128)); assertEquals(Color.RED.getRGB(),cropped.getRGB(255,128));
    }
}
