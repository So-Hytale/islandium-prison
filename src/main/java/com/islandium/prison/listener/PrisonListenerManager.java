package com.islandium.prison.listener;

import com.hypixel.hytale.event.EventRegistry;
import com.islandium.prison.PrisonPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Gestionnaire des listeners Prison.
 */
public class PrisonListenerManager {

    private final PrisonPlugin plugin;
    private final List<PrisonListener> listeners = new ArrayList<>();

    public PrisonListenerManager(@NotNull PrisonPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Enregistre tous les listeners.
     */
    public void registerAll() {
        EventRegistry registry = plugin.getCore().getEventRegistry();

        // Block break listener (for mine tracking)
        register(new BlockBreakListener(plugin), registry);

        // Player join listener (for rank initialization and HUD)
        register(new PrisonJoinListener(plugin), registry);

        // Player quit listener (for cleanup)
        register(new PrisonQuitListener(plugin), registry);

        plugin.log(Level.INFO, "Registered " + listeners.size() + " prison listeners");
    }

    private void register(@NotNull PrisonListener listener, @NotNull EventRegistry registry) {
        listener.register(registry);
        listeners.add(listener);
    }
}
