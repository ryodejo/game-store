package gamestore.server;

import java.sql.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseTest {
    @Test void profileMigrationPreservesOldAccountAndPurchasedData() throws Exception {
        try (DatabaseFixture fixture = new DatabaseFixture("2")) {
            String login = "old_" + "x".repeat(28),password = "test-only-migration-password";
            String hash = org.mindrot.jbcrypt.BCrypt.hashpw(password,org.mindrot.jbcrypt.BCrypt.gensalt(4));
            long userId;
            try (Connection c = fixture.db.open()) {
                userId = Sql.one(c,"INSERT INTO users(login,password_hash,role,balance) VALUES (?,?,'ADMIN',7500) RETURNING id",rs -> rs.getLong(1),login,hash);
                long orderId = Sql.one(c,"INSERT INTO orders(user_id,operation_id,total,status) VALUES (?,?,1000,'PAID') RETURNING id",rs -> rs.getLong(1),userId,java.util.UUID.randomUUID());
                Sql.update(c,"INSERT INTO order_items(order_id,game_id,title,price) VALUES (?,2,'Minecraft',1000)",orderId);
                Sql.update(c,"INSERT INTO library(user_id,game_id,order_id) VALUES (?,2,?)",userId,orderId); Sql.update(c,"INSERT INTO cart_items(user_id,game_id) VALUES (?,3)",userId);
            }
            fixture.db.migrate(); Store store = new Store(fixture.db); var restored = store.users.login(login,password);
            assertEquals(login,restored.login()); assertEquals(login.substring(0,30),restored.displayName()); assertNull(restored.email()); assertEquals("ADMIN",restored.role()); assertEquals(new java.math.BigDecimal("7500.00"),restored.balance());
            assertEquals(1,store.orders.history(userId).size()); assertEquals(1,store.orders.library(userId).size()); assertEquals(1,store.cart.view(userId).games().size());
            try (Connection c = fixture.db.open()) { assertEquals(hash,Sql.one(c,"SELECT password_hash FROM users WHERE id=?",rs -> rs.getString(1),userId)); }
            assertEquals("Новый ник",store.users.updateName(userId,"Новый ник").displayName());
            // A fresh service/server still accepts the original login without requiring email.
            assertNull(new Store(fixture.db).users.login(login,password).email());
            assertEquals("old@example.com",store.users.updateEmail(userId,"old@example.com",password).email());
        }
    }
    @Test void databaseRejectsNewAccountsWithoutEmailAndEmailRemoval() throws Exception {
        try (DatabaseFixture fixture = new DatabaseFixture(); Connection c = fixture.db.open()) {
            String hash = org.mindrot.jbcrypt.BCrypt.hashpw("test-only-password",org.mindrot.jbcrypt.BCrypt.gensalt(4));
            SQLException missing = assertThrows(SQLException.class,() -> Sql.update(c,"INSERT INTO users(login,password_hash,display_name) VALUES ('missing_email',?,'Missing')",hash));
            assertEquals("23514",missing.getSQLState());
            long id = Sql.one(c,"INSERT INTO users(login,password_hash,display_name,email) VALUES ('has_email',?,'Has Email','has@example.com') RETURNING id",rs -> rs.getLong(1),hash);
            SQLException removed = assertThrows(SQLException.class,() -> Sql.update(c,"UPDATE users SET email=NULL WHERE id=?",id));
            assertEquals("23514",removed.getSQLState());
            assertEquals("has@example.com",Sql.one(c,"SELECT email FROM users WHERE id=?",rs -> rs.getString(1),id));
        }
    }
    @Test void migrationsAreRepeatableAndEnforceMoneyAndForeignKeys() throws Exception {
        try (DatabaseFixture fixture = new DatabaseFixture()) {
            fixture.db.migrate();
            try (Connection c = fixture.db.open()) {
                assertEquals(20L,Sql.<Long>one(c,"SELECT count(*) FROM games",rs -> rs.getLong(1)).longValue());
                assertEquals(5L,Sql.<Long>one(c,"SELECT count(*) FROM flyway_schema_history WHERE success",rs -> rs.getLong(1)).longValue());
                SQLException money = assertThrows(SQLException.class,() -> Sql.update(c,"INSERT INTO games(title,genre,price) VALUES ('Invalid','Test',-1)"));
                assertEquals("23514",money.getSQLState());
                SQLException fk = assertThrows(SQLException.class,() -> Sql.update(c,"INSERT INTO cart_items(user_id,game_id) VALUES (99999,1)"));
                assertEquals("23503",fk.getSQLState());
            }
        }
    }
}
