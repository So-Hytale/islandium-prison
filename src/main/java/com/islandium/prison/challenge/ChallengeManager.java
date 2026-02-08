package com.islandium.prison.challenge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.islandium.core.api.IslandiumAPI;
import com.islandium.core.api.economy.EconomyService;
import com.islandium.prison.PrisonPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manager central pour le systeme de challenges.
 * Gere la persistence, le suivi de progression et la completion.
 */
public class ChallengeManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final PrisonPlugin plugin;
    private final Path dataFile;

    private final Map<UUID, PlayerChallengeProgress> playerProgress = new ConcurrentHashMap<>();

    public ChallengeManager(@NotNull PrisonPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = plugin.getDataFolder().toPath().resolve("player_challenges.json");
    }

    // ===========================
    // Persistence
    // ===========================

    public void loadAll() {
        try {
            if (Files.exists(dataFile)) {
                String content = Files.readString(dataFile);
                ChallengeFileData data = GSON.fromJson(content, ChallengeFileData.class);

                if (data != null && data.players != null) {
                    for (Map.Entry<String, PlayerChallengeProgress> entry : data.players.entrySet()) {
                        playerProgress.put(UUID.fromString(entry.getKey()), entry.getValue());
                    }
                }

                plugin.log(Level.INFO, "Loaded " + playerProgress.size() + " player challenge progress records");
            }
        } catch (Exception e) {
            plugin.log(Level.SEVERE, "Failed to load challenge data: " + e.getMessage());
        }
    }

    public void saveAll() {
        try {
            ChallengeFileData data = new ChallengeFileData();
            data.players = new HashMap<>();

            for (Map.Entry<UUID, PlayerChallengeProgress> entry : playerProgress.entrySet()) {
                data.players.put(entry.getKey().toString(), entry.getValue());
            }

            Files.createDirectories(dataFile.getParent());
            Files.writeString(dataFile, GSON.toJson(data));
        } catch (IOException e) {
            plugin.log(Level.SEVERE, "Failed to save challenge data: " + e.getMessage());
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
        return checkAndRewardTiers(uuid, def, data);
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

        data.currentValue = Math.max(data.currentValue, value);
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
                        eco.addBalance(uuid, reward, "Challenge reward: " + def.getDisplayName()).join();
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
            String msg = allDone
                    ? "&a[Defi] &e" + def.getDisplayName() + " &a-> COMPLETE! &6+" + com.islandium.prison.economy.SellService.formatMoney(reward)
                    : "&a[Defi] &e" + def.getDisplayName() + tierText + " &6+" + com.islandium.prison.economy.SellService.formatMoney(reward);
            p.sendMessage(msg);
        });
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
        for (ChallengeDefinition def : challenges) {
            progress.challenges.remove(def.getId());
        }
    }

    /**
     * Reset TOUS les challenges d'un joueur (utilise au prestige).
     */
    public void resetAllChallenges(@NotNull UUID uuid) {
        playerProgress.remove(uuid);
    }

    // ===========================
    // Utility
    // ===========================

    private EconomyService getEconomyService() {
        IslandiumAPI api = IslandiumAPI.get();
        return api != null ? api.getEconomyService() : null;
    }

    // ===========================
    // Data Class
    // ===========================

    private static class ChallengeFileData {
        Map<String, PlayerChallengeProgress> players;
    }
}
