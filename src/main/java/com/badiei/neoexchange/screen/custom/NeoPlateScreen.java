package com.badiei.neoexchange.screen.custom;

import com.badiei.neoexchange.NeoExchange;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class NeoPlateScreen extends AbstractContainerScreen<NeoPlateMenu> {
    private static final ResourceLocation GUI_TEXTURE = ResourceLocation.fromNamespaceAndPath(NeoExchange.MOD_ID, "textures/gui/neo_plate/neo_plate_gui.png");

    public NeoPlateScreen(NeoPlateMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.titleLabelY = titleLabelY - 5;
        this.titleLabelX = titleLabelX + 62;
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
        super.renderTooltip(guiGraphics, mouseX, mouseY);
    }
}
