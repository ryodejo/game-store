package gamestore.shared;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Locale;
import javax.imageio.*;
import javax.imageio.stream.MemoryCacheImageInputStream;

/** Bounded decode, centered square crop, canonical 256×256 PNG. No client paths or metadata survive. */
public final class AvatarImages {
    public static final int MAX_BYTES = 2 * 1024 * 1024;
    private AvatarImages() {}
    public static byte[] prepare(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES) throw invalid("Аватар: PNG/JPEG, файл до 2 МБ.");
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw invalid("Не удалось прочитать изображение PNG/JPEG.");
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("png") && !format.equals("jpeg")) throw invalid("Допускаются только PNG и JPEG.");
                reader.setInput(input,true,true);
                int width = reader.getWidth(0),height = reader.getHeight(0);
                if (width < 32 || height < 32 || width > 4096 || height > 4096 || (long)width * height > 16_000_000)
                    throw invalid("Размер изображения: от 32×32 до 4096×4096, не более 16 млн пикселей.");
                BufferedImage original = reader.read(0),result = new BufferedImage(256,256,BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = result.createGraphics();
                try {
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    int side = Math.min(width,height),x = (width - side) / 2,y = (height - side) / 2;
                    g.drawImage(original,0,0,256,256,x,y,x + side,y + side,null);
                } finally { g.dispose(); }
                ByteArrayOutputStream output = new ByteArrayOutputStream(); ImageIO.write(result,"png",output); return output.toByteArray();
            } finally { reader.dispose(); }
        } catch (IOException | RuntimeException e) {
            if (e instanceof IllegalArgumentException argument) throw argument;
            throw invalid("Повреждённое изображение PNG/JPEG.");
        }
    }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException(message); }
}
