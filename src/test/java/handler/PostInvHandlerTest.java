package handler;

import org.junit.jupiter.api.Test;
import service.PeerHttpClient;
import service.TransactionBroadcastService;
import store.PeerStore;
import store.TransactionStore;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostInvHandlerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void returns405ForNonPostRequest() throws IOException {
        PostInvHandler handler = createHandler(new TransactionStore());
        TestHttpExchange exchange = new TestHttpExchange("GET", "/inv", "");

        handler.handle(exchange);

        assertEquals(405, exchange.getResponseCode());
        assertEquals("Method Not Allowed", exchange.getResponseBodyAsString());
    }

    @Test
    void returns400WhenTransactionDataIsBlank() throws IOException {
        PostInvHandler handler = createHandler(new TransactionStore());
        TestHttpExchange exchange = new TestHttpExchange("POST", "/inv", "{\"data\":\"\"}");

        handler.handle(exchange);

        assertEquals(400, exchange.getResponseCode());
        assertEquals("Transaction data is required", exchange.getResponseBodyAsString());
    }

    @Test
    void returns409WhenTransactionAlreadyExists() throws IOException {
        TransactionStore transactionStore = new TransactionStore();
        PostInvHandler handler = createHandler(transactionStore);
        handler.handle(new TestHttpExchange("POST", "/inv", "{\"data\":\"alice->bob:5\"}"));

        TestHttpExchange duplicateExchange = new TestHttpExchange("POST", "/inv", "{\"data\":\"alice->bob:5\"}");
        handler.handle(duplicateExchange);

        assertEquals(409, duplicateExchange.getResponseCode());
        assertEquals("Transaction already exists", duplicateExchange.getResponseBodyAsString());
    }

    @Test
    void returnsAcceptedResponseForNewTransaction() throws IOException {
        PostInvHandler handler = createHandler(new TransactionStore());
        TestHttpExchange exchange = new TestHttpExchange("POST", "/inv", "{\"data\":\"alice->bob:5\"}");

        handler.handle(exchange);

        assertEquals(200, exchange.getResponseCode());
        JsonNode response = objectMapper.readTree(exchange.getResponseBodyAsString());
        assertTrue(response.get("accepted").asBoolean());
        assertEquals(64, response.get("hash").asText().length());
    }

    private PostInvHandler createHandler(TransactionStore transactionStore) {
        PeerStore peerStore = new PeerStore(List.of());
        PeerHttpClient peerHttpClient = new PeerHttpClient(objectMapper);
        TransactionBroadcastService transactionBroadcastService =
                new TransactionBroadcastService(peerStore, peerHttpClient, "localhost:8081");
        return new PostInvHandler(transactionStore, transactionBroadcastService, objectMapper);
    }
}
