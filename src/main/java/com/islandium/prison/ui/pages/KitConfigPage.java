package com.islandium.prison.ui.pages;

import com.islandium.prison.PrisonPlugin;
import com.islandium.prison.config.PrisonConfig;
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
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Page admin pour configurer les kits.
 * Accessible via /pa kitconfig
 */
public class KitConfigPage extends InteractiveCustomUIPage<KitConfigPage.PageData> {

    private final PrisonPlugin plugin;
    private final PlayerRef playerRef;

    // State
    private boolean createMode = false;
    private boolean firstJoinToggle = false;
    private String editingKitId = null; // Kit currently being viewed/edited (showing items)
    private boolean addItemMode = false;

    public KitConfigPage(@Nonnull PlayerRef playerRef, PrisonPlugin plugin) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageData.CODEC);
        this.plugin = plugin;
        this.playerRef = playerRef;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder event, @Nonnull Store<EntityStore> store) {
        cmd.append("Pages/Prison/KitConfigPage.ui");

        Player player = store.getComponent(ref, Player.getComponentType());

        // New kit button
        event.addEventBinding(CustomUIEventBindingType.Activating, "#NewKitBtn",
            EventData.of("Action", "showCreate"), false);

        // Create form events
        event.addEventBinding(CustomUIEventBindingType.Activating, "#ConfirmCreateBtn",
            EventData.of("Action", "confirmCreate")
                .append("@NewKitId", "#NewKitIdField.Value")
                .append("@NewKitName", "#NewKitNameField.Value")
                .append("@NewKitDesc", "#NewKitDescField.Value")
                .append("@NewKitColor", "#NewKitColorField.Value")
                .append("@NewKitCooldown", "#NewKitCooldownField.Value")
                .append("@NewKitPerm", "#NewKitPermField.Value"), false);

        event.addEventBinding(CustomUIEventBindingType.Activating, "#CancelCreateBtn",
            EventData.of("Action", "cancelCreate"), false);

        event.addEventBinding(CustomUIEventBindingType.Activating, "#ToggleFirstJoinBtn",
            EventData.of("Action", "toggleFirstJoin"), false);

        // Add item form events
        event.addEventBinding(CustomUIEventBindingType.Activating, "#ConfirmAddItemBtn",
            EventData.of("Action", "confirmAddItem")
                .append("@NewItemId", "#NewItemIdField.Value")
                .append("@NewItemQty", "#NewItemQtyField.Value"), false);

        event.addEventBinding(CustomUIEventBindingType.Activating, "#CancelAddItemBtn",
            EventData.of("Action", "cancelAddItem"), false);

        // Status
        updateStatus(cmd);

        // Build kit list
        buildKitList(cmd, event);
    }

    private void buildKitList(UICommandBuilder cmd, UIEventBuilder event) {
        cmd.clear("#KitList");

        List<PrisonConfig.KitDefinition> kits = plugin.getConfig().getKits();

        if (kits.isEmpty()) {
            cmd.appendInline("#KitList",
                "Label { Anchor: (Height: 30); Text: \"Aucun kit configure. Cliquez + NOUVEAU KIT.\"; " +
                "Style: (FontSize: 12, TextColor: #808080); }");
            return;
        }

        int index = 0;
        for (PrisonConfig.KitDefinition kit : kits) {
            boolean isEditing = kit.id.equals(editingKitId);
            String bgColor = isEditing ? "#1a2a3a" : (index % 2 == 0 ? "#111b27" : "#151d28");
            String nameColor = isEditing ? "#4fc3f7" : "#ffffff";
            String rowId = "KitRow" + index;
            String color = kit.color != null ? kit.color : "#4fc3f7";

            // Kit row: Name | Description | Cooldown | FirstJoin | Actions
            // NOTE: Use Button (not TextButton with TextButtonStyle) inline to avoid parse errors
            cmd.appendInline("#KitList",
                "Group #" + rowId + " { Anchor: (Height: 34); LayoutMode: Left; Padding: (Horizontal: 5); Background: (Color: " + bgColor + "); " +
                "  Label #KName { Anchor: (Width: 130); Style: (FontSize: 12, TextColor: " + nameColor + ", VerticalAlignment: Center" + (isEditing ? ", RenderBold: true" : "") + "); } " +
                "  Label #KDesc { FlexWeight: 1; Style: (FontSize: 10, TextColor: #7c8b99, VerticalAlignment: Center); } " +
                "  Label #KCd { Anchor: (Width: 65); Style: (FontSize: 10, TextColor: #96a9be, VerticalAlignment: Center); } " +
                "  Label #KFj { Anchor: (Width: 30); Style: (FontSize: 10, TextColor: " + (kit.giveOnFirstJoin ? "#66bb6a" : "#5a5a5a") + ", RenderBold: true, VerticalAlignment: Center); } " +
                "  Button #ItemsBtn { Anchor: (Width: 55, Left: 3, Height: 26); Background: (Color: #2d4a5a); " +
                "    Label #ItemsBtnLbl { Text: \"ITEMS\"; Style: (FontSize: 10, TextColor: #ffffff, HorizontalAlignment: Center, VerticalAlignment: Center); } } " +
                "  Button #DeleteBtn { Anchor: (Width: 55, Left: 3, Height: 26); Background: (Color: #5a2d2d); " +
                "    Label #DeleteBtnLbl { Text: \"SUPPR\"; Style: (FontSize: 10, TextColor: #ffffff, HorizontalAlignment: Center, VerticalAlignment: Center); } } " +
                "}");

            String displayName = kit.displayName != null ? kit.displayName : kit.id;
            cmd.set("#" + rowId + " #KName.Text", displayName);
            cmd.set("#" + rowId + " #KDesc.Text", kit.description != null ? kit.description : "-");

            String cdText;
            if (kit.cooldownSeconds < 0) cdText = "Aucun";
            else if (kit.cooldownSeconds == 0) cdText = "Unique";
            else cdText = com.islandium.prison.kit.KitManager.formatCooldown(kit.cooldownSeconds);
            cmd.set("#" + rowId + " #KCd.Text", cdText);

            cmd.set("#" + rowId + " #KFj.Text", kit.giveOnFirstJoin ? "FJ" : "-");

            event.addEventBinding(CustomUIEventBindingType.Activating, "#" + rowId + " #ItemsBtn",
                EventData.of("Action", "editKit").append("KitId", kit.id), false);
            event.addEventBinding(CustomUIEventBindingType.Activating, "#" + rowId + " #DeleteBtn",
                EventData.of("Action", "deleteKit").append("KitId", kit.id), false);

            // If this kit is being edited, show its items below
            if (isEditing && kit.items != null) {
                for (int itemIdx = 0; itemIdx < kit.items.size(); itemIdx++) {
                    PrisonConfig.KitItem item = kit.items.get(itemIdx);
                    String itemRowId = "KitItemRow" + index + "_" + itemIdx;

                    cmd.appendInline("#KitList",
                        "Group #" + itemRowId + " { Anchor: (Height: 30); LayoutMode: Left; Padding: (Left: 40, Right: 5); Background: (Color: #0d1925); " +
                        "  Group #" + itemRowId + "Icon { Anchor: (Width: 26, Height: 26); Background: (Color: #1a2535); } " +
                        "  Label #ItemName { FlexWeight: 1; Anchor: (Left: 6); Style: (FontSize: 11, TextColor: #96a9be, VerticalAlignment: Center); } " +
                        "  Label #ItemQty { Anchor: (Width: 60); Style: (FontSize: 11, TextColor: #66bb6a, RenderBold: true, VerticalAlignment: Center); } " +
                        "  Button #RemoveItemBtn { Anchor: (Width: 40, Left: 3, Height: 22); Background: (Color: #5a2d2d); " +
                        "    Label #RemoveBtnLbl { Text: \"X\"; Style: (FontSize: 9, TextColor: #ffffff, HorizontalAlignment: Center, VerticalAlignment: Center); } } " +
                        "}");

                    cmd.set("#" + itemRowId + " #ItemName.Text", formatBlockName(item.itemId));
                    cmd.set("#" + itemRowId + " #ItemQty.Text", "x" + item.quantity);

                    // TODO: Item icon via setObject - disabled for now, needs investigation
                    // try {
                    //     String iconId = item.itemId;
                    //     if (iconId != null && !iconId.isEmpty()) {
                    //         if (!iconId.contains(":")) iconId = "minecraft:" + iconId;
                    //         ItemStack iconStack = new ItemStack(iconId, 1);
                    //         if (iconStack != null && !ItemStack.isEmpty(iconStack)) {
                    //             cmd.setObject("#" + itemRowId + "Icon", iconStack);
                    //         }
                    //     }
                    // } catch (Exception ignored) {}

                    final int finalItemIdx = itemIdx;
                    event.addEventBinding(CustomUIEventBindingType.Activating, "#" + itemRowId + " #RemoveItemBtn",
                        EventData.of("Action", "removeItem").append("KitId", kit.id).append("ItemIndex", String.valueOf(finalItemIdx)), false);
                }

                // Add item button row
                String addRowId = "AddItemRow" + index;
                cmd.appendInline("#KitList",
                    "Group #" + addRowId + " { Anchor: (Height: 28); LayoutMode: Left; Padding: (Left: 40, Right: 5); Background: (Color: #0d2520); " +
                    "  Button #AddItemBtn { Anchor: (Width: 120, Height: 24); Background: (Color: #2d5a2d); " +
                    "    Label #AddItemBtnLbl { Text: \"+ AJOUTER ITEM\"; Style: (FontSize: 10, TextColor: #ffffff, HorizontalAlignment: Center, VerticalAlignment: Center); } } " +
                    "}");

                event.addEventBinding(CustomUIEventBindingType.Activating, "#" + addRowId + " #AddItemBtn",
                    EventData.of("Action", "showAddItem").append("KitId", kit.id), false);
            }

            index++;
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        super.handleDataEvent(ref, store, data);

        Player player = store.getComponent(ref, Player.getComponentType());
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder event = new UIEventBuilder();

        if (data.action == null) return;

        switch (data.action) {
            case "showCreate" -> {
                createMode = true;
                firstJoinToggle = false;
                cmd.set("#CreateForm.Visible", true);
                cmd.set("#ToggleFirstJoinBtn.Text", "First Join: NON");
                sendUpdate(cmd, event, false);
                return;
            }
            case "cancelCreate" -> {
                createMode = false;
                cmd.set("#CreateForm.Visible", false);
                sendUpdate(cmd, event, false);
                return;
            }
            case "toggleFirstJoin" -> {
                firstJoinToggle = !firstJoinToggle;
                cmd.set("#ToggleFirstJoinBtn.Text", "First Join: " + (firstJoinToggle ? "OUI" : "NON"));
                sendUpdate(cmd, event, false);
                return;
            }
            case "confirmCreate" -> {
                if (data.newKitId == null || data.newKitId.trim().isEmpty()) {
                    player.sendMessage(Message.raw("L'ID du kit ne peut pas etre vide!"));
                    return;
                }
                String kitId = data.newKitId.trim().toLowerCase().replace(" ", "_");

                // Check duplicate
                if (plugin.getConfig().getKit(kitId) != null) {
                    player.sendMessage(Message.raw("Un kit avec l'ID '" + kitId + "' existe deja!"));
                    return;
                }

                PrisonConfig.KitDefinition newKit = new PrisonConfig.KitDefinition();
                newKit.id = kitId;
                newKit.displayName = (data.newKitName != null && !data.newKitName.trim().isEmpty()) ? data.newKitName.trim() : kitId;
                newKit.description = (data.newKitDesc != null && !data.newKitDesc.trim().isEmpty()) ? data.newKitDesc.trim() : "";
                newKit.icon = "minecraft:chest";
                newKit.color = (data.newKitColor != null && !data.newKitColor.trim().isEmpty()) ? data.newKitColor.trim() : "#4fc3f7";
                newKit.items = new ArrayList<>();
                newKit.giveOnFirstJoin = firstJoinToggle;

                // Cooldown
                int cooldown = -1;
                if (data.newKitCooldown != null && !data.newKitCooldown.trim().isEmpty()) {
                    try {
                        cooldown = Integer.parseInt(data.newKitCooldown.trim());
                    } catch (NumberFormatException e) {
                        player.sendMessage(Message.raw("Cooldown invalide! Utilise un nombre entier."));
                        return;
                    }
                }
                newKit.cooldownSeconds = cooldown;

                // Permission
                newKit.permission = (data.newKitPerm != null && !data.newKitPerm.trim().isEmpty()) ? data.newKitPerm.trim() : null;

                plugin.getConfig().addKit(newKit);
                saveConfig(player);

                createMode = false;
                editingKitId = kitId; // Open items view directly
                cmd.set("#CreateForm.Visible", false);
                player.sendMessage(Message.raw("Kit '" + newKit.displayName + "' cree! Ajoutez des items."));

                buildKitList(cmd, event);
                updateStatus(cmd);
                sendUpdate(cmd, event, false);
                return;
            }
            case "deleteKit" -> {
                if (data.kitId != null) {
                    PrisonConfig.KitDefinition kit = plugin.getConfig().getKit(data.kitId);
                    String name = kit != null ? kit.displayName : data.kitId;
                    plugin.getConfig().removeKit(data.kitId);
                    saveConfig(player);

                    if (data.kitId.equals(editingKitId)) {
                        editingKitId = null;
                        cmd.set("#AddItemForm.Visible", false);
                    }

                    player.sendMessage(Message.raw("Kit '" + name + "' supprime!"));
                    buildKitList(cmd, event);
                    updateStatus(cmd);
                    sendUpdate(cmd, event, false);
                }
                return;
            }
            case "editKit" -> {
                if (data.kitId != null) {
                    // Toggle: si deja en edition, fermer
                    if (data.kitId.equals(editingKitId)) {
                        editingKitId = null;
                        addItemMode = false;
                        cmd.set("#AddItemForm.Visible", false);
                    } else {
                        editingKitId = data.kitId;
                        addItemMode = false;
                        cmd.set("#AddItemForm.Visible", false);
                    }
                    buildKitList(cmd, event);
                    sendUpdate(cmd, event, false);
                }
                return;
            }
            case "showAddItem" -> {
                if (data.kitId != null) {
                    addItemMode = true;
                    editingKitId = data.kitId;
                    PrisonConfig.KitDefinition kit = plugin.getConfig().getKit(data.kitId);
                    cmd.set("#AddItemForm.Visible", true);
                    cmd.set("#AddItemLabel.Text", "Kit: " + (kit != null ? kit.displayName : data.kitId));

                    // Auto-fill from main hand
                    var inv = player.getInventory();
                    if (inv != null) {
                        var mainHand = inv.getItemInHand();
                        if (mainHand != null && !mainHand.isEmpty()) {
                            String itemId = mainHand.getItemId();
                            if (itemId.startsWith("minecraft:")) {
                                itemId = itemId.substring("minecraft:".length());
                            }
                            cmd.set("#NewItemIdField.Value", itemId);
                            cmd.set("#NewItemQtyField.Value", String.valueOf(mainHand.getQuantity()));
                        }
                    }

                    sendUpdate(cmd, event, false);
                }
                return;
            }
            case "cancelAddItem" -> {
                addItemMode = false;
                cmd.set("#AddItemForm.Visible", false);
                sendUpdate(cmd, event, false);
                return;
            }
            case "confirmAddItem" -> {
                if (editingKitId == null || data.newItemId == null || data.newItemId.trim().isEmpty()) {
                    player.sendMessage(Message.raw("L'ID de l'item ne peut pas etre vide!"));
                    return;
                }

                String itemId = data.newItemId.trim();
                if (!itemId.contains(":")) {
                    itemId = "minecraft:" + itemId;
                }

                int qty = 1;
                if (data.newItemQty != null && !data.newItemQty.trim().isEmpty()) {
                    try {
                        qty = Integer.parseInt(data.newItemQty.trim());
                        if (qty <= 0) {
                            player.sendMessage(Message.raw("La quantite doit etre positive!"));
                            return;
                        }
                    } catch (NumberFormatException e) {
                        player.sendMessage(Message.raw("Quantite invalide!"));
                        return;
                    }
                }

                PrisonConfig.KitDefinition kit = plugin.getConfig().getKit(editingKitId);
                if (kit != null) {
                    if (kit.items == null) kit.items = new ArrayList<>();
                    kit.items.add(new PrisonConfig.KitItem(itemId, qty));
                    saveConfig(player);
                    player.sendMessage(Message.raw("Item " + formatBlockName(itemId) + " x" + qty + " ajoute au kit!"));
                }

                addItemMode = false;
                cmd.set("#AddItemForm.Visible", false);
                buildKitList(cmd, event);
                sendUpdate(cmd, event, false);
                return;
            }
            case "removeItem" -> {
                if (data.kitId != null && data.itemIndex != null) {
                    try {
                        int idx = Integer.parseInt(data.itemIndex);
                        PrisonConfig.KitDefinition kit = plugin.getConfig().getKit(data.kitId);
                        if (kit != null && kit.items != null && idx >= 0 && idx < kit.items.size()) {
                            PrisonConfig.KitItem removed = kit.items.remove(idx);
                            saveConfig(player);
                            player.sendMessage(Message.raw("Item " + formatBlockName(removed.itemId) + " retire du kit!"));
                        }
                    } catch (NumberFormatException ignored) {}

                    buildKitList(cmd, event);
                    sendUpdate(cmd, event, false);
                }
                return;
            }
        }
    }

    private void saveConfig(Player player) {
        try {
            plugin.getConfig().save();
        } catch (IOException e) {
            player.sendMessage(Message.raw("Erreur lors de la sauvegarde: " + e.getMessage()));
            plugin.log(Level.WARNING, "Failed to save config: " + e.getMessage());
        }
    }

    private void updateStatus(UICommandBuilder cmd) {
        int kitCount = plugin.getConfig().getKits().size();
        cmd.set("#StatusLabel.Text", kitCount + " kit(s) configure(s)");
    }

    private String formatBlockName(String blockId) {
        if (blockId == null) return "???";
        String name = blockId;
        int colonIdx = name.indexOf(':');
        if (colonIdx >= 0) name = name.substring(colonIdx + 1);
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
    // DATA CODEC
    // =========================================

    public static class PageData {
        public static final BuilderCodec<PageData> CODEC = BuilderCodec.builder(PageData.class, PageData::new)
                .addField(new KeyedCodec<>("Action", Codec.STRING), (d, v) -> d.action = v, d -> d.action)
                .addField(new KeyedCodec<>("KitId", Codec.STRING), (d, v) -> d.kitId = v, d -> d.kitId)
                .addField(new KeyedCodec<>("ItemIndex", Codec.STRING), (d, v) -> d.itemIndex = v, d -> d.itemIndex)
                .addField(new KeyedCodec<>("@NewKitId", Codec.STRING), (d, v) -> d.newKitId = v, d -> d.newKitId)
                .addField(new KeyedCodec<>("@NewKitName", Codec.STRING), (d, v) -> d.newKitName = v, d -> d.newKitName)
                .addField(new KeyedCodec<>("@NewKitDesc", Codec.STRING), (d, v) -> d.newKitDesc = v, d -> d.newKitDesc)
                .addField(new KeyedCodec<>("@NewKitColor", Codec.STRING), (d, v) -> d.newKitColor = v, d -> d.newKitColor)
                .addField(new KeyedCodec<>("@NewKitCooldown", Codec.STRING), (d, v) -> d.newKitCooldown = v, d -> d.newKitCooldown)
                .addField(new KeyedCodec<>("@NewKitPerm", Codec.STRING), (d, v) -> d.newKitPerm = v, d -> d.newKitPerm)
                .addField(new KeyedCodec<>("@NewItemId", Codec.STRING), (d, v) -> d.newItemId = v, d -> d.newItemId)
                .addField(new KeyedCodec<>("@NewItemQty", Codec.STRING), (d, v) -> d.newItemQty = v, d -> d.newItemQty)
                .build();

        public String action;
        public String kitId;
        public String itemIndex;
        public String newKitId;
        public String newKitName;
        public String newKitDesc;
        public String newKitColor;
        public String newKitCooldown;
        public String newKitPerm;
        public String newItemId;
        public String newItemQty;
    }
}
