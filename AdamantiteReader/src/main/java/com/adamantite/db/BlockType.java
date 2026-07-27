package com.adamantite.db;

import static com.adamantite.schema.adamantite.BlockType.FoldersBlock;
import static com.adamantite.schema.adamantite.BlockType.SymbolSetsBlock;
import static com.adamantite.schema.adamantite.BlockType.PhraseTemplatesBlock;
import static com.adamantite.schema.adamantite.BlockType.PhraseBlock;
import static com.adamantite.schema.adamantite.BlockType.KeyBlock;

public enum BlockType {
    FOLDERS_BLOCK(FoldersBlock),
    SYMBOL_SETS_BLOCK(SymbolSetsBlock),
    PHRASE_TEMPLATES_BLOCK(PhraseTemplatesBlock),
    PHRASE_BLOCK(PhraseBlock),
    KEY_BLOCK(KeyBlock);

    public final byte code;

    BlockType(byte code) {
        this.code = code;
    }

    public static BlockType fromCode(byte code) {
        switch (code) {
            case FoldersBlock: return BlockType.FOLDERS_BLOCK;
            case SymbolSetsBlock: return BlockType.SYMBOL_SETS_BLOCK;
            case PhraseTemplatesBlock: return BlockType.PHRASE_TEMPLATES_BLOCK;
            case PhraseBlock: return BlockType.PHRASE_BLOCK;
            case KeyBlock: return BlockType.KEY_BLOCK;
            default: throw new RuntimeException("Unknown BlockType code " + code);
        }
    }
}
