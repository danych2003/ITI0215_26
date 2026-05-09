package service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import model.Transaction;
import store.PeerStore;
import store.TransactionStore;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
public class TransactionPullSyncService {
    private static final int SYNC_INTERVAL_SECONDS = 10;

    private final PeerStore peerStore;
    private final TransactionStore transactionStore;
    private final PeerHttpClient peerHttpClient;
    private final String selfAddress;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Map<String, String> peerFailures = new ConcurrentHashMap<>();

    public void start() {
        scheduler.scheduleWithFixedDelay(this::runSync, 0, SYNC_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }

    private void runSync() {
        Set<String> peers = peerStore.getAllPeers();

        for (String peer : peers) {
            if (peer.equals(selfAddress)) {
                continue;
            }

            try {
                int addedTransactions = syncTransactionsFromPeer(peer);
                logPeerRecovered(peer);

                if (addedTransactions > 0) {
                    log.info("Pulled {} new transactions from {}", addedTransactions, peer);
                }
            } catch (Exception e) {
                logPeerFailure(peer, e);
                log.debug("Transaction pull sync failed for {}", peer, e);
            }
        }
    }

    private int syncTransactionsFromPeer(String peer) throws IOException {
        JsonNode hashesNode = peerHttpClient.getJson(peer, "/transactions");
        if (hashesNode == null || !hashesNode.isArray()) {
            throw new IOException("Unexpected /transactions response format");
        }

        int added = 0;
        for (JsonNode hashNode : hashesNode) {
            if (!hashNode.isTextual()) {
                continue;
            }

            String hash = hashNode.asText();
            if (transactionStore.getTransaction(hash) != null) {
                continue;
            }

            JsonNode transactionNode = peerHttpClient.getJson(peer, "/transactions/" + hash, true);
            if (transactionNode == null || !transactionNode.hasNonNull("data") || !transactionNode.get("data").isTextual()) {
                continue;
            }

            String data = transactionNode.get("data").asText();
            Transaction transaction = Transaction.fromData(data);

            if (!transaction.getHash().equals(hash)) {
                log.warn("Rejected pulled transaction from {} due to hash mismatch: requested={}, calculated={}",
                        peer, hash, transaction.getHash());
                continue;
            }

            if (transactionStore.addTransaction(transaction)) {
                added++;
            }
        }

        return added;
    }

    private void logPeerFailure(String peer, Exception exception) {
        String failureReason = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        String previousFailureReason = peerFailures.put(peer, failureReason);

        if (!failureReason.equals(previousFailureReason)) {
            log.warn("Transaction pull sync failed for {} ({})", peer, failureReason);
            return;
        }

        log.debug("Transaction pull peer {} is still unavailable ({})", peer, failureReason);
    }

    private void logPeerRecovered(String peer) {
        if (peerFailures.remove(peer) != null) {
            log.info("Transaction pull sync to {} recovered", peer);
        }
    }
}
