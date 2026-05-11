package model.request;

import model.SignedTransaction;

import java.util.List;

public record CreateBlockRequest(
        String data,
        Integer height,
        String previousHash,
        String timestamp,
        String nonce,
        String hash,
        String creator,
        String merkleRoot,
        List<SignedTransaction> transactions
) {
}
