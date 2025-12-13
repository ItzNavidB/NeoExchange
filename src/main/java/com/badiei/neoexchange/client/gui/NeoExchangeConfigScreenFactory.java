package com.badiei.neoexchange.client.gui;

import com.badiei.neoexchange.NeoExchange;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.fml.ModContainer;

/**
 * Factory that creates our config screen when requested by NeoForge's mod menu.
 * 
 * In NeoForge 1.21.10+, config screen factories are functional interfaces
 * that take a ModContainer and parent Screen, and return a Screen.
 * 
 * This class provides a static method that serves as the factory.
 */
public class NeoExchangeConfigScreenFactory {
    
    /**
     * Creates the config screen. This is the method referenced in neoforge.mods.toml.
     * 
     * @param modContainer The mod container
     * @param parent The parent screen to return to
     * @return The config screen
     */
    public static Screen create(ModContainer modContainer, Screen parent) {
        NeoExchange.LOGGER.info("Creating NeoExchangeConfigScreen from static factory method");
        return new NeoExchangeConfigScreen(parent);
    }
}
