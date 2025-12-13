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

        // ===== SERVER → CLIENT PACKETS =====
        
        /**
         * SyncPlayerDataPacket - Unified sync for EMC + learned items + favorites
         * This replaces the old separate SyncEMCPacket and SyncLearnedItemsPacket
         */
        registrar.playToClient(
                SyncNeoPlateDataPacket.PACKET_ID,
                SyncNeoPlateDataPacket.STREAM_CODEC,
                SyncNeoPlateDataPacket::handleClient
        );
        registrar.playToClient(
                SyncPlayerDataPacket.TYPE,
                SyncPlayerDataPacket.STREAM_CODEC,
                SyncPlayerDataPacket::handle
        );
        
        // ===== CLIENT → SERVER PACKETS =====
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

        // Scroll position sync
        registrar.playToServer(
                SyncScrollOffsetPacket.TYPE,
                SyncScrollOffsetPacket.STREAM_CODEC,
                SyncScrollOffsetPacket::handle
        );

        // Favorite toggle
        registrar.playToServer(
                ToggleFavoritePacket.TYPE,
                ToggleFavoritePacket.STREAM_CODEC,
                ToggleFavoritePacket::handle
        );

        NeoExchange.LOGGER.info("Network packets registered successfully!");
    }
}
