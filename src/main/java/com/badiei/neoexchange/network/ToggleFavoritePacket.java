package com.badiei.neoexchange.network;

import com.badiei.neoexchange.NeoExchange;
import com.badiei.neoexchange.emc.EMCHelper;
import com.badiei.neoexchange.emc.PlayerEMCData;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Single packet for favoriting - that's all we need!
 *
 * Flow:
 * 1. Client: Player middle-clicks item
 * 2. Client: Sends this packet
 * 3. Server: Toggles favorite in PlayerEMCData
 * 4. Server: Syncs via existing learned items packet (with favorites included!)
 */
public record ToggleFavoritePacket(ResourceLocation itemId) implements CustomPacketPayload {

    public static final Type<ToggleFavoritePacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NeoExchange.MOD_ID, "toggle_favorite"));

    public static final StreamCodec<ByteBuf, ToggleFavoritePacket> STREAM_CODEC =
            StreamCodec.composite(
                    ResourceLocation.STREAM_CODEC,
                    ToggleFavoritePacket::itemId,
                    ToggleFavoritePacket::new
            );

    // Convenience constructor
    public ToggleFavoritePacket(Item item) {
        this(BuiltInRegistries.ITEM.getKey(item));
    }

    @Override
    public Type<ToggleFavoritePacket> type() {
        return TYPE;
    }

    public static void handle(ToggleFavoritePacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer serverPlayer) {
                Item item = BuiltInRegistries.ITEM.get(packet.itemId()).orElseThrow().value();

                if (item == null) {
                    return;
                }

                // Toggle favorite
                PlayerEMCData emcData = EMCHelper.getPlayerEMC(serverPlayer);
                boolean nowFavorited = emcData.toggleFavorite(item);

                // Sync to client using EXISTING sync packet
                EMCHelper.syncLearnedItems(serverPlayer);  // ← This now includes favorites!

                NeoExchange.LOGGER.info("Player {} {} item: {}",
                        serverPlayer.getName().getString(),
                        nowFavorited ? "favorited" : "unfavorited",
                        item);
            }
        });
    }
}