package com.islandium.prison.kit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.permissions.PermissionsModule;
import com.islandium.prison.PrisonPlugin;
import com.islandium.prison.config.PrisonConfig;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Gestionnaire des kits : give items, cooldowns, first join.
 */
public class KitManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type COOLDOWN_TYPE = new TypeToken<Map<String, Map<String, Long>>>() {}.getType();

    private final PrisonPlugin plugin;
    private final Path cooldownPath;

    // UUID (string) -> kitId -> timestamp (epoch ms)
    private final Map<String, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    // Track players who already got first-join kits
    private final Set<String> firstJoinGiven = ConcurrentHashMap.newKeySet();

    public KitManager(@NotNull PrisonPlugin plugin) {
        this.plugin = plugin;
        this.cooldownPath = plugin.getDataFolder().toPath().resolve("kit-cooldowns.json");
    }

    // === Persistence ===

    public void loadCooldowns() {
        try {
            if (Files.exists(cooldownPath)) {
                String content = Files.readString(cooldownPath);
                Map<String, Map<String, Long>> loaded = GSON.fromJson(content, COOLDOWN_TYPE);
                if (loaded != null) {
                    cooldowns.clear();
                    cooldowns.putAll(loaded);
                }
            }
        } catch (Exception e) {
            plugin.log(Level.WARNING, "Failed to load kit cooldowns: " + e.getMessage());
        }
    }

    public void saveCooldowns() {
        try {
            Files.createDirectories(cooldownPath.getParent());
            Files.writeString(cooldownPath, GSON.toJson(cooldowns));
        } catch (IOException e) {
            plugin.log(Level.WARNING, "Failed to save kit cooldowns: " + e.getMessage());
        }
    }

    // === Kit Claiming ===

    /**
     * Result of a kit claim attempt.
     */
    public enum ClaimResult {
        SUCCESS,
        KIT_NOT_FOUND,
        NO_PERMISSION,
        ON_COOLDOWN,
        ALREADY_CLAIMED,
        INVENTORY_FULL
    }

    /**
     * Tente de donner un kit a un joueur.
     */
    public ClaimResult claimKit(@NotNull Player player, @NotNull String kitId) {
        UUID uuid = getPlayerUuid(player);
        if (uuid == null) return ClaimResult.KIT_NOT_FOUND;

        PrisonConfig.KitDefinition kit = plugin.getConfig().getKit(kitId);
        if (kit == null) return ClaimResult.KIT_NOT_FOUND;

        // Check permission
        if (!hasKitPermission(uuid, kit)) {
            return ClaimResult.NO_PERMISSION;
        }

        // Check cooldown
        long remaining = getRemainingCooldown(uuid, kitId);
        if (kit.cooldownSeconds == 0) {
            // Usage unique - verifie si deja utilise
            if (remaining == -2) {
                return ClaimResult.ALREADY_CLAIMED;
            }
        } else if (kit.cooldownSeconds > 0 && remaining > 0) {
            return ClaimResult.ON_COOLDOWN;
        }

        // Give items
        boolean allGiven = giveItems(player, kit);

        // Record cooldown
        String uuidStr = uuid.toString();
        cooldowns.computeIfAbsent(uuidStr, k -> new ConcurrentHashMap<>());
        cooldowns.get(uuidStr).put(kitId, System.currentTimeMillis());
        saveCooldowns();

        return ClaimResult.SUCCESS;
    }

    /**
     * Donne les items d'un kit a un joueur.
     */
    private boolean giveItems(@NotNull Player player, @NotNull PrisonConfig.KitDefinition kit) {
        if (kit.items == null || kit.items.isEmpty()) return true;

        var inv = player.getInventory();
        if (inv == null) return false;

        var hotbar = inv.getHotbar();
        var storage = inv.getStorage();

        for (PrisonConfig.KitItem kitItem : kit.items) {
            if (kitItem.itemId == null || kitItem.quantity <= 0) continue;

            String itemId = kitItem.itemId;
            if (!itemId.contains(":")) {
                itemId = "minecraft:" + itemId;
            }

            ItemStack stack = new ItemStack(itemId, kitItem.quantity);

            // Try hotbar first, then storage
            var tx = hotbar.addItemStack(stack);
            if (!tx.succeeded()) {
                tx = storage.addItemStack(stack);
                if (!tx.succeeded()) {
                    player.sendMessage(Message.raw("Inventaire plein! Certains items n'ont pas pu etre donnes."));
                    return false;
                }
            }
        }
        return true;
    }

    // === Cooldown Logic ===

    /**
     * Retourne le temps restant en secondes avant de pouvoir reclamer un kit.
     * 0 = disponible
     * -1 = pas de cooldown (illimite)
     * -2 = deja reclame (usage unique)
     * > 0 = secondes restantes
     */
    public long getRemainingCooldown(@NotNull UUID uuid, @NotNull String kitId) {
        PrisonConfig.KitDefinition kit = plugin.getConfig().getKit(kitId);
        if (kit == null) return 0;

        // Pas de cooldown
        if (kit.cooldownSeconds < 0) return 0;

        String uuidStr = uuid.toString();
        Map<String, Long> playerCooldowns = cooldowns.get(uuidStr);
        if (playerCooldowns == null) return 0;

        Long lastClaim = playerCooldowns.get(kitId);
        if (lastClaim == null) return 0;

        if (kit.cooldownSeconds == 0) {
            // Usage unique - deja utilise
            return -2;
        }

        long elapsed = (System.currentTimeMillis() - lastClaim) / 1000;
        long remaining = kit.cooldownSeconds - elapsed;
        return Math.max(0, remaining);
    }

    /**
     * Verifie si un joueur peut reclamer un kit.
     */
    public boolean canClaim(@NotNull UUID uuid, @NotNull String kitId) {
        PrisonConfig.KitDefinition kit = plugin.getConfig().getKit(kitId);
        if (kit == null) return false;

        if (!hasKitPermission(uuid, kit)) return false;

        long remaining = getRemainingCooldown(uuid, kitId);
        return remaining == 0 || remaining == -1;
    }

    /**
     * Formate un cooldown en texte lisible.
     */
    public static String formatCooldown(long seconds) {
        if (seconds <= 0) return "Disponible";

        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        StringBuilder sb = new StringBuilder();
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (secs > 0 && hours == 0) sb.append(secs).append("s");
        return sb.toString().trim();
    }

    // === Permission Check ===

    private boolean hasKitPermission(@NotNull UUID uuid, @NotNull PrisonConfig.KitDefinition kit) {
        // Pas de permission requise
        if (kit.permission == null || kit.permission.isEmpty()) return true;

        try {
            var perms = PermissionsModule.get();
            // OP bypass
            if (perms.getGroupsForUser(uuid).contains("OP")) return true;
            return perms.hasPermission(uuid, kit.permission) || perms.hasPermission(uuid, "*");
        } catch (Exception e) {
            return true; // En cas d'erreur, autoriser
        }
    }

    // === First Join ===

    /**
     * Donne tous les kits marques giveOnFirstJoin a un nouveau joueur.
     */
    public void giveFirstJoinKits(@NotNull Player player) {
        UUID uuid = getPlayerUuid(player);
        if (uuid == null) return;

        String uuidStr = uuid.toString();

        // Verifie si le joueur a deja recu les kits first-join
        // On regarde dans les cooldowns s'il a deja claim un kit firstJoin
        Map<String, Long> playerCooldowns = cooldowns.get(uuidStr);

        for (PrisonConfig.KitDefinition kit : plugin.getConfig().getKits()) {
            if (!kit.giveOnFirstJoin) continue;

            // Verifie si deja donne
            if (playerCooldowns != null && playerCooldowns.containsKey(kit.id)) {
                continue; // Deja donne
            }

            // Verifie permission
            if (!hasKitPermission(uuid, kit)) continue;

            // Give items
            giveItems(player, kit);

            // Record comme claim
            cooldowns.computeIfAbsent(uuidStr, k -> new ConcurrentHashMap<>());
            cooldowns.get(uuidStr).put(kit.id, System.currentTimeMillis());

            player.sendMessage(Message.raw("Kit " + kit.displayName + " recu!"));
            plugin.log(Level.INFO, "Gave first-join kit '" + kit.id + "' to " + uuidStr);
        }

        saveCooldowns();
    }

    // === Utility ===

    private UUID getPlayerUuid(@NotNull Player player) {
        try {
            var ref = player.getReference();
            if (ref == null || !ref.isValid()) return null;
            var store = ref.getStore();
            var playerRef = store.getComponent(ref, com.hypixel.hytale.server.core.universe.PlayerRef.getComponentType());
            return playerRef != null ? playerRef.getUuid() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
