package com.islandium.prison.command.impl;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.islandium.core.api.player.IslandiumPlayer;
import com.islandium.prison.PrisonPlugin;
import com.islandium.prison.cell.Cell;
import com.islandium.prison.cell.CellManager;
import com.islandium.prison.command.base.PrisonCommand;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Commande /cell [action] - Gestion des cellules.
 * Actions: tp, buy, info
 */
public class CellCommand extends PrisonCommand {

    private final OptionalArg<String> actionArg;

    public CellCommand(@NotNull PrisonPlugin plugin) {
        super(plugin, "cell", "Gère ta cellule de prison");
        addAliases("cellule", "c");
        requirePermission("prison.cell");
        actionArg = withOptionalArg("action", "Action (tp, buy, info)", ArgTypes.STRING);
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        if (!isPlayer(ctx)) {
            sendMessage(ctx, "&cCette commande est réservée aux joueurs!");
            return complete();
        }

        if (!hasPermission(ctx, "prison.cell")) {
            sendMessage(ctx, "&cTu n'as pas la permission!");
            return complete();
        }

        UUID uuid = getPlayerUUID(ctx);
        String action = ctx.provided(actionArg) ? ctx.get(actionArg).toLowerCase() : "tp";

        switch (action) {
            case "tp":
            case "teleport":
            case "home":
                return handleTeleport(ctx);
            case "buy":
            case "acheter":
                return handleBuy(ctx);
            case "info":
            case "i":
                return handleInfo(ctx, uuid);
            default:
                showHelp(ctx);
                return complete();
        }
    }

    private CompletableFuture<Void> handleTeleport(CommandContext ctx) {
        // Need IslandiumPlayer for teleportation
        IslandiumPlayer player = requireIslandiumPlayer(ctx);
        plugin.getCellManager().teleportToCell(player);
        return complete();
    }

    private CompletableFuture<Void> handleBuy(CommandContext ctx) {
        // Need IslandiumPlayer for player name
        IslandiumPlayer player = requireIslandiumPlayer(ctx);
        CellManager.PurchaseResult result = plugin.getCellManager().purchaseCell(player.getUniqueId(), player.getName());

        switch (result) {
            case SUCCESS:
                sendConfigMessage(ctx, "cell.purchased", "price", plugin.getConfig().getDefaultCellPrice().toString());
                break;
            case NOT_ENOUGH_MONEY:
                sendConfigMessage(ctx, "cell.not-enough-money");
                sendMessage(ctx, "&7Prix: &e" + plugin.getConfig().getDefaultCellPrice() + "$");
                break;
            case ALREADY_HAS_CELL:
                sendConfigMessage(ctx, "cell.already-owned");
                break;
            case NO_CELLS_AVAILABLE:
                sendMessage(ctx, "&cAucune cellule disponible pour le moment!");
                break;
            case CELL_NOT_AVAILABLE:
                sendMessage(ctx, "&cCette cellule n'est pas disponible!");
                break;
        }

        return complete();
    }

    private CompletableFuture<Void> handleInfo(CommandContext ctx, UUID uuid) {
        Cell cell = plugin.getCellManager().getPlayerCell(uuid);

        sendMessage(ctx, "&6&l=== Ta Cellule ===");
        sendMessage(ctx, "");

        if (cell == null) {
            sendMessage(ctx, "&7Tu ne possèdes pas de cellule.");
            sendMessage(ctx, "&7Utilise &e/cell buy &7pour en acheter une!");
            sendMessage(ctx, "&7Prix: &e" + plugin.getConfig().getDefaultCellPrice() + "$");
        } else {
            sendMessage(ctx, "&7ID: &e" + cell.getId());
            sendMessage(ctx, "&7Verrouillée: " + (cell.isLocked() ? "&cOui" : "&aNon"));

            if (cell.getExpirationTime() > 0) {
                long remaining = cell.getExpirationTime() - System.currentTimeMillis();
                if (remaining > 0) {
                    long days = remaining / (24 * 60 * 60 * 1000);
                    sendMessage(ctx, "&7Expire dans: &e" + days + " jours");
                } else {
                    sendMessage(ctx, "&cCellule expirée!");
                }
            } else {
                sendMessage(ctx, "&7Durée: &aPermanente");
            }

            sendMessage(ctx, "");
            sendMessage(ctx, "&7Utilise &e/cell tp &7pour t'y téléporter!");
        }

        return complete();
    }

    private void showHelp(CommandContext ctx) {
        sendMessage(ctx, "&6&l=== Aide Cellule ===");
        sendMessage(ctx, "");
        sendMessage(ctx, "&e/cell &7ou &e/cell tp &8- &fTéléporte à ta cellule");
        sendMessage(ctx, "&e/cell buy &8- &fAchète une cellule");
        sendMessage(ctx, "&e/cell info &8- &fAffiche les infos de ta cellule");
    }

    // Tab completion not available in base API
}
