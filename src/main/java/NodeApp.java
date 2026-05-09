import config.NodeConfig;
import lombok.extern.slf4j.Slf4j;
import node.NodeRuntime;

import java.io.IOException;

@Slf4j
public class NodeApp {
    public static void main(String[] args) throws IOException {
        NodeConfig config = NodeConfig.fromArgs(args);
        NodeRuntime runtime = NodeRuntime.create(config);
        runtime.start();
        Runtime.getRuntime().addShutdownHook(new Thread(runtime::shutdown));
    }
}
