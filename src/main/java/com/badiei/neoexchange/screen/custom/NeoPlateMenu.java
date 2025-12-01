package com.badiei.neoexchange.screen.custom;

import com.badiei.neoexchange.blocks.NeoBlocks;
import com.badiei.neoexchange.blocks.entity.NeoPlateEntity;
import com.badiei.neoexchange.datagen.ModTags;
import com.badiei.neoexchange.economy.NeoStoneType;
import com.badiei.neoexchange.emc.EMCHelper;
import com.badiei.neoexchange.emc.EMCRegistry;
import com.badiei.neoexchange.emc.PlayerEMCData;
import com.badiei.neoexchange.items.NeoItems;
import com.badiei.neoexchange.items.NeoStoneItem;
import com.badiei.neoexchange.network.SyncNeoPlateDataPacket;
import com.badiei.neoexchange.screen.ModMenuTypes;
import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Optional;

public class NeoPlateMenu extends AbstractContainerMenu {
    public static final Logger LOGGER = LogUtils.getLogger();
    public final NeoPlateEntity blockEntity;
    private final Level level;
    private final Player player;

    // Data tracking for GUI display
    private long playerEMCBalance = 0;      // Current player EMC
    private long lastEMCGained = 0;         // EMC from last burn
    private boolean lastItemWasNew = false; // Was last item newly learned?
    private String lastItemName = "";       // Name of last burned item
    private String lastItemName2 = "";       // Name of last burned item

    // For the "fade out" effect on gained EMC
    private int emcGainedDisplayTimer = 0;
    private static final int EMC_DISPLAY_DURATION = 100; // 3 seconds (60 ticks)

    // For the "fade out" effect on unlearned items
    private int unlearnDisplayTimer = 0;
    private static final int UNLEARN_DISPLAY_DURATION = 100; // 3 seconds (60 ticks)

    public NeoPlateMenu(int containerId, Inventory inv, FriendlyByteBuf extraData) {
        this(containerId, inv, inv.player.level().getBlockEntity(extraData.readBlockPos()));
    }

    public NeoPlateMenu(int containerId, Inventory inv, BlockEntity blockEntity) {
        super(ModMenuTypes.NEO_PLATE_MENU.get(), containerId);
        this.blockEntity = ((NeoPlateEntity) blockEntity);
        this.level = inv.player.level();
        this.player = inv.player;

        // Initialize player EMC balance
        if (!level.isClientSide()) {
            playerEMCBalance = EMCHelper.getBalance(player);

            // Send initial data to client
            sendDataToClient();
        }

        addPlayerInventory(inv, 27, 33);
        addPlayerHotbar(inv, 27, 33);
        //Stones Item Slot
        this.addSlot(new SlotItemHandler(this.blockEntity.inventory, 0, 43, 49) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                boolean allowed = stack.is(ModTags.Items.USEABLE_STONES) || stack.getItem().equals(NeoItems.NEO_STONE.asItem());
                //LOGGER.info("Checking {} -> allowed: {}", stack.getItem().getName(), allowed);
                //LOGGER.info("Checking for tag: {}", ModTags.Items.USEABLE_STONES);
                return allowed;
            }
        });
        //Burner Item Slot
        this.addSlot(new SlotItemHandler(this.blockEntity.inventory, 1, 107, 97) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                boolean allowed = EMCRegistry.getInstance().hasEMC(stack.getItem());
                return allowed;
            }
            @Override
            public void setChanged() {
                super.setChanged();

                if (level.isClientSide()) {return;}

                ItemStack item = this.getItem();
                processBurnerSlot(item);
            }
        });
        //Unlearn Item Slot
        this.addSlot(new SlotItemHandler(this.blockEntity.inventory, 2, 89, 97) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                boolean allowed = EMCRegistry.getItemsWithEMC().contains(stack.getItem());
                return allowed;
            }
            @Override
            public void setChanged() {
                super.setChanged();

                if (level.isClientSide()) {return;}

                ItemStack item = this.getItem();
                processUnlearnSlot(item);
            }
        });
    }


    private static final int HOTBAR_SLOT_COUNT = 9;
    private static final int PLAYER_INVENTORY_ROW_COUNT = 3;
    private static final int PLAYER_INVENTORY_COLUMN_COUNT = 9;
    private static final int PLAYER_INVENTORY_SLOT_COUNT = PLAYER_INVENTORY_COLUMN_COUNT * PLAYER_INVENTORY_ROW_COUNT;
    private static final int VANILLA_SLOT_COUNT = HOTBAR_SLOT_COUNT + PLAYER_INVENTORY_SLOT_COUNT;
    private static final int VANILLA_FIRST_SLOT_INDEX = 0;
    private static final int TE_INVENTORY_FIRST_SLOT_INDEX = VANILLA_FIRST_SLOT_INDEX + VANILLA_SLOT_COUNT;

    private static final int TE_INVENTORY_SLOT_COUNT = 3;  // must be the number of slots you have!


    @Override
    public ItemStack quickMoveStack(Player playerIn, int pIndex) {
        Slot sourceSlot = slots.get(pIndex);
        if (sourceSlot == null || !sourceSlot.hasItem()) return ItemStack.EMPTY;  //EMPTY_ITEM
        ItemStack sourceStack = sourceSlot.getItem();
        ItemStack copyOfSourceStack = sourceStack.copy();

        // Check if the slot clicked is one of the vanilla container slots
        if (pIndex < VANILLA_FIRST_SLOT_INDEX + VANILLA_SLOT_COUNT) {
            // This is a vanilla container slot so merge the stack into the tile inventory
            if (!moveItemStackTo(sourceStack, TE_INVENTORY_FIRST_SLOT_INDEX, TE_INVENTORY_FIRST_SLOT_INDEX
                    + TE_INVENTORY_SLOT_COUNT - 1, false)) {
                return ItemStack.EMPTY;  // EMPTY_ITEM
            }
        } else if (pIndex < TE_INVENTORY_FIRST_SLOT_INDEX + TE_INVENTORY_SLOT_COUNT) {
            // This is a TE slot so merge the stack into the players inventory
            if (!moveItemStackTo(sourceStack, VANILLA_FIRST_SLOT_INDEX, VANILLA_FIRST_SLOT_INDEX + VANILLA_SLOT_COUNT, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            System.out.println("Invalid slotIndex:" + pIndex);
            return ItemStack.EMPTY;
        }
        // If stack size == 0 (the entire stack was moved) set slot contents to null
        if (sourceStack.getCount() == 0) {
            sourceSlot.set(ItemStack.EMPTY);
        } else {
            sourceSlot.setChanged();
        }
        if (sourceStack.getItem() instanceof NeoStoneItem) {
            sourceSlot.onTake(playerIn, sourceStack);
            return copyOfSourceStack;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(ContainerLevelAccess.create(level, blockEntity.getBlockPos()), player, NeoBlocks.NEO_PLATE.get());
    }

    private void addPlayerInventory(Inventory playerInventory, int x, int y) {
        for (int i = 0; i < 3; ++i) {
            for (int l = 0; l < 9; ++l) {
                this.addSlot(new Slot(playerInventory, l + i * 9 + 9, 8 + l * 18 + x, 84 + i * 18 + y));
            }
        }
    }

    private void addPlayerHotbar(Inventory playerInventory, int x, int y) {
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18 + x, 142 + y));
        }
    }

    public static int getSlotSize() {
        return TE_INVENTORY_SLOT_COUNT;
    }
    public int getContainerSize() {return this.blockEntity.getContainerSize();}


    @Override
    public void broadcastChanges() {
        // Countdown the display timer for "EMC Gained" message
        if (emcGainedDisplayTimer > 0) {
            emcGainedDisplayTimer--;

            // Clear the message when timer runs out
            if (emcGainedDisplayTimer <= 0) {
                lastEMCGained = 0;
                lastItemWasNew = false;
                lastItemName = "";
            }
        }
        if (unlearnDisplayTimer > 0) {
            unlearnDisplayTimer--;

            // Clear the message when timer runs out
            if (unlearnDisplayTimer <= 0) {
                lastItemName2 = "";
            }
        }


        // Update player's current EMC balance
        long currentBalance = EMCHelper.getBalance(player);

        // Check if EMC balance changed
        boolean balanceChanged = currentBalance != playerEMCBalance;
        if (balanceChanged) {
            playerEMCBalance = currentBalance;
        }
        sendDataToClient();
    }
    private void sendDataToClient() {
        if (player instanceof ServerPlayer serverPlayer) {
            // Create the packet with current data
            SyncNeoPlateDataPacket packet = new SyncNeoPlateDataPacket(
                    playerEMCBalance,
                    lastEMCGained,
                    lastItemWasNew,
                    lastItemName,
                    lastItemName2,
                    emcGainedDisplayTimer,
                    unlearnDisplayTimer
            );

            // Send it to this specific player
            PacketDistributor.sendToPlayer(serverPlayer, packet);
        }
    }

    private void processBurnerSlot(ItemStack stack) {
        // Safety checks
        if (level.isClientSide() || player == null || stack.isEmpty()) {
            return;
        }

        // Get the EMC value for the ENTIRE stack
        // For example, 64 diamonds = 64 * 8192 EMC
        Optional<Long> emcValue = EMCHelper.getStackEMC(stack);

        if (emcValue.isEmpty()) {
            LOGGER.warn("Item {} in burner slot has no EMC value!", stack.getItem());
            return;
        }

        long totalEMC = emcValue.get();
        Item item = stack.getItem();
        int count = stack.getCount();

        PlayerEMCData emcData = EMCHelper.getPlayerEMC(player);

        Boolean isNewItem = !emcData.hasLearned(item);
        boolean success = EMCHelper.addEMC(player, totalEMC);

        if (success) {
            // Update display information
            lastEMCGained = totalEMC;
            lastItemWasNew = isNewItem;
            lastItemName = stack.getHoverName().getString(); // Get the item's display name
            emcGainedDisplayTimer = EMC_DISPLAY_DURATION; // Show for 3 seconds

            LOGGER.info("Successfully gave {} EMC. New item: {}", totalEMC, isNewItem);

            // Consume the item
            this.blockEntity.inventory.extractItem(1, count, false);
            this.blockEntity.setChanged();
            sendDataToClient();

            emcData.learnItem(item);

            // Play sound
            /* I personally find it annoy, but a setting to enable this by the player may be made in the future
            level.playSound(null, blockEntity.getBlockPos(),
                    SoundEvents.EXPERIENCE_ORB_PICKUP,
                    SoundSource.BLOCKS,
                    0.5f, isNewItem ? 1.5f : 1.0f); // Higher pitch for new items!
             */

        } else {
            LOGGER.error("Failed to add EMC - overflow?");
        }
    }

    private void processUnlearnSlot(ItemStack stack) {
        // Safety checks
        if (level.isClientSide() || player == null || stack.isEmpty()) {
            return;
        }

        Item item = stack.getItem();
        int count = stack.getCount();

        PlayerEMCData emcData = EMCHelper.getPlayerEMC(player);

        Boolean isNewItem = !emcData.hasLearned(item);
        if (isNewItem) {return;}
        lastItemName2 = stack.getHoverName().getString(); // Get the item's display name
        unlearnDisplayTimer = UNLEARN_DISPLAY_DURATION; // Show for 3 seconds
        sendDataToClient();

        emcData.unLearnItem(item);
    }

    /**
     * Get the player's current EMC balance
     */
    public long getPlayerEMCBalance() {
        return playerEMCBalance;
    }

    /**
     * Get the EMC gained from the last burn
     */
    public long getLastEMCGained() {
        return lastEMCGained;
    }

    /**
     * Check if the last burned item was newly learned
     */
    public boolean wasLastItemNew() {
        return lastItemWasNew;
    }

    /**
     * Get the name of the last burned item
     */
    public String getLastItemName() {
        return lastItemName;
    }

    public String getLastItemName2() {
        return lastItemName2;
    }

    /**
     * Check if we should display the "EMC Gained" message
     */
    public boolean shouldDisplayEMCGained() {
        return emcGainedDisplayTimer > 0;
    }

    public boolean shouldDisplayUnlearned() {
        return unlearnDisplayTimer > 0;
    }

    /**
     * Get the fade alpha for the EMC gained message (0.0 to 1.0)
     * This creates a nice fade-out effect
     */
    public float getEMCGainedAlpha() {
        if (emcGainedDisplayTimer <= 0) return 0.0f;
        if (emcGainedDisplayTimer > 40) return 1.0f; // Fully visible for first 2 seconds
        return emcGainedDisplayTimer / 40.0f; // Fade out over last second
    }

    public float getUnlearnAlpha() {
        if (unlearnDisplayTimer <= 0) return 0.0f;
        if (unlearnDisplayTimer > 40) return 1.0f; // Fully visible for first 2 seconds
        return unlearnDisplayTimer / 40.0f; // Fade out over last second
    }

    public void receiveDataFromServer(long emc, long gained, boolean wasNew,
                                      String name, String name2, int timer, int uTimer) {
        this.playerEMCBalance = emc;
        this.lastEMCGained = gained;
        this.lastItemWasNew = wasNew;
        this.lastItemName = name;
        this.lastItemName2 = name2;
        this.emcGainedDisplayTimer = timer;
        this.unlearnDisplayTimer = uTimer;
    }
}
