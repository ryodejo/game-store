package gamestore.client.ui;

import gamestore.client.Client;
import gamestore.shared.Models.*;
import java.awt.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

final class OrdersPanel extends TaskPanel {
    private final DefaultTableModel model = Ui.model("№ заказа","Дата","Сумма, KZT","Статус");
    private final JTable table = Ui.table(model);
    private final JTextArea details = new JTextArea();
    private List<OrderSummary> orders = List.of();
    OrdersPanel(Client client) {
        super(client); table.setName("ordersTable"); JButton reload = Ui.button("Обновить"), detail = Ui.button("Детали выбранного заказа");
        reload.addActionListener(e -> refresh()); detail.addActionListener(e -> showOrder());
        add(Ui.row(Ui.heading("Заказы"),reload),BorderLayout.NORTH);
        details.setEditable(false); details.setLineWrap(true); details.setWrapStyleWord(true); details.setMargin(new Insets(14,14,14,14)); details.setText("Выберите заказ, чтобы посмотреть состав и оплату.");
        JPanel history = new JPanel(new BorderLayout(8,8)); history.add(Ui.scroll(table),BorderLayout.CENTER); history.add(Ui.row(detail),BorderLayout.SOUTH);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT,history,Ui.scroll(details)); split.setResizeWeight(0.55); split.setBorder(BorderFactory.createEmptyBorder()); add(split,BorderLayout.CENTER);
    }
    @Override void refresh() { work(client::orders,loaded -> { orders = loaded; model.setRowCount(0); for (OrderSummary order : orders) model.addRow(new Object[]{order.id(),Ui.date(order.date()),Ui.money(order.total()),Ui.status(order.status())}); status.setText(orders.isEmpty() ? "У вас пока нет заказов." : "Цены сохранены на момент покупки."); }); }
    private void showOrder() {
        int row = table.getSelectedRow(); if (row < 0) { status.setText("Выберите заказ в таблице."); return; }
        long id = orders.get(table.convertRowIndexToModel(row)).id();
        work(() -> client.order(id),result -> {
            StringBuilder text = new StringBuilder("Заказ №" + result.order().id() + " · " + Ui.date(result.order().date()) + "\n" + Ui.status(result.order().status()) + "\n\n");
            for (OrderItem item : result.items()) text.append(item.title()).append(" — ").append(Ui.money(item.price())).append('\n');
            text.append("\nИтого: ").append(Ui.money(result.order().total())).append("\nОперация: ").append(result.operationId()).append("\nОплата: ").append(Ui.status(result.paymentStatus()));
            if ("LEGACY".equals(result.order().source())) text.append("\nИмпорт orders.txt: показаны дата импорта и цена при импорте. Баланс не списывался.");
            details.setText(text.toString()); details.setCaretPosition(0);
        });
    }
}
