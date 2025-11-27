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
 * Main datagen class that registers all data providers
 * Run with: gradlew runData
 */
@EventBusSubscriber(modid = NeoExchange.MOD_ID)
public class DataGenerators {
    @SubscribeEvent
    public static void gatherData(GatherDataEvent.Client event) {
        DataGenerator generator = event.getGenerator();
        PackOutput packOutput = generator.getPackOutput();
        CompletableFuture<HolderLookup.Provider> lookupProvider = event.getLookupProvider();

        // Server-side data (recipes, loot tables, tags)
        generator.addProvider(true, new LootTableProvider(packOutput, Collections.emptySet(),
                List.of(new LootTableProvider.SubProviderEntry(ModBlockLootTableProvider::new, LootContextParamSets.BLOCK)), 
                lookupProvider));
        
        // Use the new Runner pattern for recipes (1.21.10+)
        generator.addProvider(true, new ModRecipeProvider.Runner(packOutput, lookupProvider));


        BlockTagsProvider blockTagsProvider = new ModBlockTagProvider(packOutput, lookupProvider);
        generator.addProvider(true, blockTagsProvider);
        generator.addProvider(true, new ModItemTagProvider(packOutput, lookupProvider));

        // Client-side data (models, blockstates)
        //generator.addProvider(true, new ModItemModelProvider(packOutput, lookupProvider));
        //generator.addProvider(true, new ModBlockStateProvider(packOutput));
    }
}
