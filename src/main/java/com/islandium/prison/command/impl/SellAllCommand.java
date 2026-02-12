package com.islandium.prison.command.impl;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.islandium.core.api.util.NotificationType;
import com.islandium.prison.PrisonPlugin;
import com.islandium.prison.command.base.PrisonCommand;
import com.islandium.prison.economy.SellService;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Commande /sellall - Vend tous les blocs vendables de l'inventaire.
 * Identique à /sell sans argument mais avec un message plus détaillé.
 */
public class SellAllCommand extends PrisonCommand {

    public SellAllCommand(@NotNull PrisonPlugin plugin) {
        super(plugin, "sellall", "Vend tous les blocs vendables");
        addAliases("sa", "vendretout");
        requirePermission("prison.sell");
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        if (!isPlayer(ctx)) {
            sendNotification(ctx, NotificationType.ERROR, "Cette commande est reservee aux joueurs!");
            return complete();
        }

        if (!hasPermission(ctx, "prison.sell")) {
            sendNotification(ctx, NotificationType.ERROR, "Tu n'as pas la permission!");
            return complete();
        }

        Player player = requirePlayer(ctx);
        UUID uuid = player.getUuid();

        // Vendre TOUT via le SellService (pas de filtre)
        SellService.SellResult result = plugin.getSellService().sellFromInventory(uuid, player, null);

        if (result.isEmpty()) {
            sendConfigMessage(ctx, "sell.empty");
            return complete();
        }

        // Afficher le résumé détaillé
        sendMessage(ctx, plugin.getConfig().getMessage("prefix") + "&a&l--- Vente Totale ---");

        for (Map.Entry<String, Integer> entry : result.getSoldItems().entrySet()) {
            String itemName = formatBlockName(entry.getKey());
            int count = entry.getValue();
            sendMessage(ctx, "&7  " + itemName + " &8x" + count);
        }

        sendMessage(ctx, "&a&lTotal: &e" + SellService.formatMoney(result.getTotalEarned())
                + " &7(" + result.getTotalBlocksSold() + " blocs vendus)");

        return complete();
    }

    /**
     * Formate un nom de bloc pour l'affichage.
     */
    private String formatBlockName(String blockId) {
        if (blockId.startsWith("minecraft:")) {
            blockId = blockId.substring("minecraft:".length());
        }
        String[] parts = blockId.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)))
                        .append(part.substring(1))
                        .append(" ");
            }
        }
        return sb.toString().trim();
    }
}
