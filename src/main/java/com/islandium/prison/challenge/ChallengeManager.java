package com.islandium.prison.challenge;

import com.islandium.core.api.IslandiumAPI;
import com.islandium.core.api.economy.EconomyService;
import com.islandium.core.database.SQLExecutor;
import com.islandium.prison.PrisonPlugin;
import com.islandium.core.api.util.NotificationType;
import com.islandium.core.api.util.TitleUtil;
import org.jetbrains.annotations.NotNull;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.SlotTransaction;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manager central pour le systeme de challenges.
 * Stockage SQL avec cache en memoire pour les performances.
 * Les donnees sont chargees au demarrage et persistees de maniere async.
 */
public class ChallengeManager {

    private final PrisonPlugin plugin;

    // Cache en memoire : UUID -> (challengeId -> ProgressData)
    private final Map<UUID, PlayerChallengeProgress> playerProgress = new ConcurrentHashMap<>();

    public ChallengeManager(@NotNull PrisonPlugin plugin) {
        this.plugin = plugin;
    }

    private SQLExecutor getSql() {
        return plugin.getCore().getDatabaseManager().getExecutor();
    }

    // ===========================
    // Migrations & Loading
    // ===========================

    /**
     * Cree la table SQL si elle n'existe pas.
     */
    public void runMigrations() {
        try {
            getSql().execute("""
                CREATE TABLE IF NOT EXISTS prison_challenge_progress (
                    player_uuid CHAR(36) NOT NULL,
                    challenge_id VARCHAR(64) NOT NULL,
                    current_value BIGINT DEFAULT 0,
                    completed_tier INT DEFAULT 0,
                    updated_at BIGINT NOT NULL,
                    PRIMARY KEY (player_uuid, challenge_id),
                    INDEX idx_player (player_uuid)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """).join();
            plugin.log(Level.INFO, "Challenge table migration completed.");
        } catch (Exception e) {
            plugin.log(Level.SEVERE, "Failed to run challenge migrations: " + e.getMessage());
        }
    }

    /**
     * Charge toutes les donnees de progression depuis SQL vers le cache memoire.
     * Appele au demarrage du serveur.
     */
    public void loadAll() {
        try {
            List<ProgressRow> rows = getSql().queryList(
                "SELECT player_uuid, challenge_id, current_value, completed_tier FROM prison_challenge_progress",
                rs -> {
                    try {
                        return new ProgressRow(
                            rs.getString("player_uuid"),
                            rs.getString("challenge_id"),
                            rs.getLong("current_value"),
                            rs.getInt("completed_tier")
                        );
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            ).join();

            for (ProgressRow row : rows) {
                UUID uuid = UUID.fromString(row.playerUuid);
                PlayerChallengeProgress progress = playerProgress.computeIfAbsent(uuid, k -> new PlayerChallengeProgress());
                PlayerChallengeProgress.ChallengeProgressData data = progress.getOrCreate(row.challengeId);
                data.currentValue = row.currentValue;
                data.completedTier = row.completedTier;
            }

            plugin.log(Level.INFO, "Loaded challenge progress for " + playerProgress.size() + " players from SQL.");
        } catch (Exception e) {
            plugin.log(Level.SEVERE, "Failed to load challenge data from SQL: " + e.getMessage());
        }
    }

    /**
     * Sauvegarde toute la progression en memoire vers SQL (batch).
     * Appele a l'arret du serveur.
     */
    public void saveAll() {
        try {
            String sql = """
                INSERT INTO prison_challenge_progress (player_uuid, challenge_id, current_value, completed_tier, updated_at)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    current_value = VALUES(current_value),
                    completed_tier = VALUES(completed_tier),
                    updated_at = VALUES(updated_at)
            """;

            List<Object[]> batchParams = new ArrayList<>();
            long now = System.currentTimeMillis();

            for (Map.Entry<UUID, PlayerChallengeProgress> entry : playerProgress.entrySet()) {
                String uuidStr = entry.getKey().toString();
                for (Map.Entry<String, PlayerChallengeProgress.ChallengeProgressData> cEntry : entry.getValue().challenges.entrySet()) {
                    PlayerChallengeProgress.ChallengeProgressData data = cEntry.getValue();
                    batchParams.add(new Object[]{
                        uuidStr,
                        cEntry.getKey(),
                        data.currentValue,
                        data.completedTier,
                        now
                    });
                }
            }

            if (!batchParams.isEmpty()) {
                getSql().executeBatch(sql, batchParams).join();
                plugin.log(Level.INFO, "Saved " + batchParams.size() + " challenge progress records to SQL.");
            }
        } catch (Exception e) {
            plugin.log(Level.SEVERE, "Failed to save challenge data to SQL: " + e.getMessage());
        }
    }

    // ===========================
    // Async SQL persistence (per-record)
    // ===========================

    /**
     * Persiste un seul record de progression de maniere async.
     * Appele apres chaque changement de progression.
     */
    private void persistAsync(@NotNull UUID uuid, @NotNull String challengeId, @NotNull PlayerChallengeProgress.ChallengeProgressData data) {
        try {
            getSql().execute("""
                INSERT INTO prison_challenge_progress (player_uuid, challenge_id, current_value, completed_tier, updated_at)
                VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    current_value = VALUES(current_value),
                    completed_tier = VALUES(completed_tier),
                    updated_at = VALUES(updated_at)
            """, uuid.toString(), challengeId, data.currentValue, data.completedTier, System.currentTimeMillis());
        } catch (Exception e) {
            plugin.log(Level.WARNING, "Failed to persist challenge progress: " + e.getMessage());
        }
    }

    // ===========================
    // Progress Management
    // ===========================

    @NotNull
    private PlayerChallengeProgress getProgress(@NotNull UUID uuid) {
        return playerProgress.computeIfAbsent(uuid, k -> new PlayerChallengeProgress());
    }

    /**
     * Obtient la progression d'un joueur pour un challenge specifique.
     */
    @NotNull
    public PlayerChallengeProgress.ChallengeProgressData getProgressData(@NotNull UUID uuid, @NotNull String challengeId) {
        return getProgress(uuid).getOrCreate(challengeId);
    }

    /**
     * Incremente la progression d'un challenge (pour les types cumulatifs).
     * Verifie automatiquement si un nouveau palier est atteint et donne les recompenses.
     *
     * @return true si un nouveau palier a ete complete
     */
    public boolean incrementProgress(@NotNull UUID uuid, @NotNull String challengeId, long amount) {
        ChallengeDefinition def = ChallengeRegistry.getChallenge(challengeId);
        if (def == null) return false;

        PlayerChallengeProgress.ChallengeProgressData data = getProgressData(uuid, challengeId);

        // Deja tous les paliers completes ?
        if (data.completedTier >= def.getTierCount()) {
            return false;
        }

        data.currentValue += amount;
        boolean result = checkAndRewardTiers(uuid, def, data);
        persistAsync(uuid, challengeId, data);
        return result;
    }

    /**
     * Definit la valeur absolue de progression (pour ACCUMULATE_BALANCE, BUY_FORTUNE etc.).
     *
     * @return true si un nouveau palier a ete complete
     */
    public boolean setProgress(@NotNull UUID uuid, @NotNull String challengeId, long value) {
        ChallengeDefinition def = ChallengeRegistry.getChallenge(challengeId);
        if (def == null) return false;

        PlayerChallengeProgress.ChallengeProgressData data = getProgressData(uuid, challengeId);

        if (data.completedTier >= def.getTierCount()) {
            return false;
        }

        long oldValue = data.currentValue;
        data.currentValue = Math.max(data.currentValue, value);

        if (data.currentValue != oldValue) {
            boolean result = checkAndRewardTiers(uuid, def, data);
            persistAsync(uuid, challengeId, data);
            return result;
        }

        return checkAndRewardTiers(uuid, def, data);
    }

    /**
     * Verifie les paliers et donne les recompenses si atteints.
     */
    private boolean checkAndRewardTiers(@NotNull UUID uuid, @NotNull ChallengeDefinition def,
                                         @NotNull PlayerChallengeProgress.ChallengeProgressData data) {
        boolean tierCompleted = false;
        List<ChallengeDefinition.ChallengeTier> tiers = def.getTiers();

        while (data.completedTier < tiers.size()) {
            ChallengeDefinition.ChallengeTier nextTier = tiers.get(data.completedTier);
            if (data.currentValue >= nextTier.target()) {
                data.completedTier++;
                tierCompleted = true;

                // Donner la recompense
                BigDecimal reward = nextTier.reward();
                if (reward.compareTo(BigDecimal.ZERO) > 0) {
                    EconomyService eco = getEconomyService();
                    if (eco != null) {
                        try {
                            eco.addBalance(uuid, reward, "Challenge reward: " + def.getDisplayName());
                        } catch (Exception e) {
                            plugin.log(Level.WARNING, "Failed to give challenge reward: " + e.getMessage());
                        }
                    }
                }

                // Notification au joueur
                notifyTierComplete(uuid, def, data.completedTier, tiers.size(), reward);
            } else {
                break;
            }
        }

        return tierCompleted;
    }

    private void notifyTierComplete(@NotNull UUID uuid, @NotNull ChallengeDefinition def,
                                     int tier, int totalTiers, @NotNull BigDecimal reward) {
        plugin.getCore().getPlayerManager().getOnlinePlayer(uuid).ifPresent(p -> {
            String tierText = totalTiers > 1 ? " (palier " + tier + "/" + totalTiers + ")" : "";
            boolean allDone = tier >= totalTiers;
            String rewardText = "+" + com.islandium.prison.economy.SellService.formatMoney(reward);

            Player hytalePlayer = p.getHytalePlayer();
            if (hytalePlayer != null) {
                if (allDone) {
                    TitleUtil.showTitle(hytalePlayer,
                        "§a" + def.getDisplayName() + " COMPLETE!",
                        "§e" + rewardText,
                        0.3f, 2.0f, 0.5f);
                } else {
                    TitleUtil.showTitle(hytalePlayer,
                        "§a" + def.getDisplayName() + tierText,
                        "§e" + rewardText,
                        0.3f, 1.5f, 0.3f);
                }
            } else {
                // Fallback si le Player natif n'est pas disponible
                p.sendNotification(NotificationType.SUCCESS,
                    def.getDisplayName() + (allDone ? " -> COMPLETE!" : tierText), rewardText);
            }
        });
    }

    // ===========================
    // SUBMIT_ITEMS - Soumission d'items
    // ===========================

    /**
     * Tente de soumettre les items requis pour un challenge SUBMIT_ITEMS.
     * Verifie l'inventaire du joueur, retire les items si suffisants, et complete le palier.
     *
     * @return 0 = succes, 1 = pas assez d'items, 2 = type incorrect, 3 = deja complete
     */
    public int trySubmitItems(@NotNull UUID uuid, @NotNull String challengeId, @NotNull Player player) {
        ChallengeDefinition def = ChallengeRegistry.getChallenge(challengeId);
        if (def == null || def.getType() != ChallengeType.SUBMIT_ITEMS) return 2;

        PlayerChallengeProgress.ChallengeProgressData data = getProgressData(uuid, challengeId);
        if (data.completedTier >= def.getTierCount()) return 3;

        ChallengeDefinition.ChallengeTier tier = def.getTiers().get(data.completedTier);
        List<ChallengeDefinition.RequiredItem> requiredItems = tier.requiredItems();
        if (requiredItems.isEmpty()) return 2;

        Inventory inv = player.getInventory();
        if (inv == null) return 1;

        // Phase 1: Compter les items disponibles
        Map<String, Integer> available = new HashMap<>();
        countItemsInContainer(inv.getStorage(), available);
        countItemsInContainer(inv.getHotbar(), available);

        // Phase 2: Verifier que tous les items requis sont presents
        for (ChallengeDefinition.RequiredItem req : requiredItems) {
            int has = available.getOrDefault(req.itemId(), 0);
            if (has < req.quantity()) {
                return 1; // Pas assez
            }
        }

        // Phase 3: Retirer les items de l'inventaire
        for (ChallengeDefinition.RequiredItem req : requiredItems) {
            int toRemove = req.quantity();
            toRemove = removeItemsFromContainer(inv.getStorage(), req.itemId(), toRemove);
            if (toRemove > 0) {
                toRemove = removeItemsFromContainer(inv.getHotbar(), req.itemId(), toRemove);
            }
        }

        // Phase 4: Completer le palier
        data.completedTier++;
        BigDecimal reward = tier.reward();
        if (reward.compareTo(BigDecimal.ZERO) > 0) {
            EconomyService eco = getEconomyService();
            if (eco != null) {
                try {
                    eco.addBalance(uuid, reward, "Challenge reward: " + def.getDisplayName());
                } catch (Exception e) {
                    plugin.log(Level.WARNING, "Failed to give challenge reward: " + e.getMessage());
                }
            }
        }

        notifyTierComplete(uuid, def, data.completedTier, def.getTierCount(), reward);
        persistAsync(uuid, challengeId, data);
        return 0;
    }

    /**
     * Retourne la liste des items manquants pour le palier actuel d'un challenge SUBMIT_ITEMS.
     */
    @NotNull
    public List<String> getMissingItems(@NotNull UUID uuid, @NotNull String challengeId, @NotNull Player player) {
        ChallengeDefinition def = ChallengeRegistry.getChallenge(challengeId);
        if (def == null || def.getType() != ChallengeType.SUBMIT_ITEMS) return List.of();

        PlayerChallengeProgress.ChallengeProgressData data = getProgressData(uuid, challengeId);
        if (data.completedTier >= def.getTierCount()) return List.of();

        ChallengeDefinition.ChallengeTier tier = def.getTiers().get(data.completedTier);
        Inventory inv = player.getInventory();
        if (inv == null) return List.of();

        Map<String, Integer> available = new HashMap<>();
        countItemsInContainer(inv.getStorage(), available);
        countItemsInContainer(inv.getHotbar(), available);

        List<String> missing = new ArrayList<>();
        for (ChallengeDefinition.RequiredItem req : tier.requiredItems()) {
            int has = available.getOrDefault(req.itemId(), 0);
            if (has < req.quantity()) {
                missing.add((req.quantity() - has) + "x " + shortItemName(req.itemId()));
            }
        }
        return missing;
    }

    private void countItemsInContainer(@NotNull ItemContainer container, @NotNull Map<String, Integer> counts) {
        for (short slot = 0; slot < container.getCapacity(); slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (ItemStack.isEmpty(stack)) continue;
            counts.merge(stack.getItemId(), stack.getQuantity(), Integer::sum);
        }
    }

    /**
     * Retire un nombre specifique d'items d'un conteneur.
     * @return le nombre restant a retirer (0 si tout a ete retire)
     */
    private int removeItemsFromContainer(@NotNull ItemContainer container, @NotNull String itemId, int toRemove) {
        for (short slot = 0; slot < container.getCapacity() && toRemove > 0; slot++) {
            ItemStack stack = container.getItemStack(slot);
            if (ItemStack.isEmpty(stack) || !stack.getItemId().equals(itemId)) continue;

            int inSlot = stack.getQuantity();
            SlotTransaction tx = container.removeItemStackFromSlot(slot);
            if (!tx.succeeded()) continue;

            if (inSlot <= toRemove) {
                toRemove -= inSlot;
            } else {
                // Remettre le surplus
                int surplus = inSlot - toRemove;
                container.addItemStack(new ItemStack(itemId, surplus));
                toRemove = 0;
            }
        }
        return toRemove;
    }

    private String shortItemName(@NotNull String itemId) {
        int colonIdx = itemId.indexOf(':');
        return colonIdx >= 0 ? itemId.substring(colonIdx + 1) : itemId;
    }

    // ===========================
    // Query Methods
    // ===========================

    /**
     * Verifie si tous les challenges d'un rang sont entierement completes.
     */
    public boolean areAllChallengesComplete(@NotNull UUID uuid, @NotNull String rankId) {
        List<ChallengeDefinition> challenges = ChallengeRegistry.getChallengesForRank(rankId);
        if (challenges.isEmpty()) return true;

        for (ChallengeDefinition def : challenges) {
            PlayerChallengeProgress.ChallengeProgressData data = getProgressData(uuid, def.getId());
            if (data.completedTier < def.getTierCount()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Compte le nombre de challenges entierement completes pour un rang.
     */
    public int getCompletedCount(@NotNull UUID uuid, @NotNull String rankId) {
        List<ChallengeDefinition> challenges = ChallengeRegistry.getChallengesForRank(rankId);
        int count = 0;
        for (ChallengeDefinition def : challenges) {
            PlayerChallengeProgress.ChallengeProgressData data = getProgressData(uuid, def.getId());
            if (data.completedTier >= def.getTierCount()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Reset les challenges d'un rang pour un joueur (utilise au prestige).
     */
    public void resetChallengesForRank(@NotNull UUID uuid, @NotNull String rankId) {
        List<ChallengeDefinition> challenges = ChallengeRegistry.getChallengesForRank(rankId);
        PlayerChallengeProgress progress = getProgress(uuid);
        List<String> challengeIds = new ArrayList<>();
        for (ChallengeDefinition def : challenges) {
            progress.challenges.remove(def.getId());
            challengeIds.add(def.getId());
        }

        // Supprimer en SQL aussi
        for (String challengeId : challengeIds) {
            try {
                getSql().execute(
                    "DELETE FROM prison_challenge_progress WHERE player_uuid = ? AND challenge_id = ?",
                    uuid.toString(), challengeId
                );
            } catch (Exception e) {
                plugin.log(Level.WARNING, "Failed to delete challenge progress from SQL: " + e.getMessage());
            }
        }
    }

    /**
     * Reset un seul challenge d'un joueur (admin).
     */
    public void resetSingleChallenge(@NotNull UUID uuid, @NotNull String challengeId) {
        PlayerChallengeProgress progress = getProgress(uuid);
        progress.challenges.remove(challengeId);

        try {
            getSql().execute(
                "DELETE FROM prison_challenge_progress WHERE player_uuid = ? AND challenge_id = ?",
                uuid.toString(), challengeId
            );
        } catch (Exception e) {
            plugin.log(Level.WARNING, "Failed to delete single challenge progress from SQL: " + e.getMessage());
        }
    }

    /**
     * Reset TOUS les challenges d'un joueur (utilise au prestige).
     */
    public void resetAllChallenges(@NotNull UUID uuid) {
        playerProgress.remove(uuid);

        // Supprimer en SQL aussi
        try {
            getSql().execute(
                "DELETE FROM prison_challenge_progress WHERE player_uuid = ?",
                uuid.toString()
            );
        } catch (Exception e) {
            plugin.log(Level.WARNING, "Failed to delete all challenge progress from SQL: " + e.getMessage());
        }
    }

    // ===========================
    // Utility
    // ===========================

    private EconomyService getEconomyService() {
        IslandiumAPI api = IslandiumAPI.get();
        return api != null ? api.getEconomyService() : null;
    }

    // ===========================
    // Data Row (for loading)
    // ===========================

    private record ProgressRow(String playerUuid, String challengeId, long currentValue, int completedTier) {}
}
