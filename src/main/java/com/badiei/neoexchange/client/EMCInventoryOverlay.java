package com.badiei.neoexchange.client;

import com.badiei.neoexchange.NeoExchange;
import com.badiei.neoexchange.emc.EMCHelper;
import com.badiei.neoexchange.emc.EMCHelperNBT;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.slf4j.Logger;

import java.awt.*;
import java.util.Objects;
import java.util.UUID;

@EventBusSubscriber
public class EMCInventoryOverlay {
    private static final Logger LOGGER = LogUtils.getLogger();
    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (event.getScreen() instanceof InventoryScreen) {
            Minecraft instance = Minecraft.getInstance();
            Player player = instance.player;
            if (player != null) {
                String balance = EMCHelper.getFormattedBalance(player);
                GuiGraphics graphics = event.getGuiGraphics();
                int w = event.getScreen().width/2;
                int h = event.getScreen().height/2;
                String text = "EMC: ";
                graphics.drawString(instance.font, text, w - 85, h - 95, 0xFFAAAAAA, true);
                graphics.drawString(instance.font, balance, w - 63, h - 95, 0xFFFFFF55, true);
            }
        }
    }
}
