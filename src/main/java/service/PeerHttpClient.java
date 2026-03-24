package service;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;

@RequiredArgsConstructor
public class PeerHttpClient {
    private final ObjectMapper objectMapper;

    public void postJson(String peer, String path, Object body) throws IOException {
        URI uri = URI.create("http://" + peer + path);
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(2000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");

            byte[] requestBody = objectMapper.writeValueAsBytes(body);
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(requestBody);
            }

            int responseCode = connection.getResponseCode();
            if (responseCode != 200 && responseCode != 409) {
                throw new IOException("Unexpected response code: " + responseCode);
            }
        } finally {
            connection.disconnect();
        }
    }

    public JsonNode getJson(String peer, String path) throws IOException {
        return getJson(peer, path, false);
    }

    public JsonNode getJson(String peer, String path, boolean allowNotFound) throws IOException {
        URI uri = URI.create("http://" + peer + path);
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();

        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(2000);
            connection.setReadTimeout(2000);

            int responseCode = connection.getResponseCode();
            if (allowNotFound && responseCode == 404) {
                return null;
            }

            if (responseCode != 200) {
                throw new IOException("Unexpected response code: " + responseCode);
            }

            try (InputStream inputStream = connection.getInputStream()) {
                return objectMapper.readTree(inputStream);
            }
        } finally {
            connection.disconnect();
        }
    }
}
