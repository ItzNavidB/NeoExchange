package com.badiei.neoexchange.network;

import com.badiei.neoexchange.NeoExchange;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * NetworkHandler - Registers all network packets for the mod
 * 
 * This class is responsible for telling NeoForge:
 * "Hey, here are the packets my mod uses, and here's how to handle them"
 * 
 * The @EventBusSubscriber annotation means this class will automatically
 * listen for mod events during startup.
 */
@EventBusSubscriber(modid = NeoExchange.MOD_ID)
public class NetworkHandler {

    /**
     * Called automatically during mod initialization
     * 
     * This method runs when NeoForge is setting up networking.
     * We use it to register our packets.
     * 
     * @param event The registration event
     */
    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        // Get the registrar - this is the tool we use to register packets
        // The "1.0.0" is a protocol version - if you change packet structure,
        // increment this to prevent mismatched clients/servers from connecting
        PayloadRegistrar registrar = event.registrar("1.0.0");

        /**
         * Register the SyncEMCPacket
         * 
         * Breaking down this chain of method calls:
         * 
         * 1. playToClient() - This packet travels FROM server TO client
         *    (There's also clientToServer for packets going the other way)
         * 
         * 2. SyncEMCPacket.PACKET_ID - The unique identifier for this packet
         * 
         * 3. SyncEMCPacket.STREAM_CODEC - How to encode/decode the packet
         * 
         * 4. SyncEMCPacket::handleClient - The method to call when received
         * 
         * Think of this like setting up a mail route:
         * - "This mail goes from server to client"
         * - "It has this address on it"
         * - "Here's how to read the contents"
         * - "When it arrives, call this handler"
         */
        registrar.playToClient(
            SyncEMCPacket.PACKET_ID,
            SyncEMCPacket.STREAM_CODEC,
            SyncEMCPacket::handleClient
        );
        registrar.playToClient(
                SyncNeoPlateDataPacket.PACKET_ID,
                SyncNeoPlateDataPacket.STREAM_CODEC,
                SyncNeoPlateDataPacket::handleClient
        );
        registrar.playToClient(
                SyncLearnedItemsPacket.PACKET_ID,
                SyncLearnedItemsPacket.STREAM_CODEC,
                SyncLearnedItemsPacket::handleClient
        );
        
        // Client-to-Server packets
        registrar.playToServer(
                CreateItemPacket.TYPE,
                CreateItemPacket.STREAM_CODEC,
                CreateItemPacket::handle
        );
        registrar.playToServer(
                UpdateSearchTextPacket.TYPE,
                UpdateSearchTextPacket.STREAM_CODEC,
                UpdateSearchTextPacket::handle
        );

        NeoExchange.LOGGER.info("Network packets registered successfully!");
    }
}
