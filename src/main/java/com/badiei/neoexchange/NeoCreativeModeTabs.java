package com.badiei.neoexchange;

import com.badiei.neoexchange.blocks.NeoBlocks;
import com.badiei.neoexchange.economy.NeoStoneType;
import com.badiei.neoexchange.emc.EMCRegistry;
import com.badiei.neoexchange.items.NeoItems;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

import java.util.Arrays;
import java.util.function.Supplier;


public class NeoCreativeModeTabs {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TAB = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, NeoExchange.MOD_ID);

    public static final Supplier<CreativeModeTab> NEO_TAB = CREATIVE_MODE_TAB.register("neo_tab",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(NeoItems.NEO_STONE.get()))
                    .title(Component.translatable("creativetab.neoexchange.neo_tab"))
                    .displayItems((itemDisplayParameters, output) -> {
                        // Original items
                        output.accept(NeoItems.NEO_STONE);
                        output.accept(NeoBlocks.NEO_PLATE);
                        
                        // The five tier stones in order
                        for (DeferredHolder<Item, ? extends Item> item : NeoItems.ITEMS.getEntries()) {
                            output.accept(item.get());
                        }
                    }).build());

    public static final Supplier<CreativeModeTab> NEO_TAB2 = CREATIVE_MODE_TAB.register("neo_tab2",
            () -> CreativeModeTab.builder().icon(() -> new ItemStack(Items.DIAMOND_ORE))
                    .withTabsBefore(ResourceLocation.fromNamespaceAndPath(NeoExchange.MOD_ID,"neo_tab"))
                    .title(Component.translatable("creativetab.neoexchange.neo_tab2"))
                    .displayItems((itemDisplayParameters, output) -> {
                        for (Item item : EMCRegistry.getItemsWithoutEMC()) {
                            if (item != Items.AIR) {
                                try {
                                    output.accept(item);
                                } catch (Exception e) {
                                    LOGGER.info("Theese items should be added, but causing crash: {}", item);
                                }
                            }
                        }
                    }).build());


    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TAB.register(eventBus);
    }



}

