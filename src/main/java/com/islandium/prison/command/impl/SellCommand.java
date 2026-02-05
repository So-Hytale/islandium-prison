package com.islandium.prison.command.impl;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.islandium.prison.PrisonPlugin;
import com.islandium.prison.command.base.PrisonCommand;
import com.islandium.prison.economy.SellService;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Commande /sell [type] - Vend les blocs de l'inventaire.
 * Sans argument : vend tous les blocs vendables.
 * Avec argument : vend uniquement le type spécifié (ex: /sell minecraft:cobblestone).
 */
public class SellCommand extends PrisonCommand {

    private final OptionalArg<String> blockArg;

    public SellCommand(@NotNull PrisonPlugin plugin) {
        super(plugin, "sell", "Vend les blocs de ton inventaire");
        addAliases("vendre");
        requirePermission("prison.sell");
        blockArg = withOptionalArg("block", "Type de bloc à vendre", ArgTypes.STRING);
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        if (!isPlayer(ctx)) {
            sendMessage(ctx, "&cCette commande est réservée aux joueurs!");
            return complete();
        }

        if (!hasPermission(ctx, "prison.sell")) {
            sendMessage(ctx, "&cTu n'as pas la permission!");
            return complete();
        }

        Player player = requirePlayer(ctx);
        UUID uuid = player.getUuid();

        // Argument optionnel : type de bloc à vendre
        String blockFilter = null;
        if (ctx.provided(blockArg)) {
            blockFilter = ctx.get(blockArg);
            // Ajouter le prefix minecraft: si absent
            if (blockFilter != null && !blockFilter.contains(":")) {
                blockFilter = "minecraft:" + blockFilter;
            }
        }

        // Vendre via le SellService
        SellService.SellResult result = plugin.getSellService().sellFromInventory(uuid, player, blockFilter);

        if (result.isEmpty()) {
            sendConfigMessage(ctx, "sell.empty");
            return complete();
        }

        // Afficher le résumé
        sendMessage(ctx, plugin.getConfig().getMessage("prefix") + "&a&l--- Vente ---");

        for (Map.Entry<String, Integer> entry : result.getSoldItems().entrySet()) {
            String itemName = formatBlockName(entry.getKey());
            int count = entry.getValue();
            sendMessage(ctx, "&7  " + itemName + " &8x" + count);
        }

        sendMessage(ctx, "&a&lTotal: &e" + SellService.formatMoney(result.getTotalEarned())
                + " &7(" + result.getTotalBlocksSold() + " blocs)");

        return complete();
    }

    /**
     * Formate un nom de bloc pour l'affichage (retire le prefix minecraft:).
     */
    private String formatBlockName(String blockId) {
        if (blockId.startsWith("minecraft:")) {
            blockId = blockId.substring("minecraft:".length());
        }
        // Remplacer les underscores par des espaces et capitaliser
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
