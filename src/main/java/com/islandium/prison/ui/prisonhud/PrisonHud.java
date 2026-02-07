package com.islandium.prison.ui.prisonhud;

import com.islandium.core.api.IslandiumAPI;
import com.islandium.core.api.economy.EconomyService;
import com.islandium.core.api.location.ServerLocation;
import com.islandium.core.api.player.IslandiumPlayer;
import com.islandium.prison.PrisonPlugin;
import com.islandium.prison.config.PrisonConfig;
import com.islandium.prison.mine.Mine;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;

import java.util.concurrent.CompletableFuture;
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
    private final Player player;

    public PrisonHud(@NotNull PlayerRef playerRef, @NotNull Player player, @NotNull PrisonPlugin plugin) {
        super(playerRef);
        this.plugin = plugin;
        this.playerUuid = playerRef.getUuid();
        this.player = player;
    }

    @Override
    protected void build(UICommandBuilder cmd) {
        cmd.append("Pages/Prison/PrisonHud.ui");

        // Rang (displayName au lieu du simple ID)
        String rankId = plugin.getRankManager().getPlayerRank(playerUuid);
        PrisonConfig.RankInfo rankInfo = plugin.getConfig().getRank(rankId);
        String rankDisplay = rankInfo != null ? rankInfo.displayName : rankId;
        cmd.set("#RankValue.Text", rankDisplay);

        // Prestige
        int prestige = plugin.getRankManager().getPlayerPrestige(playerUuid);
        cmd.set("#PrestigeValue.Text", String.valueOf(prestige));
        if (prestige == 0) {
            cmd.set("#PrestigeLabel.Visible", false);
            cmd.set("#PrestigeValue.Visible", false);
        } else {
            cmd.set("#PrestigeLabel.Visible", true);
            cmd.set("#PrestigeValue.Visible", true);
        }

        // Progression vers rang suivant
        try {
            PrisonConfig.RankInfo nextRank = plugin.getRankManager().getNextRankInfo(playerUuid);
            if (nextRank == null) {
                cmd.set("#ProgressInfo.Text", "Rang Maximum !");
                cmd.set("#ProgressInfo.Style.TextColor", "#69f0ae");
            } else {
                BigDecimal rankupPrice = plugin.getRankManager().getRankupPrice(playerUuid, nextRank);
                BigDecimal balance = getBalance();
                double progress = 0.0;
                if (rankupPrice.compareTo(BigDecimal.ZERO) > 0) {
                    progress = balance.doubleValue() / rankupPrice.doubleValue();
                    progress = Math.max(0.0, Math.min(1.0, progress));
                }
                int percent = (int) (progress * 100);
                String bar = buildProgressBar(progress, 10);
                cmd.set("#ProgressInfo.Text", bar + " " + percent + "% -> " + nextRank.displayName);
                if (percent >= 100) {
                    cmd.set("#ProgressInfo.Style.TextColor", "#69f0ae");
                } else if (percent >= 50) {
                    cmd.set("#ProgressInfo.Style.TextColor", "#ffab40");
                } else {
                    cmd.set("#ProgressInfo.Style.TextColor", "#b0b8c0");
                }
            }
        } catch (Exception e) {
            cmd.set("#ProgressInfo.Text", "");
        }

        // Multiplicateur
        double multiplier = plugin.getRankManager().getPlayerMultiplier(playerUuid);
        cmd.set("#MultiplierValue.Text", "x" + MULTIPLIER_FORMAT.format(multiplier));

        // Mine actuelle
        String mineName = getCurrentMineName();
        cmd.set("#MineValue.Text", mineName);
        if (mineName.equals("Aucune") || mineName.equals("---")) {
            cmd.set("#MineValue.Style.TextColor", "#8090a0");
        } else {
            cmd.set("#MineValue.Style.TextColor", "#4dd0e1");
        }

        // Balance (avec $)
        cmd.set("#BalanceValue.Text", formatCompact(getBalance()));

        // Blocs mines
        long blocksMined = plugin.getStatsManager().getBlocksMined(playerUuid);
        cmd.set("#BlocksValue.Text", formatBlockCount(blocksMined));

        // Fortune (pips + level)
        int fortuneLevel = plugin.getStatsManager().getFortuneLevel(playerUuid);
        cmd.set("#FortuneLabel.Text", "Fortune");
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

        // Efficiency (pips + level)
        int efficiencyLevel = plugin.getStatsManager().getEfficiencyLevel(playerUuid);
        cmd.set("#EffLabel.Text", "Vitesse");
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
                cmd.set("#AutoSellLabel.Text", "ON");
                cmd.set("#AutoSellLabel.Style.TextColor", "#69f0ae");
                cmd.set("#ASLabel.Style.TextColor", "#69f0ae");
            } else {
                cmd.set("#AutoSellLabel.Text", "OFF");
                cmd.set("#AutoSellLabel.Style.TextColor", "#ff5252");
                cmd.set("#ASLabel.Style.TextColor", "#ff5252");
            }
        } else {
            cmd.set("#AutoSellLabel.Text", "---");
            cmd.set("#AutoSellLabel.Style.TextColor", "#505060");
            cmd.set("#ASLabel.Style.TextColor", "#8090a0");
        }
    }

    /**
     * Rafraichit les donnees du HUD en temps reel.
     * Execute sur le thread du World pour etre thread-safe.
     */
    public void refreshData() {
        try {
            var ref = player.getReference();
            if (ref == null || !ref.isValid()) return;

            var store = ref.getStore();
            var world = store.getExternalData().getWorld();

            CompletableFuture.runAsync(() -> {
                try {
                    doRefresh();
                } catch (Exception e) {
                    // Ignore
                }
            }, world);
        } catch (Exception e) {
            // Silently ignore refresh errors
        }
    }

    private void doRefresh() {
        try {
            UICommandBuilder cmd = new UICommandBuilder();

            // Rang
            String rankId = plugin.getRankManager().getPlayerRank(playerUuid);
            PrisonConfig.RankInfo rankInfo = plugin.getConfig().getRank(rankId);
            String rankDisplay = rankInfo != null ? rankInfo.displayName : rankId;
            cmd.set("#RankValue.TextSpans", Message.raw(rankDisplay));

            // Prestige
            int prestige = plugin.getRankManager().getPlayerPrestige(playerUuid);
            cmd.set("#PrestigeValue.TextSpans", Message.raw(String.valueOf(prestige)));
            cmd.set("#PrestigeLabel.Visible", prestige > 0);
            cmd.set("#PrestigeValue.Visible", prestige > 0);

            // Progression
            try {
                PrisonConfig.RankInfo nextRank = plugin.getRankManager().getNextRankInfo(playerUuid);
                if (nextRank == null) {
                    cmd.set("#ProgressInfo.TextSpans", Message.raw("Rang Maximum !"));
                    cmd.set("#ProgressInfo.Style.TextColor", "#69f0ae");
                } else {
                    BigDecimal rankupPrice = plugin.getRankManager().getRankupPrice(playerUuid, nextRank);
                    BigDecimal balance = getBalance();
                    double progress = 0.0;
                    if (rankupPrice.compareTo(BigDecimal.ZERO) > 0) {
                        progress = balance.doubleValue() / rankupPrice.doubleValue();
                        progress = Math.max(0.0, Math.min(1.0, progress));
                    }
                    int percent = (int) (progress * 100);
                    String bar = buildProgressBar(progress, 10);
                    cmd.set("#ProgressInfo.TextSpans", Message.raw(bar + " " + percent + "% -> " + nextRank.displayName));
                    if (percent >= 100) {
                        cmd.set("#ProgressInfo.Style.TextColor", "#69f0ae");
                    } else if (percent >= 50) {
                        cmd.set("#ProgressInfo.Style.TextColor", "#ffab40");
                    } else {
                        cmd.set("#ProgressInfo.Style.TextColor", "#b0b8c0");
                    }
                }
            } catch (Exception e) {
                cmd.set("#ProgressInfo.TextSpans", Message.raw(""));
            }

            // Balance
            cmd.set("#BalanceValue.TextSpans", Message.raw(formatCompact(getBalance())));

            // Blocs
            long blocksMined = plugin.getStatsManager().getBlocksMined(playerUuid);
            cmd.set("#BlocksValue.TextSpans", Message.raw(formatBlockCount(blocksMined)));

            // Mine
            String mineName = getCurrentMineName();
            cmd.set("#MineValue.TextSpans", Message.raw(mineName));
            if (mineName.equals("Aucune") || mineName.equals("---")) {
                cmd.set("#MineValue.Style.TextColor", "#8090a0");
            } else {
                cmd.set("#MineValue.Style.TextColor", "#4dd0e1");
            }

            // Multiplicateur
            double multiplier = plugin.getRankManager().getPlayerMultiplier(playerUuid);
            cmd.set("#MultiplierValue.TextSpans", Message.raw("x" + MULTIPLIER_FORMAT.format(multiplier)));

            // Fortune
            int fortuneLevel = plugin.getStatsManager().getFortuneLevel(playerUuid);
            cmd.set("#FortunePips.TextSpans", Message.raw(buildPips(fortuneLevel, 5)));
            cmd.set("#FortuneLevel.TextSpans", Message.raw(fortuneLevel + "/5"));
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
            cmd.set("#EffPips.TextSpans", Message.raw(buildPips(efficiencyLevel, 5)));
            cmd.set("#EffLevel.TextSpans", Message.raw(efficiencyLevel + "/5"));
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
                    cmd.set("#AutoSellLabel.TextSpans", Message.raw("ON"));
                    cmd.set("#AutoSellLabel.Style.TextColor", "#69f0ae");
                    cmd.set("#ASLabel.Style.TextColor", "#69f0ae");
                } else {
                    cmd.set("#AutoSellLabel.TextSpans", Message.raw("OFF"));
                    cmd.set("#AutoSellLabel.Style.TextColor", "#ff5252");
                    cmd.set("#ASLabel.Style.TextColor", "#ff5252");
                }
            } else {
                cmd.set("#AutoSellLabel.TextSpans", Message.raw("---"));
                cmd.set("#AutoSellLabel.Style.TextColor", "#505060");
                cmd.set("#ASLabel.Style.TextColor", "#8090a0");
            }

            update(false, cmd);
        } catch (Exception e) {
            // Ignore
        }
    }

    // === Helpers ===

    private String buildPips(int level, int max) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < max; i++) {
            sb.append(i < level ? '#' : '-');
        }
        return sb.toString();
    }

    private String buildProgressBar(double progress, int segments) {
        int filled = (int) (progress * segments);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < segments; i++) {
            sb.append(i < filled ? '|' : '.');
        }
        sb.append("]");
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
            return MULTIPLIER_FORMAT.format(count / 1_000_000_000.0) + "B";
        } else if (count >= 1_000_000) {
            return MULTIPLIER_FORMAT.format(count / 1_000_000.0) + "M";
        } else if (count >= 1_000) {
            return MULTIPLIER_FORMAT.format(count / 1_000.0) + "K";
        }
        return String.valueOf(count);
    }

    private String formatCompact(BigDecimal amount) {
        double value = amount.doubleValue();
        if (value >= 1_000_000_000_000.0) {
            return "$" + MULTIPLIER_FORMAT.format(value / 1_000_000_000_000.0) + "T";
        } else if (value >= 1_000_000_000.0) {
            return "$" + MULTIPLIER_FORMAT.format(value / 1_000_000_000.0) + "B";
        } else if (value >= 1_000_000.0) {
            return "$" + MULTIPLIER_FORMAT.format(value / 1_000_000.0) + "M";
        } else if (value >= 1_000.0) {
            return "$" + MULTIPLIER_FORMAT.format(value / 1_000.0) + "K";
        } else {
            return "$" + BALANCE_FORMAT.format(value);
        }
    }

    /**
     * @deprecated Kept for backward compatibility, use formatCompact instead.
     */
    private String getFormattedBalance() {
        return formatCompact(getBalance());
    }
}
