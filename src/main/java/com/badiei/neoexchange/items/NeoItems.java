package com.badiei.neoexchange.items;

import com.badiei.neoexchange.NeoExchange;
import com.badiei.neoexchange.economy.NeoStoneType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public class NeoItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NeoExchange.MOD_ID);




    // Original Neo Stone - keeping this for now (maybe for testing or crafting material)
    public static final DeferredItem<Item> NEO_STONE = ITEMS.registerItem("neo_stone",
            (properties) -> new Item(properties) {
                @Override
                public @NotNull Component getName(@NotNull ItemStack item) {
                    return this.getName();
                }

                @Override
                public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay tooltipDisplay, Consumer<Component> tooltipAdder, TooltipFlag flag) {
                    super.appendHoverText(stack, context, tooltipDisplay, tooltipAdder, flag);
                    tooltipAdder.accept(Component.literal("The key stone to Neo Exchange").withStyle(ChatFormatting.ITALIC).withStyle(ChatFormatting.GRAY));
                }
            }

    );

    // ========== THE FIVE TIER STONES ==========
    // Each stone uses our custom NeoStoneItem class with colored names and special properties

    /**
     * TIER 1: COMMON STONE (White/Gray)
     * - Max EMC: 256
     * - Access: Basic items only
     * - No special features
     * - Crafting: 4 Iron + 4 Coal + 1 Redstone
     */
    public static final DeferredItem<NeoStoneItem> COMMON_STONE = ITEMS.registerItem("common_stone",
        properties -> new NeoStoneItem(properties, NeoStoneType.COMMON)
    );
    public static final DeferredItem<NeoStoneItem> UNCOMMON_STONE = ITEMS.registerItem("uncommon_stone",
        properties -> new NeoStoneItem(properties, NeoStoneType.UNCOMMON)
    );
    public static final DeferredItem<NeoStoneItem> RARE_STONE = ITEMS.registerItem("rare_stone",
        properties -> new NeoStoneItem(properties, NeoStoneType.RARE)
    );
    public static final DeferredItem<NeoStoneItem> EPIC_STONE = ITEMS.registerItem("epic_stone",
        properties -> new NeoStoneItem(properties, NeoStoneType.EPIC)
    );
    public static final DeferredItem<NeoStoneItem> LEGENDARY_STONE = ITEMS.registerItem("legendary_stone",
        properties -> new NeoStoneItem(properties.fireResistant(), NeoStoneType.LEGENDARY)
    );
    public static final DeferredItem<NeoStoneItem> MYTHIC_STONE = ITEMS.registerItem("mythic_stone",
        properties -> new NeoStoneItem(properties.fireResistant(), NeoStoneType.MYTHIC)
    );

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
