package com.adamantite.dbcodec;

import com.adamantite.db.BlockType;

public class BlockData {
    public final BlockType blockType;
    public final byte[] blockData;

    public BlockData(BlockType blockType, byte[] blockData) {
        this.blockType = blockType;
        this.blockData = blockData;
    }
}
