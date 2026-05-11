package model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StructuredLedgerModelTest {
    @Test
    void signedTransactionHashIsDeterministic() {
        TransactionPayload payload = new TransactionPayload(
                "alice-public-key",
                "bob-public-key",
                new BigDecimal("1.50"),
                "2026-05-09T16:00:00Z"
        );

        SignedTransaction first = SignedTransaction.from(null, payload);
        SignedTransaction second = SignedTransaction.from(null, payload);

        assertEquals(first.hash(), second.hash());
        assertEquals(first.canonicalJson(), second.canonicalJson());
    }

    @Test
    void ledgerBlockHashIsDeterministic() {
        SignedTransaction transaction = SignedTransaction.from(
                null,
                new TransactionPayload("alice", "bob", new BigDecimal("2.00"), "2026-05-09T16:00:00Z")
        );

        LedgerBlock first = LedgerBlock.from(
                3,
                "prev-hash",
                "2026-05-09T16:01:00Z",
                "nonce-1",
                null,
                "node-a",
                "merkle-1",
                List.of(transaction)
        );
        LedgerBlock second = LedgerBlock.from(
                3,
                "prev-hash",
                "2026-05-09T16:01:00Z",
                "nonce-1",
                null,
                "node-a",
                "merkle-1",
                List.of(transaction)
        );

        assertEquals(first.hash(), second.hash());
        assertEquals(first.canonicalDataJson(), second.canonicalDataJson());
    }

    @Test
    void ledgerBlockRejectsMismatchedHash() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> LedgerBlock.from(
                        1,
                        null,
                        "2026-05-09T16:01:00Z",
                        "nonce-1",
                        "bad-hash",
                        "node-a",
                        "merkle-1",
                        List.of()
                )
        );

        assertEquals("Block hash does not match block content", exception.getMessage());
    }
}
