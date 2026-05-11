package service;

import model.request.CreateTransactionRequest;
import store.PeerStore;

import java.util.concurrent.Executor;

public class TransactionBroadcastService extends AbstractPeerBroadcastService {
    public TransactionBroadcastService(PeerStore peerStore, PeerHttpClient peerHttpClient, String selfAddress) {
        super(peerStore, peerHttpClient, selfAddress);
    }

    public TransactionBroadcastService(
            PeerStore peerStore,
            PeerHttpClient peerHttpClient,
            String selfAddress,
            int fanOut
    ) {
        super(peerStore, peerHttpClient, selfAddress, fanOut);
    }

    public TransactionBroadcastService(
            PeerStore peerStore,
            PeerHttpClient peerHttpClient,
            String selfAddress,
            Executor broadcastExecutor
    ) {
        super(peerStore, peerHttpClient, selfAddress, broadcastExecutor);
    }

    public TransactionBroadcastService(
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
        return "/inv";
    }

    @Override
    protected Object requestBody(Object requestBody) {
        if (requestBody instanceof CreateTransactionRequest createTransactionRequest) {
            return createTransactionRequest;
        }

        if (requestBody instanceof String data) {
            return new CreateTransactionRequest(data, null, null);
        }

        throw new IllegalArgumentException("Unsupported transaction broadcast body: " + requestBody);
    }

    @Override
    protected String entityName() {
        return "Transaction";
    }
}
