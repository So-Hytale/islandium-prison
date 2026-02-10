package com.islandium.prison.command;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.islandium.prison.PrisonPlugin;
import com.islandium.prison.command.impl.*;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Gestionnaire des commandes Prison.
 */
public class PrisonCommandManager {

    private final PrisonPlugin plugin;
    private final List<AbstractCommand> commands = new ArrayList<>();

    public PrisonCommandManager(@NotNull PrisonPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Enregistre toutes les commandes.
     */
    public void registerAll() {
        // Rank commands
        register(new RankupCommand(plugin));
        register(new RanksCommand(plugin));
        register(new PrestigeCommand(plugin));

        // Mine commands
        register(new MineCommand(plugin));
        register(new MinesCommand(plugin));

        // Cell commands -> migre vers islandium-cells (/cell et /celladmin)

        // Economy
        register(new SellCommand(plugin));
        register(new SellAllCommand(plugin));

        // Upgrades
        register(new UpgradeCommand(plugin));

        // Leaderboard
        register(new TopCommand(plugin));

        // Admin commands
        register(new PrisonAdminCommand(plugin));

        plugin.log(Level.INFO, "Registered " + commands.size() + " prison commands");
    }

    private void register(@NotNull AbstractCommand command) {
        plugin.getCommandRegistry().registerCommand(command);
        commands.add(command);
    }

    @NotNull
    public List<AbstractCommand> getCommands() {
        return commands;
    }
}
