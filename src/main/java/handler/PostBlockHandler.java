package handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import model.Block;
import model.request.CreateBlockRequest;
import service.BlockBroadcastService;
import store.BlockStore;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RequiredArgsConstructor
public class PostBlockHandler implements HttpHandler {
    private final BlockStore blockStore;
    private final BlockBroadcastService blockBroadcastService;
    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpRequestHelper.rejectNonPost(exchange)) {
            return;
        }

        CreateBlockRequest request = objectMapper.readValue(exchange.getRequestBody(), CreateBlockRequest.class);
        if (request.data() == null || request.data().isBlank()) {
            HttpResponseWriter.writeText(exchange, 400, "Block data is required");
            return;
        }

        Block block = Block.fromData(request.data());
        boolean blockAdded = blockStore.addBlock(block);

        if (!blockAdded) {
            HttpResponseWriter.writeText(exchange, 409, "Block already exists");
            return;
        }

        log.info("Accepted block {}", block.getHash());
        Map<String, Object> response = Map.of(
                "accepted", true,
                "hash", block.getHash()
        );
        HttpResponseWriter.writeJson(exchange, 200, objectMapper.writeValueAsBytes(response));

        CompletableFuture.runAsync(() -> blockBroadcastService.broadcast(block.getData()));
    }
}
