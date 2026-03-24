package service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import model.Block;
import store.BlockStore;
import store.PeerStore;
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
public class BlockPullSyncService {
    private static final int SYNC_INTERVAL_SECONDS = 10;

    private final PeerStore peerStore;
    private final BlockStore blockStore;
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
                int addedBlocks = syncBlocksFromPeer(peer);
                logPeerRecovered(peer);

                if (addedBlocks > 0) {
                    log.info("Pulled {} new blocks from {}", addedBlocks, peer);
                }
            } catch (Exception e) {
                logPeerFailure(peer, e);
                log.debug("Block pull sync failed for {}", peer, e);
            }
        }
    }

    private int syncBlocksFromPeer(String peer) throws IOException {
        JsonNode hashesNode = peerHttpClient.getJson(peer, "/getblocks");
        if (hashesNode == null || !hashesNode.isArray()) {
            throw new IOException("Unexpected /getblocks response format");
        }

        int added = 0;
        for (JsonNode hashNode : hashesNode) {
            if (!hashNode.isTextual()) {
                continue;
            }

            String hash = hashNode.asText();
            if (blockStore.getBlock(hash) != null) {
                continue;
            }

            JsonNode blockNode = peerHttpClient.getJson(peer, "/getdata/" + hash, true);
            if (blockNode == null || !blockNode.hasNonNull("data") || !blockNode.get("data").isTextual()) {
                continue;
            }

            String data = blockNode.get("data").asText();
            Block block = Block.fromData(data);

            if (!block.getHash().equals(hash)) {
                log.warn("Rejected pulled block from {} due to hash mismatch: requested={}, calculated={}",
                        peer, hash, block.getHash());
                continue;
            }

            if (blockStore.addBlock(block)) {
                added++;
            }
        }

        return added;
    }

    private void logPeerFailure(String peer, Exception exception) {
        String failureReason = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        String previousFailureReason = peerFailures.put(peer, failureReason);

        if (!failureReason.equals(previousFailureReason)) {
            log.warn("Block pull sync failed for {} ({})", peer, failureReason);
            return;
        }

        log.debug("Block pull peer {} is still unavailable ({})", peer, failureReason);
    }

    private void logPeerRecovered(String peer) {
        if (peerFailures.remove(peer) != null) {
            log.info("Block pull sync to {} recovered", peer);
        }
    }
}

