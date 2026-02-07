package com.islandium.prison.ui.pages;

import com.islandium.prison.PrisonPlugin;
import com.islandium.prison.config.PrisonConfig;
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
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Page admin pour configurer le Sell Shop.
 * Accessible via /pa sellconfig
 */
public class SellConfigPage extends InteractiveCustomUIPage<SellConfigPage.PageData> {

    private final PrisonPlugin plugin;
    private final PlayerRef playerRef;
    private String editingBlockId = null;
    private boolean addMode = false;

    public SellConfigPage(@Nonnull PlayerRef playerRef, PrisonPlugin plugin) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageData.CODEC);
        this.plugin = plugin;
        this.playerRef = playerRef;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder event, @Nonnull Store<EntityStore> store) {
        cmd.append("Pages/Prison/SellConfigPage.ui");

        // Multiplier field events
        event.addEventBinding(CustomUIEventBindingType.Activating, "#SaveMultiplierBtn",
            EventData.of("Action", "saveMultiplier").append("@Multiplier", "#MultiplierField.Value"), false);

        // Add block button
        event.addEventBinding(CustomUIEventBindingType.Activating, "#AddBlockBtn",
            EventData.of("Action", "showAdd"), false);

        // Confirm add
        event.addEventBinding(CustomUIEventBindingType.Activating, "#ConfirmAddBtn",
            EventData.of("Action", "confirmAdd")
                .append("@NewBlockId", "#NewBlockIdField.Value")
                .append("@NewBlockPrice", "#NewBlockPriceField.Value"), false);

        // Cancel add
        event.addEventBinding(CustomUIEventBindingType.Activating, "#CancelAddBtn",
            EventData.of("Action", "cancelAdd"), false);

        // Edit form events (fixed form in .ui)
        event.addEventBinding(CustomUIEventBindingType.Activating, "#SaveEditBtn",
            EventData.of("Action", "saveEdit").append("@EditPrice", "#EditPriceField.Value"), false);
        event.addEventBinding(CustomUIEventBindingType.Activating, "#CancelEditBtn",
            EventData.of("Action", "cancelEdit"), false);

        // Set initial multiplier value
        double currentMult = plugin.getConfig().getBlockSellMultiplier();
        cmd.set("#MultiplierField.Value", String.valueOf(currentMult));
        cmd.set("#MultiplierInfo.Text", "Actuel: x" + String.format("%.2f", currentMult));

        // Status
        int blockCount = plugin.getConfig().getBlockValues().size();
        cmd.set("#StatusLabel.Text", blockCount + " bloc(s) configure(s)");

        // Build block list
        buildBlockList(cmd, event);
    }

    private void buildBlockList(UICommandBuilder cmd, UIEventBuilder event) {
        cmd.clear("#BlockList");

        Map<String, BigDecimal> blockValues = plugin.getConfig().getBlockValues();

        if (blockValues.isEmpty()) {
            cmd.appendInline("#BlockList",
                "Label { Anchor: (Height: 30); Text: \"Aucun bloc configure. Cliquez + AJOUTER pour commencer.\"; " +
                "Style: (FontSize: 12, TextColor: #808080); }");
            return;
        }

        // Sort by price ascending
        List<Map.Entry<String, BigDecimal>> sorted = blockValues.entrySet().stream()
            .sorted(Map.Entry.comparingByValue())
            .collect(Collectors.toList());

        int index = 0;
        for (Map.Entry<String, BigDecimal> entry : sorted) {
            String blockId = entry.getKey();
            BigDecimal price = entry.getValue();
            boolean isEditing = blockId.equals(editingBlockId);
            String bgColor = isEditing ? "#1a2a3a" : (index % 2 == 0 ? "#111b27" : "#151d28");
            String nameColor = isEditing ? "#4fc3f7" : "#ffffff";
            String rowId = "BlockRow" + index;

            cmd.appendInline("#BlockList",
                "Group #" + rowId + " { Anchor: (Height: 32); LayoutMode: Left; Padding: (Horizontal: 5); Background: (Color: " + bgColor + "); " +
                "  Label #BName { FlexWeight: 1; Style: (FontSize: 12, TextColor: " + nameColor + ", VerticalAlignment: Center" + (isEditing ? ", RenderBold: true" : "") + "); } " +
                "  Label #BPrice { Anchor: (Width: 100); Style: (FontSize: 12, TextColor: #66bb6a, RenderBold: true, VerticalAlignment: Center); } " +
                "  TextButton #EditBtn { Anchor: (Width: 55, Left: 5, Height: 26); " +
                "    Style: TextButtonStyle(Default: (Background: #2d4a5a, LabelStyle: (FontSize: 10, TextColor: #ffffff, HorizontalAlignment: Center, VerticalAlignment: Center)), " +
                "    Hovered: (Background: #3d5a6a, LabelStyle: (FontSize: 10, TextColor: #ffffff, HorizontalAlignment: Center, VerticalAlignment: Center))); } " +
                "  TextButton #DeleteBtn { Anchor: (Width: 55, Left: 3, Height: 26); " +
                "    Style: TextButtonStyle(Default: (Background: #5a2d2d, LabelStyle: (FontSize: 10, TextColor: #ffffff, HorizontalAlignment: Center, VerticalAlignment: Center)), " +
                "    Hovered: (Background: #7a3d3d, LabelStyle: (FontSize: 10, TextColor: #ffffff, HorizontalAlignment: Center, VerticalAlignment: Center))); } " +
                "}");

            cmd.set("#" + rowId + " #BName.Text", formatBlockName(blockId) + (isEditing ? " [en edition]" : ""));
            cmd.set("#" + rowId + " #BPrice.Text", price.setScale(2, RoundingMode.HALF_UP) + "$");
            cmd.set("#" + rowId + " #EditBtn.Text", "EDIT");
            cmd.set("#" + rowId + " #DeleteBtn.Text", "SUPPR");

            event.addEventBinding(CustomUIEventBindingType.Activating, "#" + rowId + " #EditBtn",
                EventData.of("Action", "editBlock").append("BlockId", blockId), false);
            event.addEventBinding(CustomUIEventBindingType.Activating, "#" + rowId + " #DeleteBtn",
                EventData.of("Action", "deleteBlock").append("BlockId", blockId), false);

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
            case "saveMultiplier" -> {
                if (data.multiplier != null) {
                    try {
                        double mult = Double.parseDouble(data.multiplier);
                        if (mult <= 0) {
                            player.sendMessage(Message.raw("Le multiplicateur doit etre positif!"));
                            return;
                        }
                        plugin.getConfig().setBlockSellMultiplier(mult);
                        saveConfig(player);
                        cmd.set("#MultiplierInfo.Text", "Sauvegarde! x" + String.format("%.2f", mult));
                        sendUpdate(cmd, event, false);
                    } catch (NumberFormatException e) {
                        player.sendMessage(Message.raw("Valeur invalide pour le multiplicateur!"));
                    }
                }
                return;
            }
            case "showAdd" -> {
                addMode = true;
                cmd.set("#AddForm.Visible", true);
                sendUpdate(cmd, event, false);
                return;
            }
            case "cancelAdd" -> {
                addMode = false;
                cmd.set("#AddForm.Visible", false);
                sendUpdate(cmd, event, false);
                return;
            }
            case "confirmAdd" -> {
                if (data.newBlockId != null && data.newBlockPrice != null) {
                    String blockId = data.newBlockId.trim();
                    if (blockId.isEmpty()) {
                        player.sendMessage(Message.raw("L'ID du bloc ne peut pas etre vide!"));
                        return;
                    }
                    // Add minecraft: prefix if missing
                    if (!blockId.contains(":")) {
                        blockId = "minecraft:" + blockId;
                    }
                    try {
                        double price = Double.parseDouble(data.newBlockPrice);
                        if (price <= 0) {
                            player.sendMessage(Message.raw("Le prix doit etre positif!"));
                            return;
                        }
                        plugin.getConfig().setBlockValue(blockId, BigDecimal.valueOf(price));
                        saveConfig(player);
                        addMode = false;
                        cmd.set("#AddForm.Visible", false);
                        player.sendMessage(Message.raw("Bloc " + blockId + " ajoute au prix de " + price + "$!"));
                        buildBlockList(cmd, event);
                        updateStatus(cmd);
                        sendUpdate(cmd, event, false);
                    } catch (NumberFormatException e) {
                        player.sendMessage(Message.raw("Valeur de prix invalide!"));
                    }
                }
                return;
            }
            case "editBlock" -> {
                if (data.blockId != null) {
                    editingBlockId = data.blockId;
                    BigDecimal currentPrice = plugin.getConfig().getBlockValue(data.blockId);
                    // Show the fixed edit form and fill it
                    cmd.set("#EditForm.Visible", true);
                    cmd.set("#EditBlockName.Text", formatBlockName(data.blockId));
                    cmd.set("#EditPriceField.Value", currentPrice.toPlainString());
                    // Highlight the row in the list
                    buildBlockList(cmd, event);
                    sendUpdate(cmd, event, false);
                }
                return;
            }
            case "cancelEdit" -> {
                editingBlockId = null;
                cmd.set("#EditForm.Visible", false);
                buildBlockList(cmd, event);
                sendUpdate(cmd, event, false);
                return;
            }
            case "saveEdit" -> {
                if (editingBlockId != null && data.editPrice != null) {
                    try {
                        double price = Double.parseDouble(data.editPrice);
                        if (price <= 0) {
                            player.sendMessage(Message.raw("Le prix doit etre positif!"));
                            return;
                        }
                        plugin.getConfig().setBlockValue(editingBlockId, BigDecimal.valueOf(price));
                        saveConfig(player);
                        player.sendMessage(Message.raw("Prix de " + formatBlockName(editingBlockId) + " mis a jour: " + price + "$"));
                        editingBlockId = null;
                        cmd.set("#EditForm.Visible", false);
                        buildBlockList(cmd, event);
                        sendUpdate(cmd, event, false);
                    } catch (Exception e) {
                        player.sendMessage(Message.raw("Erreur lors de la sauvegarde!"));
                    }
                }
                return;
            }
            case "deleteBlock" -> {
                if (data.blockId != null) {
                    plugin.getConfig().removeBlockValue(data.blockId);
                    saveConfig(player);
                    player.sendMessage(Message.raw("Bloc " + formatBlockName(data.blockId) + " supprime!"));
                    buildBlockList(cmd, event);
                    updateStatus(cmd);
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
            player.sendMessage(Message.raw("Erreur lors de la sauvegarde de la config: " + e.getMessage()));
            plugin.log(Level.WARNING, "Failed to save config: " + e.getMessage());
        }
    }

    private void updateStatus(UICommandBuilder cmd) {
        int blockCount = plugin.getConfig().getBlockValues().size();
        cmd.set("#StatusLabel.Text", blockCount + " bloc(s) configure(s)");
    }

    private String formatBlockName(String blockId) {
        String name = blockId;
        int colonIdx = name.indexOf(':');
        if (colonIdx >= 0) {
            name = name.substring(colonIdx + 1);
        }
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
                .addField(new KeyedCodec<>("BlockId", Codec.STRING), (d, v) -> d.blockId = v, d -> d.blockId)
                .addField(new KeyedCodec<>("@EditPrice", Codec.STRING), (d, v) -> d.editPrice = v, d -> d.editPrice)
                .addField(new KeyedCodec<>("@Multiplier", Codec.STRING), (d, v) -> d.multiplier = v, d -> d.multiplier)
                .addField(new KeyedCodec<>("@NewBlockId", Codec.STRING), (d, v) -> d.newBlockId = v, d -> d.newBlockId)
                .addField(new KeyedCodec<>("@NewBlockPrice", Codec.STRING), (d, v) -> d.newBlockPrice = v, d -> d.newBlockPrice)
                .build();

        public String action;
        public String blockId;
        public String editPrice;
        public String multiplier;
        public String newBlockId;
        public String newBlockPrice;
    }
}
