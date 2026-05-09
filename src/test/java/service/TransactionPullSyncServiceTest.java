package service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import model.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import store.PeerStore;
import store.TransactionStore;
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

class TransactionPullSyncServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void pullsMissingTransactionsFromPeer() throws Exception {
        Transaction first = Transaction.fromData("tx-1");
        Transaction second = Transaction.fromData("tx-2");

        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/transactions", new JsonHandler(objectMapper.writeValueAsBytes(List.of(first.getHash(), second.getHash()))));
        server.createContext("/transactions/", new GetTransactionHandler(Map.of(
                first.getHash(), first,
                second.getHash(), second
        )));
        server.start();

        String peer = "localhost:" + server.getAddress().getPort();
        TransactionStore transactionStore = new TransactionStore();
        transactionStore.addTransaction(first);

        PeerStore peerStore = new PeerStore(List.of(peer, "localhost:8081"));
        PeerHttpClient peerHttpClient = new PeerHttpClient(objectMapper);
        TransactionPullSyncService transactionPullSyncService =
                new TransactionPullSyncService(peerStore, transactionStore, peerHttpClient, "localhost:8081");

        transactionPullSyncService.start();
        try {
            assertTrue(awaitCondition(() -> transactionStore.getTransaction(second.getHash()) != null));
            assertNotNull(transactionStore.getTransaction(first.getHash()));
            assertNotNull(transactionStore.getTransaction(second.getHash()));
        } finally {
            transactionPullSyncService.shutdown();
        }
    }

    @Test
    void rejectsPulledTransactionWhenHashDoesNotMatchData() throws Exception {
        String invalidHash = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/transactions", new JsonHandler(objectMapper.writeValueAsBytes(List.of(invalidHash))));
        server.createContext("/transactions/", new JsonHandler(objectMapper.writeValueAsBytes(Map.of(
                "hash", invalidHash,
                "data", "tampered-transaction"
        ))));
        server.start();

        String peer = "localhost:" + server.getAddress().getPort();
        TransactionStore transactionStore = new TransactionStore();

        PeerStore peerStore = new PeerStore(List.of(peer, "localhost:8081"));
        PeerHttpClient peerHttpClient = new PeerHttpClient(objectMapper);
        TransactionPullSyncService transactionPullSyncService =
                new TransactionPullSyncService(peerStore, transactionStore, peerHttpClient, "localhost:8081");

        transactionPullSyncService.start();
        try {
            Thread.sleep(300);
            assertTrue(transactionStore.size() == 0);
            assertNull(transactionStore.getTransaction(invalidHash));
        } finally {
            transactionPullSyncService.shutdown();
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

    private final class GetTransactionHandler implements HttpHandler {
        private final Map<String, Transaction> transactionsByHash;

        private GetTransactionHandler(Map<String, Transaction> transactionsByHash) {
            this.transactionsByHash = transactionsByHash;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String path = exchange.getRequestURI().getPath();
            String hash = path.substring("/transactions/".length());
            Transaction transaction = transactionsByHash.get(hash);

            if (transaction == null) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }

            byte[] body = objectMapper.writeValueAsBytes(Map.of(
                    "hash", transaction.getHash(),
                    "data", transaction.getData()
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
