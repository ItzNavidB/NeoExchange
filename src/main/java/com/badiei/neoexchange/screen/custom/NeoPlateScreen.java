package com.badiei.neoexchange.screen.custom;

import com.badiei.neoexchange.NeoExchange;
import com.badiei.neoexchange.config.ClientConfig;
import com.badiei.neoexchange.emc.EMCHelper;
import com.badiei.neoexchange.emc.EMCRegistry;
import com.badiei.neoexchange.emc.PlayerEMCData;
import com.badiei.neoexchange.items.NeoStoneItem;
import com.badiei.neoexchange.network.CreateItemPacket;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class NeoPlateScreen extends AbstractContainerScreen<NeoPlateMenu> {
    private static final ResourceLocation GUI_TEXTURE = ResourceLocation.fromNamespaceAndPath(NeoExchange.MOD_ID, "textures/gui/neo_plate/neo_plate_gui.png");
    public static final Logger LOGGER = LogUtils.getLogger();

    //Grid configuration
    private static final int GRID_START_X = 150; // X position where grid starts (right side)
    private static final int GRID_START_Y = 20;  // Y position where grid starts
    private static final int GRID_COLUMNS = 4;   // 4 items per row
    private static final int GRID_ROWS = 5;      // 5 visible rows
    private static final int SLOT_SIZE = 18;     // Each slot is 18x18 pixels

    // Scrollbar configuration
    private static final int SCROLLBAR_WIDTH = 12;
    private static final int SCROLLBAR_X_OFFSET = GRID_COLUMNS * SLOT_SIZE + 2;  // Right of grid
    private static final int SCROLLBAR_HEIGHT = GRID_ROWS * SLOT_SIZE;  // Height of grid
    
    // Search bar widget
    private net.minecraft.client.gui.components.EditBox searchBox;
    
    // Smooth scrolling
    private float smoothScrollOffset = 1.0f;  // Current smooth position
    private int targetScrollOffset = 0;        // Target discrete position
    private boolean isDraggingScrollbar = false;
    private int dragStartY = 0;

    public NeoPlateScreen(NeoPlateMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 228;
        this.imageHeight = 196;
        this.titleLabelY = titleLabelY + Integer.MAX_VALUE;
        this.titleLabelX = titleLabelX + 8 + Integer.MAX_VALUE;
        this.inventoryLabelX = Integer.MAX_VALUE;
        this.inventoryLabelY = Integer.MAX_VALUE;
    }

    @Override
    protected void init() {
        super.init(); // Always call super first!

        // Calculate position for search box
        // We'll place it above the virtual item grid
        int x = (width - imageWidth) / 2;  // Center the GUI
        int y = (height - imageHeight) / 2;
        
        // Search box positioned above the grid
        int searchBoxX = x + GRID_START_X;
        int searchBoxY = y + GRID_START_Y - 14;  // 14 pixels above the grid
        int searchBoxWidth = (GRID_COLUMNS * SLOT_SIZE) - 2;  // Span the width of the grid
        int searchBoxHeight = 12;  // Standard height for text boxes

        // Create the search box
        searchBox = new net.minecraft.client.gui.components.EditBox(
                this.font,  // Use the screen's font
                searchBoxX,
                searchBoxY,
                searchBoxWidth,
                searchBoxHeight,
                Component.literal("Search Items")  // Tooltip text
        );

        // Configure the search box
        searchBox.setMaxLength(50);  // Max 50 characters
        searchBox.setBordered(true);  // Show a border
        searchBox.setVisible(true);   // Make it visible
        searchBox.setTextColor(0xFFFFFFFF);  // White text
        searchBox.setHint(Component.literal("Search..."));  // Placeholder text

        // Add a listener that triggers when text changes
        searchBox.setResponder(this::onSearchTextChanged);

        // Add the widget to the screen so it gets rendered and handles input
        this.addRenderableWidget(searchBox);

        LOGGER.info("Search box initialized at ({}, {}) with width {}", searchBoxX, searchBoxY, searchBoxWidth);
    }

    /**
     * Called whenever the player types in the search box
     * This method updates the menu's search filter and refreshes the grid
     */
    private void onSearchTextChanged(String newText) {
        // Update client-side immediately for responsive UI
        menu.setSearchText(newText);
        
        // Reset scroll position when search changes
        targetScrollOffset = 0;
        smoothScrollOffset = 0.0f;
        clientScrollOffset = 0;
        
        // Send packet to server to sync the search filter
        // This prevents desync when clicking items!
        if (this.minecraft != null && this.minecraft.getConnection() != null) {
            this.minecraft.getConnection().send(
                    new com.badiei.neoexchange.network.UpdateSearchTextPacket(newText)
            );
        }
        
        // Update grid immediately
        updateClientVirtualSlots();
        
        LOGGER.debug("Search text changed to: '{}' (synced to server)", newText);
    }

    /**
     * Override key pressed to prevent inventory key from closing GUI
     * when typing in the search box
     * 
     * This is CRITICAL for good UX - without this, typing 'E' in the search
     * box would close your inventory!
     */
    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent keyEvent) {
        // If search box is focused and has text, let it handle ALL keys
        // This prevents inventory hotkeys from interfering with typing
        if (searchBox != null && searchBox.isFocused()) {
            // Let the search box handle the key first
            // EditBox also uses KeyEvent now in 1.21.10
            if (searchBox.keyPressed(keyEvent)) {
                return true; // Search box handled it, we're done!
            }
            
            // Special case: Don't let ESC or inventory key close the GUI
            // when the search box is focused (unless it's empty)
            if (searchBox.getValue().length() >= 0) {
                // Check if this is the inventory key (usually 'E')
                var key = com.mojang.blaze3d.platform.InputConstants.getKey(keyEvent);
                if (this.minecraft != null && this.minecraft.options.keyInventory.isActiveAndMatches(key)) {
                    return true; // Consume the key, don't close GUI
                }
            }
        }
        
        // Otherwise, let the parent handle it (for ESC, etc.)
        return super.keyPressed(keyEvent);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, GUI_TEXTURE, x, y, 0, 0, imageWidth, imageHeight, 256, 256);
        
        // Grid background
        int gridSX = NeoPlateMenuSlots.getGridStartX() + x - 1;
        int gridSY = NeoPlateMenuSlots.getGridStartY() + y - 1;
        int gridWidth = NeoPlateMenuSlots.getGridColumns() * NeoPlateMenuSlots.getSlotSize() + gridSX;
        int gridHeight = NeoPlateMenuSlots.getGridRows() * NeoPlateMenuSlots.getSlotSize() + gridSY;
        guiGraphics.fill(gridSX, gridSY, gridWidth, gridHeight, 0x40000000);
        
        // Render scrollbar
        renderScrollbar(guiGraphics, mouseX, mouseY);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        // Update smooth scrolling animation
        updateSmoothScrolling(partialTick);
        
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        renderEMCInfo(guiGraphics, mouseX, mouseY, partialTick);

        super.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    /**
     * Render the EMC info displays
     */
    private void renderEMCInfo(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        // === 1. PLAYER EMC BALANCE (Top left, always visible) ===
        long balance = menu.getPlayerEMCBalance();
        String balanceText = String.format("%,d", balance);

        Component balanceLabel = Component.literal("EMC: ")
                .withStyle(ChatFormatting.GRAY);
        Component balanceValue = Component.literal(balanceText)
                .withStyle(ChatFormatting.YELLOW);

        int balanceX = x + 8;
        int balanceY = y + 8;

        guiGraphics.drawString(font, balanceLabel, balanceX, balanceY, 0xFFAAAAAA, true);
        guiGraphics.drawString(font, balanceValue,
                balanceX + font.width("EMC: "), balanceY, 0xFFFFFF55, true);

        // === 2. EMC GAINED (Below balance, green) ===
        if (menu.shouldDisplayEMCGained()) {
            long gained = menu.getLastEMCGained();
            String gainedText = String.format("+%,d EMC", gained);

            float alpha = menu.getEMCGainedAlpha();
            int alphaInt = (int)(alpha * 255);
            int color = (alphaInt << 24) | 0x55FF55; // Green with alpha

            int gainedX = x + 8;
            int gainedY = y + 20;

            guiGraphics.drawString(font, gainedText, gainedX, gainedY, color, true);
        }

        // === 3. EMC LOST (Below EMC gained, red) ===
        if (menu.shouldDisplayEMCLost()) {
            long lost = menu.getLastEMCLost();
            String lostText = String.format("-%,d EMC", lost);

            float alpha = menu.getEMCLostAlpha();
            int alphaInt = (int)(alpha * 255);
            int color = (alphaInt << 24) | 0xFF5555; // Red with alpha

            int lostX = x + 8;
            // If EMC gained is showing, offset below it. Otherwise use same position.
            int lostY = menu.shouldDisplayEMCGained() ? y + 32 : y + 20;

            guiGraphics.drawString(font, lostText, lostX, lostY, color, true);
        }

        // === 4. ITEM LEARNED (Center, two lines, smaller text) ===
        // Only show if not displaying unlearned text (prevents overlap)
        if (menu.shouldDisplayEMCGained() && menu.wasLastItemNew() && !menu.shouldDisplayUnlearned()) {
            String itemName = menu.getLastItemName();

            float alpha = menu.getEMCGainedAlpha();
            int alphaInt = (int)(alpha * 255);
            int color = (alphaInt << 24) | 0xFFAA00; // Orange with alpha

            // Use scaling for smaller text
            guiGraphics.pose().pushMatrix();
            
            // Calculate center position BEFORE scaling
            int centerX = x + (imageWidth / 2);
            int centerY = y + 82;
            
            // Scale to 75% size for more compact text
            float scale = 0.75f;
            guiGraphics.pose().translate(centerX, centerY);
            guiGraphics.pose().scale(scale, scale);
            
            // Line 1: "✦ Learned ✦"
            String line1 = "✦ Learned ✦";
            int line1X = -font.width(line1) / 2;  // Center relative to origin
            guiGraphics.drawString(font, line1, line1X, 0, color, true);
            
            // Line 2: Item name
            int line2X = -font.width(itemName) / 2;  // Center relative to origin
            guiGraphics.drawString(font, itemName, line2X, 10, color, true);
            
            guiGraphics.pose().popMatrix();
        }

        // === 5. ITEM UNLEARNED (Center, two lines, smaller text) ===
        // Shows with priority over learned text
        if (menu.shouldDisplayUnlearned()) {
            String itemName = menu.getLastItemName2();

            float alpha = menu.getUnlearnAlpha();
            int alphaInt = (int)(alpha * 255);
            int color = (alphaInt << 24) | 0xFF5555; // Red with alpha

            guiGraphics.pose().pushMatrix();
            
            int centerX = x + (imageWidth / 2);
            int centerY = y + 82;
            
            float scale = 0.75f;
            guiGraphics.pose().translate(centerX, centerY);
            guiGraphics.pose().scale(scale, scale);
            
            // Line 1: "✦ Unlearned ✦"
            String line1 = "✦ Unlearned ✦";
            int line1X = -font.width(line1) / 2;
            guiGraphics.drawString(font, line1, line1X, 0, color, true);
            
            // Line 2: Item name
            int line2X = -font.width(itemName) / 2;
            guiGraphics.drawString(font, itemName, line2X, 10, color, true);
            
            guiGraphics.pose().popMatrix();
        }

        // === 6. MAX EMC DISPLAY (Bottom center) ===
        ItemStack stone = menu.blockEntity.inventory.getStackInSlot(0);
        String maxEMCText = "Max EMC";
        Component text = Component.literal(maxEMCText).withStyle(ChatFormatting.GRAY);
        String maxEMCValue = EMCHelper.getStoneMaxEMCFormatted(stone);
        Component value = Component.literal(maxEMCValue).withStyle(ChatFormatting.YELLOW);

        int emcX = x + (imageWidth / 2) - (font.width(text) / 2) - 61;
        int emcY = y + 67;

        guiGraphics.drawString(font, text, emcX, emcY, 0xFFFFFFFF, true);
        guiGraphics.drawString(font, value, emcX + 18 - font.width(value)/2, emcY + 10, 0xFFFFFFFF, true);
    }

    /**
     * Update smooth scrolling animation
     * Interpolates between current and target scroll position
     */
    private void updateSmoothScrolling(float partialTick) {
        // Lerp factor - higher = faster scrolling (0.3 = 30% per frame)
        float lerpSpeed = 0.3f;
        
        // Smoothly interpolate towards target
        float diff = targetScrollOffset - smoothScrollOffset;
        
        if (Math.abs(diff) < 0.01f) {
            // Close enough, snap to target
            smoothScrollOffset = targetScrollOffset;
        } else {
            // Move towards target
            smoothScrollOffset += diff * lerpSpeed;
        }
        
        // Update grid based on smooth scroll position
        int discreteOffset = Math.round(smoothScrollOffset);
        if (discreteOffset != clientScrollOffset) {
            LOGGER.debug("Updating scroll: clientScrollOffset {} -> {}", clientScrollOffset, discreteOffset);
            clientScrollOffset = discreteOffset;
            
            // Update client menu and sync to server
            menu.setScrollOffset(discreteOffset);
            
            // Send packet to server
            if (this.minecraft != null && this.minecraft.getConnection() != null) {
                this.minecraft.getConnection().send(
                        new com.badiei.neoexchange.network.SyncScrollOffsetPacket(discreteOffset)
                );
            }
            
            // Update client visual display
            updateClientVirtualSlots();
        }
    }

    /**
     * Render the scrollbar
     */
    private void renderScrollbar(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (this.minecraft == null || this.minecraft.player == null) return;
        
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        
        // Calculate scrollbar position
        int scrollbarX = x + GRID_START_X + SCROLLBAR_X_OFFSET;
        int scrollbarY = y + GRID_START_Y;
        
        // Calculate total scrollable rows
        PlayerEMCData emcData = EMCHelper.getPlayerEMC(this.minecraft.player);
        int totalItems = emcData.getLearnedItems().size();
        int totalRows = (int) Math.ceil((double) totalItems / GRID_COLUMNS);
        int maxScroll = Math.max(0, totalRows - GRID_ROWS);
        
        if (maxScroll <= 0) {
            // Not enough items to scroll
            return;
        }
        
        // Draw scrollbar track
        int trackColor = 0x80000000;  // Semi-transparent black
        guiGraphics.fill(scrollbarX, scrollbarY, scrollbarX + SCROLLBAR_WIDTH, 
                        scrollbarY + SCROLLBAR_HEIGHT, trackColor);
        
        // Calculate scrollbar handle size and position
        float visibleRatio = (float) GRID_ROWS / totalRows;
        int handleHeight = Math.max(10, (int)(SCROLLBAR_HEIGHT * visibleRatio));
        
        float scrollProgress = (float) smoothScrollOffset / maxScroll;
        int handleY = scrollbarY + (int)((SCROLLBAR_HEIGHT - handleHeight) * scrollProgress);
        
        // Scrollbar handle color - brighter if hovering
        boolean hovering = mouseX >= scrollbarX && mouseX <= scrollbarX + SCROLLBAR_WIDTH &&
                          mouseY >= scrollbarY && mouseY <= scrollbarY + SCROLLBAR_HEIGHT;
        int handleColor = hovering || isDraggingScrollbar ? 0xFFAAAAAA : 0xFF888888;
        
        // Draw scrollbar handle
        guiGraphics.fill(scrollbarX + 1, handleY, scrollbarX + SCROLLBAR_WIDTH - 1, 
                        handleY + handleHeight, handleColor);
        
        // Draw scrollbar handle border
        int borderColor = 0xFF555555;
        guiGraphics.hLine(scrollbarX + 1, scrollbarX + SCROLLBAR_WIDTH - 2, handleY, borderColor);
        guiGraphics.hLine(scrollbarX + 1, scrollbarX + SCROLLBAR_WIDTH - 2, handleY + handleHeight - 1, borderColor);
        guiGraphics.vLine(scrollbarX + 1, handleY, handleY + handleHeight - 1, borderColor);
        guiGraphics.vLine(scrollbarX + SCROLLBAR_WIDTH - 1, handleY, handleY + handleHeight - 1, borderColor);
    }

    /**
     * Get maximum scroll offset based on total items
     */
    private int getMaxScrollOffset() {
        if (this.minecraft == null || this.minecraft.player == null) return 0;
        
        PlayerEMCData emcData = EMCHelper.getPlayerEMC(this.minecraft.player);
        int totalItems = emcData.getLearnedItems().size();
        int totalRows = (int) Math.ceil((double) totalItems / GRID_COLUMNS);
        return Math.max(0, totalRows - GRID_ROWS);
    }

    /**
     * Handle mouse wheel scrolling
     *
     * How scrolling works:
     * - Each "notch" of the mouse wheel = 1.0 delta
     * - Scroll up (delta > 0) = move UP in list (decrease offset)
     * - Scroll down (delta < 0) = move DOWN in list (increase offset)
     * - We scroll by ROWS, not individual items
     *
     * Example with 50 items in a 4-column grid:
     * - Offset 0: Shows items 0-19 (rows 0-4)
     * - Offset 1: Shows items 4-23 (rows 1-6) ← Scrolled down 1 row
     * - Offset 2: Shows items 8-27 (rows 2-7) ← Scrolled down another row
     *
     * @param mouseX Mouse X position
     * @param mouseY Mouse Y position
     * @param deltaX Horizontal scroll (we ignore this)
     * @param deltaY Vertical scroll amount (positive = up, negative = down)
     * @return true if we handled the scroll
     */
    // Client-side scroll state (no sync needed!)
    private int clientScrollOffset = 0;

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        // Check if mouse over grid OR scrollbar
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        int gridLeft = x + GRID_START_X;
        int gridRight = gridLeft + (GRID_COLUMNS * SLOT_SIZE) + SCROLLBAR_WIDTH + 2;
        int gridTop = y + GRID_START_Y;
        int gridBottom = gridTop + (GRID_ROWS * SLOT_SIZE);

        boolean mouseOverScrollArea = mouseX >= gridLeft && mouseX <= gridRight &&
                mouseY >= gridTop && mouseY <= gridBottom;

        if (!mouseOverScrollArea) {
            return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
        }

        // Update target scroll offset
        // Use config value for scroll sensitivity (how many rows per wheel notch)
        int scrollAmount = ClientConfig.SCROLL_SENSITIVITY.get();
        int maxScroll = getMaxScrollOffset();
        if (deltaY > 0) {
            targetScrollOffset = Math.max(0, targetScrollOffset - scrollAmount);
        } else if (deltaY < 0) {
            targetScrollOffset = Math.min(maxScroll, targetScrollOffset + scrollAmount);
        }

        return true;
    }

    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent mouseEvent, boolean hasBeenHandled) {
        // Check for scrollbar drag
        if (mouseEvent.button() == 0) {  // Left click
            int x = (width - imageWidth) / 2;
            int y = (height - imageHeight) / 2;
            int scrollbarX = x + GRID_START_X + SCROLLBAR_X_OFFSET;
            int scrollbarY = y + GRID_START_Y;
            
            if (mouseEvent.x() >= scrollbarX && mouseEvent.x() <= scrollbarX + SCROLLBAR_WIDTH &&
                mouseEvent.y() >= scrollbarY && mouseEvent.y() <= scrollbarY + SCROLLBAR_HEIGHT) {
                // Clicked on scrollbar
                isDraggingScrollbar = true;
                dragStartY = (int) mouseEvent.y();
                
                // Jump to clicked position
                int maxScroll = getMaxScrollOffset();
                float clickProgress = (float)(mouseEvent.y() - scrollbarY) / SCROLLBAR_HEIGHT;
                targetScrollOffset = Math.round(clickProgress * maxScroll);
                targetScrollOffset = Math.max(0, Math.min(maxScroll, targetScrollOffset));
                
                return true;
            }
        }
        
        // Original mouseClicked logic
        // Check if right-click (button 1) on the search box
        if (mouseEvent.button() == 1 && searchBox != null && searchBox.isMouseOver(mouseEvent.x(), mouseEvent.y())) {
            searchBox.setValue("");  // Clear the search box
            searchBox.setFocused(true);  // Keep it focused for convenience
            return true;  // We handled this click
        }

        if (mouseEvent.button() == 0 && searchBox != null && !searchBox.isMouseOver(mouseEvent.x(), mouseEvent.y())) {
            searchBox.setFocused(false);
        }
        if (mouseEvent.button() == 0 && searchBox != null && searchBox.isMouseOver(mouseEvent.x(), mouseEvent.y())) {
            searchBox.setFocused(true);
        }

        // Middle mouse button (button 2) = Toggle favorite
        if (mouseEvent.button() == 2) {  // 2 = middle mouse
            Slot clickedSlot = this.getSlotUnderMouse();

            if (clickedSlot instanceof VirtualEMCSlot virtualSlot) {
                Item clickedItem = virtualSlot.getCurrentItem();

                if (clickedItem != null && clickedItem != net.minecraft.world.item.Items.AIR) {
                    LOGGER.info("Middle-clicked item: {}", clickedItem);

                    if (this.minecraft != null && this.minecraft.getConnection() != null) {
                        this.minecraft.getConnection().send(
                                new com.badiei.neoexchange.network.ToggleFavoritePacket(clickedItem)
                        );
                    }

                    return true;
                }
            }
        }
        
        return super.mouseClicked(mouseEvent, hasBeenHandled);
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent mouseEvent) {
        if (mouseEvent.button() == 0) {  // Left click released
            isDraggingScrollbar = false;
        }
        return super.mouseReleased(mouseEvent);
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent mouseEvent, double deltaX, double deltaY) {
        if (isDraggingScrollbar) {
            int x = (width - imageWidth) / 2;
            int y = (height - imageHeight) / 2;
            int scrollbarY = y + GRID_START_Y;
            
            int maxScroll = getMaxScrollOffset();
            float dragProgress = (float)(mouseEvent.y() - scrollbarY) / SCROLLBAR_HEIGHT;
            targetScrollOffset = Math.round(dragProgress * maxScroll);
            targetScrollOffset = Math.max(0, Math.min(maxScroll, targetScrollOffset));
            
            return true;
        }
        
        return super.mouseDragged(mouseEvent, deltaX, deltaY);
    }

    /**
     * Client-side grid update (no server involved!)
     *
     * This is safe because:
     * - Clicking items sends ITEM ID, not slot index
     * - Server doesn't care about visual ordering
     * - Each client can have different scroll position!
     */
    private void updateClientVirtualSlots() {
        if (this.minecraft == null || this.minecraft.player == null) return;

        LOGGER.debug("updateClientVirtualSlots called with clientScrollOffset: {}", clientScrollOffset);

        // Build display list CLIENT-SIDE
        List<Item> displayList = NeoPlateMenuSlots.buildDisplayList(
                this.minecraft.player,
                clientScrollOffset,
                menu.isFilterAffordableOnly(),  // Get from menu
                menu.getMaxEMC(),
                menu.getSearchText(),
                menu.getTemplateItem()
        );

        LOGGER.debug("Built display list with {} items at offset {}", displayList.size(), clientScrollOffset);

        // Update visual slots
        int virtualSlotStart = menu.getVirtualSlotStartIndex();
        for (int i = 0; i < NeoPlateMenuSlots.getTotalGridSlots(); i++) {
            Slot slot = menu.slots.get(virtualSlotStart + i);

            if (slot instanceof VirtualEMCSlot virtualSlot) {
                if (i < displayList.size()) {
                    Item item = displayList.get(i);
                    int affordableAmount = calculateClientAffordableAmount(item);
                    virtualSlot.updateDisplay(item, affordableAmount);
                } else {
                    virtualSlot.updateDisplay(Items.AIR, 0);
                }
            }
        }
    }

    private int calculateClientAffordableAmount(Item item) {
        long emcPerItem = EMCHelper.getItemEMC(item).orElse(0L);
        if (emcPerItem <= 0) return item.getDefaultMaxStackSize();

        long balance = EMCHelper.getBalance(this.minecraft.player);
        long affordable = balance / emcPerItem;

        return (int) Math.min(affordable, item.getDefaultMaxStackSize());
    }

    /**
     * Render each slot - this is called for EVERY slot
     * We override it to:
     * 1. Apply smooth scroll offset to virtual slots
     * 2. Add visual effects for favorite items
     */
    @Override
    protected void renderSlot(GuiGraphics guiGraphics, Slot slot) {
        // Apply smooth pixel scrolling to virtual slots
        if (slot instanceof VirtualEMCSlot) {
            // Calculate smooth pixel offset
            float rowOffset = smoothScrollOffset - clientScrollOffset;
            float pixelOffset = rowOffset * SLOT_SIZE;

            // Apply translation to the entire rendering context
            guiGraphics.pose().pushMatrix();
            guiGraphics.pose().translate(0.0f, -pixelOffset);

            // Render the slot
            super.renderSlot(guiGraphics, slot);

            // Restore pose
            guiGraphics.pose().popMatrix();

            // Render favorite star (also needs translation)
            if (((VirtualEMCSlot) slot).isCurrentItemFavorited()) {
                guiGraphics.pose().pushMatrix();
                guiGraphics.pose().translate(0.0f, -pixelOffset);

                int borderColor = 0x40FFD700;
                guiGraphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, borderColor);
                renderFavoriteStar(guiGraphics, slot.x, slot.y);

                guiGraphics.pose().popMatrix();
            }
        } else {
            // Regular slots render normally
            super.renderSlot(guiGraphics, slot);
        }
    }

    /**
     * Render a star icon in the top-right corner of a slot
     * 
     * This creates a small, bright star indicator that clearly shows
     * which items are favorited.
     * 
     * @param guiGraphics The rendering context
     * @param slotX The X position of the slot
     * @param slotY The Y position of the slot
     */
    private void renderFavoriteStar(GuiGraphics guiGraphics, int slotX, int slotY) {
        // Star will be in top-right corner of the slot
        // Slot is 16x16, so we position the star at the top-right
        int starX = slotX + 12;  // 12 pixels from left (leaves 6 pixels on right)
        int starY = slotY + 0;   // 0 pixel from top

        // Option 1: Unicode star character ⭐
        // This is simple and works everywhere!
        String starSymbol = "⭐";
        
        // Use the pose stack for transformations
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(starX, starY);  // Move to position
        float scale = 0.75f;  // Scale to 75% size
        guiGraphics.pose().scale(scale, scale);
        
        // Draw the star with a slight shadow for depth
        // Shadow first (offset, dark)
        guiGraphics.drawString(font, starSymbol, 1, 1, 0x88000000, false);  // Semi-transparent black
        // Star on top (gold)
        guiGraphics.drawString(font, starSymbol, 0, 0, 0xFFFFD700, false);  // Gold
        
        guiGraphics.pose().popMatrix();
    }
}

