/**
 * GUIDE: How to Add New Config Options
 * =====================================
 * 
 * When you want to add a new setting that players can control,
 * follow these steps:
 * 
 * STEP 1: Define the config value in ClientConfig.java
 * -----------------------------------------------------
 * 
 * Add it in the appropriate category (inside the correct push/pop block):
 * 
 * ```java
 * static {
 *     BUILDER.push("ui");  // or "animations" or "performance"
 *     
 *     // For a boolean (ON/OFF toggle):
 *     MY_NEW_SETTING = BUILDER
 *         .comment("What this setting does - appears in the TOML file")
 *         .define("myNewSetting", true);  // true = default value
 *     
 *     // For an integer with a range:
 *     MY_NUMBER_SETTING = BUILDER
 *         .comment("A number setting")
 *         .defineInRange("myNumberSetting", 5, 1, 10);  // (key, default, min, max)
 *     
 *     // For a decimal number:
 *     MY_DECIMAL_SETTING = BUILDER
 *         .comment("A decimal setting")
 *         .defineInRange("myDecimalSetting", 1.5, 0.5, 3.0);
 *     
 *     BUILDER.pop();
 * }
 * ```
 * 
 * And declare it at the top of the class:
 * 
 * ```java
 * public static final ModConfigSpec.BooleanValue MY_NEW_SETTING;
 * public static final ModConfigSpec.IntValue MY_NUMBER_SETTING;
 * public static final ModConfigSpec.DoubleValue MY_DECIMAL_SETTING;
 * ```
 * 
 * STEP 2: Add a control to NeoExchangeConfigScreen.java
 * ------------------------------------------------------
 * 
 * In the init() method, add a widget for your new setting:
 * 
 * For a toggle button:
 * ```java
 * this.addRenderableWidget(
 *     Button.builder(
 *         Component.literal("My Setting: " + (ClientConfig.MY_NEW_SETTING.get() ? "ON" : "OFF")),
 *         button -> {
 *             boolean newValue = !ClientConfig.MY_NEW_SETTING.get();
 *             ClientConfig.MY_NEW_SETTING.set(newValue);
 *             button.setMessage(Component.literal("My Setting: " + (newValue ? "ON" : "OFF")));
 *         }
 *     )
 *     .bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT)
 *     .tooltip(Tooltip.create(Component.literal("Helpful tooltip text")))
 *     .build()
 * );
 * y += SPACING;  // Move down for next widget
 * ```
 * 
 * For a slider:
 * ```java
 * this.addRenderableWidget(
 *     new ExtendedSlider(
 *         x, y, BUTTON_WIDTH, BUTTON_HEIGHT,
 *         Component.literal("My Number: "),
 *         Component.empty(),
 *         1, 10,  // min, max
 *         ClientConfig.MY_NUMBER_SETTING.get(),  // current value
 *         1.0,    // step size
 *         0,      // precision (decimal places)
 *         true    // show value
 *     ) {
 *         @Override
 *         protected void applyValue() {
 *             ClientConfig.MY_NUMBER_SETTING.set((int) this.getValue());
 *         }
 *     }
 * );
 * y += SPACING;
 * ```
 * 
 * STEP 3: Use the config value in your code
 * ------------------------------------------
 * 
 * Anywhere you need it:
 * ```java
 * if (ClientConfig.MY_NEW_SETTING.get()) {
 *     // do something
 * }
 * 
 * int value = ClientConfig.MY_NUMBER_SETTING.get();
 * // use the value
 * ```
 * 
 * 
 * ADVANCED: Different Config Types
 * =================================
 * 
 * COMMON CONFIG (gameplay mechanics that can differ client/server):
 * -----------------------------------------------------------------
 * 
 * Create CommonConfig.java similar to ClientConfig.java, then register it:
 * 
 * ```java
 * // In NeoExchange.java constructor:
 * modContainer.registerConfig(ModConfig.Type.COMMON, CommonConfig.SPEC);
 * ```
 * 
 * This creates neoexchange-common.toml in the config folder.
 * Changes require restarting the game.
 * Both client and server have their own copy.
 * 
 * 
 * SERVER CONFIG (rules enforced by the server):
 * ---------------------------------------------
 * 
 * Create ServerConfig.java, then register it:
 * 
 * ```java
 * modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
 * ```
 * 
 * This creates neoexchange-server.toml in the world's serverconfig folder.
 * Only the server can change these values.
 * Perfect for things like "max EMC per player" or "enabled/disabled features".
 * 
 * 
 * SYNCING SERVER CONFIG TO CLIENTS:
 * ---------------------------------
 * 
 * If clients need to know server config values (for UI purposes),
 * you can send them via packets. But that's more advanced!
 * 
 * For now, remember:
 * - CLIENT config = visual preferences, UI, animations
 * - COMMON config = features that might differ between client/server
 * - SERVER config = server admin controls gameplay rules
 * 
 * 
 * TIPS:
 * =====
 * 
 * 1. Choose good default values
 *    - Think about what most players would want
 *    - Err on the side of "better performance" for defaults
 * 
 * 2. Write clear comments
 *    - Players will read the TOML file
 *    - Explain what each option does in simple terms
 * 
 * 3. Set reasonable ranges
 *    - Don't let players set scroll sensitivity to 1000
 *    - Prevent values that would crash the game
 * 
 * 4. Test with extreme values
 *    - What if someone sets UI scale to 0.5?
 *    - What if they set max items to 180?
 *    - Make sure nothing breaks!
 * 
 * 5. Consider performance
 *    - Don't call .get() thousands of times per frame
 *    - Cache values at the start of render/tick methods
 * 
 * 6. Provide tooltips
 *    - Help players understand what each option does
 *    - Mention if changing it requires a restart
 */
