package gamestore.client.ui;

import gamestore.client.Client;
import gamestore.shared.Models.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.*;

final class CatalogPanel extends TaskPanel {
    private final JTextField search = new JTextField(19), min = new JTextField(7), max = new JTextField(7);
    private final JComboBox<String> genre = new JComboBox<>(new String[]{"Все жанры"});
    private final JComboBox<String> sort = new JComboBox<>(new String[]{"Название А–Я","Цена ↑","Цена ↓"});
    private final JPanel cards = new CoverGrid();
    private final Map<Long,UUID> pending = new HashMap<>();
    private final Set<String> genres = new TreeSet<>();
    private final Consumer<User> updateUser;
    private record Cover(Game game,ImageIcon image) {}
    private record Loaded(List<Cover> covers,Set<Long> owned,Set<Long> cart,User user) {}
    private Consumer<Integer> updateCart = count -> {};
    private final JPanel searchRow;
    JTextField searchField() { return search; }
    void externalSearch(Consumer<Integer> updateCart) { this.updateCart = updateCart; searchRow.setVisible(false); }
    private record Paid(Payment payment,User user) {}
    CatalogPanel(Client client,Consumer<User> updateUser) {
        super(client); this.updateUser = updateUser; genre.setEditable(true); search.setName("catalogSearch");
        JButton apply = Ui.button("Найти / обновить"); apply.addActionListener(e -> refresh()); search.addActionListener(e -> refresh());
        JPanel filters = new JPanel(); filters.setLayout(new BoxLayout(filters,BoxLayout.Y_AXIS));
        filters.add(Ui.row(Ui.heading("Магазин")));
        searchRow = Ui.row(Ui.label("Название"),search); filters.add(searchRow);
        JPanel options = new JPanel(new GridLayout(1,4,Ui.scale(8),0));
        genre.setToolTipText("Жанр"); sort.setToolTipText("Сортировка"); min.putClientProperty("JTextField.placeholderText","Цена от, KZT"); max.putClientProperty("JTextField.placeholderText","до, KZT");
        options.add(genre); options.add(sort); options.add(min); options.add(max); filters.add(options); filters.add(Ui.row(apply));
        add(filters,BorderLayout.NORTH); add(Ui.scroll(cards),BorderLayout.CENTER);
    }
    @Override void refresh() {
        String term = search.getText().trim(), category = String.valueOf(genre.getSelectedItem());
        if (category.equals("Все жанры")) category = "";
        String filterGenre = category, minimum = min.getText().trim().replace(',','.'), maximum = max.getText().trim().replace(',','.');
        String ordering = new String[]{"TITLE","PRICE_ASC","PRICE_DESC"}[sort.getSelectedIndex()];
        work(() -> {
            List<Game> games = client.catalog(term,filterGenre,minimum,maximum,ordering);
            List<Cover> covers = games.stream().map(game -> new Cover(game,Ui.cover(game.image(),Ui.scale(190),Ui.scale(200)))).toList();
            Set<Long> owned = new HashSet<>(); for (LibraryGame game : client.library()) owned.add(game.id());
            Set<Long> cart = new HashSet<>(); for (Game game : client.cart().games()) cart.add(game.id());
            return new Loaded(covers,owned,cart,client.profile());
        },loaded -> {
            updateUser.accept(loaded.user); updateCart.accept(loaded.cart.size()); cards.removeAll();
            for (Cover cover : loaded.covers) {
                if (genres.add(cover.game.genre())) genre.addItem(cover.game.genre());
                cards.add(card(cover,loaded.owned.contains(cover.game.id()),loaded.cart.contains(cover.game.id())));
            }
            if (loaded.covers.isEmpty()) cards.add(Ui.label("Игры не найдены. Измените фильтры."));
            status.setText("Найдено игр: " + loaded.covers.size() + " · цены в KZT"); cards.revalidate(); cards.repaint();
        });
    }
    private JPanel card(Cover cover,boolean owned,boolean inCart) {
        Game game = cover.game;
        JPanel card = Ui.surface(new BorderLayout(8,10));
        card.add(new JLabel(cover.image),BorderLayout.NORTH);
        JPanel info = new JPanel(); info.setOpaque(false); info.setLayout(new BoxLayout(info,BoxLayout.Y_AXIS));
        JLabel title = Ui.label(game.title()); title.setToolTipText(game.title()); title.setFont(title.getFont().deriveFont(Font.BOLD,Ui.scale(15)));
        JLabel category = Ui.label(game.genre()); category.setForeground(Ui.MUTED);
        info.add(title); info.add(Box.createVerticalStrut(6)); info.add(category); info.add(Box.createVerticalStrut(8)); info.add(Ui.label(Ui.money(game.price())));
        card.add(info,BorderLayout.CENTER);
        JButton add = Ui.primary(owned ? "В библиотеке" : inCart ? "В корзине" : "В корзину"), buy = Ui.button(pending.containsKey(game.id()) ? "Повторить оплату" : "Купить (демо)");
        add.setEnabled(!owned && !inCart); buy.setEnabled(!owned || pending.containsKey(game.id()));
        add.addActionListener(e -> work(() -> { client.addToCart(game.id()); return client.cart().games().size(); },count -> { updateCart.accept(count); add.setText("В корзине"); add.setEnabled(false); status.setText("Добавлено в корзину: " + game.title()); }));
        buy.addActionListener(e -> {
            UUID operation = pending.computeIfAbsent(game.id(),id -> UUID.randomUUID());
            work(() -> new Paid(client.buy(game.id(),operation),client.profile()),paid -> {
                pending.remove(game.id()); updateUser.accept(paid.user);
                Ui.message(this,Ui.status(paid.payment.status()) + "\nСумма: " + Ui.money(paid.payment.amount()) + "\nОперация: " + paid.payment.operationId()); refresh();
            },error -> {
                if (!Ui.uncertain(error)) pending.remove(game.id());
                buy.setText(pending.containsKey(game.id()) ? "Повторить оплату" : "Купить (демо)");
                buy.setToolTipText("UUID: " + operation);
            });
        });
        JPanel buttons = new JPanel(new GridLayout(2,1,0,Ui.scale(6))); buttons.setOpaque(false); buttons.add(add); buttons.add(buy); card.add(buttons,BorderLayout.SOUTH);
        return card;
    }
}
