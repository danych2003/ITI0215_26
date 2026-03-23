package store;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PeerStore {
    private final Set<String> peers;

    public PeerStore(Collection<String> initialPeers) {
        this.peers = ConcurrentHashMap.newKeySet();
        this.peers.addAll(initialPeers);
    }

    public void addPeer(String peer) {
        peers.add(peer);
    }

    public Set<String> addPeers(Collection<String> discoveredPeers) {
        Set<String> addedPeers = new HashSet<>();

        for (String discoveredPeer : discoveredPeers) {
            if (peers.add(discoveredPeer)) {
                addedPeers.add(discoveredPeer);
            }
        }

        return addedPeers;
    }

    public Set<String> getAllPeers() {
        return new HashSet<>(peers);
    }

    public int size() {
        return peers.size();
    }
}
