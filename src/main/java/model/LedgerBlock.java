package model;

import util.CanonicalJsonUtils;
import util.HashUtils;
import util.MerkleUtils;

import java.util.ArrayList;
import java.util.List;

public record LedgerBlock(
        String hash,
        int height,
        String previousHash,
        String timestamp,
        String nonce,
        String creator,
        String merkleRoot,
        List<SignedTransaction> transactions
) {
    public static LedgerBlock from(
            Integer height,
            String previousHash,
            String timestamp,
            String nonce,
            String hash,
            String creator,
            String merkleRoot,
            List<SignedTransaction> transactions
    ) {
        int resolvedHeight = height == null ? 0 : height;
        List<SignedTransaction> resolvedTransactions = transactions == null ? List.of() : List.copyOf(transactions);
        String resolvedMerkleRoot = merkleRoot == null || merkleRoot.isBlank()
                ? MerkleUtils.merkleRoot(resolveTransactionHashes(resolvedTransactions))
                : merkleRoot;
        String canonicalData = canonicalDataJson(
                resolvedHeight,
                previousHash,
                timestamp,
                nonce,
                creator,
                resolvedMerkleRoot,
                resolvedTransactions
        );
        String resolvedHash = hash == null || hash.isBlank() ? HashUtils.sha256Hex(canonicalData) : hash;

        if (!HashUtils.sha256Hex(canonicalData).equals(resolvedHash)) {
            throw new IllegalArgumentException("Block hash does not match block content");
        }

        return new LedgerBlock(
                resolvedHash,
                resolvedHeight,
                previousHash,
                timestamp,
                nonce,
                creator,
                resolvedMerkleRoot,
                resolvedTransactions
        );
    }

    public String canonicalDataJson() {
        return canonicalDataJson(height, previousHash, timestamp, nonce, creator, merkleRoot, transactions);
    }

    public String canonicalJson() {
        return "{"
                + "\"hash\":" + CanonicalJsonUtils.quote(hash)
                + ",\"height\":" + height
                + ",\"previousHash\":" + CanonicalJsonUtils.quote(previousHash)
                + ",\"timestamp\":" + CanonicalJsonUtils.quote(timestamp)
                + ",\"nonce\":" + CanonicalJsonUtils.quote(nonce)
                + ",\"creator\":" + CanonicalJsonUtils.quote(creator)
                + ",\"merkleRoot\":" + CanonicalJsonUtils.quote(merkleRoot)
                + ",\"count\":" + transactions.size()
                + ",\"transactions\":" + transactionsJson(transactions)
                + "}";
    }

    private static String canonicalDataJson(
            int height,
            String previousHash,
            String timestamp,
            String nonce,
            String creator,
            String merkleRoot,
            List<SignedTransaction> transactions
    ) {
        return "{"
                + "\"height\":" + height
                + ",\"previousHash\":" + CanonicalJsonUtils.quote(previousHash)
                + ",\"timestamp\":" + CanonicalJsonUtils.quote(timestamp)
                + ",\"nonce\":" + CanonicalJsonUtils.quote(nonce)
                + ",\"creator\":" + CanonicalJsonUtils.quote(creator)
                + ",\"merkleRoot\":" + CanonicalJsonUtils.quote(merkleRoot)
                + ",\"count\":" + transactions.size()
                + ",\"transactions\":" + transactionsJson(transactions)
                + "}";
    }

    private static String transactionsJson(List<SignedTransaction> transactions) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < transactions.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(transactions.get(i).canonicalJson());
        }
        builder.append(']');
        return builder.toString();
    }

    private static List<String> resolveTransactionHashes(List<SignedTransaction> transactions) {
        List<String> hashes = new ArrayList<>(transactions.size());
        for (SignedTransaction transaction : transactions) {
            if (transaction.hash() != null && !transaction.hash().isBlank()) {
                hashes.add(transaction.hash());
            } else {
                hashes.add(SignedTransaction.from(transaction.signature(), transaction.transaction()).hash());
            }
        }
        return hashes;
    }
}
