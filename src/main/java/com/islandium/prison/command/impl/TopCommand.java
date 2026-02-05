package com.islandium.prison.command.impl;

import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.islandium.core.api.IslandiumAPI;
import com.islandium.core.api.economy.EconomyService;
import com.islandium.prison.PrisonPlugin;
import com.islandium.prison.command.base.PrisonCommand;
import com.islandium.prison.economy.SellService;
import com.islandium.prison.stats.PlayerStatsManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Commande /top - Affiche les leaderboards.
 *
 * Usage:
 *   /top          - Top 10 richesse (balance)
 *   /top balance  - Top 10 richesse
 *   /top blocks   - Top 10 blocs minés
 *   /top prestige - Top 10 prestige
 */
public class TopCommand extends PrisonCommand {

    private static final int TOP_SIZE = 10;
    private static final long CACHE_DURATION_MS = 60_000; // 60 secondes

    // Cache simple
    private final Map<String, CachedLeaderboard> cache = new ConcurrentHashMap<>();

    private final OptionalArg<String> typeArg;

    public TopCommand(@NotNull PrisonPlugin plugin) {
        super(plugin, "top", "Affiche les classements");
        addAliases("baltop", "classement", "leaderboard");
        requirePermission("prison.top");
        typeArg = withOptionalArg("type", "Type de classement (balance, blocks, prestige)", ArgTypes.STRING);
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        if (!hasPermission(ctx, "prison.top")) {
            sendMessage(ctx, "&cTu n'as pas la permission!");
            return complete();
        }

        String type = ctx.provided(typeArg) ? ctx.get(typeArg).toLowerCase() : "balance";

        switch (type) {
            case "balance":
            case "money":
            case "bal":
                showBalanceTop(ctx);
                break;

            case "blocks":
            case "blocs":
            case "mined":
                showBlocksTop(ctx);
                break;

            case "prestige":
            case "prestiges":
                showPrestigeTop(ctx);
                break;

            default:
                sendMessage(ctx, "&cUsage: /top [balance|blocks|prestige]");
                break;
        }

        return complete();
    }

    /**
     * Affiche le top richesse.
     */
    private void showBalanceTop(CommandContext ctx) {
        String prefix = plugin.getConfig().getMessage("prefix");

        // Vérifier le cache
        CachedLeaderboard cached = cache.get("balance");
        if (cached != null && !cached.isExpired()) {
            displayLeaderboard(ctx, "&6&l=== Top Richesse ===", cached.entries);
            return;
        }

        // Charger depuis EconomyService
        EconomyService eco = getEconomyService();
        if (eco == null) {
            sendMessage(ctx, prefix + "&cService économique non disponible!");
            return;
        }

        try {
            List<UUID> topUuids = eco.getTopPlayers(TOP_SIZE).join();
            List<LeaderboardEntry> entries = new ArrayList<>();

            for (UUID uuid : topUuids) {
                BigDecimal balance = eco.getBalance(uuid).join();
                String name = plugin.getStatsManager().getPlayerName(uuid);
                entries.add(new LeaderboardEntry(name, SellService.formatMoney(balance)));
            }

            // Mettre en cache
            cache.put("balance", new CachedLeaderboard(entries));

            displayLeaderboard(ctx, "&6&l=== Top Richesse ===", entries);
        } catch (Exception e) {
            sendMessage(ctx, prefix + "&cErreur lors du chargement du classement!");
            plugin.log(Level.WARNING, "Failed to load balance leaderboard: " + e.getMessage());
        }
    }

    /**
     * Affiche le top blocs minés.
     */
    private void showBlocksTop(CommandContext ctx) {
        // Vérifier le cache
        CachedLeaderboard cached = cache.get("blocks");
        if (cached != null && !cached.isExpired()) {
            displayLeaderboard(ctx, "&b&l=== Top Blocs Minés ===", cached.entries);
            return;
        }

        Map<UUID, PlayerStatsManager.PlayerStatsData> allStats = plugin.getStatsManager().getAllStats();

        List<LeaderboardEntry> entries = allStats.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().blocksMined, a.getValue().blocksMined))
                .limit(TOP_SIZE)
                .map(entry -> {
                    String name = entry.getValue().playerName != null ? entry.getValue().playerName : "Unknown";
                    String value = formatNumber(entry.getValue().blocksMined) + " blocs";
                    return new LeaderboardEntry(name, value);
                })
                .collect(Collectors.toList());

        // Mettre en cache
        cache.put("blocks", new CachedLeaderboard(entries));

        displayLeaderboard(ctx, "&b&l=== Top Blocs Minés ===", entries);
    }

    /**
     * Affiche le top prestige.
     */
    private void showPrestigeTop(CommandContext ctx) {
        // Vérifier le cache
        CachedLeaderboard cached = cache.get("prestige");
        if (cached != null && !cached.isExpired()) {
            displayLeaderboard(ctx, "&d&l=== Top Prestige ===", cached.entries);
            return;
        }

        Map<UUID, PlayerStatsManager.PlayerStatsData> allStats = plugin.getStatsManager().getAllStats();
        List<LeaderboardEntry> entries = new ArrayList<>();

        for (Map.Entry<UUID, PlayerStatsManager.PlayerStatsData> entry : allStats.entrySet()) {
            UUID uuid = entry.getKey();
            int prestige = plugin.getRankManager().getPlayerPrestige(uuid);
            String rank = plugin.getRankManager().getPlayerRank(uuid);
            String name = entry.getValue().playerName != null ? entry.getValue().playerName : "Unknown";

            if (prestige > 0 || !rank.equalsIgnoreCase("A")) {
                entries.add(new LeaderboardEntry(name, "P" + prestige + " Rang " + rank));
            }
        }

        // Trier par prestige décroissant, puis par rang décroissant
        entries.sort((a, b) -> {
            // On extrait les valeurs du display pour le tri
            // Ceci est simplifié - on pourrait stocker les vrais valeurs
            return b.value.compareTo(a.value);
        });

        if (entries.size() > TOP_SIZE) {
            entries = entries.subList(0, TOP_SIZE);
        }

        // Mettre en cache
        cache.put("prestige", new CachedLeaderboard(entries));

        displayLeaderboard(ctx, "&d&l=== Top Prestige ===", entries);
    }

    /**
     * Affiche un leaderboard formaté.
     */
    private void displayLeaderboard(CommandContext ctx, String title, List<LeaderboardEntry> entries) {
        String prefix = plugin.getConfig().getMessage("prefix");

        sendMessage(ctx, prefix + title);
        sendMessage(ctx, "");

        if (entries.isEmpty()) {
            sendMessage(ctx, "  &7Aucune donnée disponible.");
        } else {
            for (int i = 0; i < entries.size(); i++) {
                LeaderboardEntry entry = entries.get(i);
                String rankColor;
                switch (i) {
                    case 0:
                        rankColor = "&6"; // Or
                        break;
                    case 1:
                        rankColor = "&f"; // Argent
                        break;
                    case 2:
                        rankColor = "&c"; // Bronze
                        break;
                    default:
                        rankColor = "&7";
                        break;
                }
                sendMessage(ctx, "  " + rankColor + "#" + (i + 1) + " &e" + entry.name + " &8- &a" + entry.value);
            }
        }

        sendMessage(ctx, "");
        sendMessage(ctx, "&8Classements actualisés toutes les 60s | /top [balance|blocks|prestige]");
    }

    /**
     * Formate un grand nombre pour l'affichage.
     */
    private String formatNumber(long number) {
        if (number >= 1_000_000_000) {
            return String.format("%.2fB", number / 1_000_000_000.0);
        } else if (number >= 1_000_000) {
            return String.format("%.2fM", number / 1_000_000.0);
        } else if (number >= 1_000) {
            return String.format("%.1fK", number / 1_000.0);
        }
        return String.valueOf(number);
    }

    @Nullable
    private EconomyService getEconomyService() {
        IslandiumAPI api = IslandiumAPI.get();
        return api != null ? api.getEconomyService() : null;
    }

    // === Inner Classes ===

    private static class LeaderboardEntry {
        final String name;
        final String value;

        LeaderboardEntry(String name, String value) {
            this.name = name;
            this.value = value;
        }
    }

    private static class CachedLeaderboard {
        final List<LeaderboardEntry> entries;
        final long timestamp;

        CachedLeaderboard(List<LeaderboardEntry> entries) {
            this.entries = entries;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > CACHE_DURATION_MS;
        }
    }
}
