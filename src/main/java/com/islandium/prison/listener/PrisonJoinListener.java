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

        // Initialize player stats: record join time and player name
        plugin.getStatsManager().setLastJoinTime(uuid, System.currentTimeMillis());
        plugin.getStatsManager().setPlayerName(uuid, name);

        // Use a Thread to ensure exceptions are logged (CompletableFuture swallows them)
        new Thread(() -> {
            try {
                Thread.sleep(2000); // Wait for core to load player and world

                // Initialize player rank if not exists
                String rank = plugin.getRankManager().getPlayerRank(uuid);
                PrisonConfig.RankInfo rankInfo = plugin.getConfig().getRank(rank);

                plugin.log(Level.INFO, "Player " + name + " connected with rank " + rank);

                // Track player pour show/hide HUD automatique selon le monde
                if (player != null && playerRef != null) {
                    try {
                        var ref = player.getReference();
                        if (ref == null || !ref.isValid()) {
                            plugin.log(Level.WARNING, "Player reference invalid for " + name + ", tracking directly");
                            plugin.getUIManager().trackPlayer(playerRef, player);
                            return;
                        }
                        var store = ref.getStore();
                        var world = store.getExternalData().getWorld();
                        CompletableFuture.runAsync(() -> {
                            try {
                                plugin.getUIManager().trackPlayer(playerRef, player);
                            } catch (Exception e) {
                                plugin.log(Level.WARNING, "Failed to track HUD for " + name + ": " + e.getMessage());
                                e.printStackTrace();
                            }
                        }, world);
                    } catch (Exception e) {
                        plugin.log(Level.WARNING, "Error tracking HUD for " + name + ": " + e.getMessage());
                        e.printStackTrace();
                    }
                }

                // Send rank info to player (in chat)
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
                plugin.log(Level.SEVERE, "Error in player init for " + name + ": " + e.getMessage());
                e.printStackTrace();
            }
        }, "Prison-Join-" + name).start();
    }
}
