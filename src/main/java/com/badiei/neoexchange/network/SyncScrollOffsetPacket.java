package com.badiei.neoexchange.network;

import com.badiei.neoexchange.NeoExchange;
import com.badiei.neoexchange.screen.custom.NeoPlateMenu;
import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

/**
 * SyncScrollOffsetPacket - Client tells server current scroll position
 * 
 * This keeps virtual slots in sync between client and server.
 * When client scrolls, it sends this packet so the server updates
 * its virtual slots to match.
 */
public record SyncScrollOffsetPacket(int scrollOffset) implements CustomPacketPayload {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    public static final CustomPacketPayload.Type<SyncScrollOffsetPacket> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(NeoExchange.MOD_ID, "sync_scroll_offset"));
    
    public static final StreamCodec<ByteBuf, SyncScrollOffsetPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            SyncScrollOffsetPacket::scrollOffset,
            SyncScrollOffsetPacket::new
    );
    
    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
    
    /**
     * Handle the packet on the server
     */
    public static void handle(SyncScrollOffsetPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            LOGGER.warn("Received SyncScrollOffsetPacket from non-server player!");
            return;
        }
        
        context.enqueueWork(() -> {
            // Update the server's menu scroll offset
            if (player.containerMenu instanceof NeoPlateMenu menu) {
                
                menu.setScrollOffset(packet.scrollOffset);
            }
        });
    }
}
