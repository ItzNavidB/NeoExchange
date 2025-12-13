package com.badiei.neoexchange.network;

import com.badiei.neoexchange.NeoExchange;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * SyncPlayerDataPacket - Unified server-to-client sync packet
 * 
 * This packet sends ALL player EMC-related data in one shot:
 * - Current EMC balance
 * - Learned items
 * - Favorited items
 * 
 * Sent when:
 * - Player opens NeoPlate GUI
 * - Player learns a new item
 * - Player toggles a favorite
 * - EMC balance changes
 * - Player logs in or changes dimension
 */
public record SyncPlayerDataPacket(
        long currentEMC,
        List<String> learnedItems,
        List<String> favoritedItems
) implements CustomPacketPayload {
    
    public static final CustomPacketPayload.Type<SyncPlayerDataPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(NeoExchange.MOD_ID, "sync_player_data"));

    /**
     * StreamCodec for serialization
     * Order: EMC first, then learned items, then favorites
     */
    public static final StreamCodec<ByteBuf, SyncPlayerDataPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG,
                    SyncPlayerDataPacket::currentEMC,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
                    SyncPlayerDataPacket::learnedItems,
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()),
                    SyncPlayerDataPacket::favoritedItems,
                    SyncPlayerDataPacket::new
            );

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Handle receiving this packet on the CLIENT side
     * Delegates to ClientPacketHandlers to avoid loading client-only classes on the server
     */
    public static void handle(SyncPlayerDataPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientPacketHandlers.handleSyncPlayerData(packet);
        });
    }
}
