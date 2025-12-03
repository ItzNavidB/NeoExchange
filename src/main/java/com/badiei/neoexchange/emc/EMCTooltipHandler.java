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
 * EMCTooltipHandler - Advanced version with extra features
 *
 * Features:
 * - Shows EMC value on item hover
 * - Shows total EMC for stacks
 * - Hold SHIFT for additional EMC information
 * - Configurable styling and format
 * - Shows if item is learned (for future learning system)
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
 * With SHIFT held:
 * Diamond
 * EMC: 8,192 ✦
 * └ Can be transmuted
 * └ Stack value: 524,288 ✦
 */
@EventBusSubscriber(modid = NeoExchange.MOD_ID, value = Dist.CLIENT)
public class EMCTooltipHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Configuration - you can move these to a config file later
    private static final boolean SHOW_TOOLTIPS = true;
    private static final boolean SHOW_ON_STACKS = true;
    private static final boolean SHOW_ADVANCED_ON_SHIFT = true;
    private static final String EMC_SYMBOL = "";

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

        // Get the EMC value for this item
        Optional<Long> emcValue = EMCHelper.getItemEMC(stack.getItem());

        // If the item has no EMC value, optionally show "No EMC Value"
        if (emcValue.isEmpty()) {
            // Uncomment if you want to show items have no value:
            // if (Screen.hasShiftDown()) {
            //     event.getToolTip().add(Component.literal("No EMC Value")
            //         .withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
            // }
            return;
        }

        long value = emcValue.get();
        boolean isStack = stack.getCount() > 1;
        boolean shiftDown = Minecraft.getInstance().hasShiftDown();

        // Add a blank line before EMC info for visual separation
        // (Only if there are other tooltip lines)
        if (event.getToolTip().size() > 1) {
            event.getToolTip().add(Component.empty());
        }

        // Basic EMC line - always show
        addBasicEMCLine(event, value, stack.getCount(), isStack);

        // Advanced info when holding shift
        if (shiftDown && SHOW_ADVANCED_ON_SHIFT) {
            addAdvancedEMCInfo(event, value, stack.getCount());
        }
    }

    /**
     * Add the basic EMC value line
     */
    private static void addBasicEMCLine(ItemTooltipEvent event, long value, int count, boolean isStack) {
        if (event.getItemStack().isDamageableItem()) {
            ItemStack item = event.getItemStack();
            int currentDurability = item.getMaxDamage() - item.getDamageValue();
            int maxDurability = item.getMaxDamage();
            float durabilityRatio = (float) currentDurability / maxDurability;
            value = (long) (value * durabilityRatio);
        }
        String formattedValue = String.format("%,d", value);
        if (isStack && SHOW_ON_STACKS) {
            // Show both per-item and total for stacks
            long totalValue = value * count;

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
    private static void addAdvancedEMCInfo(ItemTooltipEvent event, long value, int count) {
        // Show transmutation status
        /*
        event.getToolTip().add(
                Component.literal("  └ ")
                        .withStyle(ChatFormatting.DARK_GRAY)
                        .append(Component.literal("Can be transmuted")
                                .withStyle(ChatFormatting.GREEN, ChatFormatting.ITALIC))
        );
        */

        // Show conversion info
        /*
        if (count > 1) {
            long totalValue = value * count;
            String formattedTotal = String.format("%,d", totalValue);

            event.getToolTip().add(
                    Component.literal("  └ ")
                            .withStyle(ChatFormatting.DARK_GRAY)
                            .append(Component.literal("Stack worth: " + formattedTotal + EMC_SYMBOL)
                                    .withStyle(ChatFormatting.AQUA, ChatFormatting.ITALIC))
            );
        }
         */

        // Show how many of this item you could buy with common materials
        // Example: "≈ 8 diamonds" or "≈ 256 cobblestone"
        //addComparisonLine(event, value);

        // Future: Show if item is "learned" for transmutation
        // event.getToolTip().add(
        //     Component.literal("  └ ")
        //         .withStyle(ChatFormatting.DARK_GRAY)
        //         .append(Component.literal("Learned")
        //             .withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC))
        // );
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