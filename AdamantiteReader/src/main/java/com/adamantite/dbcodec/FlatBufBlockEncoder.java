package com.adamantite.dbcodec;

import com.google.flatbuffers.FlatBufferBuilder;
import com.adamantite.db.Block;
import com.adamantite.db.BlockType;
import com.adamantite.db.FoldersBlock;
import com.adamantite.db.Icon;
import com.adamantite.db.KeyBlock;
import com.adamantite.db.PhraseBlock;
import com.adamantite.db.PhraseTemplatesBlock;
import com.adamantite.db.StoreBlock;
import com.adamantite.db.SymbolSetsBlock;
import com.adamantite.utils.UnsignedConverter;

import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

public class FlatBufBlockEncoder {
    public static int[] toIdArray(List<Integer> idList) {
        int[] ids = new int[idList.size()];
        for (int j = 0; j < idList.size(); j++) {
            ids[j] = idList.get(j);
        }
        return ids;
    }

    public static byte[] toFlatBufBlock(Block block) {
        BlockType blockType = block.blockType();
        switch (blockType) {
            case KEY_BLOCK:
                return toFlatBufKeyBlock(checkNotNull(block.keyBlock()));
            case SYMBOL_SETS_BLOCK:
                return toFlatBufSymbolSetsBlock(checkNotNull(block.symbolSetsBlock()));
            case FOLDERS_BLOCK:
                return toFlatBufFoldersBlock(checkNotNull(block.foldersBlock()));
            case PHRASE_TEMPLATES_BLOCK:
                return toFlatBufPhraseTemplatesBlock(checkNotNull(block.phraseTemplatesBlock()));
            case PHRASE_BLOCK:
                return toFlatBufPhraseBlock(checkNotNull(block.phraseBlock()));
            default:
                throw new RuntimeException("Unsupported Block type: " + blockType);
        }
    }

    public static byte[] toFlatBufKeyBlock(KeyBlock keyBlock) {
        StoreBlock storeBlock = keyBlock;
        FlatBufferBuilder builder = new FlatBufferBuilder(12000);

        int dbNameOffset = builder.createString(keyBlock.dbName());
        int keyOffset = builder.createByteVector(keyBlock.key());
        int ivOffset = builder.createByteVector(keyBlock.iv());

        com.adamantite.schema.adamantite.KeyBlock.startKeyBlock(builder);

        int storeBlockOffset = com.adamantite.schema.adamantite.StoreBlock.createStoreBlock(builder,
                storeBlock.blockId(), storeBlock.version(), storeBlock.entropy());

        com.adamantite.schema.adamantite.KeyBlock.addBlock(builder, storeBlockOffset);
        com.adamantite.schema.adamantite.KeyBlock.addBlockCount(builder, keyBlock.blockCount());
        com.adamantite.schema.adamantite.KeyBlock.addDbName(builder, dbNameOffset);
        com.adamantite.schema.adamantite.KeyBlock.addKey(builder, keyOffset);
        com.adamantite.schema.adamantite.KeyBlock.addIv(builder, ivOffset);

        int keyBlockOffset = com.adamantite.schema.adamantite.KeyBlock.endKeyBlock(builder);
        builder.finish(keyBlockOffset);

        return builder.sizedByteArray();
    }

    public static byte[] toFlatBufSymbolSetsBlock(SymbolSetsBlock symbolSetsBlock) {
        StoreBlock storeBlock = symbolSetsBlock;
        FlatBufferBuilder builder = new FlatBufferBuilder(12000);

        List<SymbolSetsBlock.SymbolSet> symbolSets = checkNotNull(symbolSetsBlock).symbolSets();
        int[] symbolSetOffsets = new int[symbolSets.size()];
        for (int i = 0; i < symbolSets.size(); i++) {
            SymbolSetsBlock.SymbolSet symbolSet = symbolSets.get(i);

            int symbolSetNameOffset = builder.createString(symbolSet.getName());
            int symbolSetStrOffset = builder.createString(symbolSet.getSymbolSet());
            int symbolSetOffset = com.adamantite.schema.adamantite.SymbolSet.createSymbolSet(builder, symbolSet.symbolSetId(),
                    symbolSetNameOffset, symbolSetStrOffset);

            symbolSetOffsets[i] = symbolSetOffset;
        }

        int symbolSetsOffset = com.adamantite.schema.adamantite.SymbolSetsBlock.createSymbolSetsVector(builder, symbolSetOffsets);

        com.adamantite.schema.adamantite.SymbolSetsBlock.startSymbolSetsBlock(builder);

        int storeBlockOffset = com.adamantite.schema.adamantite.StoreBlock.createStoreBlock(builder,
                storeBlock.blockId(), storeBlock.version(), storeBlock.entropy());

        com.adamantite.schema.adamantite.SymbolSetsBlock.addBlock(builder, storeBlockOffset);
        com.adamantite.schema.adamantite.SymbolSetsBlock.addSymbolSets(builder, symbolSetsOffset);
        int symbolSetsBlockOffset = com.adamantite.schema.adamantite.SymbolSetsBlock.endSymbolSetsBlock(builder);

        builder.finish(symbolSetsBlockOffset);

        return builder.sizedByteArray();
    }

    public static byte[] toFlatBufFoldersBlock(FoldersBlock foldersBlock) {
        StoreBlock storeBlock = foldersBlock;
        FlatBufferBuilder builder = new FlatBufferBuilder(12000);

        List<FoldersBlock.Folder> folders = checkNotNull(foldersBlock).folders();
        int[] folderOffsets = new int[folders.size()];
        for (int i = 0; i < folders.size(); i++) {
            FoldersBlock.Folder folder = folders.get(i);

            int folderNameOffset = builder.createString(folder.folderName());

            int folderOffset = com.adamantite.schema.adamantite.Folder.createFolder(builder,
                    folder.folderId(), folder.parentFolderId(), folderNameOffset);

            folderOffsets[i] = folderOffset;
        }

        int foldersOffset = com.adamantite.schema.adamantite.FoldersBlock.createFoldersVector(builder, folderOffsets);

        // -----------------------------------------------------------------------

        com.adamantite.schema.adamantite.FoldersBlock.startFoldersBlock(builder);

        int storeBlockOffset = com.adamantite.schema.adamantite.StoreBlock.createStoreBlock(builder,
                storeBlock.blockId(), storeBlock.version(), storeBlock.entropy());

        com.adamantite.schema.adamantite.FoldersBlock.addBlock(builder, storeBlockOffset);
        com.adamantite.schema.adamantite.FoldersBlock.addFolders(builder, foldersOffset);
        int symbolSetsBlockOffset = com.adamantite.schema.adamantite.FoldersBlock.endFoldersBlock(builder);

        builder.finish(symbolSetsBlockOffset);

        return builder.sizedByteArray();
    }

    public static byte[] toFlatBufPhraseTemplatesBlock(PhraseTemplatesBlock phraseTemplatesBlock) {
        StoreBlock storeBlock = phraseTemplatesBlock;
        FlatBufferBuilder builder = new FlatBufferBuilder(12000);

        List<PhraseTemplatesBlock.WordTemplate> wordTemplates =
                checkNotNull(phraseTemplatesBlock).wordTemplates();

        int[] wordTemplateOffsets = new int[wordTemplates.size()];
        for (int i = 0; i < wordTemplates.size(); i++) {
            PhraseTemplatesBlock.WordTemplate wordTemplate = wordTemplates.get(i);

            int wordTemplateId = wordTemplate.wordTemplateId();
            byte permissions = wordTemplate.permissions();
            byte icon = wordTemplate.icon().code;
            int minLength = wordTemplate.minLength();
            int maxLength = wordTemplate.maxLength();

            int wordTemplateNameOffset = builder.createString(wordTemplate.wordTemplateName());
            int[] symbolSetIds = toIdArray(wordTemplate.symbolSetIds());
            int symbolSetIdsOffset = com.adamantite.schema.adamantite.WordTemplate.createSymbolSetIdsVector(builder, symbolSetIds);

            int wordTemplateOffset = com.adamantite.schema.adamantite.WordTemplate.createWordTemplate(builder,
                    wordTemplateId, permissions, icon, minLength, maxLength, wordTemplateNameOffset, symbolSetIdsOffset);

            wordTemplateOffsets[i] = wordTemplateOffset;
        }

        int wordTemplatesOffset =
                com.adamantite.schema.adamantite.PhraseTemplatesBlock.createWordTemplatesVector(builder, wordTemplateOffsets);

        // -----------------------------------------------------------------------

        List<PhraseTemplatesBlock.PhraseTemplate> phraseTemplates =
                checkNotNull(phraseTemplatesBlock).phraseTemplates();

        int[] phraseTemplateOffsets = new int[phraseTemplates.size()];
        for (int i = 0; i < phraseTemplates.size(); i++) {
            PhraseTemplatesBlock.PhraseTemplate phraseTemplate = phraseTemplates.get(i);

            int phraseTemplateId = phraseTemplate.phraseTemplateId();
            int phraseTemplateNameOffset = builder.createString(phraseTemplate.phraseTemplateName());

            int[] wordTemplateRefOffsets = new int[phraseTemplate.wordTemplateRefs().size()];
            for (int j = 0; j < phraseTemplate.wordTemplateRefs().size(); j++) {
                PhraseTemplatesBlock.WordTemplateRef wordTemplateRef = phraseTemplate.wordTemplateRefs().get(j);
                int wordTemplateRefOffset = com.adamantite.schema.adamantite.WordTemplateRef.createWordTemplateRef(builder,
                        wordTemplateRef.wordTemplateId(), UnsignedConverter.shortToByte(wordTemplateRef.wordTemplateOrdinal()));
                wordTemplateRefOffsets[j] = wordTemplateRefOffset;
            }
            int wordTemplateRefsOffset = com.adamantite.schema.adamantite.PhraseTemplate.createWordTemplateRefsVector(builder, wordTemplateRefOffsets);

            int phraseTemplateOffset = com.adamantite.schema.adamantite.PhraseTemplate.createPhraseTemplate(builder,
                    phraseTemplateId, phraseTemplateNameOffset, wordTemplateRefsOffset);

            phraseTemplateOffsets[i] = phraseTemplateOffset;
        }

        int phraseTemplatesOffset =
                com.adamantite.schema.adamantite.PhraseTemplatesBlock.createPhraseTemplatesVector(builder, phraseTemplateOffsets);

        // -----------------------------------------------------------------------

        com.adamantite.schema.adamantite.PhraseTemplatesBlock.startPhraseTemplatesBlock(builder);

        int storeBlockOffset = com.adamantite.schema.adamantite.StoreBlock.createStoreBlock(builder,
                storeBlock.blockId(), storeBlock.version(), storeBlock.entropy());

        com.adamantite.schema.adamantite.PhraseTemplatesBlock.addBlock(builder, storeBlockOffset);
        com.adamantite.schema.adamantite.PhraseTemplatesBlock.addPhraseTemplates(builder, phraseTemplatesOffset);
        com.adamantite.schema.adamantite.PhraseTemplatesBlock.addWordTemplates(builder, wordTemplatesOffset);
        int symbolSetsBlockOffset = com.adamantite.schema.adamantite.FoldersBlock.endFoldersBlock(builder);

        builder.finish(symbolSetsBlockOffset);

        return builder.sizedByteArray();
    }

    public static byte[] toFlatBufPhraseBlock(PhraseBlock phraseBlock) {
        StoreBlock storeBlock = phraseBlock;
        FlatBufferBuilder builder = new FlatBufferBuilder(12000);

        List<PhraseBlock.PhraseHistory> phraseHistories = checkNotNull(phraseBlock).history();

        // Phrase History (array)
        int[] phraseHistoryOffsets = new int[phraseHistories.size()];
        for (int i = 0; i < phraseHistories.size(); i++) {
            PhraseBlock.PhraseHistory phraseHistory = phraseHistories.get(i);

            int phraseTemplateId = phraseHistory.phraseTemplateId();
            List<PhraseBlock.Word> phrase = phraseHistory.phrase();

            // Phrase (word array)
            int[] phraseWordOffsets = new int[phrase.size()];
            for (int j = 0; j < phrase.size(); j++) {
                PhraseBlock.Word phraseWord = phrase.get(j);
                int wordTemplateId = phraseWord.wordTemplateId();
                short wordTemplateOrdinal = phraseWord.wordTemplateOrdinal();
                String name = phraseWord.name();
                String word = phraseWord.word();
                byte permissions = phraseWord.permissions();
                Icon icon = phraseWord.icon();

                int nameOffset = builder.createString(name);
                int wordOffset = builder.createString(word);

                int phraseWordOffset =
                        com.adamantite.schema.adamantite.Word.createWord(builder, wordTemplateId, UnsignedConverter.shortToByte(wordTemplateOrdinal),
                                nameOffset, wordOffset, permissions, icon.code);
                phraseWordOffsets[j] = phraseWordOffset;
            }

            int phraseOffset = com.adamantite.schema.adamantite.PhraseHistory.createPhraseVector(builder, phraseWordOffsets);
            // Phrase END

            int phraseHistoryOffset =
                    com.adamantite.schema.adamantite.PhraseHistory.createPhraseHistory(builder, phraseTemplateId, phraseOffset);
            phraseHistoryOffsets[i] = phraseHistoryOffset;
        }

        int phraseHistoryArrayOffset =
                com.adamantite.schema.adamantite.PhraseBlock.createHistoryVector(builder, phraseHistoryOffsets);
        // Phrase History END

        //Phrase Block
        int phraseNameOffset = builder.createString(phraseBlock.phraseName());

        com.adamantite.schema.adamantite.PhraseBlock.startPhraseBlock(builder);

        int storeBlockOffset = com.adamantite.schema.adamantite.StoreBlock.createStoreBlock(builder,
                storeBlock.blockId(), storeBlock.version(), storeBlock.entropy());
        com.adamantite.schema.adamantite.PhraseBlock.addBlock(builder, storeBlockOffset);

        com.adamantite.schema.adamantite.PhraseBlock.addPhraseTemplateId(builder, phraseBlock.phraseTemplateId());
        com.adamantite.schema.adamantite.PhraseBlock.addFolderId(builder, phraseBlock.folderId());
        com.adamantite.schema.adamantite.PhraseBlock.addIsTombstone(builder, phraseBlock.isTombstone());
        com.adamantite.schema.adamantite.PhraseBlock.addPhraseName(builder, phraseNameOffset);
        com.adamantite.schema.adamantite.PhraseBlock.addHistory(builder, phraseHistoryArrayOffset);

        int phraseBlockOffset = com.adamantite.schema.adamantite.PhraseBlock.endPhraseBlock(builder);
        builder.finish(phraseBlockOffset);

        return builder.sizedByteArray();
    }
}
