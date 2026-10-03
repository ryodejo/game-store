package gamestore.client.ui;

import gamestore.client.Client;
import gamestore.shared.Models.User;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.*;

/** Swing navigation shell; all requests remain in the existing worker-backed panels. */
final class StoreShell extends JPanel {
    private final CardLayout layout = new CardLayout();
    private final JPanel pages = new JPanel(layout);
    private final Map<String,TaskPanel> sections = new LinkedHashMap<>();
    private final Map<String,JButton> navigation = new LinkedHashMap<>();
    private final JLabel balance = Ui.label("");
    private final JButton cartButton = Ui.button("Корзина");
    private final JPanel menu = new JPanel();
    private final JLabel accountName = Ui.label("");
    StoreShell(Client client,User user,Runnable reconnect) {
        super(new BorderLayout()); setName("storeShell");
        accountName.setName("accountDisplayName");
        JPanel sidebar = new JPanel(new BorderLayout(0,Ui.scale(24))); sidebar.setBackground(Ui.PANEL); sidebar.setBorder(Ui.padding(14)); sidebar.setPreferredSize(new Dimension(Ui.scale(182),0));
        JLabel brand = Ui.label("GAME STORE"); brand.setFont(brand.getFont().deriveFont(Font.BOLD,Ui.scale(17))); sidebar.add(brand,BorderLayout.NORTH);
        menu.setOpaque(false); menu.setLayout(new BoxLayout(menu,BoxLayout.Y_AXIS)); sidebar.add(menu,BorderLayout.CENTER);
        CatalogPanel catalog = new CatalogPanel(client,this::updateUser); CartPanel cart = new CartPanel(client,this::updateUser);
        catalog.externalSearch(this::updateCart); cart.onCartChanged(this::updateCart);
        section("Магазин",catalog,true); section("Библиотека",new LibraryPanel(client),true); section("Заказы",new OrdersPanel(client),true);
        section("Профиль",new ProfilePanel(client,this::updateUser,reconnect),true); section("Корзина",cart,false);
        if ("ADMIN".equals(user.role())) section("Администрирование",new AdminPanel(client),true);
        JPanel account = new JPanel(new GridLayout(3,1,0,8)); account.setOpaque(false); account.add(accountName); account.add(Ui.muted("ADMIN".equals(user.role()) ? "Администратор" : "Пользователь"));
        JButton again = Ui.button("Войти снова"); again.setToolTipText("Восстановить сессию, сохранив незавершённые операции."); again.addActionListener(e -> reconnect.run()); account.add(again); sidebar.add(account,BorderLayout.SOUTH); add(sidebar,BorderLayout.WEST);
        JPanel content = new JPanel(new BorderLayout()); JPanel header = new JPanel(new BorderLayout(Ui.scale(12),0)); header.setBorder(Ui.padding(18));
        JTextField search = catalog.searchField(); search.putClientProperty("JTextField.placeholderText","Поиск игр по названию"); search.setColumns(0); search.getAccessibleContext().setAccessibleName("Поиск игр по названию");
        JButton find = Ui.button("Найти"); JPanel searchBox = new JPanel(new BorderLayout(8,0)); searchBox.add(search,BorderLayout.CENTER); searchBox.add(find,BorderLayout.EAST); header.add(searchBox,BorderLayout.CENTER);
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT,Ui.scale(12),0)); controls.add(balance); controls.add(cartButton); header.add(controls,BorderLayout.EAST);
        find.addActionListener(e -> show("Магазин",true)); search.addActionListener(e -> show("Магазин",true)); cartButton.addActionListener(e -> show("Корзина",true));
        content.add(header,BorderLayout.NORTH); content.add(pages,BorderLayout.CENTER); add(content,BorderLayout.CENTER); updateUser(user); show("Магазин",false);
    }
    private void section(String name,TaskPanel panel,boolean visible) {
        sections.put(name,panel); pages.add(panel,name);
        if (!visible) return;
        JButton button = Ui.button(name); button.setHorizontalAlignment(SwingConstants.LEFT); button.setAlignmentX(LEFT_ALIGNMENT); button.setMaximumSize(new Dimension(Integer.MAX_VALUE,Ui.scale(40)));
        button.addActionListener(e -> show(name,true)); menu.add(button); menu.add(Box.createVerticalStrut(Ui.scale(6))); navigation.put(name,button);
    }
    void show(String name,boolean refresh) {
        layout.show(pages,name); navigation.forEach((title,button) -> { button.setBackground(title.equals(name) ? new Color(0x304258) : Ui.PANEL); button.setForeground(title.equals(name) ? Ui.TEXT : Ui.MUTED); });
        if (refresh) sections.get(name).refresh();
    }
    void refresh() { sections.get("Магазин").refresh(); }
    void updateUser(User user) { String old = accountName.getText(); accountName.setText(user.displayName()); accountName.setToolTipText(user.displayName() + " · логин: " + user.login()); balance.setText(Ui.money(user.balance())); balance.setToolTipText("Учебный баланс"); firePropertyChange("displayName",old,user.displayName()); }
    private void updateCart(int count) { cartButton.setText(count == 0 ? "Корзина" : "Корзина · " + count); }
}
