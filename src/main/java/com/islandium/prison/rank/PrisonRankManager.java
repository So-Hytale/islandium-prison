package com.islandium.prison.rank;

import com.islandium.core.api.IslandiumAPI;
import com.islandium.core.api.economy.EconomyService;
import com.islandium.core.database.SQLExecutor;
import com.islandium.prison.PrisonPlugin;
import com.islandium.prison.config.PrisonConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Gestionnaire des rangs Prison.
 * Gere la progression A -> Z -> FREE.
 * Stockage SQL avec cache en memoire.
 */
public class PrisonRankManager {

    private static final String DEFAULT_RANK = "A";

    private final PrisonPlugin plugin;

    // UUID -> Rank ID
    private final Map<UUID, String> playerRanks = new ConcurrentHashMap<>();

    // Prestige system: UUID -> Prestige level
    private final Map<UUID, Integer> playerPrestiges = new ConcurrentHashMap<>();

    public PrisonRankManager(@NotNull PrisonPlugin plugin) {
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
                CREATE TABLE IF NOT EXISTS prison_player_ranks (
                    player_uuid CHAR(36) PRIMARY KEY,
                    rank_id VARCHAR(8) NOT NULL DEFAULT 'A',
                    prestige INT DEFAULT 0
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """).join();
            plugin.log(Level.INFO, "Player ranks table migration completed.");
        } catch (Exception e) {
            plugin.log(Level.SEVERE, "Failed to run ranks migrations: " + e.getMessage());
        }
    }

    /**
     * Charge les rangs des joueurs depuis SQL.
     */
    public void loadAll() {
        try {
            List<RankRow> rows = getSql().queryList(
                "SELECT player_uuid, rank_id, prestige FROM prison_player_ranks",
                rs -> {
                    try {
                        return new RankRow(
                            rs.getString("player_uuid"),
                            rs.getString("rank_id"),
                            rs.getInt("prestige")
                        );
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            ).join();

            for (RankRow row : rows) {
                UUID uuid = UUID.fromString(row.playerUuid);
                playerRanks.put(uuid, row.rankId);
                if (row.prestige > 0) {
                    playerPrestiges.put(uuid, row.prestige);
                }
            }

            plugin.log(Level.INFO, "Loaded " + playerRanks.size() + " player ranks from SQL.");
        } catch (Exception e) {
            plugin.log(Level.SEVERE, "Failed to load player ranks: " + e.getMessage());
        }
    }

    /**
     * Sauvegarde tous les rangs en batch vers SQL (shutdown).
     */
    public void saveAll() {
        try {
            String sql = """
                INSERT INTO prison_player_ranks (player_uuid, rank_id, prestige)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    rank_id = VALUES(rank_id),
                    prestige = VALUES(prestige)
            """;

            // Collecter tous les UUIDs uniques
            Set<UUID> allUuids = new HashSet<>();
            allUuids.addAll(playerRanks.keySet());
            allUuids.addAll(playerPrestiges.keySet());

            List<Object[]> batchParams = new ArrayList<>();
            for (UUID uuid : allUuids) {
                batchParams.add(new Object[]{
                    uuid.toString(),
                    playerRanks.getOrDefault(uuid, DEFAULT_RANK),
                    playerPrestiges.getOrDefault(uuid, 0)
                });
            }

            if (!batchParams.isEmpty()) {
                getSql().executeBatch(sql, batchParams).join();
                plugin.log(Level.INFO, "Saved " + batchParams.size() + " player ranks to SQL.");
            }
        } catch (Exception e) {
            plugin.log(Level.SEVERE, "Failed to save player ranks: " + e.getMessage());
        }
    }

    /**
     * Persiste un joueur de maniere async.
     */
    private void persistAsync(@NotNull UUID uuid) {
        try {
            getSql().execute("""
                INSERT INTO prison_player_ranks (player_uuid, rank_id, prestige)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    rank_id = VALUES(rank_id),
                    prestige = VALUES(prestige)
            """, uuid.toString(),
               playerRanks.getOrDefault(uuid, DEFAULT_RANK),
               playerPrestiges.getOrDefault(uuid, 0));
        } catch (Exception e) {
            plugin.log(Level.WARNING, "Failed to persist rank for " + uuid + ": " + e.getMessage());
        }
    }

    // === Rank Management ===

    @NotNull
    public String getPlayerRank(@NotNull UUID uuid) {
        return playerRanks.getOrDefault(uuid, DEFAULT_RANK);
    }

    public void setPlayerRank(@NotNull UUID uuid, @NotNull String rankId) {
        playerRanks.put(uuid, rankId.toUpperCase());
        persistAsync(uuid);
    }

    @Nullable
    public PrisonConfig.RankInfo getPlayerRankInfo(@NotNull UUID uuid) {
        String rankId = getPlayerRank(uuid);
        return plugin.getConfig().getRank(rankId);
    }

    @Nullable
    public PrisonConfig.RankInfo getNextRankInfo(@NotNull UUID uuid) {
        String currentRank = getPlayerRank(uuid);
        return plugin.getConfig().getNextRank(currentRank);
    }

    @Nullable
    private EconomyService getEconomyService() {
        IslandiumAPI api = IslandiumAPI.get();
        return api != null ? api.getEconomyService() : null;
    }

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

    public BigDecimal getRankupPrice(@NotNull UUID uuid, @NotNull PrisonConfig.RankInfo nextRank) {
        BigDecimal price = nextRank.price;

        int prestige = getPlayerPrestige(uuid);
        if (prestige > 0) {
            price = price.multiply(BigDecimal.valueOf(1 + prestige * 0.5));
        }

        return price;
    }

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

    public int maxRankup(@NotNull UUID uuid) {
        int count = 0;
        while (rankup(uuid) == RankupResult.SUCCESS) {
            count++;
        }
        return count;
    }

    // === Prestige System ===

    public int getPlayerPrestige(@NotNull UUID uuid) {
        return playerPrestiges.getOrDefault(uuid, 0);
    }

    public void setPlayerPrestige(@NotNull UUID uuid, int prestige) {
        playerPrestiges.put(uuid, prestige);
        persistAsync(uuid);
    }

    public boolean canPrestige(@NotNull UUID uuid) {
        String currentRank = getPlayerRank(uuid);
        return currentRank.equalsIgnoreCase("FREE");
    }

    public boolean prestige(@NotNull UUID uuid) {
        if (!canPrestige(uuid)) {
            return false;
        }

        int newPrestige = getPlayerPrestige(uuid) + 1;
        setPlayerPrestige(uuid, newPrestige);

        // Reset rank to A
        setPlayerRank(uuid, DEFAULT_RANK);

        // Reset tous les challenges
        try { plugin.getChallengeManager().resetAllChallenges(uuid); } catch (Exception ignored) {}
        try { plugin.getChallengeTracker().invalidateRankCache(uuid); } catch (Exception ignored) {}

        // Reset balance
        EconomyService eco = getEconomyService();
        if (eco != null) {
            BigDecimal startingBalance = plugin.getCore().getConfigManager().getMainConfig().getStartingBalance();
            eco.setBalance(uuid, startingBalance);
        }

        return true;
    }

    // === Rank Utility ===

    public double getPlayerMultiplier(@NotNull UUID uuid) {
        PrisonConfig.RankInfo rankInfo = getPlayerRankInfo(uuid);
        double baseMultiplier = rankInfo != null ? rankInfo.multiplier : 1.0;

        int prestige = getPlayerPrestige(uuid);
        double prestigeBonus = prestige * 0.25;

        return baseMultiplier + prestigeBonus;
    }

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

    // === Data Row ===

    private record RankRow(String playerUuid, String rankId, int prestige) {}
}
