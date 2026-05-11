package node;

import com.sun.net.httpserver.HttpServer;
import config.NodeConfig;
import handler.GetAddrHandler;
import handler.GetBlocksAfterHandler;
import handler.GetBlocksHandler;
import handler.GetDataHandler;
import handler.GetTransactionDataHandler;
import handler.GetTransactionsHandler;
import handler.PostBlockHandler;
import handler.PostInvHandler;
import handler.StatusHandler;
import service.BlockBroadcastService;
import service.LedgerStateService;
import service.TransactionValidationService;
import service.TransactionBroadcastService;
import store.PeerStore;
import store.TransactionStore;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class NodeHttpServerFactory {
    private static final int MIN_SERVER_THREADS = 8;
    private static final int MAX_SERVER_THREADS = 32;

    public HttpServer create(
            NodeConfig config,
            String selfAddress,
            PeerStore peerStore,
            LedgerStateService ledgerStateService,
            TransactionStore transactionStore,
            BlockBroadcastService blockBroadcastService,
            TransactionBroadcastService transactionBroadcastService,
            TransactionValidationService transactionValidationService,
            int miningDifficulty,
            BigDecimal miningReward,
            ObjectMapper objectMapper
    ) throws IOException {
        HttpServer server = HttpServer.create(createListenAddress(config), 0);
        server.createContext("/status", new StatusHandler(
                selfAddress,
                peerStore,
                ledgerStateService,
                transactionStore,
                objectMapper
        ));
        server.createContext("/addr", new GetAddrHandler(peerStore, objectMapper));
        server.createContext("/getblocks", new GetBlocksHandler(ledgerStateService, objectMapper));
        server.createContext("/getblocks/", new GetBlocksAfterHandler(ledgerStateService, objectMapper));
        server.createContext("/getdata/", new GetDataHandler(ledgerStateService, objectMapper));
        server.createContext("/transactions", new GetTransactionsHandler(transactionStore, objectMapper));
        server.createContext("/transactions/", new GetTransactionDataHandler(transactionStore, objectMapper));
        server.createContext("/block", new PostBlockHandler(
                ledgerStateService,
                blockBroadcastService,
                objectMapper,
                miningDifficulty,
                miningReward
        ));
        server.createContext("/inv", new PostInvHandler(
                transactionStore,
                transactionBroadcastService,
                transactionValidationService,
                objectMapper
        ));
        server.setExecutor(createServerExecutor());
        return server;
    }

    private InetSocketAddress createListenAddress(NodeConfig config) {
        if ("localhost".equalsIgnoreCase(config.host())) {
            return new InetSocketAddress(config.port());
        }

        return new InetSocketAddress(config.host(), config.port());
    }

    private ExecutorService createServerExecutor() {
        int threadCount = Math.clamp(Runtime.getRuntime().availableProcessors() * 2L,
                MIN_SERVER_THREADS, MAX_SERVER_THREADS);
        return Executors.newFixedThreadPool(threadCount);
    }
}
