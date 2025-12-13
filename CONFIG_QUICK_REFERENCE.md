# Config Quick Reference Card

## Adding a New Config Option (3 Steps)

### Step 1: Declare the field in ClientConfig.java
```java
public static final ModConfigSpec.IntValue MY_NEW_SETTING;
```

### Step 2: Define it in the static block
```java
static {
    BUILDER.push("my_category");
    
    MY_NEW_SETTING = BUILDER
        .comment("What this setting does")
        .defineInRange("myNewSetting", 50, 0, 100);  // default, min, max
    
    BUILDER.pop();
}
```

### Step 3: Use it anywhere in client code
```java
int value = ClientConfig.MY_NEW_SETTING.get();
```

---

## Common Config Types Cheat Sheet

| Type | Code | Example Value |
|------|------|---------------|
| On/Off Toggle | `BooleanValue` | `true` / `false` |
| Whole Number | `IntValue` | `42` |
| Decimal | `DoubleValue` | `1.5` |
| Text | `ConfigValue<String>` | `"Hello"` |
| Number List | `ConfigValue<List<Integer>>` | `[1, 2, 3]` |
| Text List | `ConfigValue<List<String>>` | `["a", "b"]` |

---

## Type Templates

### Boolean (Checkbox)
```java
public static final ModConfigSpec.BooleanValue FEATURE_ENABLED;

FEATURE_ENABLED = BUILDER
    .comment("Enable this feature")
    .define("featureEnabled", true);

// Use: if (ClientConfig.FEATURE_ENABLED.get()) { ... }
```

### Integer with Range (Slider)
```java
public static final ModConfigSpec.IntValue VOLUME;

VOLUME = BUILDER
    .comment("Volume level (0-100)")
    .defineInRange("volume", 75, 0, 100);

// Use: int vol = ClientConfig.VOLUME.get();
```

### Double with Range (Precise Slider)
```java
public static final ModConfigSpec.DoubleValue SCALE;

SCALE = BUILDER
    .comment("UI scale (0.5 to 2.0)")
    .defineInRange("scale", 1.0, 0.5, 2.0);

// Use: double s = ClientConfig.SCALE.get();
```

### String (Text Field)
```java
public static final ModConfigSpec.ConfigValue<String> CUSTOM_TEXT;

CUSTOM_TEXT = BUILDER
    .comment("Custom text to display")
    .define("customText", "Default Value");

// Use: String text = ClientConfig.CUSTOM_TEXT.get();
```

### Enum (Dropdown)
```java
public enum SortMode { NAME, EMC, RARITY }

public static final ModConfigSpec.EnumValue<SortMode> SORT_MODE;

SORT_MODE = BUILDER
    .comment("How to sort items")
    .defineEnum("sortMode", SortMode.NAME);

// Use: SortMode mode = ClientConfig.SORT_MODE.get();
```

### List of Strings (Multiple Values)
```java
public static final ModConfigSpec.ConfigValue<List<? extends String>> BLACKLIST;

BLACKLIST = BUILDER
    .comment("Items to exclude")
    .defineListAllowEmpty("blacklist", 
        List.of("minecraft:dirt", "minecraft:stone"),
        () -> "",
        obj -> obj instanceof String);

// Use: List<String> items = (List<String>) ClientConfig.BLACKLIST.get();
```

---

## File Locations

| Environment | File Path |
|-------------|-----------|
| Dev (your IDE) | `run/config/neoexchange-client.toml` |
| Player's Game | `.minecraft/config/neoexchange-client.toml` |
| Server | `world/serverconfig/neoexchange-server.toml` |

---

## Config Types & When to Use

| Config Type | When to Use | Example |
|-------------|-------------|---------|
| **CLIENT** | Visual/UI only | Animations, tooltips, colors |
| **COMMON** | Shared logic | Recipe costs, EMC values |
| **SERVER** | Server rules | Max balance, item limits |

---

## Registration (Main Mod Class Constructor)

```java
// Client config
modContainer.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);

// Common config
modContainer.registerConfig(ModConfig.Type.COMMON, CommonConfig.SPEC);

// Server config
modContainer.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);
```

---

## Tips & Tricks

### ✅ Live Reload
- Configs auto-reload when changed
- No game restart needed!
- `.get()` always returns current value

### ✅ Validation
- Use `defineInRange()` to prevent invalid values
- Players can't set volume to 1000 if max is 100

### ✅ Comments Matter
- They appear in the .toml file
- Help players understand what each option does

### ✅ Defaults Are Important
- Most players never change configs
- Pick good defaults that work for 90% of people

### ❌ Don't Use Client Config on Server
- Dedicated servers don't have CLIENT configs
- Server crashes if you try to access them

### ❌ Don't Call .get() During Construction
- Configs aren't loaded yet
- Use events or lazy initialization

---

## Debugging Config Issues

### Config not appearing?
1. Check registration in main mod class
2. Make sure it's `ModConfig.Type.CLIENT` not COMMON
3. Look in `run/config/` folder

### Values not applying?
1. Make sure you're calling `.get()` not using a cached value
2. Check you're using the right config type (CLIENT vs COMMON)
3. Add debug logging: `LOGGER.info("Config value: {}", ClientConfig.MY_VALUE.get());`

### Config file missing?
- Run game once to generate it
- It's created automatically when mod loads

---

## Example: Complete Feature with Config

**Goal**: Add a "show item count" feature in the Neo Plate

**1. Add config option:**
```java
// In ClientConfig.java
public static final ModConfigSpec.BooleanValue SHOW_ITEM_COUNT;

SHOW_ITEM_COUNT = BUILDER
    .comment("Show item count in virtual slots")
    .define("showItemCount", true);
```

**2. Use in rendering:**
```java
// In NeoPlateScreen.java
@Override
protected void renderSlot(GuiGraphics graphics, Slot slot) {
    super.renderSlot(graphics, slot);
    
    if (ClientConfig.SHOW_ITEM_COUNT.get() && slot instanceof VirtualEMCSlot virtualSlot) {
        int count = virtualSlot.getAffordableAmount();
        String text = String.valueOf(count);
        graphics.drawString(font, text, slot.x + 12, slot.y + 12, 0xFFFFFF);
    }
}
```

**3. Test it:**
- Run game
- Open mod config menu
- Toggle "Show Item Count"
- See it change instantly!

---

## Next Steps

Want to add more configs? Common additions:
- **Color pickers** (IntValue with hex colors)
- **Keybinds** (use KeyMapping, not ModConfigSpec)
- **Sound toggles** (BooleanValue)
- **Performance modes** (EnumValue with LOW/MEDIUM/HIGH)
- **Custom EMC multipliers** (DoubleValue)

Happy configuring! 🎮
