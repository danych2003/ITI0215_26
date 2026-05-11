package handler;

import org.junit.jupiter.api.Test;
import service.BlockValidationService;
import service.BlockBroadcastService;
import service.LedgerStateService;
import service.PeerHttpClient;
import store.BlockStore;
import store.CanonicalChainStore;
import store.PeerStore;
import store.TransactionStore;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import util.MerkleUtils;

import java.io.IOException;
import java.math.BigDecimal;
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

    @Test
    void acceptsStructuredBlockPayload() throws IOException {
        PostBlockHandler handler = createHandler(new BlockStore());
        TestHttpExchange exchange = new TestHttpExchange(
                "POST",
                "/block",
                validStructuredGenesisRequest("alice", "2026-05-09T16:00:00Z", "2026-05-09T16:01:00Z")
        );

        handler.handle(exchange);

        assertEquals(200, exchange.getResponseCode());
        JsonNode response = objectMapper.readTree(exchange.getResponseBodyAsString());
        assertTrue(response.get("accepted").asBoolean());
        assertEquals(64, response.get("hash").asText().length());
    }

    @Test
    void rejectsStructuredBlockWithMissingParent() throws IOException {
        PostBlockHandler handler = createHandler(new BlockStore());
        TestHttpExchange exchange = new TestHttpExchange(
                "POST",
                "/block",
                "{\"height\":2,\"previousHash\":\"missing-parent\",\"timestamp\":\"2026-05-09T16:01:00Z\",\"nonce\":\"nonce-1\",\"creator\":\"node-a\",\"merkleRoot\":\"merkle-1\",\"transactions\":[]}"
        );

        handler.handle(exchange);

        assertEquals(400, exchange.getResponseCode());
        assertEquals("Parent block is missing", exchange.getResponseBodyAsString());
    }

    private PostBlockHandler createHandler(BlockStore blockStore) {
        TransactionStore transactionStore = new TransactionStore();
        LedgerStateService ledgerStateService =
                new LedgerStateService(blockStore, transactionStore, new CanonicalChainStore(), objectMapper);
        PeerStore peerStore = new PeerStore(List.of());
        PeerHttpClient peerHttpClient = new PeerHttpClient(objectMapper);
        BlockBroadcastService blockBroadcastService =
                new BlockBroadcastService(peerStore, peerHttpClient, "localhost:8081");
        return new PostBlockHandler(ledgerStateService, blockBroadcastService, objectMapper, 0, BigDecimal.ONE);
    }

    private String validStructuredGenesisRequest(String recipient, String rewardTimestamp, String blockTimestamp) {
        String transactionJson = "{\"signature\":null,\"transaction\":{\"from\":\"0\",\"to\":\"" + recipient
                + "\",\"amount\":1,\"timestamp\":\"" + rewardTimestamp + "\"}}";
        String merkleRoot = MerkleUtils.merkleRoot(List.of(objectHash(transactionJson)));
        return "{\"height\":1,\"previousHash\":null,\"timestamp\":\"" + blockTimestamp
                + "\",\"nonce\":\"nonce-1\",\"creator\":\"" + recipient
                + "\",\"merkleRoot\":\"" + merkleRoot + "\",\"transactions\":[" + transactionJson + "]}";
    }

    private String objectHash(String transactionJson) {
        model.SignedTransaction transaction = objectMapper.readValue(transactionJson, model.SignedTransaction.class);
        return transaction.hash() == null || transaction.hash().isBlank()
                ? model.SignedTransaction.from(transaction.signature(), transaction.transaction()).hash()
                : transaction.hash();
    }
}
