package com.badiei.neoexchange.datagen;

import com.badiei.neoexchange.NeoExchange;
import com.badiei.neoexchange.blocks.NeoBlocks;
import com.badiei.neoexchange.items.NeoItems;
import com.mojang.math.Quadrant;
import net.minecraft.client.data.models.BlockModelGenerators;
import net.minecraft.client.data.models.ItemModelGenerators;
import net.minecraft.client.data.models.ModelProvider;
import net.minecraft.client.data.models.MultiVariant;
import net.minecraft.client.data.models.blockstates.MultiVariantGenerator;
import net.minecraft.client.data.models.blockstates.PropertyDispatch;
import net.minecraft.client.data.models.model.ModelTemplates;
import net.minecraft.client.data.models.model.TextureMapping;
import net.minecraft.client.data.models.model.TextureSlot;
import net.minecraft.client.renderer.block.model.VariantMutator;
import net.minecraft.core.Direction;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * Automatically generates model JSON files for items AND blocks.
 * 
 * This is the 1.21.10+ way of doing datagen - one provider handles both!
 * The older way used separate ItemModelProvider and BlockStateProvider classes.
 * 
 * IMPORTANT: This class uses the new unified ModelProvider system where:
 * - BlockModelGenerators handles block models and blockstates
 * - ItemModelGenerators handles item models
 * 
 * Run with: gradlew runData
 */
public class ModelsProvider extends ModelProvider {
    
    public ModelsProvider(PackOutput output) {
        super(output, NeoExchange.MOD_ID);
    }

    @Override
    protected void registerModels(BlockModelGenerators blockModels, ItemModelGenerators itemModels) {
        // ========== ITEMS ==========
        registerItems(itemModels);
        
        // ========== BLOCKS ==========
        registerBlocks(blockModels);
    }
    
    /**
     * Register all item models here.
     * 
     * generateFlatItem() creates a simple 2D item model with the pattern:
     * {
     *   "parent": "minecraft:item/generated",
     *   "textures": {
     *     "layer0": "neoexchange:item/<item_name>"
     *   }
     * }
     */
    private void registerItems(ItemModelGenerators itemModels) {
        // Original neo stone
        itemModels.generateFlatItem(NeoItems.NEO_STONE.get(), ModelTemplates.FLAT_ITEM);
        
        // All the tiered stones
        itemModels.generateFlatItem(NeoItems.COMMON_STONE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(NeoItems.UNCOMMON_STONE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(NeoItems.RARE_STONE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(NeoItems.EPIC_STONE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(NeoItems.LEGENDARY_STONE.get(), ModelTemplates.FLAT_ITEM);
        itemModels.generateFlatItem(NeoItems.MYTHIC_STONE.get(), ModelTemplates.FLAT_ITEM);
        
        // ========== FUTURE ITEMS ==========
        // When you add more items, just add them here!
        // itemModels.generateFlatItem(NeoItems.YOUR_ITEM.get(), ModelTemplates.FLAT_ITEM);
    }
    
    /**
     * Register all block models and blockstates here.
     * 
     * For blocks, we need to:
     * 1. Create the block model (defines textures and shape)
     * 2. Create the blockstate (tells game which model to use)
     * 3. The item model is auto-generated from the block model (since BlockItem)
     */
    private void registerBlocks(BlockModelGenerators blockModels) {
        // Neo Plate block - HYBRID APPROACH (Option 1)
        // Datagen creates: blockstate JSON with rotations + item model
        // Manual file: models/block/neo_plate.json (custom 3-block-tall shape)
        createNeoPlateBlock(blockModels);
        
        // ========== FUTURE BLOCKS ==========
        // When you add more blocks, add them here!
        // For simple cube blocks: blockModels.createTrivialCube(YourBlock.get());
        // For directional blocks: blockModels.createRotatedPillarBlock(YourBlock.get());
    }
    
    /**
     * Creates the Neo Plate block - a directional 3-block-tall block.
     * 
     * HYBRID APPROACH (Option 1):
     * - Manual model.json: Contains custom 3-block-tall shape with display transforms
     * - Datagen blockstate.json: Creates all facing rotations automatically
     * - Datagen item model: Auto-generated to point to block model
     * 
     * This is the BEST approach because:
     * 1. Complex custom model is maintained manually (easy to edit visually)
     * 2. Repetitive blockstate rotations are auto-generated (no copy-paste errors)
     * 3. You learn how PropertyDispatch works for directional blocks!
     */
    private void createNeoPlateBlock(BlockModelGenerators blockModels) {
        // OPTION 1: Simple approach - reference manual model
        // If you have a manually created model at models/block/neo_plate.json,
        // you can just reference it:
        
        ResourceLocation manualModel = ResourceLocation.fromNamespaceAndPath(
            NeoExchange.MOD_ID, 
            "block/neo_plate"  // Points to manual JSON
        );
        
        // Create the variant that points to our manual model
        MultiVariant baseVariant = BlockModelGenerators.plainVariant(manualModel);
        
        // Now create the directional blockstate with rotations
        // This matches your manual blockstates JSON!
        // PropertyDispatch.initial() creates a C1<MultiVariant, Direction> for the FACING property
        blockModels.blockStateOutput.accept(
            MultiVariantGenerator.dispatch(NeoBlocks.NEO_PLATE.get())
                .with(PropertyDispatch.initial(BlockStateProperties.FACING)
                    .select(Direction.UP, baseVariant)
                    .select(Direction.DOWN, baseVariant.with(VariantMutator.X_ROT.withValue(Quadrant.R180)))
                    .select(Direction.NORTH, baseVariant.with(VariantMutator.X_ROT.withValue(Quadrant.R90)))
                    .select(Direction.SOUTH, baseVariant
                        .with(VariantMutator.X_ROT.withValue(Quadrant.R90))
                        .with(VariantMutator.Y_ROT.withValue(Quadrant.R180)))
                    .select(Direction.WEST, baseVariant
                        .with(VariantMutator.X_ROT.withValue(Quadrant.R90))
                        .with(VariantMutator.Y_ROT.withValue(Quadrant.R270)))
                    .select(Direction.EAST, baseVariant
                        .with(VariantMutator.X_ROT.withValue(Quadrant.R90))
                        .with(VariantMutator.Y_ROT.withValue(Quadrant.R90)))
                )
        );
        
        // OPTION 2: If you wanted datagen to create EVERYTHING (not recommended for custom shapes)
        // You'd need to create a custom ModelTemplate for the 3-block-tall shape
        // This is complex and usually not worth it for one-off custom models
    }
    
    /* 
     * WHAT GETS GENERATED FOR NEO_PLATE:
     * 
     * 1. BLOCKSTATE JSON:
     *    assets/neoexchange/blockstates/neo_plate.json
     *    {
     *      "variants": {
     *        "": {
     *          "model": "neoexchange:block/neo_plate"
     *        }
     *      }
     *    }
     * 
     * 2. BLOCK MODEL JSON:
     *    assets/neoexchange/models/block/neo_plate.json
     *    {
     *      "parent": "minecraft:block/cube_bottom_top",
     *      "textures": {
     *        "top": "neoexchange:block/neo_plate/neo_plate_top",
     *        "bottom": "neoexchange:block/neo_plate/neo_plate_base",
     *        "side": "neoexchange:block/neo_plate/neo_plate_base"
     *      }
     *    }
     * 
     * 3. ITEM MODEL (auto-generated because it's a BlockItem):
     *    assets/neoexchange/items/neo_plate.json
     *    {
     *      "model": {
     *        "type": "minecraft:model",
     *        "model": "neoexchange:block/neo_plate"
     *      }
     *    }
     * 
     * All from just that Java code above! 🎉
     * 
     * LEARNING POINTS:
     * 
     * 1. THE THREE-STEP PATTERN:
     *    Step 1: Create model → get ResourceLocation
     *    Step 2: Wrap in MultiVariant using plainVariant()
     *    Step 3: Use static dispatch(block, variant) to create complete generator
     * 
     * 2. WHY THREE STEPS?
     *    - The model file needs to exist first (Step 1)
     *    - The variant wraps the model location for the blockstate (Step 2)
     *    - The dispatch creates the complete blockstate generator (Step 3)
     * 
     * 3. STATIC vs INSTANCE METHODS:
     *    - dispatch(Block, MultiVariant) is STATIC - it creates a new generator
     *    - It's NOT an instance method you chain onto something
     *    - Think of it as a factory: "give me block + variant, I'll make the generator"
     * 
     * 4. ResourceLocation for Subfolders:
     *    When textures are in subfolders like "block/neo_plate/", you need to
     *    manually create ResourceLocations pointing to the full path.
     * 
     * 5. BlockItem Auto-Generation:
     *    In 1.21.10, when you have a BlockItem, the item model is automatically
     *    generated to point to the block model. No manual item model needed!
     * 
     * TEXTURE FILE STRUCTURE:
     * Your actual files:
     *   src/main/resources/assets/neoexchange/textures/block/neo_plate/neo_plate_top.png
     *   src/main/resources/assets/neoexchange/textures/block/neo_plate/neo_plate_base.png
     * 
     * Referenced in JSON as:
     *   "neoexchange:block/neo_plate/neo_plate_top"
     *   "neoexchange:block/neo_plate/neo_plate_base"
     * 
     * THE BIG LESSON:
     * Always read the Minecraft source code! The API changes between versions,
     * and what worked in 1.20 might be completely different in 1.21.10.
     * The static dispatch() method is the modern way - much simpler than manually
     * building blockstate generators!
     */
}
