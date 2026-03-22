package model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import util.HashUtils;

@Getter
@RequiredArgsConstructor
public class Transaction {
    private final String hash;
    private final String data;

    public static Transaction fromData(String data) {
        return new Transaction(HashUtils.sha256Hex(data), data);
    }
}
