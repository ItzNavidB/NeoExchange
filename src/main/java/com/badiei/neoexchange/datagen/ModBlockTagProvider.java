package com.badiei.neoexchange.datagen;

import com.badiei.neoexchange.NeoExchange;
import com.badiei.neoexchange.blocks.NeoBlocks;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.tags.BlockTags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * Generates block tags (which tool can mine, which tool tier needed, etc.)
 */
public class ModBlockTagProvider extends BlockTagsProvider {
    public ModBlockTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, NeoExchange.MOD_ID);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        // Neo Plate can be mined with a pickaxe
        tag(BlockTags.MINEABLE_WITH_PICKAXE)
                .add(NeoBlocks.NEO_PLATE.get());

        // Neo Plate requires iron tool or better
        //tag(BlockTags.NEEDS_IRON_TOOL)
        //        .add(NeoBlocks.NEO_PLATE.get());
    }
}
