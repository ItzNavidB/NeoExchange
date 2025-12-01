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
        boolean wasNew,
        String itemName,
        String itemName2,
        int displayTimer,
        int UdisplayTimer
) implements CustomPacketPayload {

    public static final Type<SyncNeoPlateDataPacket> PACKET_ID =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NeoExchange.MOD_ID, "sync_neo_plate"));

    public static final StreamCodec<ByteBuf, SyncNeoPlateDataPacket> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_LONG, SyncNeoPlateDataPacket::playerEMC,
                    ByteBufCodecs.VAR_LONG, SyncNeoPlateDataPacket::lastEMCGained,
                    ByteBufCodecs.BOOL, SyncNeoPlateDataPacket::wasNew,
                    ByteBufCodecs.STRING_UTF8, SyncNeoPlateDataPacket::itemName,
                    ByteBufCodecs.STRING_UTF8, SyncNeoPlateDataPacket::itemName2,
                    ByteBufCodecs.VAR_INT, SyncNeoPlateDataPacket::displayTimer,
                    ByteBufCodecs.VAR_INT, SyncNeoPlateDataPacket::UdisplayTimer,
                    SyncNeoPlateDataPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return PACKET_ID;
    }

    // Handle on client
    public static void handleClient(SyncNeoPlateDataPacket packet,
                                    net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            // Update the menu on client side
            if (context.player().containerMenu instanceof NeoPlateMenu menu) {
                menu.receiveDataFromServer(
                        packet.playerEMC(),
                        packet.lastEMCGained(),
                        packet.wasNew(),
                        packet.itemName(),
                        packet.itemName2(),
                        packet.displayTimer(),
                        packet.UdisplayTimer()
                );
            }
        });
    }
}