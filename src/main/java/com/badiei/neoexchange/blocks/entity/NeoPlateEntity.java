package com.badiei.neoexchange.blocks.entity;

import com.badiei.neoexchange.items.NeoStoneItem;
import com.badiei.neoexchange.screen.custom.NeoPlateMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
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

    public final ItemStackHandler inventory = new ItemStackHandler(NeoPlateMenu.getSlotSize()) {
        @Override
        protected int getStackLimit(int slot, ItemStack stack) {
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

    public NeoPlateEntity(BlockPos pos, BlockState blockState) {
        super(NeoBlockEntities.NEO_PLATE_BE.get(), pos, blockState);
    }

    // Animation state - these track our current animation progress
    private float rotation = 0.0f;
    private float bobTimer = 0.0f;

    // Animation speeds - adjust these to change animation speed
    private static final float ROTATION_SPEED = 1.0f;  // Degrees per tick (lower = slower spin)
    private static final float BOB_SPEED = 0.1f;       // Bob timer increment per tick (lower = slower bob)

    /**
     * Tick method that updates animations.
     * Called once per game tick (20 times per second), regardless of FPS.
     * This ensures consistent animation speed on all machines!
     */
    public static void tick(Level level, BlockPos pos, BlockState state, NeoPlateEntity entity) {
        if (level.isClientSide()) { // Only run animations on client side (visual only)
            // Update rotation
            entity.rotation += ROTATION_SPEED;
            if (entity.rotation >= 360.0f) {
                entity.rotation -= 360.0f; // Keep it in 0-360 range
            }

            // Update bobbing timer
            // We use a timer that goes from 0 to 2π (full sine wave cycle)
            entity.bobTimer += BOB_SPEED;
            if (entity.bobTimer >= Math.PI * 2) {
                entity.bobTimer -= (float)(Math.PI * 2); // Reset after full cycle
            }
        }
    }

    /**
     * Get the current rotation angle for rendering.
     * Simple getter - the actual rotation happens in tick()
     */
    public float getRenderingRotation() {
        return rotation;
    }

    /**
     * Get the current Y offset for bobbing animation.
     * Uses sine wave to create smooth up-and-down motion, just like dropped items!
     *
     * Returns a value between -0.5 and +0.5 (total movement of 1 block unit)
     */
    public float getYLocation() {
        return (float) Math.sin(bobTimer) * 0.5f;
    }

    // ========== MENU PROVIDER IMPLEMENTATION ==========

    @Override
    public Component getDisplayName() {
        return Component.literal("Neo Plate");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new NeoPlateMenu(containerId, playerInventory, this);
    }

    // ========== CONTAINER IMPLEMENTATION ==========

    @Override
    public int getContainerSize() {
        return NeoPlateMenu.getSlotSize();
    }

    @Override
    public boolean isEmpty() {
        return inventory.getStackInSlot(0).isEmpty();
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot < 0 || slot >= inventory.getSlots()) return ItemStack.EMPTY;
        return inventory.getStackInSlot(slot);
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot < 0 || slot >= inventory.getSlots()) return ItemStack.EMPTY;

        ItemStack stack = inventory.getStackInSlot(slot);
        if (stack.isEmpty()) return ItemStack.EMPTY;

        ItemStack removed = stack.split(amount);
        if (stack.isEmpty()) {
            inventory.setStackInSlot(slot, ItemStack.EMPTY);
        }
        setChanged();
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot < 0 || slot >= inventory.getSlots()) return ItemStack.EMPTY;

        ItemStack removed = inventory.getStackInSlot(slot);
        inventory.setStackInSlot(slot, ItemStack.EMPTY);
        return removed;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
    }

    @Override
    public boolean stillValid(Player player) {
        if (level == null || level.getBlockEntity(worldPosition) != this) {
            return false;
        }
        return player.distanceToSqr(worldPosition.getX() + 0.5,
                worldPosition.getY() + 0.5,
                worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < inventory.getSlots(); i++) {
            inventory.setStackInSlot(i, ItemStack.EMPTY);
        }
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (slot == 0) {
            return stack.isEmpty() || stack.getItem() instanceof NeoStoneItem;
        }
        return slot == 1;
    }

    // ========== HELPER METHODS ==========

    public ItemStack getStoredStone() {
        return inventory.getStackInSlot(0);
    }

    public boolean hasStoredStone() {
        return !inventory.getStackInSlot(0).isEmpty();
    }

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
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}