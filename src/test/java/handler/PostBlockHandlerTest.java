package handler;

import org.junit.jupiter.api.Test;
import service.BlockBroadcastService;
import service.PeerHttpClient;
import store.BlockStore;
import store.PeerStore;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostBlockHandlerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void returns405ForNonPostRequest() throws IOException {
        PostBlockHandler handler = createHandler(new BlockStore());
        TestHttpExchange exchange = new TestHttpExchange("GET", "/block", "");

        handler.handle(exchange);

        assertEquals(405, exchange.getResponseCode());
        assertEquals("Method Not Allowed", exchange.getResponseBodyAsString());
    }

    @Test
    void returns400WhenBlockDataIsBlank() throws IOException {
        PostBlockHandler handler = createHandler(new BlockStore());
        TestHttpExchange exchange = new TestHttpExchange("POST", "/block", "{\"data\":\"\"}");

        handler.handle(exchange);

        assertEquals(400, exchange.getResponseCode());
        assertEquals("Block data is required", exchange.getResponseBodyAsString());
    }

    @Test
    void returns409WhenBlockAlreadyExists() throws IOException {
        BlockStore blockStore = new BlockStore();
        PostBlockHandler handler = createHandler(blockStore);
        handler.handle(new TestHttpExchange("POST", "/block", "{\"data\":\"block-1\"}"));

        TestHttpExchange duplicateExchange = new TestHttpExchange("POST", "/block", "{\"data\":\"block-1\"}");
        handler.handle(duplicateExchange);

        assertEquals(409, duplicateExchange.getResponseCode());
        assertEquals("Block already exists", duplicateExchange.getResponseBodyAsString());
    }

    @Test
    void returnsAcceptedResponseForNewBlock() throws IOException {
        PostBlockHandler handler = createHandler(new BlockStore());
        TestHttpExchange exchange = new TestHttpExchange("POST", "/block", "{\"data\":\"block-1\"}");

        handler.handle(exchange);

        assertEquals(200, exchange.getResponseCode());
        JsonNode response = objectMapper.readTree(exchange.getResponseBodyAsString());
        assertTrue(response.get("accepted").asBoolean());
        assertEquals(64, response.get("hash").asText().length());
    }

    private PostBlockHandler createHandler(BlockStore blockStore) {
        PeerStore peerStore = new PeerStore(List.of());
        PeerHttpClient peerHttpClient = new PeerHttpClient(objectMapper);
        BlockBroadcastService blockBroadcastService =
                new BlockBroadcastService(peerStore, peerHttpClient, "localhost:8081");
        return new PostBlockHandler(blockStore, blockBroadcastService, objectMapper);
    }
}
