package handler;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public class HttpResponseWriter {
    public static void writeText(HttpExchange exchange, int statusCode, String body) throws IOException {
        writeBytes(exchange, statusCode, "text/plain; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
    }

    public static void writeJson(HttpExchange exchange, int statusCode, byte[] body) throws IOException {
        writeBytes(exchange, statusCode, "application/json; charset=utf-8", body);
    }

    private static void writeBytes(HttpExchange exchange, int statusCode, String contentType, byte[] body)
            throws IOException {
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(statusCode, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private HttpResponseWriter() {}
}
