package model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import util.HashUtils;

@Getter
public class Block {
    private final String hash;
    private final String data;

    private Block(String hash, String data) {
        this.hash = hash;
        this.data = data;
    }

    public static Block fromData(String data) {
        return new Block(HashUtils.sha256Hex(data), data);
    }
}
