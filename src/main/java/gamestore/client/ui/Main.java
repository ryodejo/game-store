package gamestore.client.ui;

import gamestore.client.Client;
import javax.swing.SwingUtilities;

public final class Main {
    private Main() {}
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Ui.theme();
            try { new LoginFrame(new Client()); }
            catch (RuntimeException e) { Ui.message(null,"Не удалось запустить клиент. Проверьте GAMESTORE_HOST и GAMESTORE_PORT."); }
        });
    }
}
