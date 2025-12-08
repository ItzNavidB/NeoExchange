package com.badiei.neoexchange.network;

import com.badiei.neoexchange.NeoExchange;
import com.badiei.neoexchange.emc.EMCHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

public record SyncLearnedItemsPacket(List<String> learnedItems) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SyncLearnedItemsPacket> PACKET_ID =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(NeoExchange.MOD_ID, "sync_learned_items"));

    public static final StreamCodec<ByteBuf, SyncLearnedItemsPacket> STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).map(
                    SyncLearnedItemsPacket::new,           // List<String> -> Packet
                    SyncLearnedItemsPacket::learnedItems   // Packet -> List<String>
            );

    /**
     * Returns this packet's ID
     * Required by CustomPacketPayload interface
     */
    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return PACKET_ID;
    }

    /**
     * Handle receiving this packet on the CLIENT side
     *
     * This method runs when the client receives the packet from the server.
     * It updates the client-side player's EMC data with the value from the server.
     *
     * @param context Provides access to the player and network context
     */
    public static void handleClient(SyncLearnedItemsPacket packet, IPayloadContext context) {

        context.enqueueWork(() -> {
            var player = context.player();
            var data = EMCHelper.getPlayerEMC(player);
            data.clearLearneditemsList();
            for (String itemIdString : packet.learnedItems) {
                try {
                    ResourceLocation itemId = ResourceLocation.parse(itemIdString);
                    BuiltInRegistries.ITEM.getOptional(itemId).ifPresent(data::learnItem);
                }
                catch (Exception e) {
                    NeoExchange.LOGGER.warn("Failed to sync learned item: {}", itemIdString);
                }
            }
            NeoExchange.LOGGER.debug("Client synced {} learned items", packet.learnedItems().size());
        });
    }
}
