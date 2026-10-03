package gamestore.shared;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class Models {
    private Models() {}
    public static final String CURRENCY = "KZT";
    public record User(long id, String login, String role, BigDecimal balance, String displayName, String email, boolean emailVerified) {
        public User(long id,String login,String role,BigDecimal balance) { this(id,login,role,balance,login,null,false); }
    }
    public record Game(long id, String title, String genre, BigDecimal price, String image, boolean active) {}
    public record Cart(List<Game> games, BigDecimal total, String currency) {}
    public record Payment(long orderId, UUID operationId, String status, BigDecimal amount, BigDecimal balance) {}
    public record OrderSummary(long id, BigDecimal total, String currency, String status, String date, String source) {}
    public record OrderItem(long gameId, String title, BigDecimal price) {}
    public record OrderDetail(OrderSummary order, List<OrderItem> items, String operationId, String paymentStatus) {}
    public record LibraryGame(long id, String title, String genre, String image, String acquiredAt) {}
}
