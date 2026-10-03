package gamestore.client.ui;

import gamestore.client.Client;
import gamestore.shared.Models.LibraryGame;
import java.awt.*;
import java.util.List;
import javax.swing.*;

final class LibraryPanel extends TaskPanel {
    private final CoverGrid cards = new CoverGrid();
    private record Entry(LibraryGame game,ImageIcon image) {}
    LibraryPanel(Client client) {
        super(client); JButton reload = Ui.button("Обновить"); reload.addActionListener(e -> refresh());
        add(Ui.row(Ui.heading("Библиотека"),reload),BorderLayout.NORTH); add(Ui.scroll(cards),BorderLayout.CENTER);
    }
    @Override void refresh() {
        work(() -> client.library().stream().map(game -> new Entry(game,Ui.cover(game.image(),Ui.scale(190),Ui.scale(200)))).toList(),this::display);
    }
    private void display(List<Entry> entries) {
        cards.removeAll();
        for (Entry entry : entries) {
            JPanel card = Ui.surface(new BorderLayout(8,12)); card.add(new JLabel(entry.image),BorderLayout.NORTH);
            JPanel text = new JPanel(new GridLayout(3,1,0,6)); text.setOpaque(false);
            JLabel title = Ui.label(entry.game.title()); title.setFont(title.getFont().deriveFont(Font.BOLD)); title.setToolTipText(entry.game.title());
            text.add(title); text.add(Ui.muted(entry.game.genre())); text.add(Ui.muted("Куплена " + Ui.date(entry.game.acquiredAt()))); card.add(text,BorderLayout.CENTER); cards.add(card);
        }
        if (entries.isEmpty()) cards.add(Ui.muted("В библиотеке пока нет игр."));
        status.setText("Учебные покупки. Скачивание игр не предусмотрено."); cards.revalidate(); cards.repaint();
    }
}
