package gamestore.shared;

import com.google.gson.*;
import java.io.*;

/** UTF-8 JSON lines: exactly one response for each bounded request. */
public final class Protocol {
    private Protocol() {}
    public static final int VERSION = 1;
    public static final int MAX_REQUEST = 16_384;
    public static final int MAX_AVATAR_REQUEST = 2_800_000; // 2 MiB binary, base64 and a small JSON envelope
    public static final int MAX_RESPONSE = 1_048_576;
    public static final Gson JSON = new GsonBuilder().setStrictness(Strictness.STRICT).create();
    public static JsonObject object(String text) {
        int depth = 0;
        boolean quoted = false, escaped = false;
        for (char c : text.toCharArray()) {
            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
            } else if (c == '"') quoted = true;
            else if (c == '{' || c == '[') { if (++depth > 16) throw new JsonParseException("Nesting limit"); }
            else if (c == '}' || c == ']') depth--;
        }
        JsonElement element = JSON.fromJson(text, JsonElement.class);
        if (element == null || !element.isJsonObject()) throw new JsonParseException("Object required");
        return element.getAsJsonObject();
    }
    public record Request(int version, String command, JsonObject args) {}
    public record Response(int version, boolean ok, String code, String message, JsonElement data) {
        public static Response success(Object data) {
            return new Response(VERSION, true, "OK", "Готово", JSON.toJsonTree(data));
        }
        public static Response error(String code, String message) {
            return new Response(VERSION, false, code, message, JsonNull.INSTANCE);
        }
    }
    public static String readLine(Reader in, int maximum) throws IOException {
        StringBuilder line = new StringBuilder();
        for (int c; (c = in.read()) != -1;) {
            if (c == '\n') return line.toString();
            if (line.length() >= maximum) throw new FrameTooLargeException();
            line.append((char)c);
        }
        if (line.length() != 0) throw new EOFException("Incomplete frame");
        return null;
    }
    public static final class FrameTooLargeException extends IOException {}
}
