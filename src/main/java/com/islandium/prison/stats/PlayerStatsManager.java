package com.islandium.prison.stats;

import com.islandium.core.database.SQLExecutor;
import com.islandium.prison.PrisonPlugin;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Gestionnaire des statistiques joueurs Prison.
 * Stocke les stats de minage, argent gagne, temps joue, et niveaux d'upgrades.
 * Stockage SQL avec cache en memoire.
 */
public class PlayerStatsManager {

    private final PrisonPlugin plugin;
    private final Map<UUID, PlayerStatsData> playerStats = new ConcurrentHashMap<>();

    public PlayerStatsManager(@NotNull PrisonPlugin plugin) {
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
                CREATE TABLE IF NOT EXISTS prison_player_stats (
                    player_uuid CHAR(36) PRIMARY KEY,
                    player_name VARCHAR(32),
                    blocks_mined BIGINT DEFAULT 0,
                    total_money_earned DECIMAL(20,2) DEFAULT 0,
                    time_played BIGINT DEFAULT 0,
                    last_join_time BIGINT DEFAULT 0,
                    fortune_level INT DEFAULT 0,
                    efficiency_level INT DEFAULT 0,
                    auto_sell_level INT DEFAULT 0,
                    auto_sell_enabled BOOLEAN DEFAULT false
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """).join();
            plugin.log(Level.INFO, "Player stats table migration completed.");
        } catch (Exception e) {
            plugin.log(Level.SEVERE, "Failed to run stats migrations: " + e.getMessage());
        }
    }

    /**
     * Charge toutes les stats joueurs depuis SQL.
     */
    public void loadAll() {
        try {
            List<StatsRow> rows = getSql().queryList(
                "SELECT player_uuid, player_name, blocks_mined, total_money_earned, time_played, last_join_time, fortune_level, efficiency_level, auto_sell_level, auto_sell_enabled FROM prison_player_stats",
                rs -> {
                    try {
                        return new StatsRow(
                            rs.getString("player_uuid"),
                            rs.getString("player_name"),
                            rs.getLong("blocks_mined"),
                            rs.getBigDecimal("total_money_earned"),
                            rs.getLong("time_played"),
                            rs.getLong("last_join_time"),
                            rs.getInt("fortune_level"),
                            rs.getInt("efficiency_level"),
                            rs.getInt("auto_sell_level"),
                            rs.getBoolean("auto_sell_enabled")
                        );
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            ).join();

            for (StatsRow row : rows) {
                UUID uuid = UUID.fromString(row.playerUuid);
                PlayerStatsData data = new PlayerStatsData();
                data.playerName = row.playerName;
                data.blocksMined = row.blocksMined;
                data.totalMoneyEarned = row.totalMoneyEarned != null ? row.totalMoneyEarned : BigDecimal.ZERO;
                data.timePlayed = row.timePlayed;
                data.lastJoinTime = row.lastJoinTime;
                data.fortuneLevel = row.fortuneLevel;
                data.efficiencyLevel = row.efficiencyLevel;
                data.autoSellLevel = row.autoSellLevel;
                data.autoSellEnabled = row.autoSellEnabled;
                playerStats.put(uuid, data);
            }

            plugin.log(Level.INFO, "Loaded " + playerStats.size() + " player stats from SQL.");
        } catch (Exception e) {
            plugin.log(Level.SEVERE, "Failed to load player stats: " + e.getMessage());
        }
    }

    /**
     * Sauvegarde toutes les stats en batch vers SQL (shutdown).
     */
    public void saveAll() {
        try {
            String sql = """
                INSERT INTO prison_player_stats (player_uuid, player_name, blocks_mined, total_money_earned, time_played, last_join_time, fortune_level, efficiency_level, auto_sell_level, auto_sell_enabled)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    player_name = VALUES(player_name),
                    blocks_mined = VALUES(blocks_mined),
                    total_money_earned = VALUES(total_money_earned),
                    time_played = VALUES(time_played),
                    last_join_time = VALUES(last_join_time),
                    fortune_level = VALUES(fortune_level),
                    efficiency_level = VALUES(efficiency_level),
                    auto_sell_level = VALUES(auto_sell_level),
                    auto_sell_enabled = VALUES(auto_sell_enabled)
            """;

            List<Object[]> batchParams = new ArrayList<>();
            for (Map.Entry<UUID, PlayerStatsData> entry : playerStats.entrySet()) {
                PlayerStatsData d = entry.getValue();
                batchParams.add(new Object[]{
                    entry.getKey().toString(),
                    d.playerName,
                    d.blocksMined,
                    d.totalMoneyEarned,
                    d.timePlayed,
                    d.lastJoinTime,
                    d.fortuneLevel,
                    d.efficiencyLevel,
                    d.autoSellLevel,
                    d.autoSellEnabled
                });
            }

            if (!batchParams.isEmpty()) {
                getSql().executeBatch(sql, batchParams).join();
                plugin.log(Level.INFO, "Saved " + batchParams.size() + " player stats to SQL.");
            }
        } catch (Exception e) {
            plugin.log(Level.SEVERE, "Failed to save player stats: " + e.getMessage());
        }
    }

    /**
     * Persiste un joueur de maniere async (apres changement important).
     */
    private void persistAsync(@NotNull UUID uuid) {
        PlayerStatsData d = playerStats.get(uuid);
        if (d == null) return;
        try {
            getSql().execute("""
                INSERT INTO prison_player_stats (player_uuid, player_name, blocks_mined, total_money_earned, time_played, last_join_time, fortune_level, efficiency_level, auto_sell_level, auto_sell_enabled)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    player_name = VALUES(player_name),
                    blocks_mined = VALUES(blocks_mined),
                    total_money_earned = VALUES(total_money_earned),
                    time_played = VALUES(time_played),
                    last_join_time = VALUES(last_join_time),
                    fortune_level = VALUES(fortune_level),
                    efficiency_level = VALUES(efficiency_level),
                    auto_sell_level = VALUES(auto_sell_level),
                    auto_sell_enabled = VALUES(auto_sell_enabled)
            """, uuid.toString(), d.playerName, d.blocksMined, d.totalMoneyEarned, d.timePlayed,
               d.lastJoinTime, d.fortuneLevel, d.efficiencyLevel, d.autoSellLevel, d.autoSellEnabled);
        } catch (Exception e) {
            plugin.log(Level.WARNING, "Failed to persist stats for " + uuid + ": " + e.getMessage());
        }
    }

    // ===========================
    // Stats Access
    // ===========================

    @NotNull
    public PlayerStatsData getStats(@NotNull UUID uuid) {
        return playerStats.computeIfAbsent(uuid, k -> new PlayerStatsData());
    }

    @NotNull
    public Map<UUID, PlayerStatsData> getAllStats() {
        return Collections.unmodifiableMap(playerStats);
    }

    // ===========================
    // Blocks Mined
    // ===========================

    public void incrementBlocksMined(@NotNull UUID uuid) {
        getStats(uuid).blocksMined++;
    }

    public void addBlocksMined(@NotNull UUID uuid, int count) {
        getStats(uuid).blocksMined += count;
    }

    public long getBlocksMined(@NotNull UUID uuid) {
        return getStats(uuid).blocksMined;
    }

    // ===========================
    // Money Earned
    // ===========================

    public void addMoneyEarned(@NotNull UUID uuid, @NotNull BigDecimal amount) {
        PlayerStatsData stats = getStats(uuid);
        stats.totalMoneyEarned = stats.totalMoneyEarned.add(amount);
    }

    @NotNull
    public BigDecimal getTotalMoneyEarned(@NotNull UUID uuid) {
        return getStats(uuid).totalMoneyEarned;
    }

    // ===========================
    // Time Played
    // ===========================

    public void setLastJoinTime(@NotNull UUID uuid, long timestamp) {
        getStats(uuid).lastJoinTime = timestamp;
    }

    public void updateTimePlayed(@NotNull UUID uuid) {
        PlayerStatsData stats = getStats(uuid);
        if (stats.lastJoinTime > 0) {
            long sessionTime = System.currentTimeMillis() - stats.lastJoinTime;
            if (sessionTime > 0) {
                stats.timePlayed += sessionTime;
            }
            stats.lastJoinTime = 0;
        }
        persistAsync(uuid);
    }

    public long getTimePlayed(@NotNull UUID uuid) {
        return getStats(uuid).timePlayed;
    }

    // ===========================
    // Pickaxe Upgrades
    // ===========================

    public int getFortuneLevel(@NotNull UUID uuid) {
        return getStats(uuid).fortuneLevel;
    }

    public void setFortuneLevel(@NotNull UUID uuid, int level) {
        getStats(uuid).fortuneLevel = Math.max(0, Math.min(5, level));
        persistAsync(uuid);
    }

    public int getEfficiencyLevel(@NotNull UUID uuid) {
        return getStats(uuid).efficiencyLevel;
    }

    public void setEfficiencyLevel(@NotNull UUID uuid, int level) {
        getStats(uuid).efficiencyLevel = Math.max(0, Math.min(5, level));
        persistAsync(uuid);
    }

    public boolean hasAutoSell(@NotNull UUID uuid) {
        return getStats(uuid).autoSellLevel > 0;
    }

    public void setAutoSellLevel(@NotNull UUID uuid, int level) {
        getStats(uuid).autoSellLevel = Math.max(0, Math.min(1, level));
        persistAsync(uuid);
    }

    public boolean isAutoSellEnabled(@NotNull UUID uuid) {
        PlayerStatsData stats = getStats(uuid);
        return stats.autoSellLevel > 0 && stats.autoSellEnabled;
    }

    public boolean toggleAutoSell(@NotNull UUID uuid) {
        PlayerStatsData stats = getStats(uuid);
        if (stats.autoSellLevel <= 0) {
            return false;
        }
        stats.autoSellEnabled = !stats.autoSellEnabled;
        persistAsync(uuid);
        return stats.autoSellEnabled;
    }

    // ===========================
    // Player Name (pour leaderboard)
    // ===========================

    public void setPlayerName(@NotNull UUID uuid, @NotNull String name) {
        getStats(uuid).playerName = name;
    }

    @NotNull
    public String getPlayerName(@NotNull UUID uuid) {
        String name = getStats(uuid).playerName;
        return name != null ? name : "Unknown";
    }

    // ===========================
    // Data Classes
    // ===========================

    public static class PlayerStatsData {
        public String playerName;
        public long blocksMined = 0;
        public BigDecimal totalMoneyEarned = BigDecimal.ZERO;
        public long timePlayed = 0;
        public long lastJoinTime = 0;
        public int fortuneLevel = 0;
        public int efficiencyLevel = 0;
        public int autoSellLevel = 0;
        public boolean autoSellEnabled = false;
    }

    private record StatsRow(String playerUuid, String playerName, long blocksMined, BigDecimal totalMoneyEarned,
                            long timePlayed, long lastJoinTime, int fortuneLevel, int efficiencyLevel,
                            int autoSellLevel, boolean autoSellEnabled) {}
}
