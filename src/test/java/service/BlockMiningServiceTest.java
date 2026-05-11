package service;

import model.Block;
import model.NodeKeyMaterial;
import model.SignedTransaction;
import model.Transaction;
import model.TransactionPayload;
import org.junit.jupiter.api.Test;
import store.BlockStore;
import store.CanonicalChainStore;
import store.PeerStore;
import store.TransactionStore;
import tools.jackson.databind.ObjectMapper;
import util.CryptoUtils;
import util.MerkleUtils;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockMiningServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final BigDecimal MINING_REWARD = new BigDecimal("5.00");

    @Test
    void minesStructuredBlockFromPendingTransactions() throws Exception {
        KeyPair minerKeys = CryptoUtils.generateKeyPair();
        String minerPublicKey = CryptoUtils.encodePublicKey(minerKeys.getPublic());
        NodeKeyMaterial nodeKeyMaterial = new NodeKeyMaterial(
                minerPublicKey,
                CryptoUtils.encodePrivateKey(minerKeys.getPrivate()),
                minerKeys.getPublic(),
                minerKeys.getPrivate()
        );

        KeyPair senderKeys = CryptoUtils.generateKeyPair();
        String senderPublicKey = CryptoUtils.encodePublicKey(senderKeys.getPublic());
        String receiverPublicKey = CryptoUtils.encodePublicKey(CryptoUtils.generateKeyPair().getPublic());

        BlockStore blockStore = new BlockStore();
        TransactionStore transactionStore = new TransactionStore();
        CanonicalChainStore canonicalChainStore = new CanonicalChainStore(1, MINING_REWARD);
        LedgerStateService ledgerStateService =
                new LedgerStateService(blockStore, transactionStore, canonicalChainStore, objectMapper);

        SignedTransaction fundingReward = SignedTransaction.from(
                null,
                new TransactionPayload("0", senderPublicKey, new BigDecimal("5.00"), "2026-05-09T18:00:00Z")
        );
        model.LedgerBlock fundingBlock = mineTestBlock(1, null, senderPublicKey, List.of(fundingReward), 1);
        ledgerStateService.addKnownBlock(Block.fromHashAndData(fundingBlock.hash(), fundingBlock.canonicalDataJson()));

        TransactionPayload payload = new TransactionPayload(
                senderPublicKey,
                receiverPublicKey,
                new BigDecimal("2.00"),
                "2026-05-09T18:01:00Z"
        );
        SignedTransaction pendingTransaction = SignedTransaction.from(
                CryptoUtils.sign(payload.canonicalJson(), senderKeys.getPrivate()),
                payload
        );
        transactionStore.addTransaction(Transaction.fromHashAndData(
                pendingTransaction.hash(),
                pendingTransaction.canonicalJson()
        ));

        BlockBroadcastService blockBroadcastService = new BlockBroadcastService(
                new PeerStore(List.of()),
                new PeerHttpClient(objectMapper),
                "localhost:8081"
        );
        BlockMiningService blockMiningService = new BlockMiningService(
                transactionStore::getAllTransactions,
                ledgerStateService,
                blockBroadcastService,
                nodeKeyMaterial,
                objectMapper,
                1,
                1_000L,
                MINING_REWARD
        );

        boolean mined = blockMiningService.mineOnce();

        assertTrue(mined);
        assertEquals(2, ledgerStateService.getCanonicalBlockCount());
        assertEquals(0, transactionStore.size());
        model.LedgerBlock minedBlock = BlockValidationService.parseLedgerBlock(
                ledgerStateService.getCanonicalBlocks().get(1),
                objectMapper
        );
        assertTrue(minedBlock.hash().startsWith("0"));
        assertEquals(minerPublicKey, minedBlock.creator());
        assertEquals(2, minedBlock.transactions().size());
    }

    private model.LedgerBlock mineTestBlock(
            int height,
            String previousHash,
            String creator,
            List<SignedTransaction> transactions,
            int difficulty
    ) {
        String timestamp = "2026-05-09T18:00:30Z";
        String merkleRoot = MerkleUtils.merkleRoot(transactions.stream().map(SignedTransaction::hash).toList());
        long nonce = 0L;
        while (true) {
            model.LedgerBlock block = model.LedgerBlock.from(
                    height,
                    previousHash,
                    timestamp,
                    Long.toHexString(nonce),
                    null,
                    creator,
                    merkleRoot,
                    transactions
            );
            if (block.hash().startsWith("0".repeat(difficulty))) {
                return block;
            }
            nonce++;
        }
    }
}
