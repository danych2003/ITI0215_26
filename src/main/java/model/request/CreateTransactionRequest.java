package model.request;

import model.TransactionPayload;

public record CreateTransactionRequest(
        String data,
        String signature,
        TransactionPayload transaction
) {
}
