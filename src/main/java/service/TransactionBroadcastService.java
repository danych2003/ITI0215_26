package service;

import model.request.CreateTransactionRequest;
import store.PeerStore;

public class TransactionBroadcastService extends AbstractPeerBroadcastService {
    public TransactionBroadcastService(PeerStore peerStore, PeerHttpClient peerHttpClient, String selfAddress) {
        super(peerStore, peerHttpClient, selfAddress);
    }

    @Override
    protected String path() {
        return "/inv";
    }

    @Override
    protected Object requestBody(String data) {
        return new CreateTransactionRequest(data);
    }

    @Override
    protected String entityName() {
        return "Transaction";
    }
}
