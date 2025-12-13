package com.badiei.neoexchange.client.gui;

import com.badiei.neoexchange.config.ClientConfig;

/**
 * EXAMPLE FILE: How to Use Config Values in Your Code
 * ====================================================
 * 
 * This is a demonstration file showing all the different ways
 * you can read and use the config values you've defined.
 * 
 * You don't need to actually use this class - it's just for learning!
 */
public class ConfigUsageExample {

    /**
     * Example 1: Simple boolean check
     * 
     * This is the most common pattern - just check if a feature is enabled
     * before executing code.
     */
    public void exampleBooleanUsage() {
        // Check if EMC tooltips are enabled
        if (ClientConfig.SHOW_EMC_TOOLTIPS.get()) {
            // Add EMC info to tooltip
            // addTooltipLine("EMC: 1024");
        }
        
        // You can also store it in a local variable if you use it multiple times
        boolean animationsEnabled = ClientConfig.ENABLE_ITEM_ANIMATIONS.get();
        if (animationsEnabled) {
            // doFloatingAnimation();
            // doRotationAnimation();
        }
    }

    /**
     * Example 2: Using numeric values (IntValue)
     * 
     * Integer configs are perfect for counts, limits, or speed multipliers.
     */
    public void exampleIntUsage() {
        // Get scroll sensitivity to determine how far to scroll
        int sensitivity = ClientConfig.SCROLL_SENSITIVITY.get();
        int scrollAmount = sensitivity * 3;  // Multiply by item height or whatever
        
        // Use max visible items to limit grid size
        int maxItems = ClientConfig.MAX_VISIBLE_ITEMS.get();
        // createVirtualGrid(maxItems);
        
        // Use animation speed for tick-based timing
        int animSpeed = ClientConfig.ANIMATION_SPEED.get();
        // Every 'animSpeed' ticks, advance the animation by one frame
        // if (tickCount % animSpeed == 0) { nextFrame(); }
    }

    /**
     * Example 3: Using decimal values (DoubleValue)
     * 
     * Double configs are great for scaling factors, percentages, etc.
     */
    public void exampleDoubleUsage() {
        // Get UI scale and apply it to rendering
        double scale = ClientConfig.UI_SCALE.get();
        
        // In your render method, you'd do something like:
        // graphics.pose().pushPose();
        // graphics.pose().scale((float)scale, (float)scale, 1.0f);
        // renderYourUI(graphics);
        // graphics.pose().popPose();
    }

    /**
     * Example 4: Real-world usage in NeoPlateScreen
     * 
     * Here's how you might actually use these configs in your screen class.
     */
    public void renderExample() {
        // BEFORE rendering items in the grid:
        if (!ClientConfig.ENABLE_ITEM_ANIMATIONS.get()) {
            // Skip animation calculations entirely
            // Just render items static
            return;
        }
        
        // Calculate animation based on speed setting
        int animSpeed = ClientConfig.ANIMATION_SPEED.get();
        // float rotation = (tickCount % (20 * animSpeed)) / (float)(20 * animSpeed) * 360f;
        
        // Apply UI scale
        double uiScale = ClientConfig.UI_SCALE.get();
        // graphics.pose().scale((float)uiScale, (float)uiScale, 1.0f);
    }

    /**
     * Example 5: Performance optimization based on config
     * 
     * Use configs to enable/disable expensive features
     */
    public void performanceExample() {
        // Check if caching is enabled
        if (ClientConfig.CACHE_ITEM_RENDERS.get()) {
            // Use cached render from previous frame
            // renderFromCache(itemStack);
        } else {
            // Re-render from scratch each time
            // renderFresh(itemStack);
        }
        
        // Limit grid size based on performance setting
        int maxVisible = ClientConfig.MAX_VISIBLE_ITEMS.get();
        // List<Item> itemsToDisplay = allItems.subList(0, Math.min(allItems.size(), maxVisible));
    }

    /**
     * Example 6: Conditional rendering based on config
     * 
     * Show/hide UI elements based on user preference
     */
    public void conditionalRenderExample() {
        // Only highlight search results if enabled
        if (ClientConfig.ENABLE_SEARCH_HIGHLIGHTING.get()) {
            // drawHighlight(x, y, width, height);
        }
    }

    /**
     * Example 7: Using config in event handlers
     * 
     * Modify behavior based on user settings
     */
    public void mouseScrollExample(double scrollDelta) {
        // Get sensitivity from config
        int sensitivity = ClientConfig.SCROLL_SENSITIVITY.get();
        
        // Calculate how many items to scroll
        int itemsToScroll = (int) (scrollDelta * sensitivity);
        
        // Apply the scroll
        // scrollOffset += itemsToScroll;
    }

    /**
     * IMPORTANT NOTES:
     * ================
     * 
     * 1. CONFIG VALUES ARE LIVE
     *    - When a player changes a config in the GUI and clicks "Done",
     *      the value is saved and IMMEDIATELY available
     *    - You don't need to restart the game (unless you want to require that)
     * 
     * 2. ALWAYS USE .get()
     *    - ClientConfig.SHOW_EMC_TOOLTIPS is a BooleanValue object
     *    - ClientConfig.SHOW_EMC_TOOLTIPS.get() is the actual boolean
     *    - Don't forget the .get()!
     * 
     * 3. CLIENT-SIDE ONLY
     *    - These configs are CLIENT configs
     *    - They only exist on the client (the player's computer)
     *    - Don't try to access them in server-side code!
     *    - If you need server configs, create a separate ServerConfig class
     * 
     * 4. THREAD SAFETY
     *    - Config values can be changed at any time
     *    - If you cache a value, it might become outdated
     *    - For real-time responsiveness, call .get() when you need the value
     *    - For performance, cache the value at the start of a render/tick
     * 
     * 5. VALIDATION
     *    - The defineInRange() already validates min/max
     *    - You don't need to check if a value is in range - it's guaranteed!
     */

    /**
     * Example 8: Best practice pattern for render methods
     */
    public void renderMethodBestPractice() {
        // Cache config values at the start of the method
        // This way they're consistent throughout the render,
        // but still responsive if changed between renders
        boolean showAnimations = ClientConfig.ENABLE_ITEM_ANIMATIONS.get();
        int animSpeed = ClientConfig.ANIMATION_SPEED.get();
        double uiScale = ClientConfig.UI_SCALE.get();
        int maxItems = ClientConfig.MAX_VISIBLE_ITEMS.get();
        
        // Now use these local variables throughout your render method
        if (showAnimations) {
            // doAnimations(animSpeed);
        }
        
        // scaleUI(uiScale);
        // renderItems(maxItems);
    }
}
