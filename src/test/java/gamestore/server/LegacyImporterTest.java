package gamestore.server;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LegacyImporterTest {
    @TempDir Path temporary;
    @Test void previewImportReplayAndExplicitOwnershipMapping() throws Exception {
        try (DatabaseFixture fixture = new DatabaseFixture()) {
            Store store = new Store(fixture.db);
            var user = store.users.register("legacy_player","T!" + UUID.randomUUID(),"legacy@example.com");
            var other = store.users.register("other_player","T!" + UUID.randomUUID(),"other@example.com");
            Path file = temporary.resolve("orders.txt");
            String contents = "User 3 bought game 2\nUser 3 bought game 2\nUser 3 bought game 3\nUser 4 bought game 20\n";
            Files.writeString(file,contents,StandardCharsets.UTF_8);
            LegacyImporter importer = new LegacyImporter(fixture.db);
            assertTrue(importer.run(file,3,user.login(),false).startsWith("ПРЕДПРОСМОТР"));
            assertTrue(store.orders.history(user.id()).isEmpty());
            assertTrue(importer.run(file,3,user.login(),true).startsWith("Импорт выполнен"));
            assertEquals(3,store.orders.history(user.id()).size()); assertEquals(2,store.orders.library(user.id()).size());
            assertTrue(store.orders.history(user.id()).stream().allMatch(o -> o.status().equals("IMPORTED") && o.source().equals("LEGACY")));
            assertEquals(0,store.users.profile(user.id()).balance().signum());
            assertTrue(importer.run(file,3,user.login(),true).contains("уже импортирован"));
            assertEquals(3,store.orders.history(user.id()).size());
            assertEquals("IMPORT_CONFLICT",assertThrows(StoreException.class,() -> importer.run(file,3,other.login(),true)).code());
            assertEquals(contents,Files.readString(file));
        }
    }
    @Test void invalidOrUnknownGamesLeaveDatabaseAndFileUntouched() throws Exception {
        try (DatabaseFixture fixture = new DatabaseFixture()) {
            Store store = new Store(fixture.db); var user = store.users.register("legacy_bad","T!" + UUID.randomUUID(),"bad@example.com");
            Path file = temporary.resolve("bad.txt");
            Files.writeString(file,"User 3 bought game 2\nUser 3 bought game 99999\n");
            assertThrows(StoreException.class,() -> new LegacyImporter(fixture.db).run(file,3,user.login(),true));
            assertTrue(store.orders.history(user.id()).isEmpty()); assertTrue(store.orders.library(user.id()).isEmpty());
            Files.writeString(file,"User 3 bought game 2\nmalformed\n");
            assertThrows(StoreException.class,() -> new LegacyImporter(fixture.db).run(file,3,user.login(),true));
            assertTrue(store.orders.history(user.id()).isEmpty());
        }
    }
}
