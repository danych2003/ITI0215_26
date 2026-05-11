package service;

import model.SignedTransaction;
import model.Transaction;
import model.TransactionPayload;
import model.request.CreateTransactionRequest;
import store.TransactionStore;
import tools.jackson.databind.ObjectMapper;
import util.CryptoUtils;

import java.math.BigDecimal;

public final class TransactionValidationService {
    private final TransactionStore transactionStore;
    private final LedgerStateService ledgerStateService;
    private final ObjectMapper objectMapper;

    public TransactionValidationService(
            TransactionStore transactionStore,
            LedgerStateService ledgerStateService,
            ObjectMapper objectMapper
    ) {
        this.transactionStore = transactionStore;
        this.ledgerStateService = ledgerStateService;
        this.objectMapper = objectMapper;
    }

    public ValidationResult validate(CreateTransactionRequest request) {
        TransactionPayload payload = request.transaction();
        if (payload == null) {
            return ValidationResult.rejected(400, "Transaction data is required");
        }

        if (payload.from() == null || payload.from().isBlank()
                || payload.to() == null || payload.to().isBlank()
                || payload.amount() == null
                || payload.timestamp() == null || payload.timestamp().isBlank()) {
            return ValidationResult.rejected(400, "Structured transaction fields are required");
        }

        if (payload.amount().compareTo(BigDecimal.ZERO) <= 0) {
            return ValidationResult.rejected(400, "Transaction amount must be positive");
        }

        if ("0".equals(payload.from())) {
            return ValidationResult.rejected(400, "Reward transaction is only allowed inside a block");
        }

        SignedTransaction signedTransaction = SignedTransaction.from(request.signature(), payload);
        if (!CryptoUtils.verify(payload.canonicalJson(), signedTransaction.signature(), payload.from())) {
            return ValidationResult.rejected(400, "Transaction signature is invalid");
        }

        if (transactionStore.getTransaction(signedTransaction.hash()) != null) {
            return ValidationResult.rejected(409, "Transaction already exists");
        }

        BigDecimal availableBalance = ledgerStateService.balanceForAccount(payload.from());
        BigDecimal reservedBalance = reservedOutgoingBalance(payload.from());
        if (availableBalance.subtract(reservedBalance).compareTo(payload.amount()) < 0) {
            return ValidationResult.rejected(400, "Insufficient balance");
        }

        return ValidationResult.accepted(
                signedTransaction,
                Transaction.fromHashAndData(signedTransaction.hash(), signedTransaction.canonicalJson())
        );
    }

    private BigDecimal reservedOutgoingBalance(String account) {
        BigDecimal reserved = BigDecimal.ZERO;
        for (Transaction transaction : transactionStore.getAllTransactions()) {
            SignedTransaction signedTransaction = parseSignedTransaction(transaction);
            if (signedTransaction == null) {
                continue;
            }

            TransactionPayload payload = signedTransaction.transaction();
            if (payload != null && account.equals(payload.from()) && payload.amount() != null) {
                reserved = reserved.add(payload.amount());
            }
        }
        return reserved;
    }

    private SignedTransaction parseSignedTransaction(Transaction transaction) {
        try {
            return objectMapper.readValue(transaction.getData(), SignedTransaction.class);
        } catch (Exception e) {
            return null;
        }
    }

    public record ValidationResult(
            boolean accepted,
            int statusCode,
            String message,
            SignedTransaction signedTransaction,
            Transaction storedTransaction
    ) {
        public static ValidationResult accepted(SignedTransaction signedTransaction, Transaction storedTransaction) {
            return new ValidationResult(true, 200, null, signedTransaction, storedTransaction);
        }

        public static ValidationResult rejected(int statusCode, String message) {
            return new ValidationResult(false, statusCode, message, null, null);
        }
    }
}
