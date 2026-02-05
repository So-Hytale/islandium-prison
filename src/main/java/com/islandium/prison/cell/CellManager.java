package com.islandium.prison.cell;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.islandium.core.api.IslandiumAPI;
import com.islandium.core.api.economy.EconomyService;
import com.islandium.core.api.player.IslandiumPlayer;
import com.islandium.prison.PrisonPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Gestionnaire des cellules Prison.
 */
public class CellManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final PrisonPlugin plugin;
    private final Path cellsFile;
    private final Map<String, Cell> cells = new ConcurrentHashMap<>();

    public CellManager(@NotNull PrisonPlugin plugin) {
        this.plugin = plugin;
        this.cellsFile = plugin.getDataFolder().toPath().resolve("cells.json");
    }

    /**
     * Charge toutes les cellules.
     */
    public void loadAll() {
        try {
            if (Files.exists(cellsFile)) {
                String content = Files.readString(cellsFile);
                Type type = new TypeToken<List<Cell.CellData>>() {}.getType();
                List<Cell.CellData> dataList = GSON.fromJson(content, type);

                if (dataList != null) {
                    for (Cell.CellData data : dataList) {
                        Cell cell = Cell.fromData(data);
                        cells.put(cell.getId().toLowerCase(), cell);
                    }
                }

                plugin.log(Level.INFO, "Loaded " + cells.size() + " cells");
            }
        } catch (Exception e) {
            plugin.log(Level.SEVERE, "Failed to load cells: " + e.getMessage());
        }
    }

    /**
     * Sauvegarde toutes les cellules.
     */
    public void saveAll() {
        try {
            List<Cell.CellData> dataList = new ArrayList<>();
            for (Cell cell : cells.values()) {
                dataList.add(cell.toData());
            }

            Files.createDirectories(cellsFile.getParent());
            Files.writeString(cellsFile, GSON.toJson(dataList));
        } catch (IOException e) {
            plugin.log(Level.SEVERE, "Failed to save cells: " + e.getMessage());
        }
    }

    // === Cell CRUD ===

    @Nullable
    public Cell getCell(@NotNull String id) {
        return cells.get(id.toLowerCase());
    }

    @NotNull
    public Collection<Cell> getAllCells() {
        return Collections.unmodifiableCollection(cells.values());
    }

    /**
     * Crée une nouvelle cellule.
     */
    public Cell createCell(@NotNull String id) {
        Cell cell = new Cell(id);
        cells.put(id.toLowerCase(), cell);
        saveAll();
        return cell;
    }

    /**
     * Supprime une cellule.
     */
    public boolean deleteCell(@NotNull String id) {
        Cell removed = cells.remove(id.toLowerCase());
        if (removed != null) {
            saveAll();
            return true;
        }
        return false;
    }

    // === Player Cell Management ===

    /**
     * Obtient la cellule d'un joueur.
     */
    @Nullable
    public Cell getPlayerCell(@NotNull UUID uuid) {
        return cells.values().stream()
                .filter(cell -> uuid.equals(cell.getOwner()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Vérifie si un joueur possède une cellule.
     */
    public boolean hasCell(@NotNull UUID uuid) {
        return getPlayerCell(uuid) != null;
    }

    /**
     * Compte le nombre de cellules d'un joueur.
     */
    public int getPlayerCellCount(@NotNull UUID uuid) {
        return (int) cells.values().stream()
                .filter(cell -> uuid.equals(cell.getOwner()))
                .count();
    }

    /**
     * Obtient toutes les cellules disponibles (sans propriétaire).
     */
    @NotNull
    public List<Cell> getAvailableCells() {
        return cells.values().stream()
                .filter(cell -> !cell.hasOwner() && cell.isConfigured())
                .collect(Collectors.toList());
    }

    // === Cell Purchase ===

    /**
     * Obtient le service économique.
     */
    @Nullable
    private EconomyService getEconomyService() {
        IslandiumAPI api = IslandiumAPI.get();
        return api != null ? api.getEconomyService() : null;
    }

    /**
     * Résultat d'un achat de cellule.
     */
    public enum PurchaseResult {
        SUCCESS,
        NOT_ENOUGH_MONEY,
        ALREADY_HAS_CELL,
        NO_CELLS_AVAILABLE,
        CELL_NOT_AVAILABLE
    }

    /**
     * Achète une cellule pour un joueur via UUID.
     */
    public PurchaseResult purchaseCell(@NotNull UUID uuid, @NotNull String playerName) {
        // Check if player already has max cells
        int maxCells = plugin.getConfig().getMaxCellsPerPlayer();
        if (getPlayerCellCount(uuid) >= maxCells) {
            return PurchaseResult.ALREADY_HAS_CELL;
        }

        // Find available cell
        List<Cell> available = getAvailableCells();
        if (available.isEmpty()) {
            return PurchaseResult.NO_CELLS_AVAILABLE;
        }

        Cell cell = available.get(0);
        return purchaseSpecificCell(uuid, playerName, cell);
    }

    /**
     * Achète une cellule pour un joueur (legacy).
     */
    public PurchaseResult purchaseCell(@NotNull IslandiumPlayer player) {
        return purchaseCell(player.getUniqueId(), player.getName());
    }

    /**
     * Achète une cellule spécifique pour un joueur via UUID.
     */
    public PurchaseResult purchaseSpecificCell(@NotNull UUID uuid, @NotNull String playerName, @NotNull Cell cell) {
        // Check if player already has max cells
        int maxCells = plugin.getConfig().getMaxCellsPerPlayer();
        if (getPlayerCellCount(uuid) >= maxCells) {
            return PurchaseResult.ALREADY_HAS_CELL;
        }

        // Check if cell is available
        if (cell.hasOwner()) {
            return PurchaseResult.CELL_NOT_AVAILABLE;
        }

        // Check balance via EconomyService
        BigDecimal price = plugin.getConfig().getDefaultCellPrice();
        EconomyService eco = getEconomyService();
        if (eco == null) {
            plugin.log(Level.WARNING, "EconomyService not available for cell purchase");
            return PurchaseResult.NOT_ENOUGH_MONEY;
        }

        try {
            if (!eco.hasBalance(uuid, price).join()) {
                return PurchaseResult.NOT_ENOUGH_MONEY;
            }

            // Deduct money
            eco.removeBalance(uuid, price, "Prison cell purchase").join();
        } catch (Exception e) {
            plugin.log(Level.WARNING, "Failed to process cell purchase: " + e.getMessage());
            return PurchaseResult.NOT_ENOUGH_MONEY;
        }

        // Assign cell
        cell.setOwner(uuid);
        cell.setOwnerName(playerName);
        cell.setPurchaseTime(System.currentTimeMillis());

        // Set expiration if applicable
        int rentDays = plugin.getConfig().getCellRentDurationDays();
        if (rentDays > 0) {
            cell.setExpirationTime(System.currentTimeMillis() + (rentDays * 24L * 60L * 60L * 1000L));
        }

        saveAll();

        return PurchaseResult.SUCCESS;
    }

    /**
     * Achète une cellule spécifique pour un joueur (legacy).
     */
    public PurchaseResult purchaseSpecificCell(@NotNull IslandiumPlayer player, @NotNull Cell cell) {
        return purchaseSpecificCell(player.getUniqueId(), player.getName(), cell);
    }

    /**
     * Libère la cellule d'un joueur.
     */
    public boolean releaseCell(@NotNull UUID uuid) {
        Cell cell = getPlayerCell(uuid);
        if (cell == null) return false;

        cell.reset();
        saveAll();
        return true;
    }

    /**
     * Vérifie et nettoie les cellules expirées.
     */
    public int cleanupExpiredCells() {
        int count = 0;
        for (Cell cell : cells.values()) {
            if (cell.hasOwner() && cell.isExpired()) {
                cell.reset();
                count++;
            }
        }
        if (count > 0) {
            saveAll();
        }
        return count;
    }

    /**
     * Téléporte un joueur à sa cellule.
     */
    public boolean teleportToCell(@NotNull IslandiumPlayer player) {
        Cell cell = getPlayerCell(player.getUniqueId());
        if (cell == null || cell.getSpawnPoint() == null) {
            player.sendMessage(plugin.getConfig().getPrefixedMessage("cell.no-cell"));
            return false;
        }

        plugin.getCore().getTeleportService().teleportWithWarmup(
                player,
                cell.getSpawnPoint(),
                () -> player.sendMessage(plugin.getConfig().getPrefixedMessage("cell.teleported"))
        );

        return true;
    }
}
