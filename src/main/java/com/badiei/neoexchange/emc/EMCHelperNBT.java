package com.badiei.neoexchange.emc;

import com.mojang.logging.LogUtils;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.util.Optional;

/**
 * EMCHelper - Utility class for EMC operations
 *
 * This is a convenience class that makes working with EMC easier.
 * Instead of writing lots of boilerplate code everywhere, we centralize
 * common operations here.
 *
 * All methods are static - you don't create an instance of this class,
 * you just call EMCHelper.methodName()
 */
public class EMCHelperNBT {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String NBT_KEY = "emc_balance";

    /**
     * Get a player's current EMC balance
     * Convenience method to avoid getPlayerEMC(player).getEMC()
     */
    public static long getEMC(Player player) {
        if (player.getPersistentData().getLong(NBT_KEY).isPresent()) {
            return player.getPersistentData().getLong(NBT_KEY).get();
        }
        else {
            setEMC(player,0);
            return player.getPersistentData().getLong(NBT_KEY).get();
        }
    }

    /**
     * Set a player's EMC balance
     */
    public static void setEMC(Player player, long amount) {
        player.getPersistentData().putLong(NBT_KEY, amount);
    }

    /**
     * Add EMC to a player's balance
     *
     * @return true if successful
     */
    public static boolean addEMC(Player player, long amount) {
        long current = getEMC(player);
        setEMC(player, current + amount);
        return true;
    }

    /**
     * Remove EMC from a player's balance
     *
     * @return true if successful (enough EMC available)
     */
    public static boolean removeEMC(Player player, long amount) {
        long current = getEMC(player);
        setEMC(player, current - amount);
        return true;
    }

    /**
     * Check if a player has at least this much EMC
     */
    public static boolean hasEMC(Player player, long amount) {
        return getEMC(player) >= amount;
    }

    /**
     * Get the EMC value of an item
     *
     * @param item The item to check
     * @return Optional containing the EMC value, or empty if none
     */
    public static Optional<Long> getItemEMC(Item item) {
        return EMCRegistry.getInstance().getEMC(item);
    }

    /**
     * Get the EMC value of an ItemStack
     * Multiplies the base item EMC by the stack size
     *
     * @param stack The item stack
     * @return Optional containing the total EMC value
     */
    public static Optional<Long> getStackEMC(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }

        return getItemEMC(stack.getItem())
                .map(baseValue -> baseValue * stack.getCount());
    }

    /**
     * Convert an ItemStack to EMC and add it to a player's balance
     * This is the "learn" or "transmute to EMC" operation
     *
     * @param player The player
     * @param stack The item stack to convert
     * @return The amount of EMC gained, or -1 if the item has no EMC value
     */
    public static long convertToEMC(Player player, ItemStack stack) {
        Optional<Long> emcValue = getStackEMC(stack);

        if (emcValue.isEmpty()) {
            LOGGER.debug("Item {} has no EMC value", stack.getItem());
            return -1;
        }

        long totalEMC = emcValue.get();

        if (addEMC(player, totalEMC)) {
            LOGGER.debug("Player {} converted {} x{} to {} EMC",
                    player.getName().getString(),
                    stack.getItem(),
                    stack.getCount(),
                    totalEMC);
            return totalEMC;
        }

        return -1;
    }

    /**
     * Try to create an ItemStack by spending EMC
     * This is the "transmute from EMC" operation
     *
     * @param player The player
     * @param item The item to create
     * @param count How many to create
     * @return The created ItemStack, or ItemStack.EMPTY if failed
     */
    public static ItemStack createFromEMC(Player player, Item item, int count) {
        Optional<Long> baseEMC = getItemEMC(item);

        if (baseEMC.isEmpty()) {
            LOGGER.debug("Item {} has no EMC value", item);
            return ItemStack.EMPTY;
        }

        long totalCost = baseEMC.get() * count;

        if (!hasEMC(player, totalCost)) {
            LOGGER.debug("Player {} does not have enough EMC (needs {}, has {})",
                    player.getName().getString(),
                    totalCost,
                    getEMC(player));
            return ItemStack.EMPTY;
        }

        if (removeEMC(player, totalCost)) {
            LOGGER.debug("Player {} created {} x{} for {} EMC",
                    player.getName().getString(),
                    item,
                    count,
                    totalCost);
            return new ItemStack(item, count);
        }

        return ItemStack.EMPTY;
    }

    /**
     * Check if a player can afford to create an item
     */
    public static boolean canAfford(Player player, Item item, int count) {
        return getItemEMC(item)
                .map(baseEMC -> hasEMC(player, baseEMC * count))
                .orElse(false);
    }

    /**
     * Get a formatted string of a player's balance
     */
    public static String getFormattedBalance(Player player) {
        long bal = getEMC(player);
        return Long.toString(bal);
        //return String.format("%d", bal);
    }
}