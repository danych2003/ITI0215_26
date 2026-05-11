package service;

import model.Block;
import model.LedgerBlock;
import model.SignedTransaction;
import model.TransactionPayload;
import model.request.CreateBlockRequest;
import tools.jackson.databind.ObjectMapper;
import util.CryptoUtils;
import util.MerkleUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class BlockValidationService {
    private BlockValidationService() {
    }

    public static ValidationResult validate(
            CreateBlockRequest request,
            Collection<Block> knownBlocks,
            ObjectMapper objectMapper,
            int difficulty,
            BigDecimal rewardAmount
    ) {
        if (request.timestamp() == null || request.timestamp().isBlank()) {
            return ValidationResult.rejected(400, "Block timestamp is required");
        }

        final LedgerBlock ledgerBlock;
        try {
            ledgerBlock = LedgerBlock.from(
                    request.height(),
                    request.previousHash(),
                    request.timestamp(),
                    request.nonce(),
                    request.hash(),
                    request.creator(),
                    request.merkleRoot(),
                    request.transactions()
            );
        } catch (IllegalArgumentException e) {
            return ValidationResult.rejected(400, e.getMessage());
        }

        ValidationResult chainValidation = validateStructuredBlock(ledgerBlock, knownBlocks, objectMapper, difficulty, rewardAmount);
        if (!chainValidation.accepted()) {
            return chainValidation;
        }

        return ValidationResult.accepted(Block.fromHashAndData(ledgerBlock.hash(), ledgerBlock.canonicalDataJson()));
    }

    public static ValidationResult validateStructuredBlock(
            LedgerBlock candidate,
            Collection<Block> knownBlocks,
            ObjectMapper objectMapper,
            int difficulty,
            BigDecimal rewardAmount
    ) {
        Map<String, LedgerBlock> structuredByHash = new LinkedHashMap<>();
        for (Block knownBlock : knownBlocks) {
            LedgerBlock ledgerBlock = parseLedgerBlock(knownBlock, objectMapper);
            if (ledgerBlock != null) {
                structuredByHash.put(knownBlock.getHash(), ledgerBlock);
            }
        }
        structuredByHash.put(candidate.hash(), candidate);

        List<LedgerBlock> chain = lineage(candidate.hash(), structuredByHash);
        if (chain.isEmpty()) {
            return ValidationResult.rejected(400, "Parent block is missing");
        }

        String error = validateChain(chain, difficulty, rewardAmount);
        if (error != null) {
            return ValidationResult.rejected(400, error);
        }

        return ValidationResult.accepted(null);
    }

    public static String validateChain(List<LedgerBlock> chain, int difficulty, BigDecimal rewardAmount) {
        if (chain.isEmpty()) {
            return "Structured chain is empty";
        }

        Map<String, BigDecimal> balances = new HashMap<>();
        Set<String> seenTransactionHashes = new HashSet<>();

        for (int i = 0; i < chain.size(); i++) {
            LedgerBlock block = chain.get(i);
            String blockError = validateBlockShape(block, i == 0 ? null : chain.get(i - 1), difficulty);
            if (blockError != null) {
                return blockError;
            }

            String merkleRoot = MerkleUtils.merkleRoot(block.transactions().stream()
                    .map(BlockValidationService::resolveTransactionHash)
                    .toList());
            if (!merkleRoot.equals(block.merkleRoot())) {
                return "Block merkle root does not match transactions";
            }

            int rewardTransactions = 0;
            Set<String> blockTransactionHashes = new HashSet<>();
            for (SignedTransaction transaction : block.transactions()) {
                if (transaction == null || transaction.transaction() == null) {
                    return "Block contains malformed transaction";
                }

                TransactionPayload payload = transaction.transaction();
                String transactionError = validateTransactionShape(payload);
                if (transactionError != null) {
                    return transactionError;
                }

                String transactionHash = resolveTransactionHash(transaction);
                if (!blockTransactionHashes.add(transactionHash)) {
                    return "Block contains duplicate transactions";
                }
                if (!seenTransactionHashes.add(transactionHash)) {
                    return "Chain contains duplicate transactions";
                }

                if ("0".equals(payload.from())) {
                    rewardTransactions++;
                    if (rewardTransactions > 1) {
                        return "Block contains multiple reward transactions";
                    }
                    if (!block.creator().equals(payload.to())) {
                        return "Reward transaction recipient must match block creator";
                    }
                    if (rewardAmount != null && payload.amount().compareTo(rewardAmount) != 0) {
                        return "Reward transaction amount is invalid";
                    }
                    if (transaction.signature() != null && !transaction.signature().isBlank()) {
                        return "Reward transaction must not be signed";
                    }
                } else {
                    if (!CryptoUtils.verify(payload.canonicalJson(), transaction.signature(), payload.from())) {
                        return "Block contains transaction with invalid signature";
                    }

                    BigDecimal senderBalance = balances.getOrDefault(payload.from(), BigDecimal.ZERO);
                    if (senderBalance.compareTo(payload.amount()) < 0) {
                        return "Block contains overspending transaction";
                    }
                    balances.put(payload.from(), senderBalance.subtract(payload.amount()));
                }

                balances.put(
                        payload.to(),
                        balances.getOrDefault(payload.to(), BigDecimal.ZERO).add(payload.amount())
                );
            }
        }

        return null;
    }

    public static LedgerBlock parseLedgerBlock(Block block, ObjectMapper objectMapper) {
        try {
            CreateBlockRequest request = objectMapper.readValue(block.getData(), CreateBlockRequest.class);
            if (request.timestamp() == null || request.transactions() == null) {
                return null;
            }

            return LedgerBlock.from(
                    request.height(),
                    request.previousHash(),
                    request.timestamp(),
                    request.nonce(),
                    block.getHash(),
                    request.creator(),
                    request.merkleRoot(),
                    request.transactions()
            );
        } catch (Exception e) {
            return null;
        }
    }

    public static List<LedgerBlock> lineage(String tipHash, Map<String, LedgerBlock> byHash) {
        List<LedgerBlock> reverseChain = new ArrayList<>();
        LedgerBlock current = byHash.get(tipHash);

        while (current != null) {
            reverseChain.add(current);
            String previousHash = current.previousHash();
            if (previousHash == null || previousHash.isBlank()) {
                break;
            }

            current = byHash.get(previousHash);
            if (current == null) {
                return List.of();
            }
        }

        List<LedgerBlock> chain = new ArrayList<>(reverseChain.size());
        for (int i = reverseChain.size() - 1; i >= 0; i--) {
            chain.add(reverseChain.get(i));
        }
        return chain;
    }

    public static String resolveTransactionHash(SignedTransaction transaction) {
        if (transaction.hash() != null && !transaction.hash().isBlank()) {
            return transaction.hash();
        }

        return SignedTransaction.from(transaction.signature(), transaction.transaction()).hash();
    }

    private static String validateBlockShape(LedgerBlock block, LedgerBlock parent, int difficulty) {
        if (block.height() <= 0) {
            return "Block height must be positive";
        }
        if (block.timestamp() == null || block.timestamp().isBlank()) {
            return "Block timestamp is required";
        }
        if (block.creator() == null || block.creator().isBlank()) {
            return "Block creator is required";
        }
        try {
            Instant.parse(block.timestamp());
        } catch (Exception e) {
            return "Block timestamp is invalid";
        }
        if (!hasDifficultyPrefix(block.hash(), difficulty)) {
            return "Block hash does not satisfy mining difficulty";
        }

        if (parent == null) {
            if (block.height() != 1) {
                return "Genesis block height must be 1";
            }
            if (block.previousHash() != null && !block.previousHash().isBlank()) {
                return "Genesis block must not have parent hash";
            }
            return null;
        }

        if (!parent.hash().equals(block.previousHash())) {
            return "Block parent hash does not match previous block";
        }
        if (block.height() != parent.height() + 1) {
            return "Block height does not extend parent height";
        }

        return null;
    }

    private static boolean hasDifficultyPrefix(String hash, int difficulty) {
        if (difficulty <= 0) {
            return true;
        }

        if (hash == null || hash.length() < difficulty) {
            return false;
        }

        for (int i = 0; i < difficulty; i++) {
            if (hash.charAt(i) != '0') {
                return false;
            }
        }
        return true;
    }

    private static String validateTransactionShape(TransactionPayload payload) {
        if (payload.from() == null || payload.from().isBlank()
                || payload.to() == null || payload.to().isBlank()
                || payload.amount() == null
                || payload.timestamp() == null || payload.timestamp().isBlank()) {
            return "Block contains transaction with missing fields";
        }
        if (payload.amount().compareTo(BigDecimal.ZERO) <= 0) {
            return "Block contains transaction with non-positive amount";
        }
        try {
            Instant.parse(payload.timestamp());
        } catch (Exception e) {
            return "Block contains transaction with invalid timestamp";
        }
        return null;
    }

    public record ValidationResult(boolean accepted, int statusCode, String message, Block block) {
        public static ValidationResult accepted(Block block) {
            return new ValidationResult(true, 200, null, block);
        }

        public static ValidationResult rejected(int statusCode, String message) {
            return new ValidationResult(false, statusCode, message, null);
        }
    }
}
