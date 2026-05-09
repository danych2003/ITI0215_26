package simulation;

import lombok.extern.slf4j.Slf4j;
import model.NodeStatus;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@Slf4j
public final class NetworkSimulationApp {
    private static final String HOST = "localhost";
    private static final int SIMULATION_BROADCAST_FAN_OUT = 10;
    private static final Duration SCALE_SEND_PAUSE = Duration.ofMillis(15);
    private static final Duration NODE_START_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration PROPAGATION_TIMEOUT = Duration.ofSeconds(25);
    private static final Duration RECOVERY_TIMEOUT = Duration.ofSeconds(20);
    private static final int[] SCALE_NODE_COUNTS = {5, 10, 20, 30};
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final AtomicInteger NEXT_PORT = new AtomicInteger(62000);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SimulationHttpClient httpClient = new SimulationHttpClient();
    private final Path simulationDirectory = Path.of("build", "simulation");
    private final Path logsDirectory = simulationDirectory.resolve("logs");
    private final Path reportsDirectory = simulationDirectory.resolve("reports");

    public static void main(String[] args) throws Exception {
        new NetworkSimulationApp().run();
    }

    private void run() throws Exception {
        Files.createDirectories(logsDirectory);
        Files.createDirectories(reportsDirectory);

        List<SimulationScenarioResult> results = List.of(
                runPropagationScenario(),
                runFailureRecoveryScenario(),
                runScaleSeriesScenario()
        );

        Path reportFile = writeReport(results);
        printSummary(results, reportFile);
    }

    private SimulationScenarioResult runPropagationScenario() throws Exception {
        String scenarioName = "Propagation Scenario";
        List<ManagedNode> nodes = createCluster(3, "propagation", false, SIMULATION_BROADCAST_FAN_OUT);
        List<String> details = new ArrayList<>();
        long startedAt = System.nanoTime();

        try {
            startCluster(nodes);
            waitForNodeStartup(nodes);

            httpClient.postTransaction(nodes.get(0).address(), "tx-propagation-1");
            httpClient.postBlock(nodes.get(0).address(), "block-propagation-1");

            awaitCondition(
                    "all nodes receive the propagated transaction and block",
                    () -> allNodesMatch(nodes, 1, 1),
                    PROPAGATION_TIMEOUT
            );

            details.add("Started nodes: " + joinAddresses(nodes));
            details.add("Sent 1 transaction and 1 block to " + nodes.get(0).address());
            appendStatuses(details, nodes);
            return scenarioResult(scenarioName, true, nodes.size(), startedAt, details);
        } catch (Exception e) {
            details.add("Failure: " + e.getMessage());
            appendStatusesQuietly(details, nodes);
            return scenarioResult(scenarioName, false, nodes.size(), startedAt, details);
        } finally {
            stopCluster(nodes);
        }
    }

    private SimulationScenarioResult runFailureRecoveryScenario() throws Exception {
        String scenarioName = "Failure Recovery Scenario";
        List<ManagedNode> nodes = createCluster(3, "failure-recovery", true, 0);
        List<String> details = new ArrayList<>();
        ManagedNode restartedNode = nodes.get(2);
        long startedAt = System.nanoTime();

        try {
            startCluster(nodes);
            waitForNodeStartup(nodes);

            restartedNode.stop();
            details.add("Stopped node: " + restartedNode.address());

            httpClient.postBlock(nodes.get(0).address(), "block-recovery-1");
            details.add("Sent block while node was offline via " + nodes.get(0).address());

            restartedNode.start();
            waitForNodeStartup(List.of(restartedNode));

            awaitCondition(
                    "restarted node catches up blocks from peers",
                    () -> httpClient.getStatus(restartedNode.address()).blocksCount() >= 1,
                    RECOVERY_TIMEOUT
            );

            details.add("Started nodes: " + joinAddresses(nodes));
            appendStatuses(details, nodes);
            return scenarioResult(scenarioName, true, nodes.size(), startedAt, details);
        } catch (Exception e) {
            details.add("Failure: " + e.getMessage());
            appendStatusesQuietly(details, nodes);
            return scenarioResult(scenarioName, false, nodes.size(), startedAt, details);
        } finally {
            stopCluster(nodes);
        }
    }

    private SimulationScenarioResult runScaleSeriesScenario() throws Exception {
        String scenarioName = "Scale Series Scenario";
        List<String> details = new ArrayList<>();
        long startedAt = System.nanoTime();
        boolean success = true;
        int maxSuccessfulNodeCount = 0;

        for (int nodeCount : SCALE_NODE_COUNTS) {
            List<ManagedNode> nodes = createCluster(nodeCount, "scale-" + nodeCount, true, SIMULATION_BROADCAST_FAN_OUT);
            try {
                runScaleIteration(nodes, nodeCount, details);
                maxSuccessfulNodeCount = nodeCount;
            } catch (Exception e) {
                success = false;
                details.add("Scale run " + nodeCount + " nodes failed: " + e.getMessage());
                appendStatusesQuietly(details, nodes);
                return scenarioResult(scenarioName, false, maxSuccessfulNodeCount, startedAt, details);
            } finally {
                stopCluster(nodes);
            }
        }

        return scenarioResult(scenarioName, success, maxSuccessfulNodeCount, startedAt, details);
    }

    private void runScaleIteration(List<ManagedNode> nodes, int nodeCount, List<String> details) throws Exception {
        int transactionsToSend = Math.max(5, nodeCount);
        int blocksToSend = Math.max(3, nodeCount / 2);

        startCluster(nodes);
        waitForNodeStartup(nodes);

        for (int i = 1; i <= transactionsToSend; i++) {
            httpClient.postTransaction(nodes.get(0).address(), "tx-scale-" + nodeCount + "-" + i);
            Thread.sleep(SCALE_SEND_PAUSE.toMillis());
        }

        for (int i = 1; i <= blocksToSend; i++) {
            httpClient.postBlock(nodes.get(0).address(), "block-scale-" + nodeCount + "-" + i);
            Thread.sleep(SCALE_SEND_PAUSE.toMillis());
        }

        awaitCondition(
                "all " + nodeCount + " nodes receive scale scenario messages",
                () -> allNodesMatch(nodes, blocksToSend, transactionsToSend),
                PROPAGATION_TIMEOUT
        );

        details.add(String.format(
                "Scale run %d nodes: PASS, transactions=%d, blocks=%d, seed=%s",
                nodeCount,
                transactionsToSend,
                blocksToSend,
                nodes.get(0).address()
        ));
        appendStatuses(details, nodes);
    }

    private List<ManagedNode> createCluster(
            int nodeCount,
            String scenarioKey,
            boolean backgroundServicesEnabled,
            int broadcastFanOut
    ) throws IOException {
        List<Integer> ports = new ArrayList<>();
        for (int i = 0; i < nodeCount; i++) {
            ports.add(findFreePort());
        }

        List<String> addresses = ports.stream()
                .sorted()
                .map(port -> HOST + ":" + port)
                .toList();

        Path peersConfigFile = simulationDirectory.resolve("peers-" + scenarioKey + ".json").toAbsolutePath();
        objectMapper.writeValue(peersConfigFile.toFile(), addresses);

        List<ManagedNode> nodes = new ArrayList<>();
        for (int port : ports) {
            resetNodeData(port);
            Path logFile = logsDirectory.resolve(scenarioKey + "-" + port + ".log").toAbsolutePath();
            nodes.add(new ManagedNode(
                    port,
                    HOST,
                    peersConfigFile.toString(),
                    backgroundServicesEnabled,
                    broadcastFanOut,
                    logFile
            ));
        }

        nodes.sort(Comparator.comparing(ManagedNode::address));
        return nodes;
    }

    private void startCluster(List<ManagedNode> nodes) throws IOException {
        for (ManagedNode node : nodes) {
            node.start();
        }
    }

    private void stopCluster(List<ManagedNode> nodes) {
        for (ManagedNode node : nodes) {
            node.stop();
        }
    }

    private void waitForNodeStartup(List<ManagedNode> nodes) throws Exception {
        for (ManagedNode node : nodes) {
            awaitCondition(
                    "node " + node.address() + " becomes reachable",
                    () -> {
                        httpClient.getStatus(node.address());
                        return true;
                    },
                    NODE_START_TIMEOUT
            );
        }
    }

    private boolean allNodesMatch(List<ManagedNode> nodes, int expectedBlocks, int expectedTransactions) throws IOException {
        for (ManagedNode node : nodes) {
            NodeStatus status = httpClient.getStatus(node.address());
            if (status.blocksCount() < expectedBlocks || status.transactionsCount() < expectedTransactions) {
                return false;
            }
        }

        return true;
    }

    private void appendStatuses(List<String> details, List<ManagedNode> nodes) throws IOException {
        for (ManagedNode node : nodes) {
            NodeStatus status = httpClient.getStatus(node.address());
            details.add(String.format(
                    "%s -> peers=%d, blocks=%d, transactions=%d",
                    node.address(),
                    status.peersCount(),
                    status.blocksCount(),
                    status.transactionsCount()
            ));
        }
    }

    private void appendStatusesQuietly(List<String> details, List<ManagedNode> nodes) {
        for (ManagedNode node : nodes) {
            try {
                NodeStatus status = httpClient.getStatus(node.address());
                details.add(String.format(
                        "%s -> peers=%d, blocks=%d, transactions=%d",
                        node.address(),
                        status.peersCount(),
                        status.blocksCount(),
                        status.transactionsCount()
                ));
            } catch (Exception ignored) {
                details.add(node.address() + " -> unreachable");
            }
        }
    }

    private void awaitCondition(String description, CheckedBooleanSupplier condition, Duration timeout) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        Exception lastFailure = null;

        while (System.nanoTime() < deadline) {
            try {
                if (condition.getAsBoolean()) {
                    return;
                }
            } catch (Exception e) {
                lastFailure = e;
            }

            Thread.sleep(250);
        }

        if (lastFailure != null) {
            throw new IllegalStateException(description + " failed: " + lastFailure.getMessage(), lastFailure);
        }

        throw new IllegalStateException(description + " timed out after " + timeout.toSeconds() + "s");
    }

    private Path writeReport(List<SimulationScenarioResult> results) throws IOException {
        String timestamp = FILE_TIMESTAMP.format(LocalDateTime.now());
        Path reportFile = reportsDirectory.resolve("network-simulation-report-" + timestamp + ".md").toAbsolutePath();

        StringBuilder report = new StringBuilder();
        report.append("# Network Simulation Report\n\n");

        for (SimulationScenarioResult result : results) {
            report.append("## ").append(result.name()).append("\n\n");
            report.append("- Result: ").append(result.success() ? "PASS" : "FAIL").append("\n");
            report.append("- Nodes: ").append(result.nodeCount()).append("\n");
            report.append("- DurationMs: ").append(result.durationMillis()).append("\n");
            for (String detail : result.details()) {
                report.append("- ").append(detail).append("\n");
            }
            report.append("\n");
        }

        Files.writeString(reportFile, report.toString(), StandardCharsets.UTF_8);
        return reportFile;
    }

    private void printSummary(List<SimulationScenarioResult> results, Path reportFile) {
        log.info("Simulation finished.");
        for (SimulationScenarioResult result : results) {
            log.info(
                    "{}: {}, nodes={}, durationMs={}",
                    result.name(),
                    result.success() ? "PASS" : "FAIL",
                    result.nodeCount(),
                    result.durationMillis()
            );
        }
        log.info("Report: {}", reportFile);
    }

    private SimulationScenarioResult scenarioResult(
            String name,
            boolean success,
            int nodeCount,
            long startedAt,
            List<String> details
    ) {
        return new SimulationScenarioResult(
                name,
                success,
                nodeCount,
                Duration.ofNanos(System.nanoTime() - startedAt).toMillis(),
                details
        );
    }

    private String joinAddresses(List<ManagedNode> nodes) {
        return nodes.stream().map(ManagedNode::address).sorted().reduce((left, right) -> left + ", " + right).orElse("");
    }

    private int findFreePort() throws IOException {
        while (true) {
            int candidate = NEXT_PORT.getAndIncrement();
            if (candidate > 65000) {
                throw new IOException("No simulation ports left in reserved range");
            }

            try (ServerSocket socket = new ServerSocket(candidate)) {
                return socket.getLocalPort();
            } catch (IOException ignored) {
                // Try the next reserved port.
            }
        }
    }

    private void resetNodeData(int port) throws IOException {
        Path nodeDataDirectory = Path.of("data", "node-" + port);
        if (!Files.exists(nodeDataDirectory)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(nodeDataDirectory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to delete " + path, e);
                }
            });
        } catch (RuntimeException e) {
            if (e.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw e;
        }
    }

    @FunctionalInterface
    private interface CheckedBooleanSupplier {
        boolean getAsBoolean() throws Exception;
    }
}
