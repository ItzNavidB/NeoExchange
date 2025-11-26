package com.badiei.neoexchange.blocks.entity;

import com.badiei.neoexchange.items.NeoStoneItem;
import com.badiei.neoexchange.screen.custom.NeoPlateMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

/**
 * Block Entity for Neo Plate
 * Stores a single Neo Stone item that determines which tier of exchange is available
 * Implements MenuProvider to provide a GUI for inserting/removing stones
 */
public class NeoPlateEntity extends BlockEntity implements MenuProvider, Container {
    
    // Inventory to hold a single item (the Neo Stone)
    public final ItemStackHandler inventory = new ItemStackHandler(1) {
        @Override
        protected int getStackLimit(int slot, ItemStack stack) {
            return 1;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if(!level.isClientSide()) {
                level.sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), 3);
            }
        }
    };

    /**
     * Constructor called when creating the block entity
     */
    public NeoPlateEntity(BlockPos pos, BlockState blockState) {
        super(NeoBlockEntities.NEO_PLATE_BE.get(), pos, blockState);
    }

    private float rotation;
    public float getRenderingRotation() {
        rotation += 0.5f;
        if(rotation >= 360) {
            rotation = 0;
        }
        return rotation;
    }
    
    // ========== MENU PROVIDER IMPLEMENTATION ==========
    
    @Override
    public Component getDisplayName() {
        return Component.literal("Neo Plate");
    }
    
    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        // Use a simple chest menu with 1 slot (9 slots per row, we only show 1)
        return new NeoPlateMenu(containerId, playerInventory, this);
    }
    
    // ========== CONTAINER IMPLEMENTATION ==========
    
    @Override
    public int getContainerSize() {
        return 1; // Only 1 slot for the stone
    }
    
    @Override
    public boolean isEmpty() {
        return inventory.getStackInSlot(0).isEmpty();
    }

    
    @Override
    public ItemStack getItem(int slot) {
        return slot == 0 ? inventory.getStackInSlot(0) : ItemStack.EMPTY;
    }
    
    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot != 0) return ItemStack.EMPTY;
        
        ItemStack stack = inventory.getStackInSlot(0);
        if (stack.isEmpty()) return ItemStack.EMPTY;
        
        ItemStack removed = stack.split(amount);
        if (stack.isEmpty()) {
            inventory.setStackInSlot(0, ItemStack.EMPTY);
        }
        setChanged();
        return removed;
    }
    
    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot != 0) return ItemStack.EMPTY;
        
        ItemStack removed = inventory.getStackInSlot(0);
        inventory.setStackInSlot(0, ItemStack.EMPTY);
        return removed;
    }
    
    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot != 0) return;
        
        // Only accept Neo Stones or empty stacks
        if (stack.isEmpty() || stack.getItem() instanceof NeoStoneItem) {
            inventory.setStackInSlot(0, stack);
            if (stack.getCount() > getMaxStackSize()) {
                stack.setCount(getMaxStackSize());
            }
            setChanged();
        }
    }
    
    @Override
    public boolean stillValid(Player player) {
        // Check if player is close enough to use the block
        if (level == null || level.getBlockEntity(worldPosition) != this) {
            return false;
        }
        return player.distanceToSqr(worldPosition.getX() + 0.5, 
                                     worldPosition.getY() + 0.5, 
                                     worldPosition.getZ() + 0.5) <= 64.0;
    }
    
    @Override
    public void clearContent() {
        inventory.setStackInSlot(0, ItemStack.EMPTY);
    }
    
    @Override
    public int getMaxStackSize() {
        return 1; // Only allow 1 stone at a time
    }
    
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        // Only accept Neo Stones in the slot
        return slot == 0 && (stack.isEmpty() || stack.getItem() instanceof NeoStoneItem);
    }
    
    // ========== HELPER METHODS ==========
    
    /**
     * Get the stored Neo Stone
     * @return The ItemStack in the slot (may be empty)
     */
    public ItemStack getStoredStone() {
        return inventory.getStackInSlot(0);
    }
    
    /**
     * Check if a stone is currently stored
     * @return true if there's a stone in the slot
     */
    public boolean hasStoredStone() {
        return !inventory.getStackInSlot(0).isEmpty();
    }
    
    /**
     * Drop the stored item when the block is broken
     */
    public void drops() {
        SimpleContainer inv = new SimpleContainer(inventory.getSlots());
        for(int i = 0; i < inventory.getSlots(); i++) {
            inv.setItem(i, inventory.getStackInSlot(i));
        }

        Containers.dropContents(this.level, this.worldPosition, inv);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        drops();
        super.preRemoveSideEffects(pos, state);
    }
    
    // ========== DATA SAVING/LOADING ==========
    
    @Override
    protected void saveAdditional(ValueOutput valueOutput) {
        super.saveAdditional(valueOutput);

        inventory.serialize(valueOutput);
    }
    
    @Override
    protected void loadAdditional(ValueInput valueInput) {
        super.loadAdditional(valueInput);
        
        inventory.deserialize(valueInput);
    }
    
    // ========== CLIENT-SERVER SYNC ==========
    
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {return saveWithoutMetadata(registries);}
    
    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {return ClientboundBlockEntityDataPacket.create(this);}
}
