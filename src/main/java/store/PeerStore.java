package store;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class PeerStore {
    private final Set<String> peers;

    public PeerStore(Collection<String> initialPeers) {
        this.peers = new HashSet<>(initialPeers);
    }

    public void addPeer(String peer) {
        peers.add(peer);
    }

    public void addPeers(Collection<String> discoveredPeers) {
        peers.addAll(discoveredPeers);
    }

    public Set<String> getAllPeers() {
        return new HashSet<>(peers);
    }

    public int size() {
        return peers.size();
    }
}
