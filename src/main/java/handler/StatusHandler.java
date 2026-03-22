package handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import lombok.RequiredArgsConstructor;
import store.BlockStore;
import store.PeerStore;
import store.TransactionStore;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;

@RequiredArgsConstructor
public class StatusHandler implements HttpHandler {
    private final String selfAddress;
    private final PeerStore peerStore;
    private final BlockStore blockStore;
    private final TransactionStore transactionStore;
    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpRequestHelper.rejectNonGet(exchange)) {
            return;
        }

        Map<String, Object> response = Map.of(
                "selfAddress", selfAddress,
                "peersCount", peerStore.size(),
                "blocksCount", blockStore.size(),
                "transactionsCount", transactionStore.size()
        );
        HttpResponseWriter.writeJson(exchange, 200, objectMapper.writeValueAsBytes(response));
    }
}