package util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MerkleUtils {
    public static String merkleRoot(List<String> leaves) {
        if (leaves == null || leaves.isEmpty()) {
            return HashUtils.sha256Hex("");
        }

        List<String> currentLevel = new ArrayList<>(leaves);
        while (currentLevel.size() > 1) {
            List<String> nextLevel = new ArrayList<>((currentLevel.size() + 1) / 2);
            for (int i = 0; i < currentLevel.size(); i += 2) {
                String left = currentLevel.get(i);
                String right = i + 1 < currentLevel.size() ? currentLevel.get(i + 1) : left;
                nextLevel.add(HashUtils.sha256Hex(left + right));
            }
            currentLevel = nextLevel;
        }

        return currentLevel.get(0);
    }
}
