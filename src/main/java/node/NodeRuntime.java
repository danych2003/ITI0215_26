package node;

import com.sun.net.httpserver.HttpServer;
import config.NodeConfig;
import config.PeerConfigLoader;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import model.NodeKeyMaterial;
import service.BlockBroadcastService;
import service.BlockMiningService;
import service.BlockPullSyncService;
import service.LedgerStateService;
import service.NodeKeyPairStore;
import service.PeerDiscoveryService;
import service.PeerHttpClient;
import service.TransactionPullSyncService;
import service.TransactionBroadcastService;
import service.TransactionValidationService;
import store.BlockStore;
import store.CanonicalChainStore;
import store.PeerStore;
import store.TransactionStore;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class NodeRuntime {
    private static final int MIN_BROADCAST_THREADS = 4;
    private static final int MAX_BROADCAST_THREADS = 8;
    private static final int BROADCAST_QUEUE_CAPACITY = 256;

    private final NodeConfig config;
    private final NodeLifecycle lifecycle;
    private final HttpServer server;

    public static NodeRuntime create(NodeConfig config) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        String selfAddress = config.selfAddress();

        PeerConfigLoader peerConfigLoader = new PeerConfigLoader(objectMapper, config.peersConfigPath());
        PeerStore peerStore = new PeerStore(peerConfigLoader.loadPeers());
        BlockStore blockStore = new BlockStore(blockStoragePath(config), objectMapper);
        TransactionStore transactionStore = new TransactionStore();
        CanonicalChainStore canonicalChainStore = new CanonicalChainStore(
                config.miningDifficulty(),
                config.miningReward()
        );
        LedgerStateService ledgerStateService =
                new LedgerStateService(blockStore, transactionStore, canonicalChainStore, objectMapper);
        NodeKeyMaterial nodeKeyMaterial = new NodeKeyPairStore(nodeKeysPath(config), objectMapper).loadOrCreate();
        PeerHttpClient peerHttpClient = new PeerHttpClient(objectMapper);
        ExecutorService broadcastExecutor = createBroadcastExecutor();

        peerStore.addPeer(selfAddress);

        BlockBroadcastService blockBroadcastService = new BlockBroadcastService(
                peerStore,
                peerHttpClient,
                selfAddress,
                broadcastExecutor,
                config.broadcastFanOut()
        );
        TransactionBroadcastService transactionBroadcastService =
                new TransactionBroadcastService(
                        peerStore,
                        peerHttpClient,
                        selfAddress,
                        broadcastExecutor,
                        config.broadcastFanOut()
                );
        PeerDiscoveryService peerDiscoveryService = new PeerDiscoveryService(objectMapper);
        BlockPullSyncService blockPullSyncService = new BlockPullSyncService(
                peerStore,
                ledgerStateService,
                peerHttpClient,
                selfAddress
        );
        TransactionPullSyncService transactionPullSyncService = new TransactionPullSyncService(
                peerStore,
                transactionStore,
                peerHttpClient,
                selfAddress
        );
        TransactionValidationService transactionValidationService =
                new TransactionValidationService(transactionStore, ledgerStateService, objectMapper);
        BlockMiningService blockMiningService = new BlockMiningService(
                transactionStore::getAllTransactions,
                ledgerStateService,
                blockBroadcastService,
                nodeKeyMaterial,
                objectMapper,
                config.miningDifficulty(),
                config.miningIntervalMillis(),
                config.miningReward()
        );

        NodeHttpServerFactory serverFactory = new NodeHttpServerFactory();
        HttpServer server = serverFactory.create(
                config,
                selfAddress,
                peerStore,
                ledgerStateService,
                transactionStore,
                blockBroadcastService,
                transactionBroadcastService,
                transactionValidationService,
                config.miningDifficulty(),
                config.miningReward(),
                objectMapper
        );

        NodeLifecycle lifecycle = new NodeLifecycle(
                peerStore,
                selfAddress,
                peerDiscoveryService,
                blockPullSyncService,
                transactionPullSyncService,
                blockMiningService,
                broadcastExecutor,
                config.backgroundServicesEnabled()
        );

        log.info("Node public key: {}", nodeKeyMaterial.publicKeyEncoded());

        return new NodeRuntime(config, lifecycle, server);
    }

    private static Path blockStoragePath(NodeConfig config) {
        return Path.of("data", "node-" + config.port(), "blocks.json");
    }

    private static Path nodeKeysPath(NodeConfig config) {
        return Path.of("data", "node-" + config.port(), "keys.json");
    }

    private static ExecutorService createBroadcastExecutor() {
        int threadCount = Math.clamp(Runtime.getRuntime().availableProcessors(),
                MIN_BROADCAST_THREADS, MAX_BROADCAST_THREADS);
        return new ThreadPoolExecutor(
                threadCount,
                threadCount,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(BROADCAST_QUEUE_CAPACITY),
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    public void start() {
        server.start();
        lifecycle.start();
        log.info("Node started on {}", config.selfAddress());
    }

    public void shutdown() {
        lifecycle.shutdown();
        server.stop(0);
    }
}
