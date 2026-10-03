package gamestore.client.ui;

import gamestore.client.Client;
import gamestore.shared.AvatarImages;
import gamestore.shared.Models.User;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;

final class ProfilePanel extends TaskPanel {
    private final Consumer<User> updateUser;
    private final AvatarView avatar = new AvatarView();
    private final JLabel nickname = Ui.heading("Профиль"),address = Ui.muted(""),account = Ui.muted(""),balance = Ui.label(""),operationLabel = Ui.muted("");
    private final JTextField name = new JTextField(),email = new JTextField(),amount = new JTextField("10000.00",10);
    private final JPasswordField emailPassword = new JPasswordField(),current = new JPasswordField(),replacement = new JPasswordField(),repeat = new JPasswordField();
    private final JLabel nameError = error(),emailError = error(),emailPasswordError = error(),currentError = error(),newError = error(),repeatError = error(),avatarError = error();
    private final JButton saveAvatar = Ui.primary("Сохранить аватар"),cancelAvatar = Ui.button("Отменить"),removeAvatar = Ui.button("Удалить аватар"),topUp = Ui.primary("Пополнить демо-баланс");
    private byte[] selectedBytes;
    private BufferedImage savedImage;
    private User user;
    private UUID pending;
    private String pendingAmount;
    private record Loaded(User user,BufferedImage image) {}
    private record Preview(byte[] bytes,BufferedImage image) {}
    ProfilePanel(Client client,Consumer<User> updateUser) { this(client,updateUser,() -> {}); }
    ProfilePanel(Client client,Consumer<User> updateUser,Runnable reconnect) {
        super(client); name.setName("displayName"); email.setName("profileEmail"); amount.setName("topUpAmount");
        current.setName("currentPassword"); replacement.setName("newPassword"); repeat.setName("repeatPassword"); emailPassword.setName("emailPassword");
        repeatError.setName("repeatPasswordError"); currentError.setName("currentPasswordError"); emailPasswordError.setName("emailPasswordError");
        nickname.setName("profileDisplayName");
        this.updateUser = updateUser;
        JPanel body = new ScrollableForm();
        JPanel header = new JPanel(new BorderLayout(Ui.scale(16),0)); header.add(avatar,BorderLayout.WEST);
        JPanel identity = new JPanel(new GridLayout(3,1,0,4)); identity.add(nickname); identity.add(address); identity.add(account); header.add(identity,BorderLayout.CENTER); body.add(header);
        JButton choose = Ui.button("Выбрать PNG / JPEG");
        body.add(Ui.row(choose,saveAvatar,cancelAvatar,removeAvatar)); body.add(avatarError); gap(body);
        saveAvatar.setVisible(false); cancelAvatar.setVisible(false); removeAvatar.setEnabled(false);
        choose.addActionListener(e -> chooseAvatar()); cancelAvatar.addActionListener(e -> { selectedBytes = null; avatar.display(savedImage,displayName()); previewState(); avatarError.setText(" "); });
        saveAvatar.addActionListener(e -> { byte[] bytes = selectedBytes; if (bytes != null) work(() -> { client.saveAvatar(bytes); return load(); },loaded -> { display(loaded); status.setText("Аватар сохранён."); },error -> avatarError.setText(error.getMessage())); });
        removeAvatar.addActionListener(e -> work(() -> { client.removeAvatar(); return load(); },loaded -> { display(loaded); status.setText("Аватар удалён."); },error -> avatarError.setText(error.getMessage())));

        body.add(section("Личные данные")); body.add(field("Отображаемый ник",name,nameError));
        JButton saveName = Ui.primary("Сохранить ник"); body.add(Ui.row(saveName));
        saveName.addActionListener(e -> { clearErrors(); String value = name.getText(); work(() -> client.updateName(value),updated -> { updateIdentity(updated); name.setText(updated.displayName()); status.setText("Ник сохранён."); },error -> nameError.setText(error.getMessage())); });
        body.add(field("Email · пока не подтверждается",email,emailError)); body.add(field("Текущий пароль для изменения email",emailPassword,emailPasswordError));
        JButton saveEmail = Ui.primary("Сохранить email"); body.add(Ui.row(showPassword(emailPassword),saveEmail));
        saveEmail.addActionListener(e -> { clearErrors(); String value = email.getText(),password = secret(emailPassword); work(() -> client.updateEmail(value,password),updated -> { emailPassword.setText(""); updateIdentity(updated); email.setText(updated.email()); status.setText("Email сохранён. Не подтверждён."); },error -> (error.code().equals("CURRENT_PASSWORD") ? emailPasswordError : emailError).setText(error.getMessage())); });
        gap(body); body.add(section("Безопасность")); body.add(field("Текущий пароль",current,currentError)); body.add(field("Новый пароль · от 8 символов, до 72 байт UTF-8",replacement,newError)); body.add(field("Повтор нового пароля",repeat,repeatError));
        JButton change = Ui.primary("Изменить пароль"),login = Ui.button("Войти снова"); login.setVisible(false); login.addActionListener(e -> reconnect.run());
        body.add(Ui.row(showPassword(current,replacement,repeat),change,login));
        change.addActionListener(e -> {
            clearErrors(); String old = secret(current),next = secret(replacement),confirmation = secret(repeat);
            if (!next.equals(confirmation)) { repeatError.setText("Новые пароли не совпадают."); return; }
            work(() -> { client.changePassword(old,next,confirmation); return true; },ok -> { current.setText(""); replacement.setText(""); repeat.setText(""); emailPassword.setText(""); login.setVisible(true); status.setText("Пароль изменён. Все сессии завершены. Войдите снова."); },error -> {
                JLabel target = switch (error.code()) { case "CURRENT_PASSWORD" -> currentError; case "PASSWORD_MISMATCH" -> repeatError; default -> newError; }; target.setText(error.getMessage());
            });
        });
        gap(body); body.add(section("Учебный баланс")); body.add(balance); body.add(Ui.row(Ui.label("Сумма, KZT"),amount,topUp)); body.add(operationLabel);
        topUp.addActionListener(e -> {
            if (pending == null) { pending = UUID.randomUUID(); pendingAmount = amount.getText().trim().replace(',','.'); }
            UUID operation = pending; String addition = pendingAmount; pendingState();
            work(() -> { client.topUp(addition,operation); return client.profile(); },updated -> { pending = null; pendingAmount = null; updateIdentity(updated); pendingState(); status.setText("Учебный баланс пополнен."); },error -> { if (!Ui.uncertain(error)) { pending = null; pendingAmount = null; } pendingState(); });
        });
        for (Component child : body.getComponents()) if (child instanceof JComponent component) { component.setAlignmentX(LEFT_ALIGNMENT); Dimension preferred = component.getPreferredSize(); component.setMaximumSize(new Dimension(Integer.MAX_VALUE,preferred.height)); }
        JScrollPane scroll = Ui.scroll(body); scroll.getVerticalScrollBar().setUnitIncrement(Ui.scale(24)); add(scroll,BorderLayout.CENTER); pendingState();
    }
    private Loaded load() throws IOException { User user = client.profile(); String encoded = client.avatar(); BufferedImage image = encoded.isEmpty() ? null : ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(encoded))); return new Loaded(user,image); }
    @Override void refresh() { work(this::load,this::display); }
    private void display(Loaded loaded) { savedImage = loaded.image; selectedBytes = null; updateIdentity(loaded.user); name.setText(loaded.user.displayName()); email.setText(loaded.user.email() == null ? "" : loaded.user.email()); previewState(); clearErrors(); pendingState(); }
    private String displayName() { return user == null ? "GS" : user.displayName(); }
    private void updateIdentity(User updated) {
        user = updated; updateUser.accept(updated); nickname.setText(updated.displayName()); address.setText(updated.email() == null ? "Email не указан" : updated.email() + " · не подтверждён");
        account.setText("Логин: " + updated.login() + " · " + ("ADMIN".equals(updated.role()) ? "Администратор" : "Пользователь")); balance.setText("Баланс: " + Ui.money(updated.balance())); if (selectedBytes == null) avatar.display(savedImage,displayName());
    }
    private void chooseAvatar() {
        JFileChooser chooser = new JFileChooser(); chooser.setDialogTitle("Выберите аватар · PNG / JPEG до 2 МБ"); chooser.setAcceptAllFileFilterUsed(false); chooser.setFileFilter(new FileNameExtensionFilter("PNG и JPEG","png","jpg","jpeg"));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        previewAvatar(chooser.getSelectedFile().toPath());
    }
    void previewAvatar(Path file) {
        avatarError.setText(" ");
        work(() -> {
            byte[] bytes;
            try (InputStream input = Files.newInputStream(file)) { bytes = input.readNBytes(AvatarImages.MAX_BYTES + 1); }
            byte[] cropped = AvatarImages.prepare(bytes); return new Preview(bytes,ImageIO.read(new ByteArrayInputStream(cropped)));
        },preview -> { selectedBytes = preview.bytes; avatar.display(preview.image,displayName()); previewState(); status.setText("Предпросмотр. Нажмите «Сохранить аватар»."); },error -> avatarError.setText("Выберите корректный PNG/JPEG до 2 МБ, размером 32–4096 пикселей."));
    }
    private void previewState() { saveAvatar.setVisible(selectedBytes != null); cancelAvatar.setVisible(selectedBytes != null); removeAvatar.setEnabled(savedImage != null); revalidate(); }
    private void pendingState() { amount.setEnabled(pending == null); topUp.setText(pending == null ? "Пополнить демо-баланс" : "Повторить демо-пополнение"); operationLabel.setText(pending == null ? "От 0.01 до 100000.00 KZT. Без реальных списаний." : "UUID: " + pending + " · " + pendingAmount + " KZT"); }
    private void clearErrors() { for (JLabel label : new JLabel[]{nameError,emailError,emailPasswordError,currentError,newError,repeatError,avatarError}) label.setText(" "); }
    private static JLabel error() { JLabel label = Ui.label(" "); label.setForeground(new Color(0xD7A2A2)); label.setVisible(false); label.addPropertyChangeListener("text",e -> { label.setToolTipText(label.getText().strip()); label.setVisible(!label.getText().isBlank()); }); return label; }
    private static JPanel section(String title) { return Ui.row(Ui.heading(title)); }
    private static JPanel field(String title,JComponent input,JLabel error) { JPanel panel = new JPanel(new BorderLayout(0,Ui.scale(4))); panel.add(Ui.label(title),BorderLayout.NORTH); panel.add(input,BorderLayout.CENTER); panel.add(error,BorderLayout.SOUTH); panel.setBorder(BorderFactory.createEmptyBorder(Ui.scale(4),0,0,0)); return panel; }
    private static void gap(JPanel panel) { panel.add(Box.createVerticalStrut(Ui.scale(12))); }
    private static String secret(JPasswordField field) { char[] chars = field.getPassword(); try { return new String(chars); } finally { Arrays.fill(chars,'\0'); } }
    private static JCheckBox showPassword(JPasswordField... fields) { JCheckBox show = new JCheckBox("Показать пароль"); char[] echoes = new char[fields.length]; for (int i = 0; i < fields.length; i++) echoes[i] = fields[i].getEchoChar(); show.addActionListener(e -> { for (int i = 0; i < fields.length; i++) fields[i].setEchoChar(show.isSelected() ? (char)0 : echoes[i]); }); return show; }
}
