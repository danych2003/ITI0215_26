package service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import store.PeerStore;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@RequiredArgsConstructor
public abstract class AbstractPeerBroadcastService {
    private final PeerStore peerStore;
    private final PeerHttpClient peerHttpClient;
    private final String selfAddress;
    private final Map<String, String> peerFailures = new ConcurrentHashMap<>();

    public void broadcast(String data) {
        Set<String> peers = peerStore.getAllPeers();

        for (String peer : peers) {
            if (peer.equals(selfAddress)) {
                continue;
            }

            try {
                peerHttpClient.postJson(peer, path(), requestBody(data));
                logPeerRecovered(peer);
            } catch (Exception e) {
                logPeerFailure(peer, e);
                log.debug("{} broadcast failed for {}", entityName(), peer, e);
            }
        }
    }

    protected abstract String path();

    protected abstract Object requestBody(String data);

    protected abstract String entityName();

    private void logPeerFailure(String peer, Exception exception) {
        String failureReason = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        String previousFailureReason = peerFailures.put(peer, failureReason);

        if (!failureReason.equals(previousFailureReason)) {
            log.warn("{} broadcast failed for {} ({})", entityName(), peer, failureReason);
            return;
        }

        log.debug("{} peer {} is still unavailable ({})", entityName(), peer, failureReason);
    }

    private void logPeerRecovered(String peer) {
        if (peerFailures.remove(peer) != null) {
            log.info("{} broadcast to {} recovered", entityName(), peer);
        }
    }
}
