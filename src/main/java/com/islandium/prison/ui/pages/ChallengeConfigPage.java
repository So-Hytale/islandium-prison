package com.islandium.prison.ui.pages;

import com.islandium.prison.PrisonPlugin;
import com.islandium.prison.challenge.ChallengeDefinition;
import com.islandium.prison.challenge.ChallengeRegistry;
import com.islandium.prison.challenge.ChallengeType;
import com.islandium.prison.economy.SellService;
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
import java.util.List;
import java.util.logging.Level;

/**
 * Page admin pour visualiser et configurer les challenges.
 * Accessible via le bouton CONFIG CHALLENGES dans la page defis (admin only).
 */
public class ChallengeConfigPage extends InteractiveCustomUIPage<ChallengeConfigPage.PageData> {

    private final PrisonPlugin plugin;
    private final PlayerRef playerRef;
    private String viewingRank = "A";

    public ChallengeConfigPage(@Nonnull PlayerRef playerRef, PrisonPlugin plugin) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageData.CODEC);
        this.plugin = plugin;
        this.playerRef = playerRef;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder event, @Nonnull Store<EntityStore> store) {
        cmd.append("Pages/Prison/ChallengeConfigPage.ui");

        event.addEventBinding(CustomUIEventBindingType.Activating, "#CloseBtn", EventData.of("Action", "close"), false);

        buildRankList(cmd, event);
    }

    private void buildRankList(UICommandBuilder cmd, UIEventBuilder event) {
        cmd.clear("#PageContent");
        cmd.set("#HeaderTitle.Text", "CHALLENGES - RANG " + viewingRank);

        // Navigation entre rangs
        cmd.appendInline("#PageContent",
            "Group { Anchor: (Height: 35); LayoutMode: Left; " +
            "  TextButton #PrevRank { Anchor: (Width: 100, Height: 30); " +
            "    Style: TextButtonStyle(Default: (Background: #1a2836, LabelStyle: (FontSize: 12, TextColor: #96a9be, VerticalAlignment: Center)), " +
            "    Hovered: (Background: #253545, LabelStyle: (FontSize: 12, TextColor: #ffffff, VerticalAlignment: Center))); } " +
            "  Group { FlexWeight: 1; } " +
            "  Label #RankLabel { Anchor: (Width: 120); Style: (FontSize: 16, TextColor: #ffd700, RenderBold: true, VerticalAlignment: Center); } " +
            "  Group { FlexWeight: 1; } " +
            "  TextButton #NextRank { Anchor: (Width: 100, Height: 30); " +
            "    Style: TextButtonStyle(Default: (Background: #1a2836, LabelStyle: (FontSize: 12, TextColor: #96a9be, VerticalAlignment: Center)), " +
            "    Hovered: (Background: #253545, LabelStyle: (FontSize: 12, TextColor: #ffffff, VerticalAlignment: Center))); } " +
            "}");

        cmd.set("#PrevRank.Text", "< Precedent");
        cmd.set("#NextRank.Text", "Suivant >");
        cmd.set("#RankLabel.Text", "Rang " + viewingRank);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#PrevRank", EventData.of("Action", "prevRank"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#NextRank", EventData.of("Action", "nextRank"), false);

        // Header colonnes
        cmd.appendInline("#PageContent",
            "Group { Anchor: (Height: 28, Top: 8); LayoutMode: Left; Padding: (Horizontal: 8); Background: (Color: #1a2836); " +
            "  Label { Anchor: (Width: 30); Text: \"#\"; Style: (FontSize: 10, TextColor: #7c8b99, RenderBold: true, VerticalAlignment: Center); } " +
            "  Label { Anchor: (Width: 150); Text: \"Nom\"; Style: (FontSize: 10, TextColor: #7c8b99, RenderBold: true, VerticalAlignment: Center); } " +
            "  Label { Anchor: (Width: 120); Text: \"Type\"; Style: (FontSize: 10, TextColor: #7c8b99, RenderBold: true, VerticalAlignment: Center); } " +
            "  Label { Anchor: (Width: 120); Text: \"Cible bloc\"; Style: (FontSize: 10, TextColor: #7c8b99, RenderBold: true, VerticalAlignment: Center); } " +
            "  Label { FlexWeight: 1; Text: \"Paliers (cible -> recompense)\"; Style: (FontSize: 10, TextColor: #7c8b99, RenderBold: true, VerticalAlignment: Center); } " +
            "}");

        // Liste des challenges pour ce rang
        List<ChallengeDefinition> challenges = ChallengeRegistry.getChallengesForRank(viewingRank);

        if (challenges.isEmpty()) {
            cmd.appendInline("#PageContent",
                "Label { Anchor: (Height: 30, Top: 10); Text: \"Aucun challenge defini pour ce rang.\"; " +
                "Style: (FontSize: 13, TextColor: #808080); }");
            return;
        }

        for (int i = 0; i < challenges.size(); i++) {
            ChallengeDefinition def = challenges.get(i);
            String bgColor = i % 2 == 0 ? "#111b27" : "#151d28";
            String rowId = "CRow" + i;

            // Paliers en texte
            StringBuilder tiersText = new StringBuilder();
            for (int t = 0; t < def.getTiers().size(); t++) {
                ChallengeDefinition.ChallengeTier tier = def.getTiers().get(t);
                if (t > 0) tiersText.append(" | ");
                tiersText.append(formatNumber(tier.target())).append(" -> ").append(SellService.formatMoney(tier.reward()));
            }

            String targetBlock = def.getTargetBlockId() != null ? def.getTargetBlockId() : "-";
            String typeColor = getTypeColor(def.getType());

            cmd.appendInline("#PageContent",
                "Group #" + rowId + " { Anchor: (Height: 32, Top: 2); LayoutMode: Left; Padding: (Horizontal: 8); Background: (Color: " + bgColor + "); " +
                "  Label #Idx { Anchor: (Width: 30); Style: (FontSize: 11, TextColor: #96a9be, VerticalAlignment: Center); } " +
                "  Label #Name { Anchor: (Width: 150); Style: (FontSize: 11, TextColor: #ffd700, RenderBold: true, VerticalAlignment: Center); } " +
                "  Label #Type { Anchor: (Width: 120); Style: (FontSize: 10, TextColor: " + typeColor + ", VerticalAlignment: Center); } " +
                "  Label #Block { Anchor: (Width: 120); Style: (FontSize: 10, TextColor: #96a9be, VerticalAlignment: Center); } " +
                "  Label #Tiers { FlexWeight: 1; Style: (FontSize: 10, TextColor: #66bb6a, VerticalAlignment: Center); } " +
                "}");

            cmd.set("#" + rowId + " #Idx.Text", String.valueOf(i + 1));
            cmd.set("#" + rowId + " #Name.Text", def.getDisplayName());
            cmd.set("#" + rowId + " #Type.Text", def.getType().name());
            cmd.set("#" + rowId + " #Block.Text", targetBlock);
            cmd.set("#" + rowId + " #Tiers.Text", tiersText.toString());
        }

        // Resume
        cmd.appendInline("#PageContent",
            "Group { Anchor: (Height: 30, Top: 15); Background: (Color: #1a2836); Padding: (Horizontal: 15); LayoutMode: Left; " +
            "  Label #Summary { FlexWeight: 1; Style: (FontSize: 12, TextColor: #96a9be, VerticalAlignment: Center); } " +
            "}");
        cmd.set("#Summary.Text", challenges.size() + " challenges pour le rang " + viewingRank);
    }

    private String getTypeColor(ChallengeType type) {
        return switch (type) {
            case MINE_BLOCKS, MINE_SPECIFIC -> "#4fc3f7";
            case EARN_MONEY, ACCUMULATE_BALANCE -> "#66bb6a";
            case SELL_ITEMS -> "#ff9800";
            case BUY_FORTUNE, BUY_EFFICIENCY, BUY_AUTOSELL -> "#ab47bc";
            case SPEND_MONEY -> "#ef5350";
        };
    }

    private String formatNumber(long number) {
        if (number >= 1_000_000) {
            return String.format("%.1fM", number / 1_000_000.0);
        } else if (number >= 1_000) {
            return String.format("%.1fK", number / 1_000.0);
        }
        return String.valueOf(number);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        super.handleDataEvent(ref, store, data);

        if (data.action == null) return;

        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder event = new UIEventBuilder();

        switch (data.action) {
            case "close" -> {
                close();
                return;
            }
            case "prevRank" -> {
                int idx = rankToIndex(viewingRank);
                if (idx > 0) {
                    viewingRank = indexToRank(idx - 1);
                    buildRankList(cmd, event);
                    sendUpdate(cmd, event, false);
                }
                return;
            }
            case "nextRank" -> {
                int idx = rankToIndex(viewingRank);
                if (idx < 26) { // 26 = FREE
                    viewingRank = indexToRank(idx + 1);
                    buildRankList(cmd, event);
                    sendUpdate(cmd, event, false);
                }
                return;
            }
        }
    }

    private int rankToIndex(String rank) {
        if ("FREE".equalsIgnoreCase(rank)) return 26;
        if (rank.length() == 1) return rank.charAt(0) - 'A';
        return 0;
    }

    private String indexToRank(int index) {
        if (index == 26) return "FREE";
        if (index >= 0 && index <= 25) return String.valueOf((char) ('A' + index));
        return "A";
    }

    // =========================================
    // DATA CODEC
    // =========================================

    public static class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
                .addField(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .build();

        public String action;
    }
}
