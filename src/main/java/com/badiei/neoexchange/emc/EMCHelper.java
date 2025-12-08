package com.badiei.neoexchange.emc;

import com.badiei.neoexchange.economy.NeoStoneType;
import com.badiei.neoexchange.items.NeoStoneItem;
import com.badiei.neoexchange.network.SyncEMCPacket;
import com.badiei.neoexchange.network.SyncLearnedItemsPacket;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.List;
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
public class EMCHelper {
    private static final Logger LOGGER = LogUtils.getLogger();


    /**
     * Get a player's EMC data attachment
     *
     * @param player The player
     * @return The player's EMC data
     */
    public static PlayerEMCData getPlayerEMC(Player player) {
        return player.getData(NeoAttachments.PLAYER_EMC);
    }

    /**
     * Get a player's current EMC balance
     * Convenience method to avoid getPlayerEMC(player).getEMC()
     */
    public static long getBalance(Player player) {
        return getPlayerEMC(player).getEMC();
    }

    /**
     * Set a player's EMC balance
     *
     * If called on the server side, automatically syncs to client
     */
    public static void setBalance(Player player, long amount) {
        getPlayerEMC(player).setEMC(amount);
        syncEMC(player);
    }

    /**
     * Add EMC to a player's balance
     *
     * If called on the server side, automatically syncs to client
     *
     * @return true if successful
     */
    public static boolean addEMC(Player player, long amount) {
        boolean success = getPlayerEMC(player).addEMC(amount);
        if (success) {
            syncEMC(player);
        }
        return success;
    }

    /**
     * Remove EMC from a player's balance
     *
     * If called on the server side, automatically syncs to client
     *
     * @return true if successful (enough EMC available)
     */
    public static boolean removeEMC(Player player, long amount) {
        boolean success = getPlayerEMC(player).removeEMC(amount);
        if (success) {
            syncEMC(player);
        }
        return success;
    }

    /**
     * Check if a player has at least this much EMC
     */
    public static boolean hasEMC(Player player, long amount) {
        return getPlayerEMC(player).hasEMC(amount);
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
     * Get the EMC value of an ItemStack with durability consideration
     *
     * How this works:
     * 1. Gets the base EMC value for the item
     * 2. If the item has durability, calculates a durability ratio (current / max)
     * 3. Multiplies the base value by this ratio to get the actual value
     * 4. Multiplies by stack count for the final total
     *
     * Examples:
     * - Fresh diamond pickaxe (100% durability): Full EMC value
     * - Half-broken pickaxe (50% durability): Half EMC value
     * - Almost broken pickaxe (10% durability): 10% EMC value
     *
     * For non-damageable items (like diamonds, dirt, etc.), this returns
     * the same value as getStackEMC().
     *
     * @param stack The item stack to evaluate
     * @return Optional containing the durability-adjusted total EMC value
     */
    public static Optional<Long> getStackEMCWithDurability(ItemStack stack) {
        if (stack.isEmpty()) {
            return Optional.empty();
        }

        // Get the base EMC value for this item type
        Optional<Long> baseEMC = getItemEMC(stack.getItem());

        if (baseEMC.isEmpty()) {
            return Optional.empty();
        }

        long itemValue = baseEMC.get();

        // Check if this item can be damaged (tools, weapons, armor, etc.)
        if (stack.isDamageableItem()) {
            // Calculate remaining durability
            int currentDurability = stack.getMaxDamage() - stack.getDamageValue();
            int maxDurability = stack.getMaxDamage();

            // Calculate the durability ratio (0.0 to 1.0)
            // Example: If maxDurability is 1561 and currentDurability is 780
            // ratio = 780 / 1561 = 0.5 (50% durability remaining)
            float durabilityRatio = (float) currentDurability / maxDurability;

            // Apply the durability multiplier to the item value
            // Example: If base value is 8192 and ratio is 0.5
            // adjusted value = 8192 * 0.5 = 4096
            itemValue = (long) (itemValue * durabilityRatio);

            /*
            LOGGER.debug("Item {} has {}/{} durability ({}%), adjusted EMC from {} to {}",
                    stack.getItem(),
                    currentDurability,
                    maxDurability,
                    (int)(durabilityRatio * 100),
                    baseEMC.get(),
                    itemValue);
             */
        }

        // Multiply by stack count for the final total
        long totalValue = itemValue * stack.getCount();

        return Optional.of(totalValue);
    }

    /**
     * Convert an ItemStack to EMC and add it to a player's balance
     * This is the "learn" or "transmute to EMC" operation
     *
     * Now with durability support! Damaged items give less EMC.
     *
     * @param player The player
     * @param stack The item stack to convert
     * @return The amount of EMC gained, or -1 if the item has no EMC value
     */
    public static long convertToEMC(Player player, ItemStack stack) {
        // Use the durability-aware method instead of the basic one
        // This means damaged tools/armor will give proportionally less EMC
        Optional<Long> emcValue = getStackEMCWithDurability(stack);

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
                    getBalance(player));
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
        return getPlayerEMC(player).getFormattedEMC();
    }

    public static void syncALL(Player player) {
        syncEMC(player);
        syncLearnedItems(player);
    }

    /**
     * Synchronize EMC from server to client
     *
     * This method sends a packet to the client with the player's current EMC balance.
     * It only works on the server side - calling it on the client does nothing.
     *
     * Why check if it's ServerPlayer?
     * - ServerPlayer = server-side player object
     * - Player (client) = client-side player object
     * - We only send packets FROM server TO client
     *
     * @param player The player whose EMC should be synced
     */
    public static void syncEMC(Player player) {
        // Only send packets from the server side
        if (player instanceof ServerPlayer serverPlayer) {
            long balance = getBalance(player);

            // Create and send the packet to this specific player
            PacketDistributor.sendToPlayer(serverPlayer, new SyncEMCPacket(balance));

            LOGGER.debug("Synced EMC to client: {} for player {}",
                    balance, player.getName().getString());
        }
    }

    /**
     * Synchronize learned items from server to client
     *
     * @param player The player whose learned items should be synced
     */
    public static void syncLearnedItems(Player player) {
        // Only send packets from the server side
        if (player instanceof ServerPlayer serverPlayer) {
            PlayerEMCData emcData = getPlayerEMC(player);

            // Convert learned items to strings for transmission
            List<String> itemStrings = emcData.getLearnedItems().stream()
                    .map(ResourceLocation::toString)
                    .toList();

            // Create and send the packet
            PacketDistributor.sendToPlayer(serverPlayer, new SyncLearnedItemsPacket(itemStrings));

            LOGGER.debug("Synced {} learned items to client for player {}",
                    itemStrings.size(), player.getName().getString());
        }
    }

    public static List<Item> getLearnedItems(Player player) {
        List<Item> list = List.of();
        for (ItemStack stack : getPlayerEMC(player).getLearnedItemsList()) {
            list.add(stack.getItem());
        }
        return list;
    }

    public static int getStoneMaxEMC(ItemStack stone) {
        int maxEMC = NeoStoneType.COMMON.getDefaultMaxEMC();
        if (stone != ItemStack.EMPTY) {
            maxEMC = ((NeoStoneItem) stone.getItem()).getStoneType().getMaxEMC();
        }
        return maxEMC;
    }

    public static String getStoneMaxEMCFormatted(ItemStack stone) {
        int maxEMC = getStoneMaxEMC(stone);
        if (stone != ItemStack.EMPTY) {
            maxEMC = ((NeoStoneItem) stone.getItem()).getStoneType().getMaxEMC();
        }
        return maxEMC == Integer.MAX_VALUE ? "∞" : String.valueOf(maxEMC);
    }
}