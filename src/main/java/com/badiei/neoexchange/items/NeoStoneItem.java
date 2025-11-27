package com.badiei.neoexchange.items;

import com.badiei.neoexchange.economy.NeoStoneType;
import com.badiei.neoexchange.emc.EMCHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * Custom item class for Neo Stones.
 * Each stone has a colored name and special properties based on its tier.
 */
public class NeoStoneItem extends Item {
    
    private final NeoStoneType stoneType;
    
    /**
     * Constructor for Neo Stone items
     * @param properties The properties passed by the registration system (MUST USE THESE!)
     * @param stoneType The tier of this stone (COMMON, UNCOMMON, etc.)
     */
    public NeoStoneItem(Properties properties, NeoStoneType stoneType) {
        // CRITICAL: Use the properties parameter, not new Properties()!
        // The registration system has already set the item ID in these properties
        super(properties
                .stacksTo(1)  // Neo Stones don't stack (they're special/powerful)
                .durability(stoneType.getMaxEMC()*10)
        );
        
        this.stoneType = stoneType;
    }
    
    /**
     * Get the tier type of this stone
     * @return The NeoStoneType enum value
     */
    public NeoStoneType getStoneType() {
        return this.stoneType;
    }
    
    /**
     * Override the display name to add custom coloring
     * This makes the item name colored regardless of language!
     * 
     * @param stack The item stack
     * @return Component with colored name
     */
    @Override
    public Component getName(ItemStack stack) {
        // Get the base name from the language file
        Component baseName = super.getName(stack);
        
        // Return it with our custom color applied
        // withStyle() adds formatting (color, bold, italic, etc.)
        return baseName.copy().withStyle(this.stoneType.getColor());
    }

    /**
     * Add tooltip information when hovering over the item
     * Shows what tier it is and what it unlocks
     * Only shows detailed info when holding SHIFT
     *
     * @param stack The item stack
     * @param context Tooltip context
     * @param tooltipDisplay Tooltip display
     * @param tooltipAdder Consumer to add tooltip lines
     * @param flag Tooltip display flags
     */

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @NotNull TooltipContext context, @NotNull TooltipDisplay tooltipDisplay, @NotNull Consumer<Component> tooltipAdder, @NotNull TooltipFlag flag) {
        if (Minecraft.getInstance().hasShiftDown()) {
            // Add tier information
            tooltipAdder.accept(Component.literal("\nTier " + stoneType.getTier())
                    .withStyle(ChatFormatting.GRAY));
            
            // Add what this stone unlocks (specific to each tier)
            String formattedString = String.format("%,d", stoneType.getMaxEMC());
            if (stoneType == NeoStoneType.COMMON) {
                tooltipAdder.accept(Component.literal("Max EMC: " + formattedString).withStyle(ChatFormatting.YELLOW));
                tooltipAdder.accept(Component.literal("Unlocks: Basic Exchange").withStyle(ChatFormatting.GRAY));
            } else if (stoneType == NeoStoneType.UNCOMMON) {
                tooltipAdder.accept(Component.literal("Max EMC: " + formattedString).withStyle(ChatFormatting.YELLOW));
                tooltipAdder.accept(Component.literal("Unlocks: TBD").withStyle(ChatFormatting.YELLOW));
            } else if (stoneType == NeoStoneType.RARE) {
                tooltipAdder.accept(Component.literal("Max EMC: " + formattedString).withStyle(ChatFormatting.YELLOW));
                tooltipAdder.accept(Component.literal("Unlocks: TBD").withStyle(ChatFormatting.YELLOW));
            } else if (stoneType == NeoStoneType.EPIC) {
                tooltipAdder.accept(Component.literal("Max EMC: " + formattedString).withStyle(ChatFormatting.YELLOW));
                tooltipAdder.accept(Component.literal("Unlocks: Auto-Sell").withStyle(ChatFormatting.YELLOW));
            } else if (stoneType == NeoStoneType.LEGENDARY) {
                tooltipAdder.accept(Component.literal("Max EMC: " + formattedString).withStyle(ChatFormatting.YELLOW));
                tooltipAdder.accept(Component.literal("Unlocks: Portable Shop").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            } else if (stoneType == NeoStoneType.MYTHIC) {
                tooltipAdder.accept(Component.literal("Max EMC: Unlimited").withStyle(ChatFormatting.YELLOW));
                tooltipAdder.accept(Component.literal("Unlocks: Portable Shop").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
            }

            // Add usage hint
            tooltipAdder.accept(Component.empty());
            tooltipAdder.accept(Component.literal("Used in Neo Plate to access vast varieties of exchanges")
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            tooltipAdder.accept(Component.literal("Uses left:" + (getDefaultInstance().getMaxDamage() - getDefaultInstance().getDamageValue()))
                    .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
        } else {
            // SHIFT NOT HELD - Show hint to press shift
            tooltipAdder.accept(Component.literal("Hold ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(Component.literal("SHIFT")
                            .withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal(" for more info")
                            .withStyle(ChatFormatting.GRAY)));
        }
    }
    
    /**
     * Make the item glow with enchantment effect for higher tiers
     * @param stack The item stack
     * @return true if should have glint effect
     */
    @Override
    public boolean isFoil(ItemStack stack) {
        // Epic and Legendary stones have enchantment glint
        return stoneType == NeoStoneType.EPIC || stoneType == NeoStoneType.LEGENDARY;
    }

    public static void setStoredEMC(ItemStack itemStack, long value) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("emc", value);
        itemStack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    public static long getStoredEMC(ItemStack itemStack) {
        CustomData tag = itemStack.get(DataComponents.CUSTOM_DATA);
        if (tag != null) {
            long emcValue = tag.copyTag().getLongOr("emc", 0L);
            return emcValue;
        }
        return 0L;
    }

    public boolean addEMC(ItemStack itemStack, long amount) {
        CustomData tag1 = itemStack.get(DataComponents.CUSTOM_DATA);
        CompoundTag tag2 = new CompoundTag();
        int maxEMC = this.stoneType.getMaxEMC();
        if (tag1 != null) {
            long emcValue = tag1.copyTag().getLongOr("emc", 0L);
            if (emcValue + amount >= maxEMC) {
                tag2.putLong("emc", maxEMC);
                itemStack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag2));
            }
            else {
                tag2.putLong("emc", maxEMC);
                itemStack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag2));
            }
            return false;
        }
        return false;
    }
}
