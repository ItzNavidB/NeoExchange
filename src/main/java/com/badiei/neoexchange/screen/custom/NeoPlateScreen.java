package com.badiei.neoexchange.screen.custom;

import com.badiei.neoexchange.NeoExchange;
import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import org.slf4j.Logger;

public class NeoPlateScreen extends AbstractContainerScreen<NeoPlateMenu> {
    private static final ResourceLocation GUI_TEXTURE = ResourceLocation.fromNamespaceAndPath(NeoExchange.MOD_ID, "textures/gui/neo_plate/neo_plate_gui.png");
    public static final Logger LOGGER = LogUtils.getLogger();

    public NeoPlateScreen(NeoPlateMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 228;
        this.imageHeight = 196;
        this.titleLabelY = titleLabelY;
        this.titleLabelX = titleLabelX + 8;
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
        int balanceX = x + imageWidth - 10 - font.width(balanceText) - font.width("EMC: ");
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
            int gainedX = x + (imageWidth / 2) - (font.width(gainedText) / 2);
            int gainedY = y + 25;

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
            int learnedY = y + 37;

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
    }
}

