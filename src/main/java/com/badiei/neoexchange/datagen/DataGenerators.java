package com.badiei.neoexchange.datagen;

import com.badiei.neoexchange.NeoExchange;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraft.data.loot.LootTableProvider;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.data.BlockTagsProvider;
import net.neoforged.neoforge.data.event.GatherDataEvent;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Main datagen class that registers all data providers.
 * 
 * This is the central hub that tells Gradle which providers to run
 * when you execute: gradlew runData
 * 
 * Each provider generates different types of JSON files:
 * - ModelsProvider: Item models, block models, and blockstates
 * - ModRecipeProvider: Crafting recipes
 * - LootTableProvider: What blocks drop when broken
 * - Tag Providers: Groups items/blocks together (like "minecraft:logs")
 */
@EventBusSubscriber(modid = NeoExchange.MOD_ID)
public class DataGenerators {
    @SubscribeEvent
    public static void gatherData(GatherDataEvent.Client event) {
        DataGenerator generator = event.getGenerator();
        PackOutput packOutput = generator.getPackOutput();
        CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();

        // ========== SERVER-SIDE DATA ==========
        // These files affect gameplay mechanics
        
        // Loot tables - what drops when you break blocks
        generator.addProvider(true, new LootTableProvider(packOutput, Collections.emptySet(),
                List.of(new LootTableProvider.SubProviderEntry(ModBlockLootTableProvider::new, LootContextParamSets.BLOCK)), 
                lookupProvider));
        
        // Recipes - how to craft items
        generator.addProvider(true, new ModRecipeProvider.Runner(packOutput, lookupProvider));

        // Tags - grouping items/blocks (e.g., all blocks mineable with pickaxe)
        BlockTagsProvider blockTagsProvider = new ModBlockTagProvider(packOutput, lookupProvider);
        generator.addProvider(true, blockTagsProvider);
        generator.addProvider(true, new ModItemTagProvider(packOutput, lookupProvider));

        // ========== CLIENT-SIDE DATA ==========
        // These files only affect how things look (visuals)
        
        // Models and blockstates - the unified 1.21.10+ way!
        // This single provider handles:
        // - Item models (how items look in inventory/hand)
        // - Block models (the 3D shape and textures of blocks)
        // - Blockstates (which model to use for each block state)
        generator.addProvider(true, new ModelsProvider(packOutput));
    }
}
