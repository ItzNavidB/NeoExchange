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
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.slf4j.Logger;

import java.util.ArrayList;
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

    // Total slots in the grid
    private static final int TOTAL_GRID_SLOTS = GRID_COLUMNS * GRID_ROWS;  // 20 slots

    /**
     * Create the three fixed slots for the menu
     */
    public static List<Slot> createFixedSlots(
            IItemHandler inventory,
            Level level,
            SlotChangeCallback burnerCallback,
            SlotChangeCallback unlearnCallback,
            SlotChangeCallback stoneCallback
    ) {

        List<Slot> slots = new ArrayList<>();

        // Slot 0: Neo Stone slot
        slots.add(createStoneSlot(inventory, level, stoneCallback));

        // Slot 1: Burner slot
        slots.add(createBurnerSlot(inventory, level, burnerCallback));

        // Slot 2: Unlearn slot
        slots.add(createUnlearnSlot(inventory, level, unlearnCallback));

        LOGGER.debug("Created 3 fixed slots for Neo Plate menu");
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

        LOGGER.info("Creating fixed grid of {} virtual slots", TOTAL_GRID_SLOTS);

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

        LOGGER.info("Created {} virtual EMC slots for player {} on {}",
                slots.size(),
                player.getName().getString(),
                level.isClientSide() ? "CLIENT" : "SERVER");

        return slots;
    }

    /**
     * Build the list of items that should be displayed in the virtual grid
     *
     * This is where all the magic happens:
     * - Filter by affordability
     * - Filter by Neo Stone max EMC (future)
     * - Filter by search text (future)
     * - Sort by your preference
     * - Paginate (future)
     *
     * The returned list is what gets mapped to the fixed grid of slots.
     *
     * @param player The player
     * @param scrollOffset For pagination (0 = first page)
     * @param filterAffordableOnly If true, only show items player can afford
     * @return List of items to display in the grid
     */
    public static List<Item> buildDisplayList(Player player,
                                              int scrollOffset,
                                              boolean filterAffordableOnly,
                                              int maxEMC) {

        // Get ALL learned items
        List<Item> allLearnedItems = getLearnedItems(player);
        long playerBalance = EMCHelper.getBalance(player);

        // Filter and build the display list
        List<Item> displayList = new ArrayList<>();

        for (Item item : allLearnedItems) {
            long itemEMC = EMCHelper.getItemEMC(item).orElse(0L);

            // Check affordability
            if (filterAffordableOnly && playerBalance < itemEMC) {
                continue; // Skip unaffordable items
            }

            if (maxEMC < itemEMC) {
                if (!(item instanceof NeoStoneItem)) {
                    continue; // Skip unaffordable items
                }
            }
            // Future filters would go here:
            // - Neo Stone max EMC check
            // - Search text match
            // - etc.

            displayList.add(item);
        }

        // Apply pagination offset (for scrolling in future)
        int startIndex = scrollOffset * GRID_COLUMNS;
        if (startIndex >= displayList.size()) {
            // Scrolled past the end
            return new ArrayList<>();
        }

        // Return just the visible portion
        // For now, we show all (up to TOTAL_GRID_SLOTS items)
        int endIndex = Math.min(startIndex + TOTAL_GRID_SLOTS, displayList.size());
        displayList = displayList.subList(startIndex, endIndex);

        return displayList;
    }

    // ========================================
    // Private helper methods
    // ========================================

    private static Slot createStoneSlot(IItemHandler inventory,
                                        Level level,
                                        SlotChangeCallback callback) {
        return new SlotItemHandler(inventory, 0, 43, 49) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModTags.Items.USEABLE_STONES) ||
                        stack.getItem().equals(NeoItems.NEO_STONE.asItem());
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

        return new SlotItemHandler(inventory, 1, 107, 97) {
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

        return new SlotItemHandler(inventory, 2, 89, 97) {
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
}