package com.adamantite.db;

import com.google.flatbuffers.FlatBufferBuilder;
import com.adamantite.utils.AdamantiteUtils;
import org.immutables.value.Value;

import static com.adamantite.db.Block.DATA_BLOCK_SIZE;

@Value.Immutable
public interface KeyBlock extends StoreBlock {
    /** 32 bytes key - AES-256 */
    byte[] key();
    byte[] iv();
    String dbName();
    int blockCount();

    static com.adamantite.schema.adamantite.KeyBlock createKeyBlock(byte[] key_256, byte[] iv_128) {
        assert(key_256.length == 32);
        assert(iv_128.length == 16);

        FlatBufferBuilder flatBufferBuilder = new FlatBufferBuilder(DATA_BLOCK_SIZE);
        int baseBlockOffset = com.adamantite.schema.adamantite.StoreBlock.createStoreBlock(flatBufferBuilder,
                1, 1, AdamantiteUtils.generateEntropy());
        int keyOffset = flatBufferBuilder.createByteVector(key_256);
        int ivOffset = flatBufferBuilder.createByteVector(iv_128);

        com.adamantite.schema.adamantite.KeyBlock.startKeyBlock(flatBufferBuilder);
        com.adamantite.schema.adamantite.KeyBlock.addBlock(flatBufferBuilder, baseBlockOffset);
        com.adamantite.schema.adamantite.KeyBlock.addKey(flatBufferBuilder, keyOffset);
        com.adamantite.schema.adamantite.KeyBlock.addIv(flatBufferBuilder, ivOffset);
        int keyBlockOffset = com.adamantite.schema.adamantite.KeyBlock.endKeyBlock(flatBufferBuilder);

        flatBufferBuilder.finish(keyBlockOffset);
        return com.adamantite.schema.adamantite.KeyBlock.getRootAsKeyBlock(flatBufferBuilder.dataBuffer());
    }

    // --------------------------------------------------

    static KeyBlock createFirstKeyBlock(byte[] key_256, byte[] iv_128, int version) {
        return ImmutableKeyBlock.builder()
                .key(key_256)
                .iv(iv_128)
                .version(version)
                .build();
    }
}
