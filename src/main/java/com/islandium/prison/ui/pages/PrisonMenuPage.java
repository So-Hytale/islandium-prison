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
import com.islandium.prison.challenge.ChallengeDefinition;
import com.islandium.prison.challenge.ChallengeManager;
import com.islandium.prison.challenge.ChallengeRegistry;
import com.islandium.prison.challenge.ChallengeType;
import com.islandium.prison.challenge.PlayerChallengeProgress;
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
import com.hypixel.hytale.server.core.inventory.Inventory;
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
    private String startPage = null;
    private String viewingDefiRank = null; // Rang actuellement affiché dans la page defis

    public PrisonMenuPage(@Nonnull PlayerRef playerRef, PrisonPlugin plugin) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageData.CODEC);
        this.plugin = plugin;
        this.playerRef = playerRef;
    }

    /**
     * Constructeur avec page de depart (ex: "cellule" pour ouvrir directement la page cellule).
     */
    public PrisonMenuPage(@Nonnull PlayerRef playerRef, PrisonPlugin plugin, @Nonnull String startPage) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageData.CODEC);
        this.plugin = plugin;
        this.playerRef = playerRef;
        this.startPage = startPage;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder event, @Nonnull Store<EntityStore> store) {
        cmd.append("Pages/Prison/PrisonMenuPage.ui");

        // Back button event
        event.addEventBinding(CustomUIEventBindingType.Activating, "#BackBtn", EventData.of("Action", "back"), false);

        // Close button event
        event.addEventBinding(CustomUIEventBindingType.Activating, "#CloseButton", EventData.of("Action", "close"), false);

        // Hub card events (grille definie dans le .ui)
        event.addEventBinding(CustomUIEventBindingType.Activating, "#CardMines", EventData.of("Navigate", "mines"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#CardDefis", EventData.of("Navigate", "defis"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#CardUpgrades", EventData.of("Navigate", "upgrades"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#CardVendre", EventData.of("Navigate", "vendre"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#CardClassement", EventData.of("Navigate", "classement"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#CardCellule", EventData.of("Navigate", "cellule"), false);

        // Si startPage defini, ouvrir directement la sous-page
        if (startPage != null) {
            currentPage = startPage;
            switch (startPage) {
                case "cellule" -> buildCellulePage(cmd, event);
                case "mines" -> buildMinesPage(cmd, event);
                case "upgrades" -> buildUpgradesPage(cmd, event);
                case "classement" -> buildClassementPage(cmd, event);
                case "defis" -> buildDefisPage(cmd, event);
                default -> showHub(cmd);
            }
        } else {
            // Hub visible par defaut, PageContent masque
            showHub(cmd);
        }
    }

    // =========================================
    // HUB - Toggle visibilite
    // =========================================

    private void showHub(UICommandBuilder cmd) {
        cmd.set("#HubGrid.Visible", true);
        cmd.set("#PageContent.Visible", false);
        cmd.set("#HeaderTitle.Text", "");
        cmd.set("#BackBtn.Visible", true);
        cmd.set("#HeaderConfigBtn.Visible", false);
    }

    private void showSubPage(UICommandBuilder cmd) {
        cmd.set("#HubGrid.Visible", false);
        cmd.set("#PageContent.Visible", true);
        cmd.set("#BackBtn.Visible", true);
        cmd.set("#HeaderConfigBtn.Visible", false);
    }

    // =========================================
    // MINES
    // =========================================

    private void buildMinesPage(UICommandBuilder cmd, UIEventBuilder event) {
        showSubPage(cmd);
        cmd.clear("#PageContent");
        cmd.set("#HeaderTitle.Text", "MINES");

        UUID uuid = playerRef.getUuid();
        String playerRank = plugin.getRankManager().getPlayerRank(uuid);
        Collection<Mine> mines = plugin.getMineManager().getAllMines();

        if (mines.isEmpty()) {
            cmd.appendInline("#PageContent",
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

        // Grille de cartes 3 par ligne (style hub Prison)
        // Utilise cmd.append() avec template .ui pour que TexturePath fonctionne
        int cols = 3;
        int rows = (sortedMines.size() + cols - 1) / cols;

        for (int row = 0; row < rows; row++) {
            // Creer le conteneur de ligne
            cmd.appendInline("#PageContent",
                "Group #MineRow" + row + " { Anchor: (Height: 210" + (row > 0 ? ", Top: 10" : "") + "); LayoutMode: Left; }");

            for (int col = 0; col < cols; col++) {
                int idx = row * cols + col;
                String rowSelector = "#MineRow" + row;

                if (idx >= sortedMines.size()) {
                    // Espace vide pour remplir la ligne
                    cmd.appendInline(rowSelector, "Group { FlexWeight: 1; }");
                    continue;
                }

                Mine mine = sortedMines.get(idx);
                boolean canAccess = plugin.getMineManager().canAccess(uuid, mine);

                // Append le template carte .ui
                String template = canAccess
                    ? "Pages/Prison/MineCard.ui"
                    : "Pages/Prison/MineCardLocked.ui";
                cmd.append(rowSelector, template);

                // Le template est ajoute a l'index col dans la row
                String cardSelector = rowSelector + "[" + col + "]";

                // Injecter l'icone generee (fichier .ui avec le bon TexturePath)
                String iconUi = "Pages/Prison/Icons/MineIcon_" + mine.getId().toLowerCase() + ".ui";
                cmd.append(cardSelector + " #IconTarget", iconUi);

                // Set les textes dynamiques
                cmd.set(cardSelector + " #MTitle.Text", mine.getDisplayName());
                cmd.set(cardSelector + " #MStatus.Text",
                    canAccess ? "Accessible" : "Rang " + mine.getRequiredRank() + " requis");

                if (canAccess && mine.hasSpawn()) {
                    cmd.set(cardSelector + " #TpZone.Visible", true);
                    event.addEventBinding(CustomUIEventBindingType.Activating,
                        cardSelector + " #TpBtn",
                        EventData.of("TpMine", mine.getId()),
                        false);
                }
            }
        }
    }

    // =========================================
    // RANG
    // =========================================

    private void buildRangPage(UICommandBuilder cmd, UIEventBuilder event) {
        showSubPage(cmd);
        cmd.clear("#PageContent");
        cmd.set("#HeaderTitle.Text", "RANG");

        UUID uuid = playerRef.getUuid();
        String currentRank = plugin.getRankManager().getPlayerRank(uuid);
        int prestige = plugin.getRankManager().getPlayerPrestige(uuid);
        double multiplier = plugin.getRankManager().getPlayerMultiplier(uuid);
        PrisonConfig.RankInfo nextRank = plugin.getRankManager().getNextRankInfo(uuid);

        // Rang actuel
        cmd.appendInline("#PageContent",
            "Group { Anchor: (Height: 80); Background: (Color: #151d28); Padding: (Full: 15); LayoutMode: Top; " +
            "  Label { Anchor: (Height: 25); Text: \"Rang actuel\"; Style: (FontSize: 12, TextColor: #7c8b99); } " +
            "  Label #CurrentRank { Anchor: (Height: 35); Style: (FontSize: 28, TextColor: #ffd700, RenderBold: true); } " +
            "}");
        cmd.set("#CurrentRank.Text", currentRank + (prestige > 0 ? "  [P" + prestige + "]" : ""));

        // Multiplicateur
        cmd.appendInline("#PageContent",
            "Group { Anchor: (Height: 40, Top: 8); Background: (Color: #151d28); Padding: (Horizontal: 15); LayoutMode: Left; " +
            "  Label { FlexWeight: 1; Text: \"Multiplicateur de gains\"; Style: (FontSize: 13, TextColor: #96a9be, VerticalAlignment: Center); } " +
            "  Label #Multiplier { Anchor: (Width: 100); Style: (FontSize: 15, TextColor: #00e5ff, RenderBold: true, VerticalAlignment: Center); } " +
            "}");
        cmd.set("#Multiplier.Text", String.format("x%.2f", multiplier));

        // Prochain rang
        if (nextRank != null) {
            BigDecimal price = plugin.getRankManager().getRankupPrice(uuid, nextRank);

            cmd.appendInline("#PageContent",
                "Group { Anchor: (Height: 60, Top: 8); Background: (Color: #151d28); Padding: (Full: 15); LayoutMode: Top; " +
                "  Label #NextRankLabel { Anchor: (Height: 20); Style: (FontSize: 12, TextColor: #7c8b99); } " +
                "  Label #NextRankPrice { Anchor: (Height: 25); Style: (FontSize: 14, TextColor: #ffffff); } " +
                "}");
            cmd.set("#NextRankLabel.Text", "Prochain rang: " + nextRank.displayName);
            cmd.set("#NextRankPrice.Text", "Prix: " + SellService.formatMoney(price));

            // Indicateur defis
            int challengeCompleted = plugin.getChallengeManager().getCompletedCount(uuid, currentRank);
            int challengeTotal = ChallengeRegistry.getChallengesForRank(currentRank).size();
            boolean allDone = challengeCompleted >= challengeTotal;
            String defiBgColor = allDone ? "#1a2a1a" : "#2a1a1a";
            String defiTextColor = allDone ? "#66bb6a" : "#ef5350";
            String defiText = allDone
                ? "Defis completes! (" + challengeCompleted + "/" + challengeTotal + ")"
                : "Defis requis: " + challengeCompleted + "/" + challengeTotal;

            cmd.appendInline("#PageContent",
                "Group { Anchor: (Height: 35, Top: 8); Background: (Color: " + defiBgColor + "); Padding: (Horizontal: 15); LayoutMode: Left; " +
                "  Label #DefiStatus { FlexWeight: 1; Style: (FontSize: 13, RenderBold: true, VerticalAlignment: Center); } " +
                "}");
            cmd.set("#DefiStatus.Text", defiText);
            cmd.set("#DefiStatus.Style.TextColor", defiTextColor);

            // Boutons
            cmd.appendInline("#PageContent",
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
            cmd.appendInline("#PageContent",
                "Group { Anchor: (Height: 60, Top: 10); LayoutMode: Left; " +
                "  TextButton #PrestigeBtn { Anchor: (Width: 200, Height: 45); " +
                "    Style: TextButtonStyle(Default: (Background: #5f2a5f, LabelStyle: (FontSize: 15, TextColor: #ff80ff, RenderBold: true, VerticalAlignment: Center)), " +
                "    Hovered: (Background: #7f3a7f, LabelStyle: (FontSize: 15, TextColor: #ffffff, RenderBold: true, VerticalAlignment: Center))); } " +
                "}");
            cmd.set("#PrestigeBtn.Text", "PRESTIGE");
            event.addEventBinding(CustomUIEventBindingType.Activating, "#PrestigeBtn", EventData.of("Action", "prestige"), false);
        } else {
            cmd.appendInline("#PageContent",
                "Label { Anchor: (Height: 30, Top: 10); Text: \"Rang maximum atteint!\"; " +
                "Style: (FontSize: 14, TextColor: #ffd700, RenderBold: true); }");
        }
    }

    // =========================================
    // UPGRADES
    // =========================================

    private void buildUpgradesPage(UICommandBuilder cmd, UIEventBuilder event) {
        showSubPage(cmd);
        cmd.clear("#PageContent");
        cmd.set("#HeaderTitle.Text", "UPGRADES");

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

        cmd.appendInline("#PageContent",
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

        cmd.appendInline("#PageContent",
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

        cmd.appendInline("#PageContent",
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
    // VENDRE
    // =========================================

    private void buildVendrePage(UICommandBuilder cmd, UIEventBuilder event, Player player) {
        showSubPage(cmd);
        cmd.clear("#PageContent");
        cmd.set("#HeaderTitle.Text", "VENDRE");

        UUID uuid = playerRef.getUuid();
        Map<String, BigDecimal> blockValues = plugin.getConfig().getBlockValues();
        double multiplier = plugin.getRankManager().getPlayerMultiplier(uuid) * plugin.getConfig().getBlockSellMultiplier();

        // Balance actuelle
        BigDecimal balance = BigDecimal.ZERO;
        try {
            EconomyService eco = IslandiumAPI.get().getEconomyService();
            if (eco != null) {
                balance = eco.getBalance(uuid).join();
            }
        } catch (Exception ignored) {}

        // Resume rapide
        cmd.appendInline("#PageContent",
            "Group { Anchor: (Height: 45); Background: (Color: #151d28); Padding: (Horizontal: 15); LayoutMode: Left; " +
            "  Label #MultLabel { FlexWeight: 1; Style: (FontSize: 13, TextColor: #00e5ff, RenderBold: true, VerticalAlignment: Center); } " +
            "  Label #BalLabel { Anchor: (Width: 200); Style: (FontSize: 13, TextColor: #66bb6a, RenderBold: true, VerticalAlignment: Center); } " +
            "}");
        cmd.set("#MultLabel.Text", "Multiplicateur: x" + String.format("%.2f", multiplier));
        cmd.set("#BalLabel.Text", "Balance: " + SellService.formatMoney(balance));

        // Titre prix des blocs
        cmd.appendInline("#PageContent",
            "Label { Anchor: (Height: 30, Top: 12); Text: \"PRIX DES BLOCS\"; " +
            "Style: (FontSize: 14, TextColor: #ffd700, RenderBold: true); }");

        // Header colonnes prix
        cmd.appendInline("#PageContent",
            "Group { Anchor: (Height: 22); LayoutMode: Left; Padding: (Horizontal: 10); " +
            "  Label { FlexWeight: 1; Text: \"Bloc\"; Style: (FontSize: 10, TextColor: #7c8b99, VerticalAlignment: Center); } " +
            "  Label { Anchor: (Width: 100); Text: \"Prix unit.\"; Style: (FontSize: 10, TextColor: #7c8b99, VerticalAlignment: Center); } " +
            "  Label { Anchor: (Width: 100); Text: \"Avec multi.\"; Style: (FontSize: 10, TextColor: #7c8b99, VerticalAlignment: Center); } " +
            "}");

        // Liste des blocs triés par prix
        List<Map.Entry<String, BigDecimal>> sortedBlocks = blockValues.entrySet().stream()
            .sorted(Map.Entry.comparingByValue())
            .collect(Collectors.toList());

        int priceIdx = 0;
        for (Map.Entry<String, BigDecimal> entry : sortedBlocks) {
            String blockId = entry.getKey();
            BigDecimal basePrice = entry.getValue();
            BigDecimal withMult = basePrice.multiply(BigDecimal.valueOf(multiplier)).setScale(2, java.math.RoundingMode.HALF_UP);
            String bgColor = priceIdx % 2 == 0 ? "#111b27" : "#151d28";
            String rowSelector = "#PageContent[" + (priceIdx + 3) + "]"; // +3 pour resume + titre + header

            cmd.appendInline("#PageContent",
                "Group { Anchor: (Height: 26); LayoutMode: Left; Padding: (Horizontal: 10); Background: (Color: " + bgColor + "); " +
                "  Label #BName { FlexWeight: 1; Style: (FontSize: 11, TextColor: #ffffff, VerticalAlignment: Center); } " +
                "  Label #BBase { Anchor: (Width: 100); Style: (FontSize: 11, TextColor: #96a9be, VerticalAlignment: Center); } " +
                "  Label #BMult { Anchor: (Width: 100); Style: (FontSize: 11, TextColor: #66bb6a, VerticalAlignment: Center); } " +
                "}");

            cmd.set(rowSelector + " #BName.Text", formatBlockName(blockId));
            cmd.set(rowSelector + " #BBase.Text", basePrice.setScale(2, java.math.RoundingMode.HALF_UP) + "$");
            cmd.set(rowSelector + " #BMult.Text", withMult + "$");
            priceIdx++;
        }

        // Separator
        int invHeaderIdx = priceIdx + 3;

        // Titre inventaire
        cmd.appendInline("#PageContent",
            "Label { Anchor: (Height: 30, Top: 12); Text: \"TON INVENTAIRE\"; " +
            "Style: (FontSize: 14, TextColor: #4fc3f7, RenderBold: true); }");

        // Scanner inventaire
        Map<String, Integer> sellableItems = scanSellableInventory(player, blockValues);

        if (sellableItems.isEmpty()) {
            cmd.appendInline("#PageContent",
                "Label { Anchor: (Height: 25); Text: \"Rien a vendre dans ton inventaire.\"; " +
                "Style: (FontSize: 12, TextColor: #808080); }");

            // Bouton admin meme si inventaire vide
            appendAdminButton(cmd, event, uuid);
            return;
        }

        BigDecimal totalEstimated = BigDecimal.ZERO;
        int invIdx = 0;
        for (Map.Entry<String, Integer> item : sellableItems.entrySet()) {
            String blockId = item.getKey();
            int qty = item.getValue();
            BigDecimal basePrice = blockValues.getOrDefault(blockId, BigDecimal.ZERO);
            BigDecimal itemTotal = basePrice.multiply(BigDecimal.valueOf(qty)).multiply(BigDecimal.valueOf(multiplier))
                .setScale(2, java.math.RoundingMode.HALF_UP);
            totalEstimated = totalEstimated.add(itemTotal);

            String bgColor = invIdx % 2 == 0 ? "#111b27" : "#151d28";
            // Use unique IDs per row to avoid selector issues
            String invRowId = "InvRow" + invIdx;

            cmd.appendInline("#PageContent",
                "Group #" + invRowId + " { Anchor: (Height: 26); LayoutMode: Left; Padding: (Horizontal: 10); Background: (Color: " + bgColor + "); " +
                "  Label #IName { FlexWeight: 1; Style: (FontSize: 11, TextColor: #ffffff, VerticalAlignment: Center); } " +
                "  Label #ITotal { Anchor: (Width: 120); Style: (FontSize: 11, TextColor: #66bb6a, RenderBold: true, VerticalAlignment: Center); } " +
                "}");

            cmd.set("#" + invRowId + " #IName.Text", qty + "x " + formatBlockName(blockId));
            cmd.set("#" + invRowId + " #ITotal.Text", SellService.formatMoney(itemTotal));
            invIdx++;
        }

        // Total estimé
        cmd.appendInline("#PageContent",
            "Group { Anchor: (Height: 35, Top: 8); Background: (Color: #1a2a1a); Padding: (Horizontal: 15); LayoutMode: Left; " +
            "  Label { FlexWeight: 1; Text: \"Total estime\"; Style: (FontSize: 14, TextColor: #ffffff, RenderBold: true, VerticalAlignment: Center); } " +
            "  Label #TotalEst { Anchor: (Width: 150); Style: (FontSize: 16, TextColor: #66bb6a, RenderBold: true, VerticalAlignment: Center); } " +
            "}");
        cmd.set("#TotalEst.Text", SellService.formatMoney(totalEstimated));

        // Bouton VENDRE TOUT
        cmd.appendInline("#PageContent",
            "Group { Anchor: (Height: 55, Top: 10); LayoutMode: Left; " +
            "  Group { FlexWeight: 1; } " +
            "  TextButton #SellAllBtn { Anchor: (Width: 220, Height: 45); " +
            "    Style: TextButtonStyle(Default: (Background: #2a5f2a, LabelStyle: (FontSize: 16, TextColor: #ffffff, RenderBold: true, VerticalAlignment: Center)), " +
            "    Hovered: (Background: #3a7f3a, LabelStyle: (FontSize: 16, TextColor: #ffffff, RenderBold: true, VerticalAlignment: Center))); } " +
            "  Group { FlexWeight: 1; } " +
            "}");
        cmd.set("#SellAllBtn.Text", "VENDRE TOUT");
        event.addEventBinding(CustomUIEventBindingType.Activating, "#SellAllBtn", EventData.of("Action", "sellAll"), false);

        appendAdminButton(cmd, event, uuid);
    }

    private void appendAdminButton(UICommandBuilder cmd, UIEventBuilder event, UUID uuid) {
        boolean isAdmin = false;
        try {
            var perms = com.hypixel.hytale.server.core.permissions.PermissionsModule.get();
            isAdmin = perms.getGroupsForUser(uuid).contains("OP")
                || perms.hasPermission(uuid, "prison.admin")
                || perms.hasPermission(uuid, "*");
        } catch (Exception ignored) {}

        if (isAdmin) {
            cmd.set("#HeaderConfigBtn.Visible", true);
            cmd.set("#HeaderConfigBtn.Text", "CONFIG ADMIN");
            event.addEventBinding(CustomUIEventBindingType.Activating, "#HeaderConfigBtn", EventData.of("Action", "openSellConfig"), false);
        }
    }

    private Map<String, Integer> scanSellableInventory(Player player, Map<String, BigDecimal> blockValues) {
        Map<String, Integer> result = new LinkedHashMap<>();
        try {
            Inventory inv = player.getInventory();

            // Storage
            var storage = inv.getStorage();
            for (short slot = 0; slot < storage.getCapacity(); slot++) {
                var stack = storage.getItemStack(slot);
                if (com.hypixel.hytale.server.core.inventory.ItemStack.isEmpty(stack)) continue;
                String itemId = stack.getItemId();
                BigDecimal value = blockValues.get(itemId);
                if (value != null && value.compareTo(BigDecimal.ZERO) > 0) {
                    result.merge(itemId, stack.getQuantity(), Integer::sum);
                }
            }

            // Hotbar
            var hotbar = inv.getHotbar();
            for (short slot = 0; slot < hotbar.getCapacity(); slot++) {
                var stack = hotbar.getItemStack(slot);
                if (com.hypixel.hytale.server.core.inventory.ItemStack.isEmpty(stack)) continue;
                String itemId = stack.getItemId();
                BigDecimal value = blockValues.get(itemId);
                if (value != null && value.compareTo(BigDecimal.ZERO) > 0) {
                    result.merge(itemId, stack.getQuantity(), Integer::sum);
                }
            }
        } catch (Exception ignored) {}
        return result;
    }

    private String formatBlockName(String blockId) {
        // "minecraft:cobblestone" -> "Cobblestone"
        // "minecraft:coal_ore" -> "Coal Ore"
        String name = blockId;
        int colonIdx = name.indexOf(':');
        if (colonIdx >= 0) {
            name = name.substring(colonIdx + 1);
        }
        // Replace underscores with spaces and capitalize each word
        String[] parts = name.split("_");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) sb.append(" ");
            if (!parts[i].isEmpty()) {
                sb.append(Character.toUpperCase(parts[i].charAt(0)));
                if (parts[i].length() > 1) sb.append(parts[i].substring(1));
            }
        }
        return sb.toString();
    }

    // =========================================
    // CLASSEMENT
    // =========================================

    private void buildClassementPage(UICommandBuilder cmd, UIEventBuilder event) {
        showSubPage(cmd);
        cmd.clear("#PageContent");
        cmd.set("#HeaderTitle.Text", "CLASSEMENT");

        Map<UUID, PlayerStatsManager.PlayerStatsData> allStats = plugin.getStatsManager().getAllStats();

        // Titre
        cmd.appendInline("#PageContent",
            "Label { Anchor: (Height: 30); Text: \"Top Joueurs\"; " +
            "Style: (FontSize: 16, TextColor: #ffd700, RenderBold: true); }");

        // Header colonnes
        cmd.appendInline("#PageContent",
            "Group { Anchor: (Height: 25); LayoutMode: Left; Padding: (Horizontal: 10); " +
            "  Label { Anchor: (Width: 30); Text: \"#\"; Style: (FontSize: 11, TextColor: #7c8b99, VerticalAlignment: Center); } " +
            "  Label { FlexWeight: 1; Text: \"Joueur\"; Style: (FontSize: 11, TextColor: #7c8b99, VerticalAlignment: Center); } " +
            "  Label { Anchor: (Width: 80); Text: \"Rang\"; Style: (FontSize: 11, TextColor: #7c8b99, VerticalAlignment: Center); } " +
            "  Label { Anchor: (Width: 120); Text: \"Argent gagne\"; Style: (FontSize: 11, TextColor: #7c8b99, VerticalAlignment: Center); } " +
            "  Label { Anchor: (Width: 100); Text: \"Blocs mines\"; Style: (FontSize: 11, TextColor: #7c8b99, VerticalAlignment: Center); } " +
            "}");

        // Trier par rang > argent gagne > blocs mines > nom
        var rankManager = plugin.getRankManager();
        List<Map.Entry<UUID, PlayerStatsManager.PlayerStatsData>> sorted = allStats.entrySet().stream()
            .sorted((a, b) -> {
                // 1. Prestige + Rang combinés (décroissant : P1-A > P0-Z > P0-C > P0-A)
                int prestigeA = rankManager.getPlayerPrestige(a.getKey());
                int prestigeB = rankManager.getPlayerPrestige(b.getKey());
                int rankIdxA = rankManager.getRankIndex(rankManager.getPlayerRank(a.getKey()));
                int rankIdxB = rankManager.getRankIndex(rankManager.getPlayerRank(b.getKey()));
                int scoreA = prestigeA * 100 + rankIdxA;
                int scoreB = prestigeB * 100 + rankIdxB;
                int cmp = Integer.compare(scoreB, scoreA);
                if (cmp != 0) return cmp;
                // 2. Argent gagné (décroissant)
                cmp = b.getValue().totalMoneyEarned.compareTo(a.getValue().totalMoneyEarned);
                if (cmp != 0) return cmp;
                // 3. Blocs minés (décroissant)
                cmp = Long.compare(b.getValue().blocksMined, a.getValue().blocksMined);
                if (cmp != 0) return cmp;
                // 4. Nom (alphabétique)
                String nameA = a.getValue().playerName != null ? a.getValue().playerName : "";
                String nameB = b.getValue().playerName != null ? b.getValue().playerName : "";
                return nameA.compareToIgnoreCase(nameB);
            })
            .limit(10)
            .collect(Collectors.toList());

        if (sorted.isEmpty()) {
            cmd.appendInline("#PageContent",
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
                String selector = "#PageContent[" + (i + 2) + "]"; // +2 pour le titre et le header

                cmd.appendInline("#PageContent",
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
        showSubPage(cmd);
        cmd.clear("#PageContent");
        cmd.set("#HeaderTitle.Text", "CELLULE");

        UUID uuid = playerRef.getUuid();
        var cellsApi = com.islandium.cells.api.CellsAPI.get();
        var playerCell = cellsApi != null ? cellsApi.getPlayerCell(uuid) : null;

        if (playerCell != null) {
            buildCellHubPage(cmd, event, playerCell, cellsApi);
        } else {
            buildCellNoCellPage(cmd, event, cellsApi);
        }
    }

    private void buildCellHubPage(UICommandBuilder cmd, UIEventBuilder event,
            com.islandium.cells.cell.Cell cell, com.islandium.cells.api.CellsAPI cellsApi) {
        cmd.append("#PageContent", "Pages/Prison/CellHub.ui");

        // Info cellule
        cmd.set("#CellOwnerLabel.Text", "Cellule de " + cell.getOwnerName());

        var levelMgr = cellsApi.getLevelManager();
        var level = levelMgr.getLevel(cell.getCurrentLevel());
        String levelName = level != null ? level.getName() : "Nv." + cell.getCurrentLevel();
        int zoneSize = level != null ? level.getZoneRadius() * 2 : 0;
        cmd.set("#CellLevelLabel.Text", "Niveau " + cell.getCurrentLevel() + " - " + levelName);

        int memberCount = cellsApi.getMemberManager().getMemberCount(cell.getId());
        cmd.set("#CellMembersLabel.Text", "Membres: " + memberCount + "/" + cell.getMaxMembers());

        cmd.set("#CellAccessLabel.Text", cell.isPublic() ? "Public" : "Prive");

        // Events cartes
        event.addEventBinding(CustomUIEventBindingType.Activating, "#CellTpCard", EventData.of("Action", "tpCell"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#CellMembersCard", EventData.of("Action", "cellMembers"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#CellSettingsCard", EventData.of("Action", "cellSettings"), false);
    }

    private void buildCellNoCellPage(UICommandBuilder cmd, UIEventBuilder event, com.islandium.cells.api.CellsAPI cellsApi) {
        cmd.append("#PageContent", "Pages/Prison/CellNoCell.ui");

        // Prix d'achat
        String priceText = "ACHETER UNE CELLULE";
        if (cellsApi != null) {
            var level0 = cellsApi.getLevelManager().getLevel(0);
            if (level0 != null) {
                priceText = "ACHETER UNE CELLULE - " + com.islandium.prison.economy.SellService.formatMoney(level0.getPrice());
            }
        }
        cmd.set("#BuyCellBtn.Text", priceText);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#BuyCellBtn", EventData.of("Action", "buyCell"), false);
    }

    private void buildCellMembersPage(UICommandBuilder cmd, UIEventBuilder event) {
        showSubPage(cmd);
        cmd.clear("#PageContent");
        cmd.set("#HeaderTitle.Text", "MEMBRES");

        UUID uuid = playerRef.getUuid();
        var cellsApi = com.islandium.cells.api.CellsAPI.get();
        var playerCell = cellsApi != null ? cellsApi.getPlayerCell(uuid) : null;
        if (playerCell == null) {
            buildCellulePage(cmd, event);
            return;
        }

        cmd.append("#PageContent", "Pages/Prison/CellMembers.ui");

        var memberMgr = cellsApi.getMemberManager();
        var members = memberMgr.getMembers(playerCell.getId());
        int memberCount = members.size();
        cmd.set("#MemberCountLabel.Text", "Membres: " + memberCount + "/" + playerCell.getMaxMembers());

        boolean isOwner = playerCell.isOwner(uuid);
        boolean canInvite = isOwner || memberMgr.canInvite(playerCell, uuid);

        // Ligne proprietaire
        cmd.append("#MemberList", "Pages/Prison/CellMemberOwner.ui");
        cmd.set("#MemberList[0] #MOwnerName.Text", playerCell.getOwnerName());

        // Lignes membres
        int idx = 1;
        for (var member : members) {
            cmd.append("#MemberList", "Pages/Prison/CellMemberRow.ui");
            String rowSelector = "#MemberList[" + idx + "]";

            cmd.set(rowSelector + " #MName.Text", member.getPlayerName());

            String roleName = member.getRole().name();
            String roleColor;
            switch (member.getRole()) {
                case MANAGER -> roleColor = "#ff9800";
                case MEMBER -> roleColor = "#4fc3f7";
                default -> roleColor = "#7c8b99";
            }
            cmd.set(rowSelector + " #MRole.Text", roleName);
            cmd.set(rowSelector + " #MRole.Style.TextColor", roleColor);

            // Boutons visibles uniquement si le joueur est owner
            if (isOwner) {
                String memberUuid = member.getPlayerUuid().toString();
                event.addEventBinding(CustomUIEventBindingType.Activating,
                    rowSelector + " #MPromoteBtn",
                    EventData.of("Action", "cellPromote").append("MemberTarget", memberUuid), false);
                event.addEventBinding(CustomUIEventBindingType.Activating,
                    rowSelector + " #MDemoteBtn",
                    EventData.of("Action", "cellDemote").append("MemberTarget", memberUuid), false);
                event.addEventBinding(CustomUIEventBindingType.Activating,
                    rowSelector + " #MKickBtn",
                    EventData.of("Action", "cellKick").append("MemberTarget", memberUuid), false);
            } else {
                cmd.set(rowSelector + " #MActions.Visible", false);
            }
            idx++;
        }

        // Bouton inviter visible seulement si permissions
        if (canInvite && !memberMgr.isFull(playerCell)) {
            event.addEventBinding(CustomUIEventBindingType.Activating, "#InviteBtn",
                EventData.of("Action", "cellShowInvite"), false);
            event.addEventBinding(CustomUIEventBindingType.Activating, "#InviteConfirmBtn",
                EventData.of("Action", "cellInvite").append("InviteName", "#InviteNameField.Value"), false);
        } else {
            cmd.set("#InviteZone.Visible", false);
        }
    }

    private void buildCellSettingsPage(UICommandBuilder cmd, UIEventBuilder event) {
        showSubPage(cmd);
        cmd.clear("#PageContent");
        cmd.set("#HeaderTitle.Text", "PARAMETRES");

        UUID uuid = playerRef.getUuid();
        var cellsApi = com.islandium.cells.api.CellsAPI.get();
        var playerCell = cellsApi != null ? cellsApi.getPlayerCell(uuid) : null;
        if (playerCell == null) {
            buildCellulePage(cmd, event);
            return;
        }

        cmd.append("#PageContent", "Pages/Prison/CellSettings.ui");

        // Niveau actuel
        var levelMgr = cellsApi.getLevelManager();
        var currentLevel = levelMgr.getLevel(playerCell.getCurrentLevel());
        String levelName = currentLevel != null ? currentLevel.getName() : "???";
        int zoneRadius = currentLevel != null ? currentLevel.getZoneRadius() : 0;
        int maxMem = currentLevel != null ? currentLevel.getMaxMembers() : playerCell.getMaxMembers();

        cmd.set("#SettingsLevelName.Text", "Nv." + playerCell.getCurrentLevel() + " - " + levelName);
        cmd.set("#SettingsLevelInfo.Text", "Taille: " + (zoneRadius * 2) + "x" + (zoneRadius * 2) + "  |  Max membres: " + maxMem);

        // Bouton upgrade
        var nextLevel = levelMgr.getNextLevel(playerCell.getCurrentLevel());
        if (nextLevel != null) {
            String upgradeText = "AMELIORER Nv." + nextLevel.getLevel() + " - " +
                com.islandium.prison.economy.SellService.formatMoney(nextLevel.getPrice());
            cmd.set("#UpgradeBtn.Text", upgradeText);
            event.addEventBinding(CustomUIEventBindingType.Activating, "#UpgradeBtn",
                EventData.of("Action", "cellUpgrade"), false);
        } else {
            cmd.set("#UpgradeZone.Visible", false);
        }

        // Bouton public/prive
        if (playerCell.isPublic()) {
            cmd.set("#TogglePublicBtn.Text", "Acces: PUBLIC - Cliquer pour rendre PRIVE");
        } else {
            cmd.set("#TogglePublicBtn.Text", "Acces: PRIVE - Cliquer pour rendre PUBLIC");
        }
        event.addEventBinding(CustomUIEventBindingType.Activating, "#TogglePublicBtn",
            EventData.of("Action", "cellTogglePublic"), false);

        // Bouton supprimer
        event.addEventBinding(CustomUIEventBindingType.Activating, "#DeleteCellBtn",
            EventData.of("Action", "cellDeleteConfirm"), false);
    }

    private void buildCellDeleteConfirmPage(UICommandBuilder cmd, UIEventBuilder event) {
        showSubPage(cmd);
        cmd.clear("#PageContent");
        cmd.set("#HeaderTitle.Text", "SUPPRIMER");

        cmd.append("#PageContent", "Pages/Prison/CellDeleteConfirm.ui");

        event.addEventBinding(CustomUIEventBindingType.Activating, "#ConfirmDeleteBtn",
            EventData.of("Action", "cellDeleteFinal"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#CancelDeleteBtn",
            EventData.of("Action", "cellSettings"), false);
    }

    // =========================================
    // DEFIS (CHALLENGES)
    // =========================================

    private void buildDefisPage(UICommandBuilder cmd, UIEventBuilder event) {
        buildDefisPageForRank(cmd, event, null);
    }

    private void buildDefisPageForRank(UICommandBuilder cmd, UIEventBuilder event, String rankOverride) {
        showSubPage(cmd);
        cmd.clear("#PageContent");

        UUID uuid = playerRef.getUuid();
        String playerRank = plugin.getRankManager().getPlayerRank(uuid);

        // Determiner le rang a afficher
        String displayRank = rankOverride != null ? rankOverride : playerRank;
        viewingDefiRank = displayRank;

        List<ChallengeDefinition> challenges = ChallengeRegistry.getChallengesForRank(displayRank);
        ChallengeManager challengeManager = plugin.getChallengeManager();

        int completedCount = challengeManager.getCompletedCount(uuid, displayRank);
        int totalCount = challenges.size();

        String counterText = completedCount + "/" + totalCount;
        boolean isCurrentRank = displayRank.equals(playerRank);
        cmd.set("#HeaderTitle.Text", "DEFIS - RANG " + displayRank + "  " + counterText + (isCurrentRank ? "  (actuel)" : ""));

        // Boutons navigation entre rangs
        int displayRankIndex = plugin.getRankManager().getRankIndex(displayRank);
        boolean hasPrev = displayRankIndex > 0;
        boolean hasNext = displayRankIndex < plugin.getRankManager().getRankIndex(playerRank);

        cmd.appendInline("#PageContent",
            "Group { Anchor: (Height: 32); LayoutMode: Left; " +
            (hasPrev ?
            "  TextButton #DefiPrev { Anchor: (Width: 120, Height: 28); " +
            "    Style: TextButtonStyle(Default: (Background: #1a2836, LabelStyle: (FontSize: 12, TextColor: #96a9be, HorizontalAlignment: Center, VerticalAlignment: Center)), " +
            "    Hovered: (Background: #253545, LabelStyle: (FontSize: 12, TextColor: #ffffff, HorizontalAlignment: Center, VerticalAlignment: Center))); } "
            : "") +
            "  Group { FlexWeight: 1; } " +
            "  Label #RangIndicator { Anchor: (Width: 150); Style: (FontSize: 13, TextColor: " + (isCurrentRank ? "#ff9800" : "#96a9be") + ", RenderBold: true, HorizontalAlignment: Center, VerticalAlignment: Center); } " +
            "  Group { FlexWeight: 1; } " +
            (hasNext ?
            "  TextButton #DefiNext { Anchor: (Width: 120, Height: 28); " +
            "    Style: TextButtonStyle(Default: (Background: #1a2836, LabelStyle: (FontSize: 12, TextColor: #96a9be, HorizontalAlignment: Center, VerticalAlignment: Center)), " +
            "    Hovered: (Background: #253545, LabelStyle: (FontSize: 12, TextColor: #ffffff, HorizontalAlignment: Center, VerticalAlignment: Center))); } "
            : "") +
            "}");

        cmd.set("#RangIndicator.Text", "Rang " + displayRank);

        if (hasPrev) {
            cmd.set("#DefiPrev.Text", "< Precedent");
            event.addEventBinding(CustomUIEventBindingType.Activating, "#DefiPrev", EventData.of("Action", "defiPrev"), false);
        }
        if (hasNext) {
            cmd.set("#DefiNext.Text", "Suivant >");
            event.addEventBinding(CustomUIEventBindingType.Activating, "#DefiNext", EventData.of("Action", "defiNext"), false);
        }

        // Grille 3x3 de challenges
        if (challenges.isEmpty()) {
            cmd.appendInline("#PageContent",
                "Label { Anchor: (Height: 30, Top: 10); Text: \"Aucun defi pour ce rang.\"; " +
                "Style: (FontSize: 13, TextColor: #808080); }");
            return;
        }

        // Build 3 rows of 3 challenges each
        for (int row = 0; row < 3; row++) {
            StringBuilder rowContent = new StringBuilder();
            rowContent.append("Group #DefiRow").append(row).append(" { Anchor: (Height: 130, Top: 8); LayoutMode: Left; ");

            for (int col = 0; col < 3; col++) {
                int idx = row * 3 + col;
                if (idx >= challenges.size()) {
                    // Empty spacer
                    rowContent.append("Group { FlexWeight: 1; } ");
                    if (col < 2) rowContent.append("Group { Anchor: (Width: 5); } ");
                    continue;
                }

                ChallengeDefinition def = challenges.get(idx);
                PlayerChallengeProgress.ChallengeProgressData data = challengeManager.getProgressData(uuid, def.getId());
                boolean isComplete = data.completedTier >= def.getTierCount();
                boolean isSubmitType = def.getType() == ChallengeType.SUBMIT_ITEMS;

                String cardBg = isComplete ? "#1a2a1a" : "#151d28";
                String cardHoverBg = isComplete ? "#1a2a1a" : "#1e2a38";
                String titleColor = isComplete ? "#66bb6a" : "#ffd700";
                String cardId = "Defi" + idx;

                if (isSubmitType && !isComplete) {
                    // SUBMIT_ITEMS card: clickable Button with hover
                    rowContent.append("Button #").append(cardId)
                        .append(" { FlexWeight: 1; Style: ButtonStyle(Default: (Background: ").append(cardBg)
                        .append("), Hovered: (Background: ").append(cardHoverBg).append(")); ")
                        .append("  Group { LayoutMode: Top; Padding: (Horizontal: 12, Vertical: 8); ")
                        .append("    Label #Title { Anchor: (Height: 24); Style: (FontSize: 14, TextColor: ").append(titleColor).append(", RenderBold: true); } ")
                        .append("    Label #Desc { Anchor: (Height: 18); Style: (FontSize: 11, TextColor: #7c8b99); } ")
                        .append("    Label #Items { Anchor: (Height: 36, Top: 2); Style: (FontSize: 11, TextColor: #96a9be); } ")
                        .append("    Group { Anchor: (Height: 22, Top: 2); LayoutMode: Left; ")
                        .append("      Label #Rew { FlexWeight: 1; Style: (FontSize: 12, TextColor: #66bb6a, VerticalAlignment: Center); } ")
                        .append("      Label #Action { Anchor: (Width: 120); Style: (FontSize: 11, TextColor: #ff9800, RenderBold: true, VerticalAlignment: Center); } ")
                        .append("    } ")
                        .append("  } ")
                        .append("} ");
                } else {
                    // Standard card (non-clickable Group)
                    rowContent.append("Group #").append(cardId)
                        .append(" { FlexWeight: 1; Background: (Color: ").append(cardBg).append("); Padding: (Horizontal: 12, Vertical: 8); LayoutMode: Top; ")
                        .append("  Label #Title { Anchor: (Height: 26); Style: (FontSize: 14, TextColor: ").append(titleColor).append(", RenderBold: true); } ")
                        .append("  Label #Desc { Anchor: (Height: 20); Style: (FontSize: 12, TextColor: #7c8b99); } ")
                        .append("  Label #Bar { Anchor: (Height: 22, Top: 4); Style: (FontSize: 13, TextColor: #96a9be); } ")
                        .append("  Group { Anchor: (Height: 22, Top: 2); LayoutMode: Left; ")
                        .append("    Label #Prog { FlexWeight: 1; Style: (FontSize: 12, TextColor: #ffffff, VerticalAlignment: Center); } ")
                        .append("    Label #Rew { Anchor: (Width: 100); Style: (FontSize: 12, TextColor: #66bb6a, VerticalAlignment: Center); } ")
                        .append("  } ")
                        .append("} ");
                }

                if (col < 2) rowContent.append("Group { Anchor: (Width: 5); } ");
            }

            rowContent.append("}");
            cmd.appendInline("#PageContent", rowContent.toString());

            // Set values for each card in this row
            for (int col = 0; col < 3; col++) {
                int idx = row * 3 + col;
                if (idx >= challenges.size()) continue;

                ChallengeDefinition def = challenges.get(idx);
                PlayerChallengeProgress.ChallengeProgressData data = challengeManager.getProgressData(uuid, def.getId());
                boolean isComplete = data.completedTier >= def.getTierCount();
                boolean isSubmitType = def.getType() == ChallengeType.SUBMIT_ITEMS;
                String cardSelector = "#DefiRow" + row + " #Defi" + idx;

                String tierInfo = def.getTierCount() > 1
                    ? " (" + Math.min(data.completedTier + 1, def.getTierCount()) + "/" + def.getTierCount() + ")"
                    : "";

                cmd.set(cardSelector + " #Title.Text", def.getDisplayName() + tierInfo);
                cmd.set(cardSelector + " #Desc.Text", def.getDescription());

                if (isSubmitType && !isComplete) {
                    // SUBMIT_ITEMS: show required items list + click action
                    ChallengeDefinition.ChallengeTier tier = def.getTiers().get(data.completedTier);
                    String rewardText = "-> " + SellService.formatMoney(tier.reward());

                    // Build items description
                    StringBuilder itemsText = new StringBuilder();
                    for (int i = 0; i < tier.requiredItems().size(); i++) {
                        ChallengeDefinition.RequiredItem ri = tier.requiredItems().get(i);
                        if (i > 0) itemsText.append(", ");
                        itemsText.append(ri.quantity()).append("x ").append(shortItemName(ri.itemId()));
                    }
                    if (tier.requiredItems().isEmpty()) {
                        itemsText.append("(aucun item requis)");
                    }

                    cmd.set(cardSelector + " #Items.Text", itemsText.toString());
                    cmd.set(cardSelector + " #Rew.Text", rewardText);
                    cmd.set(cardSelector + " #Action.Text", "CLIQUER POUR VALIDER");

                    // Event binding for click
                    event.addEventBinding(CustomUIEventBindingType.Activating, cardSelector,
                        EventData.of("Action", "submitChallenge").append("ChallengeId", def.getId()), false);
                } else if (isSubmitType && isComplete) {
                    // SUBMIT_ITEMS complete: show as completed (uses standard card layout)
                    cmd.set(cardSelector + " #Bar.Text", buildProgressBar(1, 1, true));
                    cmd.set(cardSelector + " #Prog.Text", "COMPLETE");
                    cmd.set(cardSelector + " #Rew.Text", "");
                } else {
                    // Standard challenge: progress bar
                    long currentValue = data.currentValue;
                    long currentTarget;
                    String progressText;
                    String rewardText;

                    if (isComplete) {
                        currentTarget = def.getFinalTarget();
                        progressText = "COMPLETE";
                        rewardText = "";
                    } else {
                        ChallengeDefinition.ChallengeTier tier = def.getTiers().get(data.completedTier);
                        currentTarget = tier.target();
                        progressText = formatNumber(currentValue) + "/" + formatNumber(currentTarget);
                        rewardText = "-> " + SellService.formatMoney(tier.reward());
                    }

                    String progressBar = buildProgressBar(currentValue, currentTarget, isComplete);
                    cmd.set(cardSelector + " #Bar.Text", progressBar);
                    cmd.set(cardSelector + " #Prog.Text", progressText);
                    cmd.set(cardSelector + " #Rew.Text", rewardText);
                }
            }
        }

        // Bouton RANKUP en bas (seulement pour le rang actuel)
        if (isCurrentRank) {
            PrisonConfig.RankInfo nextRank = plugin.getRankManager().getNextRankInfo(uuid);
            boolean allChallengesDone = completedCount >= totalCount;

            if (nextRank != null) {
                BigDecimal rankupPrice = plugin.getRankManager().getRankupPrice(uuid, nextRank);
                String priceText = "Prix: " + SellService.formatMoney(rankupPrice);
                String btnBg = allChallengesDone ? "#2a5f2a" : "#3a2a2a";
                String btnHover = allChallengesDone ? "#3a7f3a" : "#4a3a3a";
                String btnTextColor = allChallengesDone ? "#ffffff" : "#ff6666";

                cmd.appendInline("#PageContent",
                    "Group { Anchor: (Height: 65, Top: 10); LayoutMode: Top; " +
                    "  Group { Anchor: (Height: 28); LayoutMode: Left; " +
                    "    Group { FlexWeight: 1; } " +
                    "    Label #RankupPrice { Anchor: (Width: 200); Style: (FontSize: 15, TextColor: #ffd700, RenderBold: true, HorizontalAlignment: Center, VerticalAlignment: Center); } " +
                    "    Group { FlexWeight: 1; } " +
                    "  } " +
                    "  Group { Anchor: (Height: 40, Top: 4); LayoutMode: Left; " +
                    "    Group { FlexWeight: 1; } " +
                    "    TextButton #DefiRankupBtn { Anchor: (Width: 200, Height: 38); " +
                    "      Style: TextButtonStyle(Default: (Background: " + btnBg + ", LabelStyle: (FontSize: 15, TextColor: " + btnTextColor + ", RenderBold: true, HorizontalAlignment: Center, VerticalAlignment: Center)), " +
                    "      Hovered: (Background: " + btnHover + ", LabelStyle: (FontSize: 15, TextColor: #ffffff, RenderBold: true, HorizontalAlignment: Center, VerticalAlignment: Center))); } " +
                    "    Group { FlexWeight: 1; } " +
                    "  } " +
                    "}");

                cmd.set("#RankupPrice.Text", priceText);
                cmd.set("#DefiRankupBtn.Text", allChallengesDone ? "RANKUP" : "DEFIS INCOMPLETS");
                event.addEventBinding(CustomUIEventBindingType.Activating, "#DefiRankupBtn", EventData.of("Action", "defiRankup"), false);
            } else if (plugin.getRankManager().canPrestige(uuid)) {
                cmd.appendInline("#PageContent",
                    "Group { Anchor: (Height: 50, Top: 10); LayoutMode: Left; " +
                    "  Group { FlexWeight: 1; } " +
                    "  TextButton #DefiPrestigeBtn { Anchor: (Width: 200, Height: 42); " +
                    "    Style: TextButtonStyle(Default: (Background: #5f2a5f, LabelStyle: (FontSize: 15, TextColor: #ff80ff, RenderBold: true, VerticalAlignment: Center)), " +
                    "    Hovered: (Background: #7f3a7f, LabelStyle: (FontSize: 15, TextColor: #ffffff, RenderBold: true, VerticalAlignment: Center))); } " +
                    "  Group { FlexWeight: 1; } " +
                    "}");
                cmd.set("#DefiPrestigeBtn.Text", "PRESTIGE");
                event.addEventBinding(CustomUIEventBindingType.Activating, "#DefiPrestigeBtn", EventData.of("Action", "defiPrestige"), false);
            } else {
                cmd.appendInline("#PageContent",
                    "Group { Anchor: (Height: 30, Top: 10); LayoutMode: Left; " +
                    "  Group { FlexWeight: 1; } " +
                    "  Label { Anchor: (Width: 250); Text: \"Rang maximum atteint!\"; Style: (FontSize: 14, TextColor: #ffd700, RenderBold: true, VerticalAlignment: Center); } " +
                    "  Group { FlexWeight: 1; } " +
                    "}");
            }
        }

        // Bouton admin config challenges dans le header
        boolean isAdmin = false;
        try {
            var perms = com.hypixel.hytale.server.core.permissions.PermissionsModule.get();
            isAdmin = perms.getGroupsForUser(uuid).contains("OP")
                || perms.hasPermission(uuid, "prison.admin")
                || perms.hasPermission(uuid, "*");
        } catch (Exception ignored) {}

        if (isAdmin) {
            cmd.set("#HeaderConfigBtn.Visible", true);
            cmd.set("#HeaderConfigBtn.Text", "CONFIG CHALLENGES");
            event.addEventBinding(CustomUIEventBindingType.Activating, "#HeaderConfigBtn", EventData.of("Action", "openChallengeConfig"), false);
        }
    }

    /**
     * Construit une barre de progression ASCII: [####------]
     */
    private String buildProgressBar(long current, long target, boolean complete) {
        int barLength = 20;
        if (complete) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < barLength; i++) sb.append("#");
            sb.append("]");
            return sb.toString();
        }
        if (target <= 0) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < barLength; i++) sb.append("-");
            sb.append("]");
            return sb.toString();
        }
        int filled = (int) Math.min(barLength, (current * barLength) / target);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < barLength; i++) {
            sb.append(i < filled ? "#" : "-");
        }
        sb.append("]");
        return sb.toString();
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
                case "upgrades" -> { currentPage = "upgrades"; buildUpgradesPage(cmd, event); }
                case "classement" -> { currentPage = "classement"; buildClassementPage(cmd, event); }
                case "cellule" -> { currentPage = "cellule"; buildCellulePage(cmd, event); }
                case "vendre" -> { currentPage = "vendre"; buildVendrePage(cmd, event, player); }
                case "defis" -> { currentPage = "defis"; buildDefisPage(cmd, event); }
            }
            sendUpdate(cmd, event, false);
            return;
        }

        // Bouton retour
        if (data.action != null && data.action.equals("back")) {
            // Sous-pages cellule -> retour au hub cellule
            if ("cellMembers".equals(currentPage) || "cellSettings".equals(currentPage) || "cellDeleteConfirm".equals(currentPage)) {
                currentPage = "cellule";
                buildCellulePage(cmd, event);
                sendUpdate(cmd, event, false);
                return;
            }
            // Hub -> retourner au menu principal
            if ("hub".equals(currentPage)) {
                close();
                try {
                    var world = ((EntityStore) store.getExternalData()).getWorld();
                    var menuPage = new com.islandium.core.ui.pages.MenuPage(playerRef, plugin.getCore(), world.getName());
                    player.getPageManager().openCustomPage(ref, store, menuPage);
                } catch (Exception e) {
                    plugin.getLogger().at(Level.WARNING).log("[Prison] Could not reopen main menu: " + e.getMessage());
                }
                return;
            }
            currentPage = "hub";
            showHub(cmd);
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
                case "close" -> {
                    close();
                    return;
                }
                case "defiPrev", "defiNext" -> {
                    if (viewingDefiRank != null) {
                        int idx = plugin.getRankManager().getRankIndex(viewingDefiRank);
                        int newIdx = data.action.equals("defiPrev") ? idx - 1 : idx + 1;
                        String playerRankNow = plugin.getRankManager().getPlayerRank(uuid);
                        int maxIdx = plugin.getRankManager().getRankIndex(playerRankNow);
                        if (newIdx >= 0 && newIdx <= maxIdx) {
                            String newRank = rankFromIndex(newIdx);
                            buildDefisPageForRank(cmd, event, newRank);
                            sendUpdate(cmd, event, false);
                        }
                    }
                    return;
                }
                case "openSellConfig" -> {
                    plugin.getUIManager().openSellConfig(player);
                    return;
                }
                case "openChallengeConfig" -> {
                    plugin.getUIManager().openChallengeConfig(player);
                    return;
                }
                case "sellAll" -> {
                    SellService.SellResult result = plugin.getSellService().sellFromInventory(uuid, player, null);
                    if (result.isEmpty()) {
                        player.sendMessage(Message.raw("Rien a vendre dans ton inventaire!"));
                    } else {
                        player.sendMessage(Message.raw("Vendu " + result.getTotalBlocksSold() + " blocs pour " + SellService.formatMoney(result.getTotalEarned()) + "!"));
                    }
                    // Rebuild la page vendre pour rafraichir
                    buildVendrePage(cmd, event, player);
                    sendUpdate(cmd, event, false);
                    return;
                }
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
                        case CHALLENGES_INCOMPLETE -> {
                            int completed = plugin.getChallengeManager().getCompletedCount(uuid, plugin.getRankManager().getPlayerRank(uuid));
                            player.sendMessage(Message.raw("Defis incomplets! (" + completed + "/9) - Complete tes defis pour rankup."));
                        }
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
                        // Check if it's because of challenges
                        String currentRankId = plugin.getRankManager().getPlayerRank(uuid);
                        boolean challengesDone = plugin.getChallengeManager().areAllChallengesComplete(uuid, currentRankId);
                        if (!challengesDone) {
                            int completed = plugin.getChallengeManager().getCompletedCount(uuid, currentRankId);
                            player.sendMessage(Message.raw("Defis incomplets! (" + completed + "/9) - Complete tes defis pour rankup."));
                        } else {
                            player.sendMessage(Message.raw("Impossible de rankup (pas assez d'argent ou rang max)!"));
                        }
                    }
                    return;
                }
                case "submitChallenge" -> {
                    if (data.challengeId != null) {
                        ChallengeManager cm = plugin.getChallengeManager();
                        int result = cm.trySubmitItems(uuid, data.challengeId, player);
                        switch (result) {
                            case 0 -> player.sendMessage(Message.raw("Challenge valide! Items soumis avec succes."));
                            case 1 -> {
                                List<String> missing = cm.getMissingItems(uuid, data.challengeId, player);
                                player.sendMessage(Message.raw("Items manquants: " + String.join(", ", missing)));
                            }
                            case 2 -> player.sendMessage(Message.raw("Ce challenge n'est pas de type soumission."));
                            case 3 -> player.sendMessage(Message.raw("Ce challenge est deja termine!"));
                        }
                        // Refresh defis page
                        buildDefisPageForRank(cmd, event, viewingDefiRank);
                        sendUpdate(cmd, event, false);
                    }
                    return;
                }
                case "defiRankup" -> {
                    PrisonRankManager.RankupResult result = plugin.getRankManager().rankup(uuid);
                    switch (result) {
                        case SUCCESS -> {
                            String newRank = plugin.getRankManager().getPlayerRank(uuid);
                            player.sendMessage(Message.raw("Rankup! Tu es maintenant rang " + newRank + "!"));
                            buildDefisPage(cmd, event);
                            sendUpdate(cmd, event, false);
                        }
                        case NOT_ENOUGH_MONEY -> player.sendMessage(Message.raw("Pas assez d'argent!"));
                        case MAX_RANK -> player.sendMessage(Message.raw("Tu es deja au rang maximum!"));
                        case CHALLENGES_INCOMPLETE -> {
                            int completed = plugin.getChallengeManager().getCompletedCount(uuid, plugin.getRankManager().getPlayerRank(uuid));
                            player.sendMessage(Message.raw("Defis incomplets! (" + completed + "/9) - Complete tes defis pour rankup."));
                        }
                    }
                    return;
                }
                case "defiPrestige" -> {
                    if (plugin.getRankManager().prestige(uuid)) {
                        int newPrestige = plugin.getRankManager().getPlayerPrestige(uuid);
                        player.sendMessage(Message.raw("Prestige! Tu es maintenant Prestige " + newPrestige + "!"));
                        buildDefisPage(cmd, event);
                        sendUpdate(cmd, event, false);
                    } else {
                        player.sendMessage(Message.raw("Tu dois etre rang FREE pour prestige!"));
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
                    var cellsApiTp = com.islandium.cells.api.CellsAPI.get();
                    var cell = cellsApiTp != null ? cellsApiTp.getPlayerCell(uuid) : null;
                    if (cell != null) {
                        IslandiumPlayer islandiumPlayer = plugin.getCore().getPlayerManager().getOnlinePlayer(uuid).orElse(null);
                        if (islandiumPlayer != null) {
                            String cellWorldName = com.islandium.cells.CellsPlugin.get().getConfig().getWorldName();
                            var loc = com.islandium.core.api.location.ServerLocation.of(
                                plugin.getCore().getServerName(), cellWorldName,
                                cell.getSpawnX(), cell.getSpawnY(), cell.getSpawnZ(), 0, 0
                            );
                            plugin.getCore().getTeleportService().teleportWithWarmup(
                                islandiumPlayer, loc,
                                () -> player.sendMessage(Message.raw("Teleporte a ta cellule!"))
                            );
                        }
                    }
                    return;
                }
                case "buyCell" -> {
                    var cellsApiBuy = com.islandium.cells.api.CellsAPI.get();
                    if (cellsApiBuy == null) {
                        player.sendMessage(Message.raw("Systeme de cellules non disponible!"));
                        return;
                    }
                    String playerName = playerRef.getUsername();
                    var cellResult = cellsApiBuy.getCellManager().createCell(uuid, playerName);
                    switch (cellResult) {
                        case SUCCESS -> {
                            player.sendMessage(Message.raw("Cellule achetee!"));
                            buildCellulePage(cmd, event);
                            sendUpdate(cmd, event, false);
                        }
                        case NOT_ENOUGH_MONEY -> player.sendMessage(Message.raw("Pas assez d'argent!"));
                        case ALREADY_HAS_CELL -> player.sendMessage(Message.raw("Tu as deja une cellule!"));
                        case NO_LEVELS_CONFIGURED -> player.sendMessage(Message.raw("Aucun niveau de cellule configure!"));
                        case ECONOMY_ERROR -> player.sendMessage(Message.raw("Erreur economique, reessaie!"));
                    }
                    return;
                }
                case "cellMembers" -> {
                    currentPage = "cellMembers";
                    buildCellMembersPage(cmd, event);
                    sendUpdate(cmd, event, false);
                    return;
                }
                case "cellSettings" -> {
                    currentPage = "cellSettings";
                    buildCellSettingsPage(cmd, event);
                    sendUpdate(cmd, event, false);
                    return;
                }
                case "cellUpgrade" -> {
                    var cellsApiUp = com.islandium.cells.api.CellsAPI.get();
                    if (cellsApiUp == null) return;
                    var upResult = cellsApiUp.getCellManager().upgradeCell(uuid);
                    switch (upResult) {
                        case SUCCESS -> {
                            player.sendMessage(Message.raw("Cellule amelioree!"));
                            buildCellSettingsPage(cmd, event);
                            sendUpdate(cmd, event, false);
                        }
                        case NOT_ENOUGH_MONEY -> player.sendMessage(Message.raw("Pas assez d'argent!"));
                        case MAX_LEVEL -> player.sendMessage(Message.raw("Niveau maximum atteint!"));
                        case NO_CELL -> player.sendMessage(Message.raw("Tu n'as pas de cellule!"));
                        case ECONOMY_ERROR -> player.sendMessage(Message.raw("Erreur economique, reessaie!"));
                    }
                    return;
                }
                case "cellTogglePublic" -> {
                    var cellsApiPub = com.islandium.cells.api.CellsAPI.get();
                    if (cellsApiPub == null) return;
                    boolean toggled = cellsApiPub.getCellManager().togglePublic(uuid);
                    if (toggled) {
                        var cellNow = cellsApiPub.getPlayerCell(uuid);
                        player.sendMessage(Message.raw("Cellule " + (cellNow != null && cellNow.isPublic() ? "publique" : "privee") + "!"));
                        buildCellSettingsPage(cmd, event);
                        sendUpdate(cmd, event, false);
                    }
                    return;
                }
                case "cellDeleteConfirm" -> {
                    currentPage = "cellDeleteConfirm";
                    buildCellDeleteConfirmPage(cmd, event);
                    sendUpdate(cmd, event, false);
                    return;
                }
                case "cellDeleteFinal" -> {
                    var cellsApiDel = com.islandium.cells.api.CellsAPI.get();
                    if (cellsApiDel == null) return;
                    var cellToDel = cellsApiDel.getPlayerCell(uuid);
                    if (cellToDel != null) {
                        boolean deleted = cellsApiDel.getCellManager().deleteCell(cellToDel.getId());
                        if (deleted) {
                            player.sendMessage(Message.raw("Cellule supprimee!"));
                        }
                    }
                    buildCellulePage(cmd, event);
                    sendUpdate(cmd, event, false);
                    return;
                }
                case "cellShowInvite" -> {
                    cmd.set("#InviteForm.Visible", true);
                    sendUpdate(cmd, event, false);
                    return;
                }
                case "cellInvite" -> {
                    if (data.inviteName == null || data.inviteName.isBlank()) {
                        player.sendMessage(Message.raw("Entre un nom de joueur!"));
                        return;
                    }
                    var cellsApiInv = com.islandium.cells.api.CellsAPI.get();
                    if (cellsApiInv == null) return;
                    var cellInv = cellsApiInv.getPlayerCell(uuid);
                    if (cellInv == null) return;

                    String targetName = data.inviteName.trim();
                    // Lookup async du joueur
                    plugin.getCore().getPlayerManager().getPlayer(targetName).thenAccept(targetOpt -> {
                        if (targetOpt.isEmpty()) {
                            player.sendMessage(Message.raw("Joueur '" + targetName + "' introuvable!"));
                            return;
                        }
                        var target = targetOpt.get();
                        UUID targetUuid = target.getUniqueId();
                        if (targetUuid.equals(uuid)) {
                            player.sendMessage(Message.raw("Tu ne peux pas t'inviter toi-meme!"));
                            return;
                        }
                        var memberMgr = cellsApiInv.getMemberManager();
                        if (memberMgr.isFull(cellInv)) {
                            player.sendMessage(Message.raw("La cellule est pleine!"));
                            return;
                        }
                        boolean added = memberMgr.addMember(cellInv.getId(), targetUuid, target.getName(),
                            com.islandium.cells.cell.CellRole.VISITOR, uuid);
                        if (added) {
                            player.sendMessage(Message.raw(target.getName() + " a ete invite comme VISITOR!"));
                            // Refresh la page
                            UICommandBuilder cmdRefresh = new UICommandBuilder();
                            UIEventBuilder eventRefresh = new UIEventBuilder();
                            buildCellMembersPage(cmdRefresh, eventRefresh);
                            sendUpdate(cmdRefresh, eventRefresh, false);
                        } else {
                            player.sendMessage(Message.raw(target.getName() + " est deja membre!"));
                        }
                    });
                    return;
                }
                case "cellPromote" -> {
                    if (data.memberTarget == null) return;
                    var cellsApiProm = com.islandium.cells.api.CellsAPI.get();
                    if (cellsApiProm == null) return;
                    var cellProm = cellsApiProm.getPlayerCell(uuid);
                    if (cellProm == null || !cellProm.isOwner(uuid)) return;

                    UUID targetUuid = UUID.fromString(data.memberTarget);
                    var memberMgr = cellsApiProm.getMemberManager();
                    var currentRole = memberMgr.getRole(cellProm.getId(), targetUuid);
                    if (currentRole != null && currentRole.promote() != null) {
                        memberMgr.setRole(cellProm.getId(), targetUuid, currentRole.promote());
                        player.sendMessage(Message.raw("Membre promu a " + currentRole.promote().name() + "!"));
                        buildCellMembersPage(cmd, event);
                        sendUpdate(cmd, event, false);
                    } else {
                        player.sendMessage(Message.raw("Impossible de promouvoir ce membre!"));
                    }
                    return;
                }
                case "cellDemote" -> {
                    if (data.memberTarget == null) return;
                    var cellsApiDem = com.islandium.cells.api.CellsAPI.get();
                    if (cellsApiDem == null) return;
                    var cellDem = cellsApiDem.getPlayerCell(uuid);
                    if (cellDem == null || !cellDem.isOwner(uuid)) return;

                    UUID targetUuid = UUID.fromString(data.memberTarget);
                    var memberMgr = cellsApiDem.getMemberManager();
                    var currentRole = memberMgr.getRole(cellDem.getId(), targetUuid);
                    if (currentRole != null && currentRole.demote() != null) {
                        memberMgr.setRole(cellDem.getId(), targetUuid, currentRole.demote());
                        player.sendMessage(Message.raw("Membre retrograde a " + currentRole.demote().name() + "!"));
                        buildCellMembersPage(cmd, event);
                        sendUpdate(cmd, event, false);
                    } else {
                        player.sendMessage(Message.raw("Impossible de retrograder ce membre!"));
                    }
                    return;
                }
                case "cellKick" -> {
                    if (data.memberTarget == null) return;
                    var cellsApiKick = com.islandium.cells.api.CellsAPI.get();
                    if (cellsApiKick == null) return;
                    var cellKick = cellsApiKick.getPlayerCell(uuid);
                    if (cellKick == null) return;

                    UUID targetUuid = UUID.fromString(data.memberTarget);
                    if (!cellsApiKick.getMemberManager().canKick(cellKick, uuid, targetUuid)) {
                        player.sendMessage(Message.raw("Tu n'as pas la permission!"));
                        return;
                    }
                    boolean removed = cellsApiKick.getMemberManager().removeMember(cellKick.getId(), targetUuid);
                    if (removed) {
                        player.sendMessage(Message.raw("Membre expulse!"));
                        buildCellMembersPage(cmd, event);
                        sendUpdate(cmd, event, false);
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

    private String rankFromIndex(int index) {
        if (index == 26) return "FREE";
        if (index >= 0 && index <= 25) return String.valueOf((char) ('A' + index));
        return "A";
    }

    /**
     * Raccourcit un item ID en enlevant le namespace (ex: "hytale:cobblestone" -> "cobblestone").
     */
    private String shortItemName(String itemId) {
        if (itemId == null) return "???";
        int colon = itemId.indexOf(':');
        return colon >= 0 ? itemId.substring(colon + 1) : itemId;
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
                .addField(new KeyedCodec<>("ChallengeId", Codec.STRING), (d, v) -> d.challengeId = v, d -> d.challengeId)
                .addField(new KeyedCodec<>("MemberTarget", Codec.STRING), (d, v) -> d.memberTarget = v, d -> d.memberTarget)
                .addField(new KeyedCodec<>("InviteName", Codec.STRING), (d, v) -> d.inviteName = v, d -> d.inviteName)
                .build();

        public String action;
        public String navigate;
        public String tpMine;
        public String challengeId;
        public String memberTarget;
        public String inviteName;
    }
}
