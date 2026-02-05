package com.islandium.prison.command.impl;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.islandium.core.api.player.IslandiumPlayer;
import com.islandium.prison.PrisonPlugin;
import com.islandium.prison.command.base.PrisonCommand;
import com.islandium.prison.mine.Mine;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Commande /mine [nom] - Téléporte à une mine.
 */
public class MineCommand extends PrisonCommand {

    private final OptionalArg<String> mineArg;

    public MineCommand(@NotNull PrisonPlugin plugin) {
        super(plugin, "mine", "Téléporte à une mine");
        addAliases("m");
        requirePermission("prison.mine");
        mineArg = withOptionalArg("mine", "Nom de la mine (ex: A, B, C...)", ArgTypes.STRING);
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        if (!isPlayer(ctx)) {
            sendMessage(ctx, "&cCette commande est réservée aux joueurs!");
            return complete();
        }

        if (!hasPermission(ctx, "prison.mine")) {
            sendMessage(ctx, "&cTu n'as pas la permission!");
            return complete();
        }

        UUID uuid = getPlayerUUID(ctx);
        String playerRank = plugin.getRankManager().getPlayerRank(uuid);

        // If no mine specified, teleport to current rank mine
        String mineName;
        if (!ctx.provided(mineArg)) {
            mineName = playerRank;
        } else {
            mineName = ctx.get(mineArg);
        }

        Mine mine = plugin.getMineManager().getMine(mineName);
        if (mine == null) {
            sendMessage(ctx, "&cMine &e" + mineName + "&c introuvable!");
            return complete();
        }

        // Check access (using UUID)
        if (!plugin.getMineManager().canAccess(uuid, mine)) {
            sendConfigMessage(ctx, "mine.no-access");
            sendMessage(ctx, "&7Tu dois être rang &e" + mine.getRequiredRank() + "&7 ou plus pour accéder à cette mine.");
            return complete();
        }

        // Check if mine has spawn
        if (!mine.hasSpawn()) {
            sendMessage(ctx, "&cLa mine &e" + mine.getDisplayName() + "&c n'a pas de point de spawn configuré!");
            return complete();
        }

        // Teleport with warmup - needs IslandiumPlayer
        IslandiumPlayer player = requireIslandiumPlayer(ctx);
        plugin.getCore().getTeleportService().teleportWithWarmup(
                player,
                mine.getSpawnPoint(),
                () -> sendConfigMessage(ctx, "mine.teleported", "mine", mine.getDisplayName())
        );

        return complete();
    }

    // Tab completion not available in base API
}
