package service;

import model.Block;
import model.LedgerBlock;
import model.SignedTransaction;
import model.TransactionPayload;
import model.request.CreateTransactionRequest;
import org.junit.jupiter.api.Test;
import store.BlockStore;
import store.CanonicalChainStore;
import store.TransactionStore;
import tools.jackson.databind.ObjectMapper;
import util.CryptoUtils;
import util.MerkleUtils;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionValidationServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void acceptsValidSignedTransactionWhenBalanceIsSufficient() throws Exception {
        KeyPair senderKeys = CryptoUtils.generateKeyPair();
        String senderPublicKey = CryptoUtils.encodePublicKey(senderKeys.getPublic());
        String receiverPublicKey = CryptoUtils.encodePublicKey(CryptoUtils.generateKeyPair().getPublic());

        BlockStore blockStore = new BlockStore();
        blockStore.addBlock(fundingBlock(senderPublicKey, new BigDecimal("5.00")));
        LedgerStateService ledgerStateService =
                new LedgerStateService(blockStore, new TransactionStore(), new CanonicalChainStore(), objectMapper);

        TransactionValidationService validationService =
                new TransactionValidationService(new TransactionStore(), ledgerStateService, objectMapper);

        CreateTransactionRequest request = signedRequest(
                senderKeys,
                senderPublicKey,
                receiverPublicKey,
                new BigDecimal("3.00"),
                "2026-05-09T17:00:00Z"
        );

        TransactionValidationService.ValidationResult result = validationService.validate(request);

        assertTrue(result.accepted());
        assertEquals(200, result.statusCode());
        assertEquals(64, result.storedTransaction().getHash().length());
    }

    @Test
    void rejectsTransactionWithInvalidSignature() throws Exception {
        KeyPair senderKeys = CryptoUtils.generateKeyPair();
        KeyPair wrongKeys = CryptoUtils.generateKeyPair();
        String senderPublicKey = CryptoUtils.encodePublicKey(senderKeys.getPublic());
        String receiverPublicKey = CryptoUtils.encodePublicKey(CryptoUtils.generateKeyPair().getPublic());

        BlockStore blockStore = new BlockStore();
        blockStore.addBlock(fundingBlock(senderPublicKey, new BigDecimal("5.00")));
        LedgerStateService ledgerStateService =
                new LedgerStateService(blockStore, new TransactionStore(), new CanonicalChainStore(), objectMapper);

        TransactionPayload payload = new TransactionPayload(
                senderPublicKey,
                receiverPublicKey,
                new BigDecimal("3.00"),
                "2026-05-09T17:00:00Z"
        );

        CreateTransactionRequest request = new CreateTransactionRequest(
                null,
                CryptoUtils.sign(payload.canonicalJson(), wrongKeys.getPrivate()),
                payload
        );

        TransactionValidationService.ValidationResult result =
                new TransactionValidationService(new TransactionStore(), ledgerStateService, objectMapper).validate(request);

        assertFalse(result.accepted());
        assertEquals(400, result.statusCode());
        assertEquals("Transaction signature is invalid", result.message());
    }

    @Test
    void rejectsTransactionWhenBalanceIsInsufficient() throws Exception {
        KeyPair senderKeys = CryptoUtils.generateKeyPair();
        String senderPublicKey = CryptoUtils.encodePublicKey(senderKeys.getPublic());
        String receiverPublicKey = CryptoUtils.encodePublicKey(CryptoUtils.generateKeyPair().getPublic());

        BlockStore blockStore = new BlockStore();
        blockStore.addBlock(fundingBlock(senderPublicKey, new BigDecimal("2.00")));
        LedgerStateService ledgerStateService =
                new LedgerStateService(blockStore, new TransactionStore(), new CanonicalChainStore(), objectMapper);

        CreateTransactionRequest request = signedRequest(
                senderKeys,
                senderPublicKey,
                receiverPublicKey,
                new BigDecimal("3.00"),
                "2026-05-09T17:00:00Z"
        );

        TransactionValidationService.ValidationResult result =
                new TransactionValidationService(new TransactionStore(), ledgerStateService, objectMapper).validate(request);

        assertFalse(result.accepted());
        assertEquals(400, result.statusCode());
        assertEquals("Insufficient balance", result.message());
    }

    private CreateTransactionRequest signedRequest(
            KeyPair senderKeys,
            String from,
            String to,
            BigDecimal amount,
            String timestamp
    ) {
        TransactionPayload payload = new TransactionPayload(from, to, amount, timestamp);
        return new CreateTransactionRequest(
                null,
                CryptoUtils.sign(payload.canonicalJson(), senderKeys.getPrivate()),
                payload
        );
    }

    private Block fundingBlock(String receiver, BigDecimal amount) {
        SignedTransaction rewardTransaction = SignedTransaction.from(
                null,
                new TransactionPayload("0", receiver, amount, "2026-05-09T16:30:00Z")
        );
        LedgerBlock ledgerBlock = LedgerBlock.from(
                1,
                null,
                "2026-05-09T16:31:00Z",
                "nonce-1",
                null,
                receiver,
                MerkleUtils.merkleRoot(List.of(rewardTransaction.hash())),
                List.of(rewardTransaction)
        );
        return Block.fromHashAndData(ledgerBlock.hash(), ledgerBlock.canonicalDataJson());
    }
}
