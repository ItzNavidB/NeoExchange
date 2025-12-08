package com.badiei.neoexchange.emc;

import com.badiei.neoexchange.NeoExchange;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * NeoAttachments - Registers data attachments for entities
 *
 * In NeoForge, "Attachments" are the modern way to attach custom data
 * to entities, blocks, chunks, etc. They replaced the old "Capability" system.
 *
 * Think of attachments like sticky notes you can put on entities.
 * Each player will have a "sticky note" with their EMC balance on it.
 *
 * Why use attachments instead of just storing data in a map?
 * - Automatic serialization (saving/loading)
 * - Efficient memory usage
 * - Proper lifecycle management
 * - Syncs between client/server automatically
 */
public class NeoAttachments {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * DeferredRegister for AttachmentTypes
     * This is similar to how we register items and blocks
     */
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, NeoExchange.MOD_ID);

    public static final Supplier<AttachmentType<PlayerEMCData>> PLAYER_EMC = ATTACHMENT_TYPES.register(
            "player_emc", () -> AttachmentType.builder(() -> new PlayerEMCData())
                    .serialize(RecordCodecBuilder.<PlayerEMCData>create(instance ->
                            instance.group(
                                    Codec.LONG.fieldOf("emc_balance").forGetter(PlayerEMCData::getEMC),
                                    Codec.STRING.listOf().fieldOf("learned_items").forGetter(data ->
                                            data.getLearnedItems().stream()
                                                    .map(ResourceLocation::toString)
                                                    .toList()
                                    )
                            ).apply(instance, (emc, items) -> {
                                PlayerEMCData data = new PlayerEMCData(emc);
                                items.forEach(itemStr -> {
                                    try {
                                        ResourceLocation id = ResourceLocation.parse(itemStr);
                                        BuiltInRegistries.ITEM.getOptional(id).ifPresent(data::learnItem);
                                    } catch (Exception e) {
                                        LOGGER.warn("Invalid item ID: {}", itemStr);
                                    }
                                });
                                return data;
                            })
                    ).fieldOf("player_emc_data"))  // <-- This is the key addition!
                    .build()
    );

    /**
     * Register all attachments to the event bus
     * This must be called during mod initialization
     */
    public static void register(IEventBus eventBus) {
        ATTACHMENT_TYPES.register(eventBus);
    }
}