package store;

import lombok.extern.slf4j.Slf4j;
import model.Block;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
public class BlockStore {
    private final LinkedHashMap<String, Block> blocksByHash = new LinkedHashMap<>();
    private final Path storageFile;
    private final ObjectMapper objectMapper;

    public BlockStore() {
        this.storageFile = null;
        this.objectMapper = null;
    }

    public BlockStore(Path storageFile, ObjectMapper objectMapper) throws IOException {
        this.storageFile = storageFile;
        this.objectMapper = objectMapper;
        loadFromDisk();
    }

    public synchronized boolean addBlock(Block block) throws IOException {
        if (blocksByHash.containsKey(block.getHash())) {
            return false;
        }

        blocksByHash.put(block.getHash(), block);
        saveToDisk();
        return true;
    }

    public synchronized Block getBlock(String hash) {
        return blocksByHash.get(hash);
    }

    public synchronized List<String> getAllHashes() {
        return new ArrayList<>(blocksByHash.keySet());
    }

    public synchronized List<Block> getAllBlocks() {
        return new ArrayList<>(blocksByHash.values());
    }

    public synchronized List<String> getHashesAfter(String hash) {
        List<String> hashes = getAllHashes();
        int index = hashes.indexOf(hash);

        if (index < 0) {
            return List.of();
        }

        return new ArrayList<>(hashes.subList(index + 1, hashes.size()));
    }

    public synchronized int size() {
        return blocksByHash.size();
    }

    private void loadFromDisk() throws IOException {
        if (storageFile == null) {
            return;
        }

        if (!Files.exists(storageFile)) {
            return;
        }

        if (Files.size(storageFile) == 0) {
            return;
        }

        StoredBlock[] storedBlocks = objectMapper.readValue(storageFile.toFile(), StoredBlock[].class);
        for (StoredBlock storedBlock : storedBlocks) {
            Block block = Block.fromHashAndData(storedBlock.hash(), storedBlock.data());
            if (blocksByHash.putIfAbsent(block.getHash(), block) != null) {
                throw new IOException("Duplicate block hash in storage: " + block.getHash());
            }
        }

        log.info("Loaded {} blocks from {}", blocksByHash.size(), storageFile.toAbsolutePath());
    }

    private void saveToDisk() throws IOException {
        if (storageFile == null) {
            return;
        }

        Path parentDirectory = storageFile.getParent();
        if (parentDirectory != null) {
            Files.createDirectories(parentDirectory);
        }

        objectMapper.writeValue(
                storageFile.toFile(),
                blocksByHash.values().stream()
                        .map(block -> new StoredBlock(block.getHash(), block.getData()))
                        .toList()
        );
    }

    private record StoredBlock(String hash, String data) {
    }
}
