package com.badiei.neoexchange.emc;

import com.badiei.neoexchange.NeoExchange;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import org.slf4j.Logger;

import java.util.Optional;

/**
 * EMCTooltipHandler - Advanced version with durability support
 *
 * Features:
 * - Shows EMC value on item hover
 * - Shows total EMC for stacks
 * - Automatically adjusts value based on item durability
 * - Hold SHIFT for additional EMC information
 * - Configurable styling and format
 *
 * Example tooltips:
 *
 * Normal:
 * Diamond
 * EMC: 8,192 ✦
 *
 * Stack:
 * Diamond (x64)
 * EMC: 8,192 ✦ (524,288 ✦ total)
 *
 * Damaged Item:
 * Diamond Pickaxe (50% durability)
 * EMC: 4,096 ✦ (reduced from 8,192 ✦ due to damage)
 */
@EventBusSubscriber(modid = NeoExchange.MOD_ID, value = Dist.CLIENT)
public class EMCTooltipHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Configuration - you can move these to a config file later
    private static final boolean SHOW_TOOLTIPS = true;
    private static final boolean SHOW_ON_STACKS = true;
    private static final boolean SHOW_ADVANCED_ON_SHIFT = true;
    private static final String EMC_SYMBOL = "";
    private static final String EMC_REPAIRED_SYMBOL = "⚒";


    @SubscribeEvent
    public static void onItemTooltip(ItemTooltipEvent event) {
        // Check if tooltips are enabled
        if (!SHOW_TOOLTIPS) {
            return;
        }

        ItemStack stack = event.getItemStack();

        // Don't show tooltip for empty stacks
        if (stack.isEmpty()) {
            return;
        }

        // Get the durability-adjusted EMC value for this stack
        // This automatically handles damaged items, giving them less value
        Optional<Long> emcValue = EMCHelper.getStackEMCWithDurability(stack);
        Optional<Long> emcValue2 = EMCHelper.getStackEMC(stack);

        // If the item has no EMC value, optionally show "No EMC Value"
        if (emcValue.isEmpty()) {
            // Uncomment if you want to show items have no value:
            // if (Screen.hasShiftDown()) {
            //     event.getToolTip().add(Component.literal("No EMC Value")
            //         .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            // }
            return;
        }
        if (emcValue2.isEmpty()) {return;}

        // The value we get here is already adjusted for durability AND stack count
        long totalValue = emcValue.get();
        long totalValue2 = emcValue2.get();
        boolean isStack = stack.getCount() > 1;
        boolean shiftDown = Minecraft.getInstance().hasShiftDown();

        // Add a blank line before EMC info for visual separation
        // (Only if there are other tooltip lines)
        if (event.getToolTip().size() > 1) {
            event.getToolTip().add(Component.empty());
        }

        // Basic EMC line - always show
        // Note: We need to pass the per-item value for proper display
        long perItemValue = totalValue / stack.getCount();
        long perItemValue2 = totalValue2 / stack.getCount();


        // Advanced info when holding shift
        if (shiftDown && SHOW_ADVANCED_ON_SHIFT) {
            addAdvancedEMCInfo(event, perItemValue2, stack.getCount(), isStack, totalValue2);
        }
        else {
            addBasicEMCLine(event, perItemValue, stack.getCount(), isStack, totalValue);
        }
    }

    /**
     * Add the basic EMC value line
     *
     * Now simplified - the durability calculation is handled by EMCHelper!
     */
    private static void addBasicEMCLine(ItemTooltipEvent event, long perItemValue, int count, boolean isStack, long totalValue) {
        String formattedValue = String.format("%,d", perItemValue);

        if (isStack && SHOW_ON_STACKS) {
            // Show both per-item and total for stacks
            String formattedTotal = String.format("%,d", totalValue);

            event.getToolTip().add(
                    Component.literal("EMC: ")
                            .withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(formattedValue)
                                    .withStyle(ChatFormatting.YELLOW))
                            .append(Component.literal(EMC_SYMBOL)
                                    .withStyle(ChatFormatting.GOLD))
                            .append(Component.literal(" (")
                                    .withStyle(ChatFormatting.DARK_GRAY))
                            .append(Component.literal(formattedTotal + EMC_SYMBOL)
                                    .withStyle(ChatFormatting.YELLOW))
                            .append(Component.literal(" total)")
                                    .withStyle(ChatFormatting.DARK_GRAY))
            );
        } else {
            // Single item or stack display disabled
            event.getToolTip().add(
                    Component.literal("EMC: ")
                            .withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(formattedValue)
                                    .withStyle(ChatFormatting.YELLOW))
                            .append(Component.literal(EMC_SYMBOL)
                                    .withStyle(ChatFormatting.GOLD))
            );
        }
    }

    /**
     * Add advanced EMC information (shown when holding SHIFT)
     */
    private static void addAdvancedEMCInfo(ItemTooltipEvent event, long perItemValue, int count, boolean isStack, long totalValue) {
        String formattedValue = String.format("%,d", perItemValue);
        String EMC_REPAIRED_SYMBOL = "⚒";
        if (!event.getItemStack().isDamageableItem()) {EMC_REPAIRED_SYMBOL = "";}

        if (isStack && SHOW_ON_STACKS) {
            // Show both per-item and total for stacks
            String formattedTotal = String.format("%,d", totalValue);

            event.getToolTip().add(
                    Component.literal("EMC: ")
                            .withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(formattedValue)
                                    .withStyle(ChatFormatting.YELLOW))
                            .append(Component.literal(EMC_SYMBOL)
                                    .withStyle(ChatFormatting.GOLD))
                            .append(Component.literal(" (")
                                    .withStyle(ChatFormatting.DARK_GRAY))
                            .append(Component.literal(formattedTotal + EMC_SYMBOL)
                                    .withStyle(ChatFormatting.YELLOW))
                            .append(Component.literal(" total)")
                                    .withStyle(ChatFormatting.DARK_GRAY))
            );
        } else {
            // Single item or stack display disabled
            event.getToolTip().add(
                    Component.literal("EMC: ")
                            .withStyle(ChatFormatting.GRAY)
                            .append(Component.literal(formattedValue)
                                    .withStyle(ChatFormatting.YELLOW))
                            .append(Component.literal(EMC_REPAIRED_SYMBOL)
                                    .withStyle(ChatFormatting.GOLD))
            );
        }
    }

    /**
     * Add a comparison line showing EMC value relative to common items
     * Helps players understand value scale
     */
    /*
    private static void addComparisonLine(ItemTooltipEvent event, long value) {
        String comparison = getEMCComparison(value);

        if (comparison != null) {
            event.getToolTip().add(
                    Component.literal("  └ ")
                            .withStyle(ChatFormatting.DARK_GRAY)
                            .append(Component.literal("≈ " + comparison)
                                    .withStyle(ChatFormatting.GOLD, ChatFormatting.ITALIC))
            );
        }
    }
     */

    /**
     * Get a human-readable comparison for an EMC value
     * Helps players understand relative value
     */
    /*
    private static String getEMCComparison(long value) {
        // Common reference points
        if (value >= 65536) {
            long netheriteCount = value / 65536;
            return netheriteCount + " Netherite Ingot" + (netheriteCount > 1 ? "s" : "");
        } else if (value >= 16384) {
            long emeraldCount = value / 16384;
            return emeraldCount + " Emerald" + (emeraldCount > 1 ? "s" : "");
        } else if (value >= 8192) {
            long diamondCount = value / 8192;
            return diamondCount + " Diamond" + (diamondCount > 1 ? "s" : "");
        } else if (value >= 2048) {
            long goldCount = value / 2048;
            return goldCount + " Gold Ingot" + (goldCount > 1 ? "s" : "");
        } else if (value >= 256) {
            long ironCount = value / 256;
            return ironCount + " Iron Ingot" + (ironCount > 1 ? "s" : "");
        } else if (value >= 128) {
            long coalCount = value / 128;
            return coalCount + " Coal";
        } else if (value >= 32) {
            long logCount = value / 32;
            return logCount + " Log" + (logCount > 1 ? "s" : "");
        }

        // For very cheap items, compare to cobblestone
        if (value >= 1) {
            return value + " Cobblestone";
        }

        return null;
    }
    */
}