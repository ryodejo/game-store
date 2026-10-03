package gamestore.server;

import gamestore.shared.Models.Game;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.*;

/** Explicit, transactional import. Never modifies orders.txt or guesses old account ownership. */
public final class LegacyImporter {
    private final Database db;
    private static final Pattern ROW = Pattern.compile("User ([0-9]{1,18}) bought game ([0-9]{1,18})");
    public LegacyImporter(Database db) { this.db = db; }
    public String run(Path file,long oldUserId,String targetLogin,boolean apply) throws IOException,SQLException {
        if (oldUserId <= 0) throw Validation.invalid("Старый userId должен быть положительным.");
        if (Files.size(file) > 1_048_576) throw Validation.invalid("Импорт ограничен файлом до 1 MiB.");
        byte[] bytes = Files.readAllBytes(file);
        String fingerprint = sha256(bytes,oldUserId);
        String login = Validation.login(targetLogin);
        List<LegacyRow> selected = new ArrayList<>();
        String[] lines = new String(bytes,StandardCharsets.UTF_8).split("\\R",-1);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].isBlank()) continue;
            Matcher matcher = ROW.matcher(lines[i].trim());
            if (!matcher.matches()) throw Validation.invalid("Неподдерживаемый формат orders.txt, строка " + (i + 1) + ". Импорт отменён.");
            if (Long.parseLong(matcher.group(1)) == oldUserId) selected.add(new LegacyRow(i + 1,Long.parseLong(matcher.group(2))));
        }
        if (selected.isEmpty()) throw Validation.invalid("В файле нет заказов указанного старого userId.");
        return Sql.transaction(db,c -> {
            Long targetId = Sql.one(c,"SELECT id FROM users WHERE login=?",rs -> rs.getLong(1),login);
            if (targetId == null) throw Validation.invalid("Сначала зарегистрируйте целевой аккаунт.");
            UserService.lock(c,targetId);
            Long importedTarget = Sql.one(c,"SELECT target_user_id FROM legacy_imports WHERE fingerprint=?",rs -> rs.getLong(1),fingerprint);
            if (importedTarget != null) {
                if (!importedTarget.equals(targetId)) throw new StoreException("IMPORT_CONFLICT","Этот файл и старый userId уже импортированы в другой аккаунт.");
                return "Этот файл для указанного старого userId уже импортирован. Изменений нет.";
            }
            Map<Long,Game> games = new TreeMap<>();
            // Lock games in ascending order, matching checkout's lock order.
            for (long gameId : selected.stream().map(LegacyRow::gameId).distinct().sorted().toList()) {
                Game game = Sql.one(c,"SELECT * FROM games WHERE id=? AND seed_key IS NOT NULL FOR SHARE",GameService::game,gameId);
                if (game == null) throw Validation.invalid("Не найдена исходная игра ID " + gameId + ". Импорт отменён.");
                games.put(gameId,game);
            }
            String warning = "Строк: " + selected.size() + "; уникальных игр: " + games.size() + ". Старый userId " + oldUserId + " → " + login
                + ". Даты и цены отсутствуют в файле: будут использованы дата импорта и текущие цены; статус IMPORTED, без списаний.";
            if (!apply) return "ПРЕДПРОСМОТР. " + warning + " Для применения добавьте --apply --ack-reconstructed.";
            for (LegacyRow row : selected) {
                Game game = games.get(row.gameId);
                UUID operation = UUID.nameUUIDFromBytes((fingerprint + ":" + row.line).getBytes(StandardCharsets.UTF_8));
                long orderId = Sql.one(c,"INSERT INTO orders(user_id,operation_id,total,status,source) VALUES (?,?,?,'IMPORTED','LEGACY') RETURNING id",rs -> rs.getLong(1),targetId,operation,game.price());
                Sql.update(c,"INSERT INTO order_items(order_id,game_id,title,price) VALUES (?,?,?,?)",orderId,game.id(),game.title(),game.price());
                Sql.update(c,"INSERT INTO library(user_id,game_id,order_id) VALUES (?,?,?) ON CONFLICT (user_id,game_id) DO NOTHING",targetId,game.id(),orderId);
            }
            Sql.update(c,"INSERT INTO legacy_imports(fingerprint,old_user_id,target_user_id,imported_rows) VALUES (?,?,?,?)",fingerprint,oldUserId,targetId,selected.size());
            return "Импорт выполнен. " + warning;
        });
    }
    private record LegacyRow(int line,long gameId) {}
    private static String sha256(byte[] bytes,long oldUserId) {
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            hash.update(bytes); hash.update((":" + oldUserId).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash.digest());
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable",e); }
    }
}
