package com.islandium.prison.rank;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.islandium.core.api.IslandiumAPI;
import com.islandium.core.api.economy.EconomyService;
import com.islandium.prison.PrisonPlugin;
import com.islandium.prison.config.PrisonConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Gestionnaire des rangs Prison.
 * Gère la progression A -> Z -> FREE.
 */
public class PrisonRankManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DEFAULT_RANK = "A";

    private final PrisonPlugin plugin;
    private final Path playerRanksFile;

    // UUID -> Rank ID
    private final Map<UUID, String> playerRanks = new ConcurrentHashMap<>();

    // Prestige system: UUID -> Prestige level
    private final Map<UUID, Integer> playerPrestiges = new ConcurrentHashMap<>();

    public PrisonRankManager(@NotNull PrisonPlugin plugin) {
        this.plugin = plugin;
        this.playerRanksFile = plugin.getDataFolder().toPath().resolve("player_ranks.json");
    }

    /**
     * Charge les rangs des joueurs.
     */
    public void loadAll() {
        try {
            if (Files.exists(playerRanksFile)) {
                String content = Files.readString(playerRanksFile);
                PlayerRanksData data = GSON.fromJson(content, PlayerRanksData.class);

                if (data != null) {
                    if (data.ranks != null) {
                        for (Map.Entry<String, String> entry : data.ranks.entrySet()) {
                            playerRanks.put(UUID.fromString(entry.getKey()), entry.getValue());
                        }
                    }
                    if (data.prestiges != null) {
                        for (Map.Entry<String, Integer> entry : data.prestiges.entrySet()) {
                            playerPrestiges.put(UUID.fromString(entry.getKey()), entry.getValue());
                        }
                    }
                }

                plugin.log(Level.INFO, "Loaded " + playerRanks.size() + " player ranks");
            }
        } catch (Exception e) {
            plugin.log(Level.SEVERE, "Failed to load player ranks: " + e.getMessage());
        }
    }

    /**
     * Sauvegarde les rangs des joueurs.
     */
    public void saveAll() {
        try {
            PlayerRanksData data = new PlayerRanksData();
            data.ranks = new HashMap<>();
            data.prestiges = new HashMap<>();

            for (Map.Entry<UUID, String> entry : playerRanks.entrySet()) {
                data.ranks.put(entry.getKey().toString(), entry.getValue());
            }
            for (Map.Entry<UUID, Integer> entry : playerPrestiges.entrySet()) {
                data.prestiges.put(entry.getKey().toString(), entry.getValue());
            }

            Files.createDirectories(playerRanksFile.getParent());
            Files.writeString(playerRanksFile, GSON.toJson(data));
        } catch (IOException e) {
            plugin.log(Level.SEVERE, "Failed to save player ranks: " + e.getMessage());
        }
    }

    // === Rank Management ===

    /**
     * Obtient le rang d'un joueur.
     */
    @NotNull
    public String getPlayerRank(@NotNull UUID uuid) {
        return playerRanks.getOrDefault(uuid, DEFAULT_RANK);
    }

    /**
     * Définit le rang d'un joueur.
     */
    public void setPlayerRank(@NotNull UUID uuid, @NotNull String rankId) {
        playerRanks.put(uuid, rankId.toUpperCase());
        saveAll();
    }

    /**
     * Obtient les infos du rang actuel d'un joueur.
     */
    @Nullable
    public PrisonConfig.RankInfo getPlayerRankInfo(@NotNull UUID uuid) {
        String rankId = getPlayerRank(uuid);
        return plugin.getConfig().getRank(rankId);
    }

    /**
     * Obtient les infos du prochain rang pour un joueur.
     */
    @Nullable
    public PrisonConfig.RankInfo getNextRankInfo(@NotNull UUID uuid) {
        String currentRank = getPlayerRank(uuid);
        return plugin.getConfig().getNextRank(currentRank);
    }

    /**
     * Obtient le service économique.
     */
    @Nullable
    private EconomyService getEconomyService() {
        IslandiumAPI api = IslandiumAPI.get();
        return api != null ? api.getEconomyService() : null;
    }

    /**
     * Vérifie si un joueur peut rankup.
     */
    public RankupResult canRankup(@NotNull UUID uuid) {
        PrisonConfig.RankInfo nextRank = getNextRankInfo(uuid);

        if (nextRank == null) {
            return RankupResult.MAX_RANK;
        }

        // Verifier que tous les challenges du rang actuel sont completes
        if (!plugin.getChallengeManager().areAllChallengesComplete(uuid, getPlayerRank(uuid))) {
            return RankupResult.CHALLENGES_INCOMPLETE;
        }

        EconomyService eco = getEconomyService();
        if (eco == null) {
            plugin.log(Level.WARNING, "EconomyService not available for rankup check");
            return RankupResult.NOT_ENOUGH_MONEY;
        }

        try {
            BigDecimal playerBalance = eco.getBalance(uuid).join();
            BigDecimal price = getRankupPrice(uuid, nextRank);

            if (playerBalance.compareTo(price) < 0) {
                return RankupResult.NOT_ENOUGH_MONEY;
            }

            return RankupResult.SUCCESS;
        } catch (Exception e) {
            plugin.log(Level.WARNING, "Failed to check balance for rankup: " + e.getMessage());
            return RankupResult.NOT_ENOUGH_MONEY;
        }
    }

    /**
     * Calcule le prix de rankup avec le multiplicateur de prestige.
     */
    public BigDecimal getRankupPrice(@NotNull UUID uuid, @NotNull PrisonConfig.RankInfo nextRank) {
        BigDecimal price = nextRank.price;

        // Apply prestige multiplier
        int prestige = getPlayerPrestige(uuid);
        if (prestige > 0) {
            price = price.multiply(BigDecimal.valueOf(1 + prestige * 0.5)); // +50% par prestige
        }

        return price;
    }

    /**
     * Effectue le rankup d'un joueur.
     * @return Le résultat et le message à envoyer au joueur.
     */
    public RankupResult rankup(@NotNull UUID uuid) {
        RankupResult result = canRankup(uuid);

        if (result != RankupResult.SUCCESS) {
            return result;
        }

        PrisonConfig.RankInfo nextRank = getNextRankInfo(uuid);
        if (nextRank == null) return RankupResult.MAX_RANK;

        EconomyService eco = getEconomyService();
        if (eco == null) {
            plugin.log(Level.WARNING, "EconomyService not available for rankup");
            return RankupResult.NOT_ENOUGH_MONEY;
        }

        BigDecimal price = getRankupPrice(uuid, nextRank);

        // Deduct money
        eco.removeBalance(uuid, price, "Prison rankup to " + nextRank.id).join();

        // Challenge tracking - depense
        try { plugin.getChallengeTracker().onMoneySpent(uuid, price); } catch (Exception ignored) {}

        // Set new rank
        setPlayerRank(uuid, nextRank.id);

        // Invalider le cache de rang du tracker
        try { plugin.getChallengeTracker().invalidateRankCache(uuid); } catch (Exception ignored) {}

        return RankupResult.SUCCESS;
    }

    /**
     * Effectue un max rankup (rankup jusqu'au max possible).
     */
    public int maxRankup(@NotNull UUID uuid) {
        int count = 0;
        while (rankup(uuid) == RankupResult.SUCCESS) {
            count++;
        }
        return count;
    }

    // === Prestige System ===

    /**
     * Obtient le niveau de prestige d'un joueur.
     */
    public int getPlayerPrestige(@NotNull UUID uuid) {
        return playerPrestiges.getOrDefault(uuid, 0);
    }

    /**
     * Définit le niveau de prestige d'un joueur.
     */
    public void setPlayerPrestige(@NotNull UUID uuid, int prestige) {
        playerPrestiges.put(uuid, prestige);
        saveAll();
    }

    /**
     * Vérifie si un joueur peut prestige.
     */
    public boolean canPrestige(@NotNull UUID uuid) {
        String currentRank = getPlayerRank(uuid);
        return currentRank.equalsIgnoreCase("FREE");
    }

    /**
     * Effectue le prestige d'un joueur.
     * Reset le rang à A et incrémente le prestige.
     */
    public boolean prestige(@NotNull UUID uuid) {
        if (!canPrestige(uuid)) {
            return false;
        }

        // Increment prestige
        int newPrestige = getPlayerPrestige(uuid) + 1;
        setPlayerPrestige(uuid, newPrestige);

        // Reset rank to A
        setPlayerRank(uuid, DEFAULT_RANK);

        // Reset tous les challenges
        try { plugin.getChallengeManager().resetAllChallenges(uuid); } catch (Exception ignored) {}
        try { plugin.getChallengeTracker().invalidateRankCache(uuid); } catch (Exception ignored) {}

        // Reset balance (optional - configurable)
        EconomyService eco = getEconomyService();
        if (eco != null) {
            BigDecimal startingBalance = plugin.getCore().getConfigManager().getMainConfig().getStartingBalance();
            eco.setBalance(uuid, startingBalance);
        }

        return true;
    }

    // === Rank Utility ===

    /**
     * Obtient le multiplicateur de gains pour un joueur.
     */
    public double getPlayerMultiplier(@NotNull UUID uuid) {
        PrisonConfig.RankInfo rankInfo = getPlayerRankInfo(uuid);
        double baseMultiplier = rankInfo != null ? rankInfo.multiplier : 1.0;

        // Add prestige bonus
        int prestige = getPlayerPrestige(uuid);
        double prestigeBonus = prestige * 0.25; // +25% par prestige

        return baseMultiplier + prestigeBonus;
    }

    /**
     * Obtient l'index d'un rang (0 = A, 25 = Z, 26 = FREE).
     */
    public int getRankIndex(@NotNull String rankId) {
        if (rankId.equalsIgnoreCase("FREE")) return 26;
        if (rankId.length() == 1) {
            char c = rankId.toUpperCase().charAt(0);
            if (c >= 'A' && c <= 'Z') {
                return c - 'A';
            }
        }
        return -1;
    }

    /**
     * Vérifie si rank1 >= rank2.
     */
    public boolean isRankHigherOrEqual(@NotNull String rank1, @NotNull String rank2) {
        return getRankIndex(rank1) >= getRankIndex(rank2);
    }

    // === Result Enum ===

    public enum RankupResult {
        SUCCESS,
        NOT_ENOUGH_MONEY,
        MAX_RANK,
        CHALLENGES_INCOMPLETE
    }

    // === Data Class ===

    private static class PlayerRanksData {
        Map<String, String> ranks;
        Map<String, Integer> prestiges;
    }
}
