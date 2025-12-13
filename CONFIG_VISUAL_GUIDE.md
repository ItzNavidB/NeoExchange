# Config System Flow Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                        CONFIG SYSTEM OVERVIEW                        │
└─────────────────────────────────────────────────────────────────────┘

┌──────────────────┐
│  Game Startup    │
└────────┬─────────┘
         │
         ▼
┌─────────────────────────────────────────────────┐
│  NeoExchange Constructor                        │
│                                                 │
│  modContainer.registerConfig(                  │
│      ModConfig.Type.CLIENT,                    │
│      ClientConfig.SPEC                         │
│  );                                            │
└────────┬────────────────────────────────────────┘
         │
         │ NeoForge processes registration
         ▼
┌─────────────────────────────────────────────────┐
│  NeoForge Config System                         │
│                                                 │
│  1. Checks if config file exists:              │
│     .minecraft/config/neoexchange-client.toml  │
│                                                 │
│  2. If not, creates it with defaults           │
│  3. If yes, loads values from file             │
│  4. Validates values (ranges, types)           │
│  5. Makes values available via .get()          │
└────────┬────────────────────────────────────────┘
         │
         │ Config is now ready
         │
         ├──────────────────┬─────────────────┐
         │                  │                 │
         ▼                  ▼                 ▼
┌─────────────────┐  ┌─────────────┐  ┌─────────────┐
│  Your Code      │  │  In-Game    │  │ Manual File │
│                 │  │  Config UI  │  │  Editing    │
│  int val =      │  │             │  │             │
│  ClientConfig   │  │  Player     │  │  Player     │
│  .SCROLL_SENS   │  │  changes    │  │  edits      │
│  .get();       │  │  slider     │  │  .toml      │
│                 │  │             │  │             │
│  Uses value!    │  │             │  │             │
└─────────────────┘  └──────┬──────┘  └──────┬──────┘
                            │                │
                            │ Saves changes  │ Saves file
                            │                │
                            ▼                ▼
                     ┌──────────────────────────┐
                     │  Config File Updated     │
                     │                          │
                     │  scrollSensitivity = 5   │
                     │  (was 3)                │
                     └──────────┬───────────────┘
                                │
                                │ Automatic reload!
                                ▼
                     ┌──────────────────────────┐
                     │  Next .get() call        │
                     │  returns NEW value       │
                     │                          │
                     │  No restart needed! ✓    │
                     └──────────────────────────┘
```

---

## Config Definition Flow

```
ClientConfig.java
═══════════════

┌─────────────────────────────────────┐
│ 1. DECLARE THE FIELD                │
│                                     │
│ public static final                 │
│ ModConfigSpec.IntValue SCROLL_SENS; │
└─────────────────┬───────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────┐
│ 2. DEFINE IN STATIC BLOCK                       │
│                                                 │
│ static {                                        │
│     BUILDER.push("ui");                         │
│                                                 │
│     SCROLL_SENS = BUILDER                       │
│         .comment("Scroll speed (1-10)")         │
│         .defineInRange("scrollSensitivity",     │
│                        3,    // default         │
│                        1,    // min             │
│                        10);  // max             │
│                                                 │
│     BUILDER.pop();                              │
│ }                                               │
└─────────────────┬───────────────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────┐
│ 3. BUILD THE SPEC                   │
│                                     │
│ public static final ModConfigSpec   │
│ SPEC = BUILDER.build();            │
└─────────────────┬───────────────────┘
                  │
                  ▼
┌─────────────────────────────────────┐
│ 4. REGISTER IN MAIN MOD CLASS       │
│                                     │
│ modContainer.registerConfig(        │
│     ModConfig.Type.CLIENT,         │
│     ClientConfig.SPEC              │
│ );                                 │
└─────────────────┬───────────────────┘
                  │
                  ▼
┌─────────────────────────────────────┐
│ 5. USE ANYWHERE IN CLIENT CODE      │
│                                     │
│ int scroll =                        │
│     ClientConfig.SCROLL_SENS.get(); │
└─────────────────────────────────────┘
```

---

## Config File Structure

```
.minecraft/config/neoexchange-client.toml
═════════════════════════════════════════

[ui]                              ← BUILDER.push("ui")
    showEMCTooltips = true
    scrollSensitivity = 3
    uiScale = 1.0                 ← BUILDER.pop()

[animations]                      ← BUILDER.push("animations")
    enableItemAnimations = true
    animationSpeed = 10           ← BUILDER.pop()

[performance]                     ← BUILDER.push("performance")
    maxVisibleItems = 90
    cacheItemRenders = true       ← BUILDER.pop()
```

---

## Usage Pattern

```
┌────────────────────────────────────────────────┐
│  WHEN PLAYER SCROLLS                           │
└────────────────┬───────────────────────────────┘
                 │
                 ▼
        ┌────────────────────┐
        │ mouseScrolled()    │
        │ event fires        │
        └────────┬───────────┘
                 │
                 ▼
        ┌──────────────────────────────────┐
        │ int scrollAmount =               │
        │     ClientConfig.SCROLL_SENS     │
        │     .get();                      │
        │                                  │
        │ // Returns CURRENT value         │
        │ // from config file!             │
        └────────┬─────────────────────────┘
                 │
                 ▼
        ┌──────────────────────────────────┐
        │ targetScrollOffset +=            │
        │     scrollAmount;                │
        │                                  │
        │ // Uses config value to          │
        │ // determine scroll distance     │
        └──────────────────────────────────┘
```

---

## Config Type Inheritance

```
┌───────────────────────────┐
│  ModConfigSpec.Value<T>   │  ← Base class
└────────────┬──────────────┘
             │
     ┌───────┴───────┬──────────┬──────────┐
     │               │          │          │
     ▼               ▼          ▼          ▼
┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐
│ Boolean  │  │   Int    │  │  Double  │  │  Config  │
│  Value   │  │  Value   │  │  Value   │  │ Value<T> │
└──────────┘  └──────────┘  └──────────┘  └──────────┘
     │               │          │          │
     │ .get()        │ .get()   │ .get()   │ .get()
     │ returns       │ returns  │ returns  │ returns
     │ boolean       │ int      │ double   │ T
     │               │          │          │
     ▼               ▼          ▼          ▼
 true/false      42, 100      1.5, 2.0    String, List, etc.
```

---

## Client vs Server Config

```
CLIENT CONFIG                      SERVER CONFIG
═════════════                      ═════════════

┌───────────────┐                  ┌───────────────┐
│ Client Only   │                  │ Server Only   │
│               │                  │               │
│ • UI scale    │                  │ • Max EMC     │
│ • Animations  │                  │ • Item limits │
│ • Tooltips    │                  │ • World rules │
│ • Colors      │                  │               │
└───────────────┘                  └───────────────┘
        │                                  │
        │ Stored in:                       │ Stored in:
        │                                  │
        ▼                                  ▼
.minecraft/config/                 world/serverconfig/
modid-client.toml                 modid-server.toml
        │                                  │
        │ Loaded on:                       │ Loaded on:
        ▼                                  ▼
  Client only                        Server only
  (your PC)                         (server PC)
        │                                  │
        │                                  │ Synced to:
        │                                  ▼
        │                            All connected
        │                            clients
        │                                  │
        └──────────┬───────────────────────┘
                   │
                   ▼
             ┌─────────────┐
             │ COMMON      │
             │ CONFIG      │
             │             │
             │ Loaded on   │
             │ BOTH!       │
             └─────────────┘

    Use for mechanics that
    need to match on both
    client and server
```

---

## Real-World Example: Animation Toggle

```
┌────────────────────────────────────────────────────┐
│  PLAYER'S PERSPECTIVE                              │
└────────────────────────────────────────────────────┘

1. Player has slow PC, animations lag
   └→ Opens config menu (Mods → NeoExchange)
   
2. Finds "Enable Item Animations" toggle
   └→ Clicks it OFF
   
3. Saves config
   └→ File updated instantly

┌────────────────────────────────────────────────────┐
│  CODE PERSPECTIVE                                  │
└────────────────────────────────────────────────────┘

// Before player disabled animations
NeoPlateEntityRenderer.submit() {
    if (ClientConfig.ENABLE_ITEM_ANIMATIONS.get()) {
        // .get() returns TRUE → animations run
        poseStack.mulPose(rotation);
    }
}

// After player disabled animations  
NeoPlateEntityRenderer.submit() {
    if (ClientConfig.ENABLE_ITEM_ANIMATIONS.get()) {
        // .get() returns FALSE → this block skipped!
        // No rotation applied, item stays still
    }
}

Result: Smoother performance on slow PC! ✓
```

---

## Best Practices Flowchart

```
                    ┌─────────────────────┐
                    │ Adding new config?  │
                    └──────────┬──────────┘
                               │
                               ▼
                    ┌──────────────────────┐
                    │ Is it visual/UI only?│
                    └────┬──────────┬───────┘
                    YES  │          │  NO
                         ▼          ▼
                 ┌──────────┐  ┌───────────┐
                 │  CLIENT  │  │  COMMON   │
                 │  CONFIG  │  │  or       │
                 │          │  │  SERVER   │
                 └──────────┘  └───────────┘
                         │          │
                         ▼          ▼
              ┌─────────────┐  ┌────────────┐
              │ Add to      │  │ Create new │
              │ ClientConfig│  │ config     │
              │             │  │ class      │
              └──────┬──────┘  └──────┬─────┘
                     │                │
                     └────────┬───────┘
                              ▼
                   ┌──────────────────────┐
                   │ Choose config type:  │
                   │                      │
                   │ • Boolean → toggle   │
                   │ • Int → number       │
                   │ • Double → decimal   │
                   │ • String → text      │
                   │ • Enum → dropdown    │
                   └──────────┬───────────┘
                              │
                              ▼
                   ┌──────────────────────┐
                   │ Add descriptive      │
                   │ .comment()          │
                   │                      │
                   │ Players will read    │
                   │ this!               │
                   └──────────┬───────────┘
                              │
                              ▼
                   ┌──────────────────────┐
                   │ Set sensible default │
                   │                      │
                   │ Most players won't   │
                   │ change it            │
                   └──────────┬───────────┘
                              │
                              ▼
                   ┌──────────────────────┐
                   │ Use .get() in code   │
                   │                      │
                   │ Never cache values!  │
                   └──────────────────────┘
```

This visual guide should help you understand the complete flow of the config system! 🎯
