import com.sun.net.httpserver.HttpServer;
import handler.*;
import lombok.extern.slf4j.Slf4j;
import service.BlockBroadcastService;
import service.PeerDiscoveryService;
import service.PeerHttpClient;
import service.TransactionBroadcastService;
import store.BlockStore;
import store.PeerStore;
import store.TransactionStore;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

@Slf4j
public class NodeApp {
    public static void main(String[] args) throws IOException {
        int port = validateArgs(args);
        String selfAddress = "localhost:" + port;

        ObjectMapper objectMapper = new ObjectMapper();
        PeerConfigLoader peerConfigLoader = new PeerConfigLoader(objectMapper, "peers.json");
        PeerStore peerStore = new PeerStore(peerConfigLoader.loadPeers());
        BlockStore blockStore = new BlockStore();
        TransactionStore transactionStore = new TransactionStore();
        PeerHttpClient peerHttpClient = new PeerHttpClient(objectMapper);

        peerStore.addPeer(selfAddress);
        BlockBroadcastService blockBroadcastService = new BlockBroadcastService(peerStore, peerHttpClient, selfAddress);
        TransactionBroadcastService transactionBroadcastService =
                new TransactionBroadcastService(peerStore, peerHttpClient, selfAddress);

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/status", new StatusHandler(
                selfAddress,
                peerStore,
                blockStore,
                transactionStore,
                objectMapper
        ));
        server.createContext("/addr", new AddrHandler(peerStore, objectMapper));
        server.createContext("/getblocks", new GetBlocksHandler(blockStore, objectMapper));
        server.createContext("/getblocks/", new GetBlocksAfterHandler(blockStore, objectMapper));
        server.createContext("/getdata/", new GetDataHandler(blockStore, objectMapper));
        server.createContext("/transactions", new GetTransactionsHandler(transactionStore, objectMapper));
        server.createContext("/transactions/", new GetTransactionDataHandler(transactionStore, objectMapper));
        server.createContext("/block", new PostBlockHandler(blockStore, blockBroadcastService, objectMapper));
        server.createContext("/inv", new PostInvHandler(transactionStore, transactionBroadcastService, objectMapper));

        server.setExecutor(Executors.newCachedThreadPool());

        server.start();
        log.info("Node started on {}", selfAddress);

        PeerDiscoveryService peerDiscoveryService = new PeerDiscoveryService(objectMapper);
        peerDiscoveryService.startDiscovery(peerStore, selfAddress);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            peerDiscoveryService.shutdown();
            server.stop(0);
        }));
    }

    private static int validateArgs(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Port argument is required");
        }

        try {
            return Integer.parseInt(args[0]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Port must be a number", e);
        }
    }
}
