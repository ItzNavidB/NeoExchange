package com.badiei.neoexchange.datagen;

import com.badiei.neoexchange.NeoExchange;
import com.badiei.neoexchange.blocks.NeoBlocks;
import com.badiei.neoexchange.items.NeoItems;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Blocks;

import java.util.concurrent.CompletableFuture;

/**
 * Generates crafting recipes
 * Note: Stone recipes are in data/neoexchange/recipe/*.json
 * This provider is for any additional recipes you want to add programmatically
 */
public class ModRecipeProvider extends RecipeProvider {
    
    public ModRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
        super(registries, output);
    }

    @Override
    protected void buildRecipes() {
        //ShapelessRecipeBuilder.shapeless(this.registries.lookupOrThrow(Registries.ITEM), RecipeCategory.MISC, NeoItems.NEO_STONE, 1)
        //                .requires(Blocks.STONE);
        ShapedRecipeBuilder.shaped(this.registries.lookupOrThrow(Registries.ITEM), RecipeCategory.MISC, NeoItems.NEO_STONE, 1)
                .pattern("SSS")
                .pattern("SRS")
                .pattern("SSS")
                .define('S', Items.STONE)
                .define('R', Items.REDSTONE)
                .unlockedBy("has_stone", has(Items.COBBLESTONE))
                .save(this.output);

        // Example: Neo Plate crafting recipe
        ShapedRecipeBuilder.shaped(this.registries.lookupOrThrow(Registries.ITEM), RecipeCategory.MISC, NeoBlocks.NEO_PLATE.get(), 1)
                .pattern("SOS")
                .pattern("ONO")
                .pattern("SOS")
                .define('S', Items.STONE)
                .define('O', Items.OBSIDIAN)
                .define('N', NeoItems.NEO_STONE)
                .unlockedBy("has_neo_stone", has(NeoItems.NEO_STONE))
                .save(this.output);

        ShapedRecipeBuilder.shaped(this.registries.lookupOrThrow(Registries.ITEM), RecipeCategory.MISC, NeoItems.COMMON_STONE, 1)
                .pattern("ICI")
                .pattern("CNC")
                .pattern("ICI")
                .define('I', Items.IRON_BLOCK)
                .define('C', Items.COAL_BLOCK)
                .define('N', NeoItems.NEO_STONE)
                .unlockedBy("has_neo_stone", has(NeoItems.NEO_STONE))
                .save(this.output);

        ShapedRecipeBuilder.shaped(this.registries.lookupOrThrow(Registries.ITEM), RecipeCategory.MISC, NeoItems.UNCOMMON_STONE, 1)
                .pattern("GLG")
                .pattern("SNS")
                .pattern("GLG")
                .define('G', Items.GOLD_BLOCK)
                .define('L', Items.LAPIS_BLOCK)
                .define('S', Items.GLOWSTONE)
                .define('N', NeoItems.COMMON_STONE)
                .unlockedBy("has_neo_stone", has(NeoItems.NEO_STONE))
                .save(this.output);

        ShapedRecipeBuilder.shaped(this.registries.lookupOrThrow(Registries.ITEM), RecipeCategory.MISC, NeoItems.RARE_STONE, 1)
                .pattern("DED")
                .pattern("ENE")
                .pattern("DED")
                .define('D', Items.DIAMOND_BLOCK)
                .define('E', Items.ENDER_EYE)
                .define('N', NeoItems.UNCOMMON_STONE)
                .unlockedBy("has_neo_stone", has(NeoItems.NEO_STONE))
                .save(this.output);

        ShapedRecipeBuilder.shaped(this.registries.lookupOrThrow(Registries.ITEM), RecipeCategory.MISC, NeoItems.EPIC_STONE, 1)
                .pattern("RRR")
                .pattern("RNR")
                .pattern("RRR")
                .define('R', Items.NETHERITE_INGOT)
                .define('N', NeoItems.RARE_STONE)
                .unlockedBy("has_neo_stone", has(NeoItems.NEO_STONE))
                .save(this.output);

        ShapedRecipeBuilder.shaped(this.registries.lookupOrThrow(Registries.ITEM), RecipeCategory.MISC, NeoItems.LEGENDARY_STONE, 1)
                .pattern(" D ")
                .pattern("BNB")
                .pattern(" S ")
                .define('S', Items.NETHER_STAR)
                .define('B', Items.NETHERITE_BLOCK)
                .define('D', Items.DRAGON_EGG)
                .define('N', NeoItems.EPIC_STONE)
                .unlockedBy("has_neo_stone", has(NeoItems.NEO_STONE))
                .save(this.output);

        /* I Don't want this to be a recipe, but the template is here just in case
        ShapedRecipeBuilder.shaped(this.registries.lookupOrThrow(Registries.ITEM), RecipeCategory.MISC, NeoItems.MYTHIC_STONE, 1)
                .pattern("SSS")
                .pattern("SNS")
                .pattern("SSS")
                .define('S', Items.STONE)
                .define('N', NeoItems.NEO_STONE)
                .unlockedBy("has_neo_stone", has(NeoItems.NEO_STONE))
                .save(this.output);
        */
        // You can add more recipes here programmatically
        // But your stone recipes in the JSON files will work fine!
    }

    /**
     * Runner class that NeoForge uses to create the provider
     * This is the 1.21.10 pattern for recipe providers
     */
    public static class Runner extends RecipeProvider.Runner {

        public Runner(PackOutput packOutput, CompletableFuture<HolderLookup.Provider> registries) {
            super(packOutput, registries);
        }

        @Override
        protected RecipeProvider createRecipeProvider(HolderLookup.Provider registries, RecipeOutput output) {
            return new ModRecipeProvider(registries, output);
        }

        @Override
        public String getName() {
            return "NeoExchange Recipes";
        }
    }
}
