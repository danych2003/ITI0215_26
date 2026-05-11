package simulation;

import lombok.extern.slf4j.Slf4j;
import model.LedgerBlock;
import model.NodeStatus;
import model.SignedTransaction;
import model.TransactionPayload;
import model.request.CreateBlockRequest;
import tools.jackson.databind.ObjectMapper;
import util.CryptoUtils;
import util.MerkleUtils;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

@Slf4j
public final class NetworkSimulationApp {
    private static final String HOST = "localhost";
    private static final int SIMULATION_BROADCAST_FAN_OUT = 10;
    private static final Duration SCALE_SEND_PAUSE = Duration.ofMillis(15);
    private static final Duration NODE_START_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration PROPAGATION_TIMEOUT = Duration.ofSeconds(25);
    private static final Duration RECOVERY_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration SCENARIO_COOLDOWN = Duration.ofSeconds(1);
    private static final int[] SCALE_NODE_COUNTS = {5, 10, 20, 30};
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final AtomicInteger NEXT_PORT = new AtomicInteger(initialPort());

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SimulationHttpClient httpClient = new SimulationHttpClient();
    private final Path simulationDirectory = Path.of("build", "simulation");
    private final Path logsDirectory = simulationDirectory.resolve("logs");
    private final Path reportsDirectory = simulationDirectory.resolve("reports");

    public static void main(String[] args) throws Exception {
        new NetworkSimulationApp().run(args);
    }

    private void run(String[] args) throws Exception {
        Files.createDirectories(logsDirectory);
        Files.createDirectories(reportsDirectory);

        List<SimulationScenarioResult> results = selectScenarios(args);

        Path reportFile = writeReport(results);
        printSummary(results, reportFile);
    }

    private List<SimulationScenarioResult> selectScenarios(String[] args) throws Exception {
        if (args == null || args.length == 0) {
            List<SimulationScenarioResult> results = new ArrayList<>();
            results.add(runDivergenceWithoutConsensusScenario());
            cooldownBetweenScenarios();
            results.add(runConvergenceWithConsensusScenario());
            cooldownBetweenScenarios();
            results.add(runConsensusFailureUnderPartitionScenario());
            cooldownBetweenScenarios();
            results.add(runPropagationScenario());
            cooldownBetweenScenarios();
            results.add(runFailureRecoveryScenario());
            cooldownBetweenScenarios();
            results.add(runScaleSeriesScenario());
            return results;
        }

        return switch (args[0]) {
            case "divergence" -> List.of(runDivergenceWithoutConsensusScenario());
            case "convergence" -> List.of(runConvergenceWithConsensusScenario());
            case "partition-failure" -> List.of(runConsensusFailureUnderPartitionScenario());
            case "propagation" -> List.of(runPropagationScenario());
            case "recovery" -> List.of(runFailureRecoveryScenario());
            case "scale" -> List.of(runScaleSeriesScenario());
            default -> throw new IllegalArgumentException("Unknown simulation scenario: " + args[0]);
        };
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

    private SimulationScenarioResult runConsensusFailureUnderPartitionScenario() throws Exception {
        String scenarioName = "Consensus Failure Under Permanent Partition Scenario";
        String scenarioKey = "consensus-failure-partition";
        List<String> details = new ArrayList<>();
        long startedAt = System.nanoTime();

        List<Integer> ports = reservePorts(4);
        List<Integer> leftPorts = List.of(ports.get(0), ports.get(1));
        List<Integer> rightPorts = List.of(ports.get(2), ports.get(3));

        List<ManagedNode> leftPartition = createSharedCluster(
                leftPorts,
                addressesForPorts(leftPorts),
                scenarioKey + "-left",
                true,
                true,
                SIMULATION_BROADCAST_FAN_OUT
        );
        List<ManagedNode> rightPartition = createSharedCluster(
                rightPorts,
                addressesForPorts(rightPorts),
                scenarioKey + "-right",
                true,
                true,
                SIMULATION_BROADCAST_FAN_OUT
        );

        List<ManagedNode> allNodes = new ArrayList<>();
        allNodes.addAll(leftPartition);
        allNodes.addAll(rightPartition);
        allNodes.sort(Comparator.comparing(ManagedNode::address));

        try {
            startCluster(allNodes);
            waitForNodeStartup(allNodes);

            KeyPair aliceKeys = CryptoUtils.generateKeyPair();
            KeyPair bobKeys = CryptoUtils.generateKeyPair();
            String alice = CryptoUtils.encodePublicKey(aliceKeys.getPublic());
            String bob = CryptoUtils.encodePublicKey(bobKeys.getPublic());
            String carol = CryptoUtils.encodePublicKey(CryptoUtils.generateKeyPair().getPublic());
            String dave = CryptoUtils.encodePublicKey(CryptoUtils.generateKeyPair().getPublic());

            LedgerBlock genesis = buildBlock(
                    1,
                    null,
                    "2026-05-09T19:00:00Z",
                    alice,
                    List.of(rewardTransaction(alice, "2026-05-09T19:00:00Z"))
            );
            LedgerBlock leftBranch = buildBlock(
                    2,
                    genesis.hash(),
                    "2026-05-09T19:01:00Z",
                    alice,
                    List.of(rewardTransaction(alice, "2026-05-09T19:01:00Z"))
            );
            LedgerBlock rightBranch = buildBlock(
                    2,
                    genesis.hash(),
                    "2026-05-09T19:01:30Z",
                    bob,
                    List.of(
                            rewardTransaction(bob, "2026-05-09T19:01:30Z"),
                            signedTransaction(bobKeys, bob, carol, new BigDecimal("0.50"), "2026-05-09T19:01:45Z")
                    )
            );

            httpClient.postStructuredBlock(leftPartition.get(0).address(), toBlockRequestJson(genesis));
            httpClient.postStructuredBlock(rightPartition.get(0).address(), toBlockRequestJson(genesis));

            awaitCondition(
                    "genesis propagates inside each partition",
                    () -> allNodesHaveBlockHashes(leftPartition, List.of(genesis.hash()))
                            && allNodesHaveBlockHashes(rightPartition, List.of(genesis.hash())),
                    PROPAGATION_TIMEOUT
            );

            httpClient.postStructuredBlock(leftPartition.get(0).address(), toBlockRequestJson(leftBranch));
            httpClient.postStructuredBlock(rightPartition.get(0).address(), toBlockRequestJson(rightBranch));

            awaitCondition(
                    "each partition converges internally to its local branch",
                    () -> allNodesHaveBlockHashes(leftPartition, List.of(genesis.hash(), leftBranch.hash()))
                            && allNodesHaveBlockHashes(rightPartition, List.of(genesis.hash(), rightBranch.hash())),
                    PROPAGATION_TIMEOUT
            );

            boolean globalAgreement = allNodesHaveSameBlockHashes(allNodes);

            details.add("Consensus services enabled, but peer graph is permanently partitioned into 2 isolated groups");
            details.add("Left partition expected branch: " + leftBranch.hash());
            details.add("Right partition expected branch: " + rightBranch.hash());
            details.add("Global canonical chain agreement: " + (globalAgreement ? "YES" : "NO"));
            appendHashes(details, allNodes);
            appendStatuses(details, allNodes);

            boolean success = !globalAgreement;
            if (!success) {
                details.add("Failure: partitions converged globally, which was not expected");
            }
            return scenarioResult(scenarioName, success, allNodes.size(), startedAt, details);
        } catch (Exception e) {
            details.add("Failure: " + e.getMessage());
            appendStatusesQuietly(details, allNodes);
            return scenarioResult(scenarioName, false, allNodes.size(), startedAt, details);
        } finally {
            stopCluster(allNodes);
        }
    }

    private SimulationScenarioResult runConvergenceWithConsensusScenario() throws Exception {
        String scenarioName = "Convergence With Consensus Scenario";
        String scenarioKey = "convergence-with-consensus";
        List<String> details = new ArrayList<>();
        long startedAt = System.nanoTime();

        List<Integer> ports = reservePorts(3);
        List<String> addresses = addressesForPorts(ports);
        List<ManagedNode> isolatedNodes = createIsolatedCluster(ports, scenarioKey + "-isolated", true);
        List<ManagedNode> reconnectedNodes = createSharedCluster(
                ports,
                addresses,
                scenarioKey + "-reconnected",
                false,
                true,
                SIMULATION_BROADCAST_FAN_OUT
        );

        try {
            startCluster(isolatedNodes);
            waitForNodeStartup(isolatedNodes);

            KeyPair aliceKeys = CryptoUtils.generateKeyPair();
            KeyPair bobKeys = CryptoUtils.generateKeyPair();
            String alice = CryptoUtils.encodePublicKey(aliceKeys.getPublic());
            String bob = CryptoUtils.encodePublicKey(bobKeys.getPublic());
            String carol = CryptoUtils.encodePublicKey(CryptoUtils.generateKeyPair().getPublic());
            String dave = CryptoUtils.encodePublicKey(CryptoUtils.generateKeyPair().getPublic());

            LedgerBlock genesis = buildBlock(
                    1,
                    null,
                    "2026-05-09T18:00:00Z",
                    alice,
                    List.of(rewardTransaction(alice, "2026-05-09T18:00:00Z"))
            );
            LedgerBlock lightBranch = buildBlock(
                    2,
                    genesis.hash(),
                    "2026-05-09T18:01:00Z",
                    carol,
                    List.of(rewardTransaction(carol, "2026-05-09T18:01:00Z"))
            );
            LedgerBlock heavyBranch = buildBlock(
                    2,
                    genesis.hash(),
                    "2026-05-09T18:02:00Z",
                    bob,
                    List.of(
                            rewardTransaction(bob, "2026-05-09T18:02:00Z"),
                            signedTransaction(
                                    bobKeys,
                                    bob,
                                    dave,
                                    BigDecimal.ONE,
                                    "2026-05-09T18:02:30Z"
                            )
                    )
            );

            for (ManagedNode node : isolatedNodes) {
                httpClient.postStructuredBlock(node.address(), toBlockRequestJson(genesis));
            }
            httpClient.postStructuredBlock(isolatedNodes.get(0).address(), toBlockRequestJson(lightBranch));
            httpClient.postStructuredBlock(isolatedNodes.get(1).address(), toBlockRequestJson(heavyBranch));

            awaitCondition(
                    "isolated nodes keep different canonical chains",
                    () -> !allNodesHaveSameBlockHashes(isolatedNodes),
                    PROPAGATION_TIMEOUT
            );

            details.add("Phase 1: isolated nodes accepted competing branches");
            appendHashes(details, isolatedNodes);

            stopCluster(isolatedNodes);
            waitForClusterShutdown(isolatedNodes);

            startCluster(reconnectedNodes);
            waitForNodeStartup(reconnectedNodes);

            awaitCondition(
                    "all nodes converge to the same canonical chain after reconnection",
                    () -> allNodesHaveSameBlockHashes(reconnectedNodes)
                            && httpClient.getBlockHashes(reconnectedNodes.get(0).address()).equals(
                            List.of(genesis.hash(), heavyBranch.hash())
                    ),
                    RECOVERY_TIMEOUT
            );

            details.add("Phase 2: nodes restarted with shared peer set and background sync enabled");
            details.add("Expected winning branch: " + heavyBranch.hash());
            appendHashes(details, reconnectedNodes);
            appendStatuses(details, reconnectedNodes);
            return scenarioResult(scenarioName, true, reconnectedNodes.size(), startedAt, details);
        } catch (Exception e) {
            details.add("Failure: " + e.getMessage());
            appendStatusesQuietly(details, isolatedNodes);
            appendStatusesQuietly(details, reconnectedNodes);
            return scenarioResult(scenarioName, false, 3, startedAt, details);
        } finally {
            stopCluster(isolatedNodes);
            stopCluster(reconnectedNodes);
        }
    }

    private SimulationScenarioResult runDivergenceWithoutConsensusScenario() throws Exception {
        String scenarioName = "Divergence Without Consensus Scenario";
        List<ManagedNode> nodes = createCluster(3, "divergence-no-consensus", false, 0);
        List<String> details = new ArrayList<>();
        long startedAt = System.nanoTime();

        try {
            startCluster(nodes);
            waitForNodeStartup(nodes);

            details.add("Background services disabled for all nodes");
            details.add("Each node receives a different local transaction and block");
            details.add("Broadcast is still enabled, so nodes learn the same data but may preserve different arrival order");

            for (int i = 0; i < nodes.size(); i++) {
                ManagedNode node = nodes.get(i);
                httpClient.postTransaction(node.address(), "tx-divergence-" + (i + 1));
                httpClient.postBlock(node.address(), "block-divergence-" + (i + 1));
            }

            awaitCondition(
                    "every node receives all divergence inputs",
                    () -> allNodesMatch(nodes, nodes.size(), nodes.size()),
                    PROPAGATION_TIMEOUT
            );

            boolean chainsAgree = allNodesHaveSameBlockHashes(nodes);
            boolean transactionsAgree = allNodesHaveSameTransactionHashes(nodes);

            details.add("Canonical chain agreement: " + (chainsAgree ? "YES" : "NO"));
            details.add("Transaction pool agreement: " + (transactionsAgree ? "YES" : "NO"));
            appendHashes(details, nodes);
            appendStatuses(details, nodes);

            boolean success = !chainsAgree && !transactionsAgree;
            if (!success) {
                details.add("Failure: nodes converged unexpectedly under the no-consensus scenario");
            }
            return scenarioResult(scenarioName, success, nodes.size(), startedAt, details);
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

    private List<ManagedNode> createIsolatedCluster(
            List<Integer> ports,
            String scenarioKey,
            boolean resetData
    ) throws IOException {
        List<ManagedNode> nodes = new ArrayList<>();
        for (int port : ports) {
            if (resetData) {
                resetNodeData(port);
            }
            Path peersConfigFile = simulationDirectory.resolve("peers-" + scenarioKey + "-" + port + ".json").toAbsolutePath();
            objectMapper.writeValue(peersConfigFile.toFile(), List.of(HOST + ":" + port));
            Path logFile = logsDirectory.resolve(scenarioKey + "-" + port + ".log").toAbsolutePath();
            nodes.add(new ManagedNode(port, HOST, peersConfigFile.toString(), false, 0, logFile));
        }
        nodes.sort(Comparator.comparing(ManagedNode::address));
        return nodes;
    }

    private List<ManagedNode> createSharedCluster(
            List<Integer> ports,
            List<String> addresses,
            String scenarioKey,
            boolean resetData,
            boolean backgroundServicesEnabled,
            int broadcastFanOut
    ) throws IOException {
        Path peersConfigFile = simulationDirectory.resolve("peers-" + scenarioKey + ".json").toAbsolutePath();
        objectMapper.writeValue(peersConfigFile.toFile(), addresses);

        List<ManagedNode> nodes = new ArrayList<>();
        for (int port : ports) {
            if (resetData) {
                resetNodeData(port);
            }
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

    private void waitForClusterShutdown(List<ManagedNode> nodes) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            boolean anyRunning = false;
            for (ManagedNode node : nodes) {
                if (node.isRunning()) {
                    anyRunning = true;
                    break;
                }
            }
            if (!anyRunning) {
                Thread.sleep(250);
                return;
            }
            Thread.sleep(100);
        }
        Thread.sleep(250);
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

    private void appendHashes(List<String> details, List<ManagedNode> nodes) throws IOException {
        for (ManagedNode node : nodes) {
            details.add(String.format(
                    "%s -> blockHashes=%s, transactionHashes=%s",
                    node.address(),
                    httpClient.getBlockHashes(node.address()),
                    httpClient.getTransactionHashes(node.address())
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

    private boolean allNodesHaveSameBlockHashes(List<ManagedNode> nodes) throws IOException {
        List<String> expected = null;
        for (ManagedNode node : nodes) {
            List<String> hashes = httpClient.getBlockHashes(node.address());
            if (expected == null) {
                expected = hashes;
                continue;
            }
            if (!expected.equals(hashes)) {
                return false;
            }
        }
        return true;
    }

    private boolean allNodesHaveSameTransactionHashes(List<ManagedNode> nodes) throws IOException {
        List<String> expected = null;
        for (ManagedNode node : nodes) {
            List<String> hashes = httpClient.getTransactionHashes(node.address());
            if (expected == null) {
                expected = hashes;
                continue;
            }
            if (!expected.equals(hashes)) {
                return false;
            }
        }
        return true;
    }

    private boolean allNodesHaveBlockHashes(List<ManagedNode> nodes, List<String> expectedHashes) throws IOException {
        for (ManagedNode node : nodes) {
            if (!expectedHashes.equals(httpClient.getBlockHashes(node.address()))) {
                return false;
            }
        }
        return true;
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

    private void cooldownBetweenScenarios() throws InterruptedException {
        Thread.sleep(SCENARIO_COOLDOWN.toMillis());
    }

    private static int initialPort() {
        return ThreadLocalRandom.current().nextInt(40000, 64000);
    }

    private List<Integer> reservePorts(int nodeCount) throws IOException {
        List<Integer> ports = new ArrayList<>(nodeCount);
        for (int i = 0; i < nodeCount; i++) {
            ports.add(findFreePort());
        }
        ports.sort(Integer::compareTo);
        return ports;
    }

    private List<String> addressesForPorts(List<Integer> ports) {
        return ports.stream().map(port -> HOST + ":" + port).toList();
    }

    private String toBlockRequestJson(LedgerBlock block) throws IOException {
        return objectMapper.writeValueAsString(new CreateBlockRequest(
                null,
                block.height(),
                block.previousHash(),
                block.timestamp(),
                block.nonce(),
                block.hash(),
                block.creator(),
                block.merkleRoot(),
                block.transactions()
        ));
    }

    private LedgerBlock buildBlock(
            int height,
            String previousHash,
            String timestamp,
            String creator,
            List<SignedTransaction> transactions
    ) {
        return LedgerBlock.from(
                height,
                previousHash,
                timestamp,
                "nonce-" + height + "-" + creator.hashCode(),
                null,
                creator,
                MerkleUtils.merkleRoot(transactions.stream()
                        .map(transaction -> transaction.hash() == null || transaction.hash().isBlank()
                                ? SignedTransaction.from(transaction.signature(), transaction.transaction()).hash()
                                : transaction.hash())
                        .toList()),
                transactions
        );
    }

    private SignedTransaction rewardTransaction(String recipient, String timestamp) {
        return SignedTransaction.from(
                null,
                new TransactionPayload("0", recipient, BigDecimal.ONE, timestamp)
        );
    }

    private SignedTransaction signedTransaction(
            KeyPair keyPair,
            String from,
            String to,
            BigDecimal amount,
            String timestamp
    ) {
        TransactionPayload payload = new TransactionPayload(from, to, amount, timestamp);
        return SignedTransaction.from(CryptoUtils.sign(payload.canonicalJson(), keyPair.getPrivate()), payload);
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
