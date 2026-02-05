package com.islandium.prison.command.impl;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.islandium.prison.PrisonPlugin;
import com.islandium.prison.command.base.PrisonCommand;
import com.islandium.prison.config.PrisonConfig;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Commande /ranks - Affiche la liste des rangs.
 */
public class RanksCommand extends PrisonCommand {

    public RanksCommand(@NotNull PrisonPlugin plugin) {
        super(plugin, "ranks", "Affiche la liste des rangs prison");
        addAliases("prisonranks", "ranklist");
        requirePermission("prison.ranks");
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        if (!hasPermission(ctx, "prison.ranks")) {
            sendMessage(ctx, "&cTu n'as pas la permission!");
            return complete();
        }

        List<PrisonConfig.RankInfo> ranks = plugin.getConfig().getRanks();
        String currentRank = "A";

        if (isPlayer(ctx)) {
            UUID playerUUID = getPlayerUUID(ctx);
            currentRank = plugin.getRankManager().getPlayerRank(playerUUID);
        }

        sendMessage(ctx, "&6&l=== Rangs Prison ===");
        sendMessage(ctx, "");

        for (PrisonConfig.RankInfo rank : ranks) {
            boolean isCurrent = rank.id.equalsIgnoreCase(currentRank);
            boolean isUnlocked = plugin.getRankManager().isRankHigherOrEqual(currentRank, rank.id);

            String status;
            if (isCurrent) {
                status = "&a[ACTUEL]";
            } else if (isUnlocked) {
                status = "&2[DÉBLOQUÉ]";
            } else {
                status = "&7[VERROUILLÉ]";
            }

            String color = isCurrent ? "&a" : (isUnlocked ? "&2" : "&7");

            sendMessage(ctx, String.format("%s%s &8- &f%s &8- &e%s$ %s",
                    color, rank.id,
                    rank.displayName,
                    rank.price.toString(),
                    status
            ));
        }

        sendMessage(ctx, "");
        sendMessage(ctx, "&7Utilise &e/rankup &7pour monter de rang!");

        return complete();
    }
}
