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
import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;

/**
 * NeoPlateMenu - The menu/container for the Neo Plate GUI
 *
 * This menu now uses a Model-View architecture:
 * - The MODEL is the list of items to display (filtered, sorted, paginated)
 * - The VIEW is the fixed grid of VirtualEMCSlots
 * - When the model changes, we update the view
 *
 * This approach is much more flexible and performant!
 */
public class NeoPlateMenu extends AbstractContainerMenu {
    public static final Logger LOGGER = LogUtils.getLogger();
    public final NeoPlateEntity blockEntity;
    private final Level level;
    private final Player player;

    // Data tracking for GUI display
    private long playerEMCBalance = 0;
    private long previousEMCBalance = 0;
    private long lastEMCGained = 0;
    private boolean lastItemWasNew = false;
    private String lastItemName = "";
    private String lastItemName2 = "";

    // Display timers
    private int emcGainedDisplayTimer = 0;
    private static final int EMC_DISPLAY_DURATION = 100;
    private int unlearnDisplayTimer = 0;
    private static final int UNLEARN_DISPLAY_DURATION = 100;

    // Virtual grid management
    private int virtualSlotStartIndex = 0;
    private int scrollOffset = 0;  // For pagination (future feature)
    private boolean filterAffordableOnly = true;  // Show only affordable items by default
    private int maxEMC = 0;

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
            previousEMCBalance = playerEMCBalance;
            sendDataToClient();
        }

        // Add player inventory (36 slots: 27 inventory + 9 hotbar)
        addPlayerInventory(inv, 27, 33);
        addPlayerHotbar(inv, 27, 33);

        // Create and add the 3 fixed slots (stone, burner, unlearn)
        List<Slot> fixedSlots = NeoPlateMenuSlots.createFixedSlots(
                this.blockEntity.inventory,
                level,
                this::processBurnerSlot,
                this::processUnlearnSlot,
                this::processStoneSlot
        );
        for (Slot slot : fixedSlots) {
            this.addSlot(slot);
        }

        // Remember where virtual slots start
        virtualSlotStartIndex = this.slots.size();

        // Create a FIXED GRID of virtual slots
        // These start empty and will be populated by updateVirtualSlots()
        List<Slot> virtualSlots = NeoPlateMenuSlots.createVirtualSlots(player, level);
        for (Slot slot : virtualSlots) {
            this.addSlot(slot);
        }

        // Initial population of the virtual grid
        updateVirtualSlots();

        LOGGER.info("NeoPlateMenu initialized with {} total slots ({} virtual slots starting at index {})",
                this.slots.size(), virtualSlots.size(), virtualSlotStartIndex);
    }

    // Slot indices constants
    private static final int HOTBAR_SLOT_COUNT = 9;
    private static final int PLAYER_INVENTORY_ROW_COUNT = 3;
    private static final int PLAYER_INVENTORY_COLUMN_COUNT = 9;
    private static final int PLAYER_INVENTORY_SLOT_COUNT = PLAYER_INVENTORY_COLUMN_COUNT * PLAYER_INVENTORY_ROW_COUNT;
    private static final int VANILLA_SLOT_COUNT = HOTBAR_SLOT_COUNT + PLAYER_INVENTORY_SLOT_COUNT;
    private static final int VANILLA_FIRST_SLOT_INDEX = 0;
    private static final int TE_INVENTORY_FIRST_SLOT_INDEX = VANILLA_FIRST_SLOT_INDEX + VANILLA_SLOT_COUNT;

    private static final int FIXED_SLOT_COUNT = 3;  // Stone, Burner, Unlearn
    private static final int VIRTUAL_SLOT_COUNT = NeoPlateMenuSlots.getTotalGridSlots();  // 20
    private static final int TE_INVENTORY_SLOT_COUNT = FIXED_SLOT_COUNT + VIRTUAL_SLOT_COUNT;  // 23 total

    // Individual slot indices for special handling
    private static final int STONE_SLOT_INDEX = TE_INVENTORY_FIRST_SLOT_INDEX;      // 36
    private static final int BURNER_SLOT_INDEX = TE_INVENTORY_FIRST_SLOT_INDEX + 1; // 37
    private static final int UNLEARN_SLOT_INDEX = TE_INVENTORY_FIRST_SLOT_INDEX + 2; // 38
    private static final int VIRTUAL_SLOTS_START_INDEX = TE_INVENTORY_FIRST_SLOT_INDEX + 3; // 39

    @Override
    public ItemStack quickMoveStack(Player playerIn, int pIndex) {
        Slot sourceSlot = slots.get(pIndex);
        if (sourceSlot == null || !sourceSlot.hasItem()) return ItemStack.EMPTY;
        ItemStack sourceStack = sourceSlot.getItem();
        ItemStack copyOfSourceStack = sourceStack.copy();

        if (pIndex < VANILLA_FIRST_SLOT_INDEX + VANILLA_SLOT_COUNT) {
            if (!moveItemStackTo(sourceStack, TE_INVENTORY_FIRST_SLOT_INDEX, TE_INVENTORY_FIRST_SLOT_INDEX
                    + TE_INVENTORY_SLOT_COUNT - 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (pIndex < TE_INVENTORY_FIRST_SLOT_INDEX + TE_INVENTORY_SLOT_COUNT) {
            if (!moveItemStackTo(sourceStack, VANILLA_FIRST_SLOT_INDEX, VANILLA_FIRST_SLOT_INDEX + VANILLA_SLOT_COUNT, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            LOGGER.debug("Attempted shift-click from virtual slot {}", pIndex);
            return ItemStack.EMPTY;
        }

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
    public void broadcastChanges() {
        super.broadcastChanges();

        // Countdown display timers
        if (emcGainedDisplayTimer > 0) {
            emcGainedDisplayTimer--;
            if (emcGainedDisplayTimer <= 0) {
                lastEMCGained = 0;
                lastItemWasNew = false;
                lastItemName = "";
            }
        }

        if (unlearnDisplayTimer > 0) {
            unlearnDisplayTimer--;
            if (unlearnDisplayTimer <= 0) {
                lastItemName2 = "";
            }
        }

        // Check if EMC balance changed
        long currentBalance = EMCHelper.getBalance(player);
        boolean balanceChanged = currentBalance != previousEMCBalance;

        if (balanceChanged) {
            LOGGER.debug("EMC balance changed from {} to {}", previousEMCBalance, currentBalance);
            previousEMCBalance = currentBalance;
            playerEMCBalance = currentBalance;

            // ✨ THE MAGIC: Update the virtual grid when EMC changes
            updateVirtualSlots();
        }

        sendDataToClient();
    }

    /**
     * Update all virtual slots based on the current filtered/sorted item list
     *
     * This is THE KEY METHOD in your new architecture!
     *
     * How it works:
     * 1. Build a list of items to display (filtered, sorted)
     * 2. Map the list to the fixed grid of slots
     * 3. Tell each slot which item (if any) to display
     *
     * Example with 5 items and 20 slots:
     * - Slots 0-4 show the 5 items
     * - Slots 5-19 are empty
     *
     * When EMC increases and more items become affordable:
     * - Rebuild the list (now 10 items)
     * - Slots 0-9 show the 10 items
     * - Slots 10-19 are empty
     */
    private void updateVirtualSlots() {
        // Step 1: Build the display list
        // This list is filtered, sorted, and ready to display
        ItemStack stone = this.blockEntity.inventory.getStackInSlot(0);

        maxEMC = EMCHelper.getStoneMaxEMC(stone);

        List<Item> displayList = NeoPlateMenuSlots.buildDisplayList(
                player,
                scrollOffset,
                filterAffordableOnly,
                maxEMC
        );

        // Step 2: Map the list to slots
        for (int i = virtualSlotStartIndex; i < this.slots.size(); i++) {
            Slot slot = this.slots.get(i);

            if (slot instanceof VirtualEMCSlot virtualSlot) {
                // Calculate which item in the list this slot should show
                int listIndex = i - virtualSlotStartIndex;

                if (listIndex < displayList.size()) {
                    // This slot should show an item
                    Item item = displayList.get(listIndex);
                    int affordableAmount = calculateAffordableAmount(item);

                    // Tell the slot to display this item
                    virtualSlot.updateDisplay(item, affordableAmount);
                } else {
                    // This slot should be empty (no more items in the list)
                    virtualSlot.updateDisplay(net.minecraft.world.item.Items.AIR, 0);
                }
            }
        }
    }

    /**
     * Calculate how many of an item the player can afford
     */
    private int calculateAffordableAmount(Item item) {
        long emcPerItem = EMCHelper.getItemEMC(item).orElse(0L);
        if (emcPerItem <= 0) {
            return 0; // Free!
        }

        long balance = EMCHelper.getBalance(player);
        long affordable = balance / emcPerItem;

        return (int) Math.min(affordable, item.getDefaultMaxStackSize());
    }

    /**
     * Toggle the affordable-only filter
     *
     * When true: Only show items player can afford
     * When false: Show all learned items (unaffordable ones have 0 count)
     *
     * This can be called from the GUI (future button)
     */
    public void toggleAffordableFilter() {
        this.filterAffordableOnly = !this.filterAffordableOnly;
        updateVirtualSlots();
        LOGGER.info("Affordable filter toggled to: {}", filterAffordableOnly);
    }

    /**
     * Set the scroll offset for pagination
     *
     * offset = 0: Show items 0-19
     * offset = 1: Show items 4-23 (scrolled down 1 row with 4 columns)
     * etc.
     *
     * This can be called from the GUI (future scrollbar)
     */
    public void setScrollOffset(int offset) {
        this.scrollOffset = Math.max(0, offset);
        updateVirtualSlots();
        LOGGER.info("Scroll offset set to: {}", scrollOffset);
    }

    private void sendDataToClient() {
        if (player instanceof ServerPlayer serverPlayer) {
            SyncNeoPlateDataPacket packet = new SyncNeoPlateDataPacket(
                    playerEMCBalance,
                    lastEMCGained,
                    lastItemWasNew,
                    lastItemName,
                    lastItemName2,
                    emcGainedDisplayTimer,
                    unlearnDisplayTimer
            );
            PacketDistributor.sendToPlayer(serverPlayer, packet);
        }
    }

    private void processBurnerSlot(ItemStack stack) {
        if (level.isClientSide() || player == null || stack.isEmpty()) {
            return;
        }

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
            lastEMCGained = totalEMC;
            lastItemWasNew = isNewItem;
            lastItemName = stack.getHoverName().getString();
            emcGainedDisplayTimer = EMC_DISPLAY_DURATION;

            LOGGER.info("Successfully gave {} EMC. New item: {}", totalEMC, isNewItem);

            this.blockEntity.inventory.extractItem(1, count, false);
            this.blockEntity.setChanged();

            emcData.learnItem(item);
            EMCHelper.syncLearnedItems(player);

            sendDataToClient();

            // If a new item was learned, the grid needs updating!
            if (isNewItem) {
                updateVirtualSlots();
            }
        } else {
            LOGGER.error("Failed to add EMC - overflow?");
        }
    }

    private void processStoneSlot(ItemStack stack) {
        if (level.isClientSide() || player == null || stack.isEmpty()) {
            return;
        }

        // No special processing needed server-side for stone changes
        // The stone slot is handled automatically by the block entity

        LOGGER.info("Player {} changed Neo Stone to {}", player.getName().getString(), stack.getItem());

        // Update the maxEMC based on the new stone
        NeoStoneType stoneType = NeoStoneType.COMMON;
        if (stack.getItem() instanceof NeoStoneItem neoStone) {
            stoneType = neoStone.getStoneType();
        }
        maxEMC = stoneType.getMaxEMC();

        // Update the virtual slots to reflect new maxEMC
        updateVirtualSlots();
    }

    private void processUnlearnSlot(ItemStack stack) {
        if (level.isClientSide() || player == null || stack.isEmpty()) {
            return;
        }

        Item item = stack.getItem();
        PlayerEMCData emcData = EMCHelper.getPlayerEMC(player);

        Boolean isLearned = emcData.hasLearned(item);
        if (!isLearned) {
            return;
        }

        lastItemName2 = stack.getHoverName().getString();
        unlearnDisplayTimer = UNLEARN_DISPLAY_DURATION;

        emcData.unLearnItem(item);
        sendDataToClient();
        EMCHelper.syncLearnedItems(player);

        // An item was unlearned, update the grid!
        updateVirtualSlots();

        LOGGER.info("Player {} unlearned {}", player.getName().getString(), item);
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

    public int getContainerSize() {
        return this.blockEntity.getContainerSize();
    }

    // Getters for GUI display
    public long getPlayerEMCBalance() { return playerEMCBalance; }
    public long getLastEMCGained() { return lastEMCGained; }
    public boolean wasLastItemNew() { return lastItemWasNew; }
    public String getLastItemName() { return lastItemName; }
    public String getLastItemName2() { return lastItemName2; }
    public boolean shouldDisplayEMCGained() {
            return emcGainedDisplayTimer > 0 && emcGainedDisplayTimer > unlearnDisplayTimer;
    }
    public boolean shouldDisplayUnlearned() {
        return unlearnDisplayTimer > 0 && emcGainedDisplayTimer < unlearnDisplayTimer;
    }

    public float getEMCGainedAlpha() {
        if (emcGainedDisplayTimer <= 0) return 0.0f;
        if (emcGainedDisplayTimer > 40) return 1.0f;
        return emcGainedDisplayTimer / 40.0f;
    }

    public float getUnlearnAlpha() {
        if (unlearnDisplayTimer <= 0) return 0.0f;
        if (unlearnDisplayTimer > 40) return 1.0f;
        return unlearnDisplayTimer / 40.0f;
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

        // Update virtual slots on client side too!
        updateVirtualSlots();
    }
}