package store;

import model.Transaction;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class TransactionStore {
    private final LinkedHashMap<String, Transaction> transactionsByHash = new LinkedHashMap<>();

    public synchronized boolean addTransaction(Transaction transaction) {
        if (transactionsByHash.containsKey(transaction.getHash())) {
            return false;
        }

        transactionsByHash.put(transaction.getHash(), transaction);
        return true;
    }

    public synchronized Transaction getTransaction(String hash) {
        return transactionsByHash.get(hash);
    }

    public synchronized List<String> getAllHashes() {
        return new ArrayList<>(transactionsByHash.keySet());
    }

    public synchronized int size() {
        return transactionsByHash.size();
    }
}
