package model;

import util.CanonicalJsonUtils;
import util.HashUtils;

public record SignedTransaction(
        String hash,
        String signature,
        TransactionPayload transaction
) {
    public static SignedTransaction from(String signature, TransactionPayload transaction) {
        String canonicalJson = canonicalJson(signature, transaction);
        return new SignedTransaction(HashUtils.sha256Hex(canonicalJson), signature, transaction);
    }

    public String canonicalJson() {
        return canonicalJson(signature, transaction);
    }

    private static String canonicalJson(String signature, TransactionPayload transaction) {
        return "{"
                + "\"signature\":" + CanonicalJsonUtils.quote(signature)
                + ",\"transaction\":" + (transaction == null ? "null" : transaction.canonicalJson())
                + "}";
    }
}
