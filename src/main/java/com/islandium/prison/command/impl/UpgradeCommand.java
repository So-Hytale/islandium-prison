package com.islandium.prison.command.impl;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
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
            sendMessage(ctx, "&cCette commande est réservée aux joueurs!");
            return complete();
        }

        if (!hasPermission(ctx, "prison.upgrade")) {
            sendMessage(ctx, "&cTu n'as pas la permission!");
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
                sendMessage(ctx, "&cUsage: /upgrade [fortune|efficiency|autosell]");
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
                sendMessage(ctx, plugin.getConfig().getMessage("prefix")
                        + "&a⛏ Fortune améliorée au niveau &e" + newLevel + "&a!");
                sendMessage(ctx, "   " + plugin.getUpgradeManager().getFortuneDescription(newLevel));
                plugin.getUIManager().refreshHud(requirePlayer(ctx));
                break;

            case MAX_LEVEL:
                sendMessage(ctx, plugin.getConfig().getMessage("prefix")
                        + "&cTu as déjà atteint le niveau maximum de Fortune!");
                break;

            case NOT_ENOUGH_MONEY:
                BigDecimal price = plugin.getUpgradeManager().getFortuneNextPrice(uuid);
                sendMessage(ctx, plugin.getConfig().getMessage("prefix")
                        + "&cPas assez d'argent! Il te faut &e" + SellService.formatMoney(price) + "&c.");
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
                sendMessage(ctx, plugin.getConfig().getMessage("prefix")
                        + "&a⚡ Efficacité améliorée au niveau &e" + newLevel + "&a!");
                sendMessage(ctx, "   " + plugin.getUpgradeManager().getEfficiencyDescription(newLevel));
                plugin.getUIManager().refreshHud(requirePlayer(ctx));
                break;

            case MAX_LEVEL:
                sendMessage(ctx, plugin.getConfig().getMessage("prefix")
                        + "&cTu as déjà atteint le niveau maximum d'Efficacité!");
                break;

            case NOT_ENOUGH_MONEY:
                BigDecimal price = plugin.getUpgradeManager().getEfficiencyNextPrice(uuid);
                sendMessage(ctx, plugin.getConfig().getMessage("prefix")
                        + "&cPas assez d'argent! Il te faut &e" + SellService.formatMoney(price) + "&c.");
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
                sendMessage(ctx, plugin.getConfig().getMessage("prefix")
                        + "&a🔄 Auto-Sell &aactivé! &7Les blocs seront vendus automatiquement.");
            } else {
                sendMessage(ctx, plugin.getConfig().getMessage("prefix")
                        + "&c🔄 Auto-Sell &cdésactivé! &7Les blocs iront dans ton inventaire.");
            }
            plugin.getUIManager().refreshHud(requirePlayer(ctx));
        } else {
            // Acheter
            PickaxeUpgradeManager.UpgradeResult result = plugin.getUpgradeManager().purchaseAutoSell(uuid);

            switch (result) {
                case SUCCESS:
                    sendMessage(ctx, plugin.getConfig().getMessage("prefix")
                            + "&a🔄 Auto-Sell acheté et activé!");
                    sendMessage(ctx, "   &7Les blocs seront vendus automatiquement au minage.");
                    sendMessage(ctx, "   &8(/upgrade autosell pour désactiver)");
                    plugin.getUIManager().refreshHud(requirePlayer(ctx));
                    break;

                case NOT_ENOUGH_MONEY:
                    sendMessage(ctx, plugin.getConfig().getMessage("prefix")
                            + "&cPas assez d'argent! Il te faut &e"
                            + SellService.formatMoney(PickaxeUpgradeManager.getAutoSellBasePrice()) + "&c.");
                    break;

                case ALREADY_OWNED:
                    sendMessage(ctx, plugin.getConfig().getMessage("prefix")
                            + "&cTu possèdes déjà l'Auto-Sell!");
                    break;
            }
        }
    }
}
