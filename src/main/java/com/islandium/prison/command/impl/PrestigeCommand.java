package com.islandium.prison.command.impl;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.islandium.core.api.util.NotificationType;
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
            sendNotification(ctx, NotificationType.ERROR, "Cette commande est reservee aux joueurs!");
            return complete();
        }

        if (!hasPermission(ctx, "prison.prestige")) {
            sendNotification(ctx, NotificationType.ERROR, "Tu n'as pas la permission!");
            return complete();
        }

        UUID uuid = getPlayerUUID(ctx);

        // Check if can prestige
        if (!plugin.getRankManager().canPrestige(uuid)) {
            String currentRank = plugin.getRankManager().getPlayerRank(uuid);
            sendNotification(ctx, NotificationType.ERROR, "Tu dois atteindre le rang FREE pour pouvoir prestige! Rang actuel: " + currentRank);
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
            sendNotification(ctx, NotificationType.SUCCESS, "PRESTIGE REUSSI! Tu es maintenant Prestige " + (currentPrestige + 1) + "!");
            sendMessage(ctx, "&7Bon courage pour ta nouvelle progression!");
        } else {
            sendNotification(ctx, NotificationType.ERROR, "Erreur lors du prestige.");
        }

        return complete();
    }
}
