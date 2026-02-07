package com.islandium.prison.command.impl;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.islandium.prison.PrisonPlugin;
import com.islandium.prison.command.base.PrisonCommand;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Commande /prestige - Prestige au rang suivant.
 */
public class PrestigeCommand extends PrisonCommand {

    public PrestigeCommand(@NotNull PrisonPlugin plugin) {
        super(plugin, "prestige", "Prestige et recommence avec des bonus");
        requirePermission("prison.prestige");
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        if (!isPlayer(ctx)) {
            sendMessage(ctx, "&cCette commande est réservée aux joueurs!");
            return complete();
        }

        if (!hasPermission(ctx, "prison.prestige")) {
            sendMessage(ctx, "&cTu n'as pas la permission!");
            return complete();
        }

        UUID uuid = getPlayerUUID(ctx);

        // Check if can prestige
        if (!plugin.getRankManager().canPrestige(uuid)) {
            String currentRank = plugin.getRankManager().getPlayerRank(uuid);
            sendMessage(ctx, "&cTu dois atteindre le rang &eFREE &cpour pouvoir prestige!");
            sendMessage(ctx, "&7Rang actuel: &e" + currentRank);
            return complete();
        }

        int currentPrestige = plugin.getRankManager().getPlayerPrestige(uuid);

        // Confirm prestige
        sendMessage(ctx, "&6&l=== PRESTIGE ===");
        sendMessage(ctx, "");
        sendMessage(ctx, "&eTu es sur le point de prestige!");
        sendMessage(ctx, "&7Prestige actuel: &e" + currentPrestige);
        sendMessage(ctx, "&7Nouveau prestige: &a" + (currentPrestige + 1));
        sendMessage(ctx, "");
        sendMessage(ctx, "&c&lATTENTION:");
        sendMessage(ctx, "&7- Ton rang sera réinitialisé à &eA");
        sendMessage(ctx, "&7- Ton argent sera réinitialisé");
        sendMessage(ctx, "&a+ Tu gagneras un bonus permanent de &e+25% &ade gains");
        sendMessage(ctx, "");

        // Perform prestige
        if (plugin.getRankManager().prestige(uuid)) {
            sendMessage(ctx, "&a&l✓ PRESTIGE RÉUSSI!");
            sendMessage(ctx, "&aTu es maintenant Prestige &e" + (currentPrestige + 1) + "&a!");
            sendMessage(ctx, "&7Bon courage pour ta nouvelle progression!");
        } else {
            sendMessage(ctx, "&cErreur lors du prestige.");
        }

        return complete();
    }
}
