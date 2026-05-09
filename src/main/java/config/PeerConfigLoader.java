package config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class PeerConfigLoader {
    private final ObjectMapper objectMapper;
    private final String resourceName;

    public List<String> loadPeers() throws IOException {
        try (InputStream inputStream = openInputStream()) {
            if (inputStream == null) {
                throw new IOException("Resource not found: " + resourceName);
            }

            return Arrays.asList(objectMapper.readValue(inputStream, String[].class));
        }
    }

    private InputStream openInputStream() throws IOException {
        try {
            Path path = Path.of(resourceName);
            if (Files.exists(path)) {
                log.debug("Using filesystem peer config at {}", path.toAbsolutePath());
                return new FileInputStream(path.toFile());
            }
        } catch (InvalidPathException ignored) {
            // Not a filesystem path; fall back to classpath resource lookup.
        }

        log.debug("Falling back to classpath resource '{}'", resourceName);
        return getClass().getClassLoader().getResourceAsStream(resourceName);
    }
}
