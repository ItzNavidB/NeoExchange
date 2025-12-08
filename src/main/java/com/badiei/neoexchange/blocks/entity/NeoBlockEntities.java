package com.badiei.neoexchange.blocks.entity;

import com.badiei.neoexchange.NeoExchange;
import com.badiei.neoexchange.blocks.NeoBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class NeoBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = 
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, NeoExchange.MOD_ID);

    /**
     * Neo Plate Block Entity - Stores a single Neo Stone
     */
    public static final Supplier<BlockEntityType<NeoPlateEntity>> NEO_PLATE_BE = 
            BLOCK_ENTITIES.register("neo_plate_be", () -> new BlockEntityType<>(
                    NeoPlateEntity::new, NeoBlocks.NEO_PLATE.get()));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
