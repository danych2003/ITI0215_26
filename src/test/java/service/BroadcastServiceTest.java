package service;

import org.junit.jupiter.api.Test;
import store.PeerStore;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BroadcastServiceTest {
    @Test
    void transactionBroadcastContinuesAfterMalformedPeer() {
        RecordingPeerHttpClient peerHttpClient = new RecordingPeerHttpClient();
        TransactionBroadcastService service = new TransactionBroadcastService(
                new PeerStore(List.of("bad peer", "localhost:8082", "localhost:8081")),
                peerHttpClient,
                "localhost:8081"
        );

        service.broadcast("alice->bob:5");

        assertEquals(List.of("bad peer/inv", "localhost:8082/inv"), peerHttpClient.calls());
    }

    @Test
    void blockBroadcastContinuesAfterMalformedPeer() {
        RecordingPeerHttpClient peerHttpClient = new RecordingPeerHttpClient();
        BlockBroadcastService service = new BlockBroadcastService(
                new PeerStore(List.of("bad peer", "localhost:8082", "localhost:8081")),
                peerHttpClient,
                "localhost:8081"
        );

        service.broadcast("block-1");

        assertEquals(List.of("bad peer/block", "localhost:8082/block"), peerHttpClient.calls());
    }

    @Test
    void transactionBroadcastFanOutLimitsPeerCount() {
        RecordingPeerHttpClient peerHttpClient = new RecordingPeerHttpClient();
        TransactionBroadcastService service = new TransactionBroadcastService(
                new PeerStore(List.of("localhost:8081", "localhost:8082", "localhost:8083", "localhost:8084", "localhost:8085")),
                peerHttpClient,
                "localhost:8081",
                2
        );

        service.broadcast("alice->bob:5");

        assertEquals(2, peerHttpClient.calls().size());
        Set<String> uniqueCalls = new HashSet<>(peerHttpClient.calls());
        assertEquals(2, uniqueCalls.size());
        for (String call : uniqueCalls) {
            assertTrue(call.endsWith("/inv"));
        }
    }

    private static final class RecordingPeerHttpClient extends PeerHttpClient {
        private final List<String> calls = new ArrayList<>();

        private RecordingPeerHttpClient() {
            super(new ObjectMapper());
        }

        @Override
        public void postJson(String peer, String path, Object body) throws IOException {
            calls.add(peer + path);

            if ("bad peer".equals(peer)) {
                throw new IllegalArgumentException("Illegal character in authority");
            }
        }

        private List<String> calls() {
            return calls;
        }
    }
}
