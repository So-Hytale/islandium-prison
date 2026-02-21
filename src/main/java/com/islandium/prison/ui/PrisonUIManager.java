package com.islandium.prison.ui;

import com.islandium.prison.PrisonPlugin;
import com.islandium.prison.ui.challengehud.ChallengeHud;
import com.islandium.prison.ui.prisonhud.PrisonHud;
import com.islandium.prison.ui.pages.ChallengeConfigPage;
import com.islandium.prison.ui.pages.MineManagerPage;
import com.islandium.prison.ui.pages.PrisonMenuPage;
import com.islandium.prison.ui.pages.SellConfigPage;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Gestionnaire des interfaces utilisateur du plugin Prison.
 * Utilise l'API MultipleHUD pour permettre plusieurs HUDs simultanés.
 */
public class PrisonUIManager {

    private static final String HUD_ID = "PrisonHud";
    private static final String CHALLENGE_HUD_ID = "ChallengeHud";

    private static final long REFRESH_INTERVAL_SECONDS = 1;

    private final PrisonPlugin plugin;
    private final Map<UUID, PrisonHud> activeHuds = new ConcurrentHashMap<>();
    private final Map<UUID, ChallengeHud> activeChallengeHuds = new ConcurrentHashMap<>();
    /** Joueurs connectes dont le HUD est masque (pas dans le monde prison). */
    private final Map<UUID, PlayerHudInfo> trackedPlayers = new ConcurrentHashMap<>();
    private ScheduledExecutorService refreshScheduler;

    private record PlayerHudInfo(PlayerRef playerRef, Player player) {}

    public PrisonUIManager(@NotNull PrisonPlugin plugin) {
        this.plugin = plugin;
        startRefreshTimer();
    }

    private void startRefreshTimer() {
        refreshScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Prison-HUD-Refresh");
            t.setDaemon(true);
            return t;
        });
        refreshScheduler.scheduleAtFixedRate(() -> {
            try {
                String requiredWorld = plugin.getConfig().getWorldName();

                // Refresh HUDs actifs et masquer si le joueur a quitte le monde prison
                for (var entry : activeHuds.entrySet()) {
                    try {
                        UUID uuid = entry.getKey();
                        PrisonHud hud = entry.getValue();
                        PlayerHudInfo info = trackedPlayers.get(uuid);
                        if (info != null && !isInWorld(info.player(), requiredWorld)) {
                            // hideHud doit s'executer sur le world thread
                            runOnWorldThread(info.player(), () -> hideHud(info.player()));
                        } else {
                            hud.refreshData();
                        }
                    } catch (Exception e) {
                        // Ignore individual refresh errors
                    }
                }

                // Refresh Challenge HUDs actifs
                for (var entry : activeChallengeHuds.entrySet()) {
                    try {
                        UUID uuid = entry.getKey();
                        ChallengeHud chHud = entry.getValue();
                        PlayerHudInfo info = trackedPlayers.get(uuid);
                        if (info != null && !isInWorld(info.player(), requiredWorld)) {
                            runOnWorldThread(info.player(), () -> hideChallengeHud(info.player()));
                        } else {
                            chHud.refreshData();
                        }
                    } catch (Exception e) {
                        // Ignore
                    }
                }

                // Verifier les joueurs trackes sans HUD actif (viennent d'arriver dans le monde prison)
                for (var entry : trackedPlayers.entrySet()) {
                    try {
                        UUID uuid = entry.getKey();
                        PlayerHudInfo info = entry.getValue();
                        if (isInWorld(info.player(), requiredWorld)) {
                            if (!activeHuds.containsKey(uuid)) {
                                // showHud doit s'executer sur le world thread
                                runOnWorldThread(info.player(), () -> showHud(info.playerRef(), info.player()));
                            }
                            if (!activeChallengeHuds.containsKey(uuid)) {
                                runOnWorldThread(info.player(), () -> showChallengeHud(info.playerRef(), info.player()));
                            }
                        }
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            } catch (Exception e) {
                // Ignore
            }
        }, REFRESH_INTERVAL_SECONDS, REFRESH_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public void shutdown() {
        if (refreshScheduler != null) {
            refreshScheduler.shutdown();
        }
    }

    // === HUD Management (via MultipleHUD API) ===

    /**
     * Execute une action sur le world thread du joueur.
     * Necessaire car les operations MultipleHUD doivent s'executer sur le world thread.
     */
    private void runOnWorldThread(@NotNull Player player, @NotNull Runnable action) {
        try {
            var ref = player.getReference();
            if (ref == null || !ref.isValid()) return;
            var store = ref.getStore();
            var world = store.getExternalData().getWorld();
            if (world == null) return;
            CompletableFuture.runAsync(action, world);
        } catch (Exception e) {
            plugin.log(Level.WARNING, "Failed to run on world thread: " + e.getMessage());
        }
    }

    /**
     * Verifie si un joueur est dans le monde specifie.
     */
    private boolean isInWorld(@NotNull Player player, @NotNull String worldName) {
        try {
            var ref = player.getReference();
            if (ref == null || !ref.isValid()) return false;
            var store = ref.getStore();
            var world = store.getExternalData().getWorld();
            return world != null && worldName.equals(world.getName());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Enregistre un joueur pour le suivi de monde (show/hide automatique).
     * Affiche le HUD seulement si le joueur est dans le monde prison.
     */
    public void trackPlayer(@NotNull PlayerRef playerRef, @NotNull Player player) {
        UUID uuid = playerRef.getUuid();
        trackedPlayers.put(uuid, new PlayerHudInfo(playerRef, player));

        String requiredWorld = plugin.getConfig().getWorldName();
        if (isInWorld(player, requiredWorld)) {
            showHud(playerRef, player);
            showChallengeHud(playerRef, player);
        }
    }

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
            PrisonHud hud = new PrisonHud(playerRef, player, plugin);
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

            // Retirer via MultipleHUD
            invokeMultipleHUD("hideCustomHud", player, playerRef, HUD_ID);
        } catch (Exception e) {
            plugin.log(Level.WARNING, "Failed to hide Prison HUD: " + e.getMessage());
        }
    }

    // === Challenge HUD Management ===

    /**
     * Affiche le Challenge HUD pour un joueur (defis suivis).
     * Ne s'affiche que si le joueur a des challenges epingles.
     */
    public void showChallengeHud(@NotNull PlayerRef playerRef, @NotNull Player player) {
        try {
            UUID uuid = playerRef.getUuid();

            if (plugin.getChallengeManager().getPinnedChallenges(uuid).isEmpty()) {
                return;
            }

            ChallengeHud hud = new ChallengeHud(playerRef, player, plugin);
            activeChallengeHuds.put(uuid, hud);
            invokeMultipleHUD("setCustomHud", player, playerRef, CHALLENGE_HUD_ID, hud);
        } catch (Exception e) {
            plugin.log(Level.WARNING, "Failed to show Challenge HUD: " + e.getMessage());
        }
    }

    /**
     * Cache le Challenge HUD d'un joueur.
     */
    public void hideChallengeHud(@NotNull Player player) {
        try {
            var ref = player.getReference();
            if (ref == null || !ref.isValid()) return;

            var store = ref.getStore();
            var playerRef = store.getComponent(ref, PlayerRef.getComponentType());
            if (playerRef == null) return;

            UUID uuid = playerRef.getUuid();
            activeChallengeHuds.remove(uuid);
            invokeMultipleHUD("hideCustomHud", player, playerRef, CHALLENGE_HUD_ID);
        } catch (Exception e) {
            plugin.log(Level.WARNING, "Failed to hide Challenge HUD: " + e.getMessage());
        }
    }

    /**
     * Rafraichit le Challenge HUD d'un joueur par UUID.
     * Appele apres un toggle pin pour forcer le refresh immediat.
     * Detruit et recree le HUD pour recalculer la taille (Height dynamique).
     */
    public void refreshChallengeHud(@NotNull UUID uuid) {
        PlayerHudInfo info = trackedPlayers.get(uuid);
        if (info == null) return;

        String requiredWorld = plugin.getConfig().getWorldName();
        if (!isInWorld(info.player(), requiredWorld)) return;

        runOnWorldThread(info.player(), () -> {
            // Toujours detruire l'ancien HUD
            if (activeChallengeHuds.containsKey(uuid)) {
                hideChallengeHud(info.player());
            }
            // Recreer seulement si le joueur a encore des pins
            if (!plugin.getChallengeManager().getPinnedChallenges(uuid).isEmpty()) {
                showChallengeHud(info.playerRef(), info.player());
            }
        });
    }

    /**
     * Rafraichit le HUD Prison pour un joueur.
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
     */
    public void refreshHud(@NotNull UUID uuid) {
        // Pas possible sans le Player
    }

    /**
     * Nettoie le HUD d'un joueur (deconnexion).
     */
    public void cleanupPlayer(@NotNull UUID uuid) {
        activeHuds.remove(uuid);
        activeChallengeHuds.remove(uuid);
        trackedPlayers.remove(uuid);
    }

    // === Page Management ===

    /**
     * Invoque une méthode sur MultipleHUD.getInstance() par reflection.
     */
    private void invokeMultipleHUD(String methodName, Object... args) {
        try {
            Class<?> mhudClass = Class.forName("com.buuz135.mhud.MultipleHUD");
            Method getInstance = mhudClass.getMethod("getInstance");
            Object instance = getInstance.invoke(null);

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
     * Ouvre la page de configuration du Sell Shop pour un admin.
     */
    public void openSellConfig(@NotNull Player player) {
        try {
            var ref = player.getReference();
            if (ref == null || !ref.isValid()) {
                plugin.log(Level.WARNING, "Cannot open sell config: Reference is null or invalid");
                return;
            }

            var store = ref.getStore();
            var world = store.getExternalData().getWorld();

            CompletableFuture.runAsync(() -> {
                var playerRef = store.getComponent(ref, PlayerRef.getComponentType());
                if (playerRef == null) {
                    plugin.log(Level.WARNING, "Cannot open sell config: PlayerRef not found");
                    return;
                }

                SellConfigPage page = new SellConfigPage(playerRef, plugin);
                player.getPageManager().openCustomPage(ref, store, page);
            }, world);

        } catch (Exception e) {
            plugin.log(Level.SEVERE, "Failed to open sell config: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Ouvre la page de configuration des challenges pour un admin.
     */
    public void openChallengeConfig(@NotNull Player player) {
        try {
            var ref = player.getReference();
            if (ref == null || !ref.isValid()) {
                plugin.log(Level.WARNING, "Cannot open challenge config: Reference is null or invalid");
                return;
            }

            var store = ref.getStore();
            var world = store.getExternalData().getWorld();

            CompletableFuture.runAsync(() -> {
                var playerRef = store.getComponent(ref, PlayerRef.getComponentType());
                if (playerRef == null) {
                    plugin.log(Level.WARNING, "Cannot open challenge config: PlayerRef not found");
                    return;
                }

                ChallengeConfigPage page = new ChallengeConfigPage(playerRef, plugin);
                player.getPageManager().openCustomPage(ref, store, page);
            }, world);

        } catch (Exception e) {
            plugin.log(Level.SEVERE, "Failed to open challenge config: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Ouvre la page des challenges d'un autre joueur (mode admin).
     */
    public void openChallengesForPlayer(@NotNull Player admin, @NotNull UUID targetUuid, @NotNull String targetName) {
        try {
            var ref = admin.getReference();
            if (ref == null || !ref.isValid()) {
                plugin.log(Level.WARNING, "Cannot open challenges for player: Reference is null or invalid");
                return;
            }

            var store = ref.getStore();
            var world = store.getExternalData().getWorld();

            CompletableFuture.runAsync(() -> {
                var playerRef = store.getComponent(ref, PlayerRef.getComponentType());
                if (playerRef == null) {
                    plugin.log(Level.WARNING, "Cannot open challenges for player: PlayerRef not found");
                    return;
                }

                PrisonMenuPage page = new PrisonMenuPage(playerRef, plugin, targetUuid, targetName);
                admin.getPageManager().openCustomPage(ref, store, page);
            }, world);

        } catch (Exception e) {
            plugin.log(Level.SEVERE, "Failed to open challenges for player: " + e.getMessage());
            e.printStackTrace();
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
