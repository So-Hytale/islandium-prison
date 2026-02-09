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
import com.hypixel.hytale.server.core.entity.entities.Player;
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

/**
 * Page admin pour visualiser et configurer les challenges.
 * Accessible via le bouton CONFIG CHALLENGES dans la page defis (admin only).
 *
 * Modes: LIST (vue par rang), EDIT (modifier un challenge), CREATE (nouveau challenge).
 */
public class ChallengeConfigPage extends InteractiveCustomUIPage<ChallengeConfigPage.PageData> {

    private static final int MAX_TIERS = 5;
    private static final int MAX_ITEMS_PER_TIER = 3;

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
    // Items requis par palier pour SUBMIT_ITEMS [tier][itemSlot]
    private String[][] formTierItemIds = new String[MAX_TIERS][MAX_ITEMS_PER_TIER];
    private int[][] formTierItemQtys = new int[MAX_TIERS][MAX_ITEMS_PER_TIER];

    public ChallengeConfigPage(@Nonnull PlayerRef playerRef, PrisonPlugin plugin) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageData.CODEC);
        this.plugin = plugin;
        this.playerRef = playerRef;
    }

    /**
     * Constructeur avec etat restaure (pour reopenPage).
     */
    public ChallengeConfigPage(@Nonnull PlayerRef playerRef, PrisonPlugin plugin,
                                ViewMode viewMode, String viewingRank,
                                String editingChallengeId, ChallengeType selectedType,
                                int tierCount, String formId, String formName,
                                String formDesc, String formBlock,
                                long[] formTierTargets, long[] formTierRewards,
                                String[][] formTierItemIds, int[][] formTierItemQtys) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageData.CODEC);
        this.plugin = plugin;
        this.playerRef = playerRef;
        this.viewMode = viewMode;
        this.viewingRank = viewingRank;
        this.editingChallengeId = editingChallengeId;
        this.selectedType = selectedType;
        this.tierCount = tierCount;
        this.formId = formId;
        this.formName = formName;
        this.formDesc = formDesc;
        this.formBlock = formBlock;
        this.formTierTargets = formTierTargets;
        this.formTierRewards = formTierRewards;
        this.formTierItemIds = formTierItemIds;
        this.formTierItemQtys = formTierItemQtys;
    }

    private SQLExecutor getSql() {
        return plugin.getCore().getDatabaseManager().getExecutor();
    }

    /**
     * Rouvre la page avec l'etat actuel pour forcer un build() complet.
     */
    private void reopenPage(Ref<EntityStore> ref, Store<EntityStore> store, Player player) {
        player.getPageManager().openCustomPage(ref, store,
            new ChallengeConfigPage(playerRef, plugin,
                viewMode, viewingRank, editingChallengeId, selectedType,
                tierCount, formId, formName, formDesc, formBlock,
                formTierTargets, formTierRewards,
                formTierItemIds, formTierItemQtys));
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder event, @Nonnull Store<EntityStore> store) {
        cmd.append("Pages/Prison/ChallengeConfigPage.ui");
        event.addEventBinding(CustomUIEventBindingType.Activating, "#CloseBtn", EventData.of("Action", "close"), false);
        if (viewMode == ViewMode.EDIT || viewMode == ViewMode.CREATE) {
            buildEditorForm(cmd, event);
        } else {
            buildRankList(cmd, event);
        }
    }

    // =============================================
    // VUE LIST : Liste des challenges par rang
    // =============================================

    private void buildRankList(UICommandBuilder cmd, UIEventBuilder event) {
        viewMode = ViewMode.LIST;

        // Toggle visibility: show list, hide editor
        cmd.set("#EditorForm.Visible", false);
        cmd.set("#PageContent.Visible", true);

        cmd.clear("#PageContent");
        cmd.set("#HeaderTitle.Text", "CHALLENGES - RANG " + viewingRank);

        // Navigation entre rangs
        cmd.appendInline("#PageContent",
            "Group { Anchor: (Height: 45); LayoutMode: Left; " +
            "  TextButton #PrevRank { Anchor: (Width: 130, Height: 38); " +
            "    Style: TextButtonStyle(Default: (Background: #1a2836, LabelStyle: (FontSize: 14, TextColor: #96a9be, RenderBold: true, HorizontalAlignment: Center, VerticalAlignment: Center)), " +
            "    Hovered: (Background: #253545, LabelStyle: (FontSize: 14, TextColor: #ffffff, RenderBold: true, HorizontalAlignment: Center, VerticalAlignment: Center))); } " +
            "  Group { FlexWeight: 1; } " +
            "  Label #RankLabel { Anchor: (Width: 200); Style: (FontSize: 20, TextColor: #ffd700, RenderBold: true, VerticalAlignment: Center); } " +
            "  Group { FlexWeight: 1; } " +
            "  TextButton #NextRank { Anchor: (Width: 130, Height: 38); " +
            "    Style: TextButtonStyle(Default: (Background: #1a2836, LabelStyle: (FontSize: 14, TextColor: #96a9be, RenderBold: true, HorizontalAlignment: Center, VerticalAlignment: Center)), " +
            "    Hovered: (Background: #253545, LabelStyle: (FontSize: 14, TextColor: #ffffff, RenderBold: true, HorizontalAlignment: Center, VerticalAlignment: Center))); } " +
            "}");

        cmd.set("#PrevRank.Text", "< Precedent");
        cmd.set("#NextRank.Text", "Suivant >");
        cmd.set("#RankLabel.Text", "Rang " + viewingRank);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#PrevRank", EventData.of("Action", "prevRank"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#NextRank", EventData.of("Action", "nextRank"), false);

        // Header colonnes
        cmd.appendInline("#PageContent",
            "Group { Anchor: (Height: 36, Top: 10); LayoutMode: Left; Padding: (Horizontal: 15); Background: (Color: #0d1520); " +
            "  Label { Anchor: (Width: 40); Text: \"#\"; Style: (FontSize: 13, TextColor: #7c8b99, RenderBold: true, VerticalAlignment: Center); } " +
            "  Label { Anchor: (Width: 180); Text: \"Nom\"; Style: (FontSize: 13, TextColor: #7c8b99, RenderBold: true, VerticalAlignment: Center); } " +
            "  Label { Anchor: (Width: 150); Text: \"Type\"; Style: (FontSize: 13, TextColor: #7c8b99, RenderBold: true, VerticalAlignment: Center); } " +
            "  Label { Anchor: (Width: 130); Text: \"Cible bloc\"; Style: (FontSize: 13, TextColor: #7c8b99, RenderBold: true, VerticalAlignment: Center); } " +
            "  Label { FlexWeight: 1; Text: \"Paliers\"; Style: (FontSize: 13, TextColor: #7c8b99, RenderBold: true, VerticalAlignment: Center); } " +
            "  Label { Anchor: (Width: 130); Text: \"Actions\"; Style: (FontSize: 13, TextColor: #7c8b99, RenderBold: true, VerticalAlignment: Center); } " +
            "}");

        // Liste des challenges pour ce rang
        List<ChallengeDefinition> challenges = ChallengeRegistry.getChallengesForRank(viewingRank);

        if (challenges.isEmpty()) {
            cmd.appendInline("#PageContent",
                "Group { Anchor: (Height: 50, Top: 15); LayoutMode: Left; " +
                "  Group { FlexWeight: 1; } " +
                "  Label { Anchor: (Width: 500); Text: \"Aucun challenge defini pour ce rang.\"; Style: (FontSize: 16, TextColor: #808080, VerticalAlignment: Center); } " +
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
                    "Group #" + rowId + " { Anchor: (Height: 42, Top: 3); LayoutMode: Left; Padding: (Horizontal: 15); Background: (Color: " + bgColor + "); " +
                    "  Label #Idx { Anchor: (Width: 40); Style: (FontSize: 14, TextColor: #96a9be, RenderBold: true, VerticalAlignment: Center); } " +
                    "  Label #Name { Anchor: (Width: 180); Style: (FontSize: 14, TextColor: #ffd700, RenderBold: true, VerticalAlignment: Center); } " +
                    "  Label #Type { Anchor: (Width: 150); Style: (FontSize: 12, TextColor: " + typeColor + ", VerticalAlignment: Center); } " +
                    "  Label #Block { Anchor: (Width: 130); Style: (FontSize: 12, TextColor: #96a9be, VerticalAlignment: Center); } " +
                    "  Label #Tiers { FlexWeight: 1; Style: (FontSize: 12, TextColor: #66bb6a, VerticalAlignment: Center); } " +
                    "  TextButton #EditBtn" + i + " { Anchor: (Width: 70, Height: 32); " +
                    "    Style: TextButtonStyle(Default: (Background: #1a3a5a, LabelStyle: (FontSize: 12, TextColor: #4fc3f7, RenderBold: true, HorizontalAlignment: Center, VerticalAlignment: Center)), " +
                    "    Hovered: (Background: #2a4a6a, LabelStyle: (FontSize: 12, TextColor: #ffffff, RenderBold: true, HorizontalAlignment: Center, VerticalAlignment: Center))); } " +
                    "  TextButton #DelBtn" + i + " { Anchor: (Width: 42, Height: 32, Left: 6); " +
                    "    Style: TextButtonStyle(Default: (Background: #3a1a1a, LabelStyle: (FontSize: 14, TextColor: #ff4444, RenderBold: true, HorizontalAlignment: Center, VerticalAlignment: Center)), " +
                    "    Hovered: (Background: #4a2a2a, LabelStyle: (FontSize: 14, TextColor: #ff6666, RenderBold: true, HorizontalAlignment: Center, VerticalAlignment: Center))); } " +
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
            "Group { Anchor: (Height: 70, Top: 20); LayoutMode: Top; " +
            "  Group { Anchor: (Height: 25); LayoutMode: Left; " +
            "    Group { FlexWeight: 1; } " +
            "    Label #Summary { Anchor: (Width: 400); Style: (FontSize: 14, TextColor: #96a9be, VerticalAlignment: Center); } " +
            "    Group { FlexWeight: 1; } " +
            "  } " +
            "  Group { Anchor: (Height: 42, Top: 8); LayoutMode: Left; " +
            "    Group { FlexWeight: 1; } " +
            "    TextButton #AddBtn { Anchor: (Width: 260, Height: 40); " +
            "      Style: TextButtonStyle(Default: (Background: #1a3a1a, LabelStyle: (FontSize: 14, TextColor: #66bb6a, RenderBold: true, HorizontalAlignment: Center, VerticalAlignment: Center)), " +
            "      Hovered: (Background: #2a5a2a, LabelStyle: (FontSize: 14, TextColor: #ffffff, RenderBold: true, HorizontalAlignment: Center, VerticalAlignment: Center))); } " +
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
        boolean isEdit = viewMode == ViewMode.EDIT;

        // Toggle visibility: show editor, hide list
        cmd.set("#EditorForm.Visible", true);
        cmd.set("#PageContent.Visible", false);

        // Header
        cmd.set("#HeaderTitle.Text", isEdit ? "MODIFIER CHALLENGE" : "CREER CHALLENGE");

        // ID field: in edit mode show label, in create mode show text field
        cmd.set("#EditIdLabel.Visible", isEdit);
        cmd.set("#EditId.Visible", !isEdit);
        cmd.set("#EditId.Value", formId);
        if (isEdit) {
            cmd.set("#EditIdLabel.Text", formId + "  (non modifiable)");
        }

        // Name & Description
        cmd.set("#EditName.Value", formName);
        cmd.set("#EditDesc.Value", formDesc);

        // Type buttons - set text (prefixe > pour le selectionne) et bind events
        ChallengeType[] types = ChallengeType.values();
        for (int i = 0; i < types.length; i++) {
            String prefix = (types[i] == selectedType) ? "> " : "";
            cmd.set("#TypeBtn" + i + ".Text", prefix + types[i].name());
            event.addEventBinding(CustomUIEventBindingType.Activating, "#TypeBtn" + i,
                EventData.of("Action", "selectType").append("TypeSelect", types[i].name()), false);
        }

        // Block row: visible only for MINE_SPECIFIC
        boolean showBlock = selectedType == ChallengeType.MINE_SPECIFIC;
        cmd.set("#BlockRow.Visible", showBlock);
        if (showBlock) {
            cmd.set("#EditBlock.Value", formBlock);
        }

        // Items section: visible only for SUBMIT_ITEMS
        boolean showItems = selectedType == ChallengeType.SUBMIT_ITEMS;
        cmd.set("#ItemsSection.Visible", showItems);
        if (showItems) {
            for (int t = 0; t < MAX_TIERS; t++) {
                cmd.set("#ItemTier" + t + ".Visible", t < tierCount);
                if (t < tierCount) {
                    for (int s = 0; s < MAX_ITEMS_PER_TIER; s++) {
                        String itemId = formTierItemIds[t][s];
                        int itemQty = formTierItemQtys[t][s];
                        if (itemId != null && !itemId.isEmpty()) {
                            cmd.set("#I" + t + "_" + s + "Id.Value", itemId);
                        }
                        if (itemQty > 0) {
                            cmd.set("#I" + t + "_" + s + "Qty.Value", itemQty);
                        }
                    }
                }
            }
        }

        // Tier info
        cmd.set("#TierInfo.Text", tierCount + "/" + MAX_TIERS + " paliers");

        // Tier rows visibility and values
        for (int i = 0; i < MAX_TIERS; i++) {
            cmd.set("#TierRow" + i + ".Visible", i < tierCount);
            if (i < tierCount) {
                if (formTierTargets[i] > 0) cmd.set("#T" + i + "Target.Value", (int) formTierTargets[i]);
                if (formTierRewards[i] > 0) cmd.set("#T" + i + "Reward.Value", (int) formTierRewards[i]);
            }
        }

        // Tier add/remove buttons
        event.addEventBinding(CustomUIEventBindingType.Activating, "#AddTierBtn", EventData.of("Action", "addTier"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#RemTierBtn", EventData.of("Action", "removeTier"), false);

        // Save button - capture all form fields
        EventData saveData = EventData.of("Action", "saveChallenge")
            .append("@EditId", "#EditId.Value")
            .append("@EditName", "#EditName.Value")
            .append("@EditDesc", "#EditDesc.Value")
            .append("@EditBlock", "#EditBlock.Value");
        for (int i = 0; i < MAX_TIERS; i++) {
            saveData = saveData
                .append("@T" + i + "Target", "#T" + i + "Target.Value")
                .append("@T" + i + "Reward", "#T" + i + "Reward.Value");
        }
        // Item fields for SUBMIT_ITEMS
        for (int t = 0; t < MAX_TIERS; t++) {
            for (int s = 0; s < MAX_ITEMS_PER_TIER; s++) {
                saveData = saveData
                    .append("@I" + t + "_" + s + "Id", "#I" + t + "_" + s + "Id.Value")
                    .append("@I" + t + "_" + s + "Qty", "#I" + t + "_" + s + "Qty.Value");
            }
        }
        event.addEventBinding(CustomUIEventBindingType.Activating, "#SaveBtn", saveData, false);
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
        formTierItemIds = new String[MAX_TIERS][MAX_ITEMS_PER_TIER];
        formTierItemQtys = new int[MAX_TIERS][MAX_ITEMS_PER_TIER];
        for (int i = 0; i < def.getTiers().size() && i < MAX_TIERS; i++) {
            ChallengeDefinition.ChallengeTier tier = def.getTiers().get(i);
            formTierTargets[i] = tier.target();
            formTierRewards[i] = tier.reward().longValue();
            // Load required items
            for (int j = 0; j < tier.requiredItems().size() && j < MAX_ITEMS_PER_TIER; j++) {
                ChallengeDefinition.RequiredItem ri = tier.requiredItems().get(j);
                formTierItemIds[i][j] = ri.itemId();
                formTierItemQtys[i][j] = ri.quantity();
            }
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
        formTierItemIds = new String[MAX_TIERS][MAX_ITEMS_PER_TIER];
        formTierItemQtys = new int[MAX_TIERS][MAX_ITEMS_PER_TIER];
    }

    // =============================================
    // HANDLE DATA EVENT
    // =============================================

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        super.handleDataEvent(ref, store, data);
        if (data.action == null) return;

        Player player = store.getComponent(ref, Player.getComponentType());

        switch (data.action) {
            case "close" -> {
                close();
                return;
            }
            case "prevRank" -> {
                int idx = rankToIndex(viewingRank);
                if (idx > 0) {
                    viewingRank = indexToRank(idx - 1);
                    viewMode = ViewMode.LIST;
                    reopenPage(ref, store, player);
                }
                return;
            }
            case "nextRank" -> {
                int idx = rankToIndex(viewingRank);
                if (idx < 26) {
                    viewingRank = indexToRank(idx + 1);
                    viewMode = ViewMode.LIST;
                    reopenPage(ref, store, player);
                }
                return;
            }
            case "editChallenge" -> {
                if (data.challengeId != null) {
                    ChallengeDefinition def = ChallengeRegistry.getChallenge(data.challengeId);
                    if (def != null) {
                        viewMode = ViewMode.EDIT;
                        initFormForEdit(def);
                        reopenPage(ref, store, player);
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
                    viewMode = ViewMode.LIST;
                    reopenPage(ref, store, player);
                }
                return;
            }
            case "createChallenge" -> {
                viewMode = ViewMode.CREATE;
                initFormForCreate();
                reopenPage(ref, store, player);
                return;
            }
            case "selectType" -> {
                if (data.typeSelect != null) {
                    try {
                        selectedType = ChallengeType.valueOf(data.typeSelect);
                    } catch (Exception ignored) {}
                    captureFormValues(data);
                    reopenPage(ref, store, player);
                }
                return;
            }
            case "addTier" -> {
                if (tierCount < MAX_TIERS) {
                    captureFormValues(data);
                    tierCount++;
                    reopenPage(ref, store, player);
                }
                return;
            }
            case "removeTier" -> {
                if (tierCount > 1) {
                    captureFormValues(data);
                    tierCount--;
                    formTierTargets[tierCount] = 0;
                    formTierRewards[tierCount] = 0;
                    reopenPage(ref, store, player);
                }
                return;
            }
            case "saveChallenge" -> {
                captureFormValues(data);
                if (handleSave()) {
                    viewMode = ViewMode.LIST;
                } else {
                    // Reste en mode edit/create
                }
                reopenPage(ref, store, player);
                return;
            }
            case "cancelEdit" -> {
                viewMode = ViewMode.LIST;
                reopenPage(ref, store, player);
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

        // Capture item fields
        for (int t = 0; t < MAX_TIERS; t++) {
            for (int s = 0; s < MAX_ITEMS_PER_TIER; s++) {
                String id = data.getItemId(t, s);
                Integer qty = data.getItemQty(t, s);
                if (id != null) formTierItemIds[t][s] = id;
                if (qty != null) formTierItemQtys[t][s] = qty;
            }
        }
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
                if (selectedType == ChallengeType.SUBMIT_ITEMS) {
                    // Build required items list for this tier
                    List<ChallengeDefinition.RequiredItem> items = new ArrayList<>();
                    for (int s = 0; s < MAX_ITEMS_PER_TIER; s++) {
                        String itemId = formTierItemIds[i][s];
                        int itemQty = formTierItemQtys[i][s];
                        if (itemId != null && !itemId.isBlank() && itemQty > 0) {
                            items.add(new ChallengeDefinition.RequiredItem(itemId, itemQty));
                        }
                    }
                    builder.tierWithItems(formTierTargets[i], formTierRewards[i], items);
                } else {
                    builder.tier(formTierTargets[i], formTierRewards[i]);
                }
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
            case SUBMIT_ITEMS -> "#ffab40";
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
                // Item fields: I{tier}_{slot}Id (string) and I{tier}_{slot}Qty (int)
                .addField(new KeyedCodec<>("I0_0Id", Codec.STRING), (d, v) -> d.i0_0Id = v, d -> d.i0_0Id)
                .addField(new KeyedCodec<>("I0_0Qty", Codec.INTEGER), (d, v) -> d.i0_0Qty = v, d -> d.i0_0Qty)
                .addField(new KeyedCodec<>("I0_1Id", Codec.STRING), (d, v) -> d.i0_1Id = v, d -> d.i0_1Id)
                .addField(new KeyedCodec<>("I0_1Qty", Codec.INTEGER), (d, v) -> d.i0_1Qty = v, d -> d.i0_1Qty)
                .addField(new KeyedCodec<>("I0_2Id", Codec.STRING), (d, v) -> d.i0_2Id = v, d -> d.i0_2Id)
                .addField(new KeyedCodec<>("I0_2Qty", Codec.INTEGER), (d, v) -> d.i0_2Qty = v, d -> d.i0_2Qty)
                .addField(new KeyedCodec<>("I1_0Id", Codec.STRING), (d, v) -> d.i1_0Id = v, d -> d.i1_0Id)
                .addField(new KeyedCodec<>("I1_0Qty", Codec.INTEGER), (d, v) -> d.i1_0Qty = v, d -> d.i1_0Qty)
                .addField(new KeyedCodec<>("I1_1Id", Codec.STRING), (d, v) -> d.i1_1Id = v, d -> d.i1_1Id)
                .addField(new KeyedCodec<>("I1_1Qty", Codec.INTEGER), (d, v) -> d.i1_1Qty = v, d -> d.i1_1Qty)
                .addField(new KeyedCodec<>("I1_2Id", Codec.STRING), (d, v) -> d.i1_2Id = v, d -> d.i1_2Id)
                .addField(new KeyedCodec<>("I1_2Qty", Codec.INTEGER), (d, v) -> d.i1_2Qty = v, d -> d.i1_2Qty)
                .addField(new KeyedCodec<>("I2_0Id", Codec.STRING), (d, v) -> d.i2_0Id = v, d -> d.i2_0Id)
                .addField(new KeyedCodec<>("I2_0Qty", Codec.INTEGER), (d, v) -> d.i2_0Qty = v, d -> d.i2_0Qty)
                .addField(new KeyedCodec<>("I2_1Id", Codec.STRING), (d, v) -> d.i2_1Id = v, d -> d.i2_1Id)
                .addField(new KeyedCodec<>("I2_1Qty", Codec.INTEGER), (d, v) -> d.i2_1Qty = v, d -> d.i2_1Qty)
                .addField(new KeyedCodec<>("I2_2Id", Codec.STRING), (d, v) -> d.i2_2Id = v, d -> d.i2_2Id)
                .addField(new KeyedCodec<>("I2_2Qty", Codec.INTEGER), (d, v) -> d.i2_2Qty = v, d -> d.i2_2Qty)
                .addField(new KeyedCodec<>("I3_0Id", Codec.STRING), (d, v) -> d.i3_0Id = v, d -> d.i3_0Id)
                .addField(new KeyedCodec<>("I3_0Qty", Codec.INTEGER), (d, v) -> d.i3_0Qty = v, d -> d.i3_0Qty)
                .addField(new KeyedCodec<>("I3_1Id", Codec.STRING), (d, v) -> d.i3_1Id = v, d -> d.i3_1Id)
                .addField(new KeyedCodec<>("I3_1Qty", Codec.INTEGER), (d, v) -> d.i3_1Qty = v, d -> d.i3_1Qty)
                .addField(new KeyedCodec<>("I3_2Id", Codec.STRING), (d, v) -> d.i3_2Id = v, d -> d.i3_2Id)
                .addField(new KeyedCodec<>("I3_2Qty", Codec.INTEGER), (d, v) -> d.i3_2Qty = v, d -> d.i3_2Qty)
                .addField(new KeyedCodec<>("I4_0Id", Codec.STRING), (d, v) -> d.i4_0Id = v, d -> d.i4_0Id)
                .addField(new KeyedCodec<>("I4_0Qty", Codec.INTEGER), (d, v) -> d.i4_0Qty = v, d -> d.i4_0Qty)
                .addField(new KeyedCodec<>("I4_1Id", Codec.STRING), (d, v) -> d.i4_1Id = v, d -> d.i4_1Id)
                .addField(new KeyedCodec<>("I4_1Qty", Codec.INTEGER), (d, v) -> d.i4_1Qty = v, d -> d.i4_1Qty)
                .addField(new KeyedCodec<>("I4_2Id", Codec.STRING), (d, v) -> d.i4_2Id = v, d -> d.i4_2Id)
                .addField(new KeyedCodec<>("I4_2Qty", Codec.INTEGER), (d, v) -> d.i4_2Qty = v, d -> d.i4_2Qty)
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
        // Item fields [tier]_[slot]
        public String i0_0Id, i0_1Id, i0_2Id;
        public Integer i0_0Qty, i0_1Qty, i0_2Qty;
        public String i1_0Id, i1_1Id, i1_2Id;
        public Integer i1_0Qty, i1_1Qty, i1_2Qty;
        public String i2_0Id, i2_1Id, i2_2Id;
        public Integer i2_0Qty, i2_1Qty, i2_2Qty;
        public String i3_0Id, i3_1Id, i3_2Id;
        public Integer i3_0Qty, i3_1Qty, i3_2Qty;
        public String i4_0Id, i4_1Id, i4_2Id;
        public Integer i4_0Qty, i4_1Qty, i4_2Qty;

        /** Helper to get item ID by tier and slot index. */
        public String getItemId(int tier, int slot) {
            return switch (tier * 3 + slot) {
                case 0 -> i0_0Id; case 1 -> i0_1Id; case 2 -> i0_2Id;
                case 3 -> i1_0Id; case 4 -> i1_1Id; case 5 -> i1_2Id;
                case 6 -> i2_0Id; case 7 -> i2_1Id; case 8 -> i2_2Id;
                case 9 -> i3_0Id; case 10 -> i3_1Id; case 11 -> i3_2Id;
                case 12 -> i4_0Id; case 13 -> i4_1Id; case 14 -> i4_2Id;
                default -> null;
            };
        }

        /** Helper to get item qty by tier and slot index. */
        public Integer getItemQty(int tier, int slot) {
            return switch (tier * 3 + slot) {
                case 0 -> i0_0Qty; case 1 -> i0_1Qty; case 2 -> i0_2Qty;
                case 3 -> i1_0Qty; case 4 -> i1_1Qty; case 5 -> i1_2Qty;
                case 6 -> i2_0Qty; case 7 -> i2_1Qty; case 8 -> i2_2Qty;
                case 9 -> i3_0Qty; case 10 -> i3_1Qty; case 11 -> i3_2Qty;
                case 12 -> i4_0Qty; case 13 -> i4_1Qty; case 14 -> i4_2Qty;
                default -> null;
            };
        }
    }
}
