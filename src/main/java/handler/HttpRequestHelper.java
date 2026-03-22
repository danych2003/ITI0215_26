package handler;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;

public class HttpRequestHelper {
    public static boolean rejectNonGet(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            HttpResponseWriter.writeText(exchange, 405, "Method Not Allowed");
            return true;
        }

        return false;
    }

    public static String extractPathValue(HttpExchange exchange, String pathPrefix, String missingValueMessage)
            throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (!path.startsWith(pathPrefix) || path.length() <= pathPrefix.length()) {
            HttpResponseWriter.writeText(exchange, 400, missingValueMessage);
            return null;
        }

        return path.substring(pathPrefix.length());
    }

    private HttpRequestHelper() {}
}
