package service;

import model.request.CreateBlockRequest;
import store.PeerStore;

public class BlockBroadcastService extends AbstractPeerBroadcastService {
    public BlockBroadcastService(PeerStore peerStore, PeerHttpClient peerHttpClient, String selfAddress) {
        super(peerStore, peerHttpClient, selfAddress);
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
