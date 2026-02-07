package com.islandium.prison.ui.pages;

import com.islandium.core.api.IslandiumAPI;
import com.islandium.core.api.economy.EconomyService;
import com.islandium.core.api.player.IslandiumPlayer;
import com.islandium.prison.PrisonPlugin;
import com.islandium.prison.config.PrisonConfig;
import com.islandium.prison.economy.SellService;
import com.islandium.prison.mine.Mine;
import com.islandium.prison.rank.PrisonRankManager;
import com.islandium.prison.stats.PlayerStatsManager;
import com.islandium.prison.upgrade.PickaxeUpgradeManager;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.math.BigDecimal;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Page menu principal Prison - Hub central pour les joueurs.
 * Accessible depuis /menu -> bouton PRISON.
 */
public class PrisonMenuPage extends InteractiveCustomUIPage<PrisonMenuPage.PageData> {

    private final PrisonPlugin plugin;
    private final PlayerRef playerRef;
    private String currentPage = "hub";

    public PrisonMenuPage(@Nonnull PlayerRef playerRef, PrisonPlugin plugin) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageData.CODEC);
        this.plugin = plugin;
        this.playerRef = playerRef;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder event, @Nonnull Store<EntityStore> store) {
        // Container principal
        cmd.appendInline("",
            "Group #PrisonMenuRoot { " +
            "  Anchor: (Width: 900, Height: 560, HCenter: 0, VCenter: 0); " +
            "  Background: (Color: #0d1520); " +
            "  LayoutMode: Top; " +
            // Header
            "  Group #Header { " +
            "    Anchor: (Height: 50); " +
            "    Background: (Color: #16212f); " +
            "    LayoutMode: Left; " +
            "    Padding: (Horizontal: 20); " +
            "    Label #HeaderTitle { " +
            "      FlexWeight: 1; " +
            "      Text: \"PRISON\"; " +
            "      Style: (FontSize: 18, TextColor: #ff9800, RenderBold: true, RenderUppercase: true, VerticalAlignment: Center); " +
            "    } " +
            "    TextButton #BackBtn { " +
            "      Anchor: (Width: 80, Height: 32); " +
            "      Visible: false; " +
            "      Style: TextButtonStyle(Default: (LabelStyle: (FontSize: 12, TextColor: #96a9be, VerticalAlignment: Center)), Hovered: (Background: #1a2836, LabelStyle: (FontSize: 12, TextColor: #ffffff, VerticalAlignment: Center))); " +
            "    } " +
            "  } " +
            // Content area
            "  Group #Content { " +
            "    FlexWeight: 1; " +
            "    LayoutMode: TopScrolling; " +
            "    Padding: (Full: 20); " +
            "  } " +
            "}");

        cmd.set("#BackBtn.Text", "< Retour");
        event.addEventBinding(CustomUIEventBindingType.Activating, "#BackBtn", EventData.of("Action", "back"), false);

        buildHub(cmd, event);
    }

    // =========================================
    // HUB - Page d'accueil avec les 6 boutons
    // =========================================

    private void buildHub(UICommandBuilder cmd, UIEventBuilder event) {
        cmd.clear("#Content");
        cmd.set("#HeaderTitle.Text", "PRISON");
        cmd.set("#BackBtn.Visible", false);

        // 6 boutons
        String[][] buttons = {
            {"mines",      "MINES",       "Teleportation vers les mines",      "#4fc3f7"},
            {"rang",       "RANG",        "Progression et rankup",             "#ffd700"},
            {"upgrades",   "UPGRADES",    "Ameliorations de pioche",           "#ab47bc"},
            {"vendre",     "VENDRE",      "Vendre les blocs de l'inventaire",  "#66bb6a"},
            {"classement", "CLASSEMENT",  "Top joueurs",                       "#ef5350"},
            {"cellule",    "CELLULE",     "Gestion de cellule",                "#8d6e63"},
        };

        for (int i = 0; i < buttons.length; i++) {
            String id = buttons[i][0];
            String label = buttons[i][1];
            String desc = buttons[i][2];
            String color = buttons[i][3];
            String selector = "#Content[" + i + "]";

            cmd.appendInline("#Content",
                "Group { Anchor: (Height: 78); " +
                "  Button #CardBtn { " +
                "    Anchor: (Height: 70); " +
                "    Background: (Color: #151d28); " +
                "    Padding: (Horizontal: 15); " +
                "    Label #CardName { " +
                "      Anchor: (Height: 35, Top: 8); " +
                "      Style: (FontSize: 16, RenderBold: true, RenderUppercase: true, VerticalAlignment: Center); " +
                "    } " +
                "    Label #CardDesc { " +
                "      Anchor: (Height: 20, Top: 43); " +
                "      Style: (FontSize: 12, TextColor: #7c8b99); " +
                "    } " +
                "  } " +
                "}");

            cmd.set(selector + " #CardBtn #CardName.Text", label);
            cmd.set(selector + " #CardBtn #CardName.Style.TextColor", color);
            cmd.set(selector + " #CardBtn #CardDesc.Text", desc);

            event.addEventBinding(CustomUIEventBindingType.Activating,
                selector + " #CardBtn",
                EventData.of("Navigate", id),
                false);
        }
    }

    // =========================================
    // MINES
    // =========================================

    private void buildMinesPage(UICommandBuilder cmd, UIEventBuilder event) {
        cmd.clear("#Content");
        cmd.set("#HeaderTitle.Text", "MINES");
        cmd.set("#BackBtn.Visible", true);

        UUID uuid = playerRef.getUuid();
        String playerRank = plugin.getRankManager().getPlayerRank(uuid);
        Collection<Mine> mines = plugin.getMineManager().getAllMines();

        if (mines.isEmpty()) {
            cmd.appendInline("#Content",
                "Label { Anchor: (Height: 40); Text: \"Aucune mine disponible.\"; " +
                "Style: (FontSize: 14, TextColor: #808080); }");
            return;
        }

        // Trier les mines par rang requis
        List<Mine> sortedMines = mines.stream()
            .sorted((a, b) -> {
                int ia = plugin.getRankManager().getRankIndex(a.getRequiredRank());
                int ib = plugin.getRankManager().getRankIndex(b.getRequiredRank());
                return Integer.compare(ia, ib);
            })
            .collect(Collectors.toList());

        int index = 0;
        for (Mine mine : sortedMines) {
            boolean canAccess = plugin.getMineManager().canAccess(uuid, mine);
            String selector = "#Content[" + index + "]";
            String bgColor = canAccess ? "#1a2a1a" : "#2a1a1a";
            String statusColor = canAccess ? "#66bb6a" : "#ef5350";
            String statusText = canAccess ? "Accessible" : "Rang " + mine.getRequiredRank() + " requis";

            cmd.appendInline("#Content",
                "Group { Anchor: (Height: 55); LayoutMode: Left; Background: (Color: " + bgColor + "); Padding: (Horizontal: 15, Vertical: 5); " +
                "  Group { FlexWeight: 1; LayoutMode: Top; " +
                "    Label #MineName { Anchor: (Height: 25); Style: (FontSize: 15, TextColor: #ffd700, RenderBold: true, VerticalAlignment: Center); } " +
                "    Label #MineStatus { Anchor: (Height: 18); Style: (FontSize: 11, TextColor: " + statusColor + "); } " +
                "  } " +
                (canAccess && mine.hasSpawn() ?
                "  TextButton #TpBtn { Anchor: (Width: 80, Height: 32); " +
                "    Style: TextButtonStyle(Default: (Background: #2a5f2a, LabelStyle: (FontSize: 12, TextColor: #ffffff, VerticalAlignment: Center)), " +
                "    Hovered: (Background: #3a7f3a, LabelStyle: (FontSize: 12, TextColor: #ffffff, VerticalAlignment: Center))); } "
                : "") +
                "}");

            cmd.set(selector + " #MineName.Text", "Mine " + mine.getDisplayName());
            cmd.set(selector + " #MineStatus.Text", statusText);

            if (canAccess && mine.hasSpawn()) {
                cmd.set(selector + " #TpBtn.Text", "TP");
                event.addEventBinding(CustomUIEventBindingType.Activating,
                    selector + " #TpBtn",
                    EventData.of("TpMine", mine.getId()),
                    false);
            }

            index++;
        }
    }

    // =========================================
    // RANG
    // =========================================

    private void buildRangPage(UICommandBuilder cmd, UIEventBuilder event) {
        cmd.clear("#Content");
        cmd.set("#HeaderTitle.Text", "RANG");
        cmd.set("#BackBtn.Visible", true);

        UUID uuid = playerRef.getUuid();
        String currentRank = plugin.getRankManager().getPlayerRank(uuid);
        int prestige = plugin.getRankManager().getPlayerPrestige(uuid);
        double multiplier = plugin.getRankManager().getPlayerMultiplier(uuid);
        PrisonConfig.RankInfo nextRank = plugin.getRankManager().getNextRankInfo(uuid);

        // Rang actuel
        cmd.appendInline("#Content",
            "Group { Anchor: (Height: 80); Background: (Color: #151d28); Padding: (Full: 15); LayoutMode: Top; " +
            "  Label { Anchor: (Height: 25); Text: \"Rang actuel\"; Style: (FontSize: 12, TextColor: #7c8b99); } " +
            "  Label #CurrentRank { Anchor: (Height: 35); Style: (FontSize: 28, TextColor: #ffd700, RenderBold: true); } " +
            "}");
        cmd.set("#CurrentRank.Text", currentRank + (prestige > 0 ? "  [P" + prestige + "]" : ""));

        // Multiplicateur
        cmd.appendInline("#Content",
            "Group { Anchor: (Height: 40, Top: 8); Background: (Color: #151d28); Padding: (Horizontal: 15); LayoutMode: Left; " +
            "  Label { FlexWeight: 1; Text: \"Multiplicateur de gains\"; Style: (FontSize: 13, TextColor: #96a9be, VerticalAlignment: Center); } " +
            "  Label #Multiplier { Anchor: (Width: 100); Style: (FontSize: 15, TextColor: #00e5ff, RenderBold: true, VerticalAlignment: Center); } " +
            "}");
        cmd.set("#Multiplier.Text", String.format("x%.2f", multiplier));

        // Prochain rang
        if (nextRank != null) {
            BigDecimal price = plugin.getRankManager().getRankupPrice(uuid, nextRank);

            cmd.appendInline("#Content",
                "Group { Anchor: (Height: 60, Top: 8); Background: (Color: #151d28); Padding: (Full: 15); LayoutMode: Top; " +
                "  Label { Anchor: (Height: 20); Style: (FontSize: 12, TextColor: #7c8b99); } " +
                "  Label #NextRankPrice { Anchor: (Height: 25); Style: (FontSize: 14, TextColor: #ffffff); } " +
                "}");
            cmd.set("#Content[2] Label.Text", "Prochain rang: " + nextRank.displayName);
            cmd.set("#NextRankPrice.Text", "Prix: " + SellService.formatMoney(price));

            // Boutons
            cmd.appendInline("#Content",
                "Group { Anchor: (Height: 50, Top: 10); LayoutMode: Left; " +
                "  TextButton #RankupBtn { Anchor: (Width: 180, Height: 40); " +
                "    Style: TextButtonStyle(Default: (Background: #2a5f2a, LabelStyle: (FontSize: 14, TextColor: #ffffff, RenderBold: true, VerticalAlignment: Center)), " +
                "    Hovered: (Background: #3a7f3a, LabelStyle: (FontSize: 14, TextColor: #ffffff, RenderBold: true, VerticalAlignment: Center))); } " +
                "  TextButton #MaxRankupBtn { Anchor: (Width: 180, Left: 10, Height: 40); " +
                "    Style: TextButtonStyle(Default: (Background: #1a3a5f, LabelStyle: (FontSize: 14, TextColor: #ffffff, RenderBold: true, VerticalAlignment: Center)), " +
                "    Hovered: (Background: #2a4a7f, LabelStyle: (FontSize: 14, TextColor: #ffffff, RenderBold: true, VerticalAlignment: Center))); } " +
                "}");

            cmd.set("#RankupBtn.Text", "RANKUP");
            cmd.set("#MaxRankupBtn.Text", "MAX RANKUP");

            event.addEventBinding(CustomUIEventBindingType.Activating, "#RankupBtn", EventData.of("Action", "rankup"), false);
            event.addEventBinding(CustomUIEventBindingType.Activating, "#MaxRankupBtn", EventData.of("Action", "maxrankup"), false);
        } else if (plugin.getRankManager().canPrestige(uuid)) {
            cmd.appendInline("#Content",
                "Group { Anchor: (Height: 60, Top: 10); LayoutMode: Left; " +
                "  TextButton #PrestigeBtn { Anchor: (Width: 200, Height: 45); " +
                "    Style: TextButtonStyle(Default: (Background: #5f2a5f, LabelStyle: (FontSize: 15, TextColor: #ff80ff, RenderBold: true, VerticalAlignment: Center)), " +
                "    Hovered: (Background: #7f3a7f, LabelStyle: (FontSize: 15, TextColor: #ffffff, RenderBold: true, VerticalAlignment: Center))); } " +
                "}");
            cmd.set("#PrestigeBtn.Text", "PRESTIGE");
            event.addEventBinding(CustomUIEventBindingType.Activating, "#PrestigeBtn", EventData.of("Action", "prestige"), false);
        } else {
            cmd.appendInline("#Content",
                "Label { Anchor: (Height: 30, Top: 10); Text: \"Rang maximum atteint!\"; " +
                "Style: (FontSize: 14, TextColor: #ffd700, RenderBold: true); }");
        }
    }

    // =========================================
    // UPGRADES
    // =========================================

    private void buildUpgradesPage(UICommandBuilder cmd, UIEventBuilder event) {
        cmd.clear("#Content");
        cmd.set("#HeaderTitle.Text", "UPGRADES");
        cmd.set("#BackBtn.Visible", true);

        UUID uuid = playerRef.getUuid();
        PickaxeUpgradeManager upgradeManager = plugin.getUpgradeManager();
        PlayerStatsManager statsManager = plugin.getStatsManager();

        int fortuneLevel = statsManager.getFortuneLevel(uuid);
        int efficiencyLevel = statsManager.getEfficiencyLevel(uuid);
        boolean hasAutoSell = statsManager.hasAutoSell(uuid);
        boolean autoSellEnabled = statsManager.isAutoSellEnabled(uuid);

        // Fortune
        BigDecimal fortunePrice = upgradeManager.getFortuneNextPrice(uuid);
        String fortunePips = buildPips(fortuneLevel, PickaxeUpgradeManager.MAX_FORTUNE_LEVEL);
        String fortuneDesc = fortuneLevel > 0 ? (fortuneLevel * 10) + "% chance 2x drops" : "Pas d'enchantement";

        cmd.appendInline("#Content",
            "Group { Anchor: (Height: 90); Background: (Color: #151d28); Padding: (Full: 12); LayoutMode: Top; " +
            "  Group { Anchor: (Height: 25); LayoutMode: Left; " +
            "    Label { FlexWeight: 1; Text: \"Fortune\"; Style: (FontSize: 15, TextColor: #ffd700, RenderBold: true, VerticalAlignment: Center); } " +
            "    Label #FortunePips { Anchor: (Width: 150); Style: (FontSize: 13, TextColor: #66bb6a, VerticalAlignment: Center); } " +
            "  } " +
            "  Label #FortuneDesc { Anchor: (Height: 20); Style: (FontSize: 12, TextColor: #96a9be); } " +
            "  Group { Anchor: (Height: 30, Top: 3); LayoutMode: Left; " +
            "    Label #FortunePrice { FlexWeight: 1; Style: (FontSize: 12, TextColor: #7c8b99, VerticalAlignment: Center); } " +
            (fortunePrice != null ?
            "    TextButton #BuyFortune { Anchor: (Width: 90, Height: 28); " +
            "      Style: TextButtonStyle(Default: (Background: #2a5f2a, LabelStyle: (FontSize: 11, TextColor: #ffffff, VerticalAlignment: Center)), " +
            "      Hovered: (Background: #3a7f3a, LabelStyle: (FontSize: 11, TextColor: #ffffff, VerticalAlignment: Center))); } "
            : "") +
            "  } " +
            "}");

        cmd.set("#FortunePips.Text", fortunePips);
        cmd.set("#FortuneDesc.Text", fortuneDesc);
        cmd.set("#FortunePrice.Text", fortunePrice != null ? "Prix: " + SellService.formatMoney(fortunePrice) : "Niveau MAX");
        if (fortunePrice != null) {
            cmd.set("#BuyFortune.Text", "ACHETER");
            event.addEventBinding(CustomUIEventBindingType.Activating, "#BuyFortune", EventData.of("Action", "buyFortune"), false);
        }

        // Efficacite
        BigDecimal efficiencyPrice = upgradeManager.getEfficiencyNextPrice(uuid);
        String efficiencyPips = buildPips(efficiencyLevel, PickaxeUpgradeManager.MAX_EFFICIENCY_LEVEL);
        String efficiencyDesc = efficiencyLevel > 0 ? "Vitesse +" + (efficiencyLevel * 20) + "%" : "Pas d'enchantement";

        cmd.appendInline("#Content",
            "Group { Anchor: (Height: 90, Top: 8); Background: (Color: #151d28); Padding: (Full: 12); LayoutMode: Top; " +
            "  Group { Anchor: (Height: 25); LayoutMode: Left; " +
            "    Label { FlexWeight: 1; Text: \"Efficacite\"; Style: (FontSize: 15, TextColor: #4fc3f7, RenderBold: true, VerticalAlignment: Center); } " +
            "    Label #EfficiencyPips { Anchor: (Width: 150); Style: (FontSize: 13, TextColor: #66bb6a, VerticalAlignment: Center); } " +
            "  } " +
            "  Label #EfficiencyDesc { Anchor: (Height: 20); Style: (FontSize: 12, TextColor: #96a9be); } " +
            "  Group { Anchor: (Height: 30, Top: 3); LayoutMode: Left; " +
            "    Label #EfficiencyPrice { FlexWeight: 1; Style: (FontSize: 12, TextColor: #7c8b99, VerticalAlignment: Center); } " +
            (efficiencyPrice != null ?
            "    TextButton #BuyEfficiency { Anchor: (Width: 90, Height: 28); " +
            "      Style: TextButtonStyle(Default: (Background: #2a5f2a, LabelStyle: (FontSize: 11, TextColor: #ffffff, VerticalAlignment: Center)), " +
            "      Hovered: (Background: #3a7f3a, LabelStyle: (FontSize: 11, TextColor: #ffffff, VerticalAlignment: Center))); } "
            : "") +
            "  } " +
            "}");

        cmd.set("#EfficiencyPips.Text", efficiencyPips);
        cmd.set("#EfficiencyDesc.Text", efficiencyDesc);
        cmd.set("#EfficiencyPrice.Text", efficiencyPrice != null ? "Prix: " + SellService.formatMoney(efficiencyPrice) : "Niveau MAX");
        if (efficiencyPrice != null) {
            cmd.set("#BuyEfficiency.Text", "ACHETER");
            event.addEventBinding(CustomUIEventBindingType.Activating, "#BuyEfficiency", EventData.of("Action", "buyEfficiency"), false);
        }

        // Auto-Sell
        String autoSellStatus;
        String autoSellColor;
        if (!hasAutoSell) {
            autoSellStatus = "Non achete";
            autoSellColor = "#7c8b99";
        } else if (autoSellEnabled) {
            autoSellStatus = "ON";
            autoSellColor = "#66bb6a";
        } else {
            autoSellStatus = "OFF";
            autoSellColor = "#ef5350";
        }

        cmd.appendInline("#Content",
            "Group { Anchor: (Height: 70, Top: 8); Background: (Color: #151d28); Padding: (Full: 12); LayoutMode: Top; " +
            "  Group { Anchor: (Height: 25); LayoutMode: Left; " +
            "    Label { FlexWeight: 1; Text: \"Auto-Sell\"; Style: (FontSize: 15, TextColor: #ab47bc, RenderBold: true, VerticalAlignment: Center); } " +
            "    Label #AutoSellStatus { Anchor: (Width: 80); Style: (FontSize: 13, RenderBold: true, VerticalAlignment: Center); } " +
            "  } " +
            "  Group { Anchor: (Height: 30, Top: 3); LayoutMode: Left; " +
            "    Label #AutoSellInfo { FlexWeight: 1; Style: (FontSize: 12, TextColor: #7c8b99, VerticalAlignment: Center); } " +
            "    TextButton #AutoSellBtn { Anchor: (Width: 90, Height: 28); " +
            "      Style: TextButtonStyle(Default: (Background: " + (hasAutoSell ? "#1a3a5f" : "#2a5f2a") + ", LabelStyle: (FontSize: 11, TextColor: #ffffff, VerticalAlignment: Center)), " +
            "      Hovered: (Background: " + (hasAutoSell ? "#2a4a7f" : "#3a7f3a") + ", LabelStyle: (FontSize: 11, TextColor: #ffffff, VerticalAlignment: Center))); } " +
            "  } " +
            "}");

        cmd.set("#AutoSellStatus.Text", autoSellStatus);
        cmd.set("#AutoSellStatus.Style.TextColor", autoSellColor);
        cmd.set("#AutoSellInfo.Text", hasAutoSell ? "Vente automatique au minage" : "Prix: " + SellService.formatMoney(PickaxeUpgradeManager.getAutoSellBasePrice()));
        cmd.set("#AutoSellBtn.Text", hasAutoSell ? "TOGGLE" : "ACHETER");
        event.addEventBinding(CustomUIEventBindingType.Activating, "#AutoSellBtn", EventData.of("Action", "autoSell"), false);
    }

    // =========================================
    // CLASSEMENT
    // =========================================

    private void buildClassementPage(UICommandBuilder cmd, UIEventBuilder event) {
        cmd.clear("#Content");
        cmd.set("#HeaderTitle.Text", "CLASSEMENT");
        cmd.set("#BackBtn.Visible", true);

        Map<UUID, PlayerStatsManager.PlayerStatsData> allStats = plugin.getStatsManager().getAllStats();

        // Top par argent gagne
        cmd.appendInline("#Content",
            "Label { Anchor: (Height: 30); Text: \"Top Richesse\"; " +
            "Style: (FontSize: 16, TextColor: #ffd700, RenderBold: true); }");

        // Header colonnes
        cmd.appendInline("#Content",
            "Group { Anchor: (Height: 25); LayoutMode: Left; Padding: (Horizontal: 10); " +
            "  Label { Anchor: (Width: 30); Text: \"#\"; Style: (FontSize: 11, TextColor: #7c8b99, VerticalAlignment: Center); } " +
            "  Label { FlexWeight: 1; Text: \"Joueur\"; Style: (FontSize: 11, TextColor: #7c8b99, VerticalAlignment: Center); } " +
            "  Label { Anchor: (Width: 80); Text: \"Rang\"; Style: (FontSize: 11, TextColor: #7c8b99, VerticalAlignment: Center); } " +
            "  Label { Anchor: (Width: 120); Text: \"Argent gagne\"; Style: (FontSize: 11, TextColor: #7c8b99, VerticalAlignment: Center); } " +
            "  Label { Anchor: (Width: 100); Text: \"Blocs mines\"; Style: (FontSize: 11, TextColor: #7c8b99, VerticalAlignment: Center); } " +
            "}");

        // Trier par argent gagne
        List<Map.Entry<UUID, PlayerStatsManager.PlayerStatsData>> sorted = allStats.entrySet().stream()
            .sorted((a, b) -> b.getValue().totalMoneyEarned.compareTo(a.getValue().totalMoneyEarned))
            .limit(10)
            .collect(Collectors.toList());

        if (sorted.isEmpty()) {
            cmd.appendInline("#Content",
                "Label { Anchor: (Height: 30, Top: 10); Text: \"Aucune donnee disponible.\"; " +
                "Style: (FontSize: 13, TextColor: #808080); }");
        } else {
            for (int i = 0; i < sorted.size(); i++) {
                Map.Entry<UUID, PlayerStatsManager.PlayerStatsData> entry = sorted.get(i);
                UUID uuid = entry.getKey();
                PlayerStatsManager.PlayerStatsData stats = entry.getValue();
                String name = stats.playerName != null ? stats.playerName : "Unknown";
                String rank = plugin.getRankManager().getPlayerRank(uuid);
                int prestige = plugin.getRankManager().getPlayerPrestige(uuid);
                String rankDisplay = rank + (prestige > 0 ? " P" + prestige : "");
                String money = SellService.formatMoney(stats.totalMoneyEarned);
                String blocks = formatNumber(stats.blocksMined);

                String rankColor;
                switch (i) {
                    case 0: rankColor = "#ffd700"; break;
                    case 1: rankColor = "#c0c0c0"; break;
                    case 2: rankColor = "#cd7f32"; break;
                    default: rankColor = "#96a9be"; break;
                }
                String selector = "#Content[" + (i + 2) + "]"; // +2 pour le titre et le header

                cmd.appendInline("#Content",
                    "Group { Anchor: (Height: 28); LayoutMode: Left; Padding: (Horizontal: 10); " +
                    "  Background: (Color: " + (i % 2 == 0 ? "#111b27" : "#151d28") + "); " +
                    "  Label #Pos { Anchor: (Width: 30); Style: (FontSize: 12, RenderBold: true, VerticalAlignment: Center); } " +
                    "  Label #Name { FlexWeight: 1; Style: (FontSize: 12, TextColor: #ffffff, VerticalAlignment: Center); } " +
                    "  Label #Rank { Anchor: (Width: 80); Style: (FontSize: 12, TextColor: #ffd700, VerticalAlignment: Center); } " +
                    "  Label #Money { Anchor: (Width: 120); Style: (FontSize: 12, TextColor: #66bb6a, VerticalAlignment: Center); } " +
                    "  Label #Blocks { Anchor: (Width: 100); Style: (FontSize: 12, TextColor: #4fc3f7, VerticalAlignment: Center); } " +
                    "}");

                cmd.set(selector + " #Pos.Text", "#" + (i + 1));
                cmd.set(selector + " #Pos.Style.TextColor", rankColor);
                cmd.set(selector + " #Name.Text", name);
                cmd.set(selector + " #Rank.Text", rankDisplay);
                cmd.set(selector + " #Money.Text", money);
                cmd.set(selector + " #Blocks.Text", blocks);
            }
        }
    }

    // =========================================
    // CELLULE
    // =========================================

    private void buildCellulePage(UICommandBuilder cmd, UIEventBuilder event) {
        cmd.clear("#Content");
        cmd.set("#HeaderTitle.Text", "CELLULE");
        cmd.set("#BackBtn.Visible", true);

        UUID uuid = playerRef.getUuid();
        var cellManager = plugin.getCellManager();
        var playerCell = cellManager.getPlayerCell(uuid);

        if (playerCell != null) {
            cmd.appendInline("#Content",
                "Group { Anchor: (Height: 80); Background: (Color: #151d28); Padding: (Full: 15); LayoutMode: Top; " +
                "  Label { Anchor: (Height: 25); Text: \"Ta cellule\"; Style: (FontSize: 14, TextColor: #8d6e63, RenderBold: true); } " +
                "  Label #CellInfo { Anchor: (Height: 20); Style: (FontSize: 12, TextColor: #96a9be); } " +
                "}");
            cmd.set("#CellInfo.Text", "Cellule: " + playerCell.getOwnerName());

            if (playerCell.hasOwner()) {
                cmd.appendInline("#Content",
                    "Group { Anchor: (Height: 50, Top: 10); " +
                    "  TextButton #CellTpBtn { Anchor: (Width: 180, Height: 40); " +
                    "    Style: TextButtonStyle(Default: (Background: #2a5f2a, LabelStyle: (FontSize: 14, TextColor: #ffffff, RenderBold: true, VerticalAlignment: Center)), " +
                    "    Hovered: (Background: #3a7f3a, LabelStyle: (FontSize: 14, TextColor: #ffffff, RenderBold: true, VerticalAlignment: Center))); } " +
                    "}");
                cmd.set("#CellTpBtn.Text", "TELEPORTER");
                event.addEventBinding(CustomUIEventBindingType.Activating, "#CellTpBtn", EventData.of("Action", "tpCell"), false);
            }
        } else {
            cmd.appendInline("#Content",
                "Group { Anchor: (Height: 80); Background: (Color: #151d28); Padding: (Full: 15); LayoutMode: Top; " +
                "  Label { Anchor: (Height: 25); Text: \"Pas de cellule\"; Style: (FontSize: 14, TextColor: #8d6e63, RenderBold: true); } " +
                "  Label { Anchor: (Height: 20); Text: \"Tu n'as pas encore de cellule.\"; Style: (FontSize: 12, TextColor: #96a9be); } " +
                "}");

            // Bouton acheter s'il y a des cellules libres
            cmd.appendInline("#Content",
                "Group { Anchor: (Height: 50, Top: 10); " +
                "  TextButton #BuyCellBtn { Anchor: (Width: 180, Height: 40); " +
                "    Style: TextButtonStyle(Default: (Background: #2a5f2a, LabelStyle: (FontSize: 14, TextColor: #ffffff, RenderBold: true, VerticalAlignment: Center)), " +
                "    Hovered: (Background: #3a7f3a, LabelStyle: (FontSize: 14, TextColor: #ffffff, RenderBold: true, VerticalAlignment: Center))); } " +
                "}");
            cmd.set("#BuyCellBtn.Text", "ACHETER UNE CELLULE");
            event.addEventBinding(CustomUIEventBindingType.Activating, "#BuyCellBtn", EventData.of("Action", "buyCell"), false);
        }
    }

    // =========================================
    // GESTION DES EVENEMENTS
    // =========================================

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        super.handleDataEvent(ref, store, data);

        Player player = store.getComponent(ref, Player.getComponentType());
        UUID uuid = playerRef.getUuid();

        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder event = new UIEventBuilder();

        // Navigation vers sous-page
        if (data.navigate != null) {
            switch (data.navigate) {
                case "mines" -> { currentPage = "mines"; buildMinesPage(cmd, event); }
                case "rang" -> { currentPage = "rang"; buildRangPage(cmd, event); }
                case "upgrades" -> { currentPage = "upgrades"; buildUpgradesPage(cmd, event); }
                case "classement" -> { currentPage = "classement"; buildClassementPage(cmd, event); }
                case "cellule" -> { currentPage = "cellule"; buildCellulePage(cmd, event); }
                case "vendre" -> {
                    // Action directe: vendre tout
                    SellService.SellResult result = plugin.getSellService().sellFromInventory(uuid, player, null);
                    if (result.isEmpty()) {
                        player.sendMessage(Message.raw("Rien a vendre dans ton inventaire!"));
                    } else {
                        StringBuilder msg = new StringBuilder("Vendu " + result.getTotalBlocksSold() + " blocs pour " + SellService.formatMoney(result.getTotalEarned()) + "!");
                        player.sendMessage(Message.raw(msg.toString()));
                    }
                    return;
                }
            }
            sendUpdate(cmd, event, false);
            return;
        }

        // Bouton retour
        if (data.action != null && data.action.equals("back")) {
            currentPage = "hub";
            buildHub(cmd, event);
            sendUpdate(cmd, event, false);
            return;
        }

        // TP mine
        if (data.tpMine != null) {
            Mine mine = plugin.getMineManager().getMine(data.tpMine);
            if (mine != null && mine.hasSpawn() && plugin.getMineManager().canAccess(uuid, mine)) {
                IslandiumPlayer islandiumPlayer = plugin.getCore().getPlayerManager().getOnlinePlayer(uuid).orElse(null);
                if (islandiumPlayer != null) {
                    plugin.getCore().getTeleportService().teleportWithWarmup(
                        islandiumPlayer,
                        mine.getSpawnPoint(),
                        () -> player.sendMessage(Message.raw("Teleporte a la mine " + mine.getDisplayName() + "!"))
                    );
                }
            }
            return;
        }

        // Actions
        if (data.action != null) {
            switch (data.action) {
                case "rankup" -> {
                    PrisonRankManager.RankupResult result = plugin.getRankManager().rankup(uuid);
                    switch (result) {
                        case SUCCESS -> {
                            String newRank = plugin.getRankManager().getPlayerRank(uuid);
                            player.sendMessage(Message.raw("Rankup! Tu es maintenant rang " + newRank + "!"));
                            buildRangPage(cmd, event);
                            sendUpdate(cmd, event, false);
                        }
                        case NOT_ENOUGH_MONEY -> player.sendMessage(Message.raw("Pas assez d'argent!"));
                        case MAX_RANK -> player.sendMessage(Message.raw("Tu es deja au rang maximum!"));
                    }
                    return;
                }
                case "maxrankup" -> {
                    int count = plugin.getRankManager().maxRankup(uuid);
                    if (count > 0) {
                        String newRank = plugin.getRankManager().getPlayerRank(uuid);
                        player.sendMessage(Message.raw("Max Rankup! +" + count + " rangs -> " + newRank));
                        buildRangPage(cmd, event);
                        sendUpdate(cmd, event, false);
                    } else {
                        player.sendMessage(Message.raw("Impossible de rankup (pas assez d'argent ou rang max)!"));
                    }
                    return;
                }
                case "prestige" -> {
                    if (plugin.getRankManager().prestige(uuid)) {
                        int newPrestige = plugin.getRankManager().getPlayerPrestige(uuid);
                        player.sendMessage(Message.raw("Prestige! Tu es maintenant Prestige " + newPrestige + "!"));
                        buildRangPage(cmd, event);
                        sendUpdate(cmd, event, false);
                    } else {
                        player.sendMessage(Message.raw("Tu dois etre rang FREE pour prestige!"));
                    }
                    return;
                }
                case "buyFortune" -> {
                    PickaxeUpgradeManager.UpgradeResult result = plugin.getUpgradeManager().purchaseFortune(uuid);
                    switch (result) {
                        case SUCCESS -> {
                            int lvl = plugin.getStatsManager().getFortuneLevel(uuid);
                            player.sendMessage(Message.raw("Fortune amelioree au niveau " + lvl + "!"));
                            buildUpgradesPage(cmd, event);
                            sendUpdate(cmd, event, false);
                        }
                        case NOT_ENOUGH_MONEY -> player.sendMessage(Message.raw("Pas assez d'argent!"));
                        case MAX_LEVEL -> player.sendMessage(Message.raw("Fortune deja au niveau max!"));
                    }
                    return;
                }
                case "buyEfficiency" -> {
                    PickaxeUpgradeManager.UpgradeResult result = plugin.getUpgradeManager().purchaseEfficiency(uuid);
                    switch (result) {
                        case SUCCESS -> {
                            int lvl = plugin.getStatsManager().getEfficiencyLevel(uuid);
                            player.sendMessage(Message.raw("Efficacite amelioree au niveau " + lvl + "!"));
                            buildUpgradesPage(cmd, event);
                            sendUpdate(cmd, event, false);
                        }
                        case NOT_ENOUGH_MONEY -> player.sendMessage(Message.raw("Pas assez d'argent!"));
                        case MAX_LEVEL -> player.sendMessage(Message.raw("Efficacite deja au niveau max!"));
                    }
                    return;
                }
                case "autoSell" -> {
                    if (plugin.getStatsManager().hasAutoSell(uuid)) {
                        boolean enabled = plugin.getUpgradeManager().toggleAutoSell(uuid);
                        player.sendMessage(Message.raw("Auto-Sell " + (enabled ? "active!" : "desactive!")));
                    } else {
                        PickaxeUpgradeManager.UpgradeResult result = plugin.getUpgradeManager().purchaseAutoSell(uuid);
                        switch (result) {
                            case SUCCESS -> player.sendMessage(Message.raw("Auto-Sell achete et active!"));
                            case NOT_ENOUGH_MONEY -> player.sendMessage(Message.raw("Pas assez d'argent!"));
                            default -> {}
                        }
                    }
                    buildUpgradesPage(cmd, event);
                    sendUpdate(cmd, event, false);
                    return;
                }
                case "tpCell" -> {
                    var cell = plugin.getCellManager().getPlayerCell(uuid);
                    if (cell != null && cell.hasOwner()) {
                        IslandiumPlayer islandiumPlayer = plugin.getCore().getPlayerManager().getOnlinePlayer(uuid).orElse(null);
                        if (islandiumPlayer != null) {
                            plugin.getCore().getTeleportService().teleportWithWarmup(
                                islandiumPlayer,
                                cell.getSpawnPoint(),
                                () -> player.sendMessage(Message.raw("Teleporte a ta cellule!"))
                            );
                        }
                    }
                    return;
                }
                case "buyCell" -> {
                    String playerName = playerRef.getUsername();
                    var cellResult = plugin.getCellManager().purchaseCell(uuid, playerName);
                    switch (cellResult) {
                        case SUCCESS -> {
                            player.sendMessage(Message.raw("Cellule achetee!"));
                            buildCellulePage(cmd, event);
                            sendUpdate(cmd, event, false);
                        }
                        case NOT_ENOUGH_MONEY -> player.sendMessage(Message.raw("Pas assez d'argent!"));
                        case ALREADY_HAS_CELL -> player.sendMessage(Message.raw("Tu as deja une cellule!"));
                        case NO_CELLS_AVAILABLE -> player.sendMessage(Message.raw("Aucune cellule disponible!"));
                        default -> player.sendMessage(Message.raw("Impossible d'acheter une cellule!"));
                    }
                    return;
                }
            }
        }
    }

    // =========================================
    // UTILITAIRES
    // =========================================

    private String buildPips(int current, int max) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < max; i++) {
            sb.append(i < current ? "#" : "-");
            if (i < max - 1) sb.append(" ");
        }
        return "[" + sb + "] " + current + "/" + max;
    }

    private String formatNumber(long number) {
        if (number >= 1_000_000_000) {
            return String.format("%.2fB", number / 1_000_000_000.0);
        } else if (number >= 1_000_000) {
            return String.format("%.2fM", number / 1_000_000.0);
        } else if (number >= 1_000) {
            return String.format("%.1fK", number / 1_000.0);
        }
        return String.valueOf(number);
    }

    // =========================================
    // DATA CODEC
    // =========================================

    public static class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
                .addField(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .addField(new KeyedCodec<>("Navigate", Codec.STRING), (d, v) -> d.navigate = v, d -> d.navigate)
                .addField(new KeyedCodec<>("TpMine", Codec.STRING), (d, v) -> d.tpMine = v, d -> d.tpMine)
                .build();

        public String action;
        public String navigate;
        public String tpMine;
    }
}
