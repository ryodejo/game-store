package gamestore.server;

import gamestore.client.*;
import gamestore.shared.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProfileIntegrationTest {
    private static final String PASSWORD = "test-only-Password-27";
    @Test void newRegistrationRequiresEmailOverTcpAndCannotBeBypassedInService() throws Exception {
        try (DatabaseFixture fixture = new DatabaseFixture(); TcpServer server = new TcpServer(new Store(fixture.db),"127.0.0.1",0)) {
            server.start();
            try (Client client = new Client("127.0.0.1",server.port())) {
                error("EMAIL_INVALID",() -> client.register("required_email",PASSWORD));
                error("EMAIL_INVALID",() -> client.register("required_email",PASSWORD,""));
                error("EMAIL_INVALID",() -> client.register("required_email",PASSWORD,"   "));
                error("EMAIL_INVALID",() -> client.register("required_email",PASSWORD,"not-an-email"));
                UserService users = new UserService(fixture.db);
                assertEquals("EMAIL_INVALID",assertThrows(StoreException.class,() -> users.register("required_email",PASSWORD,null)).code());
                try (var c = fixture.db.open()) { assertEquals(0L,Sql.<Long>one(c,"SELECT count(*) FROM users",rs -> rs.getLong(1))); }
                var created = client.register("required_email",PASSWORD," Required@Example.COM ");
                assertEquals("required@example.com",created.email());
                assertEquals(created.id(),client.login("REQUIRED@EXAMPLE.COM",PASSWORD).id());
            }
        }
    }
    @Test void profileAndProcessedAvatarSurviveServerRestartWithoutChangingPurchases() throws Exception {
        try (DatabaseFixture fixture = new DatabaseFixture()) {
            Store store = new Store(fixture.db); var user = store.users.register("profile_owner",PASSWORD,"original@example.com");
            store.users.grantAdmin(user.login()); store.users.topUp(user.id(),UUID.randomUUID(),new BigDecimal("10000")); store.orders.buy(user.id(),UUID.randomUUID(),2);
            String stored;
            try (TcpServer server = new TcpServer(store,"127.0.0.1",0)) {
                server.start(); try (Client client = new Client("127.0.0.1",server.port())) {
                    client.login(user.login(),PASSWORD); assertEquals("original@example.com",client.profile().email());
                    assertEquals("Новый Ник",client.updateName("  Новый Ник  ").displayName());
                    assertEquals("owner@example.com",client.updateEmail(" Owner@Example.COM ",PASSWORD).email());
                    assertFalse(client.profile().emailVerified());
                    client.saveAvatar(image("png",600,300)); stored = client.avatar();
                    BufferedImage png = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(stored))); assertEquals(256,png.getWidth()); assertEquals(256,png.getHeight());
                    String dto = Protocol.JSON.toJson(client.profile()); assertFalse(dto.contains("password")); assertFalse(dto.contains("sessionVersion"));
                }
            }
            try (TcpServer restarted = new TcpServer(new Store(fixture.db),"127.0.0.1",0)) {
                restarted.start(); try (Client client = new Client("127.0.0.1",restarted.port())) {
                    var restored = client.login("OWNER@EXAMPLE.COM",PASSWORD);
                    assertEquals(user.id(),restored.id()); assertEquals("profile_owner",restored.login()); assertEquals("Новый Ник",restored.displayName()); assertEquals("ADMIN",restored.role());
                    assertEquals(new BigDecimal("9000.00"),restored.balance()); assertEquals(1,client.library().size()); assertEquals(1,client.orders().size()); assertEquals(stored,client.avatar());
                    client.removeAvatar(); assertEquals("",client.avatar());
                }
            }
            assertEquals("",new UserService(fixture.db).avatar(user.id()));
        }
    }
    @Test void invalidUpdatesAndForeignUserIdsAreRejectedAndLeaveProfileIntact() throws Exception {
        try (DatabaseFixture fixture = new DatabaseFixture(); TcpServer server = new TcpServer(new Store(fixture.db),"127.0.0.1",0)) {
            server.start(); try (Client first = new Client("127.0.0.1",server.port()); Client second = new Client("127.0.0.1",server.port())) {
                var owner = first.register("first_owner",PASSWORD,"first@example.com"); var other = second.register("second_owner",PASSWORD,"second@example.com");
                first.login(owner.login(),PASSWORD); second.login(other.login(),PASSWORD);
                error("CURRENT_PASSWORD",() -> first.updateEmail("new@example.com","wrong-password"));
                error("EMAIL_TAKEN",() -> first.updateEmail("SECOND@EXAMPLE.COM",PASSWORD));
                error("EMAIL_INVALID",() -> first.updateEmail("bad..address@example.com",PASSWORD));
                error("EMAIL_INVALID",() -> first.updateEmail("user@-bad.com",PASSWORD));
                error("NICK_INVALID",() -> first.updateName("xy")); error("NICK_INVALID",() -> first.updateName("bad\nname")); error("NICK_INVALID",() -> first.updateName("x".repeat(31)));
                error("VALIDATION",() -> first.request("UPDATE_NAME",Map.of("displayName","hacked","userId",other.id())));
                error("VALIDATION",() -> first.request("UPDATE_EMAIL",Map.of("email","hacked@example.com","currentPassword",PASSWORD,"userId",other.id())));
                error("VALIDATION",() -> first.request("CHANGE_PASSWORD",Map.of("currentPassword",PASSWORD,"newPassword","Replacement-27","repeatPassword","Replacement-27","userId",other.id())));
                error("VALIDATION",() -> first.request("SET_AVATAR",Map.of("image",Base64.getEncoder().encodeToString(image("png",64,64)),"userId",other.id())));
                assertEquals("second_owner",second.profile().displayName()); assertEquals("second@example.com",second.profile().email()); assertEquals("",second.avatar());
                assertEquals("first_owner",first.profile().displayName()); assertEquals("first@example.com",first.profile().email());
                error("EMAIL_TAKEN",() -> first.register("third_owner",PASSWORD," FIRST@example.com "));
                String longLogin = "x".repeat(32); var longAccount = first.register(longLogin,PASSWORD,"long@example.com"); assertEquals(longLogin,longAccount.login()); assertEquals(30,longAccount.displayName().length());
            }
        }
    }
    @Test void passwordChecksAndUtf8LimitAreEnforcedAndAllSessionsAreRevoked() throws Exception {
        try (DatabaseFixture fixture = new DatabaseFixture(); TcpServer server = new TcpServer(new Store(fixture.db),"127.0.0.1",0)) {
            server.start(); try (Client first = new Client("127.0.0.1",server.port()); Client second = new Client("127.0.0.1",server.port())) {
                first.register("password_owner",PASSWORD,"password@example.com"); first.login("password_owner",PASSWORD); second.login("password_owner",PASSWORD);
                error("CURRENT_PASSWORD",() -> first.changePassword("wrong-password","NewPassword-27","NewPassword-27"));
                error("PASSWORD_MISMATCH",() -> first.changePassword(PASSWORD,"NewPassword-27","Different-27"));
                error("VALIDATION",() -> first.changePassword(PASSWORD,"short","short"));
                error("VALIDATION",() -> first.changePassword(PASSWORD,"😀".repeat(4),"😀".repeat(4)));
                error("VALIDATION",() -> first.changePassword(PASSWORD,"я".repeat(37),"я".repeat(37)));
                assertNotNull(second.profile()); first.changePassword(PASSWORD,"я".repeat(36),"я".repeat(36));
                error("UNAUTHORIZED",first::profile); error("UNAUTHORIZED",second::profile); error("UNAUTHORIZED",() -> second.updateName("Another Name"));
                error("BAD_CREDENTIALS",() -> first.login("password_owner",PASSWORD)); assertNotNull(first.login("password_owner","я".repeat(36)));
            }
        }
    }
    @Test void imageFormatByteAndPixelLimitsAreCheckedOnServer() throws Exception {
        try (DatabaseFixture fixture = new DatabaseFixture(); TcpServer server = new TcpServer(new Store(fixture.db),"127.0.0.1",0)) {
            server.start(); try (Client client = new Client("127.0.0.1",server.port())) {
                client.register("avatar_owner",PASSWORD,"avatar@example.com"); client.login("avatar_owner",PASSWORD);
                error("AVATAR_INVALID",() -> client.saveAvatar("not an image".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                error("AVATAR_INVALID",() -> client.saveAvatar(image("gif",64,64)));
                error("AVATAR_INVALID",() -> client.saveAvatar(image("png",16,32)));
                error("AVATAR_INVALID",() -> client.saveAvatar(image("png",4097,32)));
                error("AVATAR_INVALID",() -> client.saveAvatar(new byte[AvatarImages.MAX_BYTES + 1]));
                error("AVATAR_INVALID",() -> client.request("SET_AVATAR",Map.of("image","%%%")));
                byte[] padded = Arrays.copyOf(image("png",400,300),AvatarImages.MAX_BYTES); client.saveAvatar(padded); assertFalse(client.avatar().isEmpty());
                client.saveAvatar(image("jpeg",300,400)); assertFalse(client.avatar().isEmpty());
            }
        }
    }
    @Test void concurrentNormalizedEmailClaimsHaveOnlyOneWinner() throws Exception {
        try (DatabaseFixture fixture = new DatabaseFixture()) {
            UserService users = new UserService(fixture.db); var first = users.register("claim_one",PASSWORD,"one@example.com"); var second = users.register("claim_two",PASSWORD,"two@example.com");
            ExecutorService pool = Executors.newFixedThreadPool(2); CountDownLatch start = new CountDownLatch(1);
            try {
                var a = pool.submit(() -> claim(users,first.id()," Shared@Example.com ",start)); var b = pool.submit(() -> claim(users,second.id(),"shared@example.com",start)); start.countDown();
                assertEquals(Set.of("OK","EMAIL_TAKEN"),Set.of(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS)));
            } finally { pool.shutdownNow(); }
        }
    }
    private static String claim(UserService users,long id,String email,CountDownLatch start) throws Exception { start.await(); try { users.updateEmail(id,email,PASSWORD); return "OK"; } catch (StoreException e) { return e.code(); } }
    private static byte[] image(String format,int width,int height) throws IOException { BufferedImage image = new BufferedImage(width,height,BufferedImage.TYPE_INT_RGB); ByteArrayOutputStream out = new ByteArrayOutputStream(); ImageIO.write(image,format,out); return out.toByteArray(); }
    private static void error(String code,org.junit.jupiter.api.function.Executable action) { assertEquals(code,assertThrows(ClientException.class,action).code()); }
}
