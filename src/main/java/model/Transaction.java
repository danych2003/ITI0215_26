package model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import util.HashUtils;

@Getter
public class Transaction {
    private final String hash;
    private final String data;

    private Transaction(String hash, String data) {
        this.hash = hash;
        this.data = data;
    }

    public static Transaction fromData(String data) {
        return new Transaction(HashUtils.sha256Hex(data), data);
    }
}
