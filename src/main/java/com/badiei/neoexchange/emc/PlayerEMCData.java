package com.badiei.neoexchange.emc;

import com.badiei.neoexchange.screen.custom.NeoPlateScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * PlayerEMCData - Individual player's EMC storage
 *
 * This class represents a single player's EMC balance. Each player
 * has their own instance of this class attached to them.
 *
 * INBTSerializable means this data can be saved/loaded from NBT
 * (Named Binary Tag - Minecraft's save file format)
 */
public class PlayerEMCData {
    PlayerEMCData() {
        this.emcBalance = 0L;
    }

    PlayerEMCData(long emcBalance) {
        this.emcBalance = emcBalance;
    }

    /**
     * Reconstruct PlayerEMCData from saved data
     * Used by the codec system when loading player data
     *
     * @param emc The saved EMC balance
     * @param learnedItemStrings The saved learned items as strings
     * @return A new PlayerEMCData with the loaded data
     */
    public static PlayerEMCData fromSavedData(long emc, java.util.List<String> learnedItemStrings) {
        PlayerEMCData data = new PlayerEMCData(emc);

        // Parse each string back into an item and learn it
        for (String itemIdString : learnedItemStrings) {
            try {
                ResourceLocation itemId = ResourceLocation.parse(itemIdString);
                java.util.Optional<net.minecraft.world.item.Item> item =
                        BuiltInRegistries.ITEM.getOptional(itemId);

                if (item.isPresent()) {
                    data.learnItem(item.get());
                }
            } catch (Exception e) {
                // Skip invalid item IDs (maybe from removed mods)
                LOGGER.warn("Failed to load learned item: {}", itemIdString);
            }
        }

        return data;
    }

    private static final Logger LOGGER = LogUtils.getLogger();

    // The player's current EMC balance
    private long emcBalance = 0L;

    // The maximum EMC a player can store (prevents overflow issues)
    // This is about 9 quintillion - should be enough for anyone!
    private static final long MAX_EMC = Long.MAX_VALUE;

    /**
     * Get the player's current EMC balance
     */
    public long getEMC() {
        return emcBalance;
    }

    /**
     * Set the EMC balance directly
     * Clamps the value between 0 and MAX_EMC to prevent issues
     *
     * @param emc The new balance
     */
    public void setEMC(long emc) {
        this.emcBalance = Math.max(0, Math.min(emc, MAX_EMC));
    }

    /**
     * Add EMC to the player's balance
     *
     * @param amount Amount to add (must be positive)
     * @return true if successful, false if it would overflow
     */
    public boolean addEMC(long amount) {
        if (amount < 0) {
            LOGGER.warn("Attempted to add negative EMC: {}", amount);
            return false;
        }

        // Check for overflow before adding
        if (emcBalance > MAX_EMC - amount) {
            LOGGER.warn("EMC addition would overflow, clamping to MAX_EMC");
            emcBalance = MAX_EMC;
            return false;
        }

        emcBalance += amount;
        return true;
    }

    /**
     * Remove EMC from the player's balance
     *
     * @param amount Amount to remove (must be positive)
     * @return true if successful, false if insufficient funds
     */
    public boolean removeEMC(long amount) {
        if (amount < 0) {
            LOGGER.warn("Attempted to remove negative EMC: {}", amount);
            return false;
        }

        if (emcBalance < amount) {
            return false; // Not enough EMC
        }

        emcBalance -= amount;
        return true;
    }

    /**
     * Check if the player has at least this much EMC
     *
     * @param amount Amount to check
     * @return true if player has enough EMC
     */
    public boolean hasEMC(long amount) {
        return emcBalance >= amount;
    }

    /**
     * Try to spend EMC (checks balance first, then removes)
     * This is a convenience method that combines hasEMC and removeEMC
     *
     * @param amount Amount to spend
     * @return true if transaction successful
     */
    public boolean trySpendEMC(long amount) {
        if (hasEMC(amount)) {
            return removeEMC(amount);
        }
        return false;
    }

    /**
     * Serialize (save) this data to NBT format
     * This is called automatically when the player logs out or the world saves
     *
     * The HolderLookup parameter is used for looking up registry entries
     * when saving/loading complex data structures
     */

    /**
     * Deserialize (load) this data from NBT format
     * This is called when a player logs in or the world loads
     */

    /* Implement ValueIOSerializable
    @Override
    public void serialize(ValueOutput output) {
        output.putLong("emc_balance", emcBalance);
    }

    @Override
    public void deserialize(ValueInput input) {
        CompoundTag tag = new CompoundTag();
        input.getLongOr("emc_balance", 0);
        if (tag.contains("emc_balance")) {
            emcBalance = tag.getLongOr("emc_balance", -1L);
            // Ensure loaded value is valid
            emcBalance = Math.max(0, Math.min(emcBalance, MAX_EMC));
        }
    }
    */
    /**
     * Copy data from another PlayerEMCData instance
     * Useful when a player respawns or travels between dimensions
     */
    public void copyFrom(PlayerEMCData other) {
        this.emcBalance = other.emcBalance;
    }

    /**
     * Get a formatted string of the EMC balance for display
     * Adds commas for readability (e.g., "1,234,567")
     */
    public String getFormattedEMC() {
        return String.format("%,d", emcBalance);
    }


    private final Set<ResourceLocation> learnedItems = new HashSet<>();

    public boolean hasLearned(Item item) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        return learnedItems.contains(itemId);
    }

    public void learnItem(Item item) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        if (learnedItems.contains(itemId)) {return;}
        learnedItems.add(itemId);
        // NOTE: Syncing is handled by the caller (EMCHelper methods)
        // We don't sync here because this method is called during deserialization
        // when no player/client is available
    }

    public void unLearnItem(Item item) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        if (!learnedItems.contains(itemId)) {return;}
        learnedItems.remove(itemId);
    }

    /**
     * Get the set of learned items (for serialization)
     * Returns a copy to prevent external modification
     */
    public Set<ResourceLocation> getLearnedItems() {
        return new HashSet<>(learnedItems);
    }

    public List<ItemStack> getLearnedItemsList() {
        List<ItemStack> list = List.of();
        for (ResourceLocation RL : learnedItems) {
            list.add(BuiltInRegistries.ITEM.getValue(RL).getDefaultInstance());
        }
        return list;
    }

    public void clearLearneditemsList() {
        learnedItems.clear();
    }
}