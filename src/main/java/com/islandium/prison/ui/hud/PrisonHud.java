package com.islandium.prison.ui.hud;

import com.islandium.core.api.IslandiumAPI;
import com.islandium.core.api.economy.EconomyService;
import com.islandium.core.api.location.ServerLocation;
import com.islandium.core.api.player.IslandiumPlayer;
import com.islandium.prison.PrisonPlugin;
import com.islandium.prison.mine.Mine;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.UUID;

/**
 * HUD Prison affichant les infos du joueur.
 */
public class PrisonHud extends CustomUIHud {

    private static final DecimalFormat BALANCE_FORMAT = new DecimalFormat("#,##0.00");
    private static final DecimalFormat MULTIPLIER_FORMAT = new DecimalFormat("0.##");

    private final PrisonPlugin plugin;
    private final UUID playerUuid;

    public PrisonHud(@NotNull PlayerRef playerRef, @NotNull PrisonPlugin plugin) {
        super(playerRef);
        this.plugin = plugin;
        this.playerUuid = playerRef.getUuid();
    }

    @Override
    protected void build(UICommandBuilder cmd) {
        cmd.append("Pages/Prison/PrisonHud.ui");

        // Rang
        String rank = plugin.getRankManager().getPlayerRank(playerUuid);
        cmd.set("#RankValue.Text", rank);

        // Prestige
        int prestige = plugin.getRankManager().getPlayerPrestige(playerUuid);
        cmd.set("#PrestigeValue.Text", String.valueOf(prestige));

        // Cacher le prestige si 0
        if (prestige == 0) {
            cmd.set("#PrestigeLabel.Visible", false);
            cmd.set("#PrestigeValue.Visible", false);
        } else {
            cmd.set("#PrestigeLabel.Visible", true);
            cmd.set("#PrestigeValue.Visible", true);
        }

        // Multiplicateur
        double multiplier = plugin.getRankManager().getPlayerMultiplier(playerUuid);
        cmd.set("#MultiplierValue.Text", "x" + MULTIPLIER_FORMAT.format(multiplier));

        // Mine actuelle
        String mineName = getCurrentMineName();
        cmd.set("#MineValue.Text", mineName);

        // Balance
        String balance = getFormattedBalance();
        cmd.set("#BalanceValue.Text", balance);

        // Blocs minés
        long blocksMined = plugin.getStatsManager().getBlocksMined(playerUuid);
        cmd.set("#BlocksValue.Text", formatBlockCount(blocksMined));

        // Fortune level
        int fortuneLevel = plugin.getStatsManager().getFortuneLevel(playerUuid);
        cmd.set("#FortuneLabel.Text", "F" + fortuneLevel);
        if (fortuneLevel > 0) {
            cmd.set("#FortuneLabel.Style.TextColor", "#e0a040");
        } else {
            cmd.set("#FortuneLabel.Style.TextColor", "#505050");
        }

        // Efficiency level
        int efficiencyLevel = plugin.getStatsManager().getEfficiencyLevel(playerUuid);
        cmd.set("#EfficiencyLabel.Text", "E" + efficiencyLevel);
        if (efficiencyLevel > 0) {
            cmd.set("#EfficiencyLabel.Style.TextColor", "#40a0e0");
        } else {
            cmd.set("#EfficiencyLabel.Style.TextColor", "#505050");
        }

        // Auto-sell status
        boolean hasAutoSell = plugin.getStatsManager().hasAutoSell(playerUuid);
        boolean autoSellEnabled = plugin.getStatsManager().isAutoSellEnabled(playerUuid);
        if (hasAutoSell) {
            if (autoSellEnabled) {
                cmd.set("#AutoSellLabel.Text", "AS:ON");
                cmd.set("#AutoSellLabel.Style.TextColor", "#00ff7f");
            } else {
                cmd.set("#AutoSellLabel.Text", "AS:OFF");
                cmd.set("#AutoSellLabel.Style.TextColor", "#ff6060");
            }
        } else {
            cmd.set("#AutoSellLabel.Text", "AS:---");
            cmd.set("#AutoSellLabel.Style.TextColor", "#505050");
        }
    }

    /**
     * Obtient le nom de la mine ou le joueur se trouve.
     */
    private String getCurrentMineName() {
        try {
            IslandiumPlayer islandiumPlayer = plugin.getCore().getPlayerManager()
                    .getOnlinePlayer(playerUuid)
                    .orElse(null);

            if (islandiumPlayer == null) {
                return "---";
            }

            ServerLocation loc = islandiumPlayer.getLocation();
            if (loc == null) {
                return "---";
            }

            // Chercher la mine contenant la position du joueur
            for (Mine mine : plugin.getMineManager().getAllMines()) {
                if (mine.contains(loc)) {
                    return mine.getDisplayName();
                }
            }

            return "Aucune";
        } catch (Exception e) {
            return "---";
        }
    }

    /**
     * Obtient le solde formaté du joueur.
     */
    private String getFormattedBalance() {
        try {
            IslandiumAPI api = IslandiumAPI.get();
            if (api == null) return "0";

            EconomyService eco = api.getEconomyService();
            if (eco == null) return "0";

            BigDecimal balance = eco.getBalance(playerUuid).join();
            return formatBalance(balance);
        } catch (Exception e) {
            return "0";
        }
    }

    /**
     * Formate un nombre de blocs.
     */
    private String formatBlockCount(long count) {
        if (count >= 1_000_000_000) {
            return MULTIPLIER_FORMAT.format(count / 1_000_000_000.0) + "B";
        } else if (count >= 1_000_000) {
            return MULTIPLIER_FORMAT.format(count / 1_000_000.0) + "M";
        } else if (count >= 1_000) {
            return MULTIPLIER_FORMAT.format(count / 1_000.0) + "K";
        }
        return String.valueOf(count);
    }

    /**
     * Formate un montant avec suffixes (K, M, B, T).
     */
    private String formatBalance(BigDecimal amount) {
        double value = amount.doubleValue();

        if (value >= 1_000_000_000_000.0) {
            return MULTIPLIER_FORMAT.format(value / 1_000_000_000_000.0) + "T";
        } else if (value >= 1_000_000_000.0) {
            return MULTIPLIER_FORMAT.format(value / 1_000_000_000.0) + "B";
        } else if (value >= 1_000_000.0) {
            return MULTIPLIER_FORMAT.format(value / 1_000_000.0) + "M";
        } else if (value >= 1_000.0) {
            return MULTIPLIER_FORMAT.format(value / 1_000.0) + "K";
        } else {
            return BALANCE_FORMAT.format(value);
        }
    }

}
