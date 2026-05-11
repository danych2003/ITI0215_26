package service;

import model.request.CreateBlockRequest;
import store.PeerStore;

import java.util.concurrent.Executor;

public class BlockBroadcastService extends AbstractPeerBroadcastService {
    public BlockBroadcastService(PeerStore peerStore, PeerHttpClient peerHttpClient, String selfAddress) {
        super(peerStore, peerHttpClient, selfAddress);
    }

    public BlockBroadcastService(
            PeerStore peerStore,
            PeerHttpClient peerHttpClient,
            String selfAddress,
            int fanOut
    ) {
        super(peerStore, peerHttpClient, selfAddress, fanOut);
    }

    public BlockBroadcastService(
            PeerStore peerStore,
            PeerHttpClient peerHttpClient,
            String selfAddress,
            Executor broadcastExecutor
    ) {
        super(peerStore, peerHttpClient, selfAddress, broadcastExecutor);
    }

    public BlockBroadcastService(
            PeerStore peerStore,
            PeerHttpClient peerHttpClient,
            String selfAddress,
            Executor broadcastExecutor,
            int fanOut
    ) {
        super(peerStore, peerHttpClient, selfAddress, broadcastExecutor, fanOut);
    }

    @Override
    protected String path() {
        return "/block";
    }

    @Override
    protected Object requestBody(Object requestBody) {
        if (requestBody instanceof CreateBlockRequest createBlockRequest) {
            return createBlockRequest;
        }

        if (requestBody instanceof String data) {
            return new CreateBlockRequest(data, null, null, null, null, null, null, null, null);
        }

        throw new IllegalArgumentException("Unsupported block broadcast body: " + requestBody);
    }

    @Override
    protected String entityName() {
        return "Block";
    }
}
