package gamestore.server;

import gamestore.client.Client;
import gamestore.client.ClientException;
import gamestore.shared.Models.*;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class StoreIntegrationTest {
    private static DatabaseFixture fixture;
    private static Store store;
    private static int nextUser;
    private record Account(User user,String password) {}
    @BeforeAll static void setup() throws Exception { fixture = new DatabaseFixture(); store = new Store(fixture.db); }
    @AfterAll static void cleanup() throws Exception { if (fixture != null) fixture.close(); }
    private Account account() throws SQLException {
        String password = "T!" + UUID.randomUUID();
        String login = "test_" + (++nextUser);
        return new Account(store.users.register(login,password,login + "@example.com"),password);
    }
    private static BigDecimal money(String value) { return new BigDecimal(value); }
    private static void error(String code,org.junit.jupiter.api.function.Executable work) {
        assertEquals(code,assertThrows(StoreException.class,work).code());
    }
    private static void equalMoney(String expected,BigDecimal actual) { assertEquals(0,money(expected).compareTo(actual)); }
    @Test void registrationLoginAndPasswordStorage() throws Exception {
        Account account = account(); User user = account.user;
        assertEquals(user.id(),store.users.login(user.login().toUpperCase(Locale.ROOT),account.password).id());
        error("BAD_CREDENTIALS",() -> store.users.login(user.login(),"incorrect-password"));
        error("LOGIN_TAKEN",() -> store.users.register(user.login().toUpperCase(Locale.ROOT),account.password,"duplicate@example.com"));
        error("VALIDATION",() -> store.users.register("",account.password));
        error("VALIDATION",() -> store.users.register("valid_login","short","valid@example.com"));
        try (Connection c = fixture.db.open()) {
            String hash = Sql.one(c,"SELECT password_hash FROM users WHERE id=?",rs -> rs.getString(1),user.id());
            assertNotEquals(account.password,hash); assertTrue(hash.startsWith("$2a$12$"));
        }
    }
    @Test void searchFiltersSortAndCart() throws Exception {
        User user = account().user;
        var found = store.games.catalog("minecraft","",null,null,"TITLE");
        assertEquals(1,found.size()); assertEquals(2,found.get(0).id());
        assertTrue(store.games.catalog("' OR 1=1 --","",null,null,"TITLE").isEmpty());
        assertTrue(store.games.catalog("%","",null,null,"TITLE").isEmpty());
        var filtered = store.games.catalog("","RPG",money("10000"),money("18000"),"PRICE_DESC");
        assertEquals(List.of(5L,4L,17L),filtered.stream().map(Game::id).toList());
        error("VALIDATION",() -> store.games.catalog("","",money("200"),money("100"),"TITLE"));
        store.cart.add(user.id(),2); store.cart.add(user.id(),2); store.cart.add(user.id(),3);
        assertEquals(2,store.cart.view(user.id()).games().size()); equalMoney("3000",store.cart.view(user.id()).total());
        store.cart.remove(user.id(),2); assertEquals(1,store.cart.view(user.id()).games().size());
        store.cart.remove(user.id(),3); assertTrue(store.cart.view(user.id()).games().isEmpty());
    }
    @Test void addingExistingGameToFullCartIsIdempotentButNewGameIsRejected() throws Exception {
        User user = account().user;
        List<Long> ids;
        try (Connection c = fixture.db.open()) {
            ids = Sql.list(c,"INSERT INTO games(title,genre,price) SELECT 'Limit game ' || i,'Test',0 FROM generate_series(1,101) i RETURNING id",rs -> rs.getLong(1));
            for (long id : ids.subList(0,100)) Sql.update(c,"INSERT INTO cart_items(user_id,game_id) VALUES (?,?)",user.id(),id);
        }
        assertDoesNotThrow(() -> store.cart.add(user.id(),ids.get(0)));
        error("VALIDATION",() -> store.cart.add(user.id(),ids.get(100)));
        assertEquals(100,store.cart.view(user.id()).games().size());
    }
    @Test void successfulCheckoutKeepsSnapshotsAndRejectsRepurchase() throws Exception {
        User user = account().user;
        store.users.topUp(user.id(),UUID.randomUUID(),money("5000"));
        store.cart.add(user.id(),2); store.cart.add(user.id(),3);
        UUID operation = UUID.randomUUID(); Payment payment = store.orders.checkout(user.id(),operation);
        assertEquals("SUCCEEDED",payment.status()); equalMoney("2000",payment.balance()); equalMoney("3000",payment.amount());
        assertTrue(store.cart.view(user.id()).games().isEmpty()); assertEquals(2,store.orders.library(user.id()).size());
        assertEquals(payment,store.orders.checkout(user.id(),operation));
        assertEquals(1,store.orders.history(user.id()).size()); equalMoney("2000",store.users.profile(user.id()).balance());
        error("ALREADY_OWNED",() -> store.cart.add(user.id(),2));
        error("ALREADY_OWNED",() -> store.orders.buy(user.id(),UUID.randomUUID(),2));
        User admin = account().user; store.users.grantAdmin(admin.login());
        Game separate = store.games.save(admin.id(),0,"Snapshot game","Test",money("10.50"),"",true);
        Payment one = store.orders.buy(user.id(),UUID.randomUUID(),separate.id());
        store.games.save(admin.id(),separate.id(),"Changed title","New genre",money("999"),"",false);
        var detail = store.orders.detail(user.id(),one.orderId());
        assertEquals("Snapshot game",detail.items().get(0).title()); equalMoney("10.50",detail.items().get(0).price());
        assertEquals("PAID",detail.order().status()); assertEquals("SUCCEEDED",detail.paymentStatus());
        assertTrue(store.orders.library(user.id()).stream().anyMatch(g -> g.id() == separate.id()));
        error("NOT_FOR_SALE",() -> store.cart.add(account().user.id(),separate.id()));
    }
    @Test void insufficientBalanceCreatesDeclinedAttemptAndKeepsCart() throws Exception {
        User user = account().user; store.cart.add(user.id(),2);
        UUID operation = UUID.randomUUID(); Payment declined = store.orders.checkout(user.id(),operation);
        assertEquals("DECLINED",declined.status()); assertEquals("DECLINED",store.orders.detail(user.id(),declined.orderId()).order().status());
        equalMoney("0",store.users.profile(user.id()).balance()); assertEquals(1,store.cart.view(user.id()).games().size());
        assertTrue(store.orders.library(user.id()).isEmpty());
        store.users.topUp(user.id(),UUID.randomUUID(),money("1000"));
        assertEquals(declined,store.orders.checkout(user.id(),operation));
        equalMoney("1000",store.users.profile(user.id()).balance());
        assertEquals("SUCCEEDED",store.orders.checkout(user.id(),UUID.randomUUID()).status());
    }
    @Test void freeGameAndTopUpIdempotency() throws Exception {
        User user = account().user; UUID operation = UUID.randomUUID();
        equalMoney("100",store.users.topUp(user.id(),operation,money("100")));
        equalMoney("100",store.users.topUp(user.id(),operation,money("100")));
        error("OPERATION_CONFLICT",() -> store.users.topUp(user.id(),operation,money("200")));
        equalMoney("100",store.users.profile(user.id()).balance());
        error("VALIDATION",() -> store.users.topUp(user.id(),UUID.randomUUID(),money("0")));
        assertEquals("SUCCEEDED",store.orders.buy(user.id(),UUID.randomUUID(),1).status());
        equalMoney("100",store.users.profile(user.id()).balance());
        error("ALREADY_OWNED",() -> store.orders.buy(user.id(),UUID.randomUUID(),1));
    }
    @Test void operationCannotBeReusedForAnotherGameOrUser() throws Exception {
        User user = account().user, other = account().user; UUID operation = UUID.randomUUID();
        store.orders.buy(user.id(),operation,1);
        error("OPERATION_CONFLICT",() -> store.orders.buy(user.id(),operation,6));
        error("OPERATION_CONFLICT",() -> store.orders.buy(other.id(),operation,1));
        assertTrue(store.orders.library(other.id()).isEmpty());
    }
    @Test void concurrentSameOperationDebitsOnce() throws Exception {
        User user = account().user;
        store.users.topUp(user.id(),UUID.randomUUID(),money("3000")); store.cart.add(user.id(),2);
        UUID operation = UUID.randomUUID(); CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Payment> buy = () -> { barrier.await(5,TimeUnit.SECONDS); return store.orders.checkout(user.id(),operation); };
            Future<Payment> first = pool.submit(buy), second = pool.submit(buy);
            assertEquals(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS));
            equalMoney("2000",store.users.profile(user.id()).balance()); assertEquals(1,store.orders.history(user.id()).size());
        } finally { pool.shutdownNow(); }
    }
    @Test void concurrentDifferentOperationsCannotOverspend() throws Exception {
        User user = account().user; store.users.topUp(user.id(),UUID.randomUUID(),money("2000"));
        CyclicBarrier barrier = new CyclicBarrier(2); ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Payment> first = pool.submit(() -> { barrier.await(5,TimeUnit.SECONDS); return store.orders.buy(user.id(),UUID.randomUUID(),3); });
            Future<Payment> second = pool.submit(() -> { barrier.await(5,TimeUnit.SECONDS); return store.orders.buy(user.id(),UUID.randomUUID(),20); });
            var outcomes = List.of(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS));
            assertEquals(1,outcomes.stream().filter(p -> p.status().equals("SUCCEEDED")).count());
            assertEquals(1,outcomes.stream().filter(p -> p.status().equals("DECLINED")).count());
            equalMoney("0",store.users.profile(user.id()).balance()); assertEquals(1,store.orders.library(user.id()).size());
        } finally { pool.shutdownNow(); }
    }
    @Test void concurrentCheckoutOfSameCartHasOneWinner() throws Exception {
        User user = account().user; store.users.topUp(user.id(),UUID.randomUUID(),money("3000")); store.cart.add(user.id(),2);
        CyclicBarrier barrier = new CyclicBarrier(2); ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<String> buy = () -> { barrier.await(5,TimeUnit.SECONDS); try { return store.orders.checkout(user.id(),UUID.randomUUID()).status(); } catch (StoreException e) { return e.code(); } };
            Future<String> first = pool.submit(buy), second = pool.submit(buy);
            assertEquals(Set.of("SUCCEEDED","CART_EMPTY"),Set.of(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS)));
            equalMoney("2000",store.users.profile(user.id()).balance()); assertEquals(1,store.orders.history(user.id()).size());
        } finally { pool.shutdownNow(); }
    }
    @Test void errorAfterDebitRollsBackAllChangesAndAllowsRetry() throws Exception {
        User user = account().user; store.users.topUp(user.id(),UUID.randomUUID(),money("3000")); store.cart.add(user.id(),2);
        UUID operation = UUID.randomUUID();
        try (Connection c = fixture.db.open()) {
            Sql.update(c,"CREATE FUNCTION test_reject_library() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'injected failure'; END $$");
            Sql.update(c,"CREATE TRIGGER test_reject BEFORE INSERT ON library FOR EACH ROW EXECUTE FUNCTION test_reject_library()");
        }
        try {
            assertThrows(SQLException.class,() -> store.orders.checkout(user.id(),operation));
            equalMoney("3000",store.users.profile(user.id()).balance());
            assertTrue(store.orders.history(user.id()).isEmpty()); assertTrue(store.orders.library(user.id()).isEmpty());
            assertEquals(1,store.cart.view(user.id()).games().size());
            try (Connection c = fixture.db.open()) {
                assertEquals(0L,Sql.<Long>one(c,"SELECT count(*) FROM payment_attempts WHERE operation_id=?",rs -> rs.getLong(1),operation).longValue());
            }
        } finally {
            try (Connection c = fixture.db.open()) { Sql.update(c,"DROP TRIGGER test_reject ON library"); Sql.update(c,"DROP FUNCTION test_reject_library()"); }
        }
        assertEquals("SUCCEEDED",store.orders.checkout(user.id(),operation).status());
    }
    @Test void tcpSessionIsolationAndAdminAuthorization() throws Exception {
        Account first = account(), second = account();
        Payment purchase = store.orders.buy(first.user.id(),UUID.randomUUID(),1);
        try (TcpServer server = new TcpServer(store,"127.0.0.1",0)) {
            server.start();
            try (Client client = new Client("127.0.0.1",server.port())) {
                assertEquals("UNAUTHORIZED",assertThrows(ClientException.class,client::profile).code());
                client.login(second.user.login(),second.password);
                assertEquals("NOT_FOUND",assertThrows(ClientException.class,() -> client.order(purchase.orderId())).code());
                assertEquals("VALIDATION",assertThrows(ClientException.class,() -> client.request("ORDERS",Map.of("userId",first.user.id()))).code());
                assertTrue(client.orders().isEmpty()); assertTrue(client.library().isEmpty());
                assertEquals("FORBIDDEN",assertThrows(ClientException.class,client::adminGames).code());
                assertEquals("FORBIDDEN",assertThrows(ClientException.class,() -> client.saveGame(0,"Forbidden game","Test","10","",true)).code());
                store.users.grantAdmin(second.user.login());
                assertFalse(client.adminGames().isEmpty());
                assertEquals("NOT_FOUND",assertThrows(ClientException.class,() -> client.order(purchase.orderId())).code());
                assertEquals("BAD_CREDENTIALS",assertThrows(ClientException.class,() -> client.login(second.user.login(),"invalid-password")).code());
                assertEquals("UNAUTHORIZED",assertThrows(ClientException.class,client::profile).code());
            }
        }
    }
    @Test void persistsAcrossServerRestartAndTcpHasOneResponsePerRequest() throws Exception {
        Account account = account(); UUID operation = UUID.randomUUID(); long orderId;
        try (TcpServer server = new TcpServer(store,"127.0.0.1",0)) {
            server.start();
            try (Client client = new Client("127.0.0.1",server.port())) {
                client.login(account.user.login(),account.password); client.topUp("5000",UUID.randomUUID());
                client.addToCart(2); client.addToCart(3); Payment result = client.checkout(operation); orderId = result.orderId();
                assertTrue(client.cart().games().isEmpty()); equalMoney("2000",client.profile().balance());
            }
        }
        fixture.db.migrate();
        try (TcpServer restarted = new TcpServer(new Store(fixture.db),"127.0.0.1",0)) {
            restarted.start();
            try (Client client = new Client("127.0.0.1",restarted.port())) {
                client.login(account.user.login(),account.password);
                assertEquals(orderId,client.checkout(operation).orderId());
                assertEquals(2,client.library().size()); assertEquals(1,client.orders().size()); equalMoney("2000",client.profile().balance());
                client.addToCart(12);
            }
        }
        assertEquals(12,new Store(fixture.db).cart.view(account.user.id()).games().get(0).id());
    }
}
