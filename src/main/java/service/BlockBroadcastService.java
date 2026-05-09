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
    protected Object requestBody(String data) {
        return new CreateBlockRequest(data);
    }

    @Override
    protected String entityName() {
        return "Block";
    }
}
