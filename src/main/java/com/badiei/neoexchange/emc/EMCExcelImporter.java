package com.badiei.neoexchange.emc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * EMCExcelImporter - Converts Excel files to JSON EMC configuration
 *
 * This class allows server admins to manage EMC values in Excel (easier to edit)
 * and automatically converts them to the JSON format used by the mod.
 *
 * Expected Excel Format:
 * ┌─────────────────────────┬───────────┬─────────────────────────┐
 * │ Item ID                 │ EMC Value │ Notes (optional)        │
 * ├─────────────────────────┼───────────┼─────────────────────────┤
 * │ minecraft:diamond       │ 8192      │ Precious gem            │
 * │ minecraft:iron_ingot    │ 256       │ Common metal            │
 * │ minecraft:gold_ingot    │ 2048      │ Rare metal              │
 * └─────────────────────────┴───────────┴─────────────────────────┘
 *
 * Features:
 * - Reads both .xls and .xlsx formats
 * - Skips empty rows automatically
 * - Validates item IDs (must contain a colon)
 * - Generates formatted JSON with proper indentation
 * - Provides detailed logging of the import process
 */
public class EMCExcelImporter {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Column indices (0-based)
    private static final int COLUMN_ITEM_ID = 0;
    private static final int COLUMN_EMC_VALUE = 1;
    private static final int COLUMN_NOTES = 2; // Optional, for documentation

    /**
     * Import EMC values from an Excel file and create/update emc_base_values.json
     *
     * This is the main entry point for Excel import functionality.
     *
     * @param excelFilePath Path to the Excel file (e.g., "config/neoexchange/emc_values.xlsx")
     * @param jsonOutputPath Path where JSON should be written (e.g., "config/neoexchange/emc_base_values.json")
     * @return true if import was successful, false otherwise
     */
    public static boolean importFromExcel(Path excelFilePath, Path jsonOutputPath) {
        LOGGER.info("Starting Excel import from: {}", excelFilePath);

        // Validate input file exists
        if (!Files.exists(excelFilePath)) {
            LOGGER.error("Excel file not found: {}", excelFilePath);
            return false;
        }

        try {
            // Read the Excel file
            Map<String, Long> emcValues = readExcelFile(excelFilePath);

            if (emcValues.isEmpty()) {
                LOGGER.warn("No valid EMC values found in Excel file");
                return false;
            }

            // Convert to JSON and write to file
            writeJsonFile(emcValues, jsonOutputPath);

            LOGGER.info("Successfully imported {} EMC values from Excel to JSON", emcValues.size());
            return true;

        } catch (Exception e) {
            LOGGER.error("Failed to import EMC values from Excel", e);
            return false;
        }
    }

    /**
     * Read Excel file directly to a Map (for direct loading without JSON conversion)
     *
     * This method is useful when you want to load EMC values directly from Excel
     * without creating an intermediate JSON file. Used by EMCConfigEnhanced.
     *
     * @param excelFilePath Path to the Excel file
     * @return Map of item IDs to EMC values
     * @throws IOException if file cannot be read
     */
    public static Map<String, Long> readExcelToMap(Path excelFilePath) throws IOException {
        LOGGER.debug("Reading Excel file directly: {}", excelFilePath);
        return readExcelFile(excelFilePath);
    }

    /**
     * Read EMC values from an Excel file
     *
     * This method:
     * 1. Opens the Excel file (works with both .xls and .xlsx)
     * 2. Reads the first sheet
     * 3. Skips the header row
     * 4. Extracts item IDs and EMC values
     * 5. Validates the data
     *
     * @param excelFilePath Path to the Excel file
     * @return Map of item ID to EMC value
     * @throws IOException if file cannot be read
     */
    private static Map<String, Long> readExcelFile(Path excelFilePath) throws IOException {
        Map<String, Long> emcValues = new LinkedHashMap<>(); // LinkedHashMap preserves insertion order

        try (FileInputStream fis = new FileInputStream(excelFilePath.toFile());
             Workbook workbook = WorkbookFactory.create(fis)) {

            // Get the first sheet (index 0)
            Sheet sheet = workbook.getSheetAt(0);
            String sheetName = sheet.getSheetName();
            LOGGER.info("Reading sheet: '{}'", sheetName);

            int totalRows = sheet.getLastRowNum();
            LOGGER.info("Found {} rows in Excel file", totalRows);

            // Start from row 1 (skip header row 0)
            int validRows = 0;
            int skippedRows = 0;

            for (int rowIndex = 1; rowIndex <= totalRows; rowIndex++) {
                Row row = sheet.getRow(rowIndex);

                // Skip null rows (empty rows in Excel)
                if (row == null) {
                    skippedRows++;
                    continue;
                }

                try {
                    // Try to read this row
                    String itemId = readItemId(row);
                    Long emcValue = readEMCValue(row);

                    // Skip rows where both cells are empty
                    if (itemId == null && emcValue == null) {
                        skippedRows++;
                        continue;
                    }

                    // Validate the data
                    if (itemId == null || itemId.trim().isEmpty()) {
                        LOGGER.warn("Row {}: Skipping - empty item ID", rowIndex + 1);
                        skippedRows++;
                        continue;
                    }

                    if (emcValue == null || emcValue <= 0) {
                        LOGGER.warn("Row {}: Skipping - invalid EMC value for '{}'", rowIndex + 1, itemId);
                        skippedRows++;
                        continue;
                    }

                    // Validate item ID format (should contain namespace like "minecraft:")
                    if (!itemId.contains(":")) {
                        LOGGER.warn("Row {}: Item ID '{}' doesn't contain namespace (e.g., 'minecraft:')",
                                rowIndex + 1, itemId);
                        skippedRows++;
                        continue;
                    }

                    // Check for duplicates
                    if (emcValues.containsKey(itemId)) {
                        LOGGER.warn("Row {}: Duplicate item ID '{}' - using newer value",
                                rowIndex + 1, itemId);
                    }

                    // Add to our map
                    emcValues.put(itemId, emcValue);
                    validRows++;

                    LOGGER.debug("Row {}: {} = {} EMC", rowIndex + 1, itemId, emcValue);

                } catch (Exception e) {
                    LOGGER.warn("Row {}: Error reading row - {}", rowIndex + 1, e.getMessage());
                    skippedRows++;
                }
            }

            LOGGER.info("Import summary: {} valid rows, {} skipped rows", validRows, skippedRows);
        }

        return emcValues;
    }

    /**
     * Read the item ID from a row (Column A)
     *
     * Handles different cell types gracefully:
     * - String cells: read directly
     * - Numeric cells: convert to string
     * - Formula cells: evaluate and read result
     */
    private static String readItemId(Row row) {
        Cell cell = row.getCell(COLUMN_ITEM_ID);
        if (cell == null) {
            return null;
        }

        // Get cell type
        CellType cellType = cell.getCellType();

        // Handle formula cells
        if (cellType == CellType.FORMULA) {
            cellType = cell.getCachedFormulaResultType();
        }

        // Read based on type
        switch (cellType) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                // Sometimes item IDs might be stored as numbers (shouldn't happen, but handle it)
                return String.valueOf((long) cell.getNumericCellValue());
            case BLANK:
                return null;
            default:
                LOGGER.warn("Unexpected cell type for item ID: {}", cellType);
                return null;
        }
    }

    /**
     * Read the EMC value from a row (Column B)
     *
     * Handles different number formats:
     * - Direct numbers: 8192
     * - Formatted numbers: 8,192
     * - Scientific notation: 8.192E3
     */
    private static Long readEMCValue(Row row) {
        Cell cell = row.getCell(COLUMN_EMC_VALUE);
        if (cell == null) {
            return null;
        }

        // Get cell type
        CellType cellType = cell.getCellType();

        // Handle formula cells
        if (cellType == CellType.FORMULA) {
            cellType = cell.getCachedFormulaResultType();
        }

        // Read based on type
        switch (cellType) {
            case NUMERIC:
                // Round to long (EMC values are whole numbers)
                return (long) cell.getNumericCellValue();
            case STRING:
                // Try to parse string as number (handles cases like "8192" stored as text)
                try {
                    // Remove commas if present (e.g., "8,192" -> "8192")
                    String value = cell.getStringCellValue().replace(",", "").trim();
                    return Long.parseLong(value);
                } catch (NumberFormatException e) {
                    LOGGER.warn("Cannot parse EMC value: '{}'", cell.getStringCellValue());
                    return null;
                }
            case BLANK:
                return null;
            default:
                LOGGER.warn("Unexpected cell type for EMC value: {}", cellType);
                return null;
        }
    }

    /**
     * Write EMC values to a JSON file with automatic backup
     *
     * Creates a properly formatted JSON file like:
     * {
     *   "values": {
     *     "minecraft:diamond": 8192,
     *     "minecraft:iron_ingot": 256
     *   }
     * }
     *
     * Safety features:
     * - Backs up existing JSON before overwriting
     * - Creates parent directories if needed
     * - Uses atomic write (write to temp, then move)
     *
     * @param emcValues Map of item IDs to EMC values
     * @param jsonOutputPath Path where JSON should be written
     * @throws IOException if file cannot be written
     */
    private static void writeJsonFile(Map<String, Long> emcValues, Path jsonOutputPath) throws IOException {
        // Create parent directories if they don't exist
        Files.createDirectories(jsonOutputPath.getParent());

        // Backup existing JSON file if it exists
        if (Files.exists(jsonOutputPath)) {
            Path backupPath = jsonOutputPath.resolveSibling(
                    jsonOutputPath.getFileName().toString().replace(".json", ".json.backup")
            );

            try {
                Files.copy(jsonOutputPath, backupPath,
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                LOGGER.info("Created backup: {}", backupPath.getFileName());
            } catch (IOException e) {
                LOGGER.warn("Failed to create backup, but continuing with import", e);
            }
        }

        // Build JSON structure
        JsonObject root = new JsonObject();
        JsonObject valuesObject = new JsonObject();

        // Add all EMC values
        for (Map.Entry<String, Long> entry : emcValues.entrySet()) {
            valuesObject.addProperty(entry.getKey(), entry.getValue());
        }

        root.add("values", valuesObject);

        // Create Gson with pretty printing
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                .create();

        // Write to temporary file first (safer)
        Path tempFile = jsonOutputPath.resolveSibling(
                jsonOutputPath.getFileName().toString() + ".tmp"
        );

        try (FileWriter writer = new FileWriter(tempFile.toFile())) {
            gson.toJson(root, writer);
        }

        // Move temp file to final location (atomic operation)
        Files.move(tempFile, jsonOutputPath,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        LOGGER.info("Wrote JSON file to: {}", jsonOutputPath);
    }

    /**
     * Create a template Excel file for users
     *
     * This generates an example Excel file that users can copy and modify.
     * Useful for first-time setup!
     *
     * @param outputPath Where to create the template
     * @return true if successful
     */
    public static boolean createTemplate(Path outputPath) {
        LOGGER.info("Creating Excel template at: {}", outputPath);

        try {
            // Create a new workbook
            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("EMC Values");

            // Create header row with styling
            Row headerRow = sheet.createRow(0);

            // Create header style (bold)
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            // Add headers
            Cell headerCell1 = headerRow.createCell(0);
            headerCell1.setCellValue("Item ID");
            headerCell1.setCellStyle(headerStyle);

            Cell headerCell2 = headerRow.createCell(1);
            headerCell2.setCellValue("EMC Value");
            headerCell2.setCellStyle(headerStyle);

            Cell headerCell3 = headerRow.createCell(2);
            headerCell3.setCellValue("Notes (Optional)");
            headerCell3.setCellStyle(headerStyle);

            // Add some example data
            addExampleRow(sheet, 1, "minecraft:diamond", 8192, "Precious gem");
            addExampleRow(sheet, 2, "minecraft:emerald", 16384, "Very rare gem");
            addExampleRow(sheet, 3, "minecraft:iron_ingot", 256, "Common metal");
            addExampleRow(sheet, 4, "minecraft:gold_ingot", 2048, "Rare metal");
            addExampleRow(sheet, 5, "minecraft:netherite_ingot", 65536, "Most valuable");

            // Auto-size columns for better readability
            sheet.autoSizeColumn(0);
            sheet.autoSizeColumn(1);
            sheet.autoSizeColumn(2);

            // Create parent directories
            Files.createDirectories(outputPath.getParent());

            // Write to file
            try (FileOutputStream fos = new FileOutputStream(outputPath.toFile())) {
                workbook.write(fos);
            }

            workbook.close();

            LOGGER.info("Successfully created Excel template with {} example rows", 5);
            return true;

        } catch (Exception e) {
            LOGGER.error("Failed to create Excel template", e);
            return false;
        }
    }

    /**
     * Helper method to add a row to the template
     */
    private static void addExampleRow(Sheet sheet, int rowIndex, String itemId, long emcValue, String notes) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(itemId);
        row.createCell(1).setCellValue(emcValue);
        row.createCell(2).setCellValue(notes);
    }
}