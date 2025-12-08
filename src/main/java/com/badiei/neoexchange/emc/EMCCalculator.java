package com.badiei.neoexchange.emc;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.*;

/**
 * EMCCalculator - The brain of the EMC system
 *
 * This class analyzes all crafting recipes in the game and calculates
 * EMC values based on the ingredients. It's like a smart accountant that
 * figures out the value of products based on their components.
 *
 * Algorithm:
 * 1. Start with base values (your manual assignments)
 * 2. For each recipe, check if we know the value of all ingredients
 * 3. If yes, calculate the output value (sum of ingredients / output count)
 * 4. Repeat until no new values are found (convergence)
 * 5. Handle special cases (cheapest recipe wins, prevent loops)
 *
 * This is an iterative algorithm because recipes can depend on each other.
 * For example, you might need to know the value of planks to calculate
 * the value of sticks, but you need logs to calculate planks!
 */
public class EMCCalculator {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Level level;
    private final EMCConfig config;

    // Calculation state
    private final Map<ResourceLocation, Long> calculatedValues = new HashMap<>();
    private final Map<ResourceLocation, Integer> calculationAttempts = new HashMap<>();

    // Settings
    private static final int MAX_ITERATIONS = 10; // Prevent infinite loops
    private static final int MAX_ATTEMPTS_PER_ITEM = 5; // Prevent stuck items

    public EMCCalculator(Level level, EMCConfig config) {
        this.level = level;
        this.config = config;
    }

    /**
     * Run the EMC calculation algorithm
     *
     * This is the main entry point. It will:
     * 1. Load base values from config
     * 2. Iteratively calculate recipe-based values
     * 3. Save computed values to config
     *
     * @return Number of items with calculated values
     */
    public int calculate() {
        LOGGER.info("Starting EMC calculation...");
        long startTime = System.currentTimeMillis();

        // Step 1: Start with base values
        calculatedValues.clear();
        calculatedValues.putAll(config.getBaseValues());

        LOGGER.info("Starting with {} base values", calculatedValues.size());

        // Step 2: Get all recipes
        RecipeManager recipeManager = (RecipeManager) level.recipeAccess();
        Collection<RecipeHolder<?>> allRecipes = recipeManager.getRecipes();
        LOGGER.info("Found {} total recipes to analyze", allRecipes.size());

        // Step 3: Iteratively calculate values
        int iteration = 0;
        int lastSize = calculatedValues.size();

        while (iteration < MAX_ITERATIONS) {
            iteration++;

            LOGGER.info("Iteration {}: Processing {} recipes...", iteration, allRecipes.size());

            int newValuesThisIteration = 0;

            // Process each recipe
            for (RecipeHolder<?> holder : allRecipes) {
                Recipe<?> recipe = holder.value();

                // Try to calculate EMC for this recipe
                if (processRecipe(recipe)) {
                    newValuesThisIteration++;
                }
            }

            int currentSize = calculatedValues.size();
            int gained = currentSize - lastSize;

            LOGGER.info("Iteration {} complete: {} new items calculated ({} total)",
                    iteration, gained, currentSize);

            // If we didn't find any new values, we're done!
            if (gained == 0) {
                LOGGER.info("Convergence reached! No new values found.");
                break;
            }

            lastSize = currentSize;
        }

        // Step 4: Save to config
        config.setComputedValues(calculatedValues);
        config.saveComputedValues();

        long endTime = System.currentTimeMillis();
        LOGGER.info("EMC calculation complete in {}ms: {} items have values",
                (endTime - startTime), calculatedValues.size());

        return calculatedValues.size();
    }

    /**
     * Process a single recipe and try to calculate EMC for its output
     *
     * @param recipe The recipe to process
     * @return true if we calculated a new EMC value
     */

    private boolean processRecipe(Recipe<?> recipe) {
        // Only handle certain recipe types
        if (!isSupportedRecipeType(recipe)) {
            return false;
        }

        // Get recipe displays - these show what the recipe produces
        List<RecipeDisplay> displays = recipe.display();
        //LOGGER.info("Recipe Display: " + displays);

        if (displays.isEmpty()) {
            return false;
        }

        // Get the result from the first display
        // RecipeDisplay has a result() method that returns SlotDisplay
        //RecipeDisplay display = displays.getFirst();

        // For now, we'll skip this - it's too complex
        // We need to extract ItemStack from SlotDisplay which is nested

        // SIMPLER APPROACH: Use type-specific methods
        ItemStack result = ItemStack.EMPTY;

        if (recipe instanceof CraftingRecipe craftingRecipe) {
            // For crafting recipes, create a dummy input
            List<ItemStack> emptyGrid = Collections.nCopies(9, ItemStack.EMPTY);
            CraftingInput dummyInput = CraftingInput.of(3, 3, emptyGrid); // 3x3 empty grid
            result = craftingRecipe.assemble(dummyInput, level.registryAccess());

        } else if (recipe instanceof SingleItemRecipe singleItemRecipe) {
            // Smelting, blasting, smoking, etc. all extend SingleItemRecipe
            SingleRecipeInput dummyInput = new SingleRecipeInput(ItemStack.EMPTY);
            result = singleItemRecipe.assemble(dummyInput, level.registryAccess());
        }

        if (result.isEmpty()) {
            return false;
        }

        Item outputItem = result.getItem();
        ResourceLocation outputId = BuiltInRegistries.ITEM.getKey(outputItem);
        // ... rest of your existing code

        // Skip if we already have a value for this item
        if (calculatedValues.containsKey(outputId)) {
            return false;
        }

        // Prevent infinite loops on problematic recipes
        int attempts = calculationAttempts.getOrDefault(outputId, 0);
        if (attempts >= MAX_ATTEMPTS_PER_ITEM) {
            return false;
        }
        calculationAttempts.put(outputId, attempts + 1);

        // Calculate the total EMC cost of ingredients
        Optional<Long> ingredientCost = calculateIngredientCost(recipe);

        if (ingredientCost.isEmpty()) {
            // Can't calculate yet - missing ingredient values
            return false;
        }

        // Calculate per-item EMC value
        long totalCost = ingredientCost.get();
        int outputCount = result.getCount();

        if (outputCount <= 0) {
            LOGGER.warn("Recipe has invalid output count: {}", outputId);
            return false;
        }

        long emcPerItem = totalCost / outputCount;

        // Only accept if it's a positive value
        if (emcPerItem <= 0) {
            return false;
        }

        // Check if this is a better (cheaper) recipe than existing value
        Long existingValue = calculatedValues.get(outputId);
        if (existingValue != null && existingValue <= emcPerItem) {
            // We already have a cheaper way to make this item
            return false;
        }

        // Success! Store the calculated value
        calculatedValues.put(outputId, emcPerItem);

        LOGGER.debug("Calculated EMC for {}: {} (from {} ingredient cost, {} outputs)",
                outputId, emcPerItem, totalCost, outputCount);

        return true;
    }

    /**
     * Calculate the total EMC cost of all ingredients in a recipe
     *
     * @param recipe The recipe
     * @return Optional containing total cost, or empty if any ingredient has no value
     */
    private Optional<Long> calculateIngredientCost(Recipe<?> recipe) {
        // Get ingredients based on recipe type
        List<Ingredient> ingredients = getIngredientsFromRecipe(recipe);

        if (ingredients.isEmpty()) {
            return Optional.empty();
        }

        long totalCost = 0;

        for (Ingredient ingredient : ingredients) {
            // Each ingredient can match multiple items (tags, etc.)
            // We want the CHEAPEST matching item (best conversion rate)
            Optional<Long> cheapestValue = getCheapestIngredientValue(ingredient);

            if (cheapestValue.isEmpty()) {
                // We don't know the value of this ingredient yet
                return Optional.empty();
            }

            totalCost += cheapestValue.get();
        }

        return Optional.of(totalCost);
    }

    /**
     * Find the cheapest item that matches an ingredient
     *
     * For example, if a recipe accepts "any log", we want to use
     * the cheapest log type when calculating value.
     */
    private Optional<Long> getCheapestIngredientValue(Ingredient ingredient) {
        // Get items as a stream of holders, convert to list
        List<Holder<Item>> itemHolders = ingredient.items().toList();

        if (itemHolders.isEmpty()) {
            return Optional.empty();
        }

        Long cheapest = null;

        for (Holder<Item> holder : itemHolders) {
            Item item = holder.value(); // Get the actual Item from the Holder
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            Long value = calculatedValues.get(itemId);

            if (value != null) {
                // For simplicity, assume stack size of 1
                if (cheapest == null || value < cheapest) {
                    cheapest = value;
                }
            }
        }

        return Optional.ofNullable(cheapest);
    }

    /**
     * Extract ingredients from a recipe based on its type
     */
    private List<Ingredient> getIngredientsFromRecipe(Recipe<?> recipe) {
        PlacementInfo placementInfo = recipe.placementInfo();

        // Check if recipe is even placeable
        if (placementInfo.isImpossibleToPlace()) {
            return List.of(); // Empty list
        }

        // Get the ingredients directly!
        return placementInfo.ingredients();
    }

    /**
     * Check if we should process this recipe type
     * We skip some recipe types that don't make sense for EMC
     */
    private boolean isSupportedRecipeType(Recipe<?> recipe) {
        // Skip special recipes (map cloning, fireworks, etc.)
        if (recipe instanceof CustomRecipe) {
            return false;
        }

        // We support most standard recipe types
        return recipe instanceof CraftingRecipe ||
                recipe instanceof SmeltingRecipe ||
                recipe instanceof BlastingRecipe ||
                recipe instanceof SmokingRecipe ||
                recipe instanceof CampfireCookingRecipe ||
                recipe instanceof StonecutterRecipe ||
                recipe instanceof SmithingRecipe;
    }

    /**
     * Get the computed values (after calculation)
     */
    public Map<ResourceLocation, Long> getCalculatedValues() {
        return Collections.unmodifiableMap(calculatedValues);
    }

    /**
     * Debug method: Print items that still don't have values
     */
    public void printMissingValues() {
        LOGGER.info("=== Items without EMC values ===");

        int missingCount = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);

            // Skip air
            if (id.toString().equals("minecraft:air")) {
                continue;
            }

            if (!calculatedValues.containsKey(id)) {
                LOGGER.info("Missing: {}", id);
                missingCount++;
            }
        }

        LOGGER.info("Total items without EMC: {}", missingCount);
    }
}