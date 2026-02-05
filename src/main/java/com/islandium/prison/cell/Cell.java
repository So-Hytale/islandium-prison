package com.islandium.prison.cell;

import com.islandium.core.api.location.ServerLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Représente une cellule de prison.
 */
public class Cell {

    private final String id;
    private UUID owner;
    private String ownerName;

    // Position de la cellule
    private ServerLocation spawnPoint;
    private ServerLocation corner1;
    private ServerLocation corner2;

    // Métadonnées
    private long purchaseTime;
    private long expirationTime; // 0 = permanent
    private boolean locked = false;

    public Cell(@NotNull String id) {
        this.id = id;
    }

    // === Getters/Setters ===

    @NotNull
    public String getId() {
        return id;
    }

    @Nullable
    public UUID getOwner() {
        return owner;
    }

    public void setOwner(@Nullable UUID owner) {
        this.owner = owner;
    }

    @Nullable
    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(@Nullable String ownerName) {
        this.ownerName = ownerName;
    }

    @Nullable
    public ServerLocation getSpawnPoint() {
        return spawnPoint;
    }

    public void setSpawnPoint(@Nullable ServerLocation spawnPoint) {
        this.spawnPoint = spawnPoint;
    }

    @Nullable
    public ServerLocation getCorner1() {
        return corner1;
    }

    public void setCorner1(@Nullable ServerLocation corner1) {
        this.corner1 = corner1;
    }

    @Nullable
    public ServerLocation getCorner2() {
        return corner2;
    }

    public void setCorner2(@Nullable ServerLocation corner2) {
        this.corner2 = corner2;
    }

    public long getPurchaseTime() {
        return purchaseTime;
    }

    public void setPurchaseTime(long purchaseTime) {
        this.purchaseTime = purchaseTime;
    }

    public long getExpirationTime() {
        return expirationTime;
    }

    public void setExpirationTime(long expirationTime) {
        this.expirationTime = expirationTime;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
    }

    // === Utility Methods ===

    /**
     * Vérifie si la cellule a un propriétaire.
     */
    public boolean hasOwner() {
        return owner != null;
    }

    /**
     * Vérifie si la cellule est configurée.
     */
    public boolean isConfigured() {
        return spawnPoint != null;
    }

    /**
     * Vérifie si la cellule est expirée.
     */
    public boolean isExpired() {
        if (expirationTime == 0) return false; // Permanent
        return System.currentTimeMillis() > expirationTime;
    }

    /**
     * Vérifie si une position est dans la cellule.
     */
    public boolean contains(@NotNull ServerLocation location) {
        if (corner1 == null || corner2 == null) return false;

        double minX = Math.min(corner1.x(), corner2.x());
        double maxX = Math.max(corner1.x(), corner2.x());
        double minY = Math.min(corner1.y(), corner2.y());
        double maxY = Math.max(corner1.y(), corner2.y());
        double minZ = Math.min(corner1.z(), corner2.z());
        double maxZ = Math.max(corner1.z(), corner2.z());

        return location.x() >= minX && location.x() <= maxX
                && location.y() >= minY && location.y() <= maxY
                && location.z() >= minZ && location.z() <= maxZ;
    }

    /**
     * Réinitialise la cellule (retire le propriétaire).
     */
    public void reset() {
        this.owner = null;
        this.ownerName = null;
        this.purchaseTime = 0;
        this.expirationTime = 0;
        this.locked = false;
    }

    /**
     * Sérialise la cellule pour la sauvegarde.
     */
    @NotNull
    public CellData toData() {
        CellData data = new CellData();
        data.id = id;
        data.owner = owner != null ? owner.toString() : null;
        data.ownerName = ownerName;
        data.spawnPoint = spawnPoint != null ? spawnPoint.serialize() : null;
        data.corner1 = corner1 != null ? corner1.serialize() : null;
        data.corner2 = corner2 != null ? corner2.serialize() : null;
        data.purchaseTime = purchaseTime;
        data.expirationTime = expirationTime;
        data.locked = locked;
        return data;
    }

    /**
     * Charge une cellule depuis les données.
     */
    @NotNull
    public static Cell fromData(@NotNull CellData data) {
        Cell cell = new Cell(data.id);
        cell.owner = data.owner != null ? UUID.fromString(data.owner) : null;
        cell.ownerName = data.ownerName;
        cell.spawnPoint = data.spawnPoint != null ? ServerLocation.deserialize(data.spawnPoint) : null;
        cell.corner1 = data.corner1 != null ? ServerLocation.deserialize(data.corner1) : null;
        cell.corner2 = data.corner2 != null ? ServerLocation.deserialize(data.corner2) : null;
        cell.purchaseTime = data.purchaseTime;
        cell.expirationTime = data.expirationTime;
        cell.locked = data.locked;
        return cell;
    }

    /**
     * Classe de données pour la sérialisation JSON.
     */
    public static class CellData {
        public String id;
        public String owner;
        public String ownerName;
        public String spawnPoint;
        public String corner1;
        public String corner2;
        public long purchaseTime;
        public long expirationTime;
        public boolean locked;
    }
}
