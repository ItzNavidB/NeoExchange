package com.badiei.neoexchange.emc;

import com.badiei.neoexchange.NeoExchange;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * EMCEventHandler - Handles game events related to EMC
 *
 * This class listens for important game events and responds accordingly.
 * The @EventBusSubscriber annotation automatically registers this class
 * to listen for events on the GAME event bus.
 *
 * There are two event buses in NeoForge:
 * - MOD bus: For mod loading events (registration, setup, etc.)
 * - GAME bus: For gameplay events (player actions, world events, etc.)
 */
@EventBusSubscriber(modid = NeoExchange.MOD_ID)
public class EMCEventHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Handle player cloning
     *
     * This event fires when a player "clones" - which happens when:
     * 1. The player dies and respawns
     * 2. The player travels between dimensions (Overworld <-> Nether <-> End)
     * 3. The player returns from the End to the Overworld
     *
     * We need to copy the EMC data from the old player entity to the new one,
     * otherwise the player would lose all their EMC!
     *
     * The "wasDeath" parameter tells us if this was a death or dimension change.
     * You could use this to implement a death penalty (lose 10% EMC on death, etc.)
     */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getEntity() instanceof ServerPlayer) {
            // Get the old player data
            PlayerEMCData oldData = EMCHelper.getPlayerEMC(event.getOriginal());
            // Get the new player data
            PlayerEMCData newData = EMCHelper.getPlayerEMC(event.getEntity());

            // Copy the EMC balance from old to new
            newData.copyFrom(oldData);

            // Optional: Implement death penalty
            if (event.isWasDeath()) {
                // Example: Lose 10% of EMC on death (uncomment to enable)
                // long currentEMC = newData.getEMC();
                // long penaltyEMC = (long)(currentEMC * 0.10);
                // newData.removeEMC(penaltyEMC);

                LOGGER.debug("Player {} died. EMC preserved: {}",
                        event.getEntity().getName().getString(),
                        newData.getEMC());
            } else {
                LOGGER.debug("Player {} changed dimension. EMC preserved: {}",
                        event.getEntity().getName().getString(),
                        newData.getEMC());
            }
        }
    }

    /**
     * Handle player login
     *
     * This fires when a player logs into the server.
     * Good place to:
     * - Send welcome messages
     * - Initialize player data
     * - Sync data to the client
     * - Log for debugging
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            long balance = EMCHelper.getBalance(player);
            LOGGER.info("Player {} logged in with {} EMC",
                    player.getName().getString(),
                    balance);

            // IMPORTANT: Sync EMC to client on login!
            // Without this, the client won't know the player's balance
            EMCHelper.syncEMC(player);

            // Optional: Send a welcome message with their balance
            // player.sendSystemMessage(
            //     Component.literal("Welcome back! Your EMC: ")
            //         .append(Component.literal(EMCHelper.getFormattedBalance(player))
            //             .withStyle(style -> style.withColor(0x00FF00)))
            // );
        }
    }

    /**
     * Handle player logout
     *
     * This fires when a player logs out.
     * Data is automatically saved, but this is a good place to:
     * - Log statistics
     * - Clean up temporary data
     * - Save additional information
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            long balance = EMCHelper.getBalance(player);
            LOGGER.info("Player {} logged out with {} EMC",
                    player.getName().getString(),
                    balance);
        }
    }

    /**
     * Handle player respawn
     *
     * This fires when a player respawns (after death or returning from End).
     * Different from Clone event - this happens AFTER cloning.
     *
     * Good for:
     * - Sending messages to the player
     * - Applying effects
     * - Updating UI
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LOGGER.debug("Player {} respawned with {} EMC",
                    player.getName().getString(),
                    EMCHelper.getBalance(player));
        }
    }
}