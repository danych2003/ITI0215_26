package handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import lombok.RequiredArgsConstructor;
import model.Transaction;
import store.TransactionStore;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@RequiredArgsConstructor
public class GetTransactionDataHandler implements HttpHandler {
    private static final String PATH_PREFIX = "/transactions/";

    private final TransactionStore transactionStore;
    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpRequestHelper.rejectNonGet(exchange)) {
            return;
        }

        String hash = HttpRequestHelper.extractPathValue(exchange, PATH_PREFIX, "Transaction hash is required");
        if (hash == null) {
            return;
        }
        Transaction transaction = transactionStore.getTransaction(hash);
        if (transaction == null) {
            HttpResponseWriter.writeText(exchange, 404, "Transaction not found");
            return;
        }

        byte[] response = objectMapper.writeValueAsBytes(transaction);
        HttpResponseWriter.writeJson(exchange, 200, response);
    }
}
