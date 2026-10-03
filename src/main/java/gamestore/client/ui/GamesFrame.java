package gamestore.client.ui;

import gamestore.client.Client;
import gamestore.shared.Models.User;
import java.awt.*;
import java.awt.event.*;
import java.util.Arrays;
import javax.swing.*;

public final class GamesFrame extends JFrame {
    private final StoreShell shell;
    public GamesFrame(Client client,User user) {
        setTitle("Game Store · " + user.displayName()); setDefaultCloseOperation(EXIT_ON_CLOSE);
        addWindowListener(new WindowAdapter() { @Override public void windowClosing(WindowEvent e) { client.close(); } });
        shell = new StoreShell(client,user,() -> reconnect(client,user.login()));
        shell.addPropertyChangeListener("displayName",e -> setTitle("Game Store · " + e.getNewValue()));
        Rectangle desktop = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        add(shell); setSize(Math.min(Ui.scale(1180),desktop.width),Math.min(Ui.scale(780),desktop.height));
        setMinimumSize(new Dimension(Math.min(1000,desktop.width),Math.min(700,desktop.height))); setLocationRelativeTo(null); setVisible(true); shell.refresh();
    }
    private void updateUser(User user) { shell.updateUser(user); }
    private void reconnect(Client client,String login) {
        JDialog dialog = new JDialog(this,"Восстановить сессию · " + login,true);
        TaskPanel panel = new TaskPanel(client) { @Override void refresh() {} };
        JPasswordField password = new JPasswordField(24); JButton enter = Ui.button("Войти в тот же аккаунт");
        panel.add(Ui.row(Ui.label("Пароль"),password,enter),BorderLayout.CENTER);
        enter.addActionListener(e -> {
            char[] characters = password.getPassword(); String secret = new String(characters); Arrays.fill(characters,'\0');
            panel.work(() -> client.login(login,secret),user -> { password.setText(""); updateUser(user); dialog.dispose(); });
        });
        dialog.add(panel); dialog.getRootPane().setDefaultButton(enter); dialog.pack(); dialog.setLocationRelativeTo(this); dialog.setVisible(true);
    }
}
