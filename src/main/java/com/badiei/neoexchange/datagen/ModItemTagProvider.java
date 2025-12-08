package com.badiei.neoexchange.datagen;

import com.badiei.neoexchange.NeoExchange;
import com.badiei.neoexchange.items.NeoItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.common.data.ItemTagsProvider;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * Generates item tags
 */
public class ModItemTagProvider extends ItemTagsProvider {
    public ModItemTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookupProvider) {
        super(output, lookupProvider, NeoExchange.MOD_ID);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        // Tag all Neo Stones as useable stones
        tag(ModTags.Items.USEABLE_STONES)
                .add(NeoItems.COMMON_STONE.get())
                .add(NeoItems.UNCOMMON_STONE.get())
                .add(NeoItems.RARE_STONE.get())
                .add(NeoItems.EPIC_STONE.get())
                .add(NeoItems.LEGENDARY_STONE.get())
                .add(NeoItems.MYTHIC_STONE.get());
    }
}
