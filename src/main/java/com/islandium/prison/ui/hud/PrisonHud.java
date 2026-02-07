package com.islandium.prison.ui.hud;

import com.islandium.core.api.IslandiumAPI;
import com.islandium.core.api.economy.EconomyService;
import com.islandium.core.api.location.ServerLocation;
import com.islandium.core.api.player.IslandiumPlayer;
import com.islandium.prison.PrisonPlugin;
import com.islandium.prison.config.PrisonConfig;
import com.islandium.prison.mine.Mine;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.UUID;

/**
 * HUD Prison — Design Premium RPG.
 * Affiche rang, progression, balance, blocs, mine, upgrades et multiplicateur.
 */
public class PrisonHud extends CustomUIHud {

    private static final DecimalFormat BALANCE_FORMAT = new DecimalFormat("#,##0.00");
    private static final DecimalFormat COMPACT_FORMAT = new DecimalFormat("0.##");
    private static final DecimalFormat MULTIPLIER_FORMAT = new DecimalFormat("0.##");

    // Largeur max de la barre de progression en pixels (240 width - 24 padding = 216)
    private static final int PROGRESS_BAR_MAX_WIDTH = 216;

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

        // ===== SECTION RANG =====
        buildRankSection(cmd);

        // ===== SECTION PROGRESSION =====
        buildProgressSection(cmd);

        // ===== SECTION ECONOMIE =====
        buildEconomySection(cmd);

        // ===== SECTION UPGRADES =====
        buildUpgradesSection(cmd);

        // ===== SECTION MULTIPLICATEUR =====
        buildMultiplierSection(cmd);
    }

    /**
     * Section Rang + Prestige.
     */
    private void buildRankSection(UICommandBuilder cmd) {
        String rankId = plugin.getRankManager().getPlayerRank(playerUuid);
        PrisonConfig.RankInfo rankInfo = plugin.getConfig().getRank(rankId);

        // Afficher le displayName du rang (ex: "Rang A")
        String rankDisplay = rankInfo != null ? rankInfo.displayName : rankId;
        cmd.set("#RankValue.Text", rankDisplay);

        // Prestige
        int prestige = plugin.getRankManager().getPlayerPrestige(playerUuid);
        if (prestige > 0) {
            cmd.set("#PrestigeValue.Text", "P" + prestige);
            cmd.set("#PrestigeValue.Visible", true);
        } else {
            cmd.set("#PrestigeValue.Text", "");
            cmd.set("#PrestigeValue.Visible", false);
        }
    }

    /**
     * Barre de progression vers le prochain rang.
     */
    private void buildProgressSection(UICommandBuilder cmd) {
        PrisonConfig.RankInfo nextRank = plugin.getRankManager().getNextRankInfo(playerUuid);

        if (nextRank == null) {
            // Rang maximum atteint
            cmd.set("#ProgressBarBg.Visible", false);
            cmd.set("#ProgressRow.Visible", false);
            cmd.set("#MaxRankLabel.Visible", true);
            cmd.set("#MaxRankLabel.Text", "Rang Maximum !");
            cmd.set("#MaxRankLabel.Anchor.Height", 16);
            return;
        }

        // Cacher le label max rank
        cmd.set("#MaxRankLabel.Visible", false);
        cmd.set("#MaxRankLabel.Anchor.Height", 0);
        cmd.set("#ProgressBarBg.Visible", true);
        cmd.set("#ProgressRow.Visible", true);

        // Calculer le prix du rankup et la progression
        BigDecimal rankupPrice = plugin.getRankManager().getRankupPrice(playerUuid, nextRank);
        BigDecimal balance = getBalance();

        double progress = 0.0;
        if (rankupPrice.compareTo(BigDecimal.ZERO) > 0) {
            progress = balance.doubleValue() / rankupPrice.doubleValue();
            progress = Math.max(0.0, Math.min(1.0, progress));
        }

        int percent = (int) (progress * 100);
        int barWidth = (int) (progress * PROGRESS_BAR_MAX_WIDTH);

        // Set la barre de progression
        cmd.set("#ProgressBarFill.Anchor.Width", barWidth);

        // Couleur dynamique de la barre
        if (percent >= 100) {
            cmd.set("#ProgressBarFill.Background.Color", "#69f0ae"); // Vert clair = pret
        } else if (percent >= 75) {
            cmd.set("#ProgressBarFill.Background.Color", "#00c853"); // Vert
        } else if (percent >= 50) {
            cmd.set("#ProgressBarFill.Background.Color", "#ffab40"); // Orange
        } else {
            cmd.set("#ProgressBarFill.Background.Color", "#448aff"); // Bleu
        }

        // Texte progression
        cmd.set("#ProgressPercent.Text", percent + "%");
        cmd.set("#NextRankInfo.Text", nextRank.id + " - " + formatCompact(rankupPrice));
    }

    /**
     * Section Balance, Blocs et Mine.
     */
    private void buildEconomySection(UICommandBuilder cmd) {
        // Balance
        BigDecimal balance = getBalance();
        cmd.set("#BalanceValue.Text", formatCompact(balance));

        // Blocs mines
        long blocksMined = plugin.getStatsManager().getBlocksMined(playerUuid);
        cmd.set("#BlocksValue.Text", formatBlockCount(blocksMined) + " blocs");

        // Mine actuelle
        String mineName = getCurrentMineName();
        cmd.set("#MineValue.Text", mineName);

        // Couleur mine : cyan si dans une mine, gris sinon
        if (mineName.equals("Aucune") || mineName.equals("---")) {
            cmd.set("#MineValue.Style.TextColor", "#8090a0");
            cmd.set("#MineIcon.Style.TextColor", "#505060");
        } else {
            cmd.set("#MineValue.Style.TextColor", "#4dd0e1");
            cmd.set("#MineIcon.Style.TextColor", "#4dd0e1");
        }
    }

    /**
     * Section Upgrades : Fortune, Efficiency, Auto-Sell.
     */
    private void buildUpgradesSection(UICommandBuilder cmd) {
        // Fortune
        int fortuneLevel = plugin.getStatsManager().getFortuneLevel(playerUuid);
        cmd.set("#FortunePips.Text", buildPips(fortuneLevel, 5));
        cmd.set("#FortuneLevel.Text", fortuneLevel + "/5");
        if (fortuneLevel > 0) {
            cmd.set("#FortunePips.Style.TextColor", "#ffab40");
            cmd.set("#FortuneLabel.Style.TextColor", "#ffab40");
            cmd.set("#FortuneLevel.Style.TextColor", "#ffab40");
        } else {
            cmd.set("#FortunePips.Style.TextColor", "#3a3a4a");
            cmd.set("#FortuneLabel.Style.TextColor", "#8090a0");
            cmd.set("#FortuneLevel.Style.TextColor", "#505060");
        }

        // Efficiency
        int efficiencyLevel = plugin.getStatsManager().getEfficiencyLevel(playerUuid);
        cmd.set("#EffPips.Text", buildPips(efficiencyLevel, 5));
        cmd.set("#EffLevel.Text", efficiencyLevel + "/5");
        if (efficiencyLevel > 0) {
            cmd.set("#EffPips.Style.TextColor", "#448aff");
            cmd.set("#EffLabel.Style.TextColor", "#448aff");
            cmd.set("#EffLevel.Style.TextColor", "#448aff");
        } else {
            cmd.set("#EffPips.Style.TextColor", "#3a3a4a");
            cmd.set("#EffLabel.Style.TextColor", "#8090a0");
            cmd.set("#EffLevel.Style.TextColor", "#505060");
        }

        // Auto-sell
        boolean hasAutoSell = plugin.getStatsManager().hasAutoSell(playerUuid);
        boolean autoSellEnabled = plugin.getStatsManager().isAutoSellEnabled(playerUuid);
        if (hasAutoSell) {
            if (autoSellEnabled) {
                cmd.set("#ASStatus.Text", "ON");
                cmd.set("#ASStatus.Style.TextColor", "#69f0ae");
                cmd.set("#ASLabel.Style.TextColor", "#69f0ae");
            } else {
                cmd.set("#ASStatus.Text", "OFF");
                cmd.set("#ASStatus.Style.TextColor", "#ff5252");
                cmd.set("#ASLabel.Style.TextColor", "#ff5252");
            }
        } else {
            cmd.set("#ASStatus.Text", "---");
            cmd.set("#ASStatus.Style.TextColor", "#505060");
            cmd.set("#ASLabel.Style.TextColor", "#8090a0");
        }
    }

    /**
     * Section Multiplicateur.
     */
    private void buildMultiplierSection(UICommandBuilder cmd) {
        double multiplier = plugin.getRankManager().getPlayerMultiplier(playerUuid);
        cmd.set("#MultValue.Text", "x" + MULTIPLIER_FORMAT.format(multiplier));
    }

    // ===========================
    // Helpers
    // ===========================

    /**
     * Construit une string de pips visuels (ex: "●●●○○").
     */
    private String buildPips(int level, int max) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < max; i++) {
            sb.append(i < level ? '\u25CF' : '\u25CB'); // filled circle or empty circle
        }
        return sb.toString();
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
     * Obtient le solde du joueur.
     */
    private BigDecimal getBalance() {
        try {
            IslandiumAPI api = IslandiumAPI.get();
            if (api == null) return BigDecimal.ZERO;

            EconomyService eco = api.getEconomyService();
            if (eco == null) return BigDecimal.ZERO;

            return eco.getBalance(playerUuid).join();
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * Formate un nombre de blocs avec suffixes.
     */
    private String formatBlockCount(long count) {
        if (count >= 1_000_000_000) {
            return COMPACT_FORMAT.format(count / 1_000_000_000.0) + "B";
        } else if (count >= 1_000_000) {
            return COMPACT_FORMAT.format(count / 1_000_000.0) + "M";
        } else if (count >= 1_000) {
            return COMPACT_FORMAT.format(count / 1_000.0) + "K";
        }
        return String.valueOf(count);
    }

    /**
     * Formate un montant avec suffixes compacts (K, M, B, T).
     */
    private String formatCompact(BigDecimal amount) {
        double value = amount.doubleValue();

        if (value >= 1_000_000_000_000.0) {
            return "$" + COMPACT_FORMAT.format(value / 1_000_000_000_000.0) + "T";
        } else if (value >= 1_000_000_000.0) {
            return "$" + COMPACT_FORMAT.format(value / 1_000_000_000.0) + "B";
        } else if (value >= 1_000_000.0) {
            return "$" + COMPACT_FORMAT.format(value / 1_000_000.0) + "M";
        } else if (value >= 1_000.0) {
            return "$" + COMPACT_FORMAT.format(value / 1_000.0) + "K";
        } else {
            return "$" + BALANCE_FORMAT.format(value);
        }
    }
}
