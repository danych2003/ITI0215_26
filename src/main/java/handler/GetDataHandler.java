package handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import lombok.RequiredArgsConstructor;
import model.Block;
import store.BlockStore;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@RequiredArgsConstructor
public class GetDataHandler implements HttpHandler {
    private static final String PATH_PREFIX = "/getdata/";

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
        Block block = blockStore.getBlock(hash);
        if (block == null) {
            HttpResponseWriter.writeText(exchange, 404, "Block not found");
            return;
        }

        byte[] response = objectMapper.writeValueAsBytes(block);
        HttpResponseWriter.writeJson(exchange, 200, response);
    }
}
