package com.islandium.prison.command.impl;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.islandium.prison.PrisonPlugin;
import com.islandium.prison.command.base.PrisonCommand;
import com.islandium.prison.config.PrisonConfig;
import com.islandium.prison.rank.PrisonRankManager;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Commande /rankup - Monte d'un rang.
 */
public class RankupCommand extends PrisonCommand {

    public RankupCommand(@NotNull PrisonPlugin plugin) {
        super(plugin, "rankup", "Monte au rang suivant");
        addAliases("ru", "ranku");
        requirePermission("prison.rankup");
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        if (!isPlayer(ctx)) {
            sendMessage(ctx, "&cCette commande est réservée aux joueurs!");
            return complete();
        }

        if (!hasPermission(ctx, "prison.rankup")) {
            sendMessage(ctx, "&cTu n'as pas la permission!");
            return complete();
        }

        UUID uuid = getPlayerUUID(ctx);
        PrisonRankManager rankManager = plugin.getRankManager();

        // Check next rank
        PrisonConfig.RankInfo nextRank = rankManager.getNextRankInfo(uuid);
        if (nextRank == null) {
            sendConfigMessage(ctx, "rankup.max-rank");
            return complete();
        }

        // Attempt rankup
        PrisonRankManager.RankupResult result = rankManager.rankup(uuid);

        switch (result) {
            case SUCCESS:
                sendConfigMessage(ctx, "rankup.success", "rank", nextRank.displayName);
                break;
            case NOT_ENOUGH_MONEY:
                sendConfigMessage(ctx, "rankup.not-enough-money", "price", nextRank.price.toString());
                break;
            case MAX_RANK:
                sendConfigMessage(ctx, "rankup.max-rank");
                break;
            case CHALLENGES_INCOMPLETE:
                int completed = plugin.getChallengeManager().getCompletedCount(uuid, rankManager.getPlayerRank(uuid));
                sendMessage(ctx, "&cDefis incomplets! (" + completed + "/9) - Complete tes defis pour rankup.");
                break;
        }

        return complete();
    }
}
