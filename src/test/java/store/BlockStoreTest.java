package store;

import model.Block;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockStoreTest {
    @Test
    void addBlockRejectsDuplicateHashes() {
        BlockStore blockStore = new BlockStore();
        Block block = Block.fromData("block-1");

        assertTrue(blockStore.addBlock(block));
        assertFalse(blockStore.addBlock(block));
        assertEquals(1, blockStore.size());
    }

    @Test
    void getBlockReturnsStoredBlock() {
        BlockStore blockStore = new BlockStore();
        Block block = Block.fromData("block-1");
        blockStore.addBlock(block);

        assertSame(block, blockStore.getBlock(block.getHash()));
    }

    @Test
    void getAllHashesReturnsInsertionOrder() {
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
    void getHashesAfterReturnsOnlyHashesThatFollowKnownHash() {
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
    void getHashesAfterReturnsEmptyListForUnknownHash() {
        BlockStore blockStore = new BlockStore();
        blockStore.addBlock(Block.fromData("block-1"));

        assertEquals(List.of(), blockStore.getHashesAfter("missing"));
    }
}
