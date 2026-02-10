package com.islandium.prison.service;

import com.islandium.prison.PrisonPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Gestionnaire des services Prison.
 */
public class PrisonServiceManager {

    private final PrisonPlugin plugin;
    private final ScheduledExecutorService scheduler;

    public PrisonServiceManager(@NotNull PrisonPlugin plugin) {
        this.plugin = plugin;
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "Prison-Service");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Initialise tous les services.
     */
    public void initialize() {
        // Schedule auto-save every 5 minutes
        scheduler.scheduleAtFixedRate(this::autoSave, 5, 5, TimeUnit.MINUTES);

        plugin.log(Level.INFO, "Prison services initialized");
    }

    /**
     * Arrête tous les services.
     */
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }

        // Shutdown mine manager (cancels reset tasks)
        plugin.getMineManager().shutdown();
    }

    /**
     * Sauvegarde automatique des données.
     */
    private void autoSave() {
        try {
            plugin.getMineManager().saveAll();
            plugin.getRankManager().saveAll();
            plugin.getStatsManager().saveAll();
            // cellManager.saveAll() est dans islandium-cells (CellAutoSaver)
            plugin.log(Level.FINE, "Auto-save completed");
        } catch (Exception e) {
            plugin.log(Level.WARNING, "Auto-save failed: " + e.getMessage());
        }
    }

    // Cell expiration check migre vers islandium-cells
}
