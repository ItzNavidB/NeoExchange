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
    private long lastEMCLost = 0;
    private boolean lastItemWasNew = false;
    private String lastItemName = "";
    private String lastItemName2 = "";
    private String lastItemName3 = "";

    // Display timers
    private int emcGainedDisplayTimer = 0;
    private static final int EMC_DISPLAY_DURATION = 100;
    private int emcLostDisplayTimer = 0;
    private static final int LOSTEMC_DISPLAY_DURATION = 100;
    private int unlearnDisplayTimer = 0;
    private static final int UNLEARN_DISPLAY_DURATION = 100;

    // Virtual grid management
    private int virtualSlotStartIndex = 0;
    private int scrollOffset = 0;  // For pagination (future feature)
    private boolean filterAffordableOnly = true;  // Show only affordable items by default
    private int maxEMC = 0;
    private String searchText = "";  // Search filter for item names

    //Template slot managment
    private Item templateItem = null;

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
                this::processLearnSlot,
                this::processUnlearnSlot,
                this::processStoneSlot,
                this::processTemplateSlot
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

            // Set callback for EMC loss display
            if (slot instanceof VirtualEMCSlot virtualSlot) {
                virtualSlot.setEMCSpentCallback(this::onEMCSpent);
            }
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

    private static final int FIXED_SLOT_COUNT = 5;  // Stone, Burner, Learn, Unlearn, Template
    private static final int VIRTUAL_SLOT_COUNT = NeoPlateMenuSlots.getTotalGridSlots();  // 20
    private static final int TE_INVENTORY_SLOT_COUNT = FIXED_SLOT_COUNT + VIRTUAL_SLOT_COUNT;  // 23 total

    // Individual slot indices for special handling
    private static final int STONE_SLOT_INDEX = TE_INVENTORY_FIRST_SLOT_INDEX;      // 36
    private static final int BURNER_SLOT_INDEX = TE_INVENTORY_FIRST_SLOT_INDEX + 1; // 37
    private static final int LEARN_SLOT_INDEX = TE_INVENTORY_FIRST_SLOT_INDEX + 2; // 37
    private static final int UNLEARN_SLOT_INDEX = TE_INVENTORY_FIRST_SLOT_INDEX + 3; // 38
    private static final int TEMPLATE_SLOT_INDEX = TE_INVENTORY_FIRST_SLOT_INDEX + 4; // 39
    private static final int VIRTUAL_SLOTS_START_INDEX = TE_INVENTORY_FIRST_SLOT_INDEX + 5; // 40

    /**
     * Handle shift-clicking items
     * 
     * This is called when a player shift-clicks a slot.
     * 
     * Flow:
     * 1. Check what type of slot was clicked
     * 2. For virtual EMC slots: Buy items with EMC and add to inventory
     * 3. For regular slots: Move items between inventories
     */
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        // If clicking a virtual slot, make sure server knows what item is there
        if (slotId >= 0 && slotId < slots.size()) {
            Slot slot = slots.get(slotId);
            
            if (slot instanceof VirtualEMCSlot virtualSlot) {
                // Log what the server thinks is in this slot
                LOGGER.info("Server processing click on virtual slot {}: currentItem={}",
                        slotId, virtualSlot.getCurrentItem());
            }
        }
        
        // Let vanilla handle the click
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player playerIn, int pIndex) {
        Slot sourceSlot = slots.get(pIndex);
        if (sourceSlot == null || !sourceSlot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack sourceStack = sourceSlot.getItem();
        ItemStack copyOfSourceStack = sourceStack.copy();

        // Check if this is a virtual EMC slot
        if (sourceSlot instanceof VirtualEMCSlot virtualSlot) {
            // Handle virtual slot shift-click (EMC purchase)
            if (handleVirtualSlotShiftClick(playerIn, virtualSlot).equals(sourceStack)) {
                return ItemStack.EMPTY;
            }
        }

        // Regular slot shift-click logic
        // From player inventory to Neo Plate
        if (pIndex < VANILLA_FIRST_SLOT_INDEX + VANILLA_SLOT_COUNT) {
            if (!moveItemStackTo(sourceStack, TE_INVENTORY_FIRST_SLOT_INDEX, TE_INVENTORY_FIRST_SLOT_INDEX
                    + FIXED_SLOT_COUNT, false)) {
                return ItemStack.EMPTY;
            }
        }
        // From Neo Plate fixed slots to player inventory
        else if (pIndex < TE_INVENTORY_FIRST_SLOT_INDEX + FIXED_SLOT_COUNT) {
            if (!moveItemStackTo(sourceStack, VANILLA_FIRST_SLOT_INDEX, VANILLA_SLOT_COUNT, false)) {
                return ItemStack.EMPTY;
            }
        }
        // Virtual slots shouldn't reach here, but just in case
        else {
            LOGGER.warn("Unexpected shift-click from slot {} (not handled)", pIndex);
            return ItemStack.EMPTY;
        }

        // Update slot state
        if (sourceStack.getCount() == 0) {
            sourceSlot.set(ItemStack.EMPTY);
        } else {
            sourceSlot.setChanged();
        }

        // Special handling for Neo Stone items
        if (sourceStack.getItem() instanceof NeoStoneItem) {
            sourceSlot.onTake(playerIn, sourceStack);
            return copyOfSourceStack;
        }

        return ItemStack.EMPTY;
    }

    /**
     * Handle shift-clicking a virtual EMC slot
     * 
     * This buys as many items as the player can afford and puts them
     * in their inventory.
     * 
     * Flow:
     * 1. Calculate max affordable count
     * 2. Create item stack for that amount
     * 3. Process EMC purchase (deducts EMC) ✨ Uses shared method!
     * 4. Try to add to player inventory
     * 5. Handle any leftover items (refund EMC)
     * 6. Update displays
     * 
     * @param player The player performing the shift-click
     * @param virtualSlot The virtual EMC slot that was clicked
     * @return ItemStack representing what was moved
     */
    private ItemStack handleVirtualSlotShiftClick(Player player, VirtualEMCSlot virtualSlot) {
        // Only process on server side
        if (level.isClientSide()) {
            return ItemStack.EMPTY;
        }

        // Step 1: Calculate how many the player can afford
        int maxAffordable = virtualSlot.getAffordableCount(player);

        if (maxAffordable <= 0) {
            LOGGER.debug("Player can't afford any of {}", virtualSlot.getCurrentItem());
            return ItemStack.EMPTY;
        }

        // Step 2: Create the purchase stack
        ItemStack purchaseStack = new ItemStack(virtualSlot.getCurrentItem(), maxAffordable);

        LOGGER.debug("Player shift-clicking to buy {} x{} for {} EMC each",
                virtualSlot.getCurrentItem(),
                maxAffordable,
                virtualSlot.getEMCPerItem());

        // Step 3: Process the EMC purchase
        // ✨ THIS IS THE MAGIC - Using the shared processEMCPurchase() method!
        VirtualEMCSlot.PurchaseResult result = virtualSlot.processEMCPurchaseWithResult(player, purchaseStack);

        if (!result.success) {
            return ItemStack.EMPTY;
        }

        // Track purchase for display
        lastEMCLost = result.emcSpent;
        lastItemName3 = result.itemName;
        emcLostDisplayTimer = LOSTEMC_DISPLAY_DURATION;

        // At this point, EMC has been deducted
        int purchasedAmount = purchaseStack.getCount();

        // Step 4: Try to add items to player's inventory
        // moveItemStackTo will modify purchaseStack.count to reflect what couldn't be added
        boolean addedToInventory = this.moveItemStackTo(
                purchaseStack,
                VANILLA_FIRST_SLOT_INDEX,
                VANILLA_FIRST_SLOT_INDEX + VANILLA_SLOT_COUNT,
                true  // Try hotbar last
        );

        // Step 5: Handle items that couldn't be added to inventory
        int itemsNotAdded = purchaseStack.getCount();
        if (itemsNotAdded > 0) {
            // Inventory was full or partially full
            // We need to refund EMC for items that couldn't be added
            long emcPerItem = virtualSlot.getEMCPerItem();
            long refundAmount = emcPerItem * itemsNotAdded;

            LOGGER.info("Player inventory full, refunding {} EMC for {} items",
                    refundAmount, itemsNotAdded);

            // Refund the EMC
            EMCHelper.addEMC(player, refundAmount);
            EMCHelper.syncEMC((ServerPlayer) player);

            // Track the refund for display
            lastEMCLost = refundAmount;
            lastItemName3 = purchaseStack.getHoverName().getString();
            emcLostDisplayTimer = LOSTEMC_DISPLAY_DURATION;
        }

        // Calculate actual items added
        int actuallyAdded = purchasedAmount - itemsNotAdded;

        if (actuallyAdded > 0) {
            LOGGER.info("Player {} purchased {} x{} via shift-click",
                    player.getName().getString(),
                    virtualSlot.getCurrentItem(),
                    actuallyAdded);
        }

        // Step 6: Update the virtual slots display
        // EMC balance changed, so the grid needs updating
        updateVirtualSlots();

        // Broadcast changes to sync to client
        this.broadcastChanges();

        // Return a stack representing what was successfully moved
        if (actuallyAdded > 0) {
            return new ItemStack(virtualSlot.getCurrentItem(), actuallyAdded);
        } else {
            return ItemStack.EMPTY;
        }
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

        if (emcLostDisplayTimer > 0) {
            emcLostDisplayTimer--;
            if (emcLostDisplayTimer <= 0) {
                lastEMCLost = 0;
                lastItemName3 = "";
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
            //updateVirtualSlots();
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
    public void updateVirtualSlots() {
        // Step 1: Build the display list
        // This list is filtered, sorted, and ready to display
        ItemStack stone = this.blockEntity.inventory.getStackInSlot(0);

        maxEMC = EMCHelper.getStoneMaxEMC(stone);

        List<Item> displayList = NeoPlateMenuSlots.buildDisplayList(
                player,
                scrollOffset,
                filterAffordableOnly,
                maxEMC,
                searchText,
                templateItem
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
    /**
     * Set the scroll offset for pagination
     *
     * The offset is automatically clamped to valid range:
     * - Minimum: 0 (first page)
     * - Maximum: (totalItems / columns) - rows + 1
     *
     * Example: 50 items, 4 columns, 5 rows visible
     * - Total rows = 50/4 = 13 rows
     * - Max offset = 13 - 5 + 1 = 9
     *
     * @param offset The desired scroll offset
     */
    public void setScrollOffset(int offset) {
        // Calculate maximum valid offset
        PlayerEMCData emcData = EMCHelper.getPlayerEMC(player);
        int totalLearnedItems = emcData.getLearnedItems().size();
        int totalRows = (int) Math.ceil((double) totalLearnedItems / NeoPlateMenuSlots.getGridColumns());
        int maxOffset = Math.max(0, totalRows - NeoPlateMenuSlots.getGridRows() + 1);

        // Clamp to valid range
        this.scrollOffset = Math.max(0, Math.min(offset, maxOffset));

        updateVirtualSlots();
        LOGGER.debug("Scroll offset set to: {} (max: {})", scrollOffset, maxOffset);
    }

    /**
     * Update the search filter text
     *
     * This filters the item list to only show items whose names
     * contain the search text (case-insensitive).
     *
     * Example:
     * - searchText = "dia" → Shows Diamond, Diamond Sword, etc.
     * - searchText = "" → Shows all items (no filter)
     *
     * Called from the GUI when the player types in the search box
     */
    public void setSearchText(String text) {
        this.searchText = text.toLowerCase();  // Convert to lowercase for case-insensitive search
        updateVirtualSlots();  // Refresh the grid with filtered results
        LOGGER.info("Search filter set to: '{}'", searchText);
    }

    /**
     * Get the current search text
     * Used by the GUI to display what's in the search box
     */
    public String getSearchText() {
        return searchText;
    }

    /**
     * Called when EMC is spent - shows the red "-X EMC" message
     */
    private void onEMCSpent(long amount, String itemName, int count) {
        if (level.isClientSide()) return;

        lastEMCLost = amount;
        lastItemName3 = itemName;
        emcLostDisplayTimer = LOSTEMC_DISPLAY_DURATION;

        LOGGER.info("Displaying EMC loss: {} EMC for {} x{}", amount, itemName, count);
        sendDataToClient();
    }

    private void processBurnerSlot(ItemStack stack) {
        if (level.isClientSide() || player == null || stack.isEmpty()) {
            return;
        }

        Optional<Long> emcValue = EMCHelper.getStackEMCWithDurability(stack);
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
        if (level.isClientSide() || player == null) {
            return;
        }

        LOGGER.info("Player {} changed Neo Stone to {}", player.getName().getString(), 
                    stack.isEmpty() ? "EMPTY" : stack.getItem());

        // Update the maxEMC based on the new stone
        NeoStoneType stoneType = NeoStoneType.COMMON;
        if (!stack.isEmpty() && stack.getItem() instanceof NeoStoneItem neoStone) {
            stoneType = neoStone.getStoneType();
        }
        maxEMC = stoneType.getMaxEMC();

        // Update the virtual slots on the SERVER
        updateVirtualSlots();
        
        // Tell client to refresh virtual slots
        sendDataToClient(true);
    }

    private void processLearnSlot(ItemStack stack) {
        if (level.isClientSide() || player == null || stack.isEmpty()) {
            return;
        }

        Item item = stack.getItem();
        PlayerEMCData emcData = EMCHelper.getPlayerEMC(player);

        Boolean isLearned = emcData.hasLearned(item);
        if (isLearned) {
            return;
        }

        emcData.learnItem(item);
        EMCHelper.syncLearnedItems(player);

        // An item was learned, update the grid!
        updateVirtualSlots();
        
        // Tell client to refresh
        sendDataToClient(true);

        LOGGER.info("Player {} learned {}", player.getName().getString(), item);
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
        EMCHelper.syncLearnedItems(player);

        // An item was unlearned, update the grid!
        updateVirtualSlots();
        
        // Tell client to refresh
        sendDataToClient(true);

        LOGGER.info("Player {} unlearned {}", player.getName().getString(), item);
    }

    /**
     * Process the template slot when it changes
     *
     * This does TWO things:
     * 1. Automatically learns the item (without consuming it!)
     * 2. Triggers grid filtering by this item type
     *
     * @param stack The item in the template slot
     */
    private void processTemplateSlot(ItemStack stack) {
        if (level.isClientSide() || player == null) {
            return;
        }

        if (stack.isEmpty()) {
            this.templateItem = null;
        } else {
            Item item = stack.getItem();

            // Learn the item
            PlayerEMCData emcData = EMCHelper.getPlayerEMC(player);
            boolean wasNew = !emcData.hasLearned(item);

            if (wasNew) {
                emcData.learnItem(item);
                EMCHelper.syncToClient(player);  // Sync immediately
                // Update virtual slots AFTER learning
                updateVirtualSlots();
            }

            this.templateItem = item;
        }

        // Update grid SERVER-SIDE
        // (Client will get slot changes via normal container sync)
        updateVirtualSlots();
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
    public long getLastEMCLost() { return lastEMCLost; }
    public boolean wasLastItemNew() { return lastItemWasNew; }
    public String getLastItemName() { return lastItemName; }
    public String getLastItemName2() { return lastItemName2; }
    public String getLastItemName3() { return lastItemName3; }
    public boolean shouldDisplayEMCGained() {
            return emcGainedDisplayTimer > 0;
    }
    public boolean shouldDisplayEMCLost() {
        return emcLostDisplayTimer > 0;
    }
    public boolean shouldDisplayUnlearned() {
        return unlearnDisplayTimer > 0 && emcGainedDisplayTimer < unlearnDisplayTimer;
    }

    public float getEMCGainedAlpha() {
        if (emcGainedDisplayTimer <= 0) return 0.0f;
        if (emcGainedDisplayTimer > 40) return 1.0f;
        return emcGainedDisplayTimer / 40.0f;
    }

    public float getEMCLostAlpha() {
        if (emcLostDisplayTimer <= 0) return 0.0f;
        if (emcLostDisplayTimer > 40) return 1.0f;
        return emcLostDisplayTimer / 40.0f;
    }

    public float getUnlearnAlpha() {
        if (unlearnDisplayTimer <= 0) return 0.0f;
        if (unlearnDisplayTimer > 40) return 1.0f;
        return unlearnDisplayTimer / 40.0f;
    }

    public void receiveDataFromServer(long emc, long gained, long lost, boolean wasNew,
                                      String name, String name2, String name3,
                                      int timer, int uTimer, int lTimer) {
        this.playerEMCBalance = emc;
        this.lastEMCGained = gained;
        this.lastEMCLost = lost;  // ✨ ADDED
        this.lastItemWasNew = wasNew;
        this.lastItemName = name;
        this.lastItemName2 = name2;
        this.lastItemName3 = name3;  // ✨ ADDED
        this.emcGainedDisplayTimer = timer;
        this.unlearnDisplayTimer = uTimer;
        this.emcLostDisplayTimer = lTimer;  // ✨ ADDED

        // ❌ DO NOT call updateVirtualSlots() here!
        // The screen handles scrolling client-side
        // Calling this resets scroll position to 0
    }

    private void sendDataToClient() {
        sendDataToClient(false);  // Default: don't refresh virtual slots
    }
    
    private void sendDataToClient(boolean refreshVirtualSlots) {
        if (player instanceof ServerPlayer serverPlayer) {
            SyncNeoPlateDataPacket packet = new SyncNeoPlateDataPacket(
                    playerEMCBalance,
                    lastEMCGained,
                    lastEMCLost,
                    lastItemWasNew,
                    lastItemName,
                    lastItemName2,
                    lastItemName3,
                    emcGainedDisplayTimer,
                    unlearnDisplayTimer,
                    emcLostDisplayTimer,
                    refreshVirtualSlots  // NEW: Tell client whether to refresh
            );
            PacketDistributor.sendToPlayer(serverPlayer, packet);
        }
    }

    /**
     * Get the current scroll offset
     */
    public int getScrollOffset() {
        return scrollOffset;
    }

    /**
     * Client-side access to menu state for filtering/scrolling
     */
    public boolean isFilterAffordableOnly() {
        return filterAffordableOnly;
    }

    public int getMaxEMC() {
        return maxEMC;
    }


    public Item getTemplateItem() {
        return templateItem;
    }

    /**
     * Called by the client when learned items are synced from the server
     * This refreshes the virtual slot grid to show newly learned items
     */
    public void onLearnedItemsUpdated() {
        if (level.isClientSide()) {
            updateVirtualSlots();
            LOGGER.debug("Client-side virtual slots refreshed after learned items sync");
        }
    }

    public int getVirtualSlotStartIndex() {
        return virtualSlotStartIndex;
    }

}
