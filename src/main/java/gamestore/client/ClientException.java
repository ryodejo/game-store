package gamestore.client;

public final class ClientException extends RuntimeException {
    private final String code;
    public ClientException(String code,String message) { super(message); this.code = code; }
    public String code() { return code; }
}
