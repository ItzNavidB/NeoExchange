package com.badiei.neoexchange.emc;

import com.badiei.neoexchange.items.NeoItems;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.nio.file.Path;
import java.util.*;

/**
 * EMCRegistry - The heart of our value system (JSON-powered version)
 *
 * This singleton manages EMC values loaded from JSON files.
 * It works with EMCConfig and EMCCalculator to provide a complete
 * EMC value system.
 *
 * Flow:
 * 1. Load base values from emc_base_values.json
 * 2. Run calculator to generate values from recipes
 * 3. Save computed values to emc_computed_values.json
 * 4. Use computed values for gameplay
 */
public class EMCRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static EMCRegistry instance;

    private EMCConfig config;
    private final Map<Item, Long> activeValues = new HashMap<>();
    public static List<ItemStack> itemStackList = BuiltInRegistries.ITEM.stream().map(ItemStack::new).toList();
    public List<Item> inactiveValues = List.of();

    private boolean initialized = false;
    private Path configPath; // Store the config path for reload

    // Private constructor for singleton
    private EMCRegistry() {}

    /**
     * Get the singleton instance
     */
    public static EMCRegistry getInstance() {
        if (instance == null) {
            instance = new EMCRegistry();
        }
        return instance;
    }

    /**
     * Initialize the registry with a config directory
     * This should be called during mod setup
     *
     * @param configDir The config directory path
     */
    public void initialize(Path configDir) {
        if (initialized) {
            LOGGER.warn("EMCRegistry already initialized!");
            return;
        }

        LOGGER.info("Initializing EMC Registry...");

        this.configPath = configDir; // Store for reload
        config = new EMCConfig(configDir);
        // Load base values
        if (!config.loadBaseValues()) {
            LOGGER.error("Failed to load base EMC values!");
            return;
        }

        // Try to load existing computed values
        boolean hasComputedValues = config.loadComputedValues();

        if (hasComputedValues) {
            LOGGER.info("Loaded existing computed values");
            loadComputedValuesIntoRegistry();
        } else {
            LOGGER.info("No computed values found - use /emc calculate to generate them");
            // For now, just use base values
            loadBaseValuesIntoRegistry();
        }

        initialized = true;
        LOGGER.info("EMC Registry initialized with {} active values", activeValues.size());
        getItemsWithoutEMC();
    }

    /**
     * Run the EMC calculator
     * This analyzes all recipes and generates EMC values
     *
     * @param level The level (needed to access recipe manager)
     * @return Number of items with calculated values
     */
    public int runCalculator(Level level) {
        if (!initialized) {
            LOGGER.error("EMCRegistry not initialized! Call initialize() first.");
            return 0;
        }

        LOGGER.info("Running EMC calculator...");

        EMCCalculator calculator = new EMCCalculator(level, config);
        int count = calculator.calculate();

        // Reload the computed values into the registry
        loadComputedValuesIntoRegistry();

        LOGGER.info("Calculator finished: {} items now have EMC values", count);

        return count;
    }

    /**
     * Load computed values from config into the active registry
     */
    private void loadComputedValuesIntoRegistry() {
        activeValues.clear();

        Map<ResourceLocation, Long> computed = config.getComputedValues();

        for (Map.Entry<ResourceLocation, Long> entry : computed.entrySet()) {
            Optional<Item> item = BuiltInRegistries.ITEM.getOptional(entry.getKey());

            if (item.isPresent()) {
                activeValues.put(item.get(), entry.getValue());
                if (itemStackList.contains(item.get())) {
                    itemStackList.remove(item.get());
                }


            } else {
                LOGGER.warn("Item not found in registry: {}", entry.getKey());
            }
        }

        LOGGER.debug("Loaded {} computed values into active registry", activeValues.size());
    }

    /**
     * Load only base values into the active registry
     * Used as a fallback if computed values don't exist yet
     */
    private void loadBaseValuesIntoRegistry() {
        activeValues.clear();

        Map<ResourceLocation, Long> base = config.getBaseValues();

        for (Map.Entry<ResourceLocation, Long> entry : base.entrySet()) {
            Optional<Item> item = BuiltInRegistries.ITEM.getOptional(entry.getKey());

            if (item.isPresent()) {
                activeValues.put(item.get(), entry.getValue());
            }
        }

        LOGGER.debug("Loaded {} base values into active registry", activeValues.size());
    }

    /**
     * Get the EMC value of an item
     *
     * @param item The item to query
     * @return Optional containing the EMC value, or empty if none exists
     */
    public Optional<Long> getEMC(Item item) {
        return Optional.ofNullable(activeValues.get(item));
    }

    /**
     * Check if an item has an EMC value
     */
    public boolean hasEMC(Item item) {
        if (!initialized || item == null) return false;
        return activeValues.containsKey(item);
    }

    public static List<Item> getItemsWithoutEMC() {

        // If the singleton hasn't been created OR not initialized → prevent crashes
        if (instance == null || !instance.initialized) {
            return List.of();
        }
        List<Item> itemsList = instance.inactiveValues;
        if (itemsList.isEmpty()) {
            List<Item> IuaValues = new ArrayList<>(List.of());
            for (ItemStack value : itemStackList) {
                if (value != null) {
                    if(!getInstance().hasEMC(value.getItem())) {
                        IuaValues.add(value.getItem());
                    };
                }
            }
            return IuaValues;
        }
        return itemsList;
    }

    public static List<Item> getItemsWithEMC() {

        // If the singleton hasn't been created OR not initialized → prevent crashes
        if (instance == null || !instance.initialized) {
            return List.of();
        }
        List<Item> itemsList = instance.inactiveValues;
        if (itemsList.isEmpty()) {
            List<Item> IuaValues = new ArrayList<>(List.of());
            for (ItemStack value : itemStackList) {
                if (value != null) {
                    if(getInstance().hasEMC(value.getItem())) {
                        IuaValues.add(value.getItem());
                    };
                }
            }
            return IuaValues;
        }
        return itemsList;
    }

    /**
     * Get the total number of items with EMC values
     */
    public int getRegisteredItemCount() {
        return activeValues.size();
    }

    /**
     * Manually register an EMC value at runtime
     * This adds to the base values and saves to config
     *
     * @param item The item
     * @param value The EMC value
     */
    public void registerEMC(Item item, long value) {
        if (!initialized) {
            LOGGER.error("Cannot register EMC - registry not initialized!");
            return;
        }

        if (value < 0) {
            LOGGER.warn("Attempted to register negative EMC value for item: {}", item);
            return;
        }

        // Add to active values
        activeValues.put(item, value);

        // Add to custom values and save
        config.setBaseValue(item, value);
        config.saveCustomValue(item, value);

        LOGGER.info("Registered EMC value: {} = {}",
                BuiltInRegistries.ITEM.getKey(item), value);
    }

    /**
     * Reload the registry from files
     * Uses the stored config path from initialization
     */
    public void reload() {
        if (configPath == null) {
            LOGGER.error("Cannot reload - EMC Registry was never initialized!");
            return;
        }

        LOGGER.info("Reloading EMC Registry from {}...", configPath);

        initialized = false;
        activeValues.clear();

        initialize(configPath);
    }

    /**
     * Get statistics about the registry
     */
    public RegistryStats getStats() {
        if (!initialized) {
            return new RegistryStats(0, 0, 0, 0);
        }

        int baseCount = config.getBaseValues().size();
        int computedCount = config.getComputedValues().size() - baseCount;
        int activeCount = activeValues.size();
        int inactiveCount = inactiveValues.size();

        return new RegistryStats(baseCount, computedCount, activeCount, inactiveCount);
    }

    /**
     * Check if the registry is initialized
     */
    public boolean isInitialized() {
        return initialized;
    }

    /**
     * Get the config (for advanced use)
     */
    public EMCConfig getConfig() {
        return config;
    }

    /**
     * Simple stats class
     */
    public record RegistryStats(int baseValues, int computedValues, int activeValues, int inactiveValues) {
        @Override
        public String toString() {
            return String.format("Base: %d, Computed: %d, Active: %d, Inactive: %d",
                    baseValues, computedValues, activeValues, inactiveValues);
        }
    }
}