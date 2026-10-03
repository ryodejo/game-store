package gamestore.client.ui;

import gamestore.client.Client;
import gamestore.shared.Models.Game;
import java.awt.*;
import java.util.List;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

final class AdminPanel extends TaskPanel {
    private final DefaultTableModel model = Ui.model("ID","Название","Жанр","Цена, KZT","В продаже");
    private final JTable table = Ui.table(model);
    private final JTextField title = new JTextField(20), genre = new JTextField(20), price = new JTextField(20), image = new JTextField(20);
    private final JCheckBox active = new JCheckBox("Доступна в каталоге",true);
    private final JLabel editing = Ui.heading("Новая игра");
    private List<Game> games = List.of();
    private long id;
    AdminPanel(Client client) {
        super(client); table.setName("adminTable"); title.setName("gameTitle"); JButton reload = Ui.button("Обновить"), create = Ui.button("Новая игра"), save = Ui.button("Сохранить");
        reload.addActionListener(e -> refresh()); create.addActionListener(e -> { table.clearSelection(); edit(null); });
        add(Ui.row(Ui.heading("Управление каталогом"),reload,create),BorderLayout.NORTH);
        JPanel form = new JPanel(new GridBagLayout()); GridBagConstraints gc = new GridBagConstraints(); gc.gridx = 0; gc.fill = GridBagConstraints.HORIZONTAL; gc.weightx = 1; gc.insets = new Insets(6,10,6,10);
        Component[] fields = {editing,Ui.label("Название (до 120 символов)"),title,Ui.label("Жанр (до 40 символов)"),genre,Ui.label("Цена, KZT"),price,Ui.label("Имя ресурса images/, например CS2.jpg"),image,active,save,Ui.label("Пустое изображение — заглушка."),Ui.label("Скрытие сохраняет историю и библиотеку.")};
        for (int i = 0; i < fields.length; i++) { gc.gridy = i; form.add(fields[i],gc); }
        form.setMinimumSize(new Dimension(Ui.scale(240),0)); form.setPreferredSize(new Dimension(Ui.scale(290),Ui.scale(510)));
        JScrollPane formScroll = Ui.scroll(form); formScroll.setMinimumSize(new Dimension(Ui.scale(240),0));
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF); int[] widths = {45,180,120,100,90};
        for (int i = 0; i < widths.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(Ui.scale(widths[i]));
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,Ui.scroll(table),formScroll); split.setResizeWeight(0.65); split.setBorder(BorderFactory.createEmptyBorder()); add(split,BorderLayout.CENTER);
        save.setBackground(Ui.ACCENT); save.setForeground(Ui.TEXT);
        table.getSelectionModel().addListSelectionListener(e -> {
            int row = table.getSelectedRow(); if (!e.getValueIsAdjusting() && row >= 0 && row < games.size()) edit(games.get(table.convertRowIndexToModel(row)));
        });
        save.addActionListener(e -> {
            long gameId = id; String name = title.getText(), category = genre.getText(), cost = price.getText().trim().replace(',','.'), cover = image.getText().trim(); boolean available = active.isSelected();
            work(() -> client.saveGame(gameId,name,category,cost,cover,available),game -> { edit(game); status.setText("Игра сохранена. История заказов сохраняет цены на момент покупки."); refresh(); });
        });
        edit(null);
    }
    private void edit(Game game) {
        id = game == null ? 0 : game.id(); editing.setText(id == 0 ? "Новая игра" : "Игра №" + id);
        title.setText(game == null ? "" : game.title()); genre.setText(game == null ? "Экшен" : game.genre()); price.setText(game == null ? "0.00" : game.price().toPlainString());
        image.setText(game == null ? "" : game.image()); active.setSelected(game == null || game.active());
    }
    @Override void refresh() {
        work(client::adminGames,loaded -> {
            games = loaded; model.setRowCount(0);
            for (Game game : games) model.addRow(new Object[]{game.id(),game.title(),game.genre(),game.price().toPlainString(),game.active() ? "Да" : "Нет"});
        });
    }
}
