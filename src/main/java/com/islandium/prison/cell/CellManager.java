package com.islandium.prison.cell;

import com.islandium.core.api.IslandiumAPI;
import com.islandium.core.api.economy.EconomyService;
import com.islandium.core.api.location.ServerLocation;
import com.islandium.core.api.player.IslandiumPlayer;
import com.islandium.core.database.SQLExecutor;
import com.islandium.prison.PrisonPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Gestionnaire des cellules Prison.
 * Stockage SQL avec cache en memoire.
 */
public class CellManager {

    private final PrisonPlugin plugin;
    private final Map<String, Cell> cells = new ConcurrentHashMap<>();

    public CellManager(@NotNull PrisonPlugin plugin) {
        this.plugin = plugin;
    }

    private SQLExecutor getSql() {
        return plugin.getCore().getDatabaseManager().getExecutor();
    }

    // ===========================
    // Migrations & Loading
    // ===========================

    /**
     * Cree la table SQL si elle n'existe pas.
     */
    public void runMigrations() {
        try {
            getSql().execute("""
                CREATE TABLE IF NOT EXISTS prison_cells (
                    cell_id VARCHAR(64) PRIMARY KEY,
                    owner_uuid CHAR(36),
                    owner_name VARCHAR(32),
                    spawn_point VARCHAR(255),
                    corner1 VARCHAR(255),
                    corner2 VARCHAR(255),
                    purchase_time BIGINT DEFAULT 0,
                    expiration_time BIGINT DEFAULT 0,
                    locked BOOLEAN DEFAULT false,
                    INDEX idx_owner (owner_uuid)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
            """).join();
            plugin.log(Level.INFO, "Cells table migration completed.");
        } catch (Exception e) {
            plugin.log(Level.SEVERE, "Failed to run cells migrations: " + e.getMessage());
        }
    }

    /**
     * Charge toutes les cellules depuis SQL.
     */
    public void loadAll() {
        try {
            List<CellRow> rows = getSql().queryList(
                "SELECT cell_id, owner_uuid, owner_name, spawn_point, corner1, corner2, purchase_time, expiration_time, locked FROM prison_cells",
                rs -> {
                    try {
                        return new CellRow(
                            rs.getString("cell_id"),
                            rs.getString("owner_uuid"),
                            rs.getString("owner_name"),
                            rs.getString("spawn_point"),
                            rs.getString("corner1"),
                            rs.getString("corner2"),
                            rs.getLong("purchase_time"),
                            rs.getLong("expiration_time"),
                            rs.getBoolean("locked")
                        );
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            ).join();

            for (CellRow row : rows) {
                Cell cell = new Cell(row.cellId);
                if (row.ownerUuid != null && !row.ownerUuid.isEmpty()) {
                    cell.setOwner(UUID.fromString(row.ownerUuid));
                }
                cell.setOwnerName(row.ownerName);
                cell.setSpawnPoint(row.spawnPoint != null ? ServerLocation.deserialize(row.spawnPoint) : null);
                cell.setCorner1(row.corner1 != null ? ServerLocation.deserialize(row.corner1) : null);
                cell.setCorner2(row.corner2 != null ? ServerLocation.deserialize(row.corner2) : null);
                cell.setPurchaseTime(row.purchaseTime);
                cell.setExpirationTime(row.expirationTime);
                cell.setLocked(row.locked);
                cells.put(cell.getId().toLowerCase(), cell);
            }

            plugin.log(Level.INFO, "Loaded " + cells.size() + " cells from SQL.");
        } catch (Exception e) {
            plugin.log(Level.SEVERE, "Failed to load cells: " + e.getMessage());
        }
    }

    /**
     * Sauvegarde toutes les cellules en batch vers SQL (shutdown).
     */
    public void saveAll() {
        try {
            String sql = """
                INSERT INTO prison_cells (cell_id, owner_uuid, owner_name, spawn_point, corner1, corner2, purchase_time, expiration_time, locked)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    owner_uuid = VALUES(owner_uuid),
                    owner_name = VALUES(owner_name),
                    spawn_point = VALUES(spawn_point),
                    corner1 = VALUES(corner1),
                    corner2 = VALUES(corner2),
                    purchase_time = VALUES(purchase_time),
                    expiration_time = VALUES(expiration_time),
                    locked = VALUES(locked)
            """;

            List<Object[]> batchParams = new ArrayList<>();
            for (Cell cell : cells.values()) {
                batchParams.add(cellToParams(cell));
            }

            if (!batchParams.isEmpty()) {
                getSql().executeBatch(sql, batchParams).join();
                plugin.log(Level.INFO, "Saved " + batchParams.size() + " cells to SQL.");
            }
        } catch (Exception e) {
            plugin.log(Level.SEVERE, "Failed to save cells: " + e.getMessage());
        }
    }

    /**
     * Persiste une cellule de maniere async.
     */
    private void persistAsync(@NotNull Cell cell) {
        Object[] params = cellToParams(cell);
        try {
            getSql().execute("""
                INSERT INTO prison_cells (cell_id, owner_uuid, owner_name, spawn_point, corner1, corner2, purchase_time, expiration_time, locked)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    owner_uuid = VALUES(owner_uuid),
                    owner_name = VALUES(owner_name),
                    spawn_point = VALUES(spawn_point),
                    corner1 = VALUES(corner1),
                    corner2 = VALUES(corner2),
                    purchase_time = VALUES(purchase_time),
                    expiration_time = VALUES(expiration_time),
                    locked = VALUES(locked)
            """, params);
        } catch (Exception e) {
            plugin.log(Level.WARNING, "Failed to persist cell " + cell.getId() + ": " + e.getMessage());
        }
    }

    private Object[] cellToParams(Cell cell) {
        return new Object[]{
            cell.getId(),
            cell.getOwner() != null ? cell.getOwner().toString() : null,
            cell.getOwnerName(),
            cell.getSpawnPoint() != null ? cell.getSpawnPoint().serialize() : null,
            cell.getCorner1() != null ? cell.getCorner1().serialize() : null,
            cell.getCorner2() != null ? cell.getCorner2().serialize() : null,
            cell.getPurchaseTime(),
            cell.getExpirationTime(),
            cell.isLocked()
        };
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
     * Cree une nouvelle cellule.
     */
    public Cell createCell(@NotNull String id) {
        Cell cell = new Cell(id);
        cells.put(id.toLowerCase(), cell);
        persistAsync(cell);
        return cell;
    }

    /**
     * Supprime une cellule.
     */
    public boolean deleteCell(@NotNull String id) {
        Cell removed = cells.remove(id.toLowerCase());
        if (removed != null) {
            try {
                getSql().execute("DELETE FROM prison_cells WHERE cell_id = ?", id);
            } catch (Exception e) {
                plugin.log(Level.WARNING, "Failed to delete cell from SQL: " + e.getMessage());
            }
            return true;
        }
        return false;
    }

    // === Player Cell Management ===

    @Nullable
    public Cell getPlayerCell(@NotNull UUID uuid) {
        return cells.values().stream()
                .filter(cell -> uuid.equals(cell.getOwner()))
                .findFirst()
                .orElse(null);
    }

    public boolean hasCell(@NotNull UUID uuid) {
        return getPlayerCell(uuid) != null;
    }

    public int getPlayerCellCount(@NotNull UUID uuid) {
        return (int) cells.values().stream()
                .filter(cell -> uuid.equals(cell.getOwner()))
                .count();
    }

    @NotNull
    public List<Cell> getAvailableCells() {
        return cells.values().stream()
                .filter(cell -> !cell.hasOwner() && cell.isConfigured())
                .collect(Collectors.toList());
    }

    // === Cell Purchase ===

    @Nullable
    private EconomyService getEconomyService() {
        IslandiumAPI api = IslandiumAPI.get();
        return api != null ? api.getEconomyService() : null;
    }

    public enum PurchaseResult {
        SUCCESS,
        NOT_ENOUGH_MONEY,
        ALREADY_HAS_CELL,
        NO_CELLS_AVAILABLE,
        CELL_NOT_AVAILABLE
    }

    public PurchaseResult purchaseCell(@NotNull UUID uuid, @NotNull String playerName) {
        int maxCells = plugin.getConfig().getMaxCellsPerPlayer();
        if (getPlayerCellCount(uuid) >= maxCells) {
            return PurchaseResult.ALREADY_HAS_CELL;
        }

        List<Cell> available = getAvailableCells();
        if (available.isEmpty()) {
            return PurchaseResult.NO_CELLS_AVAILABLE;
        }

        Cell cell = available.get(0);
        return purchaseSpecificCell(uuid, playerName, cell);
    }

    public PurchaseResult purchaseCell(@NotNull IslandiumPlayer player) {
        return purchaseCell(player.getUniqueId(), player.getName());
    }

    public PurchaseResult purchaseSpecificCell(@NotNull UUID uuid, @NotNull String playerName, @NotNull Cell cell) {
        int maxCells = plugin.getConfig().getMaxCellsPerPlayer();
        if (getPlayerCellCount(uuid) >= maxCells) {
            return PurchaseResult.ALREADY_HAS_CELL;
        }

        if (cell.hasOwner()) {
            return PurchaseResult.CELL_NOT_AVAILABLE;
        }

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

            eco.removeBalance(uuid, price, "Prison cell purchase").join();
        } catch (Exception e) {
            plugin.log(Level.WARNING, "Failed to process cell purchase: " + e.getMessage());
            return PurchaseResult.NOT_ENOUGH_MONEY;
        }

        cell.setOwner(uuid);
        cell.setOwnerName(playerName);
        cell.setPurchaseTime(System.currentTimeMillis());

        int rentDays = plugin.getConfig().getCellRentDurationDays();
        if (rentDays > 0) {
            cell.setExpirationTime(System.currentTimeMillis() + (rentDays * 24L * 60L * 60L * 1000L));
        }

        persistAsync(cell);

        return PurchaseResult.SUCCESS;
    }

    public PurchaseResult purchaseSpecificCell(@NotNull IslandiumPlayer player, @NotNull Cell cell) {
        return purchaseSpecificCell(player.getUniqueId(), player.getName(), cell);
    }

    public boolean releaseCell(@NotNull UUID uuid) {
        Cell cell = getPlayerCell(uuid);
        if (cell == null) return false;

        cell.reset();
        persistAsync(cell);
        return true;
    }

    public int cleanupExpiredCells() {
        int count = 0;
        for (Cell cell : cells.values()) {
            if (cell.hasOwner() && cell.isExpired()) {
                cell.reset();
                persistAsync(cell);
                count++;
            }
        }
        return count;
    }

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

    // === Data Row ===

    private record CellRow(String cellId, String ownerUuid, String ownerName, String spawnPoint,
                           String corner1, String corner2, long purchaseTime, long expirationTime,
                           boolean locked) {}
}
