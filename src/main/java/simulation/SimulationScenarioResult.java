package simulation;

import java.util.List;

public record SimulationScenarioResult(
        String name,
        boolean success,
        int nodeCount,
        long durationMillis,
        List<String> details
) {
}
