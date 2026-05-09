package node;

import lombok.RequiredArgsConstructor;
import service.BlockPullSyncService;
import service.PeerDiscoveryService;
import service.TransactionPullSyncService;
import store.PeerStore;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public final class NodeLifecycle {
    private final PeerStore peerStore;
    private final String selfAddress;
    private final PeerDiscoveryService peerDiscoveryService;
    private final BlockPullSyncService blockPullSyncService;
    private final TransactionPullSyncService transactionPullSyncService;
    private final ExecutorService broadcastExecutor;
    private final boolean backgroundServicesEnabled;

    public void start() {
        if (backgroundServicesEnabled) {
            peerDiscoveryService.startDiscovery(peerStore, selfAddress);
            blockPullSyncService.start();
            transactionPullSyncService.start();
        }
    }

    public void shutdown() {
        if (backgroundServicesEnabled) {
            transactionPullSyncService.shutdown();
            blockPullSyncService.shutdown();
            peerDiscoveryService.shutdown();
        }
        broadcastExecutor.shutdownNow();
        try {
            broadcastExecutor.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
