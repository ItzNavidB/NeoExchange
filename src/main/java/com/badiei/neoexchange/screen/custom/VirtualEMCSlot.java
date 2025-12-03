package com.badiei.neoexchange.screen.custom;

import com.badiei.neoexchange.emc.EMCHelper;
import com.badiei.neoexchange.emc.PlayerEMCData;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.slf4j.Logger;

import java.util.List;

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
        if (currentItem == Items.AIR) {
            return; // Nothing to take
        }
        // Ensure the stack count is valid
        int actualCount = Math.min(stack.getCount(),
                Math.min(currentAmount, stack.getMaxStackSize()));
        stack.setCount(actualCount);


        // Only process on server side
        if (!player.level().isClientSide()) {
            // Calculate total EMC cost
            long emcPerItem = EMCHelper.getItemEMC(currentItem).orElse(0L);
            long totalCost = emcPerItem * actualCount;

            // Get player's EMC data
            PlayerEMCData emcData = EMCHelper.getPlayerEMC(player);

            // Deduct EMC
            if (emcData.hasEMC(totalCost)) {
                boolean success = EMCHelper.removeEMC(player, totalCost);

                if (success) {
                    // Sync new EMC balance to client
                    EMCHelper.syncEMC((ServerPlayer) player);

                    // Play success sound
                    player.level().playSound(
                            null,
                            player.blockPosition(),
                            SoundEvents.EXPERIENCE_ORB_PICKUP,
                            SoundSource.PLAYERS,
                            0.5f,
                            1.2f
                    );

                    LOGGER.info("Player {} extracted {} x{} for {} EMC",
                            player.getName().getString(),
                            currentItem,
                            actualCount,
                            totalCost);
                } else {
                    LOGGER.error("Failed to deduct {} EMC from player {}",
                            totalCost,
                            player.getName().getString());
                }
            } else {
                LOGGER.warn("Player {} tried to take {} without enough EMC",
                        player.getName().getString(),
                        stack);
            }
        }

        // Let vanilla Minecraft handle giving the item to the player
        super.onTake(player, stack);

        // The menu will handle updating the display after EMC changes
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
}