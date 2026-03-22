package util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HashUtilsTest {
    @Test
    void sameInputProducesSameHash() {
        String firstHash = HashUtils.sha256Hex("same-data");
        String secondHash = HashUtils.sha256Hex("same-data");

        assertEquals(firstHash, secondHash);
    }

    @Test
    void differentInputProducesDifferentHash() {
        String firstHash = HashUtils.sha256Hex("data-1");
        String secondHash = HashUtils.sha256Hex("data-2");

        assertNotEquals(firstHash, secondHash);
    }

    @Test
    void hashLooksLikeSha256Hex() {
        String hash = HashUtils.sha256Hex("sample");

        assertEquals(64, hash.length());
        assertTrue(hash.matches("[0-9a-f]+"));
    }
}
