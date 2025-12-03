package com.badiei.neoexchange.network;

import com.badiei.neoexchange.NeoExchange;
import com.badiei.neoexchange.emc.EMCHelper;
import com.mojang.logging.LogUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.slf4j.Logger;

/**
 * CreateItemPacket - Client-to-Server packet for EMC-to-Item conversion
 *
 * This packet is sent when a player clicks an item in the Neo Plate GUI
 * requesting to convert their EMC into that item.
 *
 * Security is CRITICAL here - we must validate EVERYTHING on the server!
 * Never trust client data.
 */
public record CreateItemPacket(ResourceLocation itemId, int quantity) implements CustomPacketPayload {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Unique ID for this packet type
    public static final Type<CreateItemPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(NeoExchange.MOD_ID, "create_item"));

    // Maximum items we'll create at once (prevents abuse)
    private static final int MAX_QUANTITY = 64 * 10; // 10 stacks max per click

    /**
     * StreamCodec - How to serialize/deserialize this packet over the network
     *
     * This is NeoForge 1.21's new way of handling packet encoding.
     * It's more type-safe and efficient than the old IMessage system.
     */
    public static final StreamCodec<ByteBuf, CreateItemPacket> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC,      // How to encode ResourceLocation
            CreateItemPacket::itemId,           // Getter for itemId
            ByteBufCodecs.INT,                  // How to encode int
            CreateItemPacket::quantity,         // Getter for quantity
            CreateItemPacket::new               // Constructor to call when decoding
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /**
     * Handle the packet when it arrives at the server
     * This is where the actual EMC-to-item conversion happens
     *
     * @param context The packet context (contains player info, etc.)
     */
    public static void handle(CreateItemPacket packet, IPayloadContext context) {
        // Get the player who sent this packet
        if (!(context.player() instanceof ServerPlayer player)) {
            LOGGER.warn("Received CreateItemPacket from non-server player!");
            return;
        }

        // Execute on the server thread (REQUIRED for world modifications)
        context.enqueueWork(() -> {
            try {
                processItemCreation(packet, player);
            } catch (Exception e) {
                LOGGER.error("Error processing item creation for player {}", player.getName().getString(), e);
            }
        });
    }

    /**
     * The actual logic for creating items from EMC
     * This runs on the server thread
     */
    private static void processItemCreation(CreateItemPacket packet, ServerPlayer player) {
        // === STEP 1: VALIDATE THE ITEM ===
        Item item = BuiltInRegistries.ITEM.get(packet.itemId()).get().value();

        if (item == null) {
            LOGGER.warn("Player {} requested invalid item: {}",
                    player.getName().getString(), packet.itemId());
            return;
        }

        // === STEP 2: VALIDATE THE QUANTITY ===
        int quantity = packet.quantity();

        if (quantity <= 0 || quantity > MAX_QUANTITY) {
            LOGGER.warn("Player {} requested invalid quantity: {}",
                    player.getName().getString(), quantity);
            return;
        }

        // === STEP 3: GET EMC COST ===
        var emcOpt = EMCHelper.getItemEMC(item);
        if (emcOpt.isEmpty()) {
            LOGGER.warn("Player {} requested item with no EMC value: {}",
                    player.getName().getString(), item);
            return;
        }

        long emcPerItem = emcOpt.get();
        long totalCost = emcPerItem * quantity;

        // Check for overflow (someone trying to crash the server)
        if (totalCost < 0 || totalCost / quantity != emcPerItem) {
            LOGGER.warn("Player {} attempted overflow attack with quantity: {}",
                    player.getName().getString(), quantity);
            return;
        }

        // === STEP 4: CHECK IF PLAYER HAS LEARNED THIS ITEM ===
        var emcData = EMCHelper.getPlayerEMC(player);
        if (!emcData.hasLearned(item)) {
            LOGGER.warn("Player {} tried to create unlearned item: {}",
                    player.getName().getString(), item);
            return;
        }

        // === STEP 5: CHECK IF PLAYER HAS ENOUGH EMC ===
        if (!emcData.hasEMC(totalCost)) {
            LOGGER.info("Player {} doesn't have enough EMC (need {}, have {})",
                    player.getName().getString(), totalCost, emcData.getEMC());

            // Send feedback to player
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("Not enough EMC!")
                            .withStyle(net.minecraft.ChatFormatting.RED),
                    true
            );
            return;
        }

        // === STEP 6: DEDUCT EMC ===
        boolean success = EMCHelper.removeEMC(player, totalCost);
        if (!success) {
            LOGGER.error("Failed to remove EMC from player {} (this shouldn't happen!)",
                    player.getName().getString());
            return;
        }

        // === STEP 7: CREATE AND GIVE THE ITEMS ===
        ItemStack stack = new ItemStack(item, quantity);

        // Try to add directly to player inventory
        boolean addedToInventory = player.getInventory().add(stack);

        if (!addedToInventory && !stack.isEmpty()) {
            // Inventory was full, drop the items at player's feet
            ItemEntity itemEntity = new ItemEntity(
                    player.level(),
                    player.getX(),
                    player.getY(),
                    player.getZ(),
                    stack
            );

            // Set pickup delay so item doesn't immediately get picked up
            itemEntity.setPickUpDelay(0);
            player.level().addFreshEntity(itemEntity);

            LOGGER.info("Dropped {} x{} at player {}'s feet (inventory full)",
                    item, quantity, player.getName().getString());
        }

        // === STEP 8: SYNC EMC BACK TO CLIENT ===
        EMCHelper.syncEMC(player);

        // === STEP 9: FEEDBACK ===
        // Play success sound
        player.level().playSound(
                null,  // null = play for all nearby players
                player.getX(),
                player.getY(),
                player.getZ(),
                SoundEvents.EXPERIENCE_ORB_PICKUP,
                SoundSource.PLAYERS,
                0.5f,  // volume
                1.2f   // pitch (slightly higher = satisfying!)
        );

        // Log success
        LOGGER.info("Player {} created {} x{} for {} EMC",
                player.getName().getString(), item, quantity, totalCost);
    }
}