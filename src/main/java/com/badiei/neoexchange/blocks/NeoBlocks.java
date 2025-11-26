package com.badiei.neoexchange.blocks;

import com.badiei.neoexchange.NeoExchange;
import com.badiei.neoexchange.blocks.custom.NeoPlateTemplate;
import com.badiei.neoexchange.items.NeoItems;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Function;

public class NeoBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NeoExchange.MOD_ID);

    public static final DeferredBlock<Block> NEO_PLATE = registerBlock("neo_plate",
            NeoPlateTemplate::new, BlockBehaviour.Properties.of()
                    .strength(2.0F)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)
                    .lightLevel(state -> 2));

    private static <B extends Block> DeferredBlock<B> registerBlock(String name, Function<BlockBehaviour.Properties, ? extends B> blockFactory, BlockBehaviour.Properties blockProperties) {
        DeferredBlock<B> block = BLOCKS.registerBlock(name, blockFactory, blockProperties);
        registerBlockItem(name, block);
        return block;
    }

    private static <B extends Block> void registerBlockItem(String name, DeferredBlock<B> block) {
        NeoItems.ITEMS.registerSimpleBlockItem(name, block);
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
