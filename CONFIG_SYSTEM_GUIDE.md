# Client Configuration Guide for NeoExchange

## What We Just Built

We've added a **client-side configuration system** that lets players customize their NeoExchange experience through both:
1. **In-game config menu** (Mods → NeoExchange → Config button)
2. **Manual .toml file editing** (`.minecraft/config/neoexchange-client.toml`)

---

## Understanding Config Types

Minecraft/NeoForge has **three config types**:

### 1. **CLIENT Config** ✅ (What we just made!)
- **Stored**: `.minecraft/config/modid-client.toml`
- **When loaded**: Only on the client (player's computer)
- **Use for**: Visual settings, UI preferences, keybinds, animations
- **Example**: "Show tooltips", "Animation speed", "UI scale"

### 2. **COMMON Config**
- **Stored**: `.minecraft/config/modid-common.toml`
- **When loaded**: Both client AND server
- **Use for**: Gameplay mechanics that should match on both sides
- **Example**: "EMC calculation multipliers", "Recipe costs"

### 3. **SERVER Config**
- **Stored**: `world/serverconfig/modid-server.toml` (per-world!)
- **When loaded**: Only on the server
- **Use for**: Server-only rules, world generation settings
- **Example**: "Max EMC per player", "Item blacklist"

---

## How Our Config Works

### Step 1: Define Config Options

In `ClientConfig.java`, we use a **builder pattern**:

```java
private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

// Define a boolean option
public static final ModConfigSpec.BooleanValue ENABLE_ANIMATIONS;

static {
    BUILDER.push("animations");  // Create a category
    
    ENABLE_ANIMATIONS = BUILDER
        .comment("Enable item animations")  // Shows in .toml file
        .define("enableAnimations", true);  // Key and default value
    
    BUILDER.pop();  // End category
}

// Build the final spec
public static final ModConfigSpec SPEC = BUILDER.build();
```

### Step 2: Register the Config

In `NeoExchange.java` constructor:

```java
modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
```

This tells NeoForge:
- "Hey, this mod has a CLIENT config"
- "Here's the specification for what's in it"
- "Please create/load the file for me"

### Step 3: Use Config Values

Anywhere in your **client-side code**:

```java
import com.badiei.neoexchange.config.ClientConfig;

// Get the current value
boolean animationsEnabled = ClientConfig.ENABLE_ANIMATIONS.get();

// Use it
if (animationsEnabled) {
    // Do animation
}
```

**IMPORTANT**: Config values are **live-reloaded**! If a player changes a setting, `get()` immediately returns the new value.

---

## Config Value Types

### BooleanValue (true/false)
```java
public static final ModConfigSpec.BooleanValue MY_TOGGLE;

MY_TOGGLE = BUILDER
    .comment("Enable feature")
    .define("myToggle", true);  // default: true

// Usage
if (ClientConfig.MY_TOGGLE.get()) { ... }
```

### IntValue (whole numbers with range)
```java
public static final ModConfigSpec.IntValue MY_NUMBER;

MY_NUMBER = BUILDER
    .comment("Some number (1-100)")
    .defineInRange("myNumber", 50, 1, 100);  // default: 50, min: 1, max: 100

// Usage
int value = ClientConfig.MY_NUMBER.get();
```

### DoubleValue (decimal numbers)
```java
public static final ModConfigSpec.DoubleValue MY_SCALE;

MY_SCALE = BUILDER
    .comment("Scale factor")
    .defineInRange("myScale", 1.0, 0.5, 2.0);  // default: 1.0, min: 0.5, max: 2.0

// Usage
double scale = ClientConfig.MY_SCALE.get();
```

### ConfigValue<String> (text)
```java
public static final ModConfigSpec.ConfigValue<String> MY_TEXT;

MY_TEXT = BUILDER
    .comment("Some text")
    .define("myText", "default value");

// Usage
String text = ClientConfig.MY_TEXT.get();
```

### ConfigValue<List> (lists of values)
```java
public static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_BLACKLIST;

ITEM_BLACKLIST = BUILDER
    .comment("Items to exclude")
    .defineListAllowEmpty("itemBlacklist", 
        List.of("minecraft:bedrock", "minecraft:barrier"),  // defaults
        () -> "",  // List element supplier (empty string = any string ok)
        obj -> obj instanceof String  // Validator
    );

// Usage
List<String> blacklist = (List<String>) ClientConfig.ITEM_BLACKLIST.get();
```

---

## Categories in Config Files

Categories help **organize** your config file:

```java
BUILDER.push("ui");
    // All UI options here...
BUILDER.pop();

BUILDER.push("performance");
    // All performance options here...
BUILDER.pop();
```

This creates:

```toml
[ui]
    showTooltips = true
    uiScale = 1.0

[performance]
    maxItems = 90
    cacheRenders = true
```

---

## Real Example: Scroll Sensitivity

### Before Config:
```java
// Hardcoded - always scrolls 1 row
if (deltaY > 0) {
    targetScrollOffset = Math.max(0, targetScrollOffset - 1);
}
```

### After Config:
```java
// Now respects player's preference (1-10 rows)
int scrollAmount = ClientConfig.SCROLL_SENSITIVITY.get();
if (deltaY > 0) {
    targetScrollOffset = Math.max(0, targetScrollOffset - scrollAmount);
}
```

### Player Experience:
1. Player edits config: `scrollSensitivity = 5`
2. Saves file
3. Next time they scroll: moves 5 rows instead of 1!
4. **No restart required** - changes apply instantly!

---

## Advanced: Config Change Listeners

Sometimes you need to **react** when a config changes:

```java
// In your main mod class
modEventBus.addListener((ModConfigEvent event) -> {
    if (event.getConfig().getSpec() == ClientConfig.SPEC) {
        LOGGER.info("Client config changed!");
        
        // React to specific changes
        if (ClientConfig.CACHE_RENDERS.get()) {
            // Rebuild cache
        } else {
            // Clear cache
        }
    }
});
```

---

## Where Config Files Go

### Development (when testing your mod):
- Client: `run/config/neoexchange-client.toml`
- Common: `run/config/neoexchange-common.toml`
- Server: `run/saves/YourWorld/serverconfig/neoexchange-server.toml`

### Players (when playing):
- Client: `.minecraft/config/neoexchange-client.toml`
- Common: `.minecraft/config/neoexchange-common.toml`
- Server: `world/serverconfig/neoexchange-server.toml`

---

## Config File Format (.toml)

TOML is like a simplified JSON. Here's what your config will look like:

```toml
# These comments come from .comment() in your code

[ui]
    # Show EMC values in item tooltips throughout the game
    showEMCTooltips = true
    # Highlight matching text when searching in Neo Plate
    enableSearchHighlighting = true
    # How many items to scroll per mouse wheel tick (1-10)
    scrollSensitivity = 3
    # Scale factor for the Neo Plate GUI (0.5 to 2.0)
    uiScale = 1.0

[animations]
    # Enable floating/rotating animations for items in Neo Plate
    enableItemAnimations = true
    # Animation speed multiplier in ticks (1-20, higher = faster)
    animationSpeed = 10

[performance]
    # Maximum items to display in the virtual inventory grid (36-180)
    maxVisibleItems = 90
    # Cache item renders for better performance
    cacheItemRenders = true
```

---

## Best Practices

### ✅ DO:
- Use **descriptive comments** - these show up in the .toml file!
- Set **reasonable ranges** with `defineInRange()` - prevents players from breaking things
- Use **categories** to organize related settings
- Give **sensible defaults** - most players won't change configs
- Put **client-only** visuals in CLIENT config
- Put **gameplay mechanics** in COMMON config

### ❌ DON'T:
- Don't put server-authority logic in CLIENT config
  - ❌ Bad: "Max EMC balance" in client (player could cheat!)
  - ✅ Good: "Show EMC in tooltips" in client (visual only)
- Don't use CLIENT config values on the server
  - It won't exist on dedicated servers!
- Don't call `.get()` during mod construction
  - Config isn't loaded yet, use events or lazy initialization

---

## Testing Your Config

1. **Run the game** in your IDE
2. Open the mod menu (usually Mods button on title screen)
3. Find "NeoExchange" and click "Config"
4. Change values and click "Save"
5. Watch it take effect immediately!

OR

1. Run the game once (creates the file)
2. Close the game
3. Edit `run/config/neoexchange-client.toml` manually
4. Start game again
5. Your changes should be loaded

---

## What's Next?

You can now add config options for:
- **EMC tooltip colors** (ConfigValue<Integer> for hex colors)
- **Search behavior** (case-sensitive? partial match?)
- **Favorite item sorting** (top of list? highlighted?)
- **Visual effects** (particle effects, sounds)
- **Performance toggles** (reduce animations on low-end PCs)

## Future Expansion: Server Config

If you later want **server-controlled settings** (like max EMC or recipe costs), create:

```java
public class ServerConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    
    public static final ModConfigSpec.LongValue MAX_EMC_BALANCE;
    
    static {
        MAX_EMC_BALANCE = BUILDER
            .comment("Maximum EMC a player can store")
            .defineInRange("maxEMCBalance", 999_999_999L, 0L, Long.MAX_VALUE);
    }
    
    public static final ModConfigSpec SPEC = BUILDER.build();
}
```

Then register it:
```java
modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
```

**Why SERVER config matters**: It's stored per-world and synced to clients, so server admins can control gameplay balance!

---

## Summary

You now have a **fully functional config system** that:
✅ Lets players customize their experience
✅ Supports live-reloading (no restart needed)
✅ Shows up in the in-game config menu
✅ Is properly organized and documented
✅ Follows NeoForge best practices

**What we added:**
1. `ClientConfig.java` - Defines all client settings
2. Registration in `NeoExchange.java` - Hooks it into the system
3. Usage in `NeoPlateEntityRenderer.java` - Animation toggle
4. Usage in `NeoPlateScreen.java` - Scroll sensitivity

**Players can now adjust:**
- UI scale and tooltips
- Animation speed and toggle
- Scroll sensitivity
- Performance options
- And more!
