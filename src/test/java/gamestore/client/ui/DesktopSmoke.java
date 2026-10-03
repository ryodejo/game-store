package gamestore.client.ui;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Optional real desktop launch check, separate from headless JUnit tests. */
public final class DesktopSmoke {
    private DesktopSmoke() {}
    public static void main(String[] args) throws Exception {
        if (GraphicsEnvironment.isHeadless()) throw new IllegalStateException("Desktop session required");
        Path folder = Path.of("target","ui-previews","desktop-" + System.getProperty("flatlaf.uiScale","100%")); Files.createDirectories(folder);
        Main.main(new String[0]);
        SwingUtilities.invokeAndWait(() -> {
            try {
                LoginFrame frame = null;
                for (Window window : Window.getWindows()) if (window instanceof LoginFrame login && login.isShowing()) frame = login;
                if (frame == null) throw new IllegalStateException("Login window did not open");
                BufferedImage image = new BufferedImage(frame.getWidth(),frame.getHeight(),BufferedImage.TYPE_INT_RGB);
                Graphics2D graphics = image.createGraphics();
                try { frame.printAll(graphics); ImageIO.write(image,"png",folder.resolve("desktop-login.png").toFile()); }
                finally { graphics.dispose(); }
                JFrame store = new JFrame("Game Store · Проверка интерфейса");
                StoreShell shell = new StoreShell(new gamestore.client.Client("127.0.0.1",1),new gamestore.shared.Models.User(1,"preview","ADMIN",java.math.BigDecimal.ZERO),() -> {});
                store.add(shell); store.setSize(1000,700); store.setLocationRelativeTo(null); store.setVisible(true); store.validate();
                BufferedImage desktop = new BufferedImage(store.getWidth(),store.getHeight(),BufferedImage.TYPE_INT_RGB); Graphics2D canvas = desktop.createGraphics();
                try { store.printAll(canvas); ImageIO.write(desktop,"png",folder.resolve("desktop-store.png").toFile()); } finally { canvas.dispose(); }
            } catch (Exception e) { throw new RuntimeException(e); }
            finally { for (Window window : Window.getWindows()) window.dispose(); }
        });
        System.out.println("DESKTOP_SMOKE_OK: packaged Main opened and rendered LoginFrame; windows closed.");
    }
}
