package gamestore.client.ui;

import gamestore.client.Client;
import gamestore.client.ClientException;
import java.awt.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import javax.swing.*;

abstract class TaskPanel extends JPanel {
    protected final Client client;
    protected final JLabel status = Ui.label("Готово");
    private boolean busy;
    private final JProgressBar progress = new JProgressBar();
    TaskPanel(Client client) {
        super(new BorderLayout(12,12)); this.client = client;
        setBorder(Ui.padding(18));
        status.setForeground(Ui.MUTED); JPanel footer = new JPanel(new BorderLayout(8,0)); footer.add(status,BorderLayout.CENTER);
        progress.setIndeterminate(true); progress.setPreferredSize(new Dimension(Ui.scale(70),Ui.scale(4))); progress.setVisible(false); footer.add(progress,BorderLayout.EAST); add(footer,BorderLayout.SOUTH);
    }
    protected final <T> void work(Callable<T> request,Consumer<T> success) { work(request,success,e -> {}); }
    protected final <T> void work(Callable<T> request,Consumer<T> success,Consumer<ClientException> failure) {
        if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("UI task must start on EDT");
        if (busy) return;
        busy = true; status.setForeground(Ui.MUTED); status.setToolTipText(null); status.setText("Загрузка…"); progress.setVisible(true);
        Map<Component,Boolean> enabled = new IdentityHashMap<>(); disable(this,enabled);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        new SwingWorker<T,Void>() {
            @Override protected T doInBackground() throws Exception { return request.call(); }
            @Override protected void done() {
                busy = false; enabled.forEach(Component::setEnabled); progress.setVisible(false); setCursor(Cursor.getDefaultCursor());
                if (!isDisplayable()) return;
                try { T result = get(); status.setText("Готово"); success.accept(result); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                catch (ExecutionException e) {
                    ClientException error = e.getCause() instanceof ClientException ce ? ce : new ClientException("CLIENT","Не удалось выполнить операцию. Проверьте введённые данные.");
                    failure.accept(error); status.setForeground(new Color(0xD7A2A2)); status.setText(error.getMessage()); status.setToolTipText(error.getMessage());
                } catch (RuntimeException e) { status.setForeground(new Color(0xD7A2A2)); status.setText("Не удалось отобразить ответ сервера."); }
            }
        }.execute();
    }
    private static void disable(Component component,Map<Component,Boolean> enabled) {
        enabled.put(component,component.isEnabled()); component.setEnabled(false);
        if (component instanceof Container container) for (Component child : container.getComponents()) disable(child,enabled);
    }
    abstract void refresh();
}
