package store;

import model.Block;
import model.LedgerBlock;
import model.SignedTransaction;
import model.TransactionPayload;
import service.BlockValidationService;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CanonicalChainStore {
    private final int miningDifficulty;
    private final BigDecimal miningReward;
    private final List<Block> canonicalBlocks = new ArrayList<>();
    private final Set<String> canonicalHashes = new LinkedHashSet<>();

    public CanonicalChainStore() {
        this(0, null);
    }

    public CanonicalChainStore(int miningDifficulty, BigDecimal miningReward) {
        this.miningDifficulty = miningDifficulty;
        this.miningReward = miningReward;
    }

    public synchronized void rebuild(Collection<Block> knownBlocks, ObjectMapper objectMapper) {
        List<Block> knownBlockList = new ArrayList<>(knownBlocks);
        List<BlockWithLedger> structuredBlocks = new ArrayList<>();
        List<Block> legacyBlocks = new ArrayList<>();
        for (Block knownBlock : knownBlockList) {
            LedgerBlock ledgerBlock = BlockValidationService.parseLedgerBlock(knownBlock, objectMapper);
            if (ledgerBlock != null) {
                structuredBlocks.add(new BlockWithLedger(knownBlock, ledgerBlock));
            } else {
                legacyBlocks.add(knownBlock);
            }
        }

        List<Block> selectedCanonicalBlocks;
        if (structuredBlocks.isEmpty()) {
            selectedCanonicalBlocks = knownBlockList;
        } else {
            selectedCanonicalBlocks = selectBestStructuredChain(structuredBlocks, legacyBlocks);
        }

        canonicalBlocks.clear();
        canonicalBlocks.addAll(selectedCanonicalBlocks);
        canonicalHashes.clear();
        for (Block block : selectedCanonicalBlocks) {
            canonicalHashes.add(block.getHash());
        }
    }

    public synchronized List<Block> getCanonicalBlocks() {
        return new ArrayList<>(canonicalBlocks);
    }

    public synchronized List<String> getCanonicalHashes() {
        return new ArrayList<>(canonicalHashes);
    }

    public synchronized List<String> getCanonicalHashesAfter(String hash) {
        List<String> hashes = getCanonicalHashes();
        int index = hashes.indexOf(hash);
        if (index < 0) {
            return List.of();
        }
        return new ArrayList<>(hashes.subList(index + 1, hashes.size()));
    }

    public synchronized int size() {
        return canonicalBlocks.size();
    }

    public synchronized Set<String> transactionHashesInCanonicalChain(ObjectMapper objectMapper) {
        Set<String> transactionHashes = new HashSet<>();
        for (Block block : canonicalBlocks) {
            LedgerBlock ledgerBlock = parseLedgerBlock(block, objectMapper);
            if (ledgerBlock == null) {
                continue;
            }

            for (SignedTransaction transaction : ledgerBlock.transactions()) {
                transactionHashes.add(BlockValidationService.resolveTransactionHash(transaction));
            }
        }
        return transactionHashes;
    }

    public synchronized BigDecimal balanceForAccount(String account, ObjectMapper objectMapper) {
        BigDecimal balance = BigDecimal.ZERO;
        for (Block block : canonicalBlocks) {
            LedgerBlock ledgerBlock = parseLedgerBlock(block, objectMapper);
            if (ledgerBlock == null) {
                continue;
            }

            for (SignedTransaction transaction : ledgerBlock.transactions()) {
                TransactionPayload payload = transaction.transaction();
                if (payload == null || payload.amount() == null) {
                    continue;
                }

                if (account.equals(payload.to())) {
                    balance = balance.add(payload.amount());
                }

                if (account.equals(payload.from()) && !"0".equals(payload.from())) {
                    balance = balance.subtract(payload.amount());
                }
            }
        }
        return balance;
    }

    public synchronized Set<String> orphanBlockHashes(Collection<Block> knownBlocks, ObjectMapper objectMapper) {
        Set<String> orphanHashes = new HashSet<>();
        Set<String> knownHashes = new HashSet<>();
        Map<String, LedgerBlock> structuredByHash = new HashMap<>();

        for (Block block : knownBlocks) {
            knownHashes.add(block.getHash());
            LedgerBlock ledgerBlock = parseLedgerBlock(block, objectMapper);
            if (ledgerBlock != null) {
                structuredByHash.put(block.getHash(), ledgerBlock);
            }
        }

        for (Map.Entry<String, LedgerBlock> entry : structuredByHash.entrySet()) {
            String previousHash = entry.getValue().previousHash();
            if (previousHash != null && !previousHash.isBlank() && !knownHashes.contains(previousHash)) {
                orphanHashes.add(entry.getKey());
            }
        }

        return orphanHashes;
    }

    public synchronized Set<String> forkBlockHashes(Collection<Block> knownBlocks, ObjectMapper objectMapper) {
        Map<String, Integer> childrenByParent = new HashMap<>();
        Map<String, LedgerBlock> structuredByHash = new HashMap<>();
        for (Block block : knownBlocks) {
            LedgerBlock ledgerBlock = parseLedgerBlock(block, objectMapper);
            if (ledgerBlock == null) {
                continue;
            }
            structuredByHash.put(block.getHash(), ledgerBlock);
            String previousHash = ledgerBlock.previousHash();
            if (previousHash != null && !previousHash.isBlank()) {
                childrenByParent.merge(previousHash, 1, Integer::sum);
            }
        }

        Set<String> forkHashes = new HashSet<>();
        for (Map.Entry<String, LedgerBlock> entry : structuredByHash.entrySet()) {
            String parentHash = entry.getValue().previousHash();
            if (parentHash != null && !parentHash.isBlank() && childrenByParent.getOrDefault(parentHash, 0) > 1) {
                forkHashes.add(entry.getKey());
            }
        }

        return forkHashes;
    }

    private List<Block> selectBestStructuredChain(List<BlockWithLedger> structuredBlocks, List<Block> legacyBlocks) {
        Map<String, BlockWithLedger> byHash = new HashMap<>();
        for (BlockWithLedger block : structuredBlocks) {
            byHash.put(block.block().getHash(), block);
        }

        List<List<BlockWithLedger>> candidateChains = new ArrayList<>();
        for (BlockWithLedger candidateTip : structuredBlocks) {
            List<BlockWithLedger> chain = buildChain(candidateTip, byHash);
            if (!chain.isEmpty()) {
                candidateChains.add(chain);
            }
        }

        if (candidateChains.isEmpty()) {
            return legacyBlocks;
        }

        Comparator<List<BlockWithLedger>> comparator = Comparator
                .comparingInt((List<BlockWithLedger> chain) -> chain.get(chain.size() - 1).ledgerBlock().height())
                .thenComparingInt(this::transactionCount)
                .thenComparing(this::lastTimestamp)
                .thenComparing(chain -> chain.get(chain.size() - 1).block().getHash(), Comparator.reverseOrder());

        List<BlockWithLedger> bestChain = candidateChains.stream().max(comparator).orElse(List.of());
        return bestChain.stream().map(BlockWithLedger::block).toList();
    }

    private List<BlockWithLedger> buildChain(BlockWithLedger tip, Map<String, BlockWithLedger> byHash) {
        List<LedgerBlock> lineage = BlockValidationService.lineage(tip.block().getHash(), toLedgerByHash(byHash));
        if (lineage.isEmpty()) {
            return List.of();
        }

        String chainError = BlockValidationService.validateChain(lineage, miningDifficulty, miningReward);
        if (chainError != null) {
            return List.of();
        }

        List<BlockWithLedger> ordered = new ArrayList<>(lineage.size());
        for (LedgerBlock ledgerBlock : lineage) {
            BlockWithLedger blockWithLedger = byHash.get(ledgerBlock.hash());
            if (blockWithLedger == null) {
                return List.of();
            }
            ordered.add(blockWithLedger);
        }
        return ordered;
    }

    private int transactionCount(List<BlockWithLedger> chain) {
        int count = 0;
        for (BlockWithLedger block : chain) {
            count += block.ledgerBlock().transactions().size();
        }
        return count;
    }

    private Instant lastTimestamp(List<BlockWithLedger> chain) {
        try {
            return Instant.parse(chain.get(chain.size() - 1).ledgerBlock().timestamp());
        } catch (Exception e) {
            return Instant.EPOCH;
        }
    }

    private LedgerBlock parseLedgerBlock(Block block, ObjectMapper objectMapper) {
        return BlockValidationService.parseLedgerBlock(block, objectMapper);
    }

    private Map<String, LedgerBlock> toLedgerByHash(Map<String, BlockWithLedger> byHash) {
        Map<String, LedgerBlock> ledgerByHash = new HashMap<>();
        for (Map.Entry<String, BlockWithLedger> entry : byHash.entrySet()) {
            ledgerByHash.put(entry.getKey(), entry.getValue().ledgerBlock());
        }
        return ledgerByHash;
    }

    private record BlockWithLedger(Block block, LedgerBlock ledgerBlock) {
    }
}
