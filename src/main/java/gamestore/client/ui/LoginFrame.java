package gamestore.client.ui;

import gamestore.client.Client;
import java.awt.*;
import java.awt.event.*;
import java.util.Arrays;
import javax.swing.*;

public final class LoginFrame extends JFrame {
    public LoginFrame(Client client) {
        setTitle("Game Store · Вход"); setDefaultCloseOperation(EXIT_ON_CLOSE);
        addWindowListener(new WindowAdapter() { @Override public void windowClosing(WindowEvent e) { client.close(); } });
        TaskPanel panel = new TaskPanel(client) { @Override void refresh() {} };
        JPanel form = Ui.surface(new GridBagLayout());
        GridBagConstraints gc = new GridBagConstraints(); gc.gridx = 0; gc.fill = GridBagConstraints.HORIZONTAL; gc.weightx = 1; gc.insets = new Insets(7,12,7,12);
        JTextField login = new JTextField(24); JPasswordField password = new JPasswordField(24);
        JTextField email = new JTextField(24); email.setName("registrationEmail");
        JButton enter = Ui.primary("Войти"), register = Ui.button("Создать аккаунт");
        JCheckBox show = new JCheckBox("Показать пароль"); show.setOpaque(false); char echo = password.getEchoChar(); show.addActionListener(e -> password.setEchoChar(show.isSelected() ? (char)0 : echo));
        login.setName("loginField"); password.setName("passwordField"); login.putClientProperty("JTextField.placeholderText","Логин или email для входа");
        Component[] components = {Ui.heading("Game Store"),Ui.muted("Вход и регистрация"),Ui.label("Логин или email для входа"),login,Ui.muted("При регистрации: логин из 3–32 букв, цифр и _"),Ui.label("Email при регистрации (обязательно)"),email,Ui.label("Пароль"),password,show,Ui.muted("От 8 символов, до 72 байт UTF-8"),Ui.row(enter,register)};
        for (int i = 0; i < components.length; i++) { gc.gridy = i; form.add(components[i],gc); }
        JPanel centered = new JPanel(new GridBagLayout()); centered.add(form); panel.add(centered,BorderLayout.CENTER); add(panel);
        enter.addActionListener(e -> {
            String name = login.getText().trim(); char[] characters = password.getPassword(); String secret = new String(characters); Arrays.fill(characters,'\0');
            panel.work(() -> client.login(name,secret),user -> { password.setText(""); new GamesFrame(client,user); dispose(); });
        });
        register.addActionListener(e -> {
            String name = login.getText().trim(); char[] characters = password.getPassword(); String secret = new String(characters); Arrays.fill(characters,'\0');
            String addressValue = email.getText().trim();
            panel.work(() -> client.register(name,secret,addressValue),user -> { password.setText(""); panel.status.setText("Аккаунт создан. Введите пароль и войдите."); });
        });
        Rectangle desktop = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        getRootPane().setDefaultButton(enter); setSize(Math.min(Ui.scale(560),desktop.width),Math.min(Ui.scale(650),desktop.height));
        setMinimumSize(new Dimension(Math.min(Ui.scale(500),desktop.width),Math.min(Ui.scale(620),desktop.height))); setLocationRelativeTo(null); setVisible(true);
    }
}
