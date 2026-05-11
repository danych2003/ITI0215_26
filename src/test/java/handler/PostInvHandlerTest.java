package handler;

import org.junit.jupiter.api.Test;
import service.TransactionValidationService;
import service.PeerHttpClient;
import service.LedgerStateService;
import service.TransactionBroadcastService;
import store.BlockStore;
import store.CanonicalChainStore;
import store.PeerStore;
import store.TransactionStore;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import util.CryptoUtils;
import util.MerkleUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.KeyPair;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PostInvHandlerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void returns405ForNonPostRequest() throws IOException {
        PostInvHandler handler = createHandler(new TransactionStore(), new BlockStore());
        TestHttpExchange exchange = new TestHttpExchange("GET", "/inv", "");

        handler.handle(exchange);

        assertEquals(405, exchange.getResponseCode());
        assertEquals("Method Not Allowed", exchange.getResponseBodyAsString());
    }

    @Test
    void returns400WhenTransactionDataIsBlank() throws IOException {
        PostInvHandler handler = createHandler(new TransactionStore(), new BlockStore());
        TestHttpExchange exchange = new TestHttpExchange("POST", "/inv", "{\"data\":\"\"}");

        handler.handle(exchange);

        assertEquals(400, exchange.getResponseCode());
        assertEquals("Transaction data is required", exchange.getResponseBodyAsString());
    }

    @Test
    void returns409WhenTransactionAlreadyExists() throws IOException {
        TransactionStore transactionStore = new TransactionStore();
        PostInvHandler handler = createHandler(transactionStore, new BlockStore());
        handler.handle(new TestHttpExchange("POST", "/inv", "{\"data\":\"alice->bob:5\"}"));

        TestHttpExchange duplicateExchange = new TestHttpExchange("POST", "/inv", "{\"data\":\"alice->bob:5\"}");
        handler.handle(duplicateExchange);

        assertEquals(409, duplicateExchange.getResponseCode());
        assertEquals("Transaction already exists", duplicateExchange.getResponseBodyAsString());
    }

    @Test
    void returnsAcceptedResponseForNewTransaction() throws IOException {
        PostInvHandler handler = createHandler(new TransactionStore(), new BlockStore());
        TestHttpExchange exchange = new TestHttpExchange("POST", "/inv", "{\"data\":\"alice->bob:5\"}");

        handler.handle(exchange);

        assertEquals(200, exchange.getResponseCode());
        JsonNode response = objectMapper.readTree(exchange.getResponseBodyAsString());
        assertTrue(response.get("accepted").asBoolean());
        assertEquals(64, response.get("hash").asText().length());
    }

    @Test
    void acceptsStructuredTransactionPayload() throws IOException {
        KeyPair senderKeys = CryptoUtils.generateKeyPair();
        String senderPublicKey = CryptoUtils.encodePublicKey(senderKeys.getPublic());
        String receiverPublicKey = CryptoUtils.encodePublicKey(CryptoUtils.generateKeyPair().getPublic());
        BlockStore blockStore = new BlockStore();
        blockStore.addBlock(fundingBlock(senderPublicKey, new BigDecimal("10.00")));

        PostInvHandler handler = createHandler(new TransactionStore(), blockStore);
        String payload = "{\"from\":\"" + senderPublicKey + "\",\"to\":\"" + receiverPublicKey
                + "\",\"amount\":5.00,\"timestamp\":\"2026-05-09T16:00:00Z\"}";
        String signature = CryptoUtils.sign(
                new model.TransactionPayload(senderPublicKey, receiverPublicKey, new BigDecimal("5.00"), "2026-05-09T16:00:00Z").canonicalJson(),
                senderKeys.getPrivate()
        );
        TestHttpExchange exchange = new TestHttpExchange(
                "POST",
                "/inv",
                "{\"signature\":\"" + signature + "\",\"transaction\":" + payload + "}"
        );

        handler.handle(exchange);

        assertEquals(200, exchange.getResponseCode());
        JsonNode response = objectMapper.readTree(exchange.getResponseBodyAsString());
        assertTrue(response.get("accepted").asBoolean());
        assertEquals(64, response.get("hash").asText().length());
    }

    @Test
    void rejectsStructuredTransactionWhenSignatureIsInvalid() throws IOException {
        KeyPair senderKeys = CryptoUtils.generateKeyPair();
        KeyPair wrongKeys = CryptoUtils.generateKeyPair();
        String senderPublicKey = CryptoUtils.encodePublicKey(senderKeys.getPublic());
        String receiverPublicKey = CryptoUtils.encodePublicKey(CryptoUtils.generateKeyPair().getPublic());
        BlockStore blockStore = new BlockStore();
        blockStore.addBlock(fundingBlock(senderPublicKey, new BigDecimal("10.00")));

        PostInvHandler handler = createHandler(new TransactionStore(), blockStore);
        String payload = "{\"from\":\"" + senderPublicKey + "\",\"to\":\"" + receiverPublicKey
                + "\",\"amount\":5.00,\"timestamp\":\"2026-05-09T16:00:00Z\"}";
        String signature = CryptoUtils.sign(
                new model.TransactionPayload(senderPublicKey, receiverPublicKey, new BigDecimal("5.00"), "2026-05-09T16:00:00Z").canonicalJson(),
                wrongKeys.getPrivate()
        );
        TestHttpExchange exchange = new TestHttpExchange(
                "POST",
                "/inv",
                "{\"signature\":\"" + signature + "\",\"transaction\":" + payload + "}"
        );

        handler.handle(exchange);

        assertEquals(400, exchange.getResponseCode());
        assertEquals("Transaction signature is invalid", exchange.getResponseBodyAsString());
    }

    private PostInvHandler createHandler(TransactionStore transactionStore, BlockStore blockStore) {
        PeerStore peerStore = new PeerStore(List.of());
        PeerHttpClient peerHttpClient = new PeerHttpClient(objectMapper);
        TransactionBroadcastService transactionBroadcastService =
                new TransactionBroadcastService(peerStore, peerHttpClient, "localhost:8081");
        LedgerStateService ledgerStateService =
                new LedgerStateService(blockStore, transactionStore, new CanonicalChainStore(), objectMapper);
        TransactionValidationService transactionValidationService =
                new TransactionValidationService(transactionStore, ledgerStateService, objectMapper);
        return new PostInvHandler(transactionStore, transactionBroadcastService, transactionValidationService, objectMapper);
    }

    private model.Block fundingBlock(String receiver, BigDecimal amount) {
        model.SignedTransaction rewardTransaction = model.SignedTransaction.from(
                null,
                new model.TransactionPayload("0", receiver, amount, "2026-05-09T15:59:00Z")
        );
        model.LedgerBlock ledgerBlock = model.LedgerBlock.from(
                1,
                null,
                "2026-05-09T15:59:30Z",
                "nonce-1",
                null,
                receiver,
                MerkleUtils.merkleRoot(List.of(rewardTransaction.hash())),
                List.of(rewardTransaction)
        );
        return storeBlock(ledgerBlock);
    }

    private model.Block storeBlock(model.LedgerBlock ledgerBlock) {
        return model.Block.fromHashAndData(ledgerBlock.hash(), ledgerBlock.canonicalDataJson());
    }
}
