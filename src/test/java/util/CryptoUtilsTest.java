package util;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CryptoUtilsTest {
    @Test
    void signsAndVerifiesPayload() {
        KeyPair keyPair = CryptoUtils.generateKeyPair();
        String payload = "{\"hello\":\"world\"}";
        String signature = CryptoUtils.sign(payload, keyPair.getPrivate());

        assertTrue(CryptoUtils.verify(payload, signature, CryptoUtils.encodePublicKey(keyPair.getPublic())));
        assertFalse(CryptoUtils.verify(payload + "!", signature, CryptoUtils.encodePublicKey(keyPair.getPublic())));
    }
}
