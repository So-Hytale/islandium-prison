package com.islandium.prison.ui;

import com.islandium.prison.PrisonPlugin;
import com.islandium.prison.ui.hud.PrisonHud;
import com.islandium.prison.ui.pages.MineManagerPage;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Gestionnaire des interfaces utilisateur du plugin Prison.
 * Utilise l'API MultipleHUD pour permettre plusieurs HUDs simultanés.
 */
public class PrisonUIManager {

    private static final String HUD_ID = "PrisonHud";

    private final PrisonPlugin plugin;
    private final Map<UUID, PrisonHud> activeHuds = new ConcurrentHashMap<>();

    public PrisonUIManager(@NotNull PrisonPlugin plugin) {
        this.plugin = plugin;
    }

    // === HUD Management (via MultipleHUD API) ===

    /**
     * Affiche le HUD Prison pour un joueur.
     */
    public void showHud(@NotNull Player player) {
        try {
            var ref = player.getReference();
            if (ref == null || !ref.isValid()) return;

            var store = ref.getStore();
            var playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) return;

            showHud(playerRef, player);
        } catch (Exception e) {
            plugin.log(Level.WARNING, "Failed to show Prison HUD: " + e.getMessage());
        }
    }

    /**
     * Affiche le HUD Prison pour un joueur via PlayerRef.
     * Utilise MultipleHUD pour permettre la coexistence avec d'autres HUDs.
     */
    public void showHud(@NotNull PlayerRef playerRef, @NotNull Player player) {
        try {
            UUID uuid = playerRef.getUuid();

            // Creer le HUD
            PrisonHud hud = new PrisonHud(playerRef, plugin);
            activeHuds.put(uuid, hud);

            // Enregistrer via MultipleHUD (reflection pour compatibilité de version)
            invokeMultipleHUD("setCustomHud", player, playerRef, HUD_ID, hud);

            plugin.log(Level.INFO, "Prison HUD shown for " + playerRef.getUsername() + " (via MultipleHUD)");
        } catch (Exception e) {
            plugin.log(Level.WARNING, "Failed to show Prison HUD: " + e.getMessage());
        }
    }

    /**
     * Cache le HUD Prison pour un joueur.
     */
    public void hideHud(@NotNull Player player) {
        try {
            var ref = player.getReference();
            if (ref == null || !ref.isValid()) return;

            var store = ref.getStore();
            var playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) return;

            UUID uuid = playerRef.getUuid();
            activeHuds.remove(uuid);

            // Retirer via MultipleHUD (reflection pour compatibilité de version)
            invokeMultipleHUD("hideCustomHud", player, playerRef, HUD_ID);
        } catch (Exception e) {
            plugin.log(Level.WARNING, "Failed to hide Prison HUD: " + e.getMessage());
        }
    }

    /**
     * Rafraichit le HUD Prison pour un joueur.
     * Recree le HUD via MultipleHUD.
     */
    public void refreshHud(@NotNull Player player) {
        try {
            var ref = player.getReference();
            if (ref == null || !ref.isValid()) return;

            var store = ref.getStore();
            var playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) return;

            UUID uuid = playerRef.getUuid();
            if (activeHuds.containsKey(uuid)) {
                showHud(playerRef, player);
            }
        } catch (Exception e) {
            plugin.log(Level.WARNING, "Failed to refresh Prison HUD: " + e.getMessage());
        }
    }

    /**
     * Rafraichit le HUD pour un joueur par UUID.
     * Note: Necessite de retrouver le Player, donc moins efficace.
     */
    public void refreshHud(@NotNull UUID uuid) {
        // Le refresh par UUID n'est pas possible sans le Player
        // Car MultipleHUD.setCustomHud necessite le Player et le PlayerRef
    }

    /**
     * Nettoie le HUD d'un joueur (deconnexion).
     */
    public void cleanupPlayer(@NotNull UUID uuid) {
        activeHuds.remove(uuid);
    }

    // === Page Management ===

    /**
     * Invoque une méthode sur MultipleHUD.getInstance() par reflection.
     * Permet d'éviter la dépendance directe au compile-time sur le JAR MultipleHUD
     * qui peut être compilé pour une version de Java différente.
     */
    private void invokeMultipleHUD(String methodName, Object... args) {
        try {
            Class<?> mhudClass = Class.forName("com.buuz135.mhud.MultipleHUD");
            Method getInstance = mhudClass.getMethod("getInstance");
            Object instance = getInstance.invoke(null);

            Class<?>[] paramTypes = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                paramTypes[i] = args[i].getClass();
            }

            // Rechercher la méthode par nom et nombre de paramètres
            for (Method m : mhudClass.getMethods()) {
                if (m.getName().equals(methodName) && m.getParameterCount() == args.length) {
                    m.invoke(instance, args);
                    return;
                }
            }

            plugin.log(Level.WARNING, "MultipleHUD method not found: " + methodName);
        } catch (ClassNotFoundException e) {
            plugin.log(Level.WARNING, "MultipleHUD not available: " + e.getMessage());
        } catch (Exception e) {
            plugin.log(Level.WARNING, "Failed to invoke MultipleHUD." + methodName + ": " + e.getMessage());
        }
    }

    /**
     * Ouvre la page de gestion des mines pour un joueur.
     */
    public void openMineManager(@NotNull Player player) {
        try {
            var ref = player.getReference();
            if (ref == null || !ref.isValid()) {
                plugin.log(Level.WARNING, "Cannot open mine manager: Reference is null or invalid");
                return;
            }

            var store = ref.getStore();
            var world = store.getExternalData().getWorld();

            CompletableFuture.runAsync(() -> {
                var playerRef = store.getComponent(ref, PlayerRef.getComponentType());
                if (playerRef == null) {
                    plugin.log(Level.WARNING, "Cannot open mine manager: PlayerRef not found");
                    return;
                }

                MineManagerPage page = new MineManagerPage(playerRef, plugin);
                player.getPageManager().openCustomPage(ref, store, page);
            }, world);

        } catch (Exception e) {
            plugin.log(Level.SEVERE, "Failed to open mine manager: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
