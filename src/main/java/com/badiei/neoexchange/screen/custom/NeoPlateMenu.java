package com.badiei.neoexchange.screen.custom;

import com.badiei.neoexchange.blocks.NeoBlocks;
import com.badiei.neoexchange.blocks.entity.NeoPlateEntity;
import com.badiei.neoexchange.datagen.ModTags;
import com.badiei.neoexchange.economy.NeoStoneType;
import com.badiei.neoexchange.items.NeoItems;
import com.badiei.neoexchange.items.NeoStoneItem;
import com.badiei.neoexchange.screen.ModMenuTypes;
import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class NeoPlateMenu extends AbstractContainerMenu {
    public static final Logger LOGGER = LogUtils.getLogger();
    public final NeoPlateEntity blockEntity;
    private final Level level;
    public NeoPlateMenu(int containerId, Inventory inv, FriendlyByteBuf extraData) {
        this(containerId, inv, inv.player.level().getBlockEntity(extraData.readBlockPos()));
    }

    public NeoPlateMenu(int containerId, Inventory inv, BlockEntity blockEntity) {
        super(ModMenuTypes.NEO_PLATE_MENU.get(), containerId);
        this.blockEntity = ((NeoPlateEntity) blockEntity);
        this.level = inv.player.level();

        addPlayerInventory(inv);
        addPlayerHotbar(inv);
        this.addSlot(new SlotItemHandler(this.blockEntity.inventory, 0, 8, 13) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                boolean allowed = stack.is(ModTags.Items.USEABLE_STONES);
                LOGGER.info("Checking {} -> allowed: {}", stack.getItem().getName(), allowed);
                LOGGER.info("Checking for tag: {}", ModTags.Items.USEABLE_STONES);
                return allowed;
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

    private static final int TE_INVENTORY_SLOT_COUNT = 1;  // must be the number of slots you have!


    @Override
    public ItemStack quickMoveStack(Player playerIn, int pIndex) {
        Slot sourceSlot = slots.get(pIndex);
        if (sourceSlot == null || !sourceSlot.hasItem()) return ItemStack.EMPTY;  //EMPTY_ITEM
        ItemStack sourceStack = sourceSlot.getItem();
        ItemStack copyOfSourceStack = sourceStack.copy();
        if (!(sourceSlot.getSlotIndex() == TE_INVENTORY_FIRST_SLOT_INDEX)) {
            if (!(sourceStack.getItem() instanceof NeoStoneItem)) {
                return ItemStack.EMPTY;
            }
        }

        // Check if the slot clicked is one of the vanilla container slots
        if (pIndex < VANILLA_FIRST_SLOT_INDEX + VANILLA_SLOT_COUNT) {
            // This is a vanilla container slot so merge the stack into the tile inventory
            if (!moveItemStackTo(sourceStack, TE_INVENTORY_FIRST_SLOT_INDEX, TE_INVENTORY_FIRST_SLOT_INDEX
                    + TE_INVENTORY_SLOT_COUNT, false)) {
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

    private void addPlayerInventory(Inventory playerInventory) {
        for (int i = 0; i < 3; ++i) {
            for (int l = 0; l < 9; ++l) {
                this.addSlot(new Slot(playerInventory, l + i * 9 + 9, 8 + l * 18, 84 + i * 18));
            }
        }
    }

    private void addPlayerHotbar(Inventory playerInventory) {
        for (int i = 0; i < 9; ++i) {
            this.addSlot(new Slot(playerInventory, i, 8 + i * 18, 142));
        }
    }
}
