package node;

import com.sun.net.httpserver.HttpServer;
import config.NodeConfig;
import org.junit.jupiter.api.Test;
import service.BlockBroadcastService;
import service.PeerHttpClient;
import service.TransactionBroadcastService;
import store.BlockStore;
import store.PeerStore;
import store.TransactionStore;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NodeHttpServerFactoryTest {
    @Test
    void bindsServerToConfiguredHostAndPort() throws IOException {
        NodeConfig config = new NodeConfig(0, "127.0.0.1", "peers.json", false, 0);
        ObjectMapper objectMapper = new ObjectMapper();
        PeerStore peerStore = new PeerStore(java.util.List.of("127.0.0.1:8081"));
        Path storageFile = Files.createTempFile("block-store", ".json");
        Files.deleteIfExists(storageFile);
        BlockStore blockStore = new BlockStore(storageFile, objectMapper);
        TransactionStore transactionStore = new TransactionStore();
        PeerHttpClient peerHttpClient = new PeerHttpClient(objectMapper);
        BlockBroadcastService blockBroadcastService =
                new BlockBroadcastService(peerStore, peerHttpClient, config.selfAddress());
        TransactionBroadcastService transactionBroadcastService =
                new TransactionBroadcastService(peerStore, peerHttpClient, config.selfAddress());

        NodeHttpServerFactory factory = new NodeHttpServerFactory();
        HttpServer server = factory.create(
                config,
                config.selfAddress(),
                peerStore,
                blockStore,
                transactionStore,
                blockBroadcastService,
                transactionBroadcastService,
                objectMapper
        );

        try {
            InetSocketAddress address = server.getAddress();
            assertEquals("127.0.0.1", address.getAddress().getHostAddress());
            assertTrue(address.getPort() > 0);
        } finally {
            server.stop(0);
            Files.deleteIfExists(storageFile);
        }
    }
}
