package com.badiei.neoexchange.client.gui;

import com.badiei.neoexchange.NeoExchange;
import com.badiei.neoexchange.config.ClientConfig;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Configuration screen for NeoExchange client settings.
 * 
 * This screen allows players to adjust client-side options like UI scale,
 * animation settings, and performance options through a visual interface
 * instead of editing TOML files manually.
 * 
 * The screen is automatically accessible through Minecraft's Mod Options menu.
 */
public class NeoExchangeConfigScreen extends Screen {
    private final Screen parentScreen;
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private static final int SPACING = 24;

    /**
     * @param parentScreen The screen to return to when done (usually the mods list)
     */
    public NeoExchangeConfigScreen(Screen parentScreen) {
        super(Component.literal("NeoExchange Config"));
        this.parentScreen = parentScreen;
    }

    @Override
    protected void init() {
        super.init();

        // Calculate starting position (centered on screen)
        int x = this.width / 2 - BUTTON_WIDTH / 2;
        int y = 40; // Start 40 pixels from top

        // ===== UI SETTINGS =====
        
        // Toggle button for EMC tooltips
        this.addRenderableWidget(
            Button.builder(
                Component.literal("EMC Tooltips: " + (ClientConfig.SHOW_EMC_TOOLTIPS.get() ? "ON" : "OFF")),
                button -> {
                    boolean newValue = !ClientConfig.SHOW_EMC_TOOLTIPS.get();
                    ClientConfig.SHOW_EMC_TOOLTIPS.set(newValue);
                    button.setMessage(Component.literal("EMC Tooltips: " + (newValue ? "ON" : "OFF")));
                }
            )
            .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
            .tooltip(Tooltip.create(Component.literal("Show EMC values in item tooltips")))
            .build()
        );
        y += SPACING;

        // Toggle for search highlighting
        this.addRenderableWidget(
            Button.builder(
                Component.literal("Search Highlighting: " + (ClientConfig.ENABLE_SEARCH_HIGHLIGHTING.get() ? "ON" : "OFF")),
                button -> {
                    boolean newValue = !ClientConfig.ENABLE_SEARCH_HIGHLIGHTING.get();
                    ClientConfig.ENABLE_SEARCH_HIGHLIGHTING.set(newValue);
                    button.setMessage(Component.literal("Search Highlighting: " + (newValue ? "ON" : "OFF")));
                }
            )
            .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
            .tooltip(Tooltip.create(Component.literal("Highlight matching text when searching")))
            .build()
        );
        y += SPACING;

        // Slider for scroll sensitivity (1-10)
        this.addRenderableWidget(
            new ConfigSlider(
                x, y, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.literal("Scroll Sensitivity: "),
                1, 10,
                ClientConfig.SCROLL_SENSITIVITY.get()
            ) {
                @Override
                protected void applyValue() {
                    ClientConfig.SCROLL_SENSITIVITY.set(this.getValueInt());
                }
            }
        );
        y += SPACING;

        // Slider for UI scale (0.5 to 2.0)
        this.addRenderableWidget(
            new ConfigSliderDouble(
                x, y, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.literal("UI Scale: "),
                0.5, 2.0,
                ClientConfig.UI_SCALE.get()
            ) {
                @Override
                protected void applyValue() {
                    ClientConfig.UI_SCALE.set(this.value);
                }
            }
        );
        y += SPACING + 10; // Extra space before next category

        // ===== ANIMATION SETTINGS =====
        
        this.addRenderableWidget(
            Button.builder(
                Component.literal("Item Animations: " + (ClientConfig.ENABLE_ITEM_ANIMATIONS.get() ? "ON" : "OFF")),
                button -> {
                    boolean newValue = !ClientConfig.ENABLE_ITEM_ANIMATIONS.get();
                    ClientConfig.ENABLE_ITEM_ANIMATIONS.set(newValue);
                    button.setMessage(Component.literal("Item Animations: " + (newValue ? "ON" : "OFF")));
                }
            )
            .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
            .tooltip(Tooltip.create(Component.literal("Enable floating/rotating item animations")))
            .build()
        );
        y += SPACING;

        this.addRenderableWidget(
            new ConfigSlider(
                x, y, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.literal("Animation Speed: "),
                1, 20,
                ClientConfig.ANIMATION_SPEED.get()
            ) {
                @Override
                protected void applyValue() {
                    ClientConfig.ANIMATION_SPEED.set(this.getValueInt());
                }
            }
        );
        y += SPACING + 10;

        // ===== PERFORMANCE SETTINGS =====
        
        this.addRenderableWidget(
            new ConfigSlider(
                x, y, BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.literal("Max Visible Items: "),
                36, 180,
                ClientConfig.MAX_VISIBLE_ITEMS.get()
            ) {
                @Override
                protected void applyValue() {
                    ClientConfig.MAX_VISIBLE_ITEMS.set(this.getValueInt());
                }
            }
        );
        y += SPACING;

        this.addRenderableWidget(
            Button.builder(
                Component.literal("Cache Item Renders: " + (ClientConfig.CACHE_ITEM_RENDERS.get() ? "ON" : "OFF")),
                button -> {
                    boolean newValue = !ClientConfig.CACHE_ITEM_RENDERS.get();
                    ClientConfig.CACHE_ITEM_RENDERS.set(newValue);
                    button.setMessage(Component.literal("Cache Item Renders: " + (newValue ? "ON" : "OFF")));
                }
            )
            .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
            .tooltip(Tooltip.create(Component.literal("Cache renders for better performance")))
            .build()
        );
        y += SPACING + 20;

        // ===== DONE BUTTON =====
        
        this.addRenderableWidget(
            Button.builder(
                Component.literal("Done"),
                button -> {
                    // Save the config to disk
                    ClientConfig.SPEC.save();
                    // Return to parent screen
                    this.minecraft.setScreen(this.parentScreen);
                }
            )
            .bounds(this.width / 2 - 100, this.height - 30, BUTTON_WIDTH, BUTTON_HEIGHT)
            .build()
        );
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Render the background
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        
        // Draw the title
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 15, 0xFFFFFF);
        
        // Draw category labels
        graphics.drawString(this.font, "UI Settings", this.width / 2 - BUTTON_WIDTH / 2, 28, 0xFFFF00);
        graphics.drawString(this.font, "Animations", this.width / 2 - BUTTON_WIDTH / 2, 138, 0xFFFF00);
        graphics.drawString(this.font, "Performance", this.width / 2 - BUTTON_WIDTH / 2, 210, 0xFFFF00);
        
        // Render all buttons and widgets
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() {
        // Save when closing with ESC
        ClientConfig.SPEC.save();
        this.minecraft.setScreen(this.parentScreen);
    }

    /**
     * Custom slider for integer values.
     * Extends AbstractSliderButton to create a clean slider interface.
     */
    private static abstract class ConfigSlider extends AbstractSliderButton {
        private final Component prefix;
        private final int minValue;
        private final int maxValue;

        public ConfigSlider(int x, int y, int width, int height, Component prefix, int minValue, int maxValue, int initialValue) {
            super(x, y, width, height, Component.empty(), (double)(initialValue - minValue) / (maxValue - minValue));
            this.prefix = prefix;
            this.minValue = minValue;
            this.maxValue = maxValue;
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Component.literal(prefix.getString() + getValueInt()));
        }

        public int getValueInt() {
            return (int) Math.round(this.value * (maxValue - minValue)) + minValue;
        }
    }

    /**
     * Custom slider for double/decimal values.
     */
    private static abstract class ConfigSliderDouble extends AbstractSliderButton {
        private final Component prefix;
        private final double minValue;
        private final double maxValue;

        public ConfigSliderDouble(int x, int y, int width, int height, Component prefix, double minValue, double maxValue, double initialValue) {
            super(x, y, width, height, Component.empty(), (initialValue - minValue) / (maxValue - minValue));
            this.prefix = prefix;
            this.minValue = minValue;
            this.maxValue = maxValue;
            this.updateMessage();
        }

        @Override
        protected void updateMessage() {
            double currentValue = this.value * (maxValue - minValue) + minValue;
            this.setMessage(Component.literal(prefix.getString() + String.format("%.1f", currentValue)));
        }
    }
}
