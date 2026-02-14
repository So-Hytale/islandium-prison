package com.islandium.prison.ui.pages;

import com.islandium.core.api.location.ServerLocation;
import com.islandium.core.api.player.IslandiumPlayer;
import com.islandium.core.api.util.NotificationType;
import com.islandium.core.api.util.NotificationUtil;
import com.islandium.prison.PrisonPlugin;
import com.islandium.prison.mine.Mine;
import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.math.matrix.Matrix4d;
import com.hypixel.hytale.protocol.DebugShape;
import com.hypixel.hytale.protocol.Vector3f;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageLifetime;
import com.hypixel.hytale.protocol.packets.interface_.CustomUIEventBindingType;
import com.hypixel.hytale.protocol.packets.player.ClearDebugShapes;
import com.hypixel.hytale.protocol.packets.player.DisplayDebug;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.InteractiveCustomUIPage;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.modules.entity.component.TransformComponent;
import com.hypixel.hytale.math.vector.Vector3d;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Map;

/**
 * Page de gestion des mines Prison.
 */
public class MineManagerPage extends InteractiveCustomUIPage<MineManagerPage.PageData> {

    private final PrisonPlugin plugin;
    private String selectedMineId = null;
    private boolean createMode = false;
    private boolean visualizationActive = false;
    private boolean ignoringRadiusChange = false; // Flag pour ignorer les events ValueChanged pendant un toggle
    private String editingBlockType = null; // Bloc en cours d'édition pour les limites de couches

    public MineManagerPage(@Nonnull PlayerRef playerRef, PrisonPlugin plugin) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageData.CODEC);
        this.plugin = plugin;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder event, @Nonnull Store<EntityStore> store) {
        cmd.append("Pages/Prison/MineManagerPage.ui");

        // Event pour creer une mine
        event.addEventBinding(CustomUIEventBindingType.Activating, "#CreateMineButton", EventData.of("Action", "showCreateMine"), false);

        // Charger la liste des mines
        buildMineList(cmd, event);

        // Initialiser #BlockList
        cmd.clear("#BlockList");
        cmd.appendInline("#BlockList", "Label { Text: \"Selectionnez une mine\"; Anchor: (Height: 25); Style: (FontSize: 11, TextColor: #808080, HorizontalAlignment: Center); }");

        // Si une mine est selectionnee, afficher ses details
        if (selectedMineId != null && !createMode) {
            Mine mine = plugin.getMineManager().getMine(selectedMineId);
            if (mine != null) {
                buildMineEditor(cmd, event, mine);
            }
        } else if (createMode) {
            buildCreateMineForm(cmd, event);
        } else {
            // Aucune mine selectionnee - masquer les sections de configuration
            // NOTE: Ne pas toucher à #CompositionSection/#CompList ici pour éviter l'erreur de sélecteur
            cmd.set("#ConfigSection.Visible", false);
            cmd.set("#NoMineSelected.Visible", true);
            // Utiliser la visibilité définie dans le .ui pour la section composition
        }
    }

    private void buildMineList(UICommandBuilder cmd, UIEventBuilder event) {
        cmd.clear("#MineList");

        Collection<Mine> mines = plugin.getMineManager().getAllMines();

        if (mines.isEmpty()) {
            cmd.appendInline("#MineList", "Label #EmptyLabel { Text: \"Aucune mine\"; Anchor: (Height: 30); Style: (FontSize: 12, TextColor: #808080, HorizontalAlignment: Center); }");
        } else {
            int index = 0;
            for (Mine mine : mines) {
                boolean isSelected = mine.getId().equals(selectedMineId);
                String bgColor = isSelected ? "#2a3f5f" : "#151d28";
                String btnId = "MineBtn" + index;

                // Affichage: [status] name (rank)
                String status = mine.isConfigured() ? "" : "! ";
                String displayText = status + mine.getDisplayName() + " [" + mine.getRequiredRank() + "]";

                cmd.appendInline("#MineList", "Button #" + btnId + " { Anchor: (Height: 36, Bottom: 3); Background: (Color: " + bgColor + "); Padding: (Horizontal: 8); Label #Lbl { Style: (FontSize: 12, VerticalAlignment: Center); } }");
                cmd.set("#" + btnId + " #Lbl.Text", displayText);
                cmd.set("#" + btnId + " #Lbl.Style.TextColor", isSelected ? "#ffffff" : "#bfcdd5");

                event.addEventBinding(CustomUIEventBindingType.Activating, "#" + btnId, EventData.of("SelectMine", mine.getId()), false);
                index++;
            }
        }
    }

    private void buildMineEditor(UICommandBuilder cmd, UIEventBuilder event, Mine mine) {
        cmd.set("#ConfigSection.Visible", true);
        cmd.set("#NoMineSelected.Visible", false);
        // Note: La visibilité des éléments de composition est gérée dans buildCompositionList()

        // Remplir les champs de configuration
        cmd.set("#DisplayNameField.Value", mine.getDisplayName());
        cmd.set("#RequiredRankField.Value", mine.getRequiredRank());

        // Toggle forme: Cylindre ou Cuboid
        boolean isCylinder = mine.isCylindrical();
        cmd.set("#ShapeCylinderCheck.Text", isCylinder ? "X" : "");
        cmd.set("#ShapeCuboidCheck.Text", isCylinder ? "" : "X");

        // Afficher/masquer les sections selon la forme
        cmd.set("#CuboidSection.Visible", !isCylinder);
        cmd.set("#CylinderSection.Visible", isCylinder);

        // === Section Cuboid ===
        if (mine.getCorner1() != null) {
            ServerLocation c1 = mine.getCorner1();
            cmd.set("#Corner1Label.Text", String.format("Coin 1: %.0f, %.0f, %.0f", c1.x(), c1.y(), c1.z()));
        } else {
            cmd.set("#Corner1Label.Text", "Coin 1: Non defini");
        }

        if (mine.getCorner2() != null) {
            ServerLocation c2 = mine.getCorner2();
            cmd.set("#Corner2Label.Text", String.format("Coin 2: %.0f, %.0f, %.0f", c2.x(), c2.y(), c2.z()));
        } else {
            cmd.set("#Corner2Label.Text", "Coin 2: Non defini");
        }

        // === Section Cylindre ===
        if (mine.getCenter() != null) {
            ServerLocation center = mine.getCenter();
            cmd.set("#CenterLabel.Text", String.format("%.0f, %.0f, %.0f", center.x(), center.y(), center.z()));
        } else {
            cmd.set("#CenterLabel.Text", "Non defini");
        }
        // Toujours afficher le rayon (plus de mode diamètre)
        cmd.set("#RadiusField.Value", String.valueOf(mine.getRadius()));
        cmd.set("#HeightField.Value", String.valueOf(mine.getHeight()));

        // Radius Adjust
        cmd.set("#RadiusAdjustField.Value", String.format("%.2f", mine.getRadiusAdjust()));

        // Spawn - pour les deux modes
        if (mine.getSpawnPoint() != null) {
            ServerLocation sp = mine.getSpawnPoint();
            String spawnText = String.format("%.0f, %.0f, %.0f", sp.x(), sp.y(), sp.z());
            cmd.set("#SpawnLabel.Text", spawnText);
            cmd.set("#SpawnLabelCuboid.Text", spawnText);
        } else {
            cmd.set("#SpawnLabel.Text", "Non defini");
            cmd.set("#SpawnLabelCuboid.Text", "Non defini");
        }

        // Section Spawn séparée seulement en mode Cuboid
        cmd.set("#SpawnSection.Visible", !isCylinder);

        // Toggles (afficher une coche si actif)
        cmd.set("#NaturalModeCheck.Text", mine.isNaturalMode() ? "X" : "");
        cmd.set("#AutoResetCheck.Text", mine.isAutoReset() ? "X" : "");
        cmd.set("#UseLayersCheck.Text", mine.isUseLayerComposition() ? "X" : "");

        // Reset Interval
        cmd.set("#ResetIntervalField.Value", String.valueOf(mine.getResetIntervalMinutes()));

        // Village Margin
        cmd.set("#VillageMarginField.Value", String.valueOf(mine.getVillageMargin()));

        // Stats
        cmd.set("#TotalBlocksLabel.Text", String.valueOf(mine.getTotalBlocks()));
        cmd.set("#RemainingBlocksLabel.Text", String.valueOf(mine.getRemainingBlocks()));
        cmd.set("#PercentageLabel.Text", String.format("%.1f%%", mine.getRemainingPercentage()));

        // Composition
        buildCompositionList(cmd, event, mine);

        // Events pour toggle forme
        event.addEventBinding(CustomUIEventBindingType.Activating, "#ShapeCylinderCheckbox", EventData.of("Action", "setShapeCylinder"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#ShapeCuboidCheckbox", EventData.of("Action", "setShapeCuboid"), false);

        // Events pour les boutons de configuration (Cuboid)
        event.addEventBinding(CustomUIEventBindingType.Activating, "#SetCorner1Button", EventData.of("Action", "setCorner1"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#SetCorner2Button", EventData.of("Action", "setCorner2"), false);

        // Events pour les boutons de configuration (Cylindre)
        event.addEventBinding(CustomUIEventBindingType.Activating, "#SetCenterButton", EventData.of("Action", "setCenter"), false);
        event.addEventBinding(CustomUIEventBindingType.ValueChanged, "#RadiusField", EventData.of("@Radius", "#RadiusField.Value"), false);
        event.addEventBinding(CustomUIEventBindingType.ValueChanged, "#HeightField", EventData.of("@Height", "#HeightField.Value"), false);
        event.addEventBinding(CustomUIEventBindingType.ValueChanged, "#RadiusAdjustField", EventData.of("@RadiusAdjust", "#RadiusAdjustField.Value"), false);

        // Events communs - Spawn (pour les deux modes)
        event.addEventBinding(CustomUIEventBindingType.Activating, "#SetSpawnButton", EventData.of("Action", "setSpawn"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#SetSpawnButtonCuboid", EventData.of("Action", "setSpawn"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#NaturalModeCheckbox", EventData.of("Toggle", "naturalMode"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#AutoResetCheckbox", EventData.of("Toggle", "autoReset"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#UseLayersCheckbox", EventData.of("Toggle", "useLayers"), false);
        event.addEventBinding(CustomUIEventBindingType.ValueChanged, "#DisplayNameField", EventData.of("@DisplayName", "#DisplayNameField.Value"), false);
        event.addEventBinding(CustomUIEventBindingType.ValueChanged, "#RequiredRankField", EventData.of("@RequiredRank", "#RequiredRankField.Value"), false);
        event.addEventBinding(CustomUIEventBindingType.ValueChanged, "#ResetIntervalField", EventData.of("@ResetInterval", "#ResetIntervalField.Value"), false);
        event.addEventBinding(CustomUIEventBindingType.ValueChanged, "#VillageMarginField", EventData.of("@VillageMargin", "#VillageMarginField.Value"), false);

        // Events actions
        event.addEventBinding(CustomUIEventBindingType.Activating, "#ResetMineButton", EventData.of("Action", "resetMine"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#ClearZoneButton", EventData.of("Action", "clearZone"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#ScanMineButton", EventData.of("Action", "scanMine"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#TeleportButton", EventData.of("Action", "teleport"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#VisualizeButton", EventData.of("Action", "visualize"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#VisualizeVillageButton", EventData.of("Action", "visualizeVillage"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#SaveButton", new EventData(Map.of(
                "Action", "saveMine",
                "@Radius", "#RadiusField.Value",
                "@Height", "#HeightField.Value"
        )), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#DeleteButton", EventData.of("Action", "deleteMine"), false);

        // Events composition - le bouton + doit inclure les valeurs des champs
        event.addEventBinding(CustomUIEventBindingType.Activating, "#AddBlockButton", new EventData(Map.of(
                "Action", "addBlock",
                "@BlockType", "#BlockTypeField.Value",
                "@BlockPercent", "#BlockPercentField.Value"
        )), false);

        // Bouton "?" pour prendre l'ID du bloc en main
        event.addEventBinding(CustomUIEventBindingType.Activating, "#PickBlockButton", EventData.of("Action", "pickBlock"), false);

        // Bouton pour ajouter de l'air
        event.addEventBinding(CustomUIEventBindingType.Activating, "#AddAirButton", new EventData(Map.of(
                "Action", "addAir",
                "@BlockPercent", "#BlockPercentField.Value"
        )), false);
    }

    private void buildCompositionList(UICommandBuilder cmd, UIEventBuilder event, Mine mine) {
        // Afficher les éléments de composition
        cmd.set("#MineStats.Visible", true);
        cmd.set("#BlocksLabel.Visible", true);
        cmd.set("#AddBlockRow.Visible", true);
        cmd.set("#NoMineSelectedComp.Visible", false);

        cmd.clear("#BlockList");

        Map<String, Double> composition = mine.getComposition();
        int maxLayer = mine.getLayerCount() > 0 ? mine.getLayerCount() - 1 : 0;

        // Info couches en haut
        cmd.appendInline("#BlockList", "Label { Text: \"Couches: 0-" + maxLayer + " (clic=edit)\"; Anchor: (Height: 16); Style: (FontSize: 9, TextColor: #6bc5ff, HorizontalAlignment: Center); }");

        if (composition.isEmpty()) {
            cmd.appendInline("#BlockList", "Label { Text: \"Aucun bloc\"; Anchor: (Height: 25); Style: (FontSize: 11, TextColor: #808080, HorizontalAlignment: Center); }");
        } else {
            int index = 0;
            for (Map.Entry<String, Double> entry : composition.entrySet()) {
                String blockType = entry.getKey();
                double percentage = entry.getValue();
                boolean isDisabled = mine.isBlockDisabled(blockType);
                boolean isEditing = blockType.equals(editingBlockType);
                String rowId = "Row" + index;
                String toggleId = "Tog" + index;
                String editBtnId = "Edit" + index;
                String delBtnId = "Del" + index;

                // Limites de couches
                int[] limits = mine.getBlockLayerLimits(blockType);
                String layerInfo = "";
                if (limits != null && (limits[0] != -1 || limits[1] != -1)) {
                    String minStr = limits[0] != -1 ? String.valueOf(limits[0]) : "0";
                    String maxStr = limits[1] != -1 ? String.valueOf(limits[1]) : String.valueOf(maxLayer);
                    layerInfo = " [" + minStr + "-" + maxStr + "]";
                }

                // Texte du bloc
                String displayText = blockType + " " + String.format("%.1f%%", percentage) + layerInfo;

                // Couleurs selon état
                String rowBg = isEditing ? "#2a3a4a" : (isDisabled ? "#1a1a1a" : "#151d28");
                String textColor = isDisabled ? "#606060" : "#bfcdd5";
                String togBg = isDisabled ? "#5a2a2a" : "#2a5a2a";
                String togText = isDisabled ? "OFF" : "ON";
                String togColor = isDisabled ? "#ff6b6b" : "#6bff6b";
                String editBg = isEditing ? "#4a6a8a" : "#2a4a6a";

                // Ligne: [ON/OFF] [Nom %] [L] [X]
                cmd.appendInline("#BlockList", "Group #" + rowId + " { Anchor: (Height: 28, Bottom: 2); Background: (Color: " + rowBg + "); LayoutMode: Left; Padding: (Horizontal: 4); Button #" + toggleId + " { Anchor: (Width: 36, Height: 22); Background: (Color: " + togBg + "); Label { Text: \"" + togText + "\"; Style: (FontSize: 9, TextColor: " + togColor + ", HorizontalAlignment: Center, VerticalAlignment: Center); } } Label #Lbl { Anchor: (Left: 4); FlexWeight: 1; Style: (FontSize: 10, TextColor: " + textColor + ", VerticalAlignment: Center); } Button #" + editBtnId + " { Anchor: (Width: 22, Height: 22); Background: (Color: " + editBg + "); Label { Text: \"L\"; Style: (FontSize: 10, TextColor: #6bc5ff, HorizontalAlignment: Center, VerticalAlignment: Center); } } Button #" + delBtnId + " { Anchor: (Width: 22, Height: 22, Left: 2); Background: (Color: #5a2a2a); Label { Text: \"X\"; Style: (FontSize: 10, TextColor: #ff6b6b, HorizontalAlignment: Center, VerticalAlignment: Center); } } }");
                cmd.set("#" + rowId + " #Lbl.Text", displayText);

                // Events
                event.addEventBinding(CustomUIEventBindingType.Activating, "#" + toggleId, EventData.of("ToggleBlock", blockType), false);
                event.addEventBinding(CustomUIEventBindingType.Activating, "#" + editBtnId, EventData.of("EditLayerLimits", blockType), false);
                event.addEventBinding(CustomUIEventBindingType.Activating, "#" + delBtnId, EventData.of("RemoveBlock", blockType), false);
                index++;
            }

            // Total (seulement blocs actifs)
            double totalAll = composition.values().stream().mapToDouble(Double::doubleValue).sum();
            double totalActive = composition.entrySet().stream()
                    .filter(e -> !mine.isBlockDisabled(e.getKey()))
                    .mapToDouble(Map.Entry::getValue)
                    .sum();
            String totalColor = Math.abs(totalActive - 100.0) < 0.1 ? "#00ff7f" : "#ff6b6b";
            String totalText = String.format("Actif: %.1f%% / Total: %.1f%%", totalActive, totalAll);
            cmd.appendInline("#BlockList", "Label { Text: \"" + totalText + "\"; Anchor: (Height: 22, Top: 6); Style: (FontSize: 10, TextColor: " + totalColor + ", HorizontalAlignment: Center); }");
        }

        // Section d'édition des limites de couches
        if (editingBlockType != null && composition.containsKey(editingBlockType)) {
            int[] limits = mine.getBlockLayerLimits(editingBlockType);
            String minVal = (limits != null && limits[0] != -1) ? String.valueOf(limits[0]) : "";
            String maxVal = (limits != null && limits[1] != -1) ? String.valueOf(limits[1]) : "";

            cmd.set("#LayerEditSection.Visible", true);
            cmd.set("#LayerEditTitle.Text", "Couches: " + editingBlockType);
            cmd.set("#LayerMinField.Value", minVal);
            cmd.set("#LayerMinField.PlaceholderText", "0");
            cmd.set("#LayerMaxField.Value", maxVal);
            cmd.set("#LayerMaxField.PlaceholderText", String.valueOf(maxLayer));

            event.addEventBinding(CustomUIEventBindingType.Activating, "#ApplyLayerBtn", new EventData(Map.of(
                    "ApplyLayerLimits", editingBlockType,
                    "@MinLayer", "#LayerMinField.Value",
                    "@MaxLayer", "#LayerMaxField.Value"
            )), false);
            event.addEventBinding(CustomUIEventBindingType.Activating, "#ClearLayerBtn", EventData.of("ClearLayerLimits", editingBlockType), false);
        } else {
            cmd.set("#LayerEditSection.Visible", false);
        }
    }

    private void buildCreateMineForm(UICommandBuilder cmd, UIEventBuilder event) {
        cmd.set("#ConfigSection.Visible", false);
        cmd.set("#NoMineSelected.Visible", false);
        // Masquer les éléments de composition
        cmd.set("#MineStats.Visible", false);
        cmd.set("#BlocksLabel.Visible", false);
        cmd.clear("#BlockList");
        cmd.set("#AddBlockRow.Visible", false);
        cmd.set("#NoMineSelectedComp.Visible", false);

        // Afficher un formulaire de creation dans la zone config
        cmd.clear("#ConfigSection");
        cmd.set("#ConfigSection.Visible", true);

        cmd.appendInline("#ConfigSection", "Group { FlexWeight: 1; LayoutMode: Top; Label { Text: \"Nouvelle Mine\"; Anchor: (Height: 35); Style: (FontSize: 18, TextColor: #ffd700, RenderBold: true); } Label { Text: \"Identifiant unique:\"; Anchor: (Height: 22, Top: 10); Style: (FontSize: 12, TextColor: #96a9be); } TextField #NewMineIdField { Anchor: (Height: 32); PlaceholderText: \"mine_a\"; } Label { Text: \"Nom d'affichage:\"; Anchor: (Height: 22, Top: 8); Style: (FontSize: 12, TextColor: #96a9be); } TextField #NewMineNameField { Anchor: (Height: 32); PlaceholderText: \"Mine A\"; } Label { Text: \"Rang requis:\"; Anchor: (Height: 22, Top: 8); Style: (FontSize: 12, TextColor: #96a9be); } TextField #NewMineRankField { Anchor: (Height: 32); PlaceholderText: \"A\"; } Group { Anchor: (Height: 50, Top: 20); LayoutMode: Left; TextButton #ConfirmCreateBtn { Anchor: (Width: 100, Height: 40); Text: \"Creer\"; Style: TextButtonStyle( Default: ( Background: #2d5a2d, LabelStyle: (FontSize: 13, TextColor: #ffffff, HorizontalAlignment: Center, VerticalAlignment: Center, RenderBold: true) ), Hovered: (Background: #3d7a3d) ); } TextButton #CancelCreateBtn { Anchor: (Width: 100, Left: 10, Height: 40); Text: \"Annuler\"; Style: TextButtonStyle( Default: ( Background: #3a3a4a, LabelStyle: (FontSize: 13, TextColor: #ffffff, HorizontalAlignment: Center, VerticalAlignment: Center) ), Hovered: (Background: #4a4a5a) ); } } }");

        // Events - Le bouton Créer doit inclure les valeurs des champs
        event.addEventBinding(CustomUIEventBindingType.Activating, "#ConfirmCreateBtn", new EventData(Map.of(
                "Action", "confirmCreate",
                "@NewMineId", "#NewMineIdField.Value",
                "@NewMineName", "#NewMineNameField.Value",
                "@NewMineRank", "#NewMineRankField.Value"
        )), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#CancelCreateBtn", EventData.of("Action", "cancelCreate"), false);
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        super.handleDataEvent(ref, store, data);

        // === DEBUG LOGS ===
        plugin.log(java.util.logging.Level.INFO, "[MineManagerPage] handleDataEvent called!");
        plugin.log(java.util.logging.Level.INFO, "[MineManagerPage] data.action=" + data.action
                + " | data.selectMine=" + data.selectMine
                + " | data.toggle=" + data.toggle
                + " | data.radius=" + data.radius
                + " | data.height=" + data.height
                + " | data.radiusAdjust=" + data.radiusAdjust
                + " | selectedMineId=" + selectedMineId);

        PlayerRef playerRef = store.getComponent(ref, PlayerRef.getComponentType());
        Player player = store.getComponent(ref, Player.getComponentType());

        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder event = new UIEventBuilder();

        // Selection d'une mine
        if (data.selectMine != null) {
            selectedMineId = data.selectMine;
            createMode = false;
            // Utiliser rebuild() au lieu de sendUpdate() car appendInline ne fonctionne pas dans sendUpdate
            rebuild();
            return;
        }

        // Toggle d'une option
        if (data.toggle != null && selectedMineId != null) {
            Mine mine = plugin.getMineManager().getMine(selectedMineId);
            if (mine != null) {
                switch (data.toggle) {
                    case "naturalMode" -> mine.setNaturalMode(!mine.isNaturalMode());
                    case "autoReset" -> mine.setAutoReset(!mine.isAutoReset());
                    case "useLayers" -> mine.setUseLayerComposition(!mine.isUseLayerComposition());
                }
                plugin.getMineManager().saveMine(mine);
                refreshPage(cmd, event);
                sendUpdate(cmd, event, false);
            }
            return;
        }

        // Toggle enabled/disabled d'un bloc de composition
        if (data.toggleBlock != null && selectedMineId != null) {
            Mine mine = plugin.getMineManager().getMine(selectedMineId);
            if (mine != null) {
                String blockType = data.toggleBlock;
                boolean nowEnabled = mine.toggleBlockEnabled(blockType);
                plugin.getMineManager().saveMine(mine);

                refreshPage(cmd, event);
                sendUpdate(cmd, event, false);
                NotificationUtil.send(player, NotificationType.INFO, "Bloc " + blockType + (nowEnabled ? " active" : " desactive"));
            }
            return;
        }

        // Ouvrir l'éditeur de limites de couches
        if (data.editLayerLimits != null && selectedMineId != null) {
            editingBlockType = data.editLayerLimits;
            refreshPage(cmd, event);
            sendUpdate(cmd, event, false);
            return;
        }

        // Appliquer les limites de couches
        if (data.applyLayerLimits != null && selectedMineId != null) {
            Mine mine = plugin.getMineManager().getMine(selectedMineId);
            if (mine != null) {
                String blockType = data.applyLayerLimits;
                int minLayer = -1;
                int maxLayer = -1;

                if (data.minLayer != null && !data.minLayer.isBlank()) {
                    try { minLayer = Integer.parseInt(data.minLayer); } catch (NumberFormatException ignored) {}
                }
                if (data.maxLayer != null && !data.maxLayer.isBlank()) {
                    try { maxLayer = Integer.parseInt(data.maxLayer); } catch (NumberFormatException ignored) {}
                }

                mine.setBlockLayerLimits(blockType, minLayer, maxLayer);
                plugin.getMineManager().saveMine(mine);
                editingBlockType = null; // Fermer l'éditeur
                refreshPage(cmd, event);
                sendUpdate(cmd, event, false);
                NotificationUtil.send(player, NotificationType.INFO, "Limites appliquees pour " + blockType);
            }
            return;
        }

        // Effacer les limites de couches
        if (data.clearLayerLimits != null && selectedMineId != null) {
            Mine mine = plugin.getMineManager().getMine(selectedMineId);
            if (mine != null) {
                String blockType = data.clearLayerLimits;
                mine.setBlockLayerLimits(blockType, -1, -1);
                plugin.getMineManager().saveMine(mine);
                editingBlockType = null; // Fermer l'éditeur
                refreshPage(cmd, event);
                sendUpdate(cmd, event, false);
                NotificationUtil.send(player, NotificationType.INFO, "Limites effacees pour " + blockType);
            }
            return;
        }

        // Mise à jour des limites de couches d'un bloc (sauvegarde automatique à la modification)
        if (data.updateBlockLayer != null && selectedMineId != null) {
            Mine mine = plugin.getMineManager().getMine(selectedMineId);
            if (mine != null) {
                String blockType = data.updateBlockLayer;

                int minLayer = -1;
                int maxLayer = -1;

                if (data.minLayer != null && !data.minLayer.isBlank()) {
                    try { minLayer = Integer.parseInt(data.minLayer); } catch (NumberFormatException ignored) {}
                }
                if (data.maxLayer != null && !data.maxLayer.isBlank()) {
                    try { maxLayer = Integer.parseInt(data.maxLayer); } catch (NumberFormatException ignored) {}
                }

                // Stocker les valeurs telles quelles (pas de conversion)
                // -1 signifie "pas de limite" (champ vide)
                mine.setBlockLayerLimits(blockType, minLayer, maxLayer);
                plugin.getMineManager().saveMine(mine);
                // Pas de message ni refresh pour éviter le spam pendant la saisie
            }
            return;
        }

        // Suppression d'un bloc de composition
        if (data.removeBlock != null && selectedMineId != null) {
            Mine mine = plugin.getMineManager().getMine(selectedMineId);
            if (mine != null) {
                String removedBlock = data.removeBlock;
                mine.getComposition().remove(removedBlock);
                // Aussi supprimer de la liste des blocs désactivés et des limites de couches
                mine.getDisabledBlocks().remove(removedBlock);
                mine.getBlockLayerLimits().remove(removedBlock);
                plugin.getMineManager().saveMine(mine);

                // Pré-remplir le champ avec le bloc supprimé pour faciliter le ré-ajout
                cmd.set("#BlockTypeField.Value", removedBlock);
                cmd.set("#BlockPercentField.Value", "");

                refreshPage(cmd, event);
                sendUpdate(cmd, event, false);
                NotificationUtil.send(player, NotificationType.SUCCESS, "Bloc " + removedBlock + " retire. Nom copie dans le champ.");
            }
            return;
        }

        // Modification du rayon (via ValueChanged sur #RadiusField)
        if (data.radius != null && !data.radius.isBlank() && selectedMineId != null && data.action == null) {
            // Ignorer si c'est un changement causé par le toggle diamètre
            if (ignoringRadiusChange) {
                ignoringRadiusChange = false;
                return;
            }
            Mine mine = plugin.getMineManager().getMine(selectedMineId);
            if (mine != null) {
                try {
                    int value = Integer.parseInt(data.radius);
                    // Si en mode diamètre, convertir en rayon
                    int radius = mine.isUseDiameterMode() ? value / 2 : value;
                    if (radius != mine.getRadius() && radius > 0) {
                        mine.setRadius(radius);
                        plugin.getMineManager().saveMine(mine);
                        // Pas de message pour éviter le spam lors de la frappe
                    }
                } catch (NumberFormatException ignored) {}
            }
            return;
        }

        // Modification de la hauteur (via ValueChanged sur #HeightField)
        if (data.height != null && !data.height.isBlank() && selectedMineId != null && data.action == null) {
            Mine mine = plugin.getMineManager().getMine(selectedMineId);
            if (mine != null) {
                try {
                    int h = Integer.parseInt(data.height);
                    if (h != mine.getHeight() && h > 0) {
                        mine.setHeight(h);
                        plugin.getMineManager().saveMine(mine);
                    }
                } catch (NumberFormatException ignored) {}
            }
            return;
        }

        // Modification du radiusAdjust (via ValueChanged sur #RadiusAdjustField)
        if (data.radiusAdjust != null && !data.radiusAdjust.isBlank() && selectedMineId != null && data.action == null) {
            Mine mine = plugin.getMineManager().getMine(selectedMineId);
            if (mine != null) {
                try {
                    double adj = Double.parseDouble(data.radiusAdjust.replace(",", "."));
                    if (Math.abs(adj - mine.getRadiusAdjust()) > 0.001) {
                        mine.setRadiusAdjust(adj);
                        plugin.getMineManager().saveMine(mine);
                    }
                } catch (NumberFormatException ignored) {}
            }
            return;
        }

        // Modification du resetInterval (via ValueChanged sur #ResetIntervalField)
        if (data.resetInterval != null && !data.resetInterval.isBlank() && selectedMineId != null && data.action == null) {
            Mine mine = plugin.getMineManager().getMine(selectedMineId);
            if (mine != null) {
                try {
                    int interval = Integer.parseInt(data.resetInterval);
                    if (interval != mine.getResetIntervalMinutes() && interval >= 0) {
                        mine.setResetIntervalMinutes(interval);
                        plugin.getMineManager().saveMine(mine);
                    }
                } catch (NumberFormatException ignored) {}
            }
            return;
        }

        // Modification du villageMargin (via ValueChanged sur #VillageMarginField)
        if (data.villageMargin != null && !data.villageMargin.isBlank() && selectedMineId != null && data.action == null) {
            Mine mine = plugin.getMineManager().getMine(selectedMineId);
            if (mine != null) {
                try {
                    int margin = Integer.parseInt(data.villageMargin);
                    if (margin != mine.getVillageMargin() && margin >= 0) {
                        mine.setVillageMargin(margin);
                        plugin.getMineManager().saveMine(mine);
                    }
                } catch (NumberFormatException ignored) {}
            }
            return;
        }

        // Actions
        if (data.action != null) {
            handleAction(data, player, playerRef, cmd, event);
            return;
        }

        plugin.log(java.util.logging.Level.WARNING, "[MineManagerPage] handleDataEvent: NO handler matched! action=" + data.action + " selectMine=" + data.selectMine + " toggle=" + data.toggle);
    }

    private void handleAction(PageData data, Player player, PlayerRef playerRef, UICommandBuilder cmd, UIEventBuilder event) {
        plugin.log(java.util.logging.Level.INFO, "[MineManagerPage] handleAction: action=" + data.action + " | selectedMineId=" + selectedMineId);
        switch (data.action) {
            case "showCreateMine" -> {
                createMode = true;
                selectedMineId = null;
                refreshPage(cmd, event);
                sendUpdate(cmd, event, false);
            }

            case "cancelCreate" -> {
                createMode = false;
                refreshPage(cmd, event);
                sendUpdate(cmd, event, false);
            }

            case "confirmCreate" -> {
                if (data.newMineId != null && !data.newMineId.isBlank()) {
                    String mineId = data.newMineId.toLowerCase().replace(" ", "_");
                    String displayName = data.newMineName != null && !data.newMineName.isBlank() ? data.newMineName : mineId;
                    String rank = data.newMineRank != null && !data.newMineRank.isBlank() ? data.newMineRank : mineId;

                    if (plugin.getMineManager().getMine(mineId) != null) {
                        NotificationUtil.send(player, NotificationType.ERROR, "Une mine avec cet ID existe deja!");
                        return;
                    }

                    Mine mine = new Mine(mineId);
                    mine.setDisplayName(displayName);
                    mine.setRequiredRank(rank);
                    plugin.getMineManager().addMine(mine);
                    plugin.getMineManager().saveMine(mine);

                    NotificationUtil.send(player, NotificationType.SUCCESS, "Mine '" + displayName + "' creee avec succes!");

                    createMode = false;
                    selectedMineId = mineId;
                    refreshPage(cmd, event);
                    sendUpdate(cmd, event, false);
                } else {
                    NotificationUtil.send(player, NotificationType.ERROR, "Veuillez specifier un ID pour la mine.");
                }
            }

            case "setShapeCylinder" -> {
                if (selectedMineId != null) {
                    Mine mine = plugin.getMineManager().getMine(selectedMineId);
                    if (mine != null) {
                        // Activer le mode cylindre
                        mine.setCylinderMode(true);
                        // Convertir en cylindre - garder le centre au milieu des anciens coins si possible
                        if (mine.getCorner1() != null && mine.getCorner2() != null) {
                            ServerLocation c1 = mine.getCorner1();
                            ServerLocation c2 = mine.getCorner2();
                            double cx = (c1.x() + c2.x()) / 2;
                            double cy = Math.min(c1.y(), c2.y());
                            double cz = (c1.z() + c2.z()) / 2;
                            int h = (int) Math.abs(c2.y() - c1.y()) + 1;
                            int r = (int) Math.max(Math.abs(c2.x() - c1.x()), Math.abs(c2.z() - c1.z())) / 2;
                            mine.setCylinder(new ServerLocation(c1.server(), c1.world(), cx, cy, cz, 0f, 0f), Math.max(1, r), Math.max(1, h));
                        } else {
                            // Valeurs par défaut
                            mine.setRadius(10);
                            mine.setHeight(10);
                        }
                        plugin.getMineManager().saveMine(mine);
                        NotificationUtil.send(player, NotificationType.SUCCESS, "Forme changee en Cylindre.");
                        refreshPage(cmd, event);
                        sendUpdate(cmd, event, false);
                    }
                }
            }

            case "setShapeCuboid" -> {
                if (selectedMineId != null) {
                    Mine mine = plugin.getMineManager().getMine(selectedMineId);
                    if (mine != null) {
                        // Convertir en cuboid - créer un cube autour du centre si possible
                        if (mine.getCenter() != null) {
                            ServerLocation center = mine.getCenter();
                            int r = mine.getRadius();
                            int h = mine.getHeight();
                            mine.setCorner1(new ServerLocation(center.server(), center.world(), center.x() - r, center.y(), center.z() - r, 0f, 0f));
                            mine.setCorner2(new ServerLocation(center.server(), center.world(), center.x() + r, center.y() + h - 1, center.z() + r, 0f, 0f));
                        }
                        // Désactiver le mode cylindre et clear les données
                        mine.setCylinderMode(false);
                        mine.setCenter(null);
                        mine.setRadius(0);
                        mine.setHeight(0);
                        plugin.getMineManager().saveMine(mine);
                        NotificationUtil.send(player, NotificationType.SUCCESS, "Forme changee en Cuboid.");
                        refreshPage(cmd, event);
                        sendUpdate(cmd, event, false);
                    }
                }
            }

            case "setCorner1", "setCorner2", "setSpawn", "setCenter" -> {
                if (selectedMineId != null) {
                    IslandiumPlayer islandiumPlayer = plugin.getCore().getPlayerManager()
                            .getOnlinePlayer(player.getUuid())
                            .orElse(null);

                    if (islandiumPlayer == null) {
                        NotificationUtil.send(player, NotificationType.ERROR, "Impossible d'obtenir votre position.");
                        return;
                    }

                    ServerLocation loc = islandiumPlayer.getLocation();
                    if (loc == null) {
                        NotificationUtil.send(player, NotificationType.ERROR, "Impossible d'obtenir votre position.");
                        return;
                    }

                    Mine mine = plugin.getMineManager().getMine(selectedMineId);
                    if (mine != null) {
                        switch (data.action) {
                            case "setCorner1" -> {
                                mine.setCorner1(loc);
                                NotificationUtil.send(player, NotificationType.SUCCESS, String.format("Coin 1 defini a %.0f, %.0f, %.0f", loc.x(), loc.y(), loc.z()));
                            }
                            case "setCorner2" -> {
                                mine.setCorner2(loc);
                                NotificationUtil.send(player, NotificationType.SUCCESS, String.format("Coin 2 defini a %.0f, %.0f, %.0f", loc.x(), loc.y(), loc.z()));
                            }
                            case "setSpawn" -> {
                                // Le spawn peut être défini indépendamment du centre (cylindre ou cuboid)
                                mine.setSpawnPoint(loc);
                                NotificationUtil.send(player, NotificationType.SUCCESS, String.format("Spawn defini a %.0f, %.0f, %.0f", loc.x(), loc.y(), loc.z()));
                            }
                            case "setCenter" -> {
                                // Arrondir aux coordonnées de bloc pour éviter les décalages
                                ServerLocation blockLoc = ServerLocation.of(
                                        loc.server(),
                                        loc.world(),
                                        Math.floor(loc.x()),
                                        Math.floor(loc.y()),
                                        Math.floor(loc.z()),
                                        0f, 0f
                                );
                                mine.setCenter(blockLoc);
                                // Forcer la mise à jour du spawn au centre (écrase l'ancien spawn)
                                mine.updateSpawnToCenter();
                                ServerLocation newSpawn = mine.getSpawnPoint();
                                NotificationUtil.send(player, NotificationType.SUCCESS, String.format("Centre defini a %.0f, %.0f, %.0f", blockLoc.x(), blockLoc.y(), blockLoc.z()));
                                if (newSpawn != null) {
                                    NotificationUtil.send(player, NotificationType.INFO, String.format("Spawn auto: %.2f, %.2f, %.2f", newSpawn.x(), newSpawn.y(), newSpawn.z()));
                                }
                                // Effacer l'ancienne visualisation et en afficher une nouvelle
                                clearVisualization(player);
                                visualizationActive = true;
                                sendMineVisualization(player, mine);
                            }
                        }
                        plugin.getMineManager().saveMine(mine);
                        refreshPage(cmd, event);
                        sendUpdate(cmd, event, false);
                    }
                }
            }

            case "resetMine" -> {
                plugin.log(java.util.logging.Level.INFO, "[MineManagerPage] >>> RESET MINE clicked! selectedMineId=" + selectedMineId);
                if (selectedMineId != null) {
                    Mine mine = plugin.getMineManager().getMine(selectedMineId);
                    plugin.log(java.util.logging.Level.INFO, "[MineManagerPage] >>> RESET mine object=" + (mine != null ? mine.getId() : "NULL"));
                    if (mine != null) {
                        plugin.log(java.util.logging.Level.INFO, "[MineManagerPage] >>> RESET calling resetMine()...");
                        plugin.getMineManager().resetMine(mine);
                        NotificationUtil.send(player, NotificationType.SUCCESS, "Mine '" + mine.getDisplayName() + "' reset!");
                        // rebuild() car appendInline ne fonctionne pas dans sendUpdate
                        rebuild();
                        plugin.log(java.util.logging.Level.INFO, "[MineManagerPage] >>> RESET done, rebuild() called");
                    }
                } else {
                    plugin.log(java.util.logging.Level.WARNING, "[MineManagerPage] >>> RESET FAILED: selectedMineId is null!");
                }
            }

            case "clearZone" -> {
                plugin.log(java.util.logging.Level.INFO, "[MineManagerPage] >>> CLEAR ZONE clicked! selectedMineId=" + selectedMineId);
                if (selectedMineId != null) {
                    Mine mine = plugin.getMineManager().getMine(selectedMineId);
                    plugin.log(java.util.logging.Level.INFO, "[MineManagerPage] >>> CLEAR mine object=" + (mine != null ? mine.getId() : "NULL") + " | configured=" + (mine != null ? mine.isConfigured() : "N/A"));
                    if (mine != null && mine.isConfigured()) {
                        plugin.log(java.util.logging.Level.INFO, "[MineManagerPage] >>> CLEAR calling clearMine()...");
                        plugin.getMineManager().clearMine(mine);
                        NotificationUtil.send(player, NotificationType.INFO, "Zone de la mine '" + mine.getDisplayName() + "' videe!");
                        // rebuild() car appendInline ne fonctionne pas dans sendUpdate
                        rebuild();
                        plugin.log(java.util.logging.Level.INFO, "[MineManagerPage] >>> CLEAR done, rebuild() called");
                    } else {
                        plugin.log(java.util.logging.Level.WARNING, "[MineManagerPage] >>> CLEAR FAILED: mine not configured!");
                        NotificationUtil.send(player, NotificationType.ERROR, "La mine n'est pas configuree.");
                    }
                } else {
                    plugin.log(java.util.logging.Level.WARNING, "[MineManagerPage] >>> CLEAR FAILED: selectedMineId is null!");
                }
            }

            case "scanMine" -> {
                if (selectedMineId != null) {
                    Mine mine = plugin.getMineManager().getMine(selectedMineId);
                    if (mine != null && mine.isConfigured()) {
                        Map<String, Integer> blocks = plugin.getMineManager().scanMineBlocks(mine);
                        if (!blocks.isEmpty()) {
                            // Convertir en pourcentages
                            int total = blocks.values().stream().mapToInt(Integer::intValue).sum();
                            mine.getComposition().clear();
                            for (Map.Entry<String, Integer> entry : blocks.entrySet()) {
                                double percent = (entry.getValue() * 100.0) / total;
                                mine.addBlock(entry.getKey(), Math.round(percent * 10.0) / 10.0);
                            }
                            plugin.getMineManager().saveMine(mine);
                            NotificationUtil.send(player, NotificationType.INFO, "Mine scannee! " + blocks.size() + " types de blocs trouves.");
                            refreshPage(cmd, event);
                            sendUpdate(cmd, event, false);
                        } else {
                            NotificationUtil.send(player, NotificationType.WARNING, "Aucun bloc trouve dans la mine.");
                        }
                    } else {
                        NotificationUtil.send(player, NotificationType.ERROR, "La mine n'est pas configuree (coins manquants).");
                    }
                }
            }

            case "teleport" -> {
                if (selectedMineId != null) {
                    Mine mine = plugin.getMineManager().getMine(selectedMineId);
                    if (mine != null && mine.hasSpawn()) {
                        ServerLocation spawn = mine.getSpawnPoint();
                        // Utiliser TeleportService pour sauvegarder /back et centraliser les TP
                        try {
                            var islandiumPlayerOpt = plugin.getCore().getPlayerManager().getOnlinePlayer(player.getUuid());
                            if (islandiumPlayerOpt.isPresent()) {
                                plugin.getCore().getTeleportService().teleportInstant(islandiumPlayerOpt.get(), spawn);
                                NotificationUtil.send(player, NotificationType.INFO, "Teleporte a la mine " + mine.getDisplayName());
                            } else {
                                NotificationUtil.send(player, NotificationType.ERROR, "Erreur: joueur non trouve");
                            }
                        } catch (Exception e) {
                            NotificationUtil.send(player, NotificationType.ERROR, "Erreur teleportation: " + e.getMessage());
                        }
                    } else {
                        NotificationUtil.send(player, NotificationType.ERROR, "Cette mine n'a pas de point de spawn defini.");
                    }
                }
            }

            case "visualize" -> {
                if (selectedMineId != null) {
                    Mine mine = plugin.getMineManager().getMine(selectedMineId);
                    if (mine != null) {
                        // Toggle ON/OFF
                        visualizationActive = !visualizationActive;
                        if (visualizationActive) {
                            sendMineVisualization(player, mine);
                            NotificationUtil.send(player, NotificationType.INFO, "Visualisation activee (5 min).");
                        } else {
                            clearVisualization(player);
                            NotificationUtil.send(player, NotificationType.INFO, "Visualisation desactivee.");
                        }
                    }
                }
            }

            case "visualizeVillage" -> {
                if (selectedMineId != null) {
                    Mine mine = plugin.getMineManager().getMine(selectedMineId);
                    if (mine != null && mine.hasVillageZone()) {
                        clearVisualization(player);
                        sendVillageVisualization(player, mine);
                        NotificationUtil.send(player, NotificationType.INFO, "Visualisation village activee (5 min).");
                    } else {
                        NotificationUtil.send(player, NotificationType.WARNING, "Village margin est a 0 pour cette mine.");
                    }
                }
            }

            case "saveMine" -> {
                if (selectedMineId != null) {
                    Mine mine = plugin.getMineManager().getMine(selectedMineId);
                    if (mine != null) {
                        // Appliquer les changements de champs texte
                        if (data.displayName != null && !data.displayName.isBlank()) {
                            mine.setDisplayName(data.displayName);
                        }
                        if (data.requiredRank != null && !data.requiredRank.isBlank()) {
                            mine.setRequiredRank(data.requiredRank);
                        }
                        // NOTE: Le rayon et la hauteur sont gérés par les events ValueChanged
                        // Le bouton Save ne modifie que displayName et requiredRank
                        plugin.getMineManager().saveMine(mine);
                        NotificationUtil.send(player, NotificationType.SUCCESS, "Mine sauvegardee!");
                        refreshPage(cmd, event);
                        sendUpdate(cmd, event, false);
                    }
                }
            }

            case "deleteMine" -> {
                if (selectedMineId != null) {
                    Mine mine = plugin.getMineManager().getMine(selectedMineId);
                    if (mine != null) {
                        plugin.getMineManager().removeMine(selectedMineId);
                        NotificationUtil.send(player, NotificationType.SUCCESS, "Mine '" + mine.getDisplayName() + "' supprimee!");
                        selectedMineId = null;
                        refreshPage(cmd, event);
                        sendUpdate(cmd, event, false);
                    }
                }
            }

            case "addBlock" -> {
                if (selectedMineId != null && data.blockType != null && !data.blockType.isBlank()) {
                    Mine mine = plugin.getMineManager().getMine(selectedMineId);
                    if (mine != null) {
                        double percent = 10.0; // Defaut
                        if (data.blockPercent != null && !data.blockPercent.isBlank()) {
                            try {
                                percent = Double.parseDouble(data.blockPercent.replace("%", ""));
                            } catch (NumberFormatException ignored) {}
                        }
                        mine.addBlock(data.blockType, percent);
                        plugin.getMineManager().saveMine(mine);
                        NotificationUtil.send(player, NotificationType.SUCCESS, "Bloc " + data.blockType + " ajoute (" + percent + "%)");

                        // Clear les champs
                        cmd.set("#BlockTypeField.Value", "");
                        cmd.set("#BlockPercentField.Value", "");

                        refreshPage(cmd, event);
                        sendUpdate(cmd, event, false);
                    }
                }
            }

            case "pickBlock" -> {
                // Récupérer l'item en main du joueur via l'inventaire
                Inventory inv = player.getInventory();
                if (inv != null) {
                    ItemStack mainHand = inv.getItemInHand();
                    if (mainHand != null && !mainHand.isEmpty()) {
                        String blockId = mainHand.getItemId();
                        cmd.set("#BlockTypeField.Value", blockId);
                        sendUpdate(cmd, event, false);
                        NotificationUtil.send(player, NotificationType.INFO, "Bloc selectionne: " + blockId);
                    } else {
                        NotificationUtil.send(player, NotificationType.ERROR, "Vous ne tenez aucun bloc en main!");
                    }
                } else {
                    NotificationUtil.send(player, NotificationType.ERROR, "Impossible de lire l'inventaire.");
                }
            }

            case "addAir" -> {
                // Ajouter de l'air à la composition
                if (selectedMineId != null) {
                    Mine mine = plugin.getMineManager().getMine(selectedMineId);
                    if (mine != null) {
                        double percent = 10.0; // Defaut
                        if (data.blockPercent != null && !data.blockPercent.isBlank()) {
                            try {
                                percent = Double.parseDouble(data.blockPercent.replace("%", ""));
                            } catch (NumberFormatException ignored) {}
                        }
                        mine.addBlock("air", percent);
                        plugin.getMineManager().saveMine(mine);
                        NotificationUtil.send(player, NotificationType.SUCCESS, "Air ajoute (" + percent + "%)");
                        refreshPage(cmd, event);
                        sendUpdate(cmd, event, false);
                    }
                }
            }

            default -> {
                plugin.log(java.util.logging.Level.WARNING, "[MineManagerPage] >>> UNKNOWN ACTION: '" + data.action + "'");
            }
        }
    }

    private void refreshPage(UICommandBuilder cmd, UIEventBuilder event) {
        buildMineList(cmd, event);

        if (selectedMineId != null && !createMode) {
            Mine mine = plugin.getMineManager().getMine(selectedMineId);
            if (mine != null) {
                buildMineEditor(cmd, event, mine);
            } else {
                hideCompositionElements(cmd);
            }
        } else if (createMode) {
            buildCreateMineForm(cmd, event);
        } else {
            hideCompositionElements(cmd);
        }
    }

    private void hideCompositionElements(UICommandBuilder cmd) {
        cmd.set("#ConfigSection.Visible", false);
        cmd.set("#NoMineSelected.Visible", true);
        cmd.set("#MineStats.Visible", false);
        cmd.set("#BlocksLabel.Visible", false);
        cmd.clear("#BlockList");
        cmd.set("#AddBlockRow.Visible", false);
        cmd.set("#LayerEditSection.Visible", false);
        cmd.set("#NoMineSelectedComp.Visible", true);
        editingBlockType = null;
    }

    // ==================== Visualisation de la mine ====================

    // Couleur de la zone (orange)
    private static final Vector3f MINE_COLOR = new Vector3f(1.0f, 0.6f, 0.2f);
    // Couleur de la zone village (violet)
    private static final Vector3f VILLAGE_COLOR = new Vector3f(0.6f, 0.2f, 0.8f);
    // Durée d'affichage en secondes (5 minutes)
    private static final float DISPLAY_DURATION = 300.0f;
    // Épaisseur des lignes
    private static final double LINE_THICKNESS = 0.08;

    /**
     * Envoie la visualisation de la zone de la mine au joueur.
     */
    @SuppressWarnings("deprecation")
    private void sendMineVisualization(Player player, Mine mine) {
        var connection = player.getPlayerConnection();
        if (connection == null) {
            return;
        }

        // Effacer les anciennes formes debug (envoyer plusieurs fois pour être sûr)
        connection.write(new ClearDebugShapes());
        connection.write(new ClearDebugShapes());

        NotificationUtil.send(player, NotificationType.INFO, "[DEBUG] Visualisation rafraichie");

        if (mine.isCylindrical()) {
            // Visualiser un cylindre
            if (mine.getCenter() != null) {
                sendCylinderVisualization(player, mine);
            } else {
                NotificationUtil.send(player, NotificationType.ERROR, "Le centre du cylindre n'est pas defini.");
                visualizationActive = false;
            }
        } else {
            // Visualiser un cuboid
            if (mine.getCorner1() != null && mine.getCorner2() != null) {
                sendCuboidVisualization(player, mine);
            } else {
                NotificationUtil.send(player, NotificationType.ERROR, "Les coins de la mine ne sont pas definis.");
                visualizationActive = false;
            }
        }
    }

    /**
     * Efface la visualisation.
     */
    @SuppressWarnings("deprecation")
    private void clearVisualization(Player player) {
        var connection = player.getPlayerConnection();
        if (connection != null) {
            connection.write(new ClearDebugShapes());
        }
    }

    /**
     * Visualise une mine cuboid (boîte).
     */
    @SuppressWarnings("deprecation")
    private void sendCuboidVisualization(Player player, Mine mine) {
        var connection = player.getPlayerConnection();
        if (connection == null) return;

        ServerLocation c1 = mine.getCorner1();
        ServerLocation c2 = mine.getCorner2();

        double minX = Math.min(c1.x(), c2.x());
        double minY = Math.min(c1.y(), c2.y());
        double minZ = Math.min(c1.z(), c2.z());
        double maxX = Math.max(c1.x(), c2.x()) + 1;
        double maxY = Math.max(c1.y(), c2.y()) + 1;
        double maxZ = Math.max(c1.z(), c2.z()) + 1;

        // Construire les 12 arêtes de la boîte
        List<DisplayDebug> packets = buildBoxEdges(minX, minY, minZ, maxX, maxY, maxZ);

        for (DisplayDebug packet : packets) {
            connection.write(packet);
        }
    }

    /**
     * Visualise une mine cylindrique avec un point sur chaque bloc du contour.
     * Utilise une grille (2*radius+1) x (2*radius+1) avec formule d'ellipse ajustée.
     *
     * Pour un rayon R, la grille va de -R à +R (soit 2R+1 positions).
     * halfW = halfH = R
     * radiusAdjust = 0.40 (pour avoir 13 blocs au bord quand R=50)
     * Condition d'inclusion: (x²/(halfW+adj)²) + (z²/(halfH+adj)²) < 1
     */
    @SuppressWarnings("deprecation")
    private void sendCylinderVisualization(Player player, Mine mine) {
        var connection = player.getPlayerConnection();
        if (connection == null) return;

        ServerLocation center = mine.getCenter();
        int radius = mine.getRadius();
        int height = mine.getHeight();

        // Centre en coordonnées de bloc
        int cx = (int) Math.floor(center.x());
        int cy = (int) Math.floor(center.y());
        int cz = (int) Math.floor(center.z());

        // Grille (2*radius+1) x (2*radius+1), de -radius à +radius
        int halfW = radius;
        int halfH = radius;
        double rX = halfW + mine.getRadiusAdjust();
        double rZ = halfH + mine.getRadiusAdjust();
        double rXSq = rX * rX;
        double rZSq = rZ * rZ;

        List<DisplayDebug> packets = new ArrayList<>();

        // Parcourir la grille de -radius à +radius
        for (int dz = -halfH; dz <= halfH; dz++) {
            for (int dx = -halfW; dx <= halfW; dx++) {
                // Formule de l'ellipse: (x²/rX²) + (z²/rZ²) < 1
                double distSq = (dx * dx) / rXSq + (dz * dz) / rZSq;
                if (distSq >= 1.0) continue; // Hors du cercle

                // Vérifier si c'est un bloc de contour (au moins un voisin hors du cercle)
                boolean isEdge = false;
                for (int[] offset : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int nx = dx + offset[0];
                    int nz = dz + offset[1];
                    double nDistSq = (nx * nx) / rXSq + (nz * nz) / rZSq;
                    if (nDistSq >= 1.0) {
                        isEdge = true;
                        break;
                    }
                }

                if (isEdge) {
                    // Position du bloc dans le monde (centre du bloc)
                    double bx = cx + dx + 0.5;
                    double bz = cz + dz + 0.5;

                    // Point en bas
                    packets.add(createSmallCube(bx, cy + 0.1, bz));
                    // Point en haut
                    packets.add(createSmallCube(bx, cy + height - 0.1, bz));

                    // Ligne verticale (tous les 4 blocs)
                    if ((dx + dz) % 4 == 0) {
                        packets.add(createEdge(bx, cy + height / 2.0, bz, LINE_THICKNESS, height, LINE_THICKNESS));
                    }
                }
            }
        }

        // Marqueur au centre
        packets.add(createEdge(cx + 0.5, cy + height / 2.0, cz + 0.5, 0.3, height + 2, 0.3));

        for (DisplayDebug packet : packets) {
            connection.write(packet);
        }
    }

    /**
     * Crée un petit cube pour marquer un point.
     */
    private DisplayDebug createSmallCube(double x, double y, double z) {
        Matrix4d matrix = new Matrix4d()
                .identity()
                .translate(x, y, z)
                .scale(0.2, 0.2, 0.2);

        return new DisplayDebug(
                DebugShape.Cube,
                matrix.asFloatData(),
                MINE_COLOR,
                DISPLAY_DURATION,
                true,
                null,
                1.0f
        );
    }

    /**
     * Construit un cercle horizontal avec des lignes connectées entre les points.
     */
    private List<DisplayDebug> buildCircleWithLines(double cx, double cy, double cz, double radius, int segments) {
        List<DisplayDebug> packets = new ArrayList<>();

        // Créer des segments de ligne le long du cercle
        for (int i = 0; i < segments; i++) {
            double angle1 = (2 * Math.PI * i) / segments;
            double angle2 = (2 * Math.PI * (i + 1)) / segments;

            double x1 = cx + radius * Math.cos(angle1);
            double z1 = cz + radius * Math.sin(angle1);
            double x2 = cx + radius * Math.cos(angle2);
            double z2 = cz + radius * Math.sin(angle2);

            // Créer une ligne entre les deux points en utilisant des petits cubes
            // On divise chaque segment en plusieurs petits cubes pour simuler une ligne
            int subSegments = 3;
            for (int j = 0; j < subSegments; j++) {
                double t = (j + 0.5) / subSegments;
                double px = x1 + t * (x2 - x1);
                double pz = z1 + t * (z2 - z1);

                Matrix4d matrix = new Matrix4d()
                        .identity()
                        .translate(px, cy, pz)
                        .scale(LINE_THICKNESS * 1.5, LINE_THICKNESS * 1.5, LINE_THICKNESS * 1.5);

                packets.add(new DisplayDebug(
                        DebugShape.Cube,
                        matrix.asFloatData(),
                        MINE_COLOR,
                        DISPLAY_DURATION,
                        true,
                        null,
                        1.0f
                ));
            }
        }

        return packets;
    }

    /**
     * Construit les 12 arêtes d'une boîte 3D.
     */
    private List<DisplayDebug> buildBoxEdges(double minX, double minY, double minZ,
                                              double maxX, double maxY, double maxZ) {
        List<DisplayDebug> packets = new ArrayList<>();

        double sizeX = maxX - minX;
        double sizeY = maxY - minY;
        double sizeZ = maxZ - minZ;

        // 4 arêtes horizontales en bas (Y = minY)
        packets.add(createEdge(minX + sizeX/2, minY, minZ, sizeX, LINE_THICKNESS, LINE_THICKNESS));
        packets.add(createEdge(minX + sizeX/2, minY, maxZ, sizeX, LINE_THICKNESS, LINE_THICKNESS));
        packets.add(createEdge(minX, minY, minZ + sizeZ/2, LINE_THICKNESS, LINE_THICKNESS, sizeZ));
        packets.add(createEdge(maxX, minY, minZ + sizeZ/2, LINE_THICKNESS, LINE_THICKNESS, sizeZ));

        // 4 arêtes horizontales en haut (Y = maxY)
        packets.add(createEdge(minX + sizeX/2, maxY, minZ, sizeX, LINE_THICKNESS, LINE_THICKNESS));
        packets.add(createEdge(minX + sizeX/2, maxY, maxZ, sizeX, LINE_THICKNESS, LINE_THICKNESS));
        packets.add(createEdge(minX, maxY, minZ + sizeZ/2, LINE_THICKNESS, LINE_THICKNESS, sizeZ));
        packets.add(createEdge(maxX, maxY, minZ + sizeZ/2, LINE_THICKNESS, LINE_THICKNESS, sizeZ));

        // 4 arêtes verticales (piliers)
        packets.add(createEdge(minX, minY + sizeY/2, minZ, LINE_THICKNESS, sizeY, LINE_THICKNESS));
        packets.add(createEdge(maxX, minY + sizeY/2, minZ, LINE_THICKNESS, sizeY, LINE_THICKNESS));
        packets.add(createEdge(minX, minY + sizeY/2, maxZ, LINE_THICKNESS, sizeY, LINE_THICKNESS));
        packets.add(createEdge(maxX, minY + sizeY/2, maxZ, LINE_THICKNESS, sizeY, LINE_THICKNESS));

        return packets;
    }

    /**
     * Crée un packet DisplayDebug pour une arête (cube allongé).
     */
    private DisplayDebug createEdge(double x, double y, double z, double scaleX, double scaleY, double scaleZ) {
        Matrix4d matrix = new Matrix4d()
                .identity()
                .translate(x, y, z)
                .scale(scaleX, scaleY, scaleZ);

        return new DisplayDebug(
                DebugShape.Cube,
                matrix.asFloatData(),
                MINE_COLOR,
                DISPLAY_DURATION,
                true,
                null,
                1.0f
        );
    }

    // ==================== Visualisation Zone Village (murs pleins violets) ====================

    /**
     * Envoie la visualisation de la zone village au joueur.
     * Murs solides, violet, de Y=0 à Y=256.
     */
    @SuppressWarnings("deprecation")
    private void sendVillageVisualization(Player player, Mine mine) {
        var connection = player.getPlayerConnection();
        if (connection == null) return;

        // Effacer anciennes formes
        connection.write(new ClearDebugShapes());

        int margin = mine.getVillageMargin();
        if (margin <= 0) return;

        List<DisplayDebug> packets = new ArrayList<>();

        if (mine.isCylindrical()) {
            packets.addAll(buildCylinderVillageWalls(mine, margin));
        } else {
            packets.addAll(buildCuboidVillageWalls(mine, margin));
        }

        for (DisplayDebug packet : packets) {
            connection.write(packet);
        }

        NotificationUtil.send(player, NotificationType.INFO, "Village: " + packets.size() + " elements affiches.");
    }

    /**
     * Construit 4 murs solides pour la zone village cuboid.
     * Les murs vont de Y=0 à Y=256 (murs "rideaux").
     */
    private List<DisplayDebug> buildCuboidVillageWalls(Mine mine, int margin) {
        List<DisplayDebug> packets = new ArrayList<>();

        ServerLocation c1 = mine.getCorner1();
        ServerLocation c2 = mine.getCorner2();
        if (c1 == null || c2 == null) return packets;

        double minX = Math.min(c1.x(), c2.x()) - margin;
        double maxX = Math.max(c1.x(), c2.x()) + margin + 1;
        double minZ = Math.min(c1.z(), c2.z()) - margin;
        double maxZ = Math.max(c1.z(), c2.z()) + margin + 1;

        double wallMinY = 0;
        double wallMaxY = 256;
        double wallHeight = wallMaxY - wallMinY;
        double wallCenterY = wallMinY + wallHeight / 2.0;
        double wallThickness = 0.15;

        double sizeX = maxX - minX;
        double sizeZ = maxZ - minZ;

        // Mur Nord (Z min)
        packets.add(createVillageEdge(minX + sizeX / 2, wallCenterY, minZ, sizeX, wallHeight, wallThickness));
        // Mur Sud (Z max)
        packets.add(createVillageEdge(minX + sizeX / 2, wallCenterY, maxZ, sizeX, wallHeight, wallThickness));
        // Mur Ouest (X min)
        packets.add(createVillageEdge(minX, wallCenterY, minZ + sizeZ / 2, wallThickness, wallHeight, sizeZ));
        // Mur Est (X max)
        packets.add(createVillageEdge(maxX, wallCenterY, minZ + sizeZ / 2, wallThickness, wallHeight, sizeZ));

        return packets;
    }

    /**
     * Construit des piliers verticaux sur le contour de la zone village cylindrique.
     * Chaque bloc de contour a un pilier allant de Y=0 à Y=256.
     */
    private List<DisplayDebug> buildCylinderVillageWalls(Mine mine, int margin) {
        List<DisplayDebug> packets = new ArrayList<>();

        ServerLocation center = mine.getCenter();
        if (center == null) return packets;

        int cx = (int) Math.floor(center.x());
        int cz = (int) Math.floor(center.z());

        int expandedRadius = mine.getRadius() + margin;
        int halfW = expandedRadius;
        int halfH = expandedRadius;
        double rX = halfW + mine.getRadiusAdjust();
        double rZ = halfH + mine.getRadiusAdjust();
        double rXSq = rX * rX;
        double rZSq = rZ * rZ;

        double wallMinY = 0;
        double wallMaxY = 256;
        double wallHeight = wallMaxY - wallMinY;
        double wallCenterY = wallMinY + wallHeight / 2.0;

        for (int dz = -halfH; dz <= halfH; dz++) {
            for (int dx = -halfW; dx <= halfW; dx++) {
                double distSq = (dx * dx) / rXSq + (dz * dz) / rZSq;
                if (distSq >= 1.0) continue;

                // Vérifier si c'est un bloc de contour
                boolean isEdge = false;
                for (int[] offset : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int nx = dx + offset[0];
                    int nz = dz + offset[1];
                    double nDistSq = (nx * nx) / rXSq + (nz * nz) / rZSq;
                    if (nDistSq >= 1.0) {
                        isEdge = true;
                        break;
                    }
                }

                if (isEdge) {
                    double bx = cx + dx + 0.5;
                    double bz = cz + dz + 0.5;
                    // Pilier vertical de Y=0 à Y=256 (1 bloc de large)
                    packets.add(createVillageEdge(bx, wallCenterY, bz, 0.3, wallHeight, 0.3));
                }
            }
        }

        return packets;
    }

    /**
     * Crée un packet DisplayDebug pour un mur/pilier village (violet).
     */
    private DisplayDebug createVillageEdge(double x, double y, double z, double scaleX, double scaleY, double scaleZ) {
        Matrix4d matrix = new Matrix4d()
                .identity()
                .translate(x, y, z)
                .scale(scaleX, scaleY, scaleZ);

        return new DisplayDebug(
                DebugShape.Cube,
                matrix.asFloatData(),
                VILLAGE_COLOR,
                DISPLAY_DURATION,
                true,
                null,
                1.0f
        );
    }

    public static class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
                .addField(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .addField(new KeyedCodec<>("SelectMine", Codec.STRING), (d, v) -> d.selectMine = v, d -> d.selectMine)
                .addField(new KeyedCodec<>("RemoveBlock", Codec.STRING), (d, v) -> d.removeBlock = v, d -> d.removeBlock)
                .addField(new KeyedCodec<>("ToggleBlock", Codec.STRING), (d, v) -> d.toggleBlock = v, d -> d.toggleBlock)
                .addField(new KeyedCodec<>("EditLayerLimits", Codec.STRING), (d, v) -> d.editLayerLimits = v, d -> d.editLayerLimits)
                .addField(new KeyedCodec<>("ApplyLayerLimits", Codec.STRING), (d, v) -> d.applyLayerLimits = v, d -> d.applyLayerLimits)
                .addField(new KeyedCodec<>("ClearLayerLimits", Codec.STRING), (d, v) -> d.clearLayerLimits = v, d -> d.clearLayerLimits)
                .addField(new KeyedCodec<>("UpdateBlockLayer", Codec.STRING), (d, v) -> d.updateBlockLayer = v, d -> d.updateBlockLayer)
                .addField(new KeyedCodec<>("@MinLayer", Codec.STRING), (d, v) -> d.minLayer = v, d -> d.minLayer)
                .addField(new KeyedCodec<>("@MaxLayer", Codec.STRING), (d, v) -> d.maxLayer = v, d -> d.maxLayer)
                .addField(new KeyedCodec<>("Toggle", Codec.STRING), (d, v) -> d.toggle = v, d -> d.toggle)
                .addField(new KeyedCodec<>("@DisplayName", Codec.STRING), (d, v) -> d.displayName = v, d -> d.displayName)
                .addField(new KeyedCodec<>("@RequiredRank", Codec.STRING), (d, v) -> d.requiredRank = v, d -> d.requiredRank)
                .addField(new KeyedCodec<>("@BlockType", Codec.STRING), (d, v) -> d.blockType = v, d -> d.blockType)
                .addField(new KeyedCodec<>("@BlockPercent", Codec.STRING), (d, v) -> d.blockPercent = v, d -> d.blockPercent)
                .addField(new KeyedCodec<>("@NewMineId", Codec.STRING), (d, v) -> d.newMineId = v, d -> d.newMineId)
                .addField(new KeyedCodec<>("@NewMineName", Codec.STRING), (d, v) -> d.newMineName = v, d -> d.newMineName)
                .addField(new KeyedCodec<>("@NewMineRank", Codec.STRING), (d, v) -> d.newMineRank = v, d -> d.newMineRank)
                // Cylindre
                .addField(new KeyedCodec<>("@Radius", Codec.STRING), (d, v) -> d.radius = v, d -> d.radius)
                .addField(new KeyedCodec<>("@Height", Codec.STRING), (d, v) -> d.height = v, d -> d.height)
                .addField(new KeyedCodec<>("@RadiusAdjust", Codec.STRING), (d, v) -> d.radiusAdjust = v, d -> d.radiusAdjust)
                // Village
                .addField(new KeyedCodec<>("@VillageMargin", Codec.STRING), (d, v) -> d.villageMargin = v, d -> d.villageMargin)
                // Reset interval
                .addField(new KeyedCodec<>("@ResetInterval", Codec.STRING), (d, v) -> d.resetInterval = v, d -> d.resetInterval)
                .build();

        public String action;
        public String selectMine;
        public String removeBlock;
        public String toggleBlock;
        public String editLayerLimits;
        public String applyLayerLimits;
        public String clearLayerLimits;
        public String updateBlockLayer;
        public String minLayer;
        public String maxLayer;
        public String toggle;
        public String displayName;
        public String requiredRank;
        public String blockType;
        public String blockPercent;
        public String newMineId;
        public String newMineName;
        public String newMineRank;
        // Cylindre
        public String radius;
        public String height;
        public String radiusAdjust;
        // Village
        public String villageMargin;
        // Reset interval
        public String resetInterval;
    }
}

