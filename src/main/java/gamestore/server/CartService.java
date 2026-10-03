package gamestore.server;

import gamestore.shared.Models.*;
import gamestore.shared.Models;
import java.math.BigDecimal;
import java.sql.*;
import java.util.List;

public final class CartService {
    private final Database db;
    public CartService(Database db) { this.db = db; }
    public Cart view(long userId) throws SQLException {
        return Sql.transaction(db, c -> {
            UserService.lock(c,userId);
            List<Game> games = Sql.list(c,"SELECT g.* FROM games g JOIN cart_items ci ON ci.game_id=g.id WHERE ci.user_id=? ORDER BY g.id",GameService::game,userId);
            return new Cart(games,games.stream().map(Game::price).reduce(new BigDecimal("0.00"), BigDecimal::add),Models.CURRENCY);
        });
    }
    public void add(long userId, long gameId) throws SQLException {
        Sql.transaction(db,c -> {
            UserService.lock(c,userId);
            Game game = Sql.one(c,"SELECT * FROM games WHERE id=? AND active FOR SHARE",GameService::game,gameId);
            if (game == null) throw new StoreException("NOT_FOR_SALE", "Игра недоступна для покупки.");
            requireNotOwned(c,userId,gameId);
            // Repeating an add must remain a no-op, even when the cart is at its limit.
            if (Sql.one(c,"SELECT 1 FROM cart_items WHERE user_id=? AND game_id=?",rs -> rs.getInt(1),userId,gameId) != null) return null;
            long count = Sql.one(c,"SELECT count(*) FROM cart_items WHERE user_id=?",rs -> rs.getLong(1),userId);
            if (count >= 100) throw Validation.invalid("В корзине может быть не более 100 игр.");
            Sql.update(c,"INSERT INTO cart_items(user_id,game_id) VALUES (?,?) ON CONFLICT DO NOTHING",userId,gameId);
            return null;
        });
    }
    static void requireNotOwned(Connection c, long userId, long gameId) throws SQLException {
        if (Sql.one(c,"SELECT 1 FROM library WHERE user_id=? AND game_id=?",rs -> rs.getInt(1),userId,gameId) != null)
            throw new StoreException("ALREADY_OWNED", "Игра уже есть в вашей библиотеке.");
    }
    public void remove(long userId, long gameId) throws SQLException {
        Sql.transaction(db,c -> { UserService.lock(c,userId); Sql.update(c,"DELETE FROM cart_items WHERE user_id=? AND game_id=?",userId,gameId); return null; });
    }
}
