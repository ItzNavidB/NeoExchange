package com.badiei.neoexchange.screen.custom;

import com.badiei.neoexchange.NeoExchange;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
    private static final int GRID_ROWS = 5;      // 6 visible rows
    private static final int SLOT_SIZE = 18;     // Each slot is 18x18 pixels

    // Search bar widget
    private net.minecraft.client.gui.components.EditBox searchBox;

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
        
        // Send packet to server to sync the search filter
        // This prevents desync when clicking items!
        if (this.minecraft != null && this.minecraft.getConnection() != null) {
            this.minecraft.getConnection().send(
                    new com.badiei.neoexchange.network.UpdateSearchTextPacket(newText)
            );
        }
        
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

    /**
     * Handle mouse clicks on the screen
     * Right-click on search box = clear it
     */
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent mouseEvent, boolean hasBeenHandled) {
        // Check if right-click (button 1) on the search box
        if (mouseEvent.button() == 1 && searchBox != null && searchBox.isMouseOver(mouseEvent.x(), mouseEvent.y())) {
            searchBox.setValue("");  // Clear the search box
            searchBox.setFocused(true);  // Keep it focused for convenience
            return true;  // We handled this click
        }
        if (mouseEvent.button() == 0 && searchBox != null && !searchBox.isMouseOver(mouseEvent.x(), mouseEvent.y())) {
            searchBox.setFocused(false);  // Keep it focused for convenience
            return true;  // We handled this click
        }
        
        // Otherwise, let parent handle it (for clicking slots, etc.)
        return super.mouseClicked(mouseEvent, hasBeenHandled);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, GUI_TEXTURE, x, y, 0, 0, imageWidth, imageHeight, 256, 256);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        renderEMCInfo(guiGraphics, mouseX, mouseY, partialTick);

        super.renderTooltip(guiGraphics, mouseX, mouseY);
    }

    /**
     * Render the three EMC info displays
     */
    private void renderEMCInfo(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;

        // === 1. PLAYER EMC BALANCE (Top right, always visible) ===
        long balance = menu.getPlayerEMCBalance();
        String balanceText = String.format("%,d", balance);

        Component balanceLabel = Component.literal("EMC: ")
                .withStyle(ChatFormatting.GRAY);
        Component balanceValue = Component.literal(balanceText)
                .withStyle(ChatFormatting.YELLOW);

        // Position in top right corner of GUI
        //int balanceX = x + imageWidth - 10 - font.width(balanceText) - font.width("EMC: ");
        // Position in top left corner of GUI
        int balanceX = x + 8;
        int balanceY = y + 8;

        guiGraphics.drawString(font, balanceLabel, balanceX, balanceY, 0xFFAAAAAA, true);
        guiGraphics.drawString(font, balanceValue,
                balanceX + font.width("EMC: "), balanceY, 0xFFFFFF55, true);


        // === 2. EMC GAINED (Center, appears after burning) ===
        if (menu.shouldDisplayEMCGained()) {
            long gained = menu.getLastEMCGained();
            String gainedText = String.format("+%,d EMC", gained);

            // Calculate alpha for fade effect
            float alpha = menu.getEMCGainedAlpha();
            int alphaInt = (int)(alpha * 255);
            int color = (alphaInt << 24) | 0x55FF55; // Green color with alpha

            // Position in center of GUI
            int gainedX = x + 8;
            int gainedY = y + 20;

            guiGraphics.drawString(font, gainedText, gainedX, gainedY, color, true);
        }
        if (menu.shouldDisplayEMCLost()) {
            long lost = menu.getLastEMCLost();
            String lostText = String.format("-%,d EMC", lost);

            // Calculate alpha for fade effect
            float alpha = menu.getEMCLostAlpha();
            int alphaInt = (int)(alpha * 255);
            int color = (alphaInt << 24) | 0xFF5555; // Green color with alpha

            // Position in center of GUI
            int lostX = x + 8;
            int lostY = y + 20;

            guiGraphics.drawString(font, lostText, lostX, lostY, color, true);
        }

        // === 3. ITEM LEARNED (Below EMC gained, appears for new items) ===
        if (menu.shouldDisplayEMCGained() && menu.wasLastItemNew()) {
            String learnedText = "✦ Learned: " + menu.getLastItemName() + " ✦";

            // Same fade effect
            float alpha = menu.getEMCGainedAlpha();
            int alphaInt = (int)(alpha * 255);
            int color = (alphaInt << 24) | 0xFFAA00; // Orange color with alpha

            // Position below the EMC gained text
            int learnedX = x + (imageWidth / 2) - (font.width(learnedText) / 2);
            int learnedY = y + 87;

            guiGraphics.drawString(font, learnedText, learnedX, learnedY, color, true);
        }

        // === 4. ITEM UNLEARNED (Above unlearn slot) ===
        if (menu.shouldDisplayUnlearned()) {
            String unlearnedText = "✦ UnLearned: " + menu.getLastItemName2() + " ✦";
            Component text = Component.literal(unlearnedText).withStyle(ChatFormatting.RED);

            // Same fade effect
            float alpha = menu.getUnlearnAlpha();
            int alphaInt = (int)(alpha * 255);
            int color = (alphaInt << 24) | 0xFFAA00; // Orange color with alpha

            // Position below the EMC gained text
            int learnedX = x + (imageWidth / 2) - (font.width(unlearnedText) / 2);
            int learnedY = y + 87;

            guiGraphics.drawString(font, text, learnedX, learnedY, color, true);
        }

        ItemStack stone = menu.blockEntity.inventory.getStackInSlot(0);
        String maxEMCText = "Max EMC";
        Component text = Component.literal(maxEMCText).withStyle(ChatFormatting.GRAY);
        String maxEMCValue = EMCHelper.getStoneMaxEMCFormatted(stone);
        Component value = Component.literal(maxEMCValue).withStyle(ChatFormatting.YELLOW);

        // Same fade effect
        float alpha = 1;
        int alphaInt = (int)(alpha * 255);
        int color = (alphaInt << 24) | 0xFFFFFF; // WHITE

        // Position below the EMC gained text
        int emcX = x + (imageWidth / 2) - (font.width(text) / 2) - 61;
        int emcY = y + 67;

        guiGraphics.drawString(font, text, emcX, emcY, color, true);
        guiGraphics.drawString(font, value, emcX + 18 - font.width(value)/2, emcY + 10, color, true);
    }
}

