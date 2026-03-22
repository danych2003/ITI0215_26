package handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import lombok.RequiredArgsConstructor;
import store.TransactionStore;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@RequiredArgsConstructor
public class GetTransactionsHandler implements HttpHandler {
    private final TransactionStore transactionStore;
    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpRequestHelper.rejectNonGet(exchange)) {
            return;
        }

        byte[] response = objectMapper.writeValueAsBytes(transactionStore.getAllHashes());
        HttpResponseWriter.writeJson(exchange, 200, response);
    }
}
