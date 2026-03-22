import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

@RequiredArgsConstructor
public class PeerConfigLoader {
    private final ObjectMapper objectMapper;
    private final String resourceName;

    public List<String> loadPeers() throws IOException {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourceName)) {
            if (inputStream == null) {
                throw new IOException("Resource not found: " + resourceName);
            }

            return Arrays.asList(objectMapper.readValue(inputStream, String[].class));
        }
    }
}
