package com.islandium.prison.ui.pages;

import com.islandium.prison.PrisonPlugin;
import com.islandium.prison.config.PrisonConfig;
import com.islandium.prison.kit.KitManager;
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
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.hypixel.hytale.server.core.ui.builder.EventData;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.ui.builder.UIEventBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Page UI joueur pour afficher et reclamer les kits.
 * Accessible via /kit
 */
public class KitPage extends InteractiveCustomUIPage<KitPage.PageData> {

    private final PrisonPlugin plugin;
    private final PlayerRef playerRef;
    private String currentPage = "hub"; // "hub" ou kitId

    public KitPage(@Nonnull PlayerRef playerRef, PrisonPlugin plugin) {
        super(playerRef, CustomPageLifetime.CanDismiss, PageData.CODEC);
        this.plugin = plugin;
        this.playerRef = playerRef;
    }

    @Override
    public void build(@Nonnull Ref<EntityStore> ref, @Nonnull UICommandBuilder cmd, @Nonnull UIEventBuilder event, @Nonnull Store<EntityStore> store) {
        cmd.append("Pages/Prison/KitPage.ui");

        Player player = store.getComponent(ref, Player.getComponentType());
        UUID uuid = playerRef.getUuid();

        // Back button
        event.addEventBinding(CustomUIEventBindingType.Activating, "#BackBtn",
            EventData.of("Action", "back"), false);

        // Build kit grid
        buildKitGrid(cmd, event, uuid);
    }

    private void showHub(UICommandBuilder cmd) {
        cmd.set("#HubGrid.Visible", true);
        cmd.set("#PageContent.Visible", false);
        cmd.set("#BackBtn.Visible", false);
        cmd.set("#HeaderTitle.Text", "KITS");
    }

    private void showSubPage(UICommandBuilder cmd) {
        cmd.set("#HubGrid.Visible", false);
        cmd.set("#PageContent.Visible", true);
        cmd.set("#BackBtn.Visible", true);
    }

    /**
     * Genere la grille de cartes de kits (3 par ligne).
     */
    private void buildKitGrid(UICommandBuilder cmd, UIEventBuilder event, UUID uuid) {
        cmd.clear("#HubGrid");
        showHub(cmd);

        List<PrisonConfig.KitDefinition> kits = plugin.getConfig().getKits();

        // Filter kits by permission
        List<PrisonConfig.KitDefinition> availableKits = new ArrayList<>();
        for (PrisonConfig.KitDefinition kit : kits) {
            if (hasKitPermission(uuid, kit)) {
                availableKits.add(kit);
            }
        }

        if (availableKits.isEmpty()) {
            cmd.appendInline("#HubGrid",
                "Label { Anchor: (Height: 40); Text: \"Aucun kit disponible.\"; " +
                "Style: (FontSize: 14, TextColor: #808080, HorizontalAlignment: Center); }");
            return;
        }

        // Build rows of 3 cards
        int cardsPerRow = 3;
        for (int i = 0; i < availableKits.size(); i += cardsPerRow) {
            String rowId = "KitRow" + (i / cardsPerRow);

            cmd.appendInline("#HubGrid",
                "Group #" + rowId + " { Anchor: (Height: 200" + (i > 0 ? ", Top: 10" : "") + "); LayoutMode: Left; }");

            for (int j = 0; j < cardsPerRow; j++) {
                int idx = i + j;
                if (idx < availableKits.size()) {
                    PrisonConfig.KitDefinition kit = availableKits.get(idx);
                    String cardId = "KitCard" + idx;
                    String color = kit.color != null ? kit.color : "#4fc3f7";

                    // Cooldown status
                    long remaining = plugin.getKitManager().getRemainingCooldown(uuid, kit.id);
                    String statusText;
                    String statusColor;
                    if (remaining == -2) {
                        statusText = "Deja reclame";
                        statusColor = "#ef5350";
                    } else if (remaining > 0) {
                        statusText = KitManager.formatCooldown(remaining);
                        statusColor = "#ffa726";
                    } else {
                        statusText = "Disponible";
                        statusColor = "#66bb6a";
                    }

                    // Padding based on position in row
                    String padding;
                    if (j == 0) padding = "Padding: (Right: 10);";
                    else if (j == cardsPerRow - 1) padding = "Padding: (Left: 10);";
                    else padding = "Padding: (Horizontal: 5);";

                    cmd.appendInline("#" + rowId,
                        "Group { FlexWeight: 1; " + padding +
                        "  Button #" + cardId + " { Background: (Color: #151d28); " +
                        "    Group { LayoutMode: Top; Padding: (Full: 12); " +
                        "      Group { Anchor: (Height: 50); LayoutMode: Left; " +
                        "        Group { FlexWeight: 1; } " +
                        "        Group #" + cardId + "Icon { Anchor: (Width: 48, Height: 48); Background: (Color: #1a2535); } " +
                        "        Group { FlexWeight: 1; } " +
                        "      } " +
                        "      Label #" + cardId + "Name { Anchor: (Height: 28, Top: 5); " +
                        "        Style: (FontSize: 15, TextColor: " + color + ", RenderBold: true, RenderUppercase: true, HorizontalAlignment: Center, VerticalAlignment: Center); } " +
                        "      Label #" + cardId + "Desc { Anchor: (Height: 40); " +
                        "        Style: (FontSize: 11, TextColor: #7c8b99, HorizontalAlignment: Center, VerticalAlignment: Center); } " +
                        "      Label #" + cardId + "Status { Anchor: (Height: 25); " +
                        "        Style: (FontSize: 12, TextColor: " + statusColor + ", RenderBold: true, HorizontalAlignment: Center, VerticalAlignment: Center); } " +
                        "    } " +
                        "  } " +
                        "}");

                    cmd.set("#" + cardId + "Name.Text", kit.displayName != null ? kit.displayName : kit.id);
                    cmd.set("#" + cardId + "Desc.Text", kit.description != null ? kit.description : "");
                    cmd.set("#" + cardId + "Status.Text", statusText);

                    // TODO: Item icon via setObject - disabled for now, needs valid item IDs
                    // if (kit.items != null && !kit.items.isEmpty()) {
                    //     try {
                    //         String iconItemId = kit.items.get(0).itemId;
                    //         if (iconItemId != null && !iconItemId.isEmpty()) {
                    //             if (!iconItemId.contains(":")) iconItemId = "minecraft:" + iconItemId;
                    //             ItemStack iconStack = new ItemStack(iconItemId, 1);
                    //             if (iconStack != null && !ItemStack.isEmpty(iconStack)) {
                    //                 cmd.setObject("#" + cardId + "Icon", iconStack);
                    //             }
                    //         }
                    //     } catch (Exception ignored) {}
                    // }

                    event.addEventBinding(CustomUIEventBindingType.Activating, "#" + cardId,
                        EventData.of("Action", "viewKit").append("KitId", kit.id), false);
                } else {
                    // Empty spacer for incomplete row
                    cmd.appendInline("#" + rowId, "Group { FlexWeight: 1; }");
                }
            }
        }

        // Bouton admin config (visible uniquement pour les admins)
        appendAdminButton(cmd, event, uuid);
    }

    private void appendAdminButton(UICommandBuilder cmd, UIEventBuilder event, UUID uuid) {
        boolean isAdmin = false;
        try {
            var perms = PermissionsModule.get();
            isAdmin = perms.getGroupsForUser(uuid).contains("OP")
                || perms.hasPermission(uuid, "prison.admin")
                || perms.hasPermission(uuid, "*");
        } catch (Exception ignored) {}

        if (isAdmin) {
            cmd.appendInline("#HubGrid",
                "Group { Anchor: (Height: 40, Top: 10); LayoutMode: Left; " +
                "  Group { FlexWeight: 1; } " +
                "  Button #AdminConfigBtn { Anchor: (Width: 180, Height: 32); Background: (Color: #2d4a5a); " +
                "    Label { Style: (FontSize: 12, TextColor: #ffd700, RenderBold: true, HorizontalAlignment: Center, VerticalAlignment: Center); } } " +
                "  Group { FlexWeight: 1; } " +
                "}");
            cmd.set("#AdminConfigBtn Label.Text", "CONFIG ADMIN");
            event.addEventBinding(CustomUIEventBindingType.Activating, "#AdminConfigBtn",
                EventData.of("Action", "openKitConfig"), false);
        }
    }

    /**
     * Affiche le detail d'un kit.
     */
    private void buildKitDetail(UICommandBuilder cmd, UIEventBuilder event, UUID uuid, String kitId) {
        PrisonConfig.KitDefinition kit = plugin.getConfig().getKit(kitId);
        if (kit == null) return;

        showSubPage(cmd);
        cmd.clear("#PageContent");
        cmd.set("#HeaderTitle.Text", kit.displayName != null ? kit.displayName : kit.id);

        String color = kit.color != null ? kit.color : "#4fc3f7";

        // Kit name
        cmd.appendInline("#PageContent",
            "Label { Anchor: (Height: 35); " +
            "Style: (FontSize: 18, TextColor: " + color + ", RenderBold: true, VerticalAlignment: Center); }" );
        cmd.set("#PageContent[0] Label.Text", kit.displayName != null ? kit.displayName : kit.id);

        // Description
        if (kit.description != null && !kit.description.isEmpty()) {
            cmd.appendInline("#PageContent",
                "Label #KitDesc { Anchor: (Height: 25); " +
                "Style: (FontSize: 12, TextColor: #96a9be); }");
            cmd.set("#KitDesc.Text", kit.description);
        }

        // Cooldown info
        String cooldownText;
        if (kit.cooldownSeconds < 0) {
            cooldownText = "Pas de cooldown (illimite)";
        } else if (kit.cooldownSeconds == 0) {
            cooldownText = "Usage unique";
        } else {
            cooldownText = "Cooldown: " + KitManager.formatCooldown(kit.cooldownSeconds);
        }
        cmd.appendInline("#PageContent",
            "Label #CooldownInfo { Anchor: (Height: 22, Top: 5); " +
            "Style: (FontSize: 11, TextColor: #7c8b99); }");
        cmd.set("#CooldownInfo.Text", cooldownText);

        // Items header
        cmd.appendInline("#PageContent",
            "Label { Anchor: (Height: 30, Top: 15); Text: \"CONTENU DU KIT\"; " +
            "Style: (FontSize: 14, TextColor: #ffd700, RenderBold: true); }");

        // Column header
        cmd.appendInline("#PageContent",
            "Group { Anchor: (Height: 22); LayoutMode: Left; Padding: (Horizontal: 10); Background: (Color: #0d1520); " +
            "  Label { FlexWeight: 1; Text: \"Item\"; Style: (FontSize: 10, TextColor: #7c8b99, VerticalAlignment: Center); } " +
            "  Label { Anchor: (Width: 80); Text: \"Quantite\"; Style: (FontSize: 10, TextColor: #7c8b99, VerticalAlignment: Center); } " +
            "}");

        // Items list
        if (kit.items != null && !kit.items.isEmpty()) {
            int idx = 0;
            for (PrisonConfig.KitItem item : kit.items) {
                String bgColor = idx % 2 == 0 ? "#111b27" : "#151d28";
                String itemRowId = "ItemRow" + idx;

                cmd.appendInline("#PageContent",
                    "Group #" + itemRowId + " { Anchor: (Height: 36); LayoutMode: Left; Padding: (Horizontal: 10); Background: (Color: " + bgColor + "); " +
                    "  Group #" + itemRowId + "Icon { Anchor: (Width: 32, Height: 32); Background: (Color: #1a2535); } " +
                    "  Label #IName { FlexWeight: 1; Anchor: (Left: 8); Style: (FontSize: 12, TextColor: #ffffff, VerticalAlignment: Center); } " +
                    "  Label #IQty { Anchor: (Width: 80); Style: (FontSize: 12, TextColor: #66bb6a, RenderBold: true, VerticalAlignment: Center); } " +
                    "}");

                cmd.set("#" + itemRowId + " #IName.Text", formatBlockName(item.itemId));
                cmd.set("#" + itemRowId + " #IQty.Text", "x" + item.quantity);

                // TODO: Item icon via setObject - disabled for now
                // try {
                //     String iconId = item.itemId;
                //     if (iconId != null && !iconId.isEmpty()) {
                //         if (!iconId.contains(":")) iconId = "minecraft:" + iconId;
                //         cmd.setObject("#" + itemRowId + "Icon", new ItemStack(iconId, 1));
                //     }
                // } catch (Exception ignored) {}
                idx++;
            }
        } else {
            cmd.appendInline("#PageContent",
                "Label { Anchor: (Height: 25); Text: \"Kit vide.\"; " +
                "Style: (FontSize: 12, TextColor: #808080); }");
        }

        // Status + Claim button
        long remaining = plugin.getKitManager().getRemainingCooldown(uuid, kitId);
        boolean canClaim = remaining == 0;

        String statusText;
        String statusColor;
        if (remaining == -2) {
            statusText = "Deja reclame";
            statusColor = "#ef5350";
        } else if (remaining > 0) {
            statusText = "Disponible dans " + KitManager.formatCooldown(remaining);
            statusColor = "#ffa726";
        } else {
            statusText = "Disponible!";
            statusColor = "#66bb6a";
        }

        cmd.appendInline("#PageContent",
            "Label #ClaimStatus { Anchor: (Height: 30, Top: 15); " +
            "Style: (FontSize: 14, TextColor: " + statusColor + ", RenderBold: true, HorizontalAlignment: Center); }");
        cmd.set("#ClaimStatus.Text", statusText);

        if (canClaim) {
            cmd.appendInline("#PageContent",
                "Group { Anchor: (Height: 55, Top: 10); LayoutMode: Left; " +
                "  Group { FlexWeight: 1; } " +
                "  Button #ClaimBtn { Anchor: (Width: 220, Height: 45); Background: (Color: #2a5f2a); " +
                "    Label { Style: (FontSize: 16, TextColor: #ffffff, RenderBold: true, HorizontalAlignment: Center, VerticalAlignment: Center); } } " +
                "  Group { FlexWeight: 1; } " +
                "}");
            cmd.set("#ClaimBtn Label.Text", "RECLAMER");
            event.addEventBinding(CustomUIEventBindingType.Activating, "#ClaimBtn",
                EventData.of("Action", "claimKit").append("KitId", kitId), false);
        }
    }

    @Override
    public void handleDataEvent(@Nonnull Ref<EntityStore> ref, @Nonnull Store<EntityStore> store, @Nonnull PageData data) {
        super.handleDataEvent(ref, store, data);

        Player player = store.getComponent(ref, Player.getComponentType());
        UICommandBuilder cmd = new UICommandBuilder();
        UIEventBuilder event = new UIEventBuilder();
        UUID uuid = playerRef.getUuid();

        if (data.action == null) return;

        switch (data.action) {
            case "openKitConfig" -> {
                plugin.getUIManager().openKitConfig(player);
                return;
            }
            case "viewKit" -> {
                if (data.kitId != null) {
                    currentPage = data.kitId;
                    buildKitDetail(cmd, event, uuid, data.kitId);
                    sendUpdate(cmd, event, false);
                }
                return;
            }
            case "claimKit" -> {
                if (data.kitId != null) {
                    KitManager.ClaimResult result = plugin.getKitManager().claimKit(player, data.kitId);
                    switch (result) {
                        case SUCCESS -> {
                            PrisonConfig.KitDefinition kit = plugin.getConfig().getKit(data.kitId);
                            String name = kit != null ? kit.displayName : data.kitId;
                            player.sendMessage(Message.raw("Kit " + name + " reclame avec succes!"));
                        }
                        case ALREADY_CLAIMED -> player.sendMessage(Message.raw("Tu as deja reclame ce kit!"));
                        case ON_COOLDOWN -> {
                            long rem = plugin.getKitManager().getRemainingCooldown(uuid, data.kitId);
                            player.sendMessage(Message.raw("Cooldown actif! Disponible dans " + KitManager.formatCooldown(rem)));
                        }
                        case NO_PERMISSION -> player.sendMessage(Message.raw("Tu n'as pas la permission pour ce kit!"));
                        case INVENTORY_FULL -> player.sendMessage(Message.raw("Ton inventaire est plein!"));
                        case KIT_NOT_FOUND -> player.sendMessage(Message.raw("Kit introuvable!"));
                    }
                    // Rebuild detail page to update status
                    buildKitDetail(cmd, event, uuid, data.kitId);
                    sendUpdate(cmd, event, false);
                }
                return;
            }
            case "back" -> {
                currentPage = "hub";
                buildKitGrid(cmd, event, uuid);
                sendUpdate(cmd, event, false);
                return;
            }
        }
    }

    // === Helpers ===

    private boolean hasKitPermission(UUID uuid, PrisonConfig.KitDefinition kit) {
        if (kit.permission == null || kit.permission.isEmpty()) return true;
        try {
            var perms = PermissionsModule.get();
            if (perms.getGroupsForUser(uuid).contains("OP")) return true;
            return perms.hasPermission(uuid, kit.permission) || perms.hasPermission(uuid, "*");
        } catch (Exception e) {
            return true;
        }
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
                .build();

        public String action;
        public String kitId;
    }
}
