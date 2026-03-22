package store;

import model.Transaction;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransactionStoreTest {
    @Test
    void addTransactionRejectsDuplicateHashes() {
        TransactionStore transactionStore = new TransactionStore();
        Transaction transaction = Transaction.fromData("alice->bob:5");

        assertTrue(transactionStore.addTransaction(transaction));
        assertFalse(transactionStore.addTransaction(transaction));
        assertEquals(1, transactionStore.size());
    }

    @Test
    void getTransactionReturnsStoredTransaction() {
        TransactionStore transactionStore = new TransactionStore();
        Transaction transaction = Transaction.fromData("alice->bob:5");
        transactionStore.addTransaction(transaction);

        assertSame(transaction, transactionStore.getTransaction(transaction.getHash()));
    }

    @Test
    void getAllHashesReturnsInsertionOrder() {
        TransactionStore transactionStore = new TransactionStore();
        Transaction first = Transaction.fromData("alice->bob:5");
        Transaction second = Transaction.fromData("bob->carol:2");

        transactionStore.addTransaction(first);
        transactionStore.addTransaction(second);

        assertEquals(
                List.of(first.getHash(), second.getHash()),
                transactionStore.getAllHashes()
        );
    }
}
