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
import java.nio.file.Paths;
import java.util.*;

/**
 * EMCConfig - Flexible EMC config loader with Excel support
 *
 * This class supports MULTIPLE loading methods:
 * 1. JSON_ONLY: Traditional JSON loading (fastest, production)
 * 2. EXCEL_DIRECT: Load directly from Excel (development, real-time)
 * 3. EXCEL_WITH_CACHE: Load from Excel, cache to JSON (balanced)
 * 4. AUTO: Automatically choose best mode (recommended!)
 *
 * Change the LOAD_MODE constant below to switch between modes.
 *
 * Files managed:
 * - emc_base_values.json - Base values (packaged in mod)
 * - emc_custom_values.json - User overrides (config directory)
 * - emc_computed_values.json - Calculated values (config directory)
 * - emc_values.xlsx - Excel source (optional, for development)
 */
public class EMCConfig {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    // ============= CONFIGURATION =============
    /**
     * Change this to switch loading modes!
     * 
     * - JSON_ONLY: Use JSON only (fastest, production)
     * - EXCEL_DIRECT: Load from Excel every time (development)
     * - EXCEL_WITH_CACHE: Load Excel, cache to JSON (balanced)
     * - AUTO: Automatically choose best mode (recommended!)
     */
    private static final LoadMode LOAD_MODE = LoadMode.EXCEL_DIRECT;
    // =========================================

    /**
     * Loading mode enum
     */
    public enum LoadMode {
        /** Load only from JSON (fastest, production) */
        JSON_ONLY,
        
        /** Load directly from Excel every time (real-time updates) */
        EXCEL_DIRECT,
        
        /** Load from Excel, save to JSON for caching (balanced) */
        EXCEL_WITH_CACHE,
        
        /** Automatically choose best mode based on environment */
        AUTO
    }

    // File paths
    private static final String BASE_VALUES_JSON = "/data/neoexchange/emc/emc_base_values.json";
    private static final String EXCEL_FILENAME = "emc_values.xlsx";
    private static final String CUSTOM_VALUES_FILE = "emc_custom_values.json";
    private static final String COMPUTED_VALUES_FILE = "emc_computed_values.json";

    // Data storage
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

        try {
            Files.createDirectories(configDir);
        } catch (IOException e) {
            LOGGER.error("Failed to create config directory", e);
        }
    }

    /**
     * Load base EMC values using the configured mode
     * 
     * This automatically uses the right loading method based on LOAD_MODE.
     * After loading base values, custom overrides are always applied.
     *
     * @return true if successful
     */
    public boolean loadBaseValues() {
        baseValues.clear();
        
        LoadMode actualMode = determineLoadMode();
        LOGGER.info("Loading EMC values using mode: {}", actualMode);
        
        boolean success = false;
        
        switch (actualMode) {
            case JSON_ONLY:
                success = loadFromJSON();
                break;
                
            case EXCEL_DIRECT:
                success = loadFromExcelDirect();
                if (!success) {
                    LOGGER.warn("Excel loading failed, falling back to JSON");
                    success = loadFromJSON();
                }
                break;
                
            case EXCEL_WITH_CACHE:
                success = loadFromExcelWithCache();
                if (!success) {
                    LOGGER.warn("Excel loading failed, falling back to JSON");
                    success = loadFromJSON();
                }
                break;
                
            default:
                LOGGER.error("Unknown load mode: {}", actualMode);
                success = loadFromJSON();
        }
        
        if (success) {
            // Always load custom overrides after base values
            ensureCustomFileExists();
            loadCustomValues();
            LOGGER.info("Loaded total of {} EMC base values", baseValues.size());
        }
        
        return success;
    }
    
    /**
     * Determine which load mode to actually use
     * Implements the AUTO mode logic
     */
    private LoadMode determineLoadMode() {
        if (LOAD_MODE != LoadMode.AUTO) {
            return LOAD_MODE;
        }
        
        // AUTO mode - intelligent detection
        LOGGER.info("AUTO mode: Detecting best loading method...");
        
        boolean isDev = isDevEnvironment();
        Path excelPath = getExcelPath();
        boolean excelExists = excelPath != null && Files.exists(excelPath);
        
        // In development with Excel? Use direct loading for instant updates
        if (isDev && excelExists) {
            LOGGER.info("  → Development + Excel exists: Using EXCEL_DIRECT");
            return LoadMode.EXCEL_DIRECT;
        }
        
        // Excel exists and is newer than JSON? Re-import it
        if (excelExists) {
            Path jsonPath = getJSONPath();
            if (jsonPath != null && Files.exists(jsonPath)) {
                try {
                    long excelTime = Files.getLastModifiedTime(excelPath).toMillis();
                    long jsonTime = Files.getLastModifiedTime(jsonPath).toMillis();
                    
                    if (excelTime > jsonTime) {
                        LOGGER.info("  → Excel newer than JSON: Using EXCEL_WITH_CACHE");
                        return LoadMode.EXCEL_WITH_CACHE;
                    }
                } catch (IOException e) {
                    LOGGER.warn("Failed to check file times", e);
                }
            }
        }
        
        // Default: Use JSON (fastest)
        LOGGER.info("  → Using JSON_ONLY (fastest)");
        return LoadMode.JSON_ONLY;
    }
    
    /**
     * Get path to Excel file (if exists in resources or config)
     */
    private Path getExcelPath() {
        // Check development resources first
        Path projectRoot = Paths.get("").toAbsolutePath();
        Path devPath = projectRoot.resolve("src/main/resources/data/neoexchange/emc/" + EXCEL_FILENAME);
        
        if (Files.exists(devPath)) {
            return devPath;
        }
        
        // Check config directory
        Path configPath = configDir.resolve(EXCEL_FILENAME);
        if (Files.exists(configPath)) {
            return configPath;
        }
        
        return null;
    }
    
    /**
     * Get path to JSON file (if exists in resources)
     */
    private Path getJSONPath() {
        Path projectRoot = Paths.get("").toAbsolutePath();
        Path devPath = projectRoot.resolve("src/main/resources/data/neoexchange/emc/emc_base_values.json");
        
        if (Files.exists(devPath)) {
            return devPath;
        }
        
        return null;
    }
    
    /**
     * Check if we're in development environment
     */
    private boolean isDevEnvironment() {
        Path projectRoot = Paths.get("").toAbsolutePath();
        Path resourcesDir = projectRoot.resolve("src/main/resources");
        return Files.exists(resourcesDir) && Files.isDirectory(resourcesDir);
    }

    /**
     * LOADING METHOD 1: Load from JSON
     * Traditional loading from JSON resources (fastest)
     */
    private boolean loadFromJSON() {
        LOGGER.info("Loading from JSON...");
        
        try (InputStream in = getClass().getResourceAsStream(BASE_VALUES_JSON)) {
            if (in == null) {
                LOGGER.error("Could not find JSON resource: {}", BASE_VALUES_JSON);
                return false;
            }
            
            Reader reader = new InputStreamReader(in);
            JsonObject json = GSON.fromJson(reader, JsonObject.class);

            if (json == null) {
                LOGGER.error("JSON resource is empty or invalid");
                return false;
            }

            // Handle both old format (flat) and new format (with "values" key)
            JsonObject values = json.has("values") ? json.getAsJsonObject("values") : json;
            int loadedCount = parseJSONValues(values);
            
            LOGGER.info("✓ Loaded {} values from JSON", loadedCount);
            return true;

        } catch (Exception e) {
            LOGGER.error("Failed to load from JSON", e);
            return false;
        }
    }

    /**
     * LOADING METHOD 2: Load directly from Excel
     * Load from Excel file in real-time (see changes immediately)
     */
    private boolean loadFromExcelDirect() {
        LOGGER.info("Loading directly from Excel...");
        
        Path excelPath = getExcelPath();
        if (excelPath == null) {
            LOGGER.warn("Excel file not found");
            return false;
        }
        
        try {
            LOGGER.info("Reading Excel: {}", excelPath);
            
            // Use EMCExcelImporter to read the Excel file
            Map<String, Long> excelValues = EMCExcelImporter.readExcelToMap(excelPath);
            
            if (excelValues.isEmpty()) {
                LOGGER.warn("No values found in Excel");
                return false;
            }
            
            // Convert to ResourceLocation map
            int loadedCount = 0;
            for (Map.Entry<String, Long> entry : excelValues.entrySet()) {
                try {
                    ResourceLocation itemLocation = ResourceLocation.parse(entry.getKey());
                    baseValues.put(itemLocation, entry.getValue());
                    loadedCount++;
                } catch (Exception e) {
                    LOGGER.warn("Invalid item ID from Excel: {}", entry.getKey());
                }
            }
            
            LOGGER.info("✓ Loaded {} values directly from Excel", loadedCount);
            return true;
            
        } catch (Exception e) {
            LOGGER.error("Failed to load from Excel", e);
            return false;
        }
    }

    /**
     * LOADING METHOD 3: Load from Excel with JSON caching
     * Load from Excel, then save to JSON for next time (best of both worlds)
     */
    private boolean loadFromExcelWithCache() {
        LOGGER.info("Loading from Excel with caching...");
        
        Path excelPath = getExcelPath();
        if (excelPath == null) {
            LOGGER.warn("Excel file not found");
            return false;
        }
        
        Path jsonPath = getJSONPath();
        if (jsonPath == null) {
            jsonPath = Paths.get("").toAbsolutePath()
                .resolve("src/main/resources/data/neoexchange/emc/emc_base_values.json");
        }
        
        try {
            LOGGER.info("Reading Excel: {}", excelPath);
            LOGGER.info("Will cache to: {}", jsonPath);
            
            // Import Excel to JSON
            boolean importSuccess = EMCExcelImporter.importFromExcel(excelPath, jsonPath);
            
            if (!importSuccess) {
                LOGGER.error("Failed to import Excel");
                return false;
            }
            
            // Now load the newly created JSON
            boolean loadSuccess = loadFromJSON();
            
            if (loadSuccess) {
                LOGGER.info("✓ Loaded from Excel and cached to JSON");
            }
            
            return loadSuccess;
            
        } catch (Exception e) {
            LOGGER.error("Failed to load from Excel with cache", e);
            return false;
        }
    }

    /**
     * Parse JSON values object into baseValues map
     */
    private int parseJSONValues(JsonObject values) {
        int loadedCount = 0;
        
        for (Map.Entry<String, JsonElement> entry : values.entrySet()) {
            String itemId = entry.getKey();
            
            // Skip comments
            if (itemId.startsWith("_")) continue;

            if (!entry.getValue().isJsonPrimitive() ||
                    !entry.getValue().getAsJsonPrimitive().isNumber()) {
                LOGGER.warn("Invalid EMC value for {}", itemId);
                continue;
            }

            long emcValue = entry.getValue().getAsLong();

            if (emcValue < 0) {
                LOGGER.warn("Negative EMC value for {}", itemId);
                continue;
            }

            try {
                ResourceLocation itemLocation = ResourceLocation.parse(itemId);
                baseValues.put(itemLocation, emcValue);
                loadedCount++;
            } catch (Exception e) {
                LOGGER.warn("Invalid item ID: {}", itemId);
            }
        }
        
        return loadedCount;
    }

    /**
     * Ensure the custom values file exists
     */
    private void ensureCustomFileExists() {
        Path customFile = configDir.resolve(CUSTOM_VALUES_FILE);
        if (!customFile.toFile().exists()) {
            try (Writer writer = Files.newBufferedWriter(customFile)) {
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
     * Load custom EMC values from user's config file
     * These override the mod's base values
     */
    private void loadCustomValues() {
        Path customFile = configDir.resolve(CUSTOM_VALUES_FILE);
        
        if (!Files.exists(customFile)) {
            return;
        }
        
        try (Reader reader = Files.newBufferedReader(customFile)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);

            if (json == null) {
                return;
            }

            int overrideCount = 0;
            int newCount = 0;
            
            for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                String itemId = entry.getKey();
                
                if (itemId.startsWith("_")) continue;

                if (!entry.getValue().isJsonPrimitive() ||
                        !entry.getValue().getAsJsonPrimitive().isNumber()) {
                    continue;
                }

                long emcValue = entry.getValue().getAsLong();

                try {
                    ResourceLocation itemLocation = ResourceLocation.parse(itemId);
                    
                    if (baseValues.containsKey(itemLocation)) {
                        overrideCount++;
                    } else {
                        newCount++;
                    }
                    
                    baseValues.put(itemLocation, emcValue);
                } catch (Exception e) {
                    LOGGER.warn("Invalid item ID in custom config: {}", itemId);
                }
            }

            if (overrideCount > 0 || newCount > 0) {
                LOGGER.info("Custom values: {} overrides, {} new", overrideCount, newCount);
            }

        } catch (Exception e) {
            LOGGER.warn("Failed to load custom values: {}", e.getMessage());
        }
    }

    /**
     * Save a custom EMC value
     */
    public boolean saveCustomValue(Item item, long value) {
        Path customFile = configDir.resolve(CUSTOM_VALUES_FILE);
        
        try {
            Map<String, Long> customValues = new HashMap<>();
            
            if (Files.exists(customFile)) {
                try (Reader reader = Files.newBufferedReader(customFile)) {
                    JsonObject json = GSON.fromJson(reader, JsonObject.class);
                    if (json != null) {
                        for (Map.Entry<String, JsonElement> entry : json.entrySet()) {
                            if (entry.getKey().startsWith("_")) continue;
                            if (entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isNumber()) {
                                customValues.put(entry.getKey(), entry.getValue().getAsLong());
                            }
                        }
                    }
                }
            }
            
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
            customValues.put(itemId.toString(), value);
            
            try (Writer writer = Files.newBufferedWriter(customFile)) {
                JsonObject json = new JsonObject();
                json.addProperty("_comment", "Add your custom EMC values here. They will override mod defaults.");
                
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
     */
    public boolean loadComputedValues() {
        Path filePath = configDir.resolve(COMPUTED_VALUES_FILE);

        if (!Files.exists(filePath)) {
            return false;
        }

        try (Reader reader = Files.newBufferedReader(filePath)) {
            JsonObject json = GSON.fromJson(reader, JsonObject.class);

            if (json == null) {
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
                    LOGGER.warn("Invalid item ID: {}", itemId);
                }
            }

            LOGGER.info("Loaded {} computed values", computedValues.size());
            return true;

        } catch (Exception e) {
            LOGGER.error("Failed to load computed values", e);
            return false;
        }
    }

    /**
     * Save computed EMC values to JSON
     */
    public boolean saveComputedValues() {
        Path filePath = configDir.resolve(COMPUTED_VALUES_FILE);

        try (Writer writer = Files.newBufferedWriter(filePath)) {
            JsonObject json = new JsonObject();

            List<ResourceLocation> sortedKeys = new ArrayList<>(computedValues.keySet());
            sortedKeys.sort(Comparator.comparing(ResourceLocation::toString));

            for (ResourceLocation itemLocation : sortedKeys) {
                json.addProperty(itemLocation.toString(), computedValues.get(itemLocation));
            }

            GSON.toJson(json, writer);
            LOGGER.info("Saved {} computed values", computedValues.size());
            return true;

        } catch (IOException e) {
            LOGGER.error("Failed to save computed values", e);
            return false;
        }
    }

    // ============= Getters/Setters =============

    public Optional<Long> getBaseValue(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return Optional.ofNullable(baseValues.get(id));
    }

    public Optional<Long> getComputedValue(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return Optional.ofNullable(computedValues.get(id));
    }

    public void setBaseValue(Item item, long value) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        baseValues.put(id, value);
    }

    public Map<ResourceLocation, Long> getBaseValues() {
        return Collections.unmodifiableMap(baseValues);
    }

    public Map<ResourceLocation, Long> getComputedValues() {
        return Collections.unmodifiableMap(computedValues);
    }

    public List<Item> getRestValues() {
        return Collections.unmodifiableList(restValues);
    }

    public void setComputedValues(Map<ResourceLocation, Long> values) {
        computedValues.clear();
        computedValues.putAll(values);
    }
}
