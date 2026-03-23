package service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import store.PeerStore;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class PeerDiscoveryService {
    private static final int DISCOVERY_INTERVAL_SECONDS = 15;

    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Map<String, String> peerFailures = new ConcurrentHashMap<>();

    public void startDiscovery(PeerStore peerStore, String selfAddress) {
        scheduler.scheduleWithFixedDelay(
                () -> runDiscovery(peerStore, selfAddress),
                0,
                DISCOVERY_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }

    private void runDiscovery(PeerStore peerStore, String selfAddress) {
        Set<String> knownPeers = new HashSet<>(peerStore.getAllPeers());

        for (String peer : knownPeers) {
            if (peer.equals(selfAddress)) {
                continue;
            }

            try {
                Set<String> discoveredPeers = fetchPeers(peer);
                Set<String> newPeers = peerStore.addPeers(discoveredPeers);
                logPeerRecovered(peer);

                if (!newPeers.isEmpty()) {
                    log.info("Discovered new peers from {}: {}", peer, newPeers);
                } else {
                    log.debug("No new peers discovered from {}", peer);
                }
            } catch (Exception e) {
                logPeerFailure(peer, e);
                log.debug("Peer discovery failed for {}", peer, e);
            }
        }
    }

    private Set<String> fetchPeers(String peer) throws IOException {
        URI uri = URI.create("http://" + peer + "/addr");
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(2000);
        connection.setReadTimeout(2000);

        int responseCode = connection.getResponseCode();
        if (responseCode != 200) {
            throw new IOException("Unexpected response code: " + responseCode);
        }

        try (InputStream inputStream = connection.getInputStream()) {
            return Arrays.stream(objectMapper.readValue(inputStream, String[].class))
                    .filter(this::isValidPeer)
                    .collect(Collectors.toSet());
        } finally {
            connection.disconnect();
        }
    }

    private boolean isValidPeer(String peer) {
        try {
            URI uri = URI.create("http://" + peer);
            return uri.getHost() != null
                    && uri.getPort() >= 1
                    && uri.getPort() <= 65535
                    && uri.getPath().isEmpty()
                    && uri.getQuery() == null
                    && uri.getFragment() == null
                    && uri.getUserInfo() == null;
        } catch (Exception e) {
            return false;
        }
    }

    private void logPeerFailure(String peer, Exception exception) {
        String failureReason = exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
        String previousFailureReason = peerFailures.put(peer, failureReason);

        if (!failureReason.equals(previousFailureReason)) {
            log.warn("Peer discovery failed for {} ({})", peer, failureReason);
            return;
        }

        log.debug("Peer {} is still unavailable ({})", peer, failureReason);
    }

    private void logPeerRecovered(String peer) {
        if (peerFailures.remove(peer) != null) {
            log.info("Peer {} is reachable again", peer);
        }
    }
}
