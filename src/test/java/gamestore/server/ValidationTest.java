package gamestore.server;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ValidationTest {
    @Test void moneyHasExactCentsAndBounds() {
        assertEquals(new BigDecimal("10.50"),Validation.money("10.50"));
        assertEquals(new BigDecimal("10.00"),Validation.money(new BigDecimal("10")));
        for (String invalid : new String[]{"-1","1.001","NaN","1e5","","9999999999999","1,50"}) assertThrows(StoreException.class,() -> Validation.money(invalid));
        assertThrows(StoreException.class,() -> Validation.money(new BigDecimal("0.001")));
    }
    @Test void loginAndPasswordRulesHandleUnicodeAndDoNotTrimSecrets() {
        assertEquals("player_1",Validation.login(" Player_1 "));
        for (String login : new String[]{"","ab","player space","' OR 1=1","x".repeat(33)}) assertThrows(StoreException.class,() -> Validation.login(login));
        assertEquals("  secret pass  ",Validation.password("  secret pass  "));
        assertEquals("я".repeat(36),Validation.password("я".repeat(36)));
        assertThrows(StoreException.class,() -> Validation.password("я".repeat(37)));
        assertThrows(StoreException.class,() -> Validation.password("        "));
        assertThrows(StoreException.class,() -> Validation.operation("1-1-1-1-1"));
    }
}
