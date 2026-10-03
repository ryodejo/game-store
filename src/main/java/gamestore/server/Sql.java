package gamestore.server;

import java.sql.*;
import java.util.*;

final class Sql {
    private Sql() {}
    @FunctionalInterface interface Mapper<T> { T map(ResultSet rs) throws SQLException; }
    @FunctionalInterface interface Work<T> { T run(Connection connection) throws SQLException; }
    static PreparedStatement prepare(Connection c, String sql, Object... args) throws SQLException {
        PreparedStatement statement = c.prepareStatement(sql);
        try {
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        } catch (SQLException | RuntimeException e) { statement.close(); throw e; }
    }
    static int update(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement s = prepare(c, sql, args)) { return s.executeUpdate(); }
    }
    static <T> List<T> list(Connection c, String sql, Mapper<T> mapper, Object... args) throws SQLException {
        try (PreparedStatement s = prepare(c, sql, args); ResultSet rs = s.executeQuery()) {
            List<T> result = new ArrayList<>();
            while (rs.next()) result.add(mapper.map(rs));
            return result;
        }
    }
    static <T> T one(Connection c, String sql, Mapper<T> mapper, Object... args) throws SQLException {
        List<T> rows = list(c, sql, mapper, args);
        return rows.isEmpty() ? null : rows.get(0);
    }
    static <T> T transaction(Database db, Work<T> work) throws SQLException {
        try (Connection c = db.open()) {
            c.setAutoCommit(false);
            try { T value = work.run(c); c.commit(); return value; }
            catch (SQLException | RuntimeException e) {
                try { c.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
                throw e;
            }
        }
    }
    static String instant(ResultSet rs, String name) throws SQLException { return rs.getTimestamp(name).toInstant().toString(); }
}
