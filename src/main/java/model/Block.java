package model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import util.HashUtils;

@Getter
@RequiredArgsConstructor
public class Block {
    private final String hash;
    private final String data;

    public static Block fromData(String data) {
        return new Block(HashUtils.sha256Hex(data), data);
    }

    public static Block fromHashAndData(String hash, String data) {
        String calculatedHash = HashUtils.sha256Hex(data);
        if (!calculatedHash.equals(hash)) {
            throw new IllegalArgumentException("Block hash does not match block data");
        }

        return new Block(hash, data);
    }
}
