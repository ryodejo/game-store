package gamestore.client.ui;

import gamestore.client.Client;
import gamestore.server.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiIntegrationTest {
    @Test void swingCatalogCartHistoryAndAdminWorkThroughRealTcp() throws Exception {
        try (DatabaseFixture fixture = new DatabaseFixture()) {
            Store store = new Store(fixture.db); String password = "T!" + UUID.randomUUID();
            var user = store.users.register("portfolio_player",password,"original@example.com"); store.users.grantAdmin(user.login());
            store.users.topUp(user.id(),UUID.randomUUID(),new BigDecimal("30000"));
            store.orders.buy(user.id(),UUID.randomUUID(),1); store.orders.buy(user.id(),UUID.randomUUID(),2); store.orders.buy(user.id(),UUID.randomUUID(),12);
            store.cart.add(user.id(),3); store.cart.add(user.id(),4);
            try (TcpServer server = new TcpServer(store,"127.0.0.1",0)) {
                server.start();
                try (Client client = new Client("127.0.0.1",server.port())) {
                    client.login(user.login(),password);
                    TaskPanel[] panels = new TaskPanel[4];
                    SwingUtilities.invokeAndWait(() -> {
                        Ui.theme(); panels[0] = new CatalogPanel(client,value -> {}); panels[1] = new CartPanel(client,value -> {});
                        panels[2] = new OrdersPanel(client); panels[3] = new AdminPanel(client);
                        for (TaskPanel panel : panels) { panel.setFocusCycleRoot(true); panel.addNotify(); panel.setSize(1140,700); panel.refresh(); }
                    });
                    try {
                        for (TaskPanel panel : panels) awaitReady(panel);
                        assertEquals(2,onEdt(() -> named(panels[1],"cartTable",JTable.class).getRowCount()));
                        assertEquals(3,onEdt(() -> named(panels[2],"ordersTable",JTable.class).getRowCount()));
                        assertEquals(20,onEdt(() -> named(panels[3],"adminTable",JTable.class).getRowCount()));
                        SwingUtilities.invokeAndWait(() -> { named(panels[0],"catalogSearch",JTextField.class).setText("Portal"); button(panels[0],"Найти / обновить").doClick(); });
                        awaitReady(panels[0]); assertEquals("Найдено игр: 1 · цены в KZT",onEdt(() -> panels[0].status.getText()));
                        SwingUtilities.invokeAndWait(() -> button(panels[0],"В корзину").doClick()); awaitReady(panels[0]);
                        SwingUtilities.invokeAndWait(panels[1]::refresh); awaitReady(panels[1]);
                        assertEquals(3,onEdt(() -> named(panels[1],"cartTable",JTable.class).getRowCount()));
                        SwingUtilities.invokeAndWait(() -> { named(panels[1],"cartTable",JTable.class).setRowSelectionInterval(2,2); button(panels[1],"Удалить выбранную игру").doClick(); });
                        awaitReady(panels[1]); assertEquals(2,client.cart().games().size());
                        SwingUtilities.invokeAndWait(() -> { JTable table = named(panels[1],"cartTable",JTable.class); assertTrue(table.editCellAt(0,4)); ((JButton)table.getEditorComponent()).doClick(); });
                        awaitReady(panels[1]); assertEquals(1,client.cart().games().size()); client.addToCart(3);
                        SwingUtilities.invokeAndWait(panels[1]::refresh); awaitReady(panels[1]);
                        SwingUtilities.invokeAndWait(() -> { named(panels[3],"adminTable",JTable.class).setRowSelectionInterval(0,0); named(panels[3],"gameTitle",JTextField.class).setText("CS2 · Portfolio"); button(panels[3],"Сохранить").doClick(); });
                        awaitReady(panels[3]); assertEquals("CS2 · Portfolio",store.games.catalog("CS2","",null,null,"TITLE").get(0).title());
                        assertEquals("CS2",store.orders.detail(user.id(),store.orders.history(user.id()).get(2).id()).items().get(0).title());
                        SwingUtilities.invokeAndWait(() -> { named(panels[2],"ordersTable",JTable.class).setRowSelectionInterval(0,0); button(panels[2],"Детали выбранного заказа").doClick(); });
                        awaitReady(panels[2]);
                        SwingUtilities.invokeAndWait(() -> { named(panels[0],"catalogSearch",JTextField.class).setText(""); panels[0].refresh(); }); awaitReady(panels[0]);
                        Path folder = Path.of("target","ui-previews","populated"); Files.createDirectories(folder);
                        String[] names = {"catalog","cart","orders","admin"};
                        for (int i = 0; i < panels.length; i++) { int index = i; SwingUtilities.invokeAndWait(() -> screenshot(panels[index],folder.resolve(names[index] + ".png"))); }
                        var currentUser = client.profile();
                        StoreShell shell = onEdt(() -> { StoreShell result = new StoreShell(client,currentUser,() -> {}); result.addNotify(); result.setSize(1000,700); return result; });
                        try {
                            for (String section : new String[]{"Магазин","Библиотека","Заказы","Профиль","Корзина","Администрирование"}) {
                                SwingUtilities.invokeAndWait(() -> { shell.show(section,true); layout(shell); });
                                TaskPanel active = onEdt(() -> descendants(shell).stream().filter(TaskPanel.class::isInstance).map(TaskPanel.class::cast).filter(Component::isVisible).findFirst().orElseThrow());
                                awaitReady(active);
                                if (section.equals("Профиль")) {
                                    SwingUtilities.invokeAndWait(() -> { named(active,"displayName",JTextField.class).setText("Portfolio Player"); button(active,"Сохранить ник").doClick(); });
                                    awaitReady(active); assertEquals("Portfolio Player",client.profile().displayName());
                                    assertEquals("Portfolio Player",onEdt(() -> named(shell,"accountDisplayName",JLabel.class).getText()));
                                    SwingUtilities.invokeAndWait(() -> { named(active,"profileEmail",JTextField.class).setText("Portfolio@Example.com"); named(active,"emailPassword",JPasswordField.class).setText("wrong-password"); button(active,"Сохранить email").doClick(); });
                                    awaitReady(active); assertEquals("original@example.com",client.profile().email()); assertTrue(onEdt(() -> named(active,"emailPasswordError",JLabel.class).getText().contains("текущий пароль")));
                                    SwingUtilities.invokeAndWait(() -> { named(active,"emailPassword",JPasswordField.class).setText(password); button(active,"Сохранить email").doClick(); });
                                    awaitReady(active); assertEquals("portfolio@example.com",client.profile().email());
                                    SwingUtilities.invokeAndWait(() -> { named(active,"newPassword",JPasswordField.class).setText("NewProfilePassword-27"); named(active,"repeatPassword",JPasswordField.class).setText("different-password"); button(active,"Изменить пароль").doClick(); });
                                    assertTrue(onEdt(() -> named(active,"repeatPasswordError",JLabel.class).getText().contains("не совпадают")));
                                    SwingUtilities.invokeAndWait(() -> { named(active,"currentPassword",JPasswordField.class).setText("wrong-password"); named(active,"repeatPassword",JPasswordField.class).setText("NewProfilePassword-27"); button(active,"Изменить пароль").doClick(); });
                                    awaitReady(active); assertTrue(onEdt(() -> named(active,"currentPasswordError",JLabel.class).getText().contains("текущий пароль")));
                                    SwingUtilities.invokeAndWait(() -> ((ProfilePanel)active).previewAvatar(Path.of("images","CS2.jpg"))); awaitReady(active); assertEquals("",client.avatar());
                                    SwingUtilities.invokeAndWait(() -> button(active,"Сохранить аватар").doClick()); awaitReady(active); assertFalse(client.avatar().isEmpty());
                                    SwingUtilities.invokeAndWait(() -> screenshot(shell,folder.resolve("profile-avatar.png")));
                                    SwingUtilities.invokeAndWait(() -> button(active,"Удалить аватар").doClick()); awaitReady(active); assertEquals("",client.avatar());
                                    SwingUtilities.invokeAndWait(() -> { named(active,"topUpAmount",JTextField.class).setText("500"); button(active,"Пополнить демо-баланс").doClick(); });
                                    awaitReady(active); assertEquals(new BigDecimal("27000.00"),client.profile().balance());
                                }
                                if (section.equals("Заказы")) {
                                    SwingUtilities.invokeAndWait(() -> { named(active,"ordersTable",JTable.class).setRowSelectionInterval(0,0); button(active,"Детали выбранного заказа").doClick(); }); awaitReady(active);
                                }
                                SwingUtilities.invokeAndWait(() -> screenshot(shell,folder.resolve("shell-" + section + ".png")));
                            }
                            SwingUtilities.invokeAndWait(() -> { shell.setSize(1500,900); shell.show("Магазин",false); screenshot(shell,folder.resolve("shell-wide.png")); });
                        } finally { SwingUtilities.invokeAndWait(shell::removeNotify); }
                    } finally { SwingUtilities.invokeAndWait(() -> { for (TaskPanel panel : panels) panel.removeNotify(); }); }
                }
            }
        }
    }
    private static void awaitReady(TaskPanel panel) throws Exception {
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15);
        while (onEdt(() -> panel.status.getText().equals("Загрузка…"))) {
            if (System.nanoTime() > deadline) fail("UI operation timeout"); Thread.sleep(40);
        }
        assertFalse(onEdt(() -> panel.status.getText().contains("Не удалось")),"UI request succeeded");
    }
    private static <T> T onEdt(java.util.concurrent.Callable<T> work) throws Exception {
        AtomicReference<T> result = new AtomicReference<>(); AtomicReference<Exception> error = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> { try { result.set(work.call()); } catch (Exception e) { error.set(e); } });
        if (error.get() != null) throw error.get(); return result.get();
    }
    private static <T extends Component> T named(Container root,String name,Class<T> type) {
        return descendants(root).stream().filter(type::isInstance).map(type::cast).filter(c -> name.equals(c.getName())).findFirst().orElseThrow();
    }
    private static JButton button(Container root,String text) { return descendants(root).stream().filter(JButton.class::isInstance).map(JButton.class::cast).filter(b -> text.equals(b.getText())).findFirst().orElseThrow(); }
    private static List<Component> descendants(Container parent) {
        List<Component> result = new ArrayList<>();
        for (Component child : parent.getComponents()) { result.add(child); if (child instanceof Container container) result.addAll(descendants(container)); } return result;
    }
    private static void screenshot(Container panel,Path file) {
        layout(panel); BufferedImage image = new BufferedImage(panel.getWidth(),panel.getHeight(),BufferedImage.TYPE_INT_RGB); Graphics2D graphics = image.createGraphics();
        try { panel.printAll(graphics); ImageIO.write(image,"png",file.toFile()); }
        catch (Exception e) { throw new RuntimeException(e); } finally { graphics.dispose(); }
    }
    private static void layout(Container parent) { parent.doLayout(); for (Component child : parent.getComponents()) if (child instanceof Container container) layout(container); }
}
