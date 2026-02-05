package com.islandium.prison.listener;

import com.hypixel.hytale.event.EventRegistry;
import com.islandium.prison.PrisonPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * Classe de base pour les listeners Prison.
 */
public abstract class PrisonListener {

    protected final PrisonPlugin plugin;

    public PrisonListener(@NotNull PrisonPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Enregistre le listener.
     */
    public abstract void register(@NotNull EventRegistry registry);
}
