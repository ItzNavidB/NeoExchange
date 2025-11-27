package com.badiei.neoexchange.emc;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import com.badiei.neoexchange.NeoExchange;
import org.slf4j.Logger;

import java.util.UUID;

/**
 * EMCCommands - In-game commands for testing and managing EMC
 *
 * Commands are how players and admins interact with your mod through
 * the chat/console. We use Mojang's Brigadier command system.
 *
 * The @EventBusSubscriber annotation makes this class automatically
 * register itself to listen for the command registration event.
 */
@EventBusSubscriber(modid = NeoExchange.MOD_ID)
public class EMCCommands {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Register all EMC commands
     *
     * This method is called automatically when the game registers commands
     * The @SubscribeEvent annotation tells NeoForge to call this method
     */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        /**
         * Build the command tree
         *
         * We're creating: /emc <subcommand> [arguments]
         *
         * The literal() method creates a fixed word (like "emc")
         * The then() method adds sub-commands
         * The executes() method defines what happens when you run the command
         */
        dispatcher.register(
                Commands.literal("emc")
                        // /emc balance - Check your EMC balance
                        .then(Commands.literal("balance")
                                .executes(EMCCommands::getBalance))

                        // /emc add <amount> - Add EMC (admin only)
                        .then(Commands.literal("add")
                                .requires(source -> source.hasPermission(2)) // Permission level 2 = operators
                                .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                        .executes(EMCCommands::addEMC)))

                        // /emc set <amount> - Set EMC (admin only)
                        .then(Commands.literal("set")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                        .executes(EMCCommands::setEMC)))

                        // /emc remove <amount> - Remove EMC (admin only)
                        .then(Commands.literal("remove")
                                .requires(source -> source.hasPermission(2))
                                .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                        .executes(EMCCommands::removeEMC)))

                        // /emc reload - Reload the EMC registry (admin only)
                        .then(Commands.literal("reload")
                                .requires(source -> source.hasPermission(2))
                                .executes(EMCCommands::reloadRegistry))

                        // /emc calculate - Run the EMC calculator (admin only)
                        .then(Commands.literal("calculate")
                                .requires(source -> source.hasPermission(2))
                                .executes(EMCCommands::calculateEMC))

                        // /emc stats - Show EMC registry statistics
                        .then(Commands.literal("stats")
                                .executes(EMCCommands::showStats))
        );
    }

    /**
     * Command: /emc balance
     * Shows the player's current EMC balance
     */
    private static int getBalance(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();

        if (player == null) {
            context.getSource().sendFailure(Component.literal("Only players can use this command!"));
            return 0;
        }

        long balance = EMCHelper.getBalance(player);
        String formatted = EMCHelper.getFormattedBalance(player);
        LOGGER.info("player: {}, balance: {}, UUID: {}", player, balance);

        // Send a nice formatted message to the player
        context.getSource().sendSuccess(
                () -> Component.literal("Your EMC Balance: ")
                        .append(Component.literal(formatted).withStyle(style ->
                                style.withColor(0x00FF00))), // Green color
                false // Don't broadcast to other players
        );

        return (int) Math.min(balance, Integer.MAX_VALUE);
    }

    /**
     * Command: /emc add <amount>
     * Adds EMC to the player's balance (admin only)
     */
    private static int addEMC(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();

        if (player == null) {
            context.getSource().sendFailure(Component.literal("Only players can use this command!"));
            return 0;
        }

        long amount = LongArgumentType.getLong(context, "amount");

        if (EMCHelper.addEMC(player, amount)) {
            context.getSource().sendSuccess(
                    () -> Component.literal("Added ")
                            .append(Component.literal(String.format("%,d", amount))
                                    .withStyle(style -> style.withColor(0x00FF00)))
                            .append(" EMC. New balance: ")
                            .append(Component.literal(EMCHelper.getFormattedBalance(player))
                                    .withStyle(style -> style.withColor(0x00FF00))),
                    false
            );
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Failed to add EMC (overflow?)"));
            return 0;
        }
    }

    /**
     * Command: /emc set <amount>
     * Sets the player's EMC balance (admin only)
     */
    private static int setEMC(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();

        if (player == null) {
            context.getSource().sendFailure(Component.literal("Only players can use this command!"));
            return 0;
        }

        long amount = LongArgumentType.getLong(context, "amount");
        EMCHelper.setBalance(player, amount);

        context.getSource().sendSuccess(
                () -> Component.literal("Set EMC balance to ")
                        .append(Component.literal(EMCHelper.getFormattedBalance(player))
                                .withStyle(style -> style.withColor(0x00FF00))),
                false
        );

        return 1;
    }

    /**
     * Command: /emc remove <amount>
     * Removes EMC from the player's balance (admin only)
     */
    private static int removeEMC(CommandContext<CommandSourceStack> context) {
        ServerPlayer player = context.getSource().getPlayer();

        if (player == null) {
            context.getSource().sendFailure(Component.literal("Only players can use this command!"));
            return 0;
        }

        long amount = LongArgumentType.getLong(context, "amount");

        if (EMCHelper.removeEMC(player, amount)) {
            context.getSource().sendSuccess(
                    () -> Component.literal("Removed ")
                            .append(Component.literal(String.format("%,d", amount))
                                    .withStyle(style -> style.withColor(0xFF0000)))
                            .append(" EMC. New balance: ")
                            .append(Component.literal(EMCHelper.getFormattedBalance(player))
                                    .withStyle(style -> style.withColor(0x00FF00))),
                    false
            );
            return 1;
        } else {
            context.getSource().sendFailure(Component.literal("Insufficient EMC!"));
            return 0;
        }
    }

    /**
     * Command: /emc reload
     * Reloads the EMC registry (admin only)
     */
    private static int reloadRegistry(CommandContext<CommandSourceStack> context) {
        EMCRegistry registry = EMCRegistry.getInstance();

        if (!registry.isInitialized()) {
            context.getSource().sendFailure(
                    Component.literal("EMC Registry not initialized!")
            );
            return 0;
        }

        context.getSource().sendSuccess(
                () -> Component.literal("Reloading EMC Registry...").withStyle(style ->
                        style.withColor(0xFFAA00)),
                true
        );

        // Reload the registry from disk
        registry.reload();

        EMCRegistry.RegistryStats stats = registry.getStats();

        context.getSource().sendSuccess(
                () -> Component.literal("Registry reloaded successfully!\n")
                        .append(Component.literal("Stats: " + stats.toString())
                                .withStyle(style -> style.withColor(0x00FF00))),
                true
        );

        return stats.activeValues();
    }

    /**
     * Command: /emc calculate
     * Runs the EMC calculator to generate values from recipes
     */
    private static int calculateEMC(CommandContext<CommandSourceStack> context) {
        EMCRegistry registry = EMCRegistry.getInstance();

        if (!registry.isInitialized()) {
            context.getSource().sendFailure(
                    Component.literal("EMC Registry not initialized!")
            );
            return 0;
        }

        context.getSource().sendSuccess(
                () -> Component.literal("Starting EMC calculation...")
                        .append(Component.literal(" This may take a moment...")
                                .withStyle(style -> style.withColor(0xFFAA00))),
                true
        );

        // Run the calculator
        // We need the server level for recipe access
        if (context.getSource().getLevel() != null) {
            int itemCount = registry.runCalculator(context.getSource().getLevel());

            context.getSource().sendSuccess(
                    () -> Component.literal("EMC calculation complete! ")
                            .append(Component.literal(String.valueOf(itemCount))
                                    .withStyle(style -> style.withColor(0x00FF00)))
                            .append(" items now have EMC values."),
                    true
            );

            return itemCount;
        } else {
            context.getSource().sendFailure(
                    Component.literal("Failed to access level for calculation")
            );
            return 0;
        }
    }

    /**
     * Command: /emc stats
     * Shows statistics about the EMC registry
     */
    private static int showStats(CommandContext<CommandSourceStack> context) {
        EMCRegistry registry = EMCRegistry.getInstance();

        if (!registry.isInitialized()) {
            context.getSource().sendFailure(
                    Component.literal("EMC Registry not initialized!")
            );
            return 0;
        }

        EMCRegistry.RegistryStats stats = registry.getStats();

        context.getSource().sendSuccess(
                () -> Component.literal("=== EMC Registry Statistics ===\n")
                        .append(Component.literal("Base Values: ")
                                .append(Component.literal(String.valueOf(stats.baseValues()))
                                        .withStyle(style -> style.withColor(0x00FF00)))
                                .append("\n"))
                        .append(Component.literal("Computed Values: ")
                                .append(Component.literal(String.valueOf(stats.computedValues()))
                                        .withStyle(style -> style.withColor(0x00FF00)))
                                .append("\n"))
                        .append(Component.literal("Active Values: ")
                                .append(Component.literal(String.valueOf(stats.activeValues()))
                                        .withStyle(style -> style.withColor(0x00FF00))))
                                .append("\n")
                        .append(Component.literal("Inactive Values: ")
                                .append(Component.literal(String.valueOf(stats.inactiveValues()))
                                        .withStyle(style -> style.withColor(0x00FF00)))),
                false
        );

        return stats.activeValues();
    }
}