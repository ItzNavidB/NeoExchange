package com.badiei.neoexchange.economy;

import com.badiei.neoexchange.NeoExchange;
import com.badiei.neoexchange.emc.EMCHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.item.ItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Optional;

/**
 * Enum representing the five tiers of Neo Stones.
 * Each tier grants access to different EMC value ranges in the exchange system.
 * 
 * Think of this enum as a "dropdown menu" with exactly 5 options.
 * Each option (COMMON, UNCOMMON, etc.) is a constant that exists when the program starts.
 * 
 * We can't create new NeoStoneType values at runtime - these 5 are it!
 */
public enum NeoStoneType {

    // Each line here creates ONE constant of this enum type
    // The numbers in parentheses are passed to the constructor below
    COMMON(1, "Common Stone", 256, ChatFormatting.GRAY),
    UNCOMMON(2, "Uncommon Stone", 1024, ChatFormatting.GREEN),
    RARE(3, "Rare Stone", 4096, ChatFormatting.AQUA),
    EPIC(4, "Epic Stone", 16384, ChatFormatting.LIGHT_PURPLE),
    LEGENDARY(5, "Legendary Stone", 50148, ChatFormatting.GOLD),  // No limit!
    MYTHIC(6, "Mythic Stone", Integer.MAX_VALUE, ChatFormatting.DARK_RED);  // Even further to no limit!

    // These are the fields that EVERY enum constant has
    // Think of these as properties that each stone type stores
    private final int tier;
    private final String displayName;
    private final int maxEMC;
    private final ChatFormatting color;
    
    /**
     * Constructor - This runs for EACH enum constant when the program starts
     * 
     * When Java sees "COMMON(1, "Common Stone", 256, Rarity.COMMON)" above,
     * it calls this constructor with those values.
     * 
     * @param tier The tier level (1-6)
     * @param displayName The human-readable name
     * @param maxEMC Maximum EMC value this tier can access
     * @param color Maximum EMC value this tier can access
     */
    NeoStoneType(int tier, String displayName, int maxEMC, ChatFormatting color) {
        // "this.tier" = THIS enum constant's tier field
        // "tier" = the parameter passed in
        this.tier = tier;
        this.displayName = displayName;
        //this.maxEMC = maxEMC;
        this.maxEMC = getMaxEMC(maxEMC);
        this.color = color;

    }
    
    /**
     * Get the tier number of this stone type
     * @return 1-6 depending on which tier this is
     */
    public int getTier() {
        return this.tier;
        // When you call COMMON.getTier(), "this" = COMMON, so returns 1
        // When you call LEGENDARY.getTier(), "this" = LEGENDARY, so returns 5
    }
    
    /**
     * Get the display name for showing to players
     * @return "Common Stone", "Uncommon Stone", etc.
     */
    public String getDisplayName() {
        return this.displayName;
    }
    
    /**
     * Get the maximum EMC value this tier can access
     * @return The EMC limit (256 for Common, unlimited for Legendary)
     */
    public int getMaxEMC() {
        return this.maxEMC;
    }

    public int getMaxEMC(int defaultValue) {
        return switch(this) {
            case COMMON -> defaultValue;
            case UNCOMMON -> defaultValue;
            case RARE -> EMCHelper.getItemEMC(Items.DIAMOND).orElse((long) defaultValue).intValue();
            case EPIC -> EMCHelper.getItemEMC(Items.EMERALD).orElse((long) defaultValue).intValue();
            case LEGENDARY -> EMCHelper.getItemEMC(Items.NETHERITE_INGOT).orElse((long) defaultValue).intValue();
            case MYTHIC -> defaultValue;
        };
    }
    
    /**
     * Get the Minecraft rarity (used for text coloring)
     * @return Rarity enum value (COMMON = white, UNCOMMON = green, etc.)
     */
    
    /**
     * Check if this stone type can access an item with the given EMC value
     * 
     * Example:
     * - COMMON.canAccess(100) → true (100 <= 256)
     * - COMMON.canAccess(500) → false (500 > 256)
     * - LEGENDARY.canAccess(999999) → true (unlimited)
     * 
     * @param itemEMC The EMC value of an item
     * @return true if this tier can buy/sell that item
     */
    public boolean canAccess(int itemEMC) {
        return itemEMC <= this.maxEMC;
    }
    
    /**
     * Check if this stone is a higher tier than another stone
     * 
     * Example:
     * - RARE.isHigherThan(COMMON) → true (tier 3 > tier 1)
     * - COMMON.isHigherThan(RARE) → false (tier 1 < tier 3)
     * 
     * @param other Another stone type to compare against
     * @return true if this stone's tier is higher
     */
    public boolean isHigherThan(NeoStoneType other) {
        return this.tier > other.tier;
    }
    
    /**
     * Get the next tier up from this one
     * Returns null if already at max tier
     * 
     * Example:
     * - COMMON.getNextTier() → UNCOMMON
     * - LEGENDARY.getNextTier() → null (already at top)
     * 
     * @return The next higher tier, or null if at max
     */
    public NeoStoneType getNextTier() {
        // values() returns an array of all enum constants: [COMMON, UNCOMMON, RARE, EPIC, LEGENDARY]
        NeoStoneType[] allTiers = NeoStoneType.values();
        
        // If we're already at the last one, return null
        if (this.tier >= allTiers.length) {
            return null;
        }
        
        // Return the next one in the array
        // tier-1 because arrays start at 0, but our tiers start at 1
        return allTiers[this.tier];  // tier is already the next index (tier 1 → index 1 = UNCOMMON)
    }
    
    /**
     * Get a stone type by its tier number
     * Useful for commands or config loading
     * 
     * Example:
     * - fromTier(1) → COMMON
     * - fromTier(5) → LEGENDARY
     * - fromTier(99) → null
     * 
     * @param tier The tier number (1-6)
     * @return The corresponding stone type, or null if invalid
     */
    public static NeoStoneType fromTier(int tier) {
        // Loop through all enum constants
        for (NeoStoneType type : NeoStoneType.values()) {
            if (type.tier == tier) {
                return type;
            }
        }
        return null;  // Invalid tier number
    }

    public ChatFormatting getColor() {return this.color;}
}
