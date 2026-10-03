package gamestore.server;

import gamestore.shared.Protocol;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.*;

public final class TcpServer implements AutoCloseable {
    private final ServerSocket listener;
    private final Store store;
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(16,16,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(32));
    private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
    private Thread acceptThread;
    public TcpServer(Store store,String host,int port) throws IOException {
        this.store = store; listener = new ServerSocket();
        try { listener.bind(new InetSocketAddress(host,port)); }
        catch (IOException e) { listener.close(); throw e; }
    }
    public int port() { return listener.getLocalPort(); }
    public void start() {
        if (acceptThread != null) throw new IllegalStateException("Server already started");
        acceptThread = new Thread(this::accept,"gamestore-accept"); acceptThread.start();
    }
    private void accept() {
        while (!listener.isClosed()) {
            try {
                Socket socket = listener.accept();
                socket.setSoTimeout(300_000); // idle session lifetime
                sockets.add(socket);
                try { workers.execute(() -> handle(socket)); }
                catch (RejectedExecutionException e) { sockets.remove(socket); socket.close(); }
            } catch (IOException e) { if (!listener.isClosed()) System.err.println("Не удалось принять соединение."); }
        }
    }
    private void handle(Socket socket) {
        Dispatcher session = new Dispatcher(store);
        try (socket;
             Reader in = new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
             Writer out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.UTF_8))) {
            for (;;) {
                String line;
                try { line = Protocol.readLine(in,Protocol.MAX_AVATAR_REQUEST); }
                catch (Protocol.FrameTooLargeException e) {
                    send(out,Protocol.Response.error("FRAME_TOO_LARGE", "TCP-сообщение слишком длинное.")); break;
                }
                if (line == null) break;
                if (line.length() > Protocol.MAX_REQUEST) {
                    boolean avatar = false;
                    try { avatar = "SET_AVATAR".equals(Protocol.object(line).get("command").getAsString()); } catch (RuntimeException ignored) {}
                    if (!avatar) { send(out,Protocol.Response.error("FRAME_TOO_LARGE","TCP-сообщение слишком длинное.")); break; }
                }
                send(out,session.handle(line));
            }
        } catch (IOException e) { /* Disconnected, partial frame, or idle timeout: close this session. */ }
        finally { sockets.remove(socket); }
    }
    private static void send(Writer out,Protocol.Response response) throws IOException {
        String json = Protocol.JSON.toJson(response);
        if (json.length() > Protocol.MAX_RESPONSE) json = Protocol.JSON.toJson(Protocol.Response.error("RESULT_TOO_LARGE","Уточните фильтры запроса."));
        out.write(json); out.write('\n'); out.flush();
    }
    @Override public void close() {
        try { listener.close(); } catch (IOException ignored) {}
        for (Socket socket : sockets) { try { socket.close(); } catch (IOException ignored) {} }
        workers.shutdownNow();
        try {
            if (acceptThread != null && acceptThread != Thread.currentThread()) acceptThread.join(2000);
            workers.awaitTermination(5,TimeUnit.SECONDS);
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
