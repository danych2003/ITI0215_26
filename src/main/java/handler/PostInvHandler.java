package handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import model.Transaction;
import model.request.CreateTransactionRequest;
import service.TransactionBroadcastService;
import service.TransactionValidationService;
import store.TransactionStore;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class PostInvHandler implements HttpHandler {
    private final TransactionStore transactionStore;
    private final TransactionBroadcastService transactionBroadcastService;
    private final TransactionValidationService transactionValidationService;
    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpRequestHelper.rejectNonPost(exchange)) {
            return;
        }

        try {
            CreateTransactionRequest request = objectMapper.readValue(exchange.getRequestBody(), CreateTransactionRequest.class);
            Transaction transaction = toStoredTransaction(request);
            if (transaction == null) {
                HttpResponseWriter.writeText(exchange, 400, "Transaction data is required");
                return;
            }
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

            transactionBroadcastService.submitBroadcast(toBroadcastRequest(request, transaction));
        } catch (InvalidTransactionException e) {
            HttpResponseWriter.writeText(exchange, e.statusCode, e.getMessage());
        }
    }

    private Transaction toStoredTransaction(CreateTransactionRequest request) {
        if (request.data() != null && !request.data().isBlank()) {
            return Transaction.fromData(request.data());
        }

        if (request.transaction() != null) {
            TransactionValidationService.ValidationResult validationResult = transactionValidationService.validate(request);
            if (!validationResult.accepted()) {
                throw new InvalidTransactionException(validationResult.statusCode(), validationResult.message());
            }
            return validationResult.storedTransaction();
        }
        return null;
    }

    private CreateTransactionRequest toBroadcastRequest(CreateTransactionRequest request, Transaction transaction) {
        if (request.transaction() != null) {
            return new CreateTransactionRequest(null, request.signature(), request.transaction());
        }

        return new CreateTransactionRequest(transaction.getData(), null, null);
    }

    private static final class InvalidTransactionException extends RuntimeException {
        private final int statusCode;

        private InvalidTransactionException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }
    }
}
