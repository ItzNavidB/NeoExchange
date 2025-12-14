package com.badiei.neoexchange.network;

import com.badiei.neoexchange.screen.custom.NeoPlateMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.badiei.neoexchange.NeoExchange;

/**
 * SyncNeoPlateDataPacket - Synchronizes Neo Plate GUI state from server to client
 *
 * This packet is sent whenever the server needs to update the client's GUI state.
 * It includes all the display information (EMC balance, notifications) AND the
 * template item filter state.
 *
 * NEW in this version:
 * - templateItemId: Syncs which item is in the template slot
 *   - When player places an item in template slot, server sends this to client
 *   - Client can then filter its virtual grid to match server's filter
 *   - This prevents desync when clicking items!
 */
public record SyncNeoPlateDataPacket(
        long playerEMC,
        long lastEMCGained,
        long lastEMCLost,
        boolean wasNew,
        String itemName,
        String itemName2,
        String itemName3,
        int displayTimer,
        int UdisplayTimer,
        int LdisplayTimer,
        boolean refreshVirtualSlots,  // Signal to refresh the virtual slots
        int maxEMC,
        String templateItemId  // NEW: Template item filter (ResourceLocation as string, "" if no template)
) implements CustomPacketPayload {

    public static final Type<SyncNeoPlateDataPacket> PACKET_ID =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NeoExchange.MOD_ID, "sync_neo_plate"));

    /**
     * Custom codec for encoding/decoding this packet
     *
     * We need a custom codec because we have more than 9 fields (the limit for auto-generated codecs).
     * This manually encodes each field in order, then decodes them in the same order.
     *
     * IMPORTANT: The order must match EXACTLY in encode() and decode()!
     */
    public static final StreamCodec<ByteBuf, SyncNeoPlateDataPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SyncNeoPlateDataPacket decode(ByteBuf buffer) {
            // Decode each field in the EXACT same order as encode()
            long playerEMC = ByteBufCodecs.VAR_LONG.decode(buffer);
            long lastEMCGained = ByteBufCodecs.VAR_LONG.decode(buffer);
            long lastEMCLost = ByteBufCodecs.VAR_LONG.decode(buffer);
            boolean wasNew = buffer.readBoolean();
            String itemName = ByteBufCodecs.STRING_UTF8.decode(buffer);
            String itemName2 = ByteBufCodecs.STRING_UTF8.decode(buffer);
            String itemName3 = ByteBufCodecs.STRING_UTF8.decode(buffer);
            int displayTimer = ByteBufCodecs.VAR_INT.decode(buffer);
            int UdisplayTimer = ByteBufCodecs.VAR_INT.decode(buffer);
            int LdisplayTimer = ByteBufCodecs.VAR_INT.decode(buffer);
            boolean refreshVirtualSlots = buffer.readBoolean();
            int maxEMC = ByteBufCodecs.VAR_INT.decode(buffer);
            String templateItemId = ByteBufCodecs.STRING_UTF8.decode(buffer);  // NEW: Read template item ID

            return new SyncNeoPlateDataPacket(
                    playerEMC,
                    lastEMCGained,
                    lastEMCLost,
                    wasNew,
                    itemName,
                    itemName2,
                    itemName3,
                    displayTimer,
                    UdisplayTimer,
                    LdisplayTimer,
                    refreshVirtualSlots,
                    maxEMC,
                    templateItemId  // NEW: Include in record
            );
        }

        @Override
        public void encode(ByteBuf buffer, SyncNeoPlateDataPacket packet) {
            // Encode each field in order
            ByteBufCodecs.VAR_LONG.encode(buffer, packet.playerEMC);
            ByteBufCodecs.VAR_LONG.encode(buffer, packet.lastEMCGained);
            ByteBufCodecs.VAR_LONG.encode(buffer, packet.lastEMCLost);
            buffer.writeBoolean(packet.wasNew);
            ByteBufCodecs.STRING_UTF8.encode(buffer, packet.itemName);
            ByteBufCodecs.STRING_UTF8.encode(buffer, packet.itemName2);
            ByteBufCodecs.STRING_UTF8.encode(buffer, packet.itemName3);
            ByteBufCodecs.VAR_INT.encode(buffer, packet.displayTimer);
            ByteBufCodecs.VAR_INT.encode(buffer, packet.UdisplayTimer);
            ByteBufCodecs.VAR_INT.encode(buffer, packet.LdisplayTimer);
            buffer.writeBoolean(packet.refreshVirtualSlots);
            ByteBufCodecs.VAR_INT.encode(buffer, packet.maxEMC);
            ByteBufCodecs.STRING_UTF8.encode(buffer, packet.templateItemId);  // NEW: Write template item ID
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return PACKET_ID;
    }

    // Handle on client
    public static void handleClient(SyncNeoPlateDataPacket packet,
                                    net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientPacketHandlers.handleSyncNeoPlateData(packet);
        });
    }
}