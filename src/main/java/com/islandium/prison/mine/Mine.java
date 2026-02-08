package com.islandium.prison.mine;

import com.islandium.core.api.location.ServerLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.HashSet;

/**
 * Représente une mine dans le système Prison.
 */
public class Mine {

    private final String id;
    private String displayName;
    private String requiredRank; // Rang minimum pour accéder

    // Positions de la mine (cuboid) - conservé pour compatibilité
    private ServerLocation corner1;
    private ServerLocation corner2;

    // Forme cylindrique (prioritaire si définie)
    private ServerLocation center;  // Centre du cylindre (bas)
    private int radius = 0;         // Rayon du cylindre
    private int height = 0;         // Hauteur du cylindre
    private boolean cylinderMode = false; // Mode cylindre actif (même sans centre défini)
    private boolean useDiameterMode = false; // Affichage en diamètre dans l'UI
    private double radiusAdjust = 0.46; // Ajustement du rayon pour le calcul de l'ellipse

    // Zone village (surzone étendue autour de la mine)
    private int villageMargin = 0; // 0 = désactivé

    // Point de spawn de la mine
    private ServerLocation spawnPoint;

    // Composition de la mine (type de bloc -> pourcentage)
    private Map<String, Double> composition = new HashMap<>();
    // Blocs désactivés dans la composition (ne seront pas générés lors du fill)
    private Set<String> disabledBlocks = new HashSet<>();
    // Limites de couches par bloc (blockType -> [minLayer, maxLayer]) - layer 0 = bas, -1 = pas de limite
    private Map<String, int[]> blockLayerLimits = new HashMap<>();

    // Composition par couche (layer index relatif -> composition)
    // Layer 0 = bas de la mine, Layer N = haut
    private Map<Integer, Map<String, Double>> layerComposition = new HashMap<>();
    private boolean useLayerComposition = false;

    // Mode mine naturelle - protection des blocs hors composition
    private boolean naturalMode = false;
    // Blocs autorisés par rang (blockType -> rang minimum requis)
    private Map<String, String> blockRankRequirements = new HashMap<>();

    // État de la mine
    private int totalBlocks;
    private int remainingBlocks;
    private long lastResetTime;
    private boolean autoReset = true;
    private int resetIntervalMinutes = 0; // 0 = utiliser la valeur globale de PrisonConfig

    public Mine(@NotNull String id) {
        this.id = id;
        this.displayName = id;
        this.requiredRank = id; // Par défaut, même nom que le rang
        this.lastResetTime = System.currentTimeMillis();
    }

    // === Getters/Setters ===

    @NotNull
    public String getId() {
        return id;
    }

    @NotNull
    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(@NotNull String displayName) {
        this.displayName = displayName;
    }

    @NotNull
    public String getRequiredRank() {
        return requiredRank;
    }

    public void setRequiredRank(@NotNull String requiredRank) {
        this.requiredRank = requiredRank;
    }

    @Nullable
    public ServerLocation getCorner1() {
        return corner1;
    }

    public void setCorner1(@Nullable ServerLocation corner1) {
        this.corner1 = corner1;
        recalculateTotalBlocks();
    }

    @Nullable
    public ServerLocation getCorner2() {
        return corner2;
    }

    public void setCorner2(@Nullable ServerLocation corner2) {
        this.corner2 = corner2;
        recalculateTotalBlocks();
    }

    // === Cylindrical Shape ===

    @Nullable
    public ServerLocation getCenter() {
        return center;
    }

    public void setCenter(@Nullable ServerLocation center) {
        this.center = center;
        if (center != null) {
            // Activer le mode cylindre et mettre des valeurs par défaut si nécessaire
            this.cylinderMode = true;
            if (radius <= 0) radius = 10;
            if (height <= 0) height = 10;
            // Définir automatiquement le spawn au centre du cylindre (au-dessus)
            updateSpawnToCenter();
        }
        recalculateTotalBlocks();
    }

    /**
     * Met à jour le spawn au centre exact du cylindre (au-dessus de la mine).
     * Appelé automatiquement quand le centre, rayon ou hauteur sont modifiés.
     */
    public void updateSpawnToCenter() {
        if (center != null && cylinderMode) {
            // Arrondir au bloc (floor) puis ajouter 0.5 pour être au centre du bloc
            double cx = Math.floor(center.x()) + 0.5;
            double cz = Math.floor(center.z()) + 0.5;
            double cy = Math.floor(center.y()) + height;

            this.spawnPoint = ServerLocation.of(
                    center.server(),
                    center.world(),
                    cx,
                    cy,
                    cz,
                    0f, 0f  // yaw, pitch
            );
        }
    }

    public int getRadius() {
        return radius;
    }

    public void setRadius(int radius) {
        this.radius = Math.max(0, radius);
        recalculateTotalBlocks();
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = Math.max(0, height);
        recalculateTotalBlocks();
        // Mettre à jour le spawn si en mode cylindre (spawn dépend de la hauteur)
        if (cylinderMode && center != null) {
            updateSpawnToCenter();
        }
    }

    /**
     * Vérifie si la mine utilise la forme cylindrique.
     */
    public boolean isCylindrical() {
        return cylinderMode;
    }

    /**
     * Vérifie si le cylindre est complètement configuré.
     */
    public boolean isCylinderConfigured() {
        return cylinderMode && center != null && radius > 0 && height > 0;
    }

    /**
     * Active ou désactive le mode cylindre.
     */
    public void setCylinderMode(boolean cylinderMode) {
        this.cylinderMode = cylinderMode;
    }

    /**
     * Vérifie si l'affichage utilise le mode diamètre.
     */
    public boolean isUseDiameterMode() {
        return useDiameterMode;
    }

    /**
     * Active ou désactive le mode d'affichage en diamètre.
     */
    public void setUseDiameterMode(boolean useDiameterMode) {
        this.useDiameterMode = useDiameterMode;
    }

    /**
     * Obtient l'ajustement du rayon pour le calcul de l'ellipse.
     */
    public double getRadiusAdjust() {
        return radiusAdjust;
    }

    /**
     * Définit l'ajustement du rayon pour le calcul de l'ellipse.
     */
    public void setRadiusAdjust(double radiusAdjust) {
        this.radiusAdjust = radiusAdjust;
        recalculateTotalBlocks();
    }

    /**
     * Obtient la marge de la zone village autour de la mine.
     * 0 = zone village désactivée.
     */
    public int getVillageMargin() {
        return villageMargin;
    }

    /**
     * Définit la marge de la zone village autour de la mine.
     */
    public void setVillageMargin(int villageMargin) {
        this.villageMargin = Math.max(0, villageMargin);
    }

    /**
     * Vérifie si la mine a une zone village configurée.
     */
    public boolean hasVillageZone() {
        return villageMargin > 0;
    }

    /**
     * Configure la mine comme un cylindre.
     */
    public void setCylinder(@NotNull ServerLocation center, int radius, int height) {
        this.cylinderMode = true;
        this.center = center;
        this.radius = Math.max(1, radius);
        this.height = Math.max(1, height);
        // Clear cuboid corners pour éviter confusion
        this.corner1 = null;
        this.corner2 = null;
        recalculateTotalBlocks();
    }

    @Nullable
    public ServerLocation getSpawnPoint() {
        return spawnPoint;
    }

    public void setSpawnPoint(@Nullable ServerLocation spawnPoint) {
        this.spawnPoint = spawnPoint;
    }

    @NotNull
    public Map<String, Double> getComposition() {
        return composition;
    }

    public void setComposition(@NotNull Map<String, Double> composition) {
        this.composition = composition;
    }

    public void addBlock(@NotNull String blockType, double percentage) {
        composition.put(blockType, percentage);
    }

    // === Disabled Blocks ===

    /**
     * Vérifie si un bloc est désactivé (ne sera pas généré lors du fill).
     */
    public boolean isBlockDisabled(@NotNull String blockType) {
        return disabledBlocks.contains(blockType);
    }

    /**
     * Active ou désactive un bloc dans la composition.
     */
    public void setBlockEnabled(@NotNull String blockType, boolean enabled) {
        if (enabled) {
            disabledBlocks.remove(blockType);
        } else {
            disabledBlocks.add(blockType);
        }
    }

    /**
     * Toggle l'état enabled/disabled d'un bloc.
     * @return true si le bloc est maintenant enabled, false sinon
     */
    public boolean toggleBlockEnabled(@NotNull String blockType) {
        if (disabledBlocks.contains(blockType)) {
            disabledBlocks.remove(blockType);
            return true;
        } else {
            disabledBlocks.add(blockType);
            return false;
        }
    }

    /**
     * Obtient la liste des blocs désactivés.
     */
    @NotNull
    public Set<String> getDisabledBlocks() {
        return disabledBlocks;
    }

    /**
     * Définit la liste des blocs désactivés.
     */
    public void setDisabledBlocks(@NotNull Set<String> disabledBlocks) {
        this.disabledBlocks = disabledBlocks;
    }

    /**
     * Obtient la composition effective (seulement les blocs activés).
     */
    @NotNull
    public Map<String, Double> getActiveComposition() {
        Map<String, Double> active = new HashMap<>();
        for (Map.Entry<String, Double> entry : composition.entrySet()) {
            if (!disabledBlocks.contains(entry.getKey())) {
                active.put(entry.getKey(), entry.getValue());
            }
        }
        return active;
    }

    // === Block Layer Limits ===

    /**
     * Définit les limites de couches pour un bloc.
     * @param blockType Le type de bloc
     * @param minLayer La couche minimum (0 = bas de la mine, -1 = pas de limite)
     * @param maxLayer La couche maximum (-1 = pas de limite, ou hauteur max)
     */
    public void setBlockLayerLimits(@NotNull String blockType, int minLayer, int maxLayer) {
        if (minLayer == -1 && maxLayer == -1) {
            blockLayerLimits.remove(blockType);
        } else {
            blockLayerLimits.put(blockType, new int[]{minLayer, maxLayer});
        }
    }

    /**
     * Obtient les limites de couches pour un bloc.
     * @return [minLayer, maxLayer] ou null si pas de limites
     */
    @Nullable
    public int[] getBlockLayerLimits(@NotNull String blockType) {
        return blockLayerLimits.get(blockType);
    }

    /**
     * Vérifie si un bloc peut apparaître à une couche donnée.
     * @param blockType Le type de bloc
     * @param layer La couche (0 = bas)
     * @return true si le bloc peut apparaître à cette couche
     */
    public boolean canBlockAppearAtLayer(@NotNull String blockType, int layer) {
        int[] limits = blockLayerLimits.get(blockType);
        if (limits == null) {
            return true; // Pas de limite
        }
        int minLayer = limits[0];
        int maxLayer = limits[1];
        if (minLayer != -1 && layer < minLayer) {
            return false;
        }
        if (maxLayer != -1 && layer > maxLayer) {
            return false;
        }
        return true;
    }

    /**
     * Obtient toutes les limites de couches.
     */
    @NotNull
    public Map<String, int[]> getBlockLayerLimits() {
        return blockLayerLimits;
    }

    /**
     * Définit toutes les limites de couches.
     */
    public void setBlockLayerLimitsMap(@NotNull Map<String, int[]> limits) {
        this.blockLayerLimits = limits;
    }

    /**
     * Obtient la composition active pour une couche spécifique (en tenant compte des limites).
     * @param layer La couche (0 = bas)
     * @return La composition filtrée pour cette couche
     */
    @NotNull
    public Map<String, Double> getActiveCompositionForLayer(int layer) {
        Map<String, Double> active = new HashMap<>();
        for (Map.Entry<String, Double> entry : composition.entrySet()) {
            String blockType = entry.getKey();
            if (!disabledBlocks.contains(blockType) && canBlockAppearAtLayer(blockType, layer)) {
                active.put(blockType, entry.getValue());
            }
        }
        return active;
    }

    // === Layer Composition ===

    @NotNull
    public Map<Integer, Map<String, Double>> getLayerComposition() {
        return layerComposition;
    }

    public void setLayerComposition(@NotNull Map<Integer, Map<String, Double>> layerComposition) {
        this.layerComposition = layerComposition;
    }

    public boolean isUseLayerComposition() {
        return useLayerComposition;
    }

    public void setUseLayerComposition(boolean useLayerComposition) {
        this.useLayerComposition = useLayerComposition;
    }

    /**
     * Ajoute un bloc à une couche spécifique.
     * @param layer L'index de la couche (0 = bas)
     * @param blockType Le type de bloc
     * @param percentage Le pourcentage
     */
    public void addBlockToLayer(int layer, @NotNull String blockType, double percentage) {
        layerComposition.computeIfAbsent(layer, k -> new HashMap<>()).put(blockType, percentage);
    }

    /**
     * Obtient la composition pour une couche spécifique.
     * Si pas de composition définie pour cette couche, retourne la composition globale.
     */
    @NotNull
    public Map<String, Double> getCompositionForLayer(int layer) {
        if (!useLayerComposition || layerComposition.isEmpty()) {
            return composition;
        }
        return layerComposition.getOrDefault(layer, composition);
    }

    /**
     * Obtient le nombre de couches dans la mine.
     */
    public int getLayerCount() {
        if (!isConfigured()) return 0;
        if (isCylindrical()) {
            return height;
        }
        return Math.abs((int) corner2.y() - (int) corner1.y()) + 1;
    }

    /**
     * Vide la composition des couches.
     */
    public void clearLayerComposition() {
        layerComposition.clear();
    }

    // === Natural Mode ===

    public boolean isNaturalMode() {
        return naturalMode;
    }

    public void setNaturalMode(boolean naturalMode) {
        this.naturalMode = naturalMode;
    }

    @NotNull
    public Map<String, String> getBlockRankRequirements() {
        return blockRankRequirements;
    }

    public void setBlockRankRequirements(@NotNull Map<String, String> blockRankRequirements) {
        this.blockRankRequirements = blockRankRequirements;
    }

    /**
     * Définit le rang requis pour casser un type de bloc spécifique.
     */
    public void setBlockRankRequirement(@NotNull String blockType, @NotNull String rank) {
        blockRankRequirements.put(blockType, rank);
    }

    /**
     * Obtient le rang requis pour casser un type de bloc.
     * Retourne le rang de la mine par défaut si non spécifié.
     */
    @NotNull
    public String getBlockRankRequirement(@NotNull String blockType) {
        return blockRankRequirements.getOrDefault(blockType, requiredRank);
    }

    /**
     * Vérifie si un type de bloc fait partie de la composition de la mine.
     */
    public boolean isBlockInComposition(@NotNull String blockType) {
        // Vérifier composition globale
        if (composition.containsKey(blockType)) {
            return true;
        }
        // Vérifier composition par layer
        for (Map<String, Double> layerComp : layerComposition.values()) {
            if (layerComp.containsKey(blockType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Vide les requirements de rang par bloc.
     */
    public void clearBlockRankRequirements() {
        blockRankRequirements.clear();
    }

    public int getTotalBlocks() {
        return totalBlocks;
    }

    public int getRemainingBlocks() {
        return remainingBlocks;
    }

    public void setRemainingBlocks(int remainingBlocks) {
        this.remainingBlocks = remainingBlocks;
    }

    public void decrementRemainingBlocks() {
        if (remainingBlocks > 0) {
            remainingBlocks--;
        }
    }

    public long getLastResetTime() {
        return lastResetTime;
    }

    public void setLastResetTime(long lastResetTime) {
        this.lastResetTime = lastResetTime;
    }

    public boolean isAutoReset() {
        return autoReset;
    }

    public void setAutoReset(boolean autoReset) {
        this.autoReset = autoReset;
    }

    public int getResetIntervalMinutes() {
        return resetIntervalMinutes;
    }

    public void setResetIntervalMinutes(int resetIntervalMinutes) {
        this.resetIntervalMinutes = resetIntervalMinutes;
    }

    // === Utility Methods ===

    /**
     * Vérifie si la mine est configurée (cylindre ou cuboid).
     */
    public boolean isConfigured() {
        if (cylinderMode) {
            return isCylinderConfigured();
        }
        return corner1 != null && corner2 != null;
    }

    /**
     * Vérifie si la mine a un spawn défini.
     */
    public boolean hasSpawn() {
        return spawnPoint != null;
    }

    /**
     * Recalcule le nombre total de blocs dans la mine.
     * Utilise une grille (2*radius+1) x (2*radius+1) avec formule d'ellipse ajustée.
     *
     * Pour un rayon R, la grille va de -R à +R (soit 2R+1 positions).
     * halfW = halfH = R
     * radiusAdjust = 0.40 (pour avoir 13 blocs au bord quand R=50)
     * Condition d'inclusion: (x²/(halfW+adj)²) + (z²/(halfH+adj)²) < 1
     */
    private void recalculateTotalBlocks() {
        // Cylindre prioritaire
        if (isCylindrical()) {
            // Grille (2*radius+1) x (2*radius+1), de -radius à +radius
            int halfW = radius;
            int halfH = radius;
            double rX = halfW + this.radiusAdjust;
            double rZ = halfH + radiusAdjust;
            double rXSq = rX * rX;
            double rZSq = rZ * rZ;

            int count = 0;
            for (int dz = -halfH; dz <= halfH; dz++) {
                for (int dx = -halfW; dx <= halfW; dx++) {
                    // Formule de l'ellipse: (x²/rX²) + (z²/rZ²) < 1
                    double distSq = (dx * dx) / rXSq + (dz * dz) / rZSq;
                    if (distSq < 1.0) {
                        count++;
                    }
                }
            }
            totalBlocks = count * height;
            remainingBlocks = totalBlocks;
            return;
        }

        // Cuboid fallback
        if (corner1 == null || corner2 == null) {
            totalBlocks = 0;
            remainingBlocks = 0;
            return;
        }

        int dx = Math.abs((int) corner2.x() - (int) corner1.x()) + 1;
        int dy = Math.abs((int) corner2.y() - (int) corner1.y()) + 1;
        int dz = Math.abs((int) corner2.z() - (int) corner1.z()) + 1;

        totalBlocks = dx * dy * dz;
        remainingBlocks = totalBlocks;
    }

    /**
     * Calcule le pourcentage de blocs restants.
     */
    public double getRemainingPercentage() {
        if (totalBlocks == 0) return 100.0;
        return (remainingBlocks * 100.0) / totalBlocks;
    }

    /**
     * Vérifie si une position est dans la mine.
     * Utilise une grille (2*radius+1) x (2*radius+1) avec formule d'ellipse ajustée.
     */
    public boolean contains(@NotNull ServerLocation location) {
        if (!isConfigured()) return false;

        // Cylindre prioritaire
        if (isCylindrical()) {
            int cx = (int) Math.floor(center.x());
            int cy = (int) Math.floor(center.y());
            int cz = (int) Math.floor(center.z());
            int lx = (int) Math.floor(location.x());
            int ly = (int) Math.floor(location.y());
            int lz = (int) Math.floor(location.z());

            // Vérifier la hauteur (Y)
            if (ly < cy || ly >= cy + height) {
                return false;
            }

            // Grille (2*radius+1) x (2*radius+1), de -radius à +radius
            int halfW = radius;
            int halfH = radius;
            double rX = halfW + this.radiusAdjust;
            double rZ = halfH + this.radiusAdjust;
            double rXSq = rX * rX;
            double rZSq = rZ * rZ;

            int dx = lx - cx;
            int dz = lz - cz;

            // Vérifier les bornes de la grille
            if (dx < -halfW || dx > halfW || dz < -halfH || dz > halfH) {
                return false;
            }

            // Formule de l'ellipse: (x²/rX²) + (z²/rZ²) < 1
            double distSq = (dx * dx) / rXSq + (dz * dz) / rZSq;
            return distSq < 1.0;
        }

        // Cuboid fallback
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
     * Vérifie si une position est dans la zone village (mine étendue par villageMargin).
     * La zone village inclut la mine elle-même.
     * Pour cylindre: radius + margin, Y étendu de margin en bas et en haut.
     * Pour cuboid: coins étendus de margin dans toutes les directions.
     */
    public boolean containsVillage(@NotNull ServerLocation location) {
        if (!isConfigured() || villageMargin <= 0) return false;

        if (isCylindrical()) {
            int cx = (int) Math.floor(center.x());
            int cy = (int) Math.floor(center.y());
            int cz = (int) Math.floor(center.z());
            int lx = (int) Math.floor(location.x());
            int ly = (int) Math.floor(location.y());
            int lz = (int) Math.floor(location.z());

            // Vérifier la hauteur étendue (Y élargi de margin en bas et en haut)
            if (ly < cy - villageMargin || ly >= cy + height + villageMargin) {
                return false;
            }

            // Rayon étendu
            int expandedRadius = radius + villageMargin;
            int halfW = expandedRadius;
            int halfH = expandedRadius;
            double rX = halfW + this.radiusAdjust;
            double rZ = halfH + this.radiusAdjust;
            double rXSq = rX * rX;
            double rZSq = rZ * rZ;

            int dx = lx - cx;
            int dz = lz - cz;

            if (dx < -halfW || dx > halfW || dz < -halfH || dz > halfH) {
                return false;
            }

            double distSq = (dx * dx) / rXSq + (dz * dz) / rZSq;
            return distSq < 1.0;
        }

        // Cuboid: coins étendus de margin dans toutes les directions
        double minX = Math.min(corner1.x(), corner2.x()) - villageMargin;
        double maxX = Math.max(corner1.x(), corner2.x()) + villageMargin;
        double minY = Math.min(corner1.y(), corner2.y()) - villageMargin;
        double maxY = Math.max(corner1.y(), corner2.y()) + villageMargin;
        double minZ = Math.min(corner1.z(), corner2.z()) - villageMargin;
        double maxZ = Math.max(corner1.z(), corner2.z()) + villageMargin;

        return location.x() >= minX && location.x() <= maxX
                && location.y() >= minY && location.y() <= maxY
                && location.z() >= minZ && location.z() <= maxZ;
    }

    /**
     * Réinitialise l'état de la mine (pour après un reset).
     */
    public void resetState() {
        remainingBlocks = totalBlocks;
        lastResetTime = System.currentTimeMillis();
    }

    /**
     * Sérialise la mine pour la sauvegarde.
     */
    @NotNull
    public MineData toData() {
        MineData data = new MineData();
        data.id = id;
        data.displayName = displayName;
        data.requiredRank = requiredRank;
        data.corner1 = corner1 != null ? corner1.serialize() : null;
        data.corner2 = corner2 != null ? corner2.serialize() : null;
        // Cylindrical shape
        data.cylinderMode = cylinderMode;
        data.useDiameterMode = useDiameterMode;
        data.radiusAdjust = radiusAdjust;
        data.center = center != null ? center.serialize() : null;
        data.radius = radius;
        data.height = height;
        // Common
        data.spawnPoint = spawnPoint != null ? spawnPoint.serialize() : null;
        data.composition = new HashMap<>(composition);
        data.layerComposition = new HashMap<>();
        for (var entry : layerComposition.entrySet()) {
            data.layerComposition.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        data.useLayerComposition = useLayerComposition;
        data.naturalMode = naturalMode;
        data.blockRankRequirements = new HashMap<>(blockRankRequirements);
        data.disabledBlocks = new HashSet<>(disabledBlocks);
        // Convertir int[] en List<Integer> pour la sérialisation JSON
        data.blockLayerLimits = new HashMap<>();
        for (Map.Entry<String, int[]> entry : blockLayerLimits.entrySet()) {
            data.blockLayerLimits.put(entry.getKey(), List.of(entry.getValue()[0], entry.getValue()[1]));
        }
        data.villageMargin = villageMargin;
        data.totalBlocks = totalBlocks;
        data.remainingBlocks = remainingBlocks;
        data.lastResetTime = lastResetTime;
        data.autoReset = autoReset;
        data.resetIntervalMinutes = resetIntervalMinutes;
        return data;
    }

    /**
     * Charge une mine depuis les données sauvegardées.
     */
    @NotNull
    public static Mine fromData(@NotNull MineData data) {
        Mine mine = new Mine(data.id);
        mine.displayName = data.displayName != null ? data.displayName : data.id;
        mine.requiredRank = data.requiredRank != null ? data.requiredRank : data.id;
        mine.corner1 = data.corner1 != null ? ServerLocation.deserialize(data.corner1) : null;
        mine.corner2 = data.corner2 != null ? ServerLocation.deserialize(data.corner2) : null;
        // Cylindrical shape
        mine.cylinderMode = data.cylinderMode;
        mine.useDiameterMode = data.useDiameterMode;
        mine.radiusAdjust = data.radiusAdjust > 0 ? data.radiusAdjust : 0.46; // Valeur par défaut si non définie
        mine.center = data.center != null ? ServerLocation.deserialize(data.center) : null;
        mine.radius = data.radius;
        mine.height = data.height;
        // Common
        mine.spawnPoint = data.spawnPoint != null ? ServerLocation.deserialize(data.spawnPoint) : null;
        mine.composition = data.composition != null ? new HashMap<>(data.composition) : new HashMap<>();
        mine.layerComposition = new HashMap<>();
        if (data.layerComposition != null) {
            for (var entry : data.layerComposition.entrySet()) {
                mine.layerComposition.put(entry.getKey(), new HashMap<>(entry.getValue()));
            }
        }
        mine.useLayerComposition = data.useLayerComposition;
        mine.naturalMode = data.naturalMode;
        mine.blockRankRequirements = data.blockRankRequirements != null ? new HashMap<>(data.blockRankRequirements) : new HashMap<>();
        mine.disabledBlocks = data.disabledBlocks != null ? new HashSet<>(data.disabledBlocks) : new HashSet<>();
        // Convertir List<Integer> en int[] depuis la sérialisation JSON
        mine.blockLayerLimits = new HashMap<>();
        if (data.blockLayerLimits != null) {
            for (Map.Entry<String, List<Integer>> entry : data.blockLayerLimits.entrySet()) {
                List<Integer> limits = entry.getValue();
                if (limits != null && limits.size() >= 2) {
                    mine.blockLayerLimits.put(entry.getKey(), new int[]{limits.get(0), limits.get(1)});
                }
            }
        }
        mine.villageMargin = data.villageMargin;
        mine.totalBlocks = data.totalBlocks;
        mine.remainingBlocks = data.remainingBlocks;
        mine.lastResetTime = data.lastResetTime;
        mine.autoReset = data.autoReset;
        mine.resetIntervalMinutes = data.resetIntervalMinutes;
        return mine;
    }

    /**
     * Classe de données pour la sérialisation JSON.
     */
    public static class MineData {
        public String id;
        public String displayName;
        public String requiredRank;
        public String corner1;
        public String corner2;
        // Cylindrical shape
        public boolean cylinderMode;
        public boolean useDiameterMode;
        public double radiusAdjust;
        public String center;
        public int radius;
        public int height;
        // Common
        public String spawnPoint;
        public Map<String, Double> composition;
        public Map<Integer, Map<String, Double>> layerComposition;
        public boolean useLayerComposition;
        public boolean naturalMode;
        public Map<String, String> blockRankRequirements;
        public Set<String> disabledBlocks;
        public Map<String, List<Integer>> blockLayerLimits;
        public int villageMargin;
        public int totalBlocks;
        public int remainingBlocks;
        public long lastResetTime;
        public boolean autoReset;
        public int resetIntervalMinutes;
    }
}
