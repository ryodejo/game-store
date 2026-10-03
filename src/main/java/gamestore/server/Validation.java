package gamestore.server;

import java.math.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

public final class Validation {
    private Validation() {}
    public static final BigDecimal MAX_MONEY = new BigDecimal("999999999999.99");
    public static String login(String value) {
        if (value == null) throw invalid("Введите логин.");
        String login = value.trim().toLowerCase(Locale.ROOT);
        if (!login.matches("[a-z0-9_]{3,32}")) throw invalid("Логин: 3–32 латинские буквы, цифры или знак _. Регистр не учитывается.");
        return login;
    }
    public static String password(String value) {
        if (value == null || value.isBlank() || value.codePointCount(0,value.length()) < 8 || value.getBytes(StandardCharsets.UTF_8).length > 72)
            throw invalid("Пароль: минимум 8 символов, максимум 72 байта UTF-8. Пробелы не обрезаются.");
        return value;
    }
    public static String displayName(String value) {
        if (value == null) throw new StoreException("NICK_INVALID","Введите ник: от 3 до 30 символов.");
        String name = value.strip(); int length = name.codePointCount(0,name.length());
        if (length < 3 || length > 30 || name.codePoints().anyMatch(c -> Character.isISOControl(c) || Character.getType(c) == Character.FORMAT))
            throw new StoreException("NICK_INVALID","Ник: 3–30 символов без управляющих символов.");
        return name;
    }
    public static String email(String value,boolean optional) {
        if (optional && (value == null || value.isBlank())) return null;
        String email = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        int at = email.indexOf('@');
        if (email.length() > 254 || at < 1 || at > 64 || !email.matches("[a-z0-9.!#$%&'*+/=?^_`{|}~-]+@[a-z0-9-]+(\\.[a-z0-9-]+)+")
                || email.startsWith(".") || email.substring(0,Math.max(0,at)).endsWith(".") || email.contains(".."))
            throw new StoreException("EMAIL_INVALID","Введите email вида name@example.com (до 254 символов).");
        for (String label : email.substring(at + 1).split("\\."))
            if (label.length() > 63 || label.startsWith("-") || label.endsWith("-")) throw new StoreException("EMAIL_INVALID","Некорректный домен email.");
        return email;
    }
    public static String text(String value, int maximum, String label) {
        if (value == null || value.isBlank() || value.length() > maximum || value.chars().anyMatch(Character::isISOControl))
            throw invalid(label + ": обязательное поле, максимум " + maximum + " символов.");
        return value.trim();
    }
    public static BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.compareTo(MAX_MONEY) > 0) throw invalid("Некорректная сумма в KZT.");
        try { return value.setScale(2, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException e) { throw invalid("Сумма должна иметь не более двух знаков после запятой."); }
    }
    public static BigDecimal money(String value) {
        if (value == null || !value.matches("[0-9]{1,12}(\\.[0-9]{1,2})?")) throw invalid("Введите сумму: например 1000.50.");
        return money(new BigDecimal(value));
    }
    public static UUID operation(String value) {
        try {
            UUID id = UUID.fromString(value);
            if (!id.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
            return id;
        } catch (RuntimeException e) { throw invalid("Некорректный идентификатор операции UUID."); }
    }
    public static StoreException invalid(String message) { return new StoreException("VALIDATION", message); }
}
