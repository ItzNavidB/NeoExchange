package com.badiei.neoexchange.network;

import com.badiei.neoexchange.screen.custom.NeoPlateMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.badiei.neoexchange.NeoExchange;

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
        boolean refreshVirtualSlots  // NEW: Signal to refresh the virtual slots
) implements CustomPacketPayload {

    public static final Type<SyncNeoPlateDataPacket> PACKET_ID =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NeoExchange.MOD_ID, "sync_neo_plate"));

    // Custom codec because we have more than 9 fields
    public static final StreamCodec<ByteBuf, SyncNeoPlateDataPacket> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public SyncNeoPlateDataPacket decode(ByteBuf buffer) {
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
            boolean refreshVirtualSlots = buffer.readBoolean();  // NEW

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
                    refreshVirtualSlots
            );
        }

        @Override
        public void encode(ByteBuf buffer, SyncNeoPlateDataPacket packet) {
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
            buffer.writeBoolean(packet.refreshVirtualSlots);  // NEW
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
