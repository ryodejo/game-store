package gamestore.server;

import java.sql.*;
import java.util.Properties;
import org.flywaydb.core.Flyway;

/** Server-only JDBC connection factory. Each request owns and closes its connection. */
public final class Database {
    private final String url, user, password;
    public Database(String url, String user, String password) {
        this.url = url; this.user = user; this.password = password;
    }
    public static Database fromEnvironment() {
        return new Database(required("GAMESTORE_DB_URL"), required("GAMESTORE_DB_USER"), required("GAMESTORE_DB_PASSWORD"));
    }
    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Задайте переменную " + name);
        return value;
    }
    public Connection open() throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", user); props.setProperty("password", password);
        props.setProperty("connectTimeout", "5"); props.setProperty("socketTimeout", "15");
        props.setProperty("options", "-c statement_timeout=10000 -c lock_timeout=5000");
        return DriverManager.getConnection(url, props);
    }
    public void migrate() {
        Flyway.configure().dataSource(url, user, password).locations("classpath:db/migration")
                .cleanDisabled(true).load().migrate();
    }
}
