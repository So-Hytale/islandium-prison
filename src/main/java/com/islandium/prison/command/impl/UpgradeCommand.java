package com.islandium.prison.command.impl;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.islandium.core.api.util.NotificationType;
import com.islandium.prison.PrisonPlugin;
import com.islandium.prison.command.base.PrisonCommand;
import com.islandium.prison.economy.SellService;
import com.islandium.prison.upgrade.PickaxeUpgradeManager;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Commande /upgrade - Gère les améliorations de pioche.
 *
 * Usage:
 *   /upgrade             - Affiche tous les upgrades et niveaux actuels
 *   /upgrade fortune     - Acheter le prochain tier de fortune
 *   /upgrade efficiency  - Acheter le prochain tier d'efficacité
 *   /upgrade autosell    - Acheter ou toggle l'auto-sell
 */
public class UpgradeCommand extends PrisonCommand {

    private final OptionalArg<String> subCommandArg;

    public UpgradeCommand(@NotNull PrisonPlugin plugin) {
        super(plugin, "upgrade", "Gérer les améliorations de pioche");
        addAliases("upgrades", "amelioration", "enchant");
        requirePermission("prison.upgrade");
        subCommandArg = withOptionalArg("type", "Type d'amélioration (fortune, efficiency, autosell)", ArgTypes.STRING);
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        if (!isPlayer(ctx)) {
            sendNotification(ctx, NotificationType.ERROR, "Cette commande est reservee aux joueurs!");
            return complete();
        }

        if (!hasPermission(ctx, "prison.upgrade")) {
            sendNotification(ctx, NotificationType.ERROR, "Tu n'as pas la permission!");
            return complete();
        }

        UUID uuid = getPlayerUUID(ctx);

        if (!ctx.provided(subCommandArg)) {
            // Afficher le menu des upgrades
            showUpgradeMenu(ctx, uuid);
            return complete();
        }

        String subCommand = ctx.get(subCommandArg).toLowerCase();

        switch (subCommand) {
            case "fortune":
                handleFortune(ctx, uuid);
                break;

            case "efficiency":
            case "efficacite":
            case "speed":
                handleEfficiency(ctx, uuid);
                break;

            case "autosell":
            case "auto-sell":
            case "auto":
                handleAutoSell(ctx, uuid);
                break;

            default:
                sendNotification(ctx, NotificationType.WARNING, "Usage: /upgrade [fortune|efficiency|autosell]");
                break;
        }

        return complete();
    }

    /**
     * Affiche le menu des upgrades avec les niveaux actuels et les prix.
     */
    private void showUpgradeMenu(CommandContext ctx, UUID uuid) {
        PickaxeUpgradeManager upgradeManager = plugin.getUpgradeManager();

        int fortuneLevel = plugin.getStatsManager().getFortuneLevel(uuid);
        int efficiencyLevel = plugin.getStatsManager().getEfficiencyLevel(uuid);
        boolean hasAutoSell = plugin.getStatsManager().hasAutoSell(uuid);
        boolean autoSellEnabled = plugin.getStatsManager().isAutoSellEnabled(uuid);

        String prefix = plugin.getConfig().getMessage("prefix");

        sendMessage(ctx, prefix + "&6&l=== Améliorations de Pioche ===");
        sendMessage(ctx, "");

        // Fortune
        sendMessage(ctx, "&e⛏ Fortune &7Nv.&a" + fortuneLevel + "&7/" + PickaxeUpgradeManager.MAX_FORTUNE_LEVEL);
        sendMessage(ctx, "   " + upgradeManager.getFortuneDescription(fortuneLevel));
        BigDecimal fortunePrice = upgradeManager.getFortuneNextPrice(uuid);
        if (fortunePrice != null) {
            sendMessage(ctx, "   &7Prochain tier: &e" + SellService.formatMoney(fortunePrice) + " &8(/upgrade fortune)");
        } else {
            sendMessage(ctx, "   &a✔ Niveau maximum atteint!");
        }

        sendMessage(ctx, "");

        // Efficiency
        sendMessage(ctx, "&b⚡ Efficacité &7Nv.&a" + efficiencyLevel + "&7/" + PickaxeUpgradeManager.MAX_EFFICIENCY_LEVEL);
        sendMessage(ctx, "   " + upgradeManager.getEfficiencyDescription(efficiencyLevel));
        BigDecimal efficiencyPrice = upgradeManager.getEfficiencyNextPrice(uuid);
        if (efficiencyPrice != null) {
            sendMessage(ctx, "   &7Prochain tier: &e" + SellService.formatMoney(efficiencyPrice) + " &8(/upgrade efficiency)");
        } else {
            sendMessage(ctx, "   &a✔ Niveau maximum atteint!");
        }

        sendMessage(ctx, "");

        // Auto-Sell
        if (hasAutoSell) {
            String status = autoSellEnabled ? "&a✔ ON" : "&c✘ OFF";
            sendMessage(ctx, "&d🔄 Auto-Sell " + status);
            sendMessage(ctx, "   &7Les blocs sont vendus automatiquement au minage");
            sendMessage(ctx, "   &8(/upgrade autosell pour toggle)");
        } else {
            sendMessage(ctx, "&d🔄 Auto-Sell &7Non acheté");
            sendMessage(ctx, "   &7Vente automatique des blocs au minage");
            sendMessage(ctx, "   &7Prix: &e" + SellService.formatMoney(PickaxeUpgradeManager.getAutoSellBasePrice()) + " &8(/upgrade autosell)");
        }

        sendMessage(ctx, "");
        sendMessage(ctx, "&8Utilise /upgrade <type> pour acheter");
    }

    /**
     * Achète le prochain tier de fortune.
     */
    private void handleFortune(CommandContext ctx, UUID uuid) {
        PickaxeUpgradeManager.UpgradeResult result = plugin.getUpgradeManager().purchaseFortune(uuid);

        switch (result) {
            case SUCCESS:
                int newLevel = plugin.getStatsManager().getFortuneLevel(uuid);
                sendNotification(ctx, NotificationType.SUCCESS, "Fortune amelioree au niveau " + newLevel + "!");
                sendMessage(ctx, "   " + plugin.getUpgradeManager().getFortuneDescription(newLevel));
                break;

            case MAX_LEVEL:
                sendNotification(ctx, NotificationType.ERROR, "Tu as deja atteint le niveau maximum de Fortune!");
                break;

            case NOT_ENOUGH_MONEY:
                BigDecimal price = plugin.getUpgradeManager().getFortuneNextPrice(uuid);
                sendNotification(ctx, NotificationType.ERROR, "Pas assez d'argent! Il te faut " + SellService.formatMoney(price) + ".");
                break;
        }
    }

    /**
     * Achète le prochain tier d'efficacité.
     */
    private void handleEfficiency(CommandContext ctx, UUID uuid) {
        PickaxeUpgradeManager.UpgradeResult result = plugin.getUpgradeManager().purchaseEfficiency(uuid);

        switch (result) {
            case SUCCESS:
                int newLevel = plugin.getStatsManager().getEfficiencyLevel(uuid);
                sendNotification(ctx, NotificationType.SUCCESS, "Efficacite amelioree au niveau " + newLevel + "!");
                sendMessage(ctx, "   " + plugin.getUpgradeManager().getEfficiencyDescription(newLevel));
                break;

            case MAX_LEVEL:
                sendNotification(ctx, NotificationType.ERROR, "Tu as deja atteint le niveau maximum d'Efficacite!");
                break;

            case NOT_ENOUGH_MONEY:
                BigDecimal price = plugin.getUpgradeManager().getEfficiencyNextPrice(uuid);
                sendNotification(ctx, NotificationType.ERROR, "Pas assez d'argent! Il te faut " + SellService.formatMoney(price) + ".");
                break;
        }
    }

    /**
     * Achète ou toggle l'auto-sell.
     */
    private void handleAutoSell(CommandContext ctx, UUID uuid) {
        if (plugin.getStatsManager().hasAutoSell(uuid)) {
            // Toggle
            boolean enabled = plugin.getUpgradeManager().toggleAutoSell(uuid);
            if (enabled) {
                sendNotification(ctx, NotificationType.SUCCESS, "Auto-Sell active! Les blocs seront vendus automatiquement.");
            } else {
                sendNotification(ctx, NotificationType.INFO, "Auto-Sell desactive! Les blocs iront dans ton inventaire.");
            }
        } else {
            // Acheter
            PickaxeUpgradeManager.UpgradeResult result = plugin.getUpgradeManager().purchaseAutoSell(uuid);

            switch (result) {
                case SUCCESS:
                    sendNotification(ctx, NotificationType.SUCCESS, "Auto-Sell achete et active!");
                    sendMessage(ctx, "   &7Les blocs seront vendus automatiquement au minage.");
                    sendMessage(ctx, "   &8(/upgrade autosell pour desactiver)");
                    break;

                case NOT_ENOUGH_MONEY:
                    sendNotification(ctx, NotificationType.ERROR, "Pas assez d'argent! Il te faut "
                            + SellService.formatMoney(PickaxeUpgradeManager.getAutoSellBasePrice()) + ".");
                    break;

                case ALREADY_OWNED:
                    sendNotification(ctx, NotificationType.ERROR, "Tu possedes deja l'Auto-Sell!");
                    break;
            }
        }
    }
}
