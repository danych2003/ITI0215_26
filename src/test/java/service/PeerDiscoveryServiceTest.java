package service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import store.PeerStore;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeerDiscoveryServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void discoveryAddsOnlyValidPeersFromAddrResponse() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/addr", new JsonHandler(
                objectMapper.writeValueAsBytes(List.of(
                        "localhost:9001",
                        "localhost:0",
                        "bad peer",
                        "localhost:9001/path"
                ))
        ));
        server.start();

        String seedPeer = "localhost:" + server.getAddress().getPort();
        PeerStore peerStore = new PeerStore(List.of(seedPeer, "localhost:8081"));
        PeerDiscoveryService peerDiscoveryService = new PeerDiscoveryService(objectMapper);

        peerDiscoveryService.startDiscovery(peerStore, "localhost:8081");
        try {
            assertTrue(awaitCondition(() -> peerStore.getAllPeers().contains("localhost:9001")));

            Set<String> allPeers = peerStore.getAllPeers();
            assertTrue(allPeers.contains("localhost:9001"));
            assertFalse(allPeers.contains("localhost:0"));
            assertFalse(allPeers.contains("bad peer"));
            assertFalse(allPeers.contains("localhost:9001/path"));
        } finally {
            peerDiscoveryService.shutdown();
        }
    }

    @Test
    void discoveryContinuesAfterMalformedPeerEntry() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/addr", new JsonHandler(
                objectMapper.writeValueAsBytes(List.of("localhost:9002"))
        ));
        server.start();

        String responsivePeer = "localhost:" + server.getAddress().getPort();
        PeerStore peerStore = new PeerStore(List.of("bad peer", responsivePeer, "localhost:8081"));
        PeerDiscoveryService peerDiscoveryService = new PeerDiscoveryService(objectMapper);

        peerDiscoveryService.startDiscovery(peerStore, "localhost:8081");
        try {
            assertTrue(awaitCondition(() -> peerStore.getAllPeers().contains("localhost:9002")));
        } finally {
            peerDiscoveryService.shutdown();
        }
    }

    private static final class JsonHandler implements HttpHandler {
        private final byte[] responseBody;

        private JsonHandler(byte[] responseBody) {
            this.responseBody = responseBody;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBody.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(responseBody);
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
