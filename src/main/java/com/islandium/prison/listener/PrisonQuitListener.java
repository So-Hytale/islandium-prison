package com.islandium.prison.listener;

import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.islandium.prison.PrisonPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.logging.Level;

/**
 * Listener pour la deconnexion des joueurs Prison.
 */
public class PrisonQuitListener extends PrisonListener {

    public PrisonQuitListener(@NotNull PrisonPlugin plugin) {
        super(plugin);
    }

    @Override
    public void register(@NotNull EventRegistry registry) {
        registry.register(PlayerDisconnectEvent.class, this::onPlayerDisconnect);
    }

    private void onPlayerDisconnect(PlayerDisconnectEvent event) {
        PlayerRef playerRef = event.getPlayerRef();
        if (playerRef == null) {
            return;
        }

        UUID uuid = playerRef.getUuid();

        // Update time played before cleanup
        plugin.getStatsManager().updateTimePlayed(uuid);

        // Nettoyer le HUD
        plugin.getUIManager().cleanupPlayer(uuid);

        plugin.log(Level.FINE, "Cleaned up Prison data for " + playerRef.getUsername());
    }
}
