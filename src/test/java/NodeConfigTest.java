import config.NodeConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NodeConfigTest {
    @Test
    void usesDefaultHostWhenOnlyPortIsProvided() {
        NodeConfig config = NodeConfig.fromArgs(new String[]{"8081"});

        assertEquals(8081, config.port());
        assertEquals("localhost", config.host());
        assertEquals("peers.json", config.peersConfigPath());
        assertEquals(true, config.backgroundServicesEnabled());
        assertEquals(0, config.broadcastFanOut());
        assertEquals("localhost:8081", config.selfAddress());
    }

    @Test
    void acceptsExplicitHost() {
        NodeConfig config = NodeConfig.fromArgs(new String[]{"8081", "192.168.0.15"});

        assertEquals(8081, config.port());
        assertEquals("192.168.0.15", config.host());
        assertEquals("192.168.0.15:8081", config.selfAddress());
    }

    @Test
    void acceptsExplicitPeersConfigPath() {
        NodeConfig config = NodeConfig.fromArgs(new String[]{"8081", "localhost", "C:/tmp/peers.json"});

        assertEquals("C:/tmp/peers.json", config.peersConfigPath());
    }

    @Test
    void acceptsBackgroundServicesFlag() {
        NodeConfig config = NodeConfig.fromArgs(new String[]{"8081", "localhost", "peers.json", "false"});

        assertEquals(false, config.backgroundServicesEnabled());
    }

    @Test
    void acceptsBroadcastFanOut() {
        NodeConfig config = NodeConfig.fromArgs(new String[]{"8081", "localhost", "peers.json", "false", "5"});

        assertEquals(5, config.broadcastFanOut());
    }

    @Test
    void rejectsMissingPort() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> NodeConfig.fromArgs(new String[]{})
        );

        assertEquals("Port argument is required", exception.getMessage());
    }

    @Test
    void rejectsNonNumericPort() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> NodeConfig.fromArgs(new String[]{"abc"})
        );

        assertEquals("Port must be a number", exception.getMessage());
    }

    @Test
    void rejectsBlankHost() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> NodeConfig.fromArgs(new String[]{"8081", "   "})
        );

        assertEquals("Host must not be blank", exception.getMessage());
    }

    @Test
    void rejectsBlankPeersConfigPath() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> NodeConfig.fromArgs(new String[]{"8081", "localhost", "   "})
        );

        assertEquals("Peers config path must not be blank", exception.getMessage());
    }

    @Test
    void rejectsInvalidBackgroundServicesFlag() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> NodeConfig.fromArgs(new String[]{"8081", "localhost", "peers.json", "maybe"})
        );

        assertEquals("Background services flag must be true or false", exception.getMessage());
    }

    @Test
    void rejectsInvalidBroadcastFanOut() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> NodeConfig.fromArgs(new String[]{"8081", "localhost", "peers.json", "false", "oops"})
        );

        assertEquals("Broadcast fan-out must be a number", exception.getMessage());
    }
}
