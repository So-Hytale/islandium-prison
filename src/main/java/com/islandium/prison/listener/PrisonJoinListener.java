package com.islandium.prison.listener;

import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.islandium.core.api.player.IslandiumPlayer;
import com.islandium.prison.PrisonPlugin;
import com.islandium.prison.config.PrisonConfig;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
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

        // Wait a bit for core plugin to load player data
        plugin.getCore().runAsync(() -> {
            try {
                Thread.sleep(1000); // Wait 1 second for core to load player
            } catch (InterruptedException ignored) {}

            // Initialize player rank if not exists
            String rank = plugin.getRankManager().getPlayerRank(uuid);
            PrisonConfig.RankInfo rankInfo = plugin.getConfig().getRank(rank);

            if (rankInfo != null) {
                plugin.log(Level.FINE, "Player " + name + " connected with rank " + rank);

                // Show Prison HUD
                if (player != null && playerRef != null) {
                    try {
                        plugin.getUIManager().showHud(playerRef, player);
                    } catch (Exception e) {
                        plugin.log(Level.WARNING, "Failed to show HUD for " + name + ": " + e.getMessage());
                    }
                }

                // Send rank info to player (in chat as backup)
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
        });
    }
}
