package service;

import model.Block;
import store.BlockStore;
import store.CanonicalChainStore;
import store.TransactionStore;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

public final class LedgerStateService {
    private final BlockStore blockStore;
    private final TransactionStore transactionStore;
    private final CanonicalChainStore canonicalChainStore;
    private final ObjectMapper objectMapper;

    public LedgerStateService(
            BlockStore blockStore,
            TransactionStore transactionStore,
            CanonicalChainStore canonicalChainStore,
            ObjectMapper objectMapper
    ) {
        this.blockStore = blockStore;
        this.transactionStore = transactionStore;
        this.canonicalChainStore = canonicalChainStore;
        this.objectMapper = objectMapper;
        rebuildCanonicalChain();
    }

    public synchronized boolean addKnownBlock(Block block) throws IOException {
        boolean added = blockStore.addBlock(block);
        if (added) {
            rebuildCanonicalChain();
        }
        return added;
    }

    public synchronized void rebuildCanonicalChain() {
        canonicalChainStore.rebuild(blockStore.getAllBlocks(), objectMapper);
        transactionStore.removeTransactions(canonicalChainStore.transactionHashesInCanonicalChain(objectMapper));
    }

    public synchronized Block getKnownBlock(String hash) {
        return blockStore.getBlock(hash);
    }

    public synchronized List<Block> getKnownBlocks() {
        return blockStore.getAllBlocks();
    }

    public synchronized List<String> getCanonicalHashes() {
        return canonicalChainStore.getCanonicalHashes();
    }

    public synchronized List<String> getCanonicalHashesAfter(String hash) {
        return canonicalChainStore.getCanonicalHashesAfter(hash);
    }

    public synchronized int getKnownBlockCount() {
        return blockStore.size();
    }

    public synchronized int getCanonicalBlockCount() {
        return canonicalChainStore.size();
    }

    public synchronized BigDecimal balanceForAccount(String account) {
        return canonicalChainStore.balanceForAccount(account, objectMapper);
    }

    public synchronized Set<String> getOrphanBlockHashes() {
        return canonicalChainStore.orphanBlockHashes(blockStore.getAllBlocks(), objectMapper);
    }

    public synchronized Set<String> getForkBlockHashes() {
        return canonicalChainStore.forkBlockHashes(blockStore.getAllBlocks(), objectMapper);
    }

    public synchronized List<Block> getCanonicalBlocks() {
        return canonicalChainStore.getCanonicalBlocks();
    }
}
