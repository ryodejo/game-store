package gamestore.server;

import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ServerMain {
    private ServerMain() {}
    public static void main(String[] args) {
        Logger.getLogger("org.flywaydb").setLevel(Level.WARNING);
        try {
            Database db = Database.fromEnvironment();
            db.migrate();
            if (args.length > 0) {
                switch (args[0]) {
                    case "migrate" -> { if (args.length != 1) throw Validation.invalid("migrate: без аргументов"); System.out.println("Миграции применены."); }
                    case "grant-admin" -> {
                        if (args.length != 2) throw Validation.invalid("grant-admin <логин>");
                        new UserService(db).grantAdmin(args[1]); System.out.println("Роль ADMIN назначена существующему аккаунту.");
                    }
                    case "import-orders" -> {
                        if (args.length != 4 && args.length != 6) throw Validation.invalid("import-orders <файл> <старый userId> <логин> [--apply --ack-reconstructed]");
                        boolean apply = args.length == 6 && args[4].equals("--apply") && args[5].equals("--ack-reconstructed");
                        if (args.length == 6 && !apply) throw Validation.invalid("Для импорта нужны --apply --ack-reconstructed.");
                        System.out.println(new LegacyImporter(db).run(Path.of(args[1]),Long.parseLong(args[2]),args[3],apply));
                    }
                    default -> throw Validation.invalid("Команды: migrate, grant-admin, import-orders. Без аргументов — запуск сервера.");
                }
                return;
            }
            String host = System.getenv().getOrDefault("GAMESTORE_HOST","127.0.0.1");
            int port = Integer.parseInt(System.getenv().getOrDefault("GAMESTORE_PORT","1234"));
            TcpServer server = new TcpServer(new Store(db),host,port);
            Runtime.getRuntime().addShutdownHook(new Thread(server::close,"gamestore-shutdown"));
            server.start();
            System.out.println("Game Store: " + host + ":" + server.port() + " | учебная оплата, KZT");
        } catch (StoreException | IllegalStateException e) { System.err.println(e.getMessage()); System.exit(1); }
        catch (Exception e) {
            System.err.println("Сервер не запущен: проверьте переменные подключения, PostgreSQL, миграции и свободный TCP-порт. Тип ошибки: " + e.getClass().getSimpleName());
            System.exit(1);
        }
    }
}
