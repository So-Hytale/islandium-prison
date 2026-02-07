package com.islandium.prison.listener;

import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.islandium.prison.PrisonPlugin;
import com.islandium.prison.config.PrisonConfig;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Listener pour la connexion des joueurs Prison.
 */
public class PrisonJoinListener extends PrisonListener {

    public PrisonJoinListener(@NotNull PrisonPlugin plugin) {
        super(plugin);
    }

    @Override
    public void register(@NotNull EventRegistry registry) {
        registry.register(PlayerConnectEvent.class, this::onPlayerConnect);
    }

    private void onPlayerConnect(PlayerConnectEvent event) {
        UUID uuid = event.getPlayerRef().getUuid();
        String name = event.getPlayerRef().getUsername();
        PlayerRef playerRef = event.getPlayerRef();
        Player player = event.getPlayer();

        plugin.log(Level.INFO, "PlayerConnectEvent for " + name);

        // Initialize player stats: record join time and player name
        plugin.getStatsManager().setLastJoinTime(uuid, System.currentTimeMillis());
        plugin.getStatsManager().setPlayerName(uuid, name);

        // Use a Thread instead of CompletableFuture to ensure exceptions are logged
        new Thread(() -> {
            try {
                Thread.sleep(2000); // Wait 2 seconds for core to load player and world

                plugin.log(Level.INFO, "Delayed init starting for " + name);

                // Initialize player rank if not exists
                String rank = plugin.getRankManager().getPlayerRank(uuid);
                PrisonConfig.RankInfo rankInfo = plugin.getConfig().getRank(rank);

                plugin.log(Level.INFO, "Player " + name + " rank=" + rank + ", rankInfo=" + (rankInfo != null ? rankInfo.displayName : "NULL"));

                // Show Prison HUD
                if (player != null && playerRef != null) {
                    try {
                        var ref = player.getReference();
                        plugin.log(Level.INFO, "Player ref for " + name + ": " + ref + " valid=" + (ref != null ? ref.isValid() : "null"));

                        if (ref == null || !ref.isValid()) {
                            plugin.log(Level.WARNING, "Player reference invalid for " + name + ", trying direct showHud");
                            plugin.getUIManager().showHud(playerRef, player);
                            return;
                        }
                        var store = ref.getStore();
                        var world = store.getExternalData().getWorld();

                        plugin.log(Level.INFO, "Scheduling HUD on world thread for " + name);

                        CompletableFuture.runAsync(() -> {
                            try {
                                plugin.log(Level.INFO, "World thread: showing HUD for " + name);
                                plugin.getUIManager().showHud(playerRef, player);
                            } catch (Exception e) {
                                plugin.log(Level.WARNING, "Failed to show HUD for " + name + ": " + e.getMessage());
                                e.printStackTrace();
                            }
                        }, world);
                    } catch (Exception e) {
                        plugin.log(Level.WARNING, "Error getting world for " + name + ": " + e.getMessage());
                        e.printStackTrace();
                        // Fallback: try without world thread
                        try {
                            plugin.getUIManager().showHud(playerRef, player);
                        } catch (Exception e2) {
                            plugin.log(Level.WARNING, "Fallback HUD also failed for " + name + ": " + e2.getMessage());
                            e2.printStackTrace();
                        }
                    }
                } else {
                    plugin.log(Level.WARNING, "Player or PlayerRef is null for " + name);
                }

                // Send rank info to player (in chat as backup)
                if (rankInfo != null) {
                    plugin.getCore().getPlayerManager().getOnlinePlayer(uuid).ifPresent(islandiumPlayer -> {
                        int prestige = plugin.getRankManager().getPlayerPrestige(uuid);
                        double multiplier = plugin.getRankManager().getPlayerMultiplier(uuid);

                        islandiumPlayer.sendMessage("&8[&6Prison&8] &7Rang: &e" + rankInfo.displayName);
                        if (prestige > 0) {
                            islandiumPlayer.sendMessage("&8[&6Prison&8] &7Prestige: &d" + prestige);
                        }
                        islandiumPlayer.sendMessage("&8[&6Prison&8] &7Multiplicateur: &ax" + String.format("%.2f", multiplier));
                    });
                }
            } catch (Exception e) {
                plugin.log(Level.SEVERE, "CRITICAL ERROR in delayed player init for " + name + ": " + e.getMessage());
                e.printStackTrace();
            }
        }, "Prison-Join-" + name).start();
    }
}
