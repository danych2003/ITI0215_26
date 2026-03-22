package handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import model.Transaction;
import model.request.CreateTransactionRequest;
import service.TransactionBroadcastService;
import store.TransactionStore;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RequiredArgsConstructor
public class PostInvHandler implements HttpHandler {
    private final TransactionStore transactionStore;
    private final TransactionBroadcastService transactionBroadcastService;
    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            HttpResponseWriter.writeText(exchange, 405, "Method Not Allowed");
            return;
        }

        CreateTransactionRequest request = objectMapper.readValue(exchange.getRequestBody(), CreateTransactionRequest.class);
        if (request.data() == null || request.data().isBlank()) {
            HttpResponseWriter.writeText(exchange, 400, "Transaction data is required");
            return;
        }

        Transaction transaction = Transaction.fromData(request.data());
        boolean transactionAdded = transactionStore.addTransaction(transaction);

        if (!transactionAdded) {
            HttpResponseWriter.writeText(exchange, 409, "Transaction already exists");
            return;
        }

        log.info("Accepted transaction {}", transaction.getHash());
        Map<String, Object> response = Map.of(
                "accepted", true,
                "hash", transaction.getHash()
        );
        HttpResponseWriter.writeJson(exchange, 200, objectMapper.writeValueAsBytes(response));

        CompletableFuture.runAsync(() -> transactionBroadcastService.broadcast(transaction.getData()));
    }
}
