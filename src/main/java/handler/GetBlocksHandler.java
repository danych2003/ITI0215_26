package handler;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import lombok.RequiredArgsConstructor;
import service.LedgerStateService;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

@RequiredArgsConstructor
public class GetBlocksHandler implements HttpHandler {
    private final LedgerStateService ledgerStateService;
    private final ObjectMapper objectMapper;

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpRequestHelper.rejectNonGet(exchange)) {
            return;
        }

        byte[] response = objectMapper.writeValueAsBytes(ledgerStateService.getCanonicalHashes());
        HttpResponseWriter.writeJson(exchange, 200, response);
    }
}
