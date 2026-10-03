package gamestore.server;

import gamestore.shared.Models.*;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

public final class OrderService {
    private final Database db;
    public OrderService(Database db) { this.db = db; }
    public Payment checkout(long userId, UUID operationId) throws SQLException { return purchase(userId,operationId,null); }
    public Payment buy(long userId, UUID operationId, long gameId) throws SQLException { return purchase(userId,operationId,gameId); }
    private Payment purchase(long userId, UUID operationId, Long gameId) throws SQLException {
        String requestKey = gameId == null ? "CART" : "BUY:" + gameId;
        try {
            return Sql.transaction(db,c -> {
                User user = UserService.lock(c,userId);
                var prior = Sql.one(c,"SELECT * FROM payment_attempts WHERE operation_id=?",
                    rs -> new Prior(rs.getLong("user_id"),rs.getString("request_key"), payment(rs)), operationId);
                if (prior != null) {
                    if (prior.userId != userId || !prior.requestKey.equals(requestKey)) throw UserService.operationConflict();
                    return prior.payment;
                }
                // User lock serializes all cart/balance/library mutations; game locks freeze current prices.
                List<Game> games = gameId == null
                    ? Sql.list(c,"SELECT g.* FROM games g JOIN cart_items ci ON ci.game_id=g.id WHERE ci.user_id=? ORDER BY g.id FOR SHARE OF g",GameService::game,userId)
                    : Sql.list(c,"SELECT * FROM games WHERE id=? FOR SHARE",GameService::game,gameId);
                if (games.isEmpty()) throw new StoreException(gameId == null ? "CART_EMPTY" : "NOT_FOUND",gameId == null ? "Корзина пуста." : "Игра не найдена.");
                BigDecimal total = new BigDecimal("0.00");
                for (Game game : games) {
                    if (!game.active()) throw new StoreException("NOT_FOR_SALE", "Игра «" + game.title() + "» скрыта из продажи. Удалите её из корзины.");
                    CartService.requireNotOwned(c,userId,game.id());
                    total = total.add(game.price());
                }
                total = Validation.money(total);
                long orderId = Sql.one(c,"INSERT INTO orders(user_id,operation_id,total,status) VALUES (?,?,?,'PENDING') RETURNING id",rs -> rs.getLong(1),userId,operationId,total);
                for (Game game : games) Sql.update(c,"INSERT INTO order_items(order_id,game_id,title,price) VALUES (?,?,?,?)",orderId,game.id(),game.title(),game.price());
                boolean sufficient = user.balance().compareTo(total) >= 0;
                BigDecimal balance = sufficient ? user.balance().subtract(total) : user.balance();
                String status = sufficient ? "SUCCEEDED" : "DECLINED";
                if (sufficient) {
                    Sql.update(c,"UPDATE users SET balance=? WHERE id=?",balance,userId);
                    for (Game game : games) Sql.update(c,"INSERT INTO library(user_id,game_id,order_id) VALUES (?,?,?)",userId,game.id(),orderId);
                    if (gameId == null) Sql.update(c,"DELETE FROM cart_items WHERE user_id=?",userId);
                    else Sql.update(c,"DELETE FROM cart_items WHERE user_id=? AND game_id=?",userId,gameId);
                }
                Sql.update(c,"UPDATE orders SET status=? WHERE id=?",sufficient ? "PAID" : "DECLINED",orderId);
                Sql.update(c,"INSERT INTO payment_attempts(operation_id,request_key,user_id,order_id,amount,status,balance_after) VALUES (?,?,?,?,?,?,?)",operationId,requestKey,userId,orderId,total,status,balance);
                return new Payment(orderId,operationId,status,total,balance);
            });
        } catch (SQLException e) { if ("23505".equals(e.getSQLState())) throw UserService.operationConflict(); throw e; }
    }
    private record Prior(long userId, String requestKey, Payment payment) {}
    private static Payment payment(ResultSet rs) throws SQLException {
        return new Payment(rs.getLong("order_id"),(UUID)rs.getObject("operation_id"),rs.getString("status"),rs.getBigDecimal("amount"),rs.getBigDecimal("balance_after"));
    }
    private static OrderSummary summary(ResultSet rs) throws SQLException {
        return new OrderSummary(rs.getLong("id"),rs.getBigDecimal("total"),rs.getString("currency"),rs.getString("status"),Sql.instant(rs,"created_at"),rs.getString("source"));
    }
    public List<OrderSummary> history(long userId) throws SQLException {
        try (Connection c = db.open()) { return Sql.list(c,"SELECT * FROM orders WHERE user_id=? ORDER BY created_at DESC,id DESC LIMIT 200",OrderService::summary,userId); }
    }
    public OrderDetail detail(long userId, long orderId) throws SQLException {
        try (Connection c = db.open()) {
            // Ownership is in SQL, including for administrator sessions.
            OrderSummary order = Sql.one(c,"SELECT * FROM orders WHERE id=? AND user_id=?",OrderService::summary,orderId,userId);
            if (order == null) throw new StoreException("NOT_FOUND", "Заказ не найден в вашем аккаунте.");
            List<OrderItem> items = Sql.list(c,"SELECT * FROM order_items WHERE order_id=? ORDER BY game_id",rs -> new OrderItem(rs.getLong("game_id"),rs.getString("title"),rs.getBigDecimal("price")),orderId);
            String operation = Sql.one(c,"SELECT operation_id FROM orders WHERE id=? AND user_id=?",rs -> rs.getString(1),orderId,userId);
            String paymentStatus = Sql.one(c,"SELECT status FROM payment_attempts WHERE order_id=? AND user_id=?",rs -> rs.getString(1),orderId,userId);
            return new OrderDetail(order,items,operation,paymentStatus == null ? "NO_PAYMENT" : paymentStatus);
        }
    }
    public List<LibraryGame> library(long userId) throws SQLException {
        try (Connection c = db.open()) {
            return Sql.list(c,"SELECT g.*,l.acquired_at FROM library l JOIN games g ON g.id=l.game_id WHERE l.user_id=? ORDER BY g.title LIMIT 1000",
                    rs -> new LibraryGame(rs.getLong("id"),rs.getString("title"),rs.getString("genre"),rs.getString("image"),Sql.instant(rs,"acquired_at")),userId);
        }
    }
}
