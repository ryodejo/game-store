package gamestore.client;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import gamestore.shared.Models.*;
import gamestore.shared.Protocol;
import java.io.*;
import java.math.BigDecimal;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Type;
import java.util.*;

/** No JDBC here. Call from a worker thread, never the Swing EDT. */
public final class Client implements AutoCloseable {
    private final String host;
    private final int port;
    private volatile Socket socket;
    private Reader in;
    private Writer out;
    public Client() {
        this(System.getenv().getOrDefault("GAMESTORE_HOST","127.0.0.1"),Integer.parseInt(System.getenv().getOrDefault("GAMESTORE_PORT","1234")));
    }
    public Client(String host,int port) { this.host = host; this.port = port; }
    private void connect() throws IOException {
        Socket candidate = new Socket();
        try {
            candidate.connect(new InetSocketAddress(host,port),4000);
            candidate.setSoTimeout(20000);
            in = new BufferedReader(new InputStreamReader(candidate.getInputStream(),StandardCharsets.UTF_8));
            out = new BufferedWriter(new OutputStreamWriter(candidate.getOutputStream(),StandardCharsets.UTF_8));
            socket = candidate;
        } catch (IOException | RuntimeException e) { candidate.close(); throw e; }
    }
    public synchronized JsonElement request(String command,Map<String,?> args) {
        try {
            if (socket == null || socket.isClosed()) connect();
            String request = Protocol.JSON.toJson(new Protocol.Request(Protocol.VERSION,command,Protocol.JSON.toJsonTree(args).getAsJsonObject()));
            if (request.length() > (command.equals("SET_AVATAR") ? Protocol.MAX_AVATAR_REQUEST : Protocol.MAX_REQUEST)) throw new ClientException("VALIDATION","Запрос слишком длинный.");
            out.write(request); out.write('\n'); out.flush();
            String line = Protocol.readLine(in,Protocol.MAX_RESPONSE);
            if (line == null) throw new EOFException();
            var response = Protocol.JSON.fromJson(Protocol.object(line),Protocol.Response.class);
            if (response.version() != Protocol.VERSION || response.code() == null || response.message() == null) throw new IOException("Protocol mismatch");
            if (!response.ok()) throw new ClientException(response.code(),response.message());
            return response.data();
        } catch (IOException | JsonParseException e) {
            close();
            throw new ClientException("NETWORK","Сервер недоступен или соединение прервано. Войдите снова. Если отправлялась оплата, повторите её с сохранённым UUID: списание не повторится.");
        }
    }
    private <T> T call(String command,Map<String,?> args,Type type) { return Protocol.JSON.fromJson(request(command,args),type); }
    private static <T> Type listType(Class<T> type) { return TypeToken.getParameterized(List.class,type).getType(); }
    public User register(String login,String password) { return call("REGISTER",Map.of("login",login,"password",password),User.class); }
    public User register(String login,String password,String email) { return call("REGISTER",Map.of("login",login,"password",password,"email",email),User.class); }
    public User updateName(String name) { return call("UPDATE_NAME",Map.of("displayName",name),User.class); }
    public User updateEmail(String email,String currentPassword) { return call("UPDATE_EMAIL",Map.of("email",email,"currentPassword",currentPassword),User.class); }
    public void changePassword(String current,String replacement,String repeat) { request("CHANGE_PASSWORD",Map.of("currentPassword",current,"newPassword",replacement,"repeatPassword",repeat)); }
    public String avatar() { return call("AVATAR",Map.of(),String.class); }
    public void saveAvatar(byte[] bytes) { request("SET_AVATAR",Map.of("image",Base64.getEncoder().encodeToString(bytes))); }
    public void removeAvatar() { request("REMOVE_AVATAR",Map.of()); }
    public User login(String login,String password) { return call("LOGIN",Map.of("login",login,"password",password),User.class); }
    public User profile() { return call("PROFILE",Map.of(),User.class); }
    public List<Game> catalog(String search,String genre,String min,String max,String sort) {
        Map<String,Object> args = new HashMap<>(Map.of("search",search,"genre",genre,"sort",sort));
        if (!min.isBlank()) args.put("min",min);
        if (!max.isBlank()) args.put("max",max);
        return call("CATALOG",args,listType(Game.class));
    }
    public Cart cart() { return call("CART",Map.of(),Cart.class); }
    public void addToCart(long id) { request("ADD_CART",Map.of("gameId",id)); }
    public void removeFromCart(long id) { request("REMOVE_CART",Map.of("gameId",id)); }
    public Payment checkout(UUID operationId) { return call("CHECKOUT",Map.of("operationId",operationId.toString()),Payment.class); }
    public Payment buy(long id,UUID operationId) { return call("BUY",Map.of("gameId",id,"operationId",operationId.toString()),Payment.class); }
    public BigDecimal topUp(String amount,UUID operationId) { return call("TOP_UP",Map.of("amount",amount,"operationId",operationId.toString()),BigDecimal.class); }
    public List<OrderSummary> orders() { return call("ORDERS",Map.of(),listType(OrderSummary.class)); }
    public OrderDetail order(long id) { return call("ORDER_DETAIL",Map.of("orderId",id),OrderDetail.class); }
    public List<LibraryGame> library() { return call("LIBRARY",Map.of(),listType(LibraryGame.class)); }
    public List<Game> adminGames() { return call("ADMIN_GAMES",Map.of(),listType(Game.class)); }
    public Game saveGame(long id,String title,String genre,String price,String image,boolean active) {
        return call("ADMIN_SAVE",Map.of("id",id,"title",title,"genre",genre,"price",price,"image",image,"active",active),Game.class);
    }
    @Override public void close() {
        Socket current = socket;
        if (current != null) { try { current.close(); } catch (IOException ignored) {} }
        // Keep the closed socket reference so concurrent close interrupts blocking reads.
    }
}
