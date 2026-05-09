package simulation;

import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

@RequiredArgsConstructor
public final class ManagedNode {
    private final int port;
    private final String host;
    private final String peersConfigPath;
    private final boolean backgroundServicesEnabled;
    private final int broadcastFanOut;
    private final Path logFile;

    private Process process;

    public void start() throws IOException {
        if (isRunning()) {
            return;
        }

        ProcessBuilder processBuilder = new ProcessBuilder(
                javaExecutable(),
                "-cp",
                System.getProperty("java.class.path"),
                "NodeApp",
                String.valueOf(port),
                host,
                peersConfigPath,
                String.valueOf(backgroundServicesEnabled),
                String.valueOf(broadcastFanOut)
        );
        processBuilder.redirectErrorStream(true);
        processBuilder.redirectOutput(logFile.toFile());
        process = processBuilder.start();
    }

    public void stop() {
        if (!isRunning()) {
            return;
        }

        process.destroy();
        awaitProcessExit(3);

        if (process.isAlive()) {
            process.destroyForcibly();
            awaitProcessExit(3);
        }
    }

    public boolean isRunning() {
        return process != null && process.isAlive();
    }

    public String address() {
        return host + ":" + port;
    }

    private String javaExecutable() {
        Path javaBin = Path.of(System.getProperty("java.home"), "bin", isWindows() ? "java.exe" : "java");
        return javaBin.toString();
    }

    private void awaitProcessExit(int timeoutSeconds) {
        try {
            process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }
}
