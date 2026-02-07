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
 * HUD Prison affichant les infos du joueur.
 * Compatible MultipleHUD : uniquement cmd.set() sur .Text, .Visible, .Style.TextColor
 */
public class PrisonHud extends CustomUIHud {

    private static final DecimalFormat BALANCE_FORMAT = new DecimalFormat("#,##0.00");
    private static final DecimalFormat COMPACT_FORMAT = new DecimalFormat("0.##");
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

        buildRankSection(cmd);
        buildProgressSection(cmd);
        buildEconomySection(cmd);
        buildUpgradesSection(cmd);
        buildMultiplierSection(cmd);
    }

    private void buildRankSection(UICommandBuilder cmd) {
        String rankId = plugin.getRankManager().getPlayerRank(playerUuid);
        PrisonConfig.RankInfo rankInfo = plugin.getConfig().getRank(rankId);
        String rankDisplay = rankInfo != null ? rankInfo.displayName : rankId;
        cmd.set("#RankValue.Text", rankDisplay);

        int prestige = plugin.getRankManager().getPlayerPrestige(playerUuid);
        if (prestige > 0) {
            cmd.set("#PrestigeValue.Text", String.valueOf(prestige));
            cmd.set("#PrestigeLabel.Visible", true);
            cmd.set("#PrestigeValue.Visible", true);
        } else {
            cmd.set("#PrestigeLabel.Visible", false);
            cmd.set("#PrestigeValue.Visible", false);
        }
    }

    private void buildProgressSection(UICommandBuilder cmd) {
        PrisonConfig.RankInfo nextRank = plugin.getRankManager().getNextRankInfo(playerUuid);

        if (nextRank == null) {
            cmd.set("#ProgressInfo.Text", "Rang Maximum !");
            cmd.set("#ProgressInfo.Style.TextColor", "#69f0ae");
            return;
        }

        BigDecimal rankupPrice = plugin.getRankManager().getRankupPrice(playerUuid, nextRank);
        BigDecimal balance = getBalance();

        double progress = 0.0;
        if (rankupPrice.compareTo(BigDecimal.ZERO) > 0) {
            progress = balance.doubleValue() / rankupPrice.doubleValue();
            progress = Math.max(0.0, Math.min(1.0, progress));
        }

        int percent = (int) (progress * 100);

        // Barre texte : 10 segments
        String bar = buildProgressBar(progress, 10);
        String text = bar + " " + percent + "% \u2192 " + nextRank.displayName;
        cmd.set("#ProgressInfo.Text", text);

        // Couleur selon progression
        if (percent >= 100) {
            cmd.set("#ProgressInfo.Style.TextColor", "#69f0ae");
        } else if (percent >= 50) {
            cmd.set("#ProgressInfo.Style.TextColor", "#ffab40");
        } else {
            cmd.set("#ProgressInfo.Style.TextColor", "#b0b8c0");
        }
    }

    private void buildEconomySection(UICommandBuilder cmd) {
        BigDecimal balance = getBalance();
        cmd.set("#BalanceValue.Text", formatCompact(balance));

        long blocksMined = plugin.getStatsManager().getBlocksMined(playerUuid);
        cmd.set("#BlocksValue.Text", formatBlockCount(blocksMined));

        String mineName = getCurrentMineName();
        cmd.set("#MineValue.Text", mineName);

        if (mineName.equals("Aucune") || mineName.equals("---")) {
            cmd.set("#MineValue.Style.TextColor", "#8090a0");
        } else {
            cmd.set("#MineValue.Style.TextColor", "#4dd0e1");
        }
    }

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
                cmd.set("#ASValue.Text", "ON");
                cmd.set("#ASValue.Style.TextColor", "#69f0ae");
                cmd.set("#ASLabel.Style.TextColor", "#69f0ae");
            } else {
                cmd.set("#ASValue.Text", "OFF");
                cmd.set("#ASValue.Style.TextColor", "#ff5252");
                cmd.set("#ASLabel.Style.TextColor", "#ff5252");
            }
        } else {
            cmd.set("#ASValue.Text", "---");
            cmd.set("#ASValue.Style.TextColor", "#505060");
            cmd.set("#ASLabel.Style.TextColor", "#8090a0");
        }
    }

    private void buildMultiplierSection(UICommandBuilder cmd) {
        double multiplier = plugin.getRankManager().getPlayerMultiplier(playerUuid);
        cmd.set("#MultValue.Text", "x" + MULTIPLIER_FORMAT.format(multiplier));
    }

    // === Helpers ===

    private String buildPips(int level, int max) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < max; i++) {
            sb.append(i < level ? '\u25CF' : '\u25CB');
        }
        return sb.toString();
    }

    private String buildProgressBar(double progress, int segments) {
        int filled = (int) (progress * segments);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < segments; i++) {
            sb.append(i < filled ? '\u2593' : '\u2591');
        }
        return sb.toString();
    }

    private String getCurrentMineName() {
        try {
            IslandiumPlayer islandiumPlayer = plugin.getCore().getPlayerManager()
                    .getOnlinePlayer(playerUuid)
                    .orElse(null);

            if (islandiumPlayer == null) return "---";

            ServerLocation loc = islandiumPlayer.getLocation();
            if (loc == null) return "---";

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
