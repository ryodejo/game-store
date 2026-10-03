package gamestore.server;

import com.google.gson.*;
import gamestore.shared.Protocol;
import gamestore.shared.Protocol.Response;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;

/** One instance per TCP connection; the user ID never comes from request arguments. */
final class Dispatcher {
    private final Store store;
    private Long userId;
    private long sessionVersion;
    Dispatcher(Store store) { this.store = store; }
    Response handle(String line) {
        try {
            JsonObject request = Protocol.object(line);
            fields(request,"version","command","args");
            if (!request.has("version") || !request.get("version").toString().equals("1"))
                return Response.error("VERSION", "Неподдерживаемая версия протокола.");
            String command = string(request,"command");
            if (line.length() > (command.equals("SET_AVATAR") ? Protocol.MAX_AVATAR_REQUEST : Protocol.MAX_REQUEST)) throw new StoreException("FRAME_TOO_LARGE","TCP-сообщение слишком длинное.");
            JsonObject args = request.getAsJsonObject("args");
            if (args == null) throw Validation.invalid("Не переданы аргументы запроса.");
            if (!Set.of("REGISTER","LOGIN","CATALOG").contains(command) && userId == null)
                throw new StoreException("UNAUTHORIZED", "Войдите в аккаунт.");
            if (userId != null && !Set.of("REGISTER","LOGIN").contains(command)) store.users.requireSession(userId,sessionVersion);
            Object result = switch (command) {
                case "REGISTER" -> { fields(args,"login","password","email"); yield store.users.register(string(args,"login"),string(args,"password"),optional(args,"email","")); }
                case "LOGIN" -> {
                    userId = null;
                    fields(args,"login","password");
                    var authenticated = store.users.authenticate(string(args,"login"),string(args,"password"));
                    userId = authenticated.user().id(); sessionVersion = authenticated.version(); yield authenticated.user();
                }
                case "LOGOUT" -> { fields(args); userId = null; yield "Вы вышли из аккаунта."; }
                case "CATALOG" -> {
                    fields(args,"search","genre","min","max","sort");
                    yield store.games.catalog(optional(args,"search",""),optional(args,"genre",""),money(args,"min",true),money(args,"max",true),optional(args,"sort","TITLE"));
                }
                case "PROFILE" -> { fields(args); yield store.users.profile(userId); }
                case "UPDATE_NAME" -> { fields(args,"displayName"); yield store.users.updateName(userId,string(args,"displayName")); }
                case "UPDATE_EMAIL" -> { fields(args,"email","currentPassword"); yield store.users.updateEmail(userId,string(args,"email"),string(args,"currentPassword")); }
                case "CHANGE_PASSWORD" -> {
                    fields(args,"currentPassword","newPassword","repeatPassword"); store.users.changePassword(userId,string(args,"currentPassword"),string(args,"newPassword"),string(args,"repeatPassword")); userId = null; yield "Пароль изменён. Войдите снова.";
                }
                case "AVATAR" -> { fields(args); yield store.users.avatar(userId); }
                case "SET_AVATAR" -> { fields(args,"image"); store.users.saveAvatar(userId,string(args,"image")); yield "Аватар сохранён."; }
                case "REMOVE_AVATAR" -> { fields(args); store.users.removeAvatar(userId); yield "Аватар удалён."; }
                case "CART" -> { fields(args); yield store.cart.view(userId); }
                case "ADD_CART" -> { fields(args,"gameId"); store.cart.add(userId,id(args,"gameId")); yield "Игра добавлена в корзину."; }
                case "REMOVE_CART" -> { fields(args,"gameId"); store.cart.remove(userId,id(args,"gameId")); yield "Игра удалена из корзины."; }
                case "CHECKOUT" -> { fields(args,"operationId"); yield store.orders.checkout(userId,operation(args)); }
                case "BUY" -> { fields(args,"gameId","operationId"); yield store.orders.buy(userId,operation(args),id(args,"gameId")); }
                case "TOP_UP" -> { fields(args,"amount","operationId"); yield store.users.topUp(userId,operation(args),money(args,"amount",false)); }
                case "ORDERS" -> { fields(args); yield store.orders.history(userId); }
                case "ORDER_DETAIL" -> { fields(args,"orderId"); yield store.orders.detail(userId,id(args,"orderId")); }
                case "LIBRARY" -> { fields(args); yield store.orders.library(userId); }
                case "ADMIN_GAMES" -> { fields(args); yield store.games.adminCatalog(userId); }
                case "ADMIN_SAVE" -> {
                    fields(args,"id","title","genre","price","image","active");
                    long id = number(args,"id",true);
                    JsonElement active = args.get("active");
                    if (active == null || !active.isJsonPrimitive() || !active.getAsJsonPrimitive().isBoolean()) throw Validation.invalid("Некорректный статус игры.");
                    yield store.games.save(userId,id,string(args,"title"),string(args,"genre"),money(args,"price",false),string(args,"image"),active.getAsBoolean());
                }
                default -> throw new StoreException("UNKNOWN_COMMAND", "Неизвестная команда.");
            };
            return Response.success(result);
        } catch (StoreException e) { if ("UNAUTHORIZED".equals(e.code())) userId = null; return Response.error(e.code(),e.getMessage()); }
        catch (SQLException e) {
            System.err.println("Ошибка БД при обработке запроса; SQLState=" + e.getSQLState());
            return Response.error("DATABASE", "Операция не выполнена. Проверьте доступность БД и повторите запрос с тем же идентификатором операции.");
        } catch (RuntimeException e) {
            return Response.error("BAD_REQUEST", "Некорректное TCP-сообщение или аргументы.");
        }
    }
    private static void fields(JsonObject object, String... names) {
        Set<String> allowed = Set.of(names);
        if (!allowed.containsAll(object.keySet())) throw Validation.invalid("Запрос содержит неизвестные поля.");
    }
    private static String string(JsonObject object,String name) {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw Validation.invalid("Ожидается строка: " + name);
        return value.getAsString();
    }
    private static String optional(JsonObject object,String name,String fallback) { return object.has(name) ? string(object,name) : fallback; }
    private static BigDecimal money(JsonObject object,String name,boolean optional) {
        if (optional && (!object.has(name) || object.get(name).isJsonNull())) return null;
        return Validation.money(string(object,name));
    }
    private static UUID operation(JsonObject args) { return Validation.operation(string(args,"operationId")); }
    private static long id(JsonObject args,String name) { return number(args,name,false); }
    private static long number(JsonObject args,String name,boolean zeroAllowed) {
        JsonElement value = args.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber() || !value.toString().matches("[0-9]{1,18}")) throw Validation.invalid("Некорректный ID: " + name);
        long number = value.getAsLong();
        if (number < (zeroAllowed ? 0 : 1)) throw Validation.invalid("Некорректный ID: " + name);
        return number;
    }
}
