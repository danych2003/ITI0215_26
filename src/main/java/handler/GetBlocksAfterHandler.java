package handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import lombok.RequiredArgsConstructor;
import store.BlockStore;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@RequiredArgsConstructor
public class GetBlocksAfterHandler implements HttpHandler {
    private static final String PATH_PREFIX = "/getblocks/";

    private final BlockStore blockStore;
    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpRequestHelper.rejectNonGet(exchange)) {
            return;
        }

        String hash = HttpRequestHelper.extractPathValue(exchange, PATH_PREFIX, "Block hash is required");
        if (hash == null) {
            return;
        }
        byte[] response = objectMapper.writeValueAsBytes(blockStore.getHashesAfter(hash));
        HttpResponseWriter.writeJson(exchange, 200, response);
    }
}
