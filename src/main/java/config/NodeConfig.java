package config;

import java.math.BigDecimal;

public record NodeConfig(
        int port,
        String host,
        String peersConfigPath,
        boolean backgroundServicesEnabled,
        int broadcastFanOut,
        int miningDifficulty,
        long miningIntervalMillis,
        BigDecimal miningReward
) {
    private static final String DEFAULT_HOST = "localhost";
    private static final String DEFAULT_PEERS_CONFIG_PATH = "peers.json";
    private static final boolean DEFAULT_BACKGROUND_SERVICES_ENABLED = true;
    private static final int DEFAULT_BROADCAST_FAN_OUT = 0;
    private static final int DEFAULT_MINING_DIFFICULTY = 0;
    private static final long DEFAULT_MINING_INTERVAL_MILLIS = 2_000L;
    private static final BigDecimal DEFAULT_MINING_REWARD = BigDecimal.ONE;

    public static NodeConfig fromArgs(String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Port argument is required");
        }

        int port = parsePort(args[0]);
        String host = args.length >= 2 ? requireHost(args[1]) : DEFAULT_HOST;
        String peersConfigPath = args.length >= 3 ? requirePeersConfigPath(args[2]) : DEFAULT_PEERS_CONFIG_PATH;
        boolean backgroundServicesEnabled = args.length >= 4
                ? parseBackgroundServicesEnabled(args[3])
                : DEFAULT_BACKGROUND_SERVICES_ENABLED;
        int broadcastFanOut = args.length >= 5
                ? parseBroadcastFanOut(args[4])
                : DEFAULT_BROADCAST_FAN_OUT;
        int miningDifficulty = args.length >= 6
                ? parseMiningDifficulty(args[5])
                : DEFAULT_MINING_DIFFICULTY;
        long miningIntervalMillis = args.length >= 7
                ? parseMiningIntervalMillis(args[6])
                : DEFAULT_MINING_INTERVAL_MILLIS;
        BigDecimal miningReward = args.length >= 8
                ? parseMiningReward(args[7])
                : DEFAULT_MINING_REWARD;

        return new NodeConfig(
                port,
                host,
                peersConfigPath,
                backgroundServicesEnabled,
                broadcastFanOut,
                miningDifficulty,
                miningIntervalMillis,
                miningReward
        );
    }

    public String selfAddress() {
        return host + ":" + port;
    }

    private static int parsePort(String rawPort) {
        try {
            return Integer.parseInt(rawPort);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Port must be a number", e);
        }
    }

    private static String requireHost(String rawHost) {
        if (rawHost == null || rawHost.isBlank()) {
            throw new IllegalArgumentException("Host must not be blank");
        }

        return rawHost;
    }

    private static String requirePeersConfigPath(String rawPeersConfigPath) {
        if (rawPeersConfigPath == null || rawPeersConfigPath.isBlank()) {
            throw new IllegalArgumentException("Peers config path must not be blank");
        }

        return rawPeersConfigPath;
    }

    private static boolean parseBackgroundServicesEnabled(String rawValue) {
        if ("true".equalsIgnoreCase(rawValue)) {
            return true;
        }

        if ("false".equalsIgnoreCase(rawValue)) {
            return false;
        }

        throw new IllegalArgumentException("Background services flag must be true or false");
    }

    private static int parseBroadcastFanOut(String rawValue) {
        try {
            return Integer.parseInt(rawValue);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Broadcast fan-out must be a number", e);
        }
    }

    private static int parseMiningDifficulty(String rawValue) {
        try {
            int difficulty = Integer.parseInt(rawValue);
            if (difficulty < 0) {
                throw new IllegalArgumentException("Mining difficulty must not be negative");
            }
            return difficulty;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Mining difficulty must be a number", e);
        }
    }

    private static long parseMiningIntervalMillis(String rawValue) {
        try {
            long intervalMillis = Long.parseLong(rawValue);
            if (intervalMillis <= 0) {
                throw new IllegalArgumentException("Mining interval must be positive");
            }
            return intervalMillis;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Mining interval must be a number", e);
        }
    }

    private static BigDecimal parseMiningReward(String rawValue) {
        try {
            BigDecimal reward = new BigDecimal(rawValue);
            if (reward.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException("Mining reward must be positive");
            }
            return reward;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Mining reward must be a decimal number", e);
        }
    }
}
