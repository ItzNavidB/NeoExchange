<!--
Guidance for AI coding agents working on NeoExchange.
Keep this file concise and actionable. Update when project structure or build changes.
-->
# Copilot instructions — NeoExchange

Purpose: Help AI agents be immediately productive in this Java/Gradle NeoForged mod template.

- **Big picture**: This repo is a Minecraft mod template using the NeoForged/Neoforge userdev Gradle plugin. The project is a standard Java/Gradle mod:
  - Sources: `src/main/java` (example package: `com.badiei.neoexchange`).
  - Resources: `src/main/resources` and generated resources in `src/generated/resources` (this directory is added to `sourceSets.main.resources`).
  - Build config: `build.gradle` defines the neoforge plugin, run configurations (`runs`), and resource expansion.

- **Key architectural patterns**:
  - Uses the `net.neoforged:neoforge` userdev plugin; mod lifecycle/hooks, registries, and config APIs come from that library.
  - Configuration classes follow the `ModConfigSpec` pattern. See `src/main/java/com/badiei/neoexchange/Config.java` for an example: `ModConfigSpec.Builder`, `define`, `defineInRange`, and `defineListAllowEmpty` are used, and validation often checks Minecraft registries (e.g., `BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(itemName))`).
  - Resource tokens in `META-INF/neoforge.mods.toml` and other resources are expanded at `ProcessResources` time using properties declared in `build.gradle` (`mod_id`, `mod_version`, etc.).

- **Developer workflows / commands** (Windows PowerShell examples):
  - Refresh dependencies: `.
    gradlew.bat --refresh-dependencies` (use the wrapper in repo root).
  - Clean build: `.
    gradlew.bat clean build` (produces artifacts under `build/`).
  - Run client for development: `.
    gradlew.bat runClient` (plugin task name provided by userdev; configured runs in `build.gradle`).
  - Run server: `.
    gradlew.bat runServer`.
  - Run game tests: `.
    gradlew.bat gameTestServer` (gameTestServer run config exists and will execute registered gametests).
  - Generate data (data gen / resources): `.
    gradlew.bat clientData` (output goes to `src/generated/resources`).

- **Project-specific conventions & gotchas**:
  - Java target: the project sets `java.toolchain.languageVersion = JavaLanguageVersion.of(21)` — assume Java 21 for compilation and runtime during development.
  - Optional runtime dependencies: the project uses a `localRuntime` configuration (see `build.gradle`). For optional mod jars that are only needed for local testing, use `localRuntime` instead of `runtimeOnly` so the published artifact does not depend on them.
  - `src/generated/resources` is part of `sourceSets.main.resources` — data generators write to this path and those resources are packaged.
  - Logging: default run configs set `forge.logging.console.level=debug` and `forge.logging.markers=REGISTRIES`. Use those markers when searching logs for registry-related events.
  - Resource location validation often uses `ResourceLocation.parse(...)` plus a registry check — replicate that pattern when adding validators (see `Config.validateItemName`).

- **Where to look for examples**:
  - Config pattern: `src/main/java/com/badiei/neoexchange/Config.java`.
  - Build & run patterns: `build.gradle` (run configs, property expansion, `clientData` args).
  - Generated assets: `src/generated/resources/` (check after running data tasks).

- **Do / Don’t for changes**:
  - Do: update `build.gradle` properties used by `ProcessResources` when adding new tokens in `META-INF` files.
  - Do: use the `ModConfigSpec` builder pattern for config values and provide validation functions for registry-backed strings.
  - Don’t: move generated resource paths — use `src/generated/resources` unless you update `build.gradle` to include the new path.
  - Don’t: publish dependencies accidentally — put optional local-only mod jars in `localRuntime`.

- **Testing & debugging tips**:
  - To reproduce developer runs, use the Gradle wrapper tasks above; the `runs` block in `build.gradle` sets recommended logging and system properties.
  - If a new registry entry isn't visible during dev runs, verify the mod registers during the `REGISTRIES` phase and check console logs (console level is `debug`).

If anything here looks wrong or you'd like more examples (e.g., registry registration patterns or a sample `gametest`), tell me which area to expand. 
