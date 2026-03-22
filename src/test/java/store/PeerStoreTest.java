package store;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PeerStoreTest {
    @Test
    void addPeerRejectsDuplicatePeer() {
        PeerStore peerStore = new PeerStore(List.of("localhost:8081"));

        peerStore.addPeer("localhost:8081");

        assertEquals(1, peerStore.size());
        assertEquals(Set.of("localhost:8081"), peerStore.getAllPeers());
    }

    @Test
    void addPeersAddsOnlyUniquePeers() {
        PeerStore peerStore = new PeerStore(List.of("localhost:8081"));

        peerStore.addPeers(List.of("localhost:8081", "localhost:8082", "localhost:8083"));

        assertEquals(3, peerStore.size());
        assertEquals(
                Set.of("localhost:8081", "localhost:8082", "localhost:8083"),
                peerStore.getAllPeers()
        );
    }

    @Test
    void getAllPeersReturnsDefensiveCopy() {
        PeerStore peerStore = new PeerStore(List.of("localhost:8081"));

        Set<String> returnedPeers = peerStore.getAllPeers();
        returnedPeers.add("localhost:8082");

        assertEquals(1, peerStore.size());
        assertFalse(peerStore.getAllPeers().contains("localhost:8082"));
        assertTrue(returnedPeers.contains("localhost:8082"));
    }
}
