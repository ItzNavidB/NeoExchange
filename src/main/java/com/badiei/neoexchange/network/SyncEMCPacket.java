package com.badiei.neoexchange.network;

import com.badiei.neoexchange.NeoExchange;
import com.badiei.neoexchange.emc.EMCHelper;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * SyncEMCPacket - Synchronizes EMC data from server to client
 * 
 * This packet carries a player's EMC balance and sends it to the client
 * so the overlay can display the correct value.
 * 
 * Think of this like a postcard:
 * - The "address" is the packet ID (PACKET_ID)
 * - The "message" is the EMC balance (a long number)
 * - The "mailman" is NeoForge's networking system
 */
public record SyncEMCPacket(long emcBalance) implements CustomPacketPayload {

    /**
     * The packet's unique identifier
     * 
     * This is like an address that tells NeoForge:
     * "Hey, when you see data with this ID, it's a SyncEMCPacket"
     */
    public static final CustomPacketPayload.Type<SyncEMCPacket> PACKET_ID = 
        new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(NeoExchange.MOD_ID, "sync_emc"));

    /**
     * StreamCodec - Tells NeoForge how to read/write this packet over the network
     * 
     * Breaking this down:
     * - ByteBufCodecs.VAR_LONG: "This packet contains a variable-length long number"
     * - .map(): "Take that long and wrap it in a SyncEMCPacket"
     * - SyncEMCPacket::emcBalance: "To get the long back out, call emcBalance()"
     * 
     * Basically: long ↔ SyncEMCPacket conversion
     */
    public static final StreamCodec<ByteBuf, SyncEMCPacket> STREAM_CODEC = 
        ByteBufCodecs.VAR_LONG.map(SyncEMCPacket::new, SyncEMCPacket::emcBalance);

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
    public static void handleClient(SyncEMCPacket packet, IPayloadContext context) {
        // context.enqueueWork() ensures this runs on the main game thread
        // (Network packets arrive on a different thread, but Minecraft
        // operations must happen on the main thread)
        context.enqueueWork(() -> {
            // Get the client player (the one viewing the screen)
            var player = context.player();
            
            if (player != null) {
                // Update the client-side EMC data with the server's value
                EMCHelper.setBalance(player, packet.emcBalance());
                
                // Log for debugging (you can remove this later)
                NeoExchange.LOGGER.debug("Client received EMC sync: {}", packet.emcBalance());
            }
        });
    }
}
