package com.badiei.neoexchange.emc;

import com.badiei.neoexchange.NeoExchange;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

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

    /**
     * DeferredRegister for AttachmentTypes
     * This is similar to how we register items and blocks
     */
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, NeoExchange.MOD_ID);

    /**
     * The actual EMC data attachment
     *
     * Codec is used for serialization - it defines how to convert
     * our PlayerEMCData object into a format that can be saved to disk
     *
     * RecordCodecBuilder is like saying "this object has these fields,
     * and here's how to encode/decode each one"
     */
    /*public static final Supplier<AttachmentType<PlayerEMCData>> PLAYER_EMC = ATTACHMENT_TYPES.register(
            "player_emc",
            () -> AttachmentType.builder(() -> new PlayerEMCData())
                    .serialize(
                            // This codec defines how to save/load PlayerEMCData
                            RecordCodecBuilder.create(instance -> instance.group(
                                    // Field name: "emc_balance"
                                    // Type: Long (Codec.LONG)
                                    // Getter: get the EMC from the data object
                                    // Setter: create new PlayerEMCData with this EMC value
                                    Codec.LONG.fieldOf("emc_balance").forGetter(PlayerEMCData::getEMC)
                            ).apply(instance, emc -> {
                                PlayerEMCData data = new PlayerEMCData();
                                data.setEMC(emc);
                                return data;
                            }))
                    )
                    .build()
    );*/

    public static final Supplier<AttachmentType<PlayerEMCData>> PLAYER_EMC = ATTACHMENT_TYPES.register(
            "player_emc", () -> AttachmentType.builder(() -> new PlayerEMCData())
                    .serialize(Codec.LONG.xmap(
                            PlayerEMCData::new,           // long -> PlayerEMCData
                            PlayerEMCData::getEMC         // PlayerEMCData -> long
                    ).fieldOf("emc_balance"))
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