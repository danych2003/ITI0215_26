package model;

import java.security.PrivateKey;
import java.security.PublicKey;

public record NodeKeyMaterial(
        String publicKeyEncoded,
        String privateKeyEncoded,
        PublicKey publicKey,
        PrivateKey privateKey
) {
}
