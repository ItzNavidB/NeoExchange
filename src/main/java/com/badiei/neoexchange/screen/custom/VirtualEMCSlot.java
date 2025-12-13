package com.badiei.neoexchange.screen.custom;

import com.badiei.neoexchange.emc.EMCHelper;
import com.badiei.neoexchange.emc.PlayerEMCData;
import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

/**
 * VirtualEMCSlot - A slot that displays an item from a dynamic list
 *
 * This is a "view" into a filtered/sorted list of items.
 * The slot doesn't know which specific item it displays - it just shows
 * whatever item is at its index in the current list.
 *
 * Think of it like a window into a scrollable list:
 * - Slot 0 shows list[0]
 * - Slot 1 shows list[1]
 * - etc.
 *
 * When the list changes (filtering, sorting, pagination), the slots
 * automatically update to show the new items at their positions!
 */
public class VirtualEMCSlot extends Slot {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final int listIndex;  // Which position in the list this slot represents
    private final Player player;

    // Current state (cached for performance)
    private Item currentItem = Items.AIR;
    private int currentAmount = 0;

    // Callback for EMC spending events
    private EMCSpentCallback emcSpentCallback = null;

    /**
     * Callback interface for EMC spending events
     * This allows the menu to be notified when EMC is spent
     */
    public interface EMCSpentCallback {
        void onEMCSpent(long amount, String itemName, int count);
    }

    /**
     * Purchase result - contains info about the transaction
     */
    public static class PurchaseResult {
        public final boolean success;
        public final long emcSpent;
        public final int itemCount;
        public final String itemName;

        public PurchaseResult(boolean success, long emcSpent, int itemCount, String itemName) {
            this.success = success;
            this.emcSpent = emcSpent;
            this.itemCount = itemCount;
            this.itemName = itemName;
        }
    }

    /**
     * Create a virtual EMC slot that shows items from a list
     *
     * @param player The player using this slot
     * @param listIndex Which index in the item list this slot displays (0-19 for a 4×5 grid)
     * @param x X position in the GUI
     * @param y Y position in the GUI
     */
    public VirtualEMCSlot(Player player, int listIndex, int x, int y) {
        // Create a dummy container - we manage items ourselves
        super(new SimpleContainer(1), 0, x, y);

        this.player = player;
        this.listIndex = listIndex;

        // Start empty
        this.container.setItem(0, ItemStack.EMPTY);
    }

    /**
     * Set callback for EMC spending events
     * The menu calls this to get notified when EMC is spent
     */
    public void setEMCSpentCallback(EMCSpentCallback callback) {
        this.emcSpentCallback = callback;
    }

    /**
     * Update this slot to display a specific item from the list
     *
     * This is called whenever the item list changes.
     * The slot is told "you should now display THIS item with THIS amount"
     *
     * @param item The item to display (or Items.AIR for empty slot)
     * @param amount How many the player can afford (0 if can't afford or empty)
     */
    public void updateDisplay(Item item, int amount) {
        // Check if anything actually changed
        boolean itemChanged = this.currentItem != item;
        boolean amountChanged = this.currentAmount != amount;

        if (!itemChanged && !amountChanged) {
            return; // No change needed
        }

        // Update our cached state
        this.currentItem = item;
        this.currentAmount = amount;

        // Update the visual display
        if (item == Items.AIR || amount <= 0) {
            // Empty slot
            this.container.setItem(0, ItemStack.EMPTY);
        } else {
            // Show the item with the affordable amount
            this.container.setItem(0, new ItemStack(item, amount));
        }

        // Mark as changed so Minecraft syncs to client
        this.setChanged();

        if (itemChanged) {
            LOGGER.debug("Slot {} now displays {} x{}, of {}", listIndex, item, amount, item.getDefaultMaxStackSize());
        }
    }

    /**
     * Get the item currently displayed in this slot
     */
    @Override
    public ItemStack getItem() {
        if (currentItem == Items.AIR || currentAmount <= 0) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(currentItem, currentAmount);
    }

    /**
     * Set the item in this slot
     * Disabled - virtual slots can't be manually modified
     */
    @Override
    public void set(ItemStack stack) {
        // Virtual slots are read-only from external perspective
    }

    /**
     * Check if an item can be placed in this slot
     * Always false - these are output-only slots
     */
    @Override
    public boolean mayPlace(ItemStack stack) {
        return false;
    }

    /**
     * Check if a player can pick up items from this slot
     * Only allowed if there's an item and they can afford it
     */
    @Override
    public boolean mayPickup(Player player) {
        if (currentItem == Items.AIR || currentAmount <= 0) {
            return false; // Empty slot
        }

        // Check if they have enough EMC for at least 1 item
        long emcPerItem = EMCHelper.getItemEMC(currentItem).orElse(0L);
        return EMCHelper.hasEMC(player, emcPerItem);
    }

    /**
     * Called when a player takes items from this slot
     * This is where we deduct EMC
     */
    @Override
    public void onTake(Player player, ItemStack stack) {
        LOGGER.info("onTake called: currentItem={}, stack={}, isClient={}",
                currentItem, stack.getItem(), player.level().isClientSide());
        
        if (currentItem == Items.AIR) {
            LOGGER.warn("onTake called but currentItem is AIR!");
            return; // Nothing to take
        }

        // Process the EMC transaction and get result
        PurchaseResult result = processEMCPurchaseWithResult(player, stack);
        
        LOGGER.info("Purchase result: success={}, emcSpent={}, count={}",
                result.success, result.emcSpent, result.itemCount);

        // Notify menu if we have a callback (for EMC loss display)
        if (result.success && emcSpentCallback != null && !player.level().isClientSide()) {
            emcSpentCallback.onEMCSpent(result.emcSpent, result.itemName, result.itemCount);
        }

        // Let vanilla Minecraft handle giving the item to the player
        super.onTake(player, stack);

        // The menu will handle updating the display after EMC changes
    }

    /**
     * Process an EMC purchase and return detailed result
     * 
     * This is the core logic for buying items with EMC. It's separated into
     * its own method so it can be called from multiple places:
     * - onTake() for normal clicks
     * - NeoPlateMenu.quickMoveStack() for shift-clicks
     * 
     * How it works:
     * 1. Validates the item count (can't exceed what's affordable)
     * 2. Calculates total EMC cost
     * 3. Checks if player has enough EMC
     * 4. Deducts EMC from player
     * 5. Syncs new balance to client
     * 6. Logs the transaction
     * 7. Returns detailed result (for display)
     * 
     * @param player The player making the purchase
     * @param stack The item stack being purchased (amount will be adjusted if needed)
     * @return PurchaseResult with transaction details
     */
    public PurchaseResult processEMCPurchaseWithResult(Player player, ItemStack stack) {
        // Validation: Make sure we have an item to purchase
        if (currentItem == Items.AIR) {
            LOGGER.warn("Attempted to purchase from empty slot");
            return new PurchaseResult(false, 0, 0, "");
        }

        // Ensure the stack count is valid
        // Can't buy more than: current affordable amount OR item max stack size
        int actualCount = Math.min(stack.getCount(),
                Math.min(currentAmount, stack.getMaxStackSize()));
        
        // Update stack to the actual amount
        stack.setCount(actualCount);

        // Only process on server side (client just displays)
        if (!player.level().isClientSide()) {
            // Calculate total EMC cost
            long emcPerItem = EMCHelper.getItemEMC(currentItem).orElse(0L);
            long totalCost = emcPerItem * actualCount;

            // Get player's EMC data
            PlayerEMCData emcData = EMCHelper.getPlayerEMC(player);

            // Check if they can afford it
            if (emcData.hasEMC(totalCost)) {
                // Attempt to deduct EMC
                boolean success = EMCHelper.removeEMC(player, totalCost);

                if (success) {
                    // Sync new EMC balance to client
                    EMCHelper.syncEMC((ServerPlayer) player);

                    LOGGER.info("Player {} purchased {} x{} for {} EMC (new balance: {})",
                            player.getName().getString(),
                            currentItem,
                            actualCount,
                            totalCost,
                            emcData.getEMC());

                    // Return success with purchase details
                    return new PurchaseResult(
                        true, 
                        totalCost, 
                        actualCount,
                        new ItemStack(currentItem).getHoverName().getString()
                    );
                } else {
                    // EMC deduction failed (shouldn't happen if hasEMC returned true)
                    LOGGER.error("Failed to deduct {} EMC from player {}",
                            totalCost,
                            player.getName().getString());
                    return new PurchaseResult(false, 0, 0, "");
                }
            } else {
                // Player doesn't have enough EMC
                LOGGER.warn("Player {} tried to purchase {} x{} without enough EMC (has: {}, needs: {})",
                        player.getName().getString(),
                        currentItem,
                        actualCount,
                        emcData.getEMC(),
                        totalCost);
                return new PurchaseResult(false, 0, 0, "");
            }
        }

        // On client side, assume success (server will validate)
        return new PurchaseResult(true, 0, actualCount, "");
    }

    /**
     * Process an EMC purchase - LEGACY METHOD for backward compatibility
     * 
     * This is kept for any code that doesn't need the detailed result.
     * It calls the new method but only returns boolean success/failure.
     * 
     * @param player The player making the purchase
     * @param stack The item stack being purchased
     * @return true if purchase was successful, false if it failed
     */
    public boolean processEMCPurchase(Player player, ItemStack stack) {
        return processEMCPurchaseWithResult(player, stack).success;
    }

    /**
     * Calculate how many of this item the player can afford
     * 
     * This is useful for shift-click logic where you want to know
     * the maximum amount the player can purchase.
     * 
     * @param player The player
     * @return Maximum affordable count (0 if can't afford any, or slot is empty)
     */
    public int getAffordableCount(Player player) {
        if (currentItem == Items.AIR) {
            return 0;
        }

        // Get EMC per item
        long emcPerItem = EMCHelper.getItemEMC(currentItem).orElse(0L);
        if (emcPerItem <= 0) {
            return 0; // Item has no EMC value
        }

        // Get player's current EMC
        PlayerEMCData emcData = EMCHelper.getPlayerEMC(player);
        long playerEMC = emcData.getEMC();

        // Calculate max affordable based on EMC
        long maxAffordableByEMC = playerEMC / emcPerItem;

        // Can't afford more than what's displayed, item max stack size, or int max
        int maxAffordable = (int) Math.min(maxAffordableByEMC, Integer.MAX_VALUE);
        maxAffordable = Math.min(maxAffordable, currentAmount);
        maxAffordable = Math.min(maxAffordable, currentItem.getDefaultMaxStackSize());

        return maxAffordable;
    }

    /**
     * Get the EMC cost for a single item in this slot
     * 
     * @return EMC cost per item, or 0 if no item or no EMC value
     */
    public long getEMCPerItem() {
        if (currentItem == Items.AIR) {
            return 0;
        }
        return EMCHelper.getItemEMC(currentItem).orElse(0L);
    }

    /**
     * Remove a specific amount from the slot
     * Used when player right-clicks to take half, etc.
     */
    @Override
    public ItemStack remove(int amount) {
        if (currentItem == Items.AIR) {
            return ItemStack.EMPTY;
        }

        // Return a stack of the requested amount (or less if can't afford)
        int actualAmount = Math.min(amount, currentAmount);
        return new ItemStack(currentItem, actualAmount);
    }

    /**
     * Get the maximum stack size for this slot
     */
    @Override
    public int getMaxStackSize() {
        return Math.min(64, currentAmount);
    }

    /**
     * Get the maximum stack size for a specific item stack
     */
    @Override
    public int getMaxStackSize(ItemStack stack) {
        return Math.min(stack.getMaxStackSize(), currentAmount);
    }

    /**
     * Get which index in the list this slot represents
     */
    public int getListIndex() {
        return listIndex;
    }

    /**
     * Get the item currently displayed (for debugging)
     */
    public Item getCurrentItem() {
        return currentItem;
    }

    /**
     * Get the current affordable amount (for debugging)
     */
    public int getCurrentAmount() {
        return currentAmount;
    }

    /**
     * Check if this slot is currently empty
     */
    public boolean isEmpty() {
        return currentItem == Items.AIR || currentAmount <= 0;
    }

    /**
     * Check if this slot is a virtual EMC slot
     */
    public static boolean isVirtualSlot(Slot slot) {
        return slot instanceof VirtualEMCSlot;
    }

    /**
     * Check if the current item is favorited
     */
    public boolean isCurrentItemFavorited() {
        if (currentItem == null || currentItem == Items.AIR) {
            return false;
        }

        PlayerEMCData emcData = EMCHelper.getPlayerEMC(player);
        return emcData.isFavorited(currentItem);
    }
}
