package handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import lombok.RequiredArgsConstructor;
import store.PeerStore;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@RequiredArgsConstructor
public class AddrHandler implements HttpHandler {
    private final PeerStore peerStore;
    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpRequestHelper.rejectNonGet(exchange)) {
            return;
        }

        byte[] response = objectMapper.writeValueAsBytes(peerStore.getAllPeers());
        HttpResponseWriter.writeJson(exchange, 200, response);
    }
}
