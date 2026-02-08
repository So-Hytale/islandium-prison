package com.islandium.prison.ui.pages;

import com.islandium.core.database.SQLExecutor;
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
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Page admin pour visualiser et configurer les challenges.
 * Accessible via le bouton CONFIG CHALLENGES dans la page defis (admin only).
 *
 * Modes: LIST (vue par rang), EDIT (modifier un challenge), CREATE (nouveau challenge).
 */
public class ChallengeConfigPage extends InteractiveCustomUIPage<ChallengeConfigPage.PageData> {

    private static final int MAX_TIERS = 5;

    private final PrisonPlugin plugin;
    private final PlayerRef playerRef;

    // Etat de la vue
    private enum ViewMode { LIST, EDIT, CREATE }
    private ViewMode viewMode = ViewMode.LIST;
    private String viewingRank = "A";

    // Etat de l'editeur
    private String editingChallengeId;
    private ChallengeType selectedType = ChallengeType.MINE_BLOCKS;
    private int tierCount = 1;
    // Cache des valeurs du formulaire (pour rebuild sans perdre les saisies)
    private String formName = "";
    private String formDesc = "";
    private String formId = "";
    private String formBlock = "";
    private long[] formTierTargets = new long[MAX_TIERS];
    private long[] formTierRewards = new long[MAX_TIERS];

    public ChallengeConfigPage(@Nonnull PlayerRef playerRef, PrisonPlugin plugin) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageData.CODEC);
        this.plugin = plugin;
        this.playerRef = playerRef;
    }

    private SQLExecutor getSql() {
        return plugin.getCore().getDatabaseManager().getExecutor();
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder event, @Nonnull Store<EntityStore> store) {
        cmd.append("Pages/Prison/ChallengeConfigPage.ui");
        event.addEventBinding(CustomUIEventBindingType.Activating, "#CloseBtn", EventData.of("Action", "close"), false);
        buildRankList(cmd, event);
    }

    // =============================================
    // VUE LIST : Liste des challenges par rang
    // =============================================

    private void buildRankList(UICommandBuilder cmd, UIEventBuilder event) {
        viewMode = ViewMode.LIST;
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
            "  Label { Anchor: (Width: 25); Text: \"#\"; Style: (FontSize: 10, TextColor: #7c8b99, RenderBold: true, VerticalAlignment: Center); } " +
            "  Label { Anchor: (Width: 130); Text: \"Nom\"; Style: (FontSize: 10, TextColor: #7c8b99, RenderBold: true, VerticalAlignment: Center); } " +
            "  Label { Anchor: (Width: 100); Text: \"Type\"; Style: (FontSize: 10, TextColor: #7c8b99, RenderBold: true, VerticalAlignment: Center); } " +
            "  Label { Anchor: (Width: 100); Text: \"Cible bloc\"; Style: (FontSize: 10, TextColor: #7c8b99, RenderBold: true, VerticalAlignment: Center); } " +
            "  Label { FlexWeight: 1; Text: \"Paliers\"; Style: (FontSize: 10, TextColor: #7c8b99, RenderBold: true, VerticalAlignment: Center); } " +
            "  Label { Anchor: (Width: 110); Text: \"Actions\"; Style: (FontSize: 10, TextColor: #7c8b99, RenderBold: true, VerticalAlignment: Center); } " +
            "}");

        // Liste des challenges pour ce rang
        List<ChallengeDefinition> challenges = ChallengeRegistry.getChallengesForRank(viewingRank);

        if (challenges.isEmpty()) {
            cmd.appendInline("#PageContent",
                "Group { Anchor: (Height: 30, Top: 10); LayoutMode: Left; " +
                "  Group { FlexWeight: 1; } " +
                "  Label { Anchor: (Width: 350); Text: \"Aucun challenge defini pour ce rang.\"; Style: (FontSize: 13, TextColor: #808080, VerticalAlignment: Center); } " +
                "  Group { FlexWeight: 1; } " +
                "}");
        } else {
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
                    "  Label #Idx { Anchor: (Width: 25); Style: (FontSize: 11, TextColor: #96a9be, VerticalAlignment: Center); } " +
                    "  Label #Name { Anchor: (Width: 130); Style: (FontSize: 11, TextColor: #ffd700, RenderBold: true, VerticalAlignment: Center); } " +
                    "  Label #Type { Anchor: (Width: 100); Style: (FontSize: 10, TextColor: " + typeColor + ", VerticalAlignment: Center); } " +
                    "  Label #Block { Anchor: (Width: 100); Style: (FontSize: 10, TextColor: #96a9be, VerticalAlignment: Center); } " +
                    "  Label #Tiers { FlexWeight: 1; Style: (FontSize: 9, TextColor: #66bb6a, VerticalAlignment: Center); } " +
                    "  TextButton #EditBtn" + i + " { Anchor: (Width: 50, Height: 26); " +
                    "    Style: TextButtonStyle(Default: (Background: #1a3a5a, LabelStyle: (FontSize: 10, TextColor: #4fc3f7, RenderBold: true, VerticalAlignment: Center)), " +
                    "    Hovered: (Background: #2a4a6a, LabelStyle: (FontSize: 10, TextColor: #ffffff, RenderBold: true, VerticalAlignment: Center))); } " +
                    "  TextButton #DelBtn" + i + " { Anchor: (Width: 30, Height: 26, Left: 4); " +
                    "    Style: TextButtonStyle(Default: (Background: #3a1a1a, LabelStyle: (FontSize: 12, TextColor: #ff4444, RenderBold: true, VerticalAlignment: Center)), " +
                    "    Hovered: (Background: #4a2a2a, LabelStyle: (FontSize: 12, TextColor: #ff6666, RenderBold: true, VerticalAlignment: Center))); } " +
                    "}");

                cmd.set("#" + rowId + " #Idx.Text", String.valueOf(i + 1));
                cmd.set("#" + rowId + " #Name.Text", def.getDisplayName());
                cmd.set("#" + rowId + " #Type.Text", def.getType().name());
                cmd.set("#" + rowId + " #Block.Text", targetBlock);
                cmd.set("#" + rowId + " #Tiers.Text", tiersText.toString());
                cmd.set("#EditBtn" + i + ".Text", "EDIT");
                cmd.set("#DelBtn" + i + ".Text", "X");

                event.addEventBinding(CustomUIEventBindingType.Activating, "#EditBtn" + i,
                    EventData.of("Action", "editChallenge").append("ChallengeId", def.getId()), false);
                event.addEventBinding(CustomUIEventBindingType.Activating, "#DelBtn" + i,
                    EventData.of("Action", "deleteChallenge").append("ChallengeId", def.getId()), false);
            }
        }

        // Resume + Bouton nouveau
        cmd.appendInline("#PageContent",
            "Group { Anchor: (Height: 50, Top: 15); LayoutMode: Top; " +
            "  Group { Anchor: (Height: 20); LayoutMode: Left; " +
            "    Group { FlexWeight: 1; } " +
            "    Label #Summary { Anchor: (Width: 300); Style: (FontSize: 12, TextColor: #96a9be, VerticalAlignment: Center); } " +
            "    Group { FlexWeight: 1; } " +
            "  } " +
            "  Group { Anchor: (Height: 35, Top: 5); LayoutMode: Left; " +
            "    Group { FlexWeight: 1; } " +
            "    TextButton #AddBtn { Anchor: (Width: 220, Height: 32); " +
            "      Style: TextButtonStyle(Default: (Background: #1a3a1a, LabelStyle: (FontSize: 12, TextColor: #66bb6a, RenderBold: true, VerticalAlignment: Center)), " +
            "      Hovered: (Background: #2a5a2a, LabelStyle: (FontSize: 12, TextColor: #ffffff, RenderBold: true, VerticalAlignment: Center))); } " +
            "    Group { FlexWeight: 1; } " +
            "  } " +
            "}");
        cmd.set("#Summary.Text", challenges.size() + " challenges pour le rang " + viewingRank);
        cmd.set("#AddBtn.Text", "+ NOUVEAU CHALLENGE");
        event.addEventBinding(CustomUIEventBindingType.Activating, "#AddBtn", EventData.of("Action", "createChallenge"), false);
    }

    // =============================================
    // VUE EDIT / CREATE : Formulaire d'edition
    // =============================================

    private void buildEditorForm(UICommandBuilder cmd, UIEventBuilder event) {
        cmd.clear("#PageContent");
        boolean isEdit = viewMode == ViewMode.EDIT;
        cmd.set("#HeaderTitle.Text", isEdit ? "MODIFIER CHALLENGE" : "CREER CHALLENGE");

        // --- ID ---
        cmd.appendInline("#PageContent",
            "Group { Anchor: (Height: 35, Top: 5); LayoutMode: Left; Padding: (Horizontal: 10); " +
            "  Label { Anchor: (Width: 100); Text: \"ID:\"; Style: (FontSize: 12, TextColor: #96a9be, RenderBold: true, VerticalAlignment: Center); } " +
            (isEdit ?
            "  Label #EditIdLabel { Anchor: (Height: 28); FlexWeight: 1; Style: (FontSize: 12, TextColor: #7c8b99, VerticalAlignment: Center); } " +
            "  TextField #EditId { Anchor: (Width: 0, Height: 0); } "
            :
            "  TextField #EditId { Anchor: (Height: 28); FlexWeight: 1; PlaceholderText: \"ex: A_1\"; } "
            ) +
            "}");
        cmd.set("#EditId.Value", formId);
        if (isEdit) {
            cmd.set("#EditIdLabel.Text", formId + "  (non modifiable)");
        }

        // --- Nom ---
        cmd.appendInline("#PageContent",
            "Group { Anchor: (Height: 35, Top: 3); LayoutMode: Left; Padding: (Horizontal: 10); " +
            "  Label { Anchor: (Width: 100); Text: \"Nom:\"; Style: (FontSize: 12, TextColor: #96a9be, RenderBold: true, VerticalAlignment: Center); } " +
            "  TextField #EditName { Anchor: (Height: 28); FlexWeight: 1; PlaceholderText: \"Nom du challenge\"; } " +
            "}");
        cmd.set("#EditName.Value", formName);

        // --- Description ---
        cmd.appendInline("#PageContent",
            "Group { Anchor: (Height: 35, Top: 3); LayoutMode: Left; Padding: (Horizontal: 10); " +
            "  Label { Anchor: (Width: 100); Text: \"Description:\"; Style: (FontSize: 12, TextColor: #96a9be, RenderBold: true, VerticalAlignment: Center); } " +
            "  TextField #EditDesc { Anchor: (Height: 28); FlexWeight: 1; PlaceholderText: \"Description\"; } " +
            "}");
        cmd.set("#EditDesc.Value", formDesc);

        // --- Type (grille de boutons) ---
        cmd.appendInline("#PageContent",
            "Group { Anchor: (Height: 20, Top: 8); Padding: (Horizontal: 10); " +
            "  Label { Text: \"Type:\"; Style: (FontSize: 12, TextColor: #96a9be, RenderBold: true, VerticalAlignment: Center); } " +
            "}");

        // Premiere ligne de types (5 boutons)
        ChallengeType[] types = ChallengeType.values();
        StringBuilder typeRow1 = new StringBuilder("Group { Anchor: (Height: 30, Top: 2); LayoutMode: Left; Padding: (Horizontal: 10); ");
        for (int i = 0; i < Math.min(5, types.length); i++) {
            ChallengeType t = types[i];
            boolean selected = t == selectedType;
            String bg = selected ? "#2a5f2a" : "#1a2836";
            String hover = selected ? "#3a7f3a" : "#253545";
            String color = selected ? "#ffffff" : getTypeColor(t);
            typeRow1.append("TextButton #TypeBtn").append(i).append(" { Anchor: (Width: 130, Height: 26); ")
                    .append("Style: TextButtonStyle(Default: (Background: ").append(bg)
                    .append(", LabelStyle: (FontSize: 9, TextColor: ").append(color).append(", RenderBold: true, VerticalAlignment: Center)), ")
                    .append("Hovered: (Background: ").append(hover)
                    .append(", LabelStyle: (FontSize: 9, TextColor: #ffffff, RenderBold: true, VerticalAlignment: Center))); } ");
        }
        typeRow1.append("}");
        cmd.appendInline("#PageContent", typeRow1.toString());
        for (int i = 0; i < Math.min(5, types.length); i++) {
            cmd.set("#TypeBtn" + i + ".Text", types[i].name());
            event.addEventBinding(CustomUIEventBindingType.Activating, "#TypeBtn" + i,
                EventData.of("Action", "selectType").append("TypeSelect", types[i].name()), false);
        }

        // Deuxieme ligne de types (4 boutons restants)
        if (types.length > 5) {
            StringBuilder typeRow2 = new StringBuilder("Group { Anchor: (Height: 30, Top: 2); LayoutMode: Left; Padding: (Horizontal: 10); ");
            for (int i = 5; i < types.length; i++) {
                ChallengeType t = types[i];
                boolean selected = t == selectedType;
                String bg = selected ? "#2a5f2a" : "#1a2836";
                String hover = selected ? "#3a7f3a" : "#253545";
                String color = selected ? "#ffffff" : getTypeColor(t);
                typeRow2.append("TextButton #TypeBtn").append(i).append(" { Anchor: (Width: 130, Height: 26); ")
                        .append("Style: TextButtonStyle(Default: (Background: ").append(bg)
                        .append(", LabelStyle: (FontSize: 9, TextColor: ").append(color).append(", RenderBold: true, VerticalAlignment: Center)), ")
                        .append("Hovered: (Background: ").append(hover)
                        .append(", LabelStyle: (FontSize: 9, TextColor: #ffffff, RenderBold: true, VerticalAlignment: Center))); } ");
            }
            typeRow2.append("}");
            cmd.appendInline("#PageContent", typeRow2.toString());
            for (int i = 5; i < types.length; i++) {
                cmd.set("#TypeBtn" + i + ".Text", types[i].name());
                event.addEventBinding(CustomUIEventBindingType.Activating, "#TypeBtn" + i,
                    EventData.of("Action", "selectType").append("TypeSelect", types[i].name()), false);
            }
        }

        // --- Bloc cible (seulement si MINE_SPECIFIC) ---
        boolean showBlock = selectedType == ChallengeType.MINE_SPECIFIC;
        if (showBlock) {
            cmd.appendInline("#PageContent",
                "Group #BlockRow { Anchor: (Height: 35, Top: 5); LayoutMode: Left; Padding: (Horizontal: 10); " +
                "  Label { Anchor: (Width: 100); Text: \"Bloc cible:\"; Style: (FontSize: 12, TextColor: #96a9be, RenderBold: true, VerticalAlignment: Center); } " +
                "  TextField #EditBlock { Anchor: (Height: 28); FlexWeight: 1; PlaceholderText: \"ex: hytale:cobblestone\"; } " +
                "}");
            cmd.set("#EditBlock.Value", formBlock);
        }

        // --- Paliers ---
        cmd.appendInline("#PageContent",
            "Group { Anchor: (Height: 25, Top: 8); LayoutMode: Left; Padding: (Horizontal: 10); " +
            "  Label { Anchor: (Width: 100); Text: \"Paliers:\"; Style: (FontSize: 12, TextColor: #96a9be, RenderBold: true, VerticalAlignment: Center); } " +
            "  Label #TierInfo { FlexWeight: 1; Style: (FontSize: 10, TextColor: #7c8b99, VerticalAlignment: Center); } " +
            "}");
        cmd.set("#TierInfo.Text", tierCount + "/" + MAX_TIERS + " paliers");

        // Header paliers
        cmd.appendInline("#PageContent",
            "Group { Anchor: (Height: 22, Top: 2); LayoutMode: Left; Padding: (Horizontal: 10); " +
            "  Label { Anchor: (Width: 30); Text: \"#\"; Style: (FontSize: 9, TextColor: #7c8b99, RenderBold: true, VerticalAlignment: Center); } " +
            "  Label { Anchor: (Width: 200); Text: \"Cible (nombre)\"; Style: (FontSize: 9, TextColor: #7c8b99, RenderBold: true, VerticalAlignment: Center); } " +
            "  Label { Anchor: (Width: 200); Text: \"Recompense ($)\"; Style: (FontSize: 9, TextColor: #7c8b99, RenderBold: true, VerticalAlignment: Center); } " +
            "}");

        // Lignes de paliers (seulement celles visibles, pas de Visible: false inline)
        for (int i = 0; i < tierCount; i++) {
            cmd.appendInline("#PageContent",
                "Group #TierRow" + i + " { Anchor: (Height: 35, Top: 2); LayoutMode: Left; Padding: (Horizontal: 10); " +
                "  Label { Anchor: (Width: 30); Text: \"" + (i + 1) + ".\"; Style: (FontSize: 11, TextColor: #96a9be, VerticalAlignment: Center); } " +
                "  NumberField #T" + i + "Target { Anchor: (Width: 200, Height: 28); PlaceholderText: \"Cible\"; } " +
                "  NumberField #T" + i + "Reward { Anchor: (Width: 200, Height: 28, Left: 8); PlaceholderText: \"Recompense\"; } " +
                "}");
            if (formTierTargets[i] > 0) cmd.set("#T" + i + "Target.Value", (int) formTierTargets[i]);
            if (formTierRewards[i] > 0) cmd.set("#T" + i + "Reward.Value", (int) formTierRewards[i]);
        }

        // Boutons ajouter/retirer palier
        cmd.appendInline("#PageContent",
            "Group { Anchor: (Height: 32, Top: 5); LayoutMode: Left; Padding: (Horizontal: 10); " +
            "  TextButton #AddTierBtn { Anchor: (Width: 140, Height: 28); " +
            "    Style: TextButtonStyle(Default: (Background: #1a3a1a, LabelStyle: (FontSize: 10, TextColor: #66bb6a, VerticalAlignment: Center)), " +
            "    Hovered: (Background: #2a5a2a, LabelStyle: (FontSize: 10, TextColor: #ffffff, VerticalAlignment: Center))); } " +
            "  TextButton #RemTierBtn { Anchor: (Width: 140, Height: 28, Left: 8); " +
            "    Style: TextButtonStyle(Default: (Background: #3a1a1a, LabelStyle: (FontSize: 10, TextColor: #ef5350, VerticalAlignment: Center)), " +
            "    Hovered: (Background: #4a2a2a, LabelStyle: (FontSize: 10, TextColor: #ffffff, VerticalAlignment: Center))); } " +
            "}");
        cmd.set("#AddTierBtn.Text", "+ PALIER");
        cmd.set("#RemTierBtn.Text", "- PALIER");
        event.addEventBinding(CustomUIEventBindingType.Activating, "#AddTierBtn", EventData.of("Action", "addTier"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#RemTierBtn", EventData.of("Action", "removeTier"), false);

        // --- Boutons Sauvegarder / Annuler ---
        // On construit l'EventData pour capturer tous les champs du formulaire
        Map<String, String> saveFields = new java.util.LinkedHashMap<>();
        saveFields.put("Action", "saveChallenge");
        saveFields.put("@EditId", "#EditId.Value");
        saveFields.put("@EditName", "#EditName.Value");
        saveFields.put("@EditDesc", "#EditDesc.Value");
        if (showBlock) {
            saveFields.put("@EditBlock", "#EditBlock.Value");
        }
        for (int i = 0; i < tierCount; i++) {
            saveFields.put("@T" + i + "Target", "#T" + i + "Target.Value");
            saveFields.put("@T" + i + "Reward", "#T" + i + "Reward.Value");
        }

        cmd.appendInline("#PageContent",
            "Group { Anchor: (Height: 50, Top: 15); LayoutMode: Left; Padding: (Horizontal: 10); " +
            "  Group { FlexWeight: 1; } " +
            "  TextButton #SaveBtn { Anchor: (Width: 180, Height: 40); " +
            "    Style: TextButtonStyle(Default: (Background: #2a5f2a, LabelStyle: (FontSize: 14, TextColor: #ffffff, RenderBold: true, VerticalAlignment: Center)), " +
            "    Hovered: (Background: #3a7f3a, LabelStyle: (FontSize: 14, TextColor: #ffffff, RenderBold: true, VerticalAlignment: Center))); } " +
            "  TextButton #CancelBtn { Anchor: (Width: 140, Height: 40, Left: 15); " +
            "    Style: TextButtonStyle(Default: (Background: #3a2a2a, LabelStyle: (FontSize: 14, TextColor: #ff6666, RenderBold: true, VerticalAlignment: Center)), " +
            "    Hovered: (Background: #4a3a3a, LabelStyle: (FontSize: 14, TextColor: #ffffff, RenderBold: true, VerticalAlignment: Center))); } " +
            "  Group { FlexWeight: 1; } " +
            "}");
        cmd.set("#SaveBtn.Text", "SAUVEGARDER");
        cmd.set("#CancelBtn.Text", "ANNULER");

        event.addEventBinding(CustomUIEventBindingType.Activating, "#SaveBtn", new EventData(saveFields), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#CancelBtn", EventData.of("Action", "cancelEdit"), false);
    }

    // =============================================
    // Initialisation du formulaire
    // =============================================

    private void initFormForEdit(ChallengeDefinition def) {
        editingChallengeId = def.getId();
        formId = def.getId();
        formName = def.getDisplayName();
        formDesc = def.getDescription();
        formBlock = def.getTargetBlockId() != null ? def.getTargetBlockId() : "";
        selectedType = def.getType();
        tierCount = Math.max(1, def.getTiers().size());
        formTierTargets = new long[MAX_TIERS];
        formTierRewards = new long[MAX_TIERS];
        for (int i = 0; i < def.getTiers().size() && i < MAX_TIERS; i++) {
            formTierTargets[i] = def.getTiers().get(i).target();
            formTierRewards[i] = def.getTiers().get(i).reward().longValue();
        }
    }

    private void initFormForCreate() {
        editingChallengeId = null;
        int nextIndex = ChallengeRegistry.getChallengesForRank(viewingRank).size();
        formId = viewingRank + "_" + (nextIndex + 1);
        formName = "";
        formDesc = "";
        formBlock = "";
        selectedType = ChallengeType.MINE_BLOCKS;
        tierCount = 1;
        formTierTargets = new long[MAX_TIERS];
        formTierRewards = new long[MAX_TIERS];
    }

    // =============================================
    // HANDLE DATA EVENT
    // =============================================

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
                if (idx < 26) {
                    viewingRank = indexToRank(idx + 1);
                    buildRankList(cmd, event);
                    sendUpdate(cmd, event, false);
                }
                return;
            }
            case "editChallenge" -> {
                if (data.challengeId != null) {
                    ChallengeDefinition def = ChallengeRegistry.getChallenge(data.challengeId);
                    if (def != null) {
                        viewMode = ViewMode.EDIT;
                        initFormForEdit(def);
                        buildEditorForm(cmd, event);
                        sendUpdate(cmd, event, false);
                    }
                }
                return;
            }
            case "deleteChallenge" -> {
                if (data.challengeId != null) {
                    try {
                        ChallengeRegistry.deleteChallenge(getSql(), data.challengeId);
                    } catch (Exception e) {
                        System.err.println("[ChallengeConfig] Delete failed: " + e.getMessage());
                    }
                    buildRankList(cmd, event);
                    sendUpdate(cmd, event, false);
                }
                return;
            }
            case "createChallenge" -> {
                viewMode = ViewMode.CREATE;
                initFormForCreate();
                buildEditorForm(cmd, event);
                sendUpdate(cmd, event, false);
                return;
            }
            case "selectType" -> {
                if (data.typeSelect != null) {
                    try {
                        selectedType = ChallengeType.valueOf(data.typeSelect);
                    } catch (Exception ignored) {}
                    // Capturer les valeurs du formulaire avant rebuild
                    captureFormValues(data);
                    buildEditorForm(cmd, event);
                    sendUpdate(cmd, event, false);
                }
                return;
            }
            case "addTier" -> {
                if (tierCount < MAX_TIERS) {
                    captureFormValues(data);
                    tierCount++;
                    buildEditorForm(cmd, event);
                    sendUpdate(cmd, event, false);
                }
                return;
            }
            case "removeTier" -> {
                if (tierCount > 1) {
                    captureFormValues(data);
                    tierCount--;
                    formTierTargets[tierCount] = 0;
                    formTierRewards[tierCount] = 0;
                    buildEditorForm(cmd, event);
                    sendUpdate(cmd, event, false);
                }
                return;
            }
            case "saveChallenge" -> {
                captureFormValues(data);
                if (handleSave()) {
                    buildRankList(cmd, event);
                } else {
                    buildEditorForm(cmd, event);
                }
                sendUpdate(cmd, event, false);
                return;
            }
            case "cancelEdit" -> {
                buildRankList(cmd, event);
                sendUpdate(cmd, event, false);
                return;
            }
        }
    }

    /**
     * Capture les valeurs des champs du formulaire depuis le PageData.
     */
    private void captureFormValues(PageData data) {
        if (data.editName != null) formName = data.editName;
        if (data.editDesc != null) formDesc = data.editDesc;
        if (data.editId != null) formId = data.editId;
        if (data.editBlock != null) formBlock = data.editBlock;

        if (data.t0Target != null) formTierTargets[0] = data.t0Target;
        if (data.t0Reward != null) formTierRewards[0] = data.t0Reward;
        if (data.t1Target != null) formTierTargets[1] = data.t1Target;
        if (data.t1Reward != null) formTierRewards[1] = data.t1Reward;
        if (data.t2Target != null) formTierTargets[2] = data.t2Target;
        if (data.t2Reward != null) formTierRewards[2] = data.t2Reward;
        if (data.t3Target != null) formTierTargets[3] = data.t3Target;
        if (data.t3Reward != null) formTierRewards[3] = data.t3Reward;
        if (data.t4Target != null) formTierTargets[4] = data.t4Target;
        if (data.t4Reward != null) formTierRewards[4] = data.t4Reward;
    }

    /**
     * Valide et sauvegarde le challenge.
     * @return true si succes, false si erreur de validation
     */
    private boolean handleSave() {
        // Validation
        if (formId == null || formId.isBlank()) return false;
        if (formName == null || formName.isBlank()) return false;
        if (tierCount < 1) return false;

        // Verifier au moins un palier valide
        boolean hasValidTier = false;
        for (int i = 0; i < tierCount; i++) {
            if (formTierTargets[i] > 0 && formTierRewards[i] >= 0) {
                hasValidTier = true;
                break;
            }
        }
        if (!hasValidTier) return false;

        // Construire le ChallengeDefinition
        int nextIndex = viewMode == ViewMode.CREATE
            ? ChallengeRegistry.getChallengesForRank(viewingRank).size()
            : (ChallengeRegistry.getChallenge(editingChallengeId) != null
                ? ChallengeRegistry.getChallenge(editingChallengeId).getIndex()
                : 0);

        ChallengeDefinition.Builder builder = new ChallengeDefinition.Builder(
            viewingRank, nextIndex, formId, formName, selectedType
        );
        builder.description(formDesc != null ? formDesc : "");
        if (selectedType == ChallengeType.MINE_SPECIFIC && formBlock != null && !formBlock.isBlank()) {
            builder.targetBlock(formBlock);
        }

        for (int i = 0; i < tierCount; i++) {
            if (formTierTargets[i] > 0) {
                builder.tier(formTierTargets[i], formTierRewards[i]);
            }
        }

        ChallengeDefinition def = builder.build();

        try {
            if (viewMode == ViewMode.EDIT) {
                ChallengeRegistry.updateChallenge(getSql(), def);
            } else {
                ChallengeRegistry.addChallenge(getSql(), def);
            }
            return true;
        } catch (Exception e) {
            System.err.println("[ChallengeConfig] Save failed: " + e.getMessage());
            return false;
        }
    }

    // =============================================
    // Utilitaires
    // =============================================

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
                .addField(new KeyedCodec<>("ChallengeId", Codec.STRING), (d, v) -> d.challengeId = v, d -> d.challengeId)
                .addField(new KeyedCodec<>("TypeSelect", Codec.STRING), (d, v) -> d.typeSelect = v, d -> d.typeSelect)
                .addField(new KeyedCodec<>("EditId", Codec.STRING), (d, v) -> d.editId = v, d -> d.editId)
                .addField(new KeyedCodec<>("EditName", Codec.STRING), (d, v) -> d.editName = v, d -> d.editName)
                .addField(new KeyedCodec<>("EditDesc", Codec.STRING), (d, v) -> d.editDesc = v, d -> d.editDesc)
                .addField(new KeyedCodec<>("EditBlock", Codec.STRING), (d, v) -> d.editBlock = v, d -> d.editBlock)
                .addField(new KeyedCodec<>("T0Target", Codec.INTEGER), (d, v) -> d.t0Target = v, d -> d.t0Target)
                .addField(new KeyedCodec<>("T0Reward", Codec.INTEGER), (d, v) -> d.t0Reward = v, d -> d.t0Reward)
                .addField(new KeyedCodec<>("T1Target", Codec.INTEGER), (d, v) -> d.t1Target = v, d -> d.t1Target)
                .addField(new KeyedCodec<>("T1Reward", Codec.INTEGER), (d, v) -> d.t1Reward = v, d -> d.t1Reward)
                .addField(new KeyedCodec<>("T2Target", Codec.INTEGER), (d, v) -> d.t2Target = v, d -> d.t2Target)
                .addField(new KeyedCodec<>("T2Reward", Codec.INTEGER), (d, v) -> d.t2Reward = v, d -> d.t2Reward)
                .addField(new KeyedCodec<>("T3Target", Codec.INTEGER), (d, v) -> d.t3Target = v, d -> d.t3Target)
                .addField(new KeyedCodec<>("T3Reward", Codec.INTEGER), (d, v) -> d.t3Reward = v, d -> d.t3Reward)
                .addField(new KeyedCodec<>("T4Target", Codec.INTEGER), (d, v) -> d.t4Target = v, d -> d.t4Target)
                .addField(new KeyedCodec<>("T4Reward", Codec.INTEGER), (d, v) -> d.t4Reward = v, d -> d.t4Reward)
                .build();

        public String action;
        public String challengeId;
        public String typeSelect;
        public String editId;
        public String editName;
        public String editDesc;
        public String editBlock;
        public Integer t0Target, t0Reward;
        public Integer t1Target, t1Reward;
        public Integer t2Target, t2Reward;
        public Integer t3Target, t3Reward;
        public Integer t4Target, t4Reward;
    }
}
