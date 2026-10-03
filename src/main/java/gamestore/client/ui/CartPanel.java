package gamestore.client.ui;

import gamestore.client.Client;
import gamestore.shared.Models.*;
import java.awt.*;
import java.util.*;
import java.util.function.Consumer;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

final class CartPanel extends TaskPanel {
    private final DefaultTableModel model = new DefaultTableModel(new String[]{"Игра","Жанр","Цена, KZT","В продаже",""},0) { @Override public boolean isCellEditable(int row,int column) { return column == 4 && pending == null; } };
    private final JTable table = Ui.table(model);
    private final JLabel total = Ui.heading("Корзина"), operationLabel = Ui.label("");
    private final JButton pay = Ui.button("Оплатить корзину (демо)"), remove = Ui.button("Удалить выбранную игру");
    private final Consumer<User> updateUser;
    private Consumer<Integer> updateCart = count -> {};
    void onCartChanged(Consumer<Integer> listener) { updateCart = listener; }
    private final java.util.Map<Long,ImageIcon> covers = new HashMap<>();
    private java.util.List<Game> games = java.util.List.of();
    private UUID pending;
    private record Loaded(Cart cart,User user,java.util.Map<Long,ImageIcon> images) {}
    private record Checkout(Payment payment,Loaded loaded) {}
    private Loaded load() throws Exception {
        Cart cart = client.cart(); java.util.Map<Long,ImageIcon> images = new HashMap<>();
        for (Game game : cart.games()) images.put(game.id(),Ui.cover(game.image(),Ui.scale(62),Ui.scale(62)));
        return new Loaded(cart,client.profile(),images);
    }
    private void display(Loaded loaded) { covers.clear(); covers.putAll(loaded.images); display(loaded.cart,loaded.user); }
    CartPanel(Client client,Consumer<User> updateUser) {
        super(client); this.updateUser = updateUser; table.setName("cartTable");
        JButton reload = Ui.button("Обновить"); reload.addActionListener(e -> refresh());
        JPanel header = new JPanel(new GridLayout(3,1)); header.add(Ui.row(total));
        header.add(Ui.row(reload,remove,pay)); header.add(Ui.row(operationLabel)); add(header,BorderLayout.NORTH);
        table.setRowHeight(Ui.scale(80)); table.getColumnModel().getColumn(0).setPreferredWidth(Ui.scale(280));
        var renderer = new javax.swing.table.DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable t,Object value,boolean selected,boolean focus,int row,int column) {
                super.getTableCellRendererComponent(t,value,selected,focus,row,column); setIcon(column == 0 && row < games.size() ? covers.get(games.get(t.convertRowIndexToModel(row)).id()) : null); setIconTextGap(Ui.scale(12)); setBorder(Ui.padding(8)); return this;
            }
        }; renderer.putClientProperty("html.disable",true); table.setDefaultRenderer(Object.class,renderer);
        add(Ui.scroll(table),BorderLayout.CENTER);
        table.getColumnModel().getColumn(4).setPreferredWidth(Ui.scale(85));
        table.getColumnModel().getColumn(4).setCellRenderer((t,value,selected,focus,row,column) -> { JPanel cell = new JPanel(new GridBagLayout()); cell.setOpaque(false); JButton button = Ui.button("Удалить"); button.setEnabled(pending == null && t.isEnabled()); cell.add(button); return cell; });
        table.getColumnModel().getColumn(4).setCellEditor(new javax.swing.DefaultCellEditor(new JCheckBox()) {
            private final JButton button = Ui.button("Удалить");
            private long gameId;
            { button.addActionListener(e -> { fireEditingStopped(); removeGame(gameId); }); }
            @Override public Component getTableCellEditorComponent(JTable t,Object value,boolean selected,int row,int column) { gameId = games.get(t.convertRowIndexToModel(row)).id(); return button; }
            @Override public Object getCellEditorValue() { return "Удалить"; }
        });
        pay.setBackground(Ui.ACCENT); pay.setForeground(Ui.TEXT);
        remove.addActionListener(e -> {
            int row = table.getSelectedRow(); if (row < 0) { status.setText("Выберите игру в таблице."); return; }
            long gameId = games.get(table.convertRowIndexToModel(row)).id();
            removeGame(gameId);
        });
        pay.addActionListener(e -> {
            if (pending == null) pending = UUID.randomUUID(); UUID operation = pending; pendingState();
            work(() -> new Checkout(client.checkout(operation),load()),result -> {
                pending = null; display(result.loaded);
                Ui.message(this,Ui.status(result.payment.status()) + "\nЗаказ №" + result.payment.orderId() + "\nСумма: " + Ui.money(result.payment.amount()) + "\nUUID: " + result.payment.operationId());
            },error -> { if (!Ui.uncertain(error)) pending = null; pendingState(); });
        });
    }
    @Override void refresh() { work(this::load,this::display); }
    private void removeGame(long gameId) { work(() -> { client.removeFromCart(gameId); return load(); },this::display); }
    private void display(Cart cart,User user) {
        updateUser.accept(user); updateCart.accept(cart.games().size()); games = cart.games(); model.setRowCount(0);
        for (Game game : games) model.addRow(new Object[]{game.title(),game.genre(),Ui.money(game.price()),game.active() ? "Да" : "Скрыта","Удалить"});
        total.setText("Итого: " + Ui.money(cart.total()) + " · " + games.size() + " игр"); pendingState();
        status.setText(games.isEmpty() ? "Корзина пуста. Добавьте игры из каталога." : "Сумму и цены при оплате определяет сервер. Баланс учебный.");
    }
    private void pendingState() {
        pay.setText(pending == null ? "Оплатить корзину (демо)" : "Повторить ту же оплату");
        operationLabel.setText(pending == null ? "Без реальных списаний и банковских реквизитов." : "Незавершённый запрос · UUID: " + pending);
        remove.setEnabled(pending == null); pay.setEnabled(!games.isEmpty() || pending != null);
    }
}
