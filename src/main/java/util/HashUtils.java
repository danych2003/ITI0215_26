package util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class HashUtils {
    public static String sha256Hex(String data) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = messageDigest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hashBuilder = new StringBuilder();

            for (byte hashByte : hashBytes) {
                hashBuilder.append(String.format("%02x", hashByte));
            }

            return hashBuilder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }
    private HashUtils() {}
}
