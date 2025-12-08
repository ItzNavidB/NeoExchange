package com.badiei.neoexchange.datagen;

import com.badiei.neoexchange.blocks.NeoBlocks;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.loot.BlockLootSubProvider;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.block.Block;

import java.util.Set;

/**
 * Defines what drops when blocks are broken
 */
public class ModBlockLootTableProvider extends BlockLootSubProvider {
    protected ModBlockLootTableProvider(HolderLookup.Provider registries) {
        super(Set.of(), FeatureFlags.REGISTRY.allFlags(), registries);
    }

    @Override
    protected void generate() {
        // Neo Plate drops itself
        // Note: The stored stone is handled by the block entity's drops() method
        dropSelf(NeoBlocks.NEO_PLATE.get());
    }

    @Override
    protected Iterable<Block> getKnownBlocks() {
        return NeoBlocks.BLOCKS.getEntries().stream().map(Holder::value)::iterator;
    }
}
