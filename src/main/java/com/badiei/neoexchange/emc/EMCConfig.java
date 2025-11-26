package com.badiei.neoexchange.emc;

import com.google.gson.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * EMCConfig - Handles loading/saving EMC values from JSON files
 *
 * This class manages two JSON files:
 * 1. emc_base_values.json - Your manually defined values (INPUT)
 * 2. emc_computed_values.json - All calculated values (OUTPUT)
 *
 * The base values are your "anchor points" - items you manually assign
 * values to. The calculator then figures out everything else based on
 * crafting recipes, smelting, etc.
 *
 * JSON Format:
 * {
 *   "minecraft:diamond": 8192,
 *   "minecraft:iron_ingot": 256,
 *   "neoexchange:neo_stone": 512
 * }
 */
public class EMCConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()  // Makes JSON readable
            .disableHtmlEscaping() // Prevents weird character escaping
            .create();

    // File paths
    private static final String BASE_VALUES_FILE = "/data/neoexchange/emc/emc_base_values.json";
    private static final String CUSTOM_VALUES_FILE = "emc_custom_values.json";
    private static final String COMPUTED_VALUES_FILE = "emc_computed_values.json";

    // The actual data storage
    private final Map<ResourceLocation, Long> baseValues = new HashMap<>();
    private final Map<ResourceLocation, Long> computedValues = new HashMap<>();
    private final List<Item> restValues = new ArrayList<>(List.of());

    private final Path configDir;

    /**
     * Constructor
     * @param configDir The config directory (usually .minecraft/config/neoexchange/)
     */
    public EMCConfig(Path configDir) {
        this.configDir = configDir;

        // Create the config directory if it doesn't exist
        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create config directory", e);
        }
    }

    /**
     * Ensure the custom values file exists
     * Creates an empty one if it doesn't exist
     */
    private void ensureCustomFileExists() {
        Path customFile = configDir.resolve(CUSTOM_VALUES_FILE);
        if (!customFile.toFile().exists()) {
            try (Writer writer = Files.newBufferedWriter(customFile)) {
                // Create empty JSON object with a helpful comment
                writer.write("{\n");
                writer.write("  \"_comment\": \"Add your custom EMC values here. They will override mod defaults.\",\n");
                writer.write("  \"_example\": \"minecraft:diamond\": 10000\n");
                writer.write("}\n");
                LOGGER.info("Created empty emc_custom_values.json");
            } catch (IOException e) {
                LOGGER.error("Failed to create custom values file", e);
            }
        }
    }

    /**
     * Load base EMC values from mod resources and custom config
     * Loads in order: mod defaults first, then custom overrides
     *
     * @return true if successful
     */
    public boolean loadBaseValues() {
        baseValues.clear();
        
        // Step 1: Load from mod resources (packaged defaults)
        if (!loadModBaseValues()) {
            LOGGER.error("Failed to load mod base values!");
            return false;
        }
        
        // Step 2: Load custom values (user overrides)
        ensureCustomFileExists();
        loadCustomValues();
        
        LOGGER.info("Loaded total of {} EMC base values (mod + custom)", baseValues.size());
        return true;
    }
    
    /**
     * Load base values from the mod's packaged resources
     * This always loads the "official" values shipped with the mod
     */
    private boolean loadModBaseValues() {

        try (InputStream in = getClass().getResourceAsStream(BASE_VALUES_FILE)) {
            if (in == null) {
                LOGGER.error("Could not find mod base values resource: {}", BASE_VALUES_FILE);
                return false;
            }
            
            Reader reader = new InputStreamReader(in);
            JsonObject json = GSON.fromJson(reader, JsonObject.class);

            if (json == null) {
                LOGGER.error("Mod base values resource is empty or invalid JSON");
                return false;
            }

            int loadedCount = 0;
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                String itemId = entry.getKey();
                
                // Skip comments
                if (itemId.startsWith("_")) continue;

                if (!entry.getValue().isJsonPrimitive() ||
                        !entry.getValue().getAsJsonPrimitive().isNumber()) {
                    LOGGER.warn("Invalid EMC value in mod resources for {}: {}", itemId, entry.getValue());
                    continue;
                }

                long emcValue = entry.getValue().getAsLong();

                if (emcValue < 0) {
                    LOGGER.warn("Negative EMC value in mod resources for {}: {}", itemId, emcValue);
                    continue;
                }

                try {
                    ResourceLocation itemLocation = ResourceLocation.parse(itemId);
                    baseValues.put(itemLocation, emcValue);
                    loadedCount++;
                } catch (Exception e) {
                    LOGGER.warn("Invalid item ID in mod resources: {}", itemId);
                }
            }

            LOGGER.info("Loaded {} base EMC values from mod resources", loadedCount);
            return true;

        } catch (IOException e) {
            LOGGER.error("Failed to load mod base values", e);
            return false;
        } catch (JsonSyntaxException e) {
            LOGGER.error("Invalid JSON in mod base values", e);
            return false;
        }
    }
    
    /**
     * Load custom EMC values from user's config file
     * These override the mod's base values
     */
    private void loadCustomValues() {
        Path customFile = configDir.resolve(CUSTOM_VALUES_FILE);
        
        if (!Files.exists(customFile)) {
            LOGGER.info("No custom values file found");
            return;
        }
        
        try (Reader reader = Files.newBufferedReader(customFile)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);

            if (json == null) {
                LOGGER.warn("Custom values file is empty or invalid JSON");
                return;
            }

            int overrideCount = 0;
            int newCount = 0;
            
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                String itemId = entry.getKey();
                
                // Skip comments
                if (itemId.startsWith("_")) continue;

                if (!entry.getValue().isJsonPrimitive() ||
                        !entry.getValue().getAsJsonPrimitive().isNumber()) {
                    LOGGER.warn("Invalid EMC value in custom config for {}: {}", itemId, entry.getValue());
                    continue;
                }

                long emcValue = entry.getValue().getAsLong();

                if (emcValue < 0) {
                    LOGGER.warn("Negative EMC value in custom config for {}: {}", itemId, emcValue);
                    continue;
                }

                try {
                    ResourceLocation itemLocation = ResourceLocation.parse(itemId);
                    
                    // Check if this is an override or new value
                    if (baseValues.containsKey(itemLocation)) {
                        overrideCount++;
                        LOGGER.debug("Custom override: {} = {} (was {})", 
                            itemId, emcValue, baseValues.get(itemLocation));
                    } else {
                        newCount++;
                        LOGGER.debug("Custom new value: {} = {}", itemId, emcValue);
                    }
                    
                    baseValues.put(itemLocation, emcValue);
                } catch (Exception e) {
                    LOGGER.warn("Invalid item ID in custom config: {}", itemId);
                }
            }

            if (overrideCount > 0 || newCount > 0) {
                LOGGER.info("Loaded custom values: {} overrides, {} new values", 
                    overrideCount, newCount);
            } else {
                LOGGER.info("No custom values defined");
            }

        } catch (IOException e) {
            LOGGER.warn("Failed to load custom values: {}", e.getMessage());
        } catch (JsonSyntaxException e) {
            LOGGER.warn("Invalid JSON in custom values file: {}", e.getMessage());
        }
    }

    /**
     * Save a custom EMC value
     * This adds/updates a value in the custom values file
     *
     * @param item The item
     * @param value The EMC value
     * @return true if successful
     */
    public boolean saveCustomValue(Item item, long value) {
        Path customFile = configDir.resolve(CUSTOM_VALUES_FILE);
        
        try {
            // Load existing custom values
            Map<String, Long> customValues = new HashMap<>();
            
            if (Files.exists(customFile)) {
                try (Reader reader = Files.newBufferedReader(customFile)) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    if (json != null) {
                        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                            if (entry.getKey().startsWith("_")) continue; // Skip comments
                            if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isNumber()) {
                                customValues.put(entry.getKey(), entry.getValue().getAsLong());
                            }
                        }
                    }
                }
            }
            
            // Add/update the new value
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            customValues.put(itemId.toString(), value);
            
            // Write back to file
            try (Writer writer = Files.newBufferedWriter(customFile)) {
                JsonObject json = new JsonObject();
                json.addProperty("_comment", "Add your custom EMC values here. They will override mod defaults.");
                
                // Sort for readability
                List<String> sortedKeys = new ArrayList<>(customValues.keySet());
                sortedKeys.sort(String::compareTo);
                
                for (String key : sortedKeys) {
                    json.addProperty(key, customValues.get(key));
                }
                
                GSON.toJson(json, writer);
            }
            
            LOGGER.info("Saved custom EMC value: {} = {}", itemId, value);
            return true;
            
        } catch (IOException e) {
            LOGGER.error("Failed to save custom value", e);
            return false;
        }
    }

    /**
     * Load computed EMC values from JSON
     * These are the calculated values from the last calculator run
     *
     * @return true if successful
     */
    public boolean loadComputedValues() {
        Path filePath = configDir.resolve(COMPUTED_VALUES_FILE);

        if (!Files.exists(filePath)) {
            LOGGER.info("Computed values file not found, will generate on next calculation");
            return false;
        }

        try (Reader reader = Files.newBufferedReader(filePath)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);

            if (json == null) {
                LOGGER.error("Computed values file is empty or invalid JSON");
                return false;
            }

            computedValues.clear();

            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                String itemId = entry.getKey();

                if (!entry.getValue().isJsonPrimitive() ||
                        !entry.getValue().getAsJsonPrimitive().isNumber()) {
                    continue;
                }

                long emcValue = entry.getValue().getAsLong();

                try {
                    ResourceLocation itemLocation = ResourceLocation.parse(itemId);
                    computedValues.put(itemLocation, emcValue);
                } catch (Exception e) {
                    LOGGER.warn("Invalid item ID in computed values: {}", itemId);
                }
            }

            LOGGER.info("Loaded {} computed EMC values from {}",
                    computedValues.size(), COMPUTED_VALUES_FILE);
            return true;

        } catch (IOException | JsonSyntaxException e) {
            LOGGER.error("Failed to load computed values", e);
            return false;
        }
    }

    /**
     * Save computed EMC values to JSON
     * This is called after the calculator runs
     *
     * @return true if successful
     */
    public boolean saveComputedValues() {
        Path filePath = configDir.resolve(COMPUTED_VALUES_FILE);

        try (Writer writer = Files.newBufferedWriter(filePath)) {
            JsonObject json = new JsonObject();

            // Sort for readability
            List<ResourceLocation> sortedKeys = new ArrayList<>(computedValues.keySet());
            sortedKeys.sort(Comparator.comparing(ResourceLocation::toString));

            for (ResourceLocation itemLocation : sortedKeys) {
                json.addProperty(itemLocation.toString(), computedValues.get(itemLocation));
            }

            GSON.toJson(json, writer);

            LOGGER.info("Saved {} computed EMC values to {}",
                    computedValues.size(), COMPUTED_VALUES_FILE);
            return true;

        } catch (IOException e) {
            LOGGER.error("Failed to save computed values", e);
            return false;
        }
    }

    /**
     * Create default base values
     * This is your starting point - the "anchor" values
     *
     * Philosophy:
     * - Start with basic raw materials
     * - The calculator will figure out everything else from recipes
     */
    private void createDefaultBaseValues() {
        // Basic building blocks
        //baseValues.put(ResourceLocation.parse("minecraft:cobblestone"), 1L);

        // Custom mod items (examples)
        // baseValues.put(ResourceLocation.fromNamespaceAndPath(NeoExchange.MOD_ID, "neo_stone"), 512L);

        LOGGER.info("Created {} default base values", baseValues.size());
    }

    /**
     * Get a base EMC value by item
     */
    public Optional<Long> getBaseValue(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return Optional.ofNullable(baseValues.get(id));
    }

    /**
     * Get a computed EMC value by item
     */
    public Optional<Long> getComputedValue(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return Optional.ofNullable(computedValues.get(id));
    }

    /**
     * Set a base value (useful for runtime modifications)
     */
    public void setBaseValue(Item item, long value) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        baseValues.put(id, value);
    }

    /**
     * Get all base values
     */
    public Map<ResourceLocation, Long> getBaseValues() {
        return Collections.unmodifiableMap(baseValues);
    }

    /**
     * Get all computed values
     */
    public Map<ResourceLocation, Long> getComputedValues() {
        return Collections.unmodifiableMap(computedValues);
    }

    public List<Item> getRestValues() {
        return Collections.unmodifiableList(restValues);
    }

    /**
     * Set all computed values (called by the calculator)
     */
    public void setComputedValues(Map<ResourceLocation, Long> values) {
        computedValues.clear();
        computedValues.putAll(values);
    }
}