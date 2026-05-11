package handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import model.Block;
import model.request.CreateBlockRequest;
import service.BlockValidationService;
import service.BlockBroadcastService;
import service.LedgerStateService;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class PostBlockHandler implements HttpHandler {
    private final LedgerStateService ledgerStateService;
    private final BlockBroadcastService blockBroadcastService;
    private final ObjectMapper objectMapper;
    private final int miningDifficulty;
    private final BigDecimal miningReward;

    private Block toStoredBlock(CreateBlockRequest request) {
        if (request.data() != null) {
            if (request.data().isBlank()) {
                return null;
            }
            return Block.fromData(request.data());
        }

        BlockValidationService.ValidationResult validationResult =
                BlockValidationService.validate(
                        request,
                        ledgerStateService.getKnownBlocks(),
                        objectMapper,
                        miningDifficulty,
                        miningReward
                );
        if (!validationResult.accepted()) {
            return invalidBlock(validationResult.statusCode(), validationResult.message());
        }
        return validationResult.block();
    }

    private Block invalidBlock(int statusCode, String message) {
        throw new InvalidBlockException(statusCode, message);
    }

    private CreateBlockRequest toBroadcastRequest(CreateBlockRequest request, Block block) {
        if (request.timestamp() != null && !request.timestamp().isBlank()) {
            return request;
        }

        return new CreateBlockRequest(block.getData(), null, null, null, null, null, null, null, null);
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpRequestHelper.rejectNonPost(exchange)) {
            return;
        }

        try {
            CreateBlockRequest request = objectMapper.readValue(exchange.getRequestBody(), CreateBlockRequest.class);
            Block block = toStoredBlock(request);
            if (block == null) {
                HttpResponseWriter.writeText(exchange, 400, "Block data is required");
                return;
            }
            boolean blockAdded = ledgerStateService.addKnownBlock(block);

            if (!blockAdded) {
                HttpResponseWriter.writeText(exchange, 409, "Block already exists");
                return;
            }

            log.info("Accepted block {}", block.getHash());
            Map<String, Object> response = Map.of(
                    "accepted", true,
                    "hash", block.getHash()
            );
            HttpResponseWriter.writeJson(exchange, 200, objectMapper.writeValueAsBytes(response));

            blockBroadcastService.submitBroadcast(toBroadcastRequest(request, block));
        } catch (InvalidBlockException e) {
            HttpResponseWriter.writeText(exchange, e.statusCode, e.getMessage());
        }
    }

    private static final class InvalidBlockException extends IllegalArgumentException {
        private final int statusCode;

        private InvalidBlockException(int statusCode, String message) {
            super(message);
            this.statusCode = statusCode;
        }
    }
}
