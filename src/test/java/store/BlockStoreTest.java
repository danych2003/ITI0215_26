package store;

import model.Block;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockStoreTest {
    @Test
    void addBlockRejectsDuplicateHashes() throws Exception {
        BlockStore blockStore = new BlockStore();
        Block block = Block.fromData("block-1");

        assertTrue(blockStore.addBlock(block));
        assertFalse(blockStore.addBlock(block));
        assertEquals(1, blockStore.size());
    }

    @Test
    void getBlockReturnsStoredBlock() throws Exception {
        BlockStore blockStore = new BlockStore();
        Block block = Block.fromData("block-1");
        blockStore.addBlock(block);

        assertSame(block, blockStore.getBlock(block.getHash()));
    }

    @Test
    void getAllHashesReturnsInsertionOrder() throws Exception {
        BlockStore blockStore = new BlockStore();
        Block first = Block.fromData("block-1");
        Block second = Block.fromData("block-2");
        Block third = Block.fromData("block-3");

        blockStore.addBlock(first);
        blockStore.addBlock(second);
        blockStore.addBlock(third);

        assertEquals(
                List.of(first.getHash(), second.getHash(), third.getHash()),
                blockStore.getAllHashes()
        );
    }

    @Test
    void getHashesAfterReturnsOnlyHashesThatFollowKnownHash() throws Exception {
        BlockStore blockStore = new BlockStore();
        Block first = Block.fromData("block-1");
        Block second = Block.fromData("block-2");
        Block third = Block.fromData("block-3");

        blockStore.addBlock(first);
        blockStore.addBlock(second);
        blockStore.addBlock(third);

        assertEquals(
                List.of(third.getHash()),
                blockStore.getHashesAfter(second.getHash())
        );
    }

    @Test
    void getHashesAfterReturnsEmptyListForUnknownHash() throws Exception {
        BlockStore blockStore = new BlockStore();
        blockStore.addBlock(Block.fromData("block-1"));

        assertEquals(List.of(), blockStore.getHashesAfter("missing"));
    }

    @Test
    void persistentStoreLoadsBlocksFromDisk(@TempDir Path tempDir) throws Exception {
        Path storageFile = tempDir.resolve("blocks.json");
        ObjectMapper objectMapper = new ObjectMapper();

        Block first = Block.fromData("block-1");
        Block second = Block.fromData("block-2");

        BlockStore firstStore = new BlockStore(storageFile, objectMapper);
        firstStore.addBlock(first);
        firstStore.addBlock(second);

        BlockStore secondStore = new BlockStore(storageFile, objectMapper);

        assertEquals(List.of(first.getHash(), second.getHash()), secondStore.getAllHashes());
        assertEquals(first.getData(), secondStore.getBlock(first.getHash()).getData());
        assertEquals(second.getData(), secondStore.getBlock(second.getHash()).getData());
    }
}
