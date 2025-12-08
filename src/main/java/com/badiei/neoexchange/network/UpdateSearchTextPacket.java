package com.badiei.neoexchange.network;

import com.badiei.neoexchange.NeoExchange;
import com.badiei.neoexchange.screen.custom.NeoPlateMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * UpdateSearchTextPacket - Client-to-Server packet for search filter
 *
 * When the player types in the search box on the client, this packet
 * tells the server to update its copy of the menu's search text.
 * 
 * This ensures both client and server have the same filtered item list,
 * preventing desync issues when clicking items.
 */
public record UpdateSearchTextPacket(String searchText) implements CustomPacketPayload {
    
    // Unique ID for this packet type
    public static final Type<UpdateSearchTextPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NeoExchange.MOD_ID, "update_search_text"));

    /**
     * StreamCodec - How to serialize/deserialize this packet
     * 
     * We're just sending a single string, so this is simple!
     */
    public static final StreamCodec<ByteBuf, UpdateSearchTextPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,          // How to encode String
            UpdateSearchTextPacket::searchText, // Getter for searchText
            UpdateSearchTextPacket::new         // Constructor
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Handle the packet when it arrives at the server
     * 
     * This updates the server's copy of the menu's search filter
     * so the virtual slot items match between client and server.
     */
    public static void handle(UpdateSearchTextPacket packet, IPayloadContext context) {
        // Only process on server side
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }

        // Execute on the server thread
        context.enqueueWork(() -> {
            // Update the search text in the player's open menu
            if (player.containerMenu instanceof NeoPlateMenu menu) {
                menu.setSearchText(packet.searchText());
                
                NeoExchange.LOGGER.debug("Server updated search text for player {} to: '{}'", 
                        player.getName().getString(), packet.searchText());
            }
        });
    }
}
