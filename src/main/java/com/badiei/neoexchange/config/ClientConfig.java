package com.badiei.neoexchange.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-side configuration for NeoExchange.
 * 
 * These settings are stored in .minecraft/config/neoexchange-client.toml
 * and can be changed by players through the in-game config screen.
 * 
 * Only affects the client's display and behavior - doesn't change gameplay.
 */
public class ClientConfig {
    // This builder lets us define our config options
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ===== UI SETTINGS =====
    // These affect how the Neo Plate GUI looks and behaves
    
    public static final ModConfigSpec.BooleanValue SHOW_EMC_TOOLTIPS;
    public static final ModConfigSpec.BooleanValue ENABLE_SEARCH_HIGHLIGHTING;
    public static final ModConfigSpec.IntValue SCROLL_SENSITIVITY;
    public static final ModConfigSpec.DoubleValue UI_SCALE;
    
    // ===== ANIMATION SETTINGS =====
    
    public static final ModConfigSpec.BooleanValue ENABLE_ITEM_ANIMATIONS;
    public static final ModConfigSpec.IntValue ANIMATION_SPEED;
    
    // ===== PERFORMANCE SETTINGS =====
    
    public static final ModConfigSpec.IntValue MAX_VISIBLE_ITEMS;
    public static final ModConfigSpec.BooleanValue CACHE_ITEM_RENDERS;

    static {
        // Push a category - this groups related options together in the config file
        BUILDER.push("ui");
        
        // Define each option with:
        // - comment: explains what it does (shows in config file)
        // - define/defineInRange: the actual setting with default value
        
        SHOW_EMC_TOOLTIPS = BUILDER
                .comment("Show EMC values in item tooltips throughout the game")
                .define("showEMCTooltips", true);
        
        ENABLE_SEARCH_HIGHLIGHTING = BUILDER
                .comment("Highlight matching text when searching in Neo Plate")
                .define("enableSearchHighlighting", true);
        
        SCROLL_SENSITIVITY = BUILDER
                .comment("How many items to scroll per mouse wheel tick (1-10)")
                .defineInRange("scrollSensitivity", 3, 1, 10);
        
        UI_SCALE = BUILDER
                .comment("Scale factor for the Neo Plate GUI (0.5 to 2.0)")
                .defineInRange("uiScale", 1.0, 0.5, 2.0);
        
        BUILDER.pop(); // End of "ui" category
        
        // Animation settings category
        BUILDER.push("animations");
        
        ENABLE_ITEM_ANIMATIONS = BUILDER
                .comment("Enable floating/rotating animations for items in Neo Plate")
                .define("enableItemAnimations", true);
        
        ANIMATION_SPEED = BUILDER
                .comment("Animation speed multiplier in ticks (1-20, higher = faster)")
                .defineInRange("animationSpeed", 10, 1, 20);
        
        BUILDER.pop();
        
        // Performance settings category
        BUILDER.push("performance");
        
        MAX_VISIBLE_ITEMS = BUILDER
                .comment("Maximum items to display in the virtual inventory grid (36-180)")
                .defineInRange("maxVisibleItems", 90, 36, 180);
        
        CACHE_ITEM_RENDERS = BUILDER
                .comment("Cache item renders for better performance (disable if you have render issues)")
                .define("cacheItemRenders", true);
        
        BUILDER.pop();
    }

    // This is what gets registered - the built specification
    public static final ModConfigSpec SPEC = BUILDER.build();
}
