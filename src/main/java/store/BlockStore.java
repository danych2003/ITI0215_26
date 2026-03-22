package store;

import model.Block;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

public class BlockStore {
    
    private final LinkedHashMap<String, Block> blocksByHash = new LinkedHashMap<>();

    public boolean addBlock(Block block) {
        if (blocksByHash.containsKey(block.getHash())) {
            return false;
        }

        blocksByHash.put(block.getHash(), block);
        return true;
    }

    public Block getBlock(String hash) {
        return blocksByHash.get(hash);
    }

    public List<String> getAllHashes() {
        return new ArrayList<>(blocksByHash.keySet());
    }

    public List<String> getHashesAfter(String hash) {
        List<String> hashes = getAllHashes();
        int index = hashes.indexOf(hash);

        if (index < 0) {
            return List.of();
        }

        return new ArrayList<>(hashes.subList(index + 1, hashes.size()));
    }

    public int size() {
        return blocksByHash.size();
    }
}
