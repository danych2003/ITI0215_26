package service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import model.Block;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import store.BlockStore;
import store.PeerStore;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockPullSyncServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void pullsMissingBlocksFromPeer() throws Exception {
        Block first = Block.fromData("block-1");
        Block second = Block.fromData("block-2");

        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/getblocks", new JsonHandler(objectMapper.writeValueAsBytes(List.of(first.getHash(), second.getHash()))));
        server.createContext("/getdata/", new GetDataHandler(Map.of(
                first.getHash(), first,
                second.getHash(), second
        )));
        server.start();

        String peer = "localhost:" + server.getAddress().getPort();
        BlockStore blockStore = new BlockStore();
        blockStore.addBlock(first);

        PeerStore peerStore = new PeerStore(List.of(peer, "localhost:8081"));
        PeerHttpClient peerHttpClient = new PeerHttpClient(objectMapper);
        BlockPullSyncService blockPullSyncService = new BlockPullSyncService(peerStore, blockStore, peerHttpClient, "localhost:8081");

        blockPullSyncService.start();
        try {
            assertTrue(awaitCondition(() -> blockStore.getBlock(second.getHash()) != null));
            assertNotNull(blockStore.getBlock(first.getHash()));
            assertNotNull(blockStore.getBlock(second.getHash()));
        } finally {
            blockPullSyncService.shutdown();
        }
    }

    @Test
    void rejectsPulledBlockWhenHashDoesNotMatchData() throws Exception {
        String invalidHash = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/getblocks", new JsonHandler(objectMapper.writeValueAsBytes(List.of(invalidHash))));
        server.createContext("/getdata/", new JsonHandler(objectMapper.writeValueAsBytes(Map.of(
                "hash", invalidHash,
                "data", "tampered-block"
        ))));
        server.start();

        String peer = "localhost:" + server.getAddress().getPort();
        BlockStore blockStore = new BlockStore();

        PeerStore peerStore = new PeerStore(List.of(peer, "localhost:8081"));
        PeerHttpClient peerHttpClient = new PeerHttpClient(objectMapper);
        BlockPullSyncService blockPullSyncService = new BlockPullSyncService(peerStore, blockStore, peerHttpClient, "localhost:8081");

        blockPullSyncService.start();
        try {
            Thread.sleep(300);
            assertTrue(blockStore.size() == 0);
            assertNull(blockStore.getBlock(invalidHash));
        } finally {
            blockPullSyncService.shutdown();
        }
    }

    private static final class JsonHandler implements HttpHandler {
        private final byte[] body;

        private JsonHandler(byte[] body) {
            this.body = body;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        }
    }

    private final class GetDataHandler implements HttpHandler {
        private final Map<String, Block> blocksByHash;

        private GetDataHandler(Map<String, Block> blocksByHash) {
            this.blocksByHash = blocksByHash;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String hash = path.substring("/getdata/".length());
            Block block = blocksByHash.get(hash);

            if (block == null) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }

            byte[] body = objectMapper.writeValueAsBytes(Map.of(
                    "hash", block.getHash(),
                    "data", block.getData()
            ));
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(body);
            }
        }
    }

    private static boolean awaitCondition(CheckedBooleanSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);

        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return true;
            }

            Thread.sleep(25);
        }

        return condition.getAsBoolean();
    }

    @FunctionalInterface
    private interface CheckedBooleanSupplier {
        boolean getAsBoolean() throws Exception;
    }
}

