package com.badiei.neoexchange;

import com.badiei.neoexchange.NeoCreativeModeTabs;
import com.badiei.neoexchange.blocks.NeoBlocks;
import com.badiei.neoexchange.blocks.entity.NeoBlockEntities;
import com.badiei.neoexchange.blocks.entity.NeoPlateEntity;
import com.badiei.neoexchange.blocks.entity.renderer.NeoPlateEntityRenderer;
import com.badiei.neoexchange.emc.EMCCalculator;
import com.badiei.neoexchange.emc.EMCRegistry;
import com.badiei.neoexchange.emc.NeoAttachments;
import com.badiei.neoexchange.items.NeoItems;

import com.badiei.neoexchange.screen.ModMenuTypes;
import com.badiei.neoexchange.screen.custom.NeoPlateScreen;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;

import java.nio.file.Path;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(NeoExchange.MOD_ID)
public class NeoExchange {
    // Define mod id in a common place for everything to reference
    public static final String MOD_ID = "neoexchange";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger();
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "examplemod" namespace
    //public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MOD_ID);

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public NeoExchange(IEventBus modEventBus, ModContainer modContainer) {
        // Register the commonSetup method for modloading
        modEventBus.addListener(this::commonSetup);
        // Register the Deferred Register to the mod event bus so tabs get registered
        //CREATIVE_MODE_TABS.register(modEventBus);

        // Register ourselves for server and other game events we are interested in.
        // Note that this is necessary if and only if we want *this* class (ExampleMod) to respond directly to events.
        // Do not add this line if there are no @SubscribeEvent-annotated functions in this class, like onServerStarting() below.
        NeoForge.EVENT_BUS.register(this);

        NeoCreativeModeTabs.register(modEventBus);

        NeoItems.register(modEventBus);
        NeoBlocks.register(modEventBus);

        NeoBlockEntities.register(modEventBus);
        ModMenuTypes.register(modEventBus);

        NeoAttachments.register(modEventBus);

        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);



    }

    /**
     * Common setup - runs on both client and server
     *
     * This is where you do things that need to happen after
     * all mods are loaded but before the game starts.
     *
     * The event.enqueueWork() ensures thread safety - some things
     * must happen on the main thread!
     */
    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Initialize the EMC Registry with JSON config
            LOGGER.info("Initializing EMC Registry with JSON config...");

            // Get the config directory
            // This will be something like .minecraft/config/neoexchange/
            Path configDir = FMLPaths.CONFIGDIR.get().resolve(MOD_ID);
            ResourceLocation emcFile = ResourceLocation.fromNamespaceAndPath(MOD_ID, "emc/emc_base_values.json");

            EMCRegistry.getInstance();
            EMCRegistry.getInstance().initialize(configDir);
            // You could add custom EMC values for your items here
            // registry.registerEMC(NeoItems.NEO_STONE.get(), 512L);

            //EMCRegistry.RegistryStats stats = registry.getStats();
            //LOGGER.info("EMC Registry initialized: {}", stats);
        });
    }

    // Add the example block item to the building blocks tab
    private void addCreative(BuildCreativeModeTabContentsEvent event) {
    }

    // You can use SubscribeEvent and let the Event Bus discover methods to call
    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("Server starting - calculating EMC values...");

        // Get the overworld level
        net.minecraft.server.level.ServerLevel level = event.getServer().getLevel(net.minecraft.world.level.Level.OVERWORLD);
        java.nio.file.Path configDir = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get()
                .resolve(MOD_ID);

        if (level != null) {
            EMCRegistry registry = EMCRegistry.getInstance();
            registry.initialize(configDir);
            int count = registry.runCalculator(level);
            LOGGER.info("EMC calculation complete! {} items now have EMC values", count);

        }
    }

    @EventBusSubscriber(modid = NeoExchange.MOD_ID)
    public static class ClientModEVents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {

        }

        @SubscribeEvent
        public static void registerBER(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(NeoBlockEntities.NEO_PLATE_BE.get(), NeoPlateEntityRenderer::new);
        }

        @SubscribeEvent
        public static void registerScreens(RegisterMenuScreensEvent event) {
            event.register(ModMenuTypes.NEO_PLATE_MENU.get(), NeoPlateScreen::new);
        }
    }
}
