package service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import model.request.CreateTransactionRequest;
import store.PeerStore;

import java.io.IOException;
import java.util.Set;

@Slf4j
@RequiredArgsConstructor
public class TransactionBroadcastService {
    private final PeerStore peerStore;
    private final PeerHttpClient peerHttpClient;
    private final String selfAddress;

    public void broadcast(String data) {
        Set<String> peers = peerStore.getAllPeers();

        for (String peer : peers) {
            if (peer.equals(selfAddress)) {
                continue;
            }

            try {
                peerHttpClient.postJson(peer, "/inv", new CreateTransactionRequest(data));
            } catch (IOException e) {
                log.warn("Transaction broadcast failed for {} ({})", peer, e.getMessage());
                log.debug("Transaction broadcast failed for {}", peer, e);
            }
        }
    }
}
