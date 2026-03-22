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
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@RequiredArgsConstructor
public class PeerDiscoveryService {
    private static final int DISCOVERY_INTERVAL_SECONDS = 15;

    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public void startDiscovery(PeerStore peerStore, String selfAddress) {
        runDiscovery(peerStore, selfAddress);
        scheduler.scheduleWithFixedDelay(
                () -> runDiscovery(peerStore, selfAddress),
                DISCOVERY_INTERVAL_SECONDS,
                DISCOVERY_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }

    private void runDiscovery(PeerStore peerStore, String selfAddress) {
        Set<String> knownPeers = peerStore.getAllPeers();

        for (String peer : knownPeers) {
            if (peer.equals(selfAddress)) {
                continue;
            }

            try {
                Set<String> discoveredPeers = fetchPeers(peer);
                Set<String> peersBeforeUpdate = peerStore.getAllPeers();
                peerStore.addPeers(discoveredPeers);
                Set<String> newPeers = peerStore.getAllPeers();
                newPeers.removeAll(peersBeforeUpdate);

                if (!newPeers.isEmpty()) {
                    log.info("Discovered new peers from {}: {}", peer, newPeers);
                } else {
                    log.debug("No new peers discovered from {}", peer);
                }
            } catch (IOException e) {
                log.warn("Peer unavailable: {} ({})", peer, e.getMessage());
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
            return new HashSet<>(Arrays.asList(objectMapper.readValue(inputStream, String[].class)));
        } finally {
            connection.disconnect();
        }
    }
}
