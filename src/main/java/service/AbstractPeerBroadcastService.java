package service;
import lombok.extern.slf4j.Slf4j;
import store.PeerStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;

@Slf4j
public abstract class AbstractPeerBroadcastService {
    private final PeerStore peerStore;
    private final PeerHttpClient peerHttpClient;
    private final String selfAddress;
    private final Executor broadcastExecutor;
    private final int fanOut;
    private final Map<String, String> peerFailures = new ConcurrentHashMap<>();

    protected AbstractPeerBroadcastService(
            PeerStore peerStore,
            PeerHttpClient peerHttpClient,
            String selfAddress
    ) {
        this(peerStore, peerHttpClient, selfAddress, Runnable::run, 0);
    }

    protected AbstractPeerBroadcastService(
            PeerStore peerStore,
            PeerHttpClient peerHttpClient,
            String selfAddress,
            Executor broadcastExecutor
    ) {
        this(peerStore, peerHttpClient, selfAddress, broadcastExecutor, 0);
    }

    protected AbstractPeerBroadcastService(
            PeerStore peerStore,
            PeerHttpClient peerHttpClient,
            String selfAddress,
            int fanOut
    ) {
        this(peerStore, peerHttpClient, selfAddress, Runnable::run, fanOut);
    }

    protected AbstractPeerBroadcastService(
            PeerStore peerStore,
            PeerHttpClient peerHttpClient,
            String selfAddress,
            Executor broadcastExecutor,
            int fanOut
    ) {
        this.peerStore = peerStore;
        this.peerHttpClient = peerHttpClient;
        this.selfAddress = selfAddress;
        this.broadcastExecutor = broadcastExecutor;
        this.fanOut = fanOut;
    }

    public void submitBroadcast(String data) {
        try {
            broadcastExecutor.execute(() -> broadcast(data));
        } catch (RejectedExecutionException e) {
            log.warn("{} broadcast dropped because executor queue is full", entityName());
            log.debug("{} broadcast submission failed", entityName(), e);
        }
    }

    public void broadcast(String data) {
        List<String> peers = selectPeers(peerStore.getAllPeers());

        for (String peer : peers) {
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

    private List<String> selectPeers(Set<String> knownPeers) {
        List<String> peers = new ArrayList<>();
        for (String peer : knownPeers) {
            if (!peer.equals(selfAddress)) {
                peers.add(peer);
            }
        }

        if (fanOut <= 0 || peers.size() <= fanOut) {
            return peers;
        }

        Collections.shuffle(peers);
        return new ArrayList<>(peers.subList(0, fanOut));
    }

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
