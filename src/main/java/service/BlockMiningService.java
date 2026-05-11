package service;

import lombok.extern.slf4j.Slf4j;
import model.Block;
import model.LedgerBlock;
import model.NodeKeyMaterial;
import model.SignedTransaction;
import model.Transaction;
import model.TransactionPayload;
import model.request.CreateBlockRequest;
import tools.jackson.databind.ObjectMapper;
import util.MerkleUtils;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public final class BlockMiningService {
    private final TransactionStoreView transactionStoreView;
    private final LedgerStateService ledgerStateService;
    private final BlockBroadcastService blockBroadcastService;
    private final NodeKeyMaterial nodeKeyMaterial;
    private final ObjectMapper objectMapper;
    private final int miningDifficulty;
    private final long miningIntervalMillis;
    private final BigDecimal miningReward;
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    public BlockMiningService(
            TransactionStoreView transactionStoreView,
            LedgerStateService ledgerStateService,
            BlockBroadcastService blockBroadcastService,
            NodeKeyMaterial nodeKeyMaterial,
            ObjectMapper objectMapper,
            int miningDifficulty,
            long miningIntervalMillis,
            BigDecimal miningReward
    ) {
        this.transactionStoreView = transactionStoreView;
        this.ledgerStateService = ledgerStateService;
        this.blockBroadcastService = blockBroadcastService;
        this.nodeKeyMaterial = nodeKeyMaterial;
        this.objectMapper = objectMapper;
        this.miningDifficulty = miningDifficulty;
        this.miningIntervalMillis = miningIntervalMillis;
        this.miningReward = miningReward;
    }

    public void start() {
        executorService.scheduleWithFixedDelay(this::safeMineOnce, miningIntervalMillis, miningIntervalMillis, TimeUnit.MILLISECONDS);
    }

    public void shutdown() {
        executorService.shutdownNow();
        try {
            executorService.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public boolean mineOnce() throws IOException {
        List<SignedTransaction> pendingTransactions = structuredPendingTransactions();
        if (pendingTransactions.isEmpty()) {
            return false;
        }

        String timestamp = Instant.now().toString();
        CanonicalTip canonicalTip = currentTip();
        SignedTransaction rewardTransaction = SignedTransaction.from(
                null,
                new TransactionPayload("0", nodeKeyMaterial.publicKeyEncoded(), miningReward, timestamp)
        );

        List<SignedTransaction> blockTransactions = new ArrayList<>(pendingTransactions.size() + 1);
        blockTransactions.add(rewardTransaction);
        blockTransactions.addAll(pendingTransactions);

        String merkleRoot = MerkleUtils.merkleRoot(blockTransactions.stream()
                .map(BlockValidationService::resolveTransactionHash)
                .toList());

        LedgerBlock ledgerBlock = mineBlock(
                canonicalTip.height() + 1,
                canonicalTip.hash(),
                timestamp,
                nodeKeyMaterial.publicKeyEncoded(),
                merkleRoot,
                blockTransactions
        );

        BlockValidationService.ValidationResult validationResult = BlockValidationService.validateStructuredBlock(
                ledgerBlock,
                ledgerStateService.getKnownBlocks(),
                objectMapper,
                miningDifficulty,
                miningReward
        );
        if (!validationResult.accepted()) {
            log.debug("Discarded locally mined block {}: {}", ledgerBlock.hash(), validationResult.message());
            return false;
        }

        Block storedBlock = Block.fromHashAndData(ledgerBlock.hash(), ledgerBlock.canonicalDataJson());
        boolean added = ledgerStateService.addKnownBlock(storedBlock);
        if (!added) {
            return false;
        }

        blockBroadcastService.submitBroadcast(new CreateBlockRequest(
                null,
                ledgerBlock.height(),
                ledgerBlock.previousHash(),
                ledgerBlock.timestamp(),
                ledgerBlock.nonce(),
                ledgerBlock.hash(),
                ledgerBlock.creator(),
                ledgerBlock.merkleRoot(),
                ledgerBlock.transactions()
        ));
        log.info("Mined block {} at height {} with {} transactions", ledgerBlock.hash(), ledgerBlock.height(), ledgerBlock.transactions().size());
        return true;
    }

    private LedgerBlock mineBlock(
            int height,
            String previousHash,
            String timestamp,
            String creator,
            String merkleRoot,
            List<SignedTransaction> transactions
    ) {
        long nonce = 0L;
        while (true) {
            LedgerBlock candidate = LedgerBlock.from(
                    height,
                    previousHash,
                    timestamp,
                    Long.toHexString(nonce),
                    null,
                    creator,
                    merkleRoot,
                    transactions
            );
            if (matchesDifficulty(candidate.hash())) {
                return candidate;
            }
            nonce++;
        }
    }

    private CanonicalTip currentTip() {
        List<Block> canonicalBlocks = ledgerStateService.getCanonicalBlocks();
        for (int i = canonicalBlocks.size() - 1; i >= 0; i--) {
            LedgerBlock ledgerBlock = BlockValidationService.parseLedgerBlock(canonicalBlocks.get(i), objectMapper);
            if (ledgerBlock != null) {
                return new CanonicalTip(ledgerBlock.height(), ledgerBlock.hash());
            }
        }
        return new CanonicalTip(0, null);
    }

    private List<SignedTransaction> structuredPendingTransactions() {
        List<SignedTransaction> structuredTransactions = new ArrayList<>();
        for (Transaction transaction : transactionStoreView.getAllTransactions()) {
            try {
                SignedTransaction signedTransaction = objectMapper.readValue(transaction.getData(), SignedTransaction.class);
                if (signedTransaction.transaction() != null) {
                    structuredTransactions.add(SignedTransaction.from(
                            signedTransaction.signature(),
                            signedTransaction.transaction()
                    ));
                }
            } catch (Exception ignored) {
                // Legacy plain string transaction; skip for structured mining.
            }
        }
        return structuredTransactions;
    }

    private boolean matchesDifficulty(String hash) {
        if (miningDifficulty <= 0) {
            return true;
        }

        for (int i = 0; i < miningDifficulty; i++) {
            if (hash.charAt(i) != '0') {
                return false;
            }
        }
        return true;
    }

    private void safeMineOnce() {
        try {
            mineOnce();
        } catch (Exception e) {
            log.debug("Mining iteration failed", e);
        }
    }

    private record CanonicalTip(int height, String hash) {
    }

    public interface TransactionStoreView {
        List<Transaction> getAllTransactions();
    }
}
