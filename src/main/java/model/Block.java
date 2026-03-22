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
}
