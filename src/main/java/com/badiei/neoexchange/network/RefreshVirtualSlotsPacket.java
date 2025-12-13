package com.badiei.neoexchange.network;

import com.badiei.neoexchange.screen.custom.NeoPlateMenu;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.badiei.neoexchange.NeoExchange;

/**
 * Packet sent from SERVER to CLIENT when virtual slots need refreshing
 * 
 * This is sent when:
 * - Neo Stone is placed/removed (changes maxEMC filter)
 * - Item is learned (new item appears in grid)
 * - Item is unlearned (item disappears from grid)
 * 
 * The client responds by calling updateVirtualSlots() on its menu
 */
public record RefreshVirtualSlotsPacket() implements CustomPacketPayload {
    
    public static final Type<RefreshVirtualSlotsPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NeoExchange.MOD_ID, "refresh_virtual_slots"));

    public static final StreamCodec<ByteBuf, RefreshVirtualSlotsPacket> STREAM_CODEC =
            StreamCodec.unit(new RefreshVirtualSlotsPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Handle this packet on the client
     * 
     * When received, tells the client's NeoPlateMenu to refresh its virtual slots
     */
    public static void handleClient(RefreshVirtualSlotsPacket packet) {
        // This runs on the client's network thread
        // We need to schedule it on the main client thread
        net.minecraft.client.Minecraft.getInstance().execute(() -> {
            var player = net.minecraft.client.Minecraft.getInstance().player;
            if (player != null && player.containerMenu instanceof NeoPlateMenu menu) {
                NeoExchange.LOGGER.debug("Client received refresh virtual slots packet");
                menu.updateVirtualSlots();
            }
        });
    }
}
