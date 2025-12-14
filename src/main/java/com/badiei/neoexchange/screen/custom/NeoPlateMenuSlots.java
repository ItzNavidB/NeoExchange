package com.badiei.neoexchange.screen.custom;

import com.badiei.neoexchange.emc.EMCHelper;
import com.badiei.neoexchange.emc.EMCRegistry;
import com.badiei.neoexchange.emc.PlayerEMCData;
import com.badiei.neoexchange.items.NeoItems;
import com.badiei.neoexchange.datagen.ModTags;
import com.badiei.neoexchange.items.NeoStoneItem;
import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * NeoPlateMenuSlots - Factory class for creating all slot types used in Neo Plate
 *
 * This class creates:
 * - 3 fixed slots (stone, burn, unlearn)
 * - A FIXED GRID of virtual slots (e.g., 20 slots for a 4×5 grid)
 *
 * The virtual slots don't "know" which items they display.
 * They're just windows into a filtered/sorted list that gets updated dynamically!
 */
public class NeoPlateMenuSlots {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Grid layout constants
    private static final int GRID_START_X = 150;
    private static final int GRID_START_Y = 20;
    private static final int GRID_COLUMNS = 4;
    private static final int GRID_ROWS = 5;  // How many rows to show
    private static final int SLOT_SIZE = 18;

    private static int DISPLAY_LIST_SIZE = 0;

    // Total slots in the grid
    private static final int TOTAL_GRID_SLOTS = GRID_COLUMNS * GRID_ROWS;  // 20 slots

    /**
     * Create the three fixed slots for the menu
     */
    public static List<Slot> createFixedSlots(
            IItemHandler inventory,
            Level level,
            SlotChangeCallback burnerCallback,
            SlotChangeCallback learnCallback,
            SlotChangeCallback unlearnCallback,
            SlotChangeCallback stoneCallback,
            SlotChangeCallback templateCallback
    ) {

        List<Slot> slots = new ArrayList<>();

        // Slot 0: Neo Stone slot
        slots.add(createStoneSlot(inventory, level, stoneCallback));

        // Slot 1: Burner slot
        slots.add(createBurnerSlot(inventory, level, burnerCallback));

        // Slot 2: Learn slot
        slots.add(createLearnSlot(inventory, level, learnCallback));

        // Slot 3: Unlearn slot
        slots.add(createUnlearnSlot(inventory, level, unlearnCallback));

        // Slot 4: Template slot
        slots.add(createTemplateSlot(inventory, level, templateCallback));

        return slots;
    }

    /**
     * Create a FIXED GRID of virtual EMC slots
     *
     * This creates exactly TOTAL_GRID_SLOTS slots (e.g., 20 for a 4×5 grid).
     * These slots start empty and will be populated by updateVirtualSlots().
     *
     * Key difference from old approach:
     * - OLD: Create N slots for N learned items
     * - NEW: Create fixed 20 slots, map items to them dynamically
     *
     * @param player The player using the menu
     * @param level The level (for client/server checks)
     * @return List of exactly TOTAL_GRID_SLOTS virtual slots
     */
    public static List<Slot> createVirtualSlots(Player player, Level level) {
        List<Slot> slots = new ArrayList<>();

        //LOGGER.info("Creating fixed grid of {} virtual slots", TOTAL_GRID_SLOTS);     // Debug log

        // Create a fixed grid of slots
        for (int i = 0; i < TOTAL_GRID_SLOTS; i++) {
            // Calculate grid position
            int row = i / GRID_COLUMNS;
            int col = i % GRID_COLUMNS;
            int x = GRID_START_X + (col * SLOT_SIZE);
            int y = GRID_START_Y + (row * SLOT_SIZE);


            // Create an empty virtual slot
            // The slot knows its index (0-19) but starts with no item
            slots.add(new VirtualEMCSlot(player, i, x, y));
        }

        /*LOGGER.info("Created {} virtual EMC slots for player {} on {}",
                slots.size(),
                player.getName().getString(),
                level.isClientSide() ? "CLIENT" : "SERVER");*/      // Debug log

        return slots;
    }

    /**
     * Build the list of items to display in the virtual grid
     *
     * NEW FEATURES:
     * - Template item appears FIRST (if affordable)
     * - Favorites appear second (gold star icon)
     * - Similar items appear third
     * - Search still works
     * - Scrolling pages through results
     *
     * Sorting priority:
     * 1. Template item itself (always first if it exists!)
     * 2. Favorited items (by EMC, highest first)
     * 3. Items similar to template (by similarity score)
     * 4. All other learned items (by EMC, highest first)
     *
     * @param player The player
     * @param scrollOffset Row offset for pagination (0 = first page)
     * @param filterAffordableOnly Show only affordable items?
     * @param maxEMC Maximum EMC based on Neo Stone tier
     * @param searchText Search filter (empty = show all)
     * @param templateItem Template filter item (null = no filter)
     * @return List of items to display, properly sorted and filtered
     */
    public static List<Item> buildDisplayList(Player player,
                                              int scrollOffset,
                                              boolean filterAffordableOnly,
                                              int maxEMC,
                                              String searchText,
                                              Item templateItem) {

        // Get all learned items
        PlayerEMCData emcData = EMCHelper.getPlayerEMC(player);
        List<Item> allLearnedItems = getLearnedItems(player);
        long playerBalance = EMCHelper.getBalance(player);

        // Separate items into categories for sorting
        Item exactTemplateMatch = null;  // NEW: Track the exact template item
        List<Item> favorites = new ArrayList<>();
        List<Item> similarToTemplate = new ArrayList<>();
        List<Item> others = new ArrayList<>();

        for (Item item : allLearnedItems) {
            long itemEMC = EMCHelper.getItemEMC(item).orElse(0L);

            // Filter 1: Check affordability
            if (filterAffordableOnly && playerBalance < itemEMC) {
                continue;
            }

            // Filter 2: Check Neo Stone max EMC
            if (maxEMC < itemEMC && !(item instanceof NeoStoneItem)) {
                continue;
            }

            // Filter 3: Check search text match
            if (searchText != null && !searchText.isEmpty()) {
                String itemName = new ItemStack(item).getHoverName().getString().toLowerCase();
                if (!itemName.contains(searchText)) {
                    continue;
                }
            }

            // Item passed all filters - now categorize it!

            // NEW: Check if this is the EXACT template item
            if (templateItem != null && item == templateItem) {
                exactTemplateMatch = item;  // Save it for first position!
                continue;  // Don't add to other lists yet
            }

            if (emcData.isFavorited(item)) {
                // This is a favorite!
                favorites.add(item);
            } else if (templateItem != null && isSimilarTo(item, templateItem)) {
                // This is similar to the template
                similarToTemplate.add(item);
            } else {
                // Regular item
                others.add(item);
            }
        }

        // Sort each category by EMC (highest first)
        sortByEMC(favorites);
        sortByEMC(similarToTemplate);
        sortByEMC(others);

        // Combine in priority order
        List<Item> combinedList = new ArrayList<>();
        
        // NEW: Template item goes FIRST if it exists and player can afford it!
        if (exactTemplateMatch != null) {
            combinedList.add(exactTemplateMatch);
        }
        
        combinedList.addAll(favorites);           // Favorites second
        combinedList.addAll(similarToTemplate);   // Similar items third
        combinedList.addAll(others);              // Everything else last

        // Apply pagination (scrolling)
        int startIndex = scrollOffset * GRID_COLUMNS;
        if (startIndex >= combinedList.size()) {
            return new ArrayList<>();  // Scrolled past the end
        }

        DISPLAY_LIST_SIZE = combinedList.size();
        int endIndex = Math.min(startIndex + TOTAL_GRID_SLOTS, combinedList.size());
        return combinedList.subList(startIndex, endIndex);
    }

    public static int getDisplayListSize() {
        return DISPLAY_LIST_SIZE;
    }


    public static int buildDisplayListSize(Player player,
                                                  int scrollOffset,
                                                  boolean filterAffordableOnly,
                                                  int maxEMC,
                                                  String searchText,
                                                  Item templateItem) {

        // Get all learned items
        PlayerEMCData emcData = EMCHelper.getPlayerEMC(player);
        List<Item> allLearnedItems = getLearnedItems(player);
        long playerBalance = EMCHelper.getBalance(player);

        // Separate items into categories for sorting
        Item exactTemplateMatch = null;  // NEW: Track the exact template item
        List<Item> favorites = new ArrayList<>();
        List<Item> similarToTemplate = new ArrayList<>();
        List<Item> others = new ArrayList<>();

        for (Item item : allLearnedItems) {
            long itemEMC = EMCHelper.getItemEMC(item).orElse(0L);

            // Filter 1: Check affordability
            if (filterAffordableOnly && playerBalance < itemEMC) {
                continue;
            }

            // Filter 2: Check Neo Stone max EMC
            if (maxEMC < itemEMC && !(item instanceof NeoStoneItem)) {
                continue;
            }

            // Filter 3: Check search text match
            if (searchText != null && !searchText.isEmpty()) {
                String itemName = new ItemStack(item).getHoverName().getString().toLowerCase();
                if (!itemName.contains(searchText)) {
                    continue;
                }
            }

            // Item passed all filters - now categorize it!

            // NEW: Check if this is the EXACT template item
            if (templateItem != null && item == templateItem) {
                exactTemplateMatch = item;  // Save it for first position!
                continue;  // Don't add to other lists yet
            }

            if (emcData.isFavorited(item)) {
                // This is a favorite!
                favorites.add(item);
            } else if (templateItem != null && isSimilarTo(item, templateItem)) {
                // This is similar to the template
                similarToTemplate.add(item);
            } else {
                // Regular item
                others.add(item);
            }
        }

        // Sort each category by EMC (highest first)
        sortByEMC(favorites);
        sortByEMC(similarToTemplate);
        sortByEMC(others);

        // Combine in priority order
        List<Item> combinedList = new ArrayList<>();
        
        // NEW: Template item goes FIRST if it exists!
        if (exactTemplateMatch != null) {
            combinedList.add(exactTemplateMatch);
        }
        
        combinedList.addAll(favorites);           // Favorites second
        combinedList.addAll(similarToTemplate);   // Similar items third
        combinedList.addAll(others);              // Everything else last

        // Apply pagination (scrolling)
        int startIndex = scrollOffset * GRID_COLUMNS;
        if (startIndex >= combinedList.size()) {
            return 0;  // Scrolled past the end
        }

        return combinedList.size();
    }

    /**
     * Check if two items are "similar"
     *
     * Similarity criteria (in order of priority):
     * 1. Exact same item → 100% similar
     * 2. Same mod source (e.g., both from "minecraft") → Similar
     * 3. Share item tags (e.g., both are "minecraft:planks") → Similar
     * 4. Same item class (e.g., both SwordItem) → Somewhat similar
     *
     * @param item1 First item
     * @param item2 Second item (template)
     * @return true if items are similar
     */
    private static boolean isSimilarTo(Item item1, Item item2) {
        // Same item?
        if (item1 == item2) {
            return true;
        }

        // Get resource locations
        ResourceLocation id1 = BuiltInRegistries.ITEM.getKey(item1);
        ResourceLocation id2 = BuiltInRegistries.ITEM.getKey(item2);

        // Same mod? (e.g., both from "minecraft" or "neoexchange")
        if (id1.getNamespace().equals(id2.getNamespace())) {
            return true;
        }

        // Share any item tags?
        // Example: Diamond Pickaxe and Iron Pickaxe both have tag "minecraft:pickaxes"
        Set<net.minecraft.tags.TagKey<Item>> tags1 = getTags(item1);
        Set<net.minecraft.tags.TagKey<Item>> tags2 = getTags(item2);

        for (net.minecraft.tags.TagKey<Item> tag : tags1) {
            if (tags2.contains(tag)) {
                return true;  // Shared tag found!
            }
        }

        // Same item class? (e.g., both are SwordItem)
        if (item1.getClass() == item2.getClass()) {
            return true;
        }

        return false;  // Not similar
    }

    /**
     * Get all tags for an item (NeoForge 1.21.10 compatible)
     *
     * @param item The item to get tags for
     * @return Set of tag keys this item belongs to
     */
    private static Set<net.minecraft.tags.TagKey<Item>> getTags(Item item) {
        Set<net.minecraft.tags.TagKey<Item>> tags = new HashSet<>();

        // Get the item's holder from the registry
        net.minecraft.core.Holder<Item> holder = BuiltInRegistries.ITEM.wrapAsHolder(item);

        // Get all tags this item belongs to
        holder.tags().forEach(tags::add);

        return tags;
    }

    /**
     * Sort items by EMC value (highest first)
     */
    private static void sortByEMC(List<Item> items) {
        items.sort((a, b) -> {
            long emcA = EMCHelper.getItemEMC(a).orElse(0L);
            long emcB = EMCHelper.getItemEMC(b).orElse(0L);
            return Long.compare(emcB, emcA);  // Descending
        });
    }

    // ========================================
    // Private helper methods
    // ========================================

    public static int getStoneX() {return 53;}
    public static int getStoneY() {return 47;}

    private static Slot createStoneSlot(IItemHandler inventory,
                                        Level level,
                                        SlotChangeCallback callback) {
        return new SlotItemHandler(inventory, 0, getStoneX(), getStoneY()) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModTags.Items.USEABLE_STONES);
            }

            @Override
            public void setChanged() {
                super.setChanged();
                if (!level.isClientSide()) {
                    ItemStack item = this.getItem();
                    callback.onSlotChanged(item);
                }
            }
        };
    }

    private static Slot createBurnerSlot(
            IItemHandler inventory,
            Level level,
            SlotChangeCallback callback) {

        return new SlotItemHandler(inventory, 1, 53, 97) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return EMCRegistry.getInstance().hasEMC(stack.getItem());
            }

            @Override
            public void setChanged() {
                super.setChanged();
                if (!level.isClientSide()) {
                    ItemStack item = this.getItem();
                    callback.onSlotChanged(item);
                }
            }
        };
    }

    private static Slot createLearnSlot(
            IItemHandler inventory,
            Level level,
            SlotChangeCallback callback) {

        return new SlotItemHandler(inventory, 2, 71, 97) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return EMCRegistry.getInstance().hasEMC(stack.getItem());
            }

            @Override
            public void setChanged() {
                super.setChanged();
                if (!level.isClientSide()) {
                    ItemStack item = this.getItem();
                    callback.onSlotChanged(item);
                }
            }
        };
    }

    private static Slot createUnlearnSlot(
            IItemHandler inventory,
            Level level,
            SlotChangeCallback callback) {

        return new SlotItemHandler(inventory, 3, 35, 97) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return EMCRegistry.getItemsWithEMC().contains(stack.getItem());
            }

            @Override
            public void setChanged() {
                super.setChanged();
                if (!level.isClientSide()) {
                    ItemStack item = this.getItem();
                    callback.onSlotChanged(item);
                }
            }
        };
    }

    private static Slot createTemplateSlot(
            IItemHandler inventory,
            Level level,
            SlotChangeCallback callback) {

        return new SlotItemHandler(inventory, 4, 125, 49) {  // Position: 125, 49
            @Override
            public boolean mayPlace(ItemStack stack) {
                // Allow any item that has EMC value
                return EMCRegistry.getInstance().hasEMC(stack.getItem());
            }

            @Override
            public void setChanged() {
                super.setChanged();
                if (!level.isClientSide()) {
                    ItemStack item = this.getItem();
                    callback.onSlotChanged(item);
                }
            }

            @Override
            public int getMaxStackSize() {
                return 1;  // Only 1 item at a time
            }
        };
    }

    /**
     * Get list of ALL learned items for the player, sorted by EMC value
     *
     * This is the "master list" before any filtering.
     * buildDisplayList() uses this as the source.
     */
    private static List<Item> getLearnedItems(Player player) {
        PlayerEMCData emcData = EMCHelper.getPlayerEMC(player);
        Set<ResourceLocation> learnedItemIds = emcData.getLearnedItems();

        // Convert ResourceLocations to Items and filter invalid
        List<Item> learnedItems = new ArrayList<>();
        for (ResourceLocation itemId : learnedItemIds) {
            Item item = BuiltInRegistries.ITEM.get(itemId).get().value();
            if (item != null && item != Items.AIR) {
                learnedItems.add(item);
            }
        }

        // Sort by EMC value (highest first)
        // This makes expensive items appear at the top by default
        learnedItems.sort((a, b) -> {
            long emcA = EMCHelper.getItemEMC(a).orElse(0L);
            long emcB = EMCHelper.getItemEMC(b).orElse(0L);
            return Long.compare(emcB, emcA); // Descending
        });

        return learnedItems;
    }

    // ========================================
    // Callback interface
    // ========================================

    @FunctionalInterface
    public interface SlotChangeCallback {
        void onSlotChanged(ItemStack stack);
    }

    // ========================================
    // Grid configuration getters
    // ========================================

    public static int getGridStartX() {
        return GRID_START_X;
    }

    public static int getGridStartY() {
        return GRID_START_Y;
    }

    public static int getGridColumns() {
        return GRID_COLUMNS;
    }

    public static int getGridRows() {
        return GRID_ROWS;
    }

    public static int getTotalGridSlots() {
        return TOTAL_GRID_SLOTS;
    }

    public static int getSlotSize() {
        return SLOT_SIZE;
    }

    /**
     * Create the template slot - a special slot for filtering by similarity
     *
     * How it works:
     * 1. Player places item in this slot
     * 2. Item is automatically learned (without burning!)
     * 3. Grid sorts to show template item FIRST, then similar items
     *
     * @param inventory The block entity's inventory
     * @param level The game level
     * @param callback Called when slot contents change
     * @return The template slot
     */

}
