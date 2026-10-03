package gamestore.server;

import gamestore.shared.Models.Game;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

public final class GameService {
    private final Database db;
    public GameService(Database db) { this.db = db; }
    static Game game(ResultSet rs) throws SQLException {
        return new Game(rs.getLong("id"),rs.getString("title"),rs.getString("genre"),rs.getBigDecimal("price"),rs.getString("image"),rs.getBoolean("active"));
    }
    public List<Game> catalog(String search, String genre, BigDecimal min, BigDecimal max, String sort) throws SQLException {
        if (search == null || search.length() > 120 || genre == null || genre.length() > 40) throw Validation.invalid("Слишком длинный поисковый запрос.");
        if (min != null) min = Validation.money(min);
        if (max != null) max = Validation.money(max);
        if (min != null && max != null && min.compareTo(max) > 0) throw Validation.invalid("Минимальная цена превышает максимальную.");
        String ordering = switch (sort) {
            case "TITLE" -> "title ASC, id ASC";
            case "PRICE_ASC" -> "price ASC, title ASC, id ASC";
            case "PRICE_DESC" -> "price DESC, title ASC, id ASC";
            default -> throw Validation.invalid("Неизвестная сортировка.");
        };
        String pattern = "%" + search.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        String sql = "SELECT * FROM games WHERE active AND title ILIKE ? ESCAPE '!' AND (?='' OR genre=?) "
                + "AND price >= ? AND price <= ? ORDER BY " + ordering + " LIMIT 1000";
        try (Connection c = db.open()) {
            return Sql.list(c, sql, GameService::game, pattern,genre,genre,min == null ? BigDecimal.ZERO : min,max == null ? Validation.MAX_MONEY : max);
        }
    }
    public List<Game> adminCatalog(long adminId) throws SQLException {
        try (Connection c = db.open()) {
            UserService.requireAdmin(c, adminId);
            return Sql.list(c, "SELECT * FROM games ORDER BY id LIMIT 1000", GameService::game);
        }
    }
    public Game save(long adminId, long id, String title, String genre, BigDecimal price, String image, boolean active) throws SQLException {
        if (id < 0) throw Validation.invalid("Некорректный ID игры.");
        String name = Validation.text(title,120,"Название");
        String category = Validation.text(genre,40,"Жанр");
        BigDecimal cost = Validation.money(price);
        if (image == null || !image.matches("[\\p{L}\\p{N} _().-]{0,160}")) throw Validation.invalid("Изображение: только имя файла ресурса без пути.");
        return Sql.transaction(db, c -> {
            UserService.requireAdmin(c, adminId);
            Game result;
            if (id == 0) result = Sql.one(c, "INSERT INTO games(title,genre,price,image,active) VALUES (?,?,?,?,?) RETURNING *", GameService::game,name,category,cost,image,active);
            else result = Sql.one(c, "UPDATE games SET title=?,genre=?,price=?,image=?,active=? WHERE id=? RETURNING *", GameService::game,name,category,cost,image,active,id);
            if (result == null) throw new StoreException("NOT_FOUND", "Игра не найдена.");
            return result;
        });
    }
}
