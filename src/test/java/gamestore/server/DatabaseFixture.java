package gamestore.server;

import java.sql.*;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;

public final class DatabaseFixture implements AutoCloseable {
    public final Database db;
    private final Database base;
    private final String schema;
    public DatabaseFixture() throws SQLException {
        this(null);
    }
    public DatabaseFixture(String migrationTarget) throws SQLException {
        String url = System.getenv("GAMESTORE_TEST_DB_URL");
        Assumptions.assumeTrue(url != null && !url.isBlank(),"Set GAMESTORE_TEST_DB_URL to a dedicated PostgreSQL test database.");
        String user = System.getenv("GAMESTORE_DB_USER"), password = System.getenv("GAMESTORE_DB_PASSWORD");
        if (user == null || password == null) throw new IllegalStateException("Test database credentials missing");
        // Only a generated schema is touched, never existing tables or databases.
        schema = "gs_test_" + UUID.randomUUID().toString().replace("-", "");
        base = new Database(url,user,password);
        try (Connection c = base.open(); Statement s = c.createStatement()) { s.execute("CREATE SCHEMA " + schema); }
        db = new Database(url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema,user,password);
        if (migrationTarget == null) db.migrate();
        else org.flywaydb.core.Flyway.configure().dataSource(url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schema,user,password)
                .locations("classpath:db/migration").cleanDisabled(true).target(migrationTarget).load().migrate();
    }
    @Override public void close() throws SQLException {
        if (!schema.matches("gs_test_[a-f0-9]{32}")) throw new IllegalStateException("Unsafe test schema");
        try (Connection c = base.open(); Statement s = c.createStatement()) { s.execute("DROP SCHEMA " + schema + " CASCADE"); }
    }
}
