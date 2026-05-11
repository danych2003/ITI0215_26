package service;

import model.Block;
import model.LedgerBlock;
import model.SignedTransaction;
import model.Transaction;
import model.TransactionPayload;
import org.junit.jupiter.api.Test;
import store.BlockStore;
import store.CanonicalChainStore;
import store.TransactionStore;
import tools.jackson.databind.ObjectMapper;
import util.MerkleUtils;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import util.CryptoUtils;

class LedgerStateServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void rebuildsCanonicalChainFromKnownBlocksAndPrunesPendingTransactions() throws Exception {
        BlockStore blockStore = new BlockStore();
        TransactionStore transactionStore = new TransactionStore();
        LedgerStateService ledgerStateService =
                new LedgerStateService(blockStore, transactionStore, new CanonicalChainStore(), objectMapper);

        KeyPair aliceKeys = CryptoUtils.generateKeyPair();
        String alice = CryptoUtils.encodePublicKey(aliceKeys.getPublic());
        String bob = CryptoUtils.encodePublicKey(CryptoUtils.generateKeyPair().getPublic());

        SignedTransaction firstTransaction = SignedTransaction.from(
                null,
                new TransactionPayload("0", alice, new BigDecimal("5.00"), "2026-05-09T10:00:00Z")
        );
        SignedTransaction secondTransaction = signedTransaction(aliceKeys, alice, bob, "2.00", "2026-05-09T10:05:00Z");

        transactionStore.addTransaction(Transaction.fromHashAndData(secondTransaction.hash(), secondTransaction.canonicalJson()));

        Block genesis = block(1, null, "2026-05-09T10:01:00Z", "genesis", List.of(firstTransaction));
        Block child = block(2, genesis.getHash(), "2026-05-09T10:06:00Z", "child", List.of(secondTransaction));

        ledgerStateService.addKnownBlock(genesis);
        ledgerStateService.addKnownBlock(child);

        assertEquals(List.of(genesis.getHash(), child.getHash()), ledgerStateService.getCanonicalHashes());
        assertTrue(ledgerStateService.balanceForAccount(alice).compareTo(new BigDecimal("3.00")) == 0);
        assertEquals(0, transactionStore.size());
    }

    @Test
    void detectsForkAndOrphanBlocks() throws Exception {
        BlockStore blockStore = new BlockStore();
        TransactionStore transactionStore = new TransactionStore();
        LedgerStateService ledgerStateService =
                new LedgerStateService(blockStore, transactionStore, new CanonicalChainStore(), objectMapper);

        Block genesis = block(1, null, "2026-05-09T10:01:00Z", "genesis", List.of());
        Block branchA = block(2, genesis.getHash(), "2026-05-09T10:02:00Z", "a", List.of());
        Block branchB = block(2, genesis.getHash(), "2026-05-09T10:03:00Z", "b", List.of());
        Block orphan = block(3, "missing-parent", "2026-05-09T10:04:00Z", "orphan", List.of());

        ledgerStateService.addKnownBlock(genesis);
        ledgerStateService.addKnownBlock(branchA);
        ledgerStateService.addKnownBlock(branchB);
        ledgerStateService.addKnownBlock(orphan);

        assertTrue(ledgerStateService.getForkBlockHashes().contains(branchA.getHash()));
        assertTrue(ledgerStateService.getForkBlockHashes().contains(branchB.getHash()));
        assertTrue(ledgerStateService.getOrphanBlockHashes().contains(orphan.getHash()));
    }

    @Test
    void choosesHigherTransactionCountWhenHeightMatches() throws Exception {
        BlockStore blockStore = new BlockStore();
        TransactionStore transactionStore = new TransactionStore();
        LedgerStateService ledgerStateService =
                new LedgerStateService(blockStore, transactionStore, new CanonicalChainStore(), objectMapper);

        KeyPair aliceKeys = CryptoUtils.generateKeyPair();
        String alice = CryptoUtils.encodePublicKey(aliceKeys.getPublic());
        String bob = CryptoUtils.encodePublicKey(CryptoUtils.generateKeyPair().getPublic());
        String carol = CryptoUtils.encodePublicKey(CryptoUtils.generateKeyPair().getPublic());

        SignedTransaction reward = SignedTransaction.from(
                null,
                new TransactionPayload("0", alice, new BigDecimal("10.00"), "2026-05-09T10:00:00Z")
        );
        SignedTransaction paymentOne = signedTransaction(aliceKeys, alice, bob, "3.00", "2026-05-09T10:01:00Z");
        SignedTransaction paymentTwo = signedTransaction(aliceKeys, alice, carol, "2.00", "2026-05-09T10:02:00Z");

        Block genesis = block(1, null, "2026-05-09T10:00:30Z", "genesis", List.of(reward));
        Block lightBranch = block(2, genesis.getHash(), "2026-05-09T10:03:00Z", "light", List.of(paymentOne));
        Block heavyBranch = block(2, genesis.getHash(), "2026-05-09T10:04:00Z", "heavy", List.of(paymentOne, paymentTwo));

        ledgerStateService.addKnownBlock(genesis);
        ledgerStateService.addKnownBlock(lightBranch);
        ledgerStateService.addKnownBlock(heavyBranch);

        assertEquals(List.of(genesis.getHash(), heavyBranch.getHash()), ledgerStateService.getCanonicalHashes());
    }

    @Test
    void ignoresInvalidStructuredBranchDuringCanonicalSelection() throws Exception {
        BlockStore blockStore = new BlockStore();
        TransactionStore transactionStore = new TransactionStore();
        LedgerStateService ledgerStateService =
                new LedgerStateService(blockStore, transactionStore, new CanonicalChainStore(), objectMapper);

        KeyPair aliceKeys = CryptoUtils.generateKeyPair();
        String alice = CryptoUtils.encodePublicKey(aliceKeys.getPublic());
        String bob = CryptoUtils.encodePublicKey(CryptoUtils.generateKeyPair().getPublic());

        SignedTransaction reward = SignedTransaction.from(
                null,
                new TransactionPayload("0", alice, new BigDecimal("5.00"), "2026-05-09T10:00:00Z")
        );
        SignedTransaction validPayment = signedTransaction(aliceKeys, alice, bob, "3.00", "2026-05-09T10:01:00Z");
        SignedTransaction overspend = signedTransaction(aliceKeys, alice, bob, "9.00", "2026-05-09T10:02:00Z");

        Block genesis = block(1, null, "2026-05-09T10:00:30Z", "genesis", List.of(reward));
        Block validBranch = block(2, genesis.getHash(), "2026-05-09T10:03:00Z", "valid", List.of(validPayment));
        Block invalidBranch = block(2, genesis.getHash(), "2026-05-09T10:04:00Z", "invalid", List.of(overspend));

        ledgerStateService.addKnownBlock(genesis);
        ledgerStateService.addKnownBlock(validBranch);
        ledgerStateService.addKnownBlock(invalidBranch);

        assertEquals(List.of(genesis.getHash(), validBranch.getHash()), ledgerStateService.getCanonicalHashes());
    }

    private Block block(
            int height,
            String previousHash,
            String timestamp,
            String nonce,
            List<SignedTransaction> transactions
    ) {
        String creator = "creator";
        for (SignedTransaction transaction : transactions) {
            if (transaction.transaction() != null && "0".equals(transaction.transaction().from())) {
                creator = transaction.transaction().to();
                break;
            }
        }
        LedgerBlock ledgerBlock = LedgerBlock.from(
                height,
                previousHash,
                timestamp,
                nonce,
                null,
                creator,
                MerkleUtils.merkleRoot(transactions.stream().map(transaction -> transaction.hash()).toList()),
                transactions
        );
        return Block.fromHashAndData(ledgerBlock.hash(), ledgerBlock.canonicalDataJson());
    }

    private SignedTransaction signedTransaction(
            KeyPair senderKeys,
            String from,
            String to,
            String amount,
            String timestamp
    ) {
        TransactionPayload payload = new TransactionPayload(from, to, new BigDecimal(amount), timestamp);
        return SignedTransaction.from(CryptoUtils.sign(payload.canonicalJson(), senderKeys.getPrivate()), payload);
    }
}
