package service;

import model.NodeKeyMaterial;
import tools.jackson.databind.ObjectMapper;
import util.CryptoUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;

public final class NodeKeyPairStore {
    private final Path storageFile;
    private final ObjectMapper objectMapper;

    public NodeKeyPairStore(Path storageFile, ObjectMapper objectMapper) {
        this.storageFile = storageFile;
        this.objectMapper = objectMapper;
    }

    public NodeKeyMaterial loadOrCreate() throws IOException {
        if (Files.exists(storageFile) && Files.size(storageFile) > 0) {
            StoredNodeKeys storedNodeKeys = objectMapper.readValue(storageFile.toFile(), StoredNodeKeys.class);
            return new NodeKeyMaterial(
                    storedNodeKeys.publicKey(),
                    storedNodeKeys.privateKey(),
                    CryptoUtils.decodePublicKey(storedNodeKeys.publicKey()),
                    CryptoUtils.decodePrivateKey(storedNodeKeys.privateKey())
            );
        }

        KeyPair keyPair = CryptoUtils.generateKeyPair();
        NodeKeyMaterial nodeKeyMaterial = new NodeKeyMaterial(
                CryptoUtils.encodePublicKey(keyPair.getPublic()),
                CryptoUtils.encodePrivateKey(keyPair.getPrivate()),
                keyPair.getPublic(),
                keyPair.getPrivate()
        );

        Path parent = storageFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        objectMapper.writeValue(
                storageFile.toFile(),
                new StoredNodeKeys(nodeKeyMaterial.publicKeyEncoded(), nodeKeyMaterial.privateKeyEncoded())
        );

        return nodeKeyMaterial;
    }

    private record StoredNodeKeys(String publicKey, String privateKey) {
    }
}
