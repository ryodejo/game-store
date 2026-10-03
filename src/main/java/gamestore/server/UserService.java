package gamestore.server;

import gamestore.shared.Models.User;
import java.math.BigDecimal;
import java.sql.*;
import java.util.UUID;
import org.mindrot.jbcrypt.BCrypt;

public final class UserService {
    private final Database db;
    public UserService(Database db) { this.db = db; }
    static User user(ResultSet rs) throws SQLException {
        return new User(rs.getLong("id"), rs.getString("login"), rs.getString("role"), rs.getBigDecimal("balance"),rs.getString("display_name"),rs.getString("email"),rs.getBoolean("email_verified"));
    }
    public User register(String login, String password) throws SQLException {
        return register(login,password,null);
    }
    public User register(String login,String password,String email) throws SQLException {
        String normalized = Validation.login(login);
        String initialName = normalized.substring(0,Math.min(30,normalized.length()));
        String address = Validation.email(email,false);
        String hash = BCrypt.hashpw(Validation.password(password), BCrypt.gensalt(12));
        try (Connection c = db.open()) {
            return Sql.one(c, "INSERT INTO users(login,password_hash,display_name,email) VALUES (?,?,?,?) RETURNING *", UserService::user, normalized, hash,initialName,address);
        } catch (SQLException e) {
            if ("23505".equals(e.getSQLState())) {
                if (e instanceof org.postgresql.util.PSQLException pg && pg.getServerErrorMessage() != null && "users_email_unique".equals(pg.getServerErrorMessage().getConstraint())) throw new StoreException("EMAIL_TAKEN","Этот email уже используется.");
                throw new StoreException("LOGIN_TAKEN", "Этот логин уже занят.");
            }
            throw e;
        }
    }
    public User login(String login, String password) throws SQLException {
        return authenticate(login,password).user();
    }
    public record Authenticated(User user,long version) {}
    private record Credentials(User user,String hash,long version) {}
    public Authenticated authenticate(String login,String password) throws SQLException {
        boolean byEmail = login != null && login.contains("@");
        String normalized = byEmail ? Validation.email(login,false) : Validation.login(login);
        if (password == null || password.isEmpty() || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) throw new StoreException("BAD_CREDENTIALS","Неверный логин/email или пароль.");
        try (Connection c = db.open()) {
            Credentials credentials = Sql.one(c,"SELECT * FROM users WHERE " + (byEmail ? "email" : "login") + "=?",rs -> new Credentials(user(rs),rs.getString("password_hash"),rs.getLong("session_version")),normalized);
            if (credentials == null || !BCrypt.checkpw(password,credentials.hash)) throw new StoreException("BAD_CREDENTIALS", "Неверный логин/email или пароль.");
            return new Authenticated(credentials.user,credentials.version);
        }
    }
    public void requireSession(long id,long version) throws SQLException {
        try (Connection c = db.open()) {
            Long current = Sql.one(c,"SELECT session_version FROM users WHERE id=?",rs -> rs.getLong(1),id);
            if (current == null || current != version) throw new StoreException("UNAUTHORIZED","Сессия завершена. Войдите снова.");
        }
    }
    private static void checkPassword(Connection c,long id,String password) throws SQLException {
        String hash = Sql.one(c,"SELECT password_hash FROM users WHERE id=?",rs -> rs.getString(1),id);
        if (password == null || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72 || hash == null || !BCrypt.checkpw(password,hash))
            throw new StoreException("CURRENT_PASSWORD","Неверный текущий пароль.");
    }
    public User updateName(long id,String name) throws SQLException {
        String valid = Validation.displayName(name);
        return Sql.transaction(db,c -> { lock(c,id); return Sql.one(c,"UPDATE users SET display_name=? WHERE id=? RETURNING *",UserService::user,valid,id); });
    }
    public User updateEmail(long id,String email,String currentPassword) throws SQLException {
        String valid = Validation.email(email,false);
        try {
            return Sql.transaction(db,c -> { lock(c,id); checkPassword(c,id,currentPassword); return Sql.one(c,"UPDATE users SET email=?,email_verified=FALSE WHERE id=? RETURNING *",UserService::user,valid,id); });
        } catch (SQLException e) { if ("23505".equals(e.getSQLState())) throw new StoreException("EMAIL_TAKEN","Этот email уже используется."); throw e; }
    }
    public void changePassword(long id,String current,String replacement,String repeat) throws SQLException {
        if (replacement == null || !replacement.equals(repeat)) throw new StoreException("PASSWORD_MISMATCH","Новые пароли не совпадают.");
        String hash = BCrypt.hashpw(Validation.password(replacement),BCrypt.gensalt(12));
        Sql.transaction(db,c -> { lock(c,id); checkPassword(c,id,current); Sql.update(c,"UPDATE users SET password_hash=?,session_version=session_version+1 WHERE id=?",hash,id); return null; });
    }
    public String avatar(long id) throws SQLException {
        try (Connection c = db.open()) { requireUser(Sql.one(c,"SELECT * FROM users WHERE id=?",UserService::user,id)); byte[] bytes = Sql.one(c,"SELECT avatar_png FROM users WHERE id=?",rs -> rs.getBytes(1),id); return bytes == null ? "" : java.util.Base64.getEncoder().encodeToString(bytes); }
    }
    public void saveAvatar(long id,String encoded) throws SQLException {
        byte[] processed;
        try {
            if (encoded == null || encoded.length() > 2_796_204) throw new IllegalArgumentException("Аватар: файл до 2 МБ.");
            processed = gamestore.shared.AvatarImages.prepare(java.util.Base64.getDecoder().decode(encoded));
        } catch (IllegalArgumentException e) { throw new StoreException("AVATAR_INVALID",e.getMessage().startsWith("Аватар") || e.getMessage().contains("изображ") || e.getMessage().contains("PNG") ? e.getMessage() : "Некорректное изображение PNG/JPEG."); }
        Sql.transaction(db,c -> { lock(c,id); Sql.update(c,"UPDATE users SET avatar_png=? WHERE id=?",processed,id); return null; });
    }
    public void removeAvatar(long id) throws SQLException { Sql.transaction(db,c -> { lock(c,id); Sql.update(c,"UPDATE users SET avatar_png=NULL WHERE id=?",id); return null; }); }
    public User profile(long id) throws SQLException {
        try (Connection c = db.open()) { return requireUser(Sql.one(c, "SELECT * FROM users WHERE id=?", UserService::user, id)); }
    }
    static User lock(Connection c, long id) throws SQLException {
        return requireUser(Sql.one(c, "SELECT * FROM users WHERE id=? FOR UPDATE", UserService::user, id));
    }
    private static User requireUser(User user) {
        if (user == null) throw new StoreException("UNAUTHORIZED", "Войдите в аккаунт.");
        return user;
    }
    static void requireAdmin(Connection c, long id) throws SQLException {
        String role = Sql.one(c, "SELECT role FROM users WHERE id=?", rs -> rs.getString(1), id);
        if (!"ADMIN".equals(role)) throw new StoreException("FORBIDDEN", "Это действие доступно только администратору.");
    }
    public BigDecimal topUp(long userId, UUID operationId, BigDecimal amount) throws SQLException {
        BigDecimal addition = Validation.money(amount);
        if (addition.signum() <= 0 || addition.compareTo(new BigDecimal("100000")) > 0)
            throw Validation.invalid("Демо-пополнение: от 0.01 до 100000.00 KZT.");
        try {
            return Sql.transaction(db, c -> {
                User user = lock(c, userId);
                var prior = Sql.one(c, "SELECT user_id,amount,balance_after FROM balance_operations WHERE operation_id=?",
                        rs -> new Object[]{rs.getLong(1),rs.getBigDecimal(2),rs.getBigDecimal(3)}, operationId);
                if (prior != null) {
                    if ((long)prior[0] != userId || ((BigDecimal)prior[1]).compareTo(addition) != 0) throw operationConflict();
                    return (BigDecimal)prior[2];
                }
                BigDecimal balance = Validation.money(user.balance().add(addition));
                Sql.update(c, "UPDATE users SET balance=? WHERE id=?", balance, userId);
                Sql.update(c, "INSERT INTO balance_operations(operation_id,user_id,amount,balance_after) VALUES (?,?,?,?)", operationId,userId,addition,balance);
                return balance;
            });
        } catch (SQLException e) { if ("23505".equals(e.getSQLState())) throw operationConflict(); throw e; }
    }
    static StoreException operationConflict() { return new StoreException("OPERATION_CONFLICT", "Идентификатор операции уже используется другим запросом."); }
    public void grantAdmin(String login) throws SQLException {
        try (Connection c = db.open()) {
            if (Sql.update(c, "UPDATE users SET role='ADMIN' WHERE login=?", Validation.login(login)) == 0)
                throw new StoreException("NOT_FOUND", "Зарегистрируйте этот логин перед назначением роли.");
        }
    }
}
