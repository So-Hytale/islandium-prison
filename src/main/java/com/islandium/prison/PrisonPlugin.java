package com.islandium.prison;

import com.islandium.core.IslandiumPlugin;
import com.islandium.core.ui.IslandiumUIRegistry;
import com.islandium.prison.command.PrisonCommandManager;
import com.islandium.prison.config.PrisonConfig;
import com.islandium.prison.economy.SellService;
import com.islandium.prison.listener.PrisonListenerManager;
import com.islandium.prison.mine.MineManager;
import com.islandium.prison.rank.PrisonRankManager;
import com.islandium.prison.cell.CellManager;
import com.islandium.prison.service.PrisonServiceManager;
import com.islandium.prison.stats.PlayerStatsManager;
import com.islandium.prison.ui.PrisonUIManager;
import com.islandium.prison.ui.pages.MineManagerPage;
import com.islandium.prison.upgrade.PickaxeUpgradeManager;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.logging.Level;

/**
 * Plugin Prison pour Hytale.
 * Dépend de islandium-core pour les fonctionnalités de base.
 */
public class PrisonPlugin extends JavaPlugin {

    private static volatile PrisonPlugin instance;

    private IslandiumPlugin corePlugin;
    private PrisonConfig config;
    private MineManager mineManager;
    private PrisonRankManager rankManager;
    private PlayerStatsManager statsManager;
    private CellManager cellManager;
    private SellService sellService;
    private PickaxeUpgradeManager upgradeManager;
    private PrisonServiceManager serviceManager;
    private PrisonCommandManager commandManager;
    private PrisonListenerManager listenerManager;
    private PrisonUIManager uiManager;

    public PrisonPlugin(JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        instance = this;
        log(Level.INFO, "Initializing Prison...");

        try {
            // 1. Get Core Plugin reference (wait for it to be initialized)
            if (!IslandiumPlugin.isInitialized()) {
                throw new IllegalStateException("Islandium Core plugin is not yet initialized! Make sure Prison loads after Core.");
            }
            this.corePlugin = IslandiumPlugin.get();

            // 2. Load configuration
            log(Level.INFO, "Loading configuration...");
            this.config = new PrisonConfig(getDataFolder().toPath().resolve("config.json"));
            config.load();

            // 3. Initialize services
            log(Level.INFO, "Initializing services...");
            this.serviceManager = new PrisonServiceManager(this);
            serviceManager.initialize();

            // 4. Initialize managers
            log(Level.INFO, "Initializing managers...");
            this.mineManager = new MineManager(this);
            this.rankManager = new PrisonRankManager(this);
            this.statsManager = new PlayerStatsManager(this);
            this.sellService = new SellService(this);
            this.upgradeManager = new PickaxeUpgradeManager(this);
            this.cellManager = new CellManager(this);

            // Load data
            mineManager.loadAll();
            rankManager.loadAll();
            statsManager.loadAll();
            cellManager.loadAll();

            // 5. Initialize UI Manager
            log(Level.INFO, "Initializing UI manager...");
            this.uiManager = new PrisonUIManager(this);

            // 6. Register commands
            log(Level.INFO, "Registering commands...");
            this.commandManager = new PrisonCommandManager(this);
            commandManager.registerAll();

            // 7. Register listeners
            log(Level.INFO, "Registering listeners...");
            this.listenerManager = new PrisonListenerManager(this);
            listenerManager.registerAll();

            // 8. Register in main menu
            log(Level.INFO, "Registering in main menu...");
            IslandiumUIRegistry.getInstance().register(new IslandiumUIRegistry.Entry(
                    "prison",
                    "PRISON",
                    "Gestion des mines et du gamemode",
                    "#e040fb",
                    playerRef -> new MineManagerPage(playerRef, this),
                    false
            ));

            log(Level.INFO, "Prison initialized successfully!");

        } catch (Exception e) {
            log(Level.SEVERE, "Failed to initialize Prison: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void teardown() {
        log(Level.INFO, "Shutting down Prison...");

        try {
            // Save all data
            if (mineManager != null) {
                mineManager.saveAll();
            }
            if (rankManager != null) {
                rankManager.saveAll();
            }
            if (statsManager != null) {
                statsManager.saveAll();
            }
            if (cellManager != null) {
                cellManager.saveAll();
            }

            // Shutdown services
            if (serviceManager != null) {
                serviceManager.shutdown();
            }

            log(Level.INFO, "Prison shut down successfully!");

        } catch (Exception e) {
            log(Level.SEVERE, "Error during shutdown: " + e.getMessage());
            e.printStackTrace();
        }

        instance = null;
    }

    // === Getters ===

    @NotNull
    public static PrisonPlugin get() {
        return instance;
    }

    @NotNull
    public IslandiumPlugin getCore() {
        return corePlugin;
    }

    @NotNull
    public File getDataFolder() {
        File folder = new File("mods/prison");
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return folder;
    }

    @NotNull
    public PrisonConfig getConfig() {
        return config;
    }

    @NotNull
    public MineManager getMineManager() {
        return mineManager;
    }

    @NotNull
    public PrisonRankManager getRankManager() {
        return rankManager;
    }

    @NotNull
    public PlayerStatsManager getStatsManager() {
        return statsManager;
    }

    @NotNull
    public SellService getSellService() {
        return sellService;
    }

    @NotNull
    public PickaxeUpgradeManager getUpgradeManager() {
        return upgradeManager;
    }

    @NotNull
    public CellManager getCellManager() {
        return cellManager;
    }

    @NotNull
    public PrisonServiceManager getServiceManager() {
        return serviceManager;
    }

    @NotNull
    public PrisonUIManager getUIManager() {
        return uiManager;
    }

    // === Logging ===

    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger("Prison");

    public void log(Level level, String message) {
        LOGGER.log(level, "[Prison] " + message);
    }
}
