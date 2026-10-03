package gamestore.server;

import com.google.gson.JsonParseException;
import gamestore.shared.Protocol;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProtocolTest {
    @Test void frameLimitsIncompleteFramesAndNesting() throws Exception {
        assertEquals("a",Protocol.readLine(new StringReader("a\n"),1));
        assertNull(Protocol.readLine(new StringReader(""),1));
        assertThrows(Protocol.FrameTooLargeException.class,() -> Protocol.readLine(new StringReader("ab\n"),1));
        assertThrows(EOFException.class,() -> Protocol.readLine(new StringReader("a"),10));
        assertThrows(JsonParseException.class,() -> Protocol.object("[".repeat(30) + "0" + "]".repeat(30)));
        assertThrows(JsonParseException.class,() -> Protocol.object("{\"x\":1,}"));
    }
    @Test void malformedTcpRequestsDoNotKillSessionAndOversizedFrameClosesIt() throws Exception {
        try (DatabaseFixture fixture = new DatabaseFixture(); TcpServer server = new TcpServer(new Store(fixture.db),"127.0.0.1",0)) {
            server.start();
            try (Socket socket = new Socket("127.0.0.1",server.port());
                 Reader in = new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8);
                 Writer out = new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.UTF_8)) {
                socket.setSoTimeout(4000);
                assertEquals("BAD_REQUEST",send(out,in,"not-json").code());
                assertEquals("VERSION",send(out,in,"{\"version\":2,\"command\":\"CATALOG\",\"args\":{}}").code());
                assertEquals("VALIDATION",send(out,in,"{\"version\":1,\"command\":\"REGISTER\",\"args\":{}}").code());
                assertEquals("BAD_REQUEST",send(out,in,"{\"version\":1,\"command\":\"CATALOG\",\"args\":[]}").code());
                assertEquals("UNAUTHORIZED",send(out,in,"{\"version\":1,\"command\":\"ADD_CART\",\"args\":{\"userId\":1,\"gameId\":2}}").code());
                var catalog = send(out,in,"{\"version\":1,\"command\":\"CATALOG\",\"args\":{}}");
                assertTrue(catalog.ok()); assertEquals(20,catalog.data().getAsJsonArray().size());
                assertEquals("FRAME_TOO_LARGE",send(out,in,"x".repeat(Protocol.MAX_REQUEST + 1)).code());
                assertNull(Protocol.readLine(in,Protocol.MAX_RESPONSE));
            }
        }
    }
    private static Protocol.Response send(Writer out,Reader in,String request) throws IOException {
        out.write(request); out.write('\n'); out.flush();
        return Protocol.JSON.fromJson(Protocol.readLine(in,Protocol.MAX_RESPONSE),Protocol.Response.class);
    }
}
