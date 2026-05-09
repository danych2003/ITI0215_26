package config;

public record NodeConfig(
        int port,
        String host,
        String peersConfigPath,
        boolean backgroundServicesEnabled,
        int broadcastFanOut
) {
    private static final String DEFAULT_HOST = "localhost";
    private static final String DEFAULT_PEERS_CONFIG_PATH = "peers.json";
    private static final boolean DEFAULT_BACKGROUND_SERVICES_ENABLED = true;
    private static final int DEFAULT_BROADCAST_FAN_OUT = 0;

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

        return new NodeConfig(port, host, peersConfigPath, backgroundServicesEnabled, broadcastFanOut);
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
}
