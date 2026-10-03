package gamestore.client.ui;

import gamestore.client.Client;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SwingTest {
    @Test void resourceCoversAndMissingImageFallbackWork() throws Exception {
        String[] resources = {"CS2.jpg","minecraft.jpg","GTA5.jpg","Cyberpunk 2077.jpg","Elden Ring.jpg","Dota 2.jpg","Valorant.jpg","rdr2.jpg","witcher3.jpg","FIFA.jpg","Forza Horizon 5.jpg","terraria icon.jpg","Stardew Valley.jpg","HADES.jpg","hollow knight.jpg","sekiro.jpg","dark souls3.jpg","resident 4.jpg","godofwar.jpg","Portal2.jpg"};
        for (String resource : resources) {
            try (InputStream in = Ui.class.getResourceAsStream("/images/" + resource)) {
                assertNotNull(in,resource); assertNotNull(ImageIO.read(in),"ImageIO must decode " + resource);
            }
            assertEquals(240,Ui.cover(resource).getIconWidth());
        }
        assertEquals(145,Ui.cover("missing.jpg").getIconHeight());
        assertEquals(240,Ui.cover("../../missing.jpg").getIconWidth());
    }
    @Test void longOperationLeavesEdtFreeAndDeliversResultOnEdt() throws Exception {
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1), completed = new CountDownLatch(1);
        AtomicBoolean offEdt = new AtomicBoolean(), onEdt = new AtomicBoolean();
        TaskPanel[] panel = new TaskPanel[1];
        SwingUtilities.invokeAndWait(() -> {
            Ui.theme(); panel[0] = new TaskPanel(new Client("127.0.0.1",1)) { @Override void refresh() {} }; panel[0].addNotify();
            panel[0].work(() -> { offEdt.set(!SwingUtilities.isEventDispatchThread()); started.countDown(); if (!release.await(5,TimeUnit.SECONDS)) throw new TimeoutException(); return "response"; },value -> { onEdt.set(SwingUtilities.isEventDispatchThread()); completed.countDown(); });
        });
        try {
            assertTrue(started.await(5,TimeUnit.SECONDS));
            FutureTask<Boolean> probe = new FutureTask<>(SwingUtilities::isEventDispatchThread); SwingUtilities.invokeLater(probe);
            assertTrue(probe.get(2,TimeUnit.SECONDS),"EDT remains responsive while I/O is waiting"); release.countDown();
            assertTrue(completed.await(5,TimeUnit.SECONDS)); assertTrue(offEdt.get()); assertTrue(onEdt.get());
        } finally { release.countDown(); SwingUtilities.invokeAndWait(() -> panel[0].removeNotify()); }
    }
    @Test void panelsRenderWithoutNetworkOnEdt() throws Exception {
        Path directory = Path.of("target","ui-previews"); Files.createDirectories(directory);
        SwingUtilities.invokeAndWait(() -> {
            Ui.theme(); Client client = new Client("127.0.0.1",1);
            JPanel[] panels = {new CatalogPanel(client,user -> {}),new CartPanel(client,user -> {}),new ProfilePanel(client,user -> {}),new AdminPanel(client)};
            String[] names = {"catalog","cart","profile","admin"};
            for (int i = 0; i < panels.length; i++) {
                panels[i].setSize(1140,620); layout(panels[i]);
                BufferedImage image = new BufferedImage(1140,620,BufferedImage.TYPE_INT_RGB); Graphics2D graphics = image.createGraphics();
                try { panels[i].printAll(graphics); ImageIO.write(image,"png",directory.resolve(names[i] + ".png").toFile()); }
                catch (Exception e) { throw new RuntimeException(e); } finally { graphics.dispose(); }
            }
        });
    }
    @Test void navigationRespectsRoleAndShellFitsSmallWindow() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            Ui.theme(); Client client = new Client("127.0.0.1",1);
            StoreShell shell = new StoreShell(client,new gamestore.shared.Models.User(1,"player","USER",java.math.BigDecimal.ZERO),() -> {});
            shell.setSize(1000,700); layout(shell);
            assertFalse(hasButton(shell,"Администрирование")); assertTrue(hasButton(shell,"Библиотека")); assertTrue(hasButton(shell,"Заказы"));
            assertTrue(shell.getComponent(1).getWidth() > 600);
            StoreShell admin = new StoreShell(client,new gamestore.shared.Models.User(2,"admin","ADMIN",java.math.BigDecimal.ZERO),() -> {});
            assertTrue(hasButton(admin,"Администрирование"));
            CoverGrid grid = new CoverGrid(); for (int i = 0; i < 6; i++) grid.add(new JPanel()); grid.setSize(760,1000); grid.doLayout();
            int wide = ((GridLayout)grid.getLayout()).getColumns(); grid.setSize(460,2000); grid.doLayout();
            assertTrue(((GridLayout)grid.getLayout()).getColumns() < wide); assertTrue(grid.getScrollableTracksViewportWidth());
        });
    }
    @Test void portraitCoverIsContainedWithoutCropping() {
        ImageIcon icon = Ui.cover("Elden Ring.jpg",240,145); BufferedImage image = (BufferedImage)icon.getImage();
        assertEquals(Ui.BACKGROUND.getRGB(),image.getRGB(0,72));
        assertNotEquals(Ui.BACKGROUND.getRGB(),image.getRGB(120,72));
    }
    private static boolean hasButton(Container parent,String text) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JButton button && text.equals(button.getText())) return true;
            if (child instanceof Container container && hasButton(container,text)) return true;
        }
        return false;
    }
    private static void layout(Container parent) { parent.doLayout(); for (Component child : parent.getComponents()) if (child instanceof Container container) layout(container); }
}
