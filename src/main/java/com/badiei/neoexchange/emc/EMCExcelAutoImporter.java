package com.badiei.neoexchange.emc;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import com.badiei.neoexchange.NeoExchange;
import org.slf4j.Logger;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * EMCExcelAutoImporter - Automatically imports Excel on server startup
 *
 * IMPORTANT: This works with your mod's data directory structure:
 * - Excel source: src/main/resources/data/neoexchange/emc/emc_values.xlsx
 * - JSON output: src/main/resources/data/neoexchange/emc/emc_base_values.json
 *
 * Workflow for mod development:
 * 1. Edit emc_values.xlsx in your resources folder
 * 2. Run your mod (development or build)
 * 3. This automatically generates emc_base_values.json
 * 4. Commit both files to version control
 *
 * For packaged mods:
 * - Both Excel and JSON are packaged in the JAR
 * - The JSON is used at runtime
 * - Excel is only processed during development
 */
@EventBusSubscriber(modid = NeoExchange.MOD_ID)
public class EMCExcelAutoImporter {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Paths relative to mod resources
    private static final String RESOURCE_BASE = "data/neoexchange/emc/";
    private static final String EXCEL_FILENAME = "emc_values.xlsx";
    private static final String JSON_FILENAME = "emc_base_values.json";

    /**
     * Check if we're in a development environment
     * In dev, we can write to the resources folder
     * In production (JAR), resources are read-only
     */
    private static boolean isDevEnvironment() {
        // Check if we can find the source folder structure
        Path projectRoot = Paths.get("").toAbsolutePath();
        Path resourcesDir = projectRoot.resolve("src/main/resources");
        return Files.exists(resourcesDir) && Files.isDirectory(resourcesDir);
    }

    /**
     * Automatically import Excel file on server startup (DEV ONLY)
     *
     * This only works in development environment where we can write to files.
     * In production (when mod is packaged as JAR), this is skipped since
     * the JSON file should already be generated and packaged.
     *
     * @param event The server starting event
     */
    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        // Only run in development environment
        if (!isDevEnvironment()) {
            LOGGER.debug("Running from JAR - skipping Excel auto-import");
            LOGGER.debug("Using pre-packaged emc_base_values.json");
            return;
        }

        LOGGER.info("=== NeoExchange Excel Auto-Import (Development) ===");

        try {
            // Get paths to the actual source files
            Path projectRoot = Paths.get("").toAbsolutePath();
            Path resourcesDir = projectRoot.resolve("src/main/resources");
            Path emcDir = resourcesDir.resolve("data/neoexchange/emc");

            Path excelFile = emcDir.resolve(EXCEL_FILENAME);
            Path jsonFile = emcDir.resolve(JSON_FILENAME);

            LOGGER.info("Excel file: {}", excelFile);
            LOGGER.info("JSON file:  {}", jsonFile);

            // Check if Excel file exists
            if (!Files.exists(excelFile)) {
                LOGGER.info("No Excel file found in resources");
                LOGGER.info("If you want to use Excel, create: {}", excelFile);

                // Check if we should create a template
                if (!Files.exists(jsonFile)) {
                    LOGGER.warn("No JSON file found either!");
                    LOGGER.warn("Creating template Excel file...");

                    boolean created = EMCExcelImporter.createTemplate(emcDir.resolve("emc_values_template.xlsx"));
                    if (created) {
                        LOGGER.info("✓ Created template at: {}", emcDir.resolve("emc_values_template.xlsx"));
                        LOGGER.info("  → Rename it to 'emc_values.xlsx' to use it");
                    }
                }

                LOGGER.info("=== Excel Auto-Import Complete ===");
                return;
            }

            // Excel file exists - import it!
            LOGGER.info("Excel file found! Auto-importing...");

            boolean success = EMCExcelImporter.importFromExcel(excelFile, jsonFile);

            if (success) {
                LOGGER.info("✓ Successfully auto-imported EMC values from Excel!");
                LOGGER.info("  → Source: {}", EXCEL_FILENAME);
                LOGGER.info("  → Output: {}", JSON_FILENAME);
                LOGGER.info("  → Remember to commit both files to version control!");
            } else {
                LOGGER.error("✗ Failed to auto-import Excel file!");
                LOGGER.error("  → Check the Excel file format");
                LOGGER.error("  → See error messages above");
            }

        } catch (Exception e) {
            LOGGER.error("Error during Excel auto-import", e);
        }

        LOGGER.info("=== Excel Auto-Import Complete ===");
    }

    /**
     * Verify the JSON file is packaged correctly
     *
     * This runs in both dev and production to verify the JSON file
     * can be loaded from resources.
     */
    public static boolean verifyJsonResource() {
        String resourcePath = "/" + RESOURCE_BASE + JSON_FILENAME;
        try (InputStream stream = EMCExcelAutoImporter.class.getResourceAsStream(resourcePath)) {
            if (stream == null) {
                LOGGER.error("Could not find {} in resources!", resourcePath);
                return false;
            }
            LOGGER.debug("✓ Found {} in resources", resourcePath);
            return true;
        } catch (Exception e) {
            LOGGER.error("Error checking for JSON resource", e);
            return false;
        }
    }
}