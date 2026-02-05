package com.islandium.prison.command.impl;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.islandium.prison.PrisonPlugin;
import com.islandium.prison.command.base.PrisonCommand;
import com.islandium.prison.mine.Mine;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Commande /mines - Affiche la liste des mines.
 */
public class MinesCommand extends PrisonCommand {

    public MinesCommand(@NotNull PrisonPlugin plugin) {
        super(plugin, "mines", "Affiche la liste des mines");
        addAliases("minelist");
        requirePermission("prison.mines");
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        if (!hasPermission(ctx, "prison.mines")) {
            sendMessage(ctx, "&cTu n'as pas la permission!");
            return complete();
        }

        Collection<Mine> mines = plugin.getMineManager().getAllMines();
        String currentRank = "A";

        if (isPlayer(ctx)) {
            // Utiliser directement l'UUID sans passer par IslandiumPlayer
            UUID playerUUID = getPlayerUUID(ctx);
            currentRank = plugin.getRankManager().getPlayerRank(playerUUID);
        }

        sendMessage(ctx, "&6&l=== Mines Prison ===");
        sendMessage(ctx, "");

        for (Mine mine : mines) {
            boolean canAccess = plugin.getMineManager().canAccessWithRank(currentRank, mine.getRequiredRank());
            boolean isConfigured = mine.isConfigured();

            String accessIcon = canAccess ? "&a✓" : "&c✗";
            String statusColor = canAccess ? "&a" : "&7";

            String blockInfo = "";
            if (isConfigured) {
                double remaining = mine.getRemainingPercentage();
                String percentColor = remaining > 50 ? "&a" : (remaining > 20 ? "&e" : "&c");
                blockInfo = String.format(" &8[%s%.0f%%&8]", percentColor, remaining);
            } else {
                blockInfo = " &8[&cNon configurée&8]";
            }

            sendMessage(ctx, String.format("%s %s%s &8- &f%s &7(Rang: &e%s&7)%s",
                    accessIcon,
                    statusColor, mine.getId(),
                    mine.getDisplayName(),
                    mine.getRequiredRank(),
                    blockInfo
            ));
        }

        sendMessage(ctx, "");
        sendMessage(ctx, "&7Utilise &e/mine <nom> &7pour te téléporter!");

        return complete();
    }
}
