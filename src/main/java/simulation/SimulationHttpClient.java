package simulation;

import model.NodeStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class SimulationHttpClient {
    private final ObjectMapper objectMapper = new ObjectMapper();

    public NodeStatus getStatus(String address) throws IOException {
        JsonNode response = getJson(address, "/status");
        return new NodeStatus(
                response.get("selfAddress").asText(),
                response.get("peersCount").asInt(),
                response.get("blocksCount").asInt(),
                response.get("transactionsCount").asInt()
        );
    }

    public List<String> getBlockHashes(String address) throws IOException {
        return getTextArray(address, "/getblocks");
    }

    public List<String> getTransactionHashes(String address) throws IOException {
        return getTextArray(address, "/transactions");
    }

    public void postTransaction(String address, String data) throws IOException {
        postJson(address, "/inv", "{\"data\":\"" + escapeJson(data) + "\"}");
    }

    public void postBlock(String address, String data) throws IOException {
        postJson(address, "/block", "{\"data\":\"" + escapeJson(data) + "\"}");
    }

    public void postStructuredBlock(String address, String requestBody) throws IOException {
        postJson(address, "/block", requestBody);
    }

    private List<String> getTextArray(String address, String path) throws IOException {
        JsonNode response = getJson(address, path);
        List<String> values = new ArrayList<>();

        for (JsonNode node : response) {
            values.add(node.asText());
        }

        return values;
    }

    private JsonNode getJson(String address, String path) throws IOException {
        HttpURLConnection connection = openConnection(address, path, "GET");

        try {
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                throw new IOException("Unexpected response code from " + path + ": " + responseCode);
            }

            try (InputStream inputStream = connection.getInputStream()) {
                return objectMapper.readTree(inputStream);
            }
        } finally {
            connection.disconnect();
        }
    }

    private void postJson(String address, String path, String requestBody) throws IOException {
        HttpURLConnection connection = openConnection(address, path, "POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");

        try {
            byte[] body = requestBody.getBytes(StandardCharsets.UTF_8);
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(body);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != 200 && responseCode != 409) {
                throw new IOException("Unexpected response code from " + path + ": " + responseCode);
            }
        } finally {
            connection.disconnect();
        }
    }

    private HttpURLConnection openConnection(String address, String path, String method) throws IOException {
        URI uri = URI.create("http://" + address + path);
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(2000);
        connection.setReadTimeout(2000);
        return connection;
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }
}
