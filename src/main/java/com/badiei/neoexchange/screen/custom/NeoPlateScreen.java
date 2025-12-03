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

    //Data for the grid
    private List<Item> availableItems = new ArrayList<>(); // Items player can extract
    private int scrollOffset = 0; // Which row we're scrolled to
    private String searchText = ""; // Current search filter

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

