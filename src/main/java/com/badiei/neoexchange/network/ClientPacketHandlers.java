package com.badiei.neoexchange.network;

import com.badiei.neoexchange.NeoExchange;
import com.badiei.neoexchange.emc.EMCHelper;
import com.badiei.neoexchange.emc.PlayerEMCData;
import com.badiei.neoexchange.screen.custom.NeoPlateMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * ClientPacketHandlers - Handles all client-side packet processing
 * 
 * This class is ONLY loaded on the client side. By isolating all
 * client-specific code here, we prevent the server from trying to
 * load classes like LocalPlayer which don't exist server-side.
 * 
 * The @OnlyIn annotation is optional but makes the intent crystal clear.
 */
//@OnlyIn(Dist.CLIENT)
public class ClientPacketHandlers {

    /**
     * Handle unified SyncPlayerDataPacket on client
     * Updates EMC balance, learned items, and favorites all at once
     */
    public static void handleSyncPlayerData(SyncPlayerDataPacket packet) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            PlayerEMCData emcData = EMCHelper.getPlayerEMC(player);

            // Update EMC balance
            emcData.setEMC(packet.currentEMC());

            // Process learned items
            emcData.clearLearneditemsList();
            for (String itemIdString : packet.learnedItems()) {
                try {
                    ResourceLocation itemId = ResourceLocation.parse(itemIdString);
                    Item item = BuiltInRegistries.ITEM.get(itemId).orElseThrow().value();
                    if (item != null && item != Items.AIR) {
                        emcData.learnItem(item);
                    }
                } catch (Exception e) {
                    // Skip invalid items
                }
            }

            // Process favorited items
            emcData.clearFavorites();
            for (String itemIdString : packet.favoritedItems()) {
                try {
                    ResourceLocation itemId = ResourceLocation.parse(itemIdString);
                    Item item = BuiltInRegistries.ITEM.get(itemId).orElseThrow().value();
                    if (item != null && item != Items.AIR) {
                        emcData.favoriteItem(item);
                    }
                } catch (Exception e) {
                    // Skip invalid items
                }
            }

            // If the player has a NeoPlate menu open, refresh it!
            if (player.containerMenu instanceof NeoPlateMenu menu) {
                menu.onLearnedItemsUpdated();
            }

            NeoExchange.LOGGER.debug("Client synced: {} EMC, {} learned items, {} favorites",
                    packet.currentEMC(),
                    packet.learnedItems().size(),
                    packet.favoritedItems().size());
        }
    }

    /**
     * Handle SyncNeoPlateDataPacket on client
     * Updates the NeoPlate menu with server data
     */
    public static void handleSyncNeoPlateData(SyncNeoPlateDataPacket packet) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && player.containerMenu instanceof NeoPlateMenu menu) {
            menu.receiveDataFromServer(
                    packet.playerEMC(),
                    packet.lastEMCGained(),
                    packet.lastEMCLost(),
                    packet.wasNew(),
                    packet.itemName(),
                    packet.itemName2(),
                    packet.itemName3(),
                    packet.displayTimer(),
                    packet.UdisplayTimer(),
                    packet.LdisplayTimer()
            );
            
            // NEW: Check if we need to refresh virtual slots
            if (packet.refreshVirtualSlots()) {
                menu.updateVirtualSlots();
                NeoExchange.LOGGER.debug("Client refreshed virtual slots");
            }
        }
    }
}
