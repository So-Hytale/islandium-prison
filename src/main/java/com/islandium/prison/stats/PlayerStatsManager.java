package com.islandium.prison.stats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
 * Gestionnaire des statistiques joueurs Prison.
 * Stocke les stats de minage, argent gagné, temps joué, et niveaux d'upgrades.
 */
public class PlayerStatsManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final PrisonPlugin plugin;
    private final Path statsFile;

    private final Map<UUID, PlayerStatsData> playerStats = new ConcurrentHashMap<>();

    public PlayerStatsManager(@NotNull PrisonPlugin plugin) {
        this.plugin = plugin;
        this.statsFile = plugin.getDataFolder().toPath().resolve("player_stats.json");
    }

    // ===========================
    // Persistence (Load / Save)
    // ===========================

    /**
     * Charge toutes les stats joueurs depuis le fichier JSON.
     */
    public void loadAll() {
        try {
            if (Files.exists(statsFile)) {
                String content = Files.readString(statsFile);
                StatsFileData data = GSON.fromJson(content, StatsFileData.class);

                if (data != null && data.players != null) {
                    for (Map.Entry<String, PlayerStatsData> entry : data.players.entrySet()) {
                        playerStats.put(UUID.fromString(entry.getKey()), entry.getValue());
                    }
                }

                plugin.log(Level.INFO, "Loaded " + playerStats.size() + " player stats");
            }
        } catch (Exception e) {
            plugin.log(Level.SEVERE, "Failed to load player stats: " + e.getMessage());
        }
    }

    /**
     * Sauvegarde toutes les stats joueurs dans le fichier JSON.
     */
    public void saveAll() {
        try {
            StatsFileData data = new StatsFileData();
            data.players = new HashMap<>();

            for (Map.Entry<UUID, PlayerStatsData> entry : playerStats.entrySet()) {
                data.players.put(entry.getKey().toString(), entry.getValue());
            }

            Files.createDirectories(statsFile.getParent());
            Files.writeString(statsFile, GSON.toJson(data));
        } catch (IOException e) {
            plugin.log(Level.SEVERE, "Failed to save player stats: " + e.getMessage());
        }
    }

    // ===========================
    // Stats Access
    // ===========================

    /**
     * Obtient les stats d'un joueur. Crée une entrée vide si elle n'existe pas.
     */
    @NotNull
    public PlayerStatsData getStats(@NotNull UUID uuid) {
        return playerStats.computeIfAbsent(uuid, k -> new PlayerStatsData());
    }

    /**
     * Retourne toutes les stats de tous les joueurs.
     */
    @NotNull
    public Map<UUID, PlayerStatsData> getAllStats() {
        return Collections.unmodifiableMap(playerStats);
    }

    // ===========================
    // Blocks Mined
    // ===========================

    /**
     * Incrémente le compteur de blocs minés pour un joueur.
     */
    public void incrementBlocksMined(@NotNull UUID uuid) {
        getStats(uuid).blocksMined++;
    }

    /**
     * Incrémente le compteur de blocs minés par un montant donné.
     */
    public void addBlocksMined(@NotNull UUID uuid, int count) {
        getStats(uuid).blocksMined += count;
    }

    /**
     * Obtient le nombre total de blocs minés par un joueur.
     */
    public long getBlocksMined(@NotNull UUID uuid) {
        return getStats(uuid).blocksMined;
    }

    // ===========================
    // Money Earned
    // ===========================

    /**
     * Ajoute un montant gagné au total du joueur.
     */
    public void addMoneyEarned(@NotNull UUID uuid, @NotNull BigDecimal amount) {
        PlayerStatsData stats = getStats(uuid);
        stats.totalMoneyEarned = stats.totalMoneyEarned.add(amount);
    }

    /**
     * Obtient le total d'argent gagné par un joueur.
     */
    @NotNull
    public BigDecimal getTotalMoneyEarned(@NotNull UUID uuid) {
        return getStats(uuid).totalMoneyEarned;
    }

    // ===========================
    // Time Played
    // ===========================

    /**
     * Enregistre le timestamp de connexion d'un joueur.
     */
    public void setLastJoinTime(@NotNull UUID uuid, long timestamp) {
        getStats(uuid).lastJoinTime = timestamp;
    }

    /**
     * Met à jour le temps joué lors de la déconnexion.
     * Calcule le delta depuis lastJoinTime et l'ajoute au total.
     */
    public void updateTimePlayed(@NotNull UUID uuid) {
        PlayerStatsData stats = getStats(uuid);
        if (stats.lastJoinTime > 0) {
            long sessionTime = System.currentTimeMillis() - stats.lastJoinTime;
            if (sessionTime > 0) {
                stats.timePlayed += sessionTime;
            }
            stats.lastJoinTime = 0; // Reset
        }
    }

    /**
     * Obtient le temps total joué en millisecondes.
     */
    public long getTimePlayed(@NotNull UUID uuid) {
        return getStats(uuid).timePlayed;
    }

    // ===========================
    // Pickaxe Upgrades
    // ===========================

    /**
     * Obtient le niveau de fortune (0-5).
     */
    public int getFortuneLevel(@NotNull UUID uuid) {
        return getStats(uuid).fortuneLevel;
    }

    /**
     * Définit le niveau de fortune.
     */
    public void setFortuneLevel(@NotNull UUID uuid, int level) {
        getStats(uuid).fortuneLevel = Math.max(0, Math.min(5, level));
    }

    /**
     * Obtient le niveau d'efficacité (0-5).
     */
    public int getEfficiencyLevel(@NotNull UUID uuid) {
        return getStats(uuid).efficiencyLevel;
    }

    /**
     * Définit le niveau d'efficacité.
     */
    public void setEfficiencyLevel(@NotNull UUID uuid, int level) {
        getStats(uuid).efficiencyLevel = Math.max(0, Math.min(5, level));
    }

    /**
     * Vérifie si le joueur a acheté l'auto-sell.
     */
    public boolean hasAutoSell(@NotNull UUID uuid) {
        return getStats(uuid).autoSellLevel > 0;
    }

    /**
     * Définit le niveau auto-sell (0 = pas acheté, 1 = acheté).
     */
    public void setAutoSellLevel(@NotNull UUID uuid, int level) {
        getStats(uuid).autoSellLevel = Math.max(0, Math.min(1, level));
    }

    /**
     * Vérifie si l'auto-sell est actuellement activé.
     */
    public boolean isAutoSellEnabled(@NotNull UUID uuid) {
        PlayerStatsData stats = getStats(uuid);
        return stats.autoSellLevel > 0 && stats.autoSellEnabled;
    }

    /**
     * Toggle l'état on/off de l'auto-sell.
     * @return le nouvel état (true = activé)
     */
    public boolean toggleAutoSell(@NotNull UUID uuid) {
        PlayerStatsData stats = getStats(uuid);
        if (stats.autoSellLevel <= 0) {
            return false; // Pas encore acheté
        }
        stats.autoSellEnabled = !stats.autoSellEnabled;
        return stats.autoSellEnabled;
    }

    // ===========================
    // Player Name (pour leaderboard)
    // ===========================

    /**
     * Met à jour le nom du joueur dans les stats.
     */
    public void setPlayerName(@NotNull UUID uuid, @NotNull String name) {
        getStats(uuid).playerName = name;
    }

    /**
     * Obtient le nom du joueur stocké dans les stats.
     */
    @NotNull
    public String getPlayerName(@NotNull UUID uuid) {
        String name = getStats(uuid).playerName;
        return name != null ? name : "Unknown";
    }

    // ===========================
    // Data Classes
    // ===========================

    /**
     * Structure du fichier JSON.
     */
    private static class StatsFileData {
        Map<String, PlayerStatsData> players;
    }

    /**
     * Données de stats pour un joueur.
     */
    public static class PlayerStatsData {
        public String playerName;
        public long blocksMined = 0;
        public BigDecimal totalMoneyEarned = BigDecimal.ZERO;
        public long timePlayed = 0;       // en millisecondes
        public long lastJoinTime = 0;     // timestamp de connexion

        // Pickaxe upgrades
        public int fortuneLevel = 0;      // 0-5
        public int efficiencyLevel = 0;   // 0-5
        public int autoSellLevel = 0;     // 0 = pas acheté, 1 = acheté
        public boolean autoSellEnabled = false; // toggle on/off
    }
}
