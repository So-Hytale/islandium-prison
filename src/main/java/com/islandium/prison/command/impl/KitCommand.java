package com.islandium.prison.command.impl;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.islandium.prison.PrisonPlugin;
import com.islandium.prison.command.base.PrisonCommand;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Commande /kit - Ouvre l'interface des kits.
 */
public class KitCommand extends PrisonCommand {

    public KitCommand(@NotNull PrisonPlugin plugin) {
        super(plugin, "kit", "Ouvre le menu des kits");
        addAliases("kits");
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        if (!isPlayer(ctx)) {
            sendMessage(ctx, "&cCette commande est reservee aux joueurs!");
            return complete();
        }

        if (!hasPermission(ctx, "prison.kit")) {
            sendMessage(ctx, "&cTu n'as pas la permission!");
            return complete();
        }

        Player player = getPlayer(ctx);
        if (player == null) {
            sendMessage(ctx, "&cErreur: Impossible de recuperer le joueur!");
            return complete();
        }

        plugin.getUIManager().openKitPage(player);
        return complete();
    }
}
