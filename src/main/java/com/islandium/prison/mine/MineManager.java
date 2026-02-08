package com.islandium.prison.mine;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.islandium.core.api.location.ServerLocation;
import com.islandium.core.api.player.IslandiumPlayer;
import com.islandium.prison.PrisonPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;

/**
 * Gestionnaire des mines Prison.
 */
public class MineManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final PrisonPlugin plugin;
    private final Path minesFile;
    private final Map<String, Mine> mines = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;

    // Reset tasks
    private final Map<String, ScheduledFuture<?>> resetTasks = new ConcurrentHashMap<>();

    public MineManager(@NotNull PrisonPlugin plugin) {
        this.plugin = plugin;
        this.minesFile = plugin.getDataFolder().toPath().resolve("mines.json");
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Prison-MineReset");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Charge toutes les mines depuis le fichier.
     */
    public void loadAll() {
        try {
            if (Files.exists(minesFile)) {
                String content = Files.readString(minesFile);
                Type type = new TypeToken<List<Mine.MineData>>() {}.getType();
                List<Mine.MineData> dataList = GSON.fromJson(content, type);

                if (dataList != null) {
                    for (Mine.MineData data : dataList) {
                        Mine mine = Mine.fromData(data);
                        mines.put(mine.getId().toLowerCase(), mine);
                        scheduleReset(mine);
                    }
                }

                plugin.log(Level.INFO, "Loaded " + mines.size() + " mines");
            } else {
                // Pas de mines par défaut - l'admin les crée manuellement
                plugin.log(Level.INFO, "No mines found. Use /pa createmine <name> to create mines.");
            }
        } catch (Exception e) {
            plugin.log(Level.SEVERE, "Failed to load mines: " + e.getMessage());
        }
    }

    /**
     * Sauvegarde toutes les mines.
     */
    public void saveAll() {
        try {
            List<Mine.MineData> dataList = new ArrayList<>();
            for (Mine mine : mines.values()) {
                dataList.add(mine.toData());
            }

            Files.createDirectories(minesFile.getParent());
            Files.writeString(minesFile, GSON.toJson(dataList));
        } catch (IOException e) {
            plugin.log(Level.SEVERE, "Failed to save mines: " + e.getMessage());
        }
    }

    // === Mine CRUD ===

    @Nullable
    public Mine getMine(@NotNull String id) {
        return mines.get(id.toLowerCase());
    }

    @NotNull
    public Collection<Mine> getAllMines() {
        return Collections.unmodifiableCollection(mines.values());
    }

    public void addMine(@NotNull Mine mine) {
        mines.put(mine.getId().toLowerCase(), mine);
        scheduleReset(mine);
        saveAll();
    }

    public void removeMine(@NotNull String id) {
        Mine mine = mines.remove(id.toLowerCase());
        if (mine != null) {
            cancelResetTask(id);
            saveAll();
        }
    }

    /**
     * Sauvegarde une mine spécifique (et toutes les autres).
     */
    public void saveMine(@NotNull Mine mine) {
        mines.put(mine.getId().toLowerCase(), mine);
        saveAll();
    }

    // === Mine Access ===

    /**
     * Vérifie si un joueur peut accéder à une mine par UUID.
     */
    public boolean canAccess(@NotNull UUID uuid, @NotNull Mine mine) {
        String playerRank = plugin.getRankManager().getPlayerRank(uuid);
        return canAccessWithRank(playerRank, mine.getRequiredRank());
    }

    /**
     * Vérifie si un joueur peut accéder à une mine.
     */
    public boolean canAccess(@NotNull IslandiumPlayer player, @NotNull Mine mine) {
        return canAccess(player.getUniqueId(), mine);
    }

    /**
     * Vérifie si un rang permet d'accéder à une mine.
     */
    public boolean canAccessWithRank(@NotNull String playerRank, @NotNull String requiredRank) {
        int playerIndex = getRankIndex(playerRank);
        int requiredIndex = getRankIndex(requiredRank);
        return playerIndex >= requiredIndex;
    }

    private int getRankIndex(String rank) {
        if (rank.equalsIgnoreCase("FREE")) return 27;
        if (rank.length() == 1 && rank.charAt(0) >= 'A' && rank.charAt(0) <= 'Z') {
            return rank.charAt(0) - 'A';
        }
        return -1;
    }

    /**
     * Obtient la mine la plus haute accessible par un joueur via UUID.
     */
    @Nullable
    public Mine getHighestAccessibleMine(@NotNull UUID uuid) {
        String playerRank = plugin.getRankManager().getPlayerRank(uuid);
        return getMine(playerRank);
    }

    /**
     * Obtient la mine la plus haute accessible par un joueur.
     */
    @Nullable
    public Mine getHighestAccessibleMine(@NotNull IslandiumPlayer player) {
        return getHighestAccessibleMine(player.getUniqueId());
    }

    // === Mine Reset ===

    /**
     * Retourne l'intervalle de reset effectif en minutes pour une mine.
     * Utilise la valeur de la mine si > 0, sinon la valeur globale de la config.
     */
    public int getEffectiveResetInterval(@NotNull Mine mine) {
        int mineInterval = mine.getResetIntervalMinutes();
        return mineInterval > 0 ? mineInterval : plugin.getConfig().getMineResetInterval();
    }

    /**
     * Calcule le temps restant en secondes avant le prochain check de reset.
     * Retourne -1 si la mine n'a pas d'auto-reset actif.
     */
    public long getSecondsUntilNextCheck(@NotNull Mine mine) {
        if (!mine.isAutoReset() || !mine.isConfigured()) return -1;

        long intervalMs = getEffectiveResetInterval(mine) * 60L * 1000L;
        long elapsed = System.currentTimeMillis() - mine.getLastResetTime();
        long remaining = intervalMs - elapsed;

        if (remaining <= 0) return 0;
        return remaining / 1000;
    }

    /**
     * Planifie le reset automatique d'une mine.
     */
    private void scheduleReset(@NotNull Mine mine) {
        if (!mine.isAutoReset() || !mine.isConfigured()) return;

        cancelResetTask(mine.getId());

        int interval = getEffectiveResetInterval(mine);
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(
                () -> checkAndResetMine(mine),
                interval,
                interval,
                TimeUnit.MINUTES
        );

        resetTasks.put(mine.getId().toLowerCase(), task);
    }

    private void cancelResetTask(String mineId) {
        ScheduledFuture<?> task = resetTasks.remove(mineId.toLowerCase());
        if (task != null) {
            task.cancel(false);
        }
    }

    /**
     * Vérifie et reset une mine.
     * Le reset se déclenche toujours quand le timer expire (scheduleAtFixedRate).
     */
    private void checkAndResetMine(@NotNull Mine mine) {
        // Broadcast warning
        if (plugin.getConfig().shouldBroadcastResetWarning()) {
            int warningSeconds = plugin.getConfig().getWarningSecondsBeforeReset();
            broadcastResetWarning(mine, warningSeconds);

            // Schedule actual reset after warning delay
            scheduler.schedule(() -> resetMine(mine), warningSeconds, TimeUnit.SECONDS);
        } else {
            resetMine(mine);
        }
    }

    /**
     * Reset une mine immédiatement.
     * Téléporte tous les joueurs dans la mine au spawn, attend 1 seconde, puis remplit les blocs.
     */
    public void resetMine(@NotNull Mine mine) {
        if (!mine.isConfigured()) {
            plugin.log(Level.WARNING, "Cannot reset mine " + mine.getId() + ": not configured");
            return;
        }

        plugin.log(Level.INFO, "Resetting mine: " + mine.getId());

        // Téléporter tous les joueurs dans la mine vers le spawn
        teleportMinePlayers(mine);

        // Attendre 1 seconde puis remplir les blocs
        scheduler.schedule(() -> {
            // Mettre à jour l'état
            mine.resetState();
            saveAll();

            // Remplir les blocs en full async
            fillMineBlocksAsync(mine).thenAccept(count -> {
                plugin.log(Level.INFO, "Mine " + mine.getId() + " reset complete: " + count + " blocks placed");
                String message = plugin.getConfig().getPrefixedMessage("mine.reset", "mine", mine.getDisplayName());
                broadcastToMinePlayers(mine, message);
            });
        }, 1, TimeUnit.SECONDS);
    }

    /**
     * Vide une mine (détruit tous les blocs de la zone).
     */
    public void clearMine(@NotNull Mine mine) {
        if (!mine.isConfigured()) {
            plugin.log(Level.WARNING, "Cannot clear mine " + mine.getId() + ": not configured");
            return;
        }

        plugin.log(Level.INFO, "Clearing mine zone: " + mine.getId());

        // Mettre à jour l'état immédiatement (mine vidée = 0 blocs restants)
        mine.setRemainingBlocks(0);
        saveAll();

        clearMineAsync(mine).thenAccept(count -> {
            plugin.log(Level.INFO, "Mine " + mine.getId() + " clear complete: " + count + " blocks cleared");
        });
    }

    // ============================================
    // ASYNC OPERATIONS (même pattern que BlockOperations du plugin edit)
    // ============================================

    private static final int BLOCKS_PER_BATCH = 5000;
    private static final int BATCH_DELAY_MS = 50;

    /**
     * Vide une mine en full async. Retourne un CompletableFuture avec le nombre de blocs.
     */
    private CompletableFuture<Integer> clearMineAsync(@NotNull Mine mine) {
        plugin.log(Level.INFO, "[DEBUG-CLEAR] clearMineAsync START for mine " + mine.getId());

        // Calculer les positions
        List<int[]> positions = computeMinePositions(mine);
        plugin.log(Level.INFO, "[DEBUG-CLEAR] positions count=" + positions.size());
        if (positions.isEmpty()) {
            plugin.log(Level.WARNING, "[DEBUG-CLEAR] ABORT: positions empty!");
            return CompletableFuture.completedFuture(0);
        }

        // Récupérer le monde
        World world = getMineWorld(mine);
        plugin.log(Level.INFO, "[DEBUG-CLEAR] world=" + (world != null ? world.toString() : "NULL"));
        if (world == null) {
            plugin.log(Level.WARNING, "[DEBUG-CLEAR] ABORT: world not found for mine " + mine.getId());
            return CompletableFuture.completedFuture(0);
        }

        // Exécuter le placement en batch async
        plugin.log(Level.INFO, "[DEBUG-CLEAR] Starting processBlocksInBatches with blockType=air");
        return processBlocksInBatches(world, positions, "air", mine.getId(), "clear");
    }

    /**
     * Remplit une mine en full async. Retourne un CompletableFuture avec le nombre de blocs.
     */
    private CompletableFuture<Integer> fillMineBlocksAsync(@NotNull Mine mine) {
        plugin.log(Level.INFO, "[DEBUG-FILL] fillMineBlocksAsync START for mine " + mine.getId());
        // Tout en async via le scheduler
        CompletableFuture<Integer> future = new CompletableFuture<>();

        scheduler.schedule(() -> {
            try {
                plugin.log(Level.INFO, "[DEBUG-FILL] scheduler task started for mine " + mine.getId());

                // Calculer les positions
                List<int[]> positions = computeMinePositions(mine);
                plugin.log(Level.INFO, "[DEBUG-FILL] positions count=" + positions.size());
                if (positions.isEmpty()) {
                    plugin.log(Level.WARNING, "[DEBUG-FILL] ABORT: positions empty!");
                    future.complete(0);
                    return;
                }

                // Récupérer le monde
                World world = getMineWorld(mine);
                plugin.log(Level.INFO, "[DEBUG-FILL] world=" + (world != null ? world.toString() : "NULL"));
                if (world == null) {
                    plugin.log(Level.WARNING, "[DEBUG-FILL] ABORT: world not found for mine " + mine.getId());
                    future.complete(0);
                    return;
                }

                // Pré-générer les blocs par couche
                int height = computeMineHeight(mine);
                int blocksPerLayer = computeBlocksPerLayer(mine);
                plugin.log(Level.INFO, "[DEBUG-FILL] height=" + height + " blocksPerLayer=" + blocksPerLayer);
                Map<Integer, List<String>> preGeneratedBlocks = preGenerateBlocksForMine(mine, height, blocksPerLayer);
                plugin.log(Level.INFO, "[DEBUG-FILL] preGenerated layers=" + preGeneratedBlocks.size());

                // Assigner un type de bloc à chaque position
                List<String> blockTypes = assignBlockTypes(mine, positions, preGeneratedBlocks);
                plugin.log(Level.INFO, "[DEBUG-FILL] blockTypes assigned=" + blockTypes.size() + " (first 3: " + blockTypes.subList(0, Math.min(3, blockTypes.size())) + ")");

                plugin.log(Level.INFO, "[DEBUG-FILL] Filling mine " + mine.getId() + ": " + positions.size() + " blocks");

                // Exécuter le placement en batch async
                processBlocksWithTypeInBatches(world, positions, blockTypes, mine.getId())
                        .thenAccept(count -> {
                            plugin.log(Level.INFO, "[DEBUG-FILL] processBlocksWithTypeInBatches complete: " + count + " blocks placed");
                            future.complete(count);
                        });
            } catch (Exception e) {
                plugin.log(Level.SEVERE, "[DEBUG-FILL] Error preparing fill for mine " + mine.getId() + ": " + e.getMessage());
                e.printStackTrace();
                future.complete(0);
            }
        }, 0, TimeUnit.MILLISECONDS);

        return future;
    }

    /**
     * Traite des blocs en batches (même type pour tous). Exactement comme BlockOperations.
     */
    private CompletableFuture<Integer> processBlocksInBatches(@NotNull World world,
                                                               @NotNull List<int[]> positions,
                                                               @NotNull String blockType,
                                                               @NotNull String mineId,
                                                               @NotNull String operation) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        int total = positions.size();
        int[] processed = {0};
        int[] failed = {0};

        List<List<int[]>> batches = partition(positions, BLOCKS_PER_BATCH);
        plugin.log(Level.INFO, "[DEBUG-BATCH][" + operation + "] Mine " + mineId + ": " + total + " blocks in " + batches.size() + " batches, blockType=" + blockType);

        for (int i = 0; i < batches.size(); i++) {
            List<int[]> batch = batches.get(i);
            int delay = i * BATCH_DELAY_MS;
            final int batchIndex = i;

            scheduler.schedule(() -> {
                plugin.log(Level.INFO, "[DEBUG-BATCH][" + operation + "] Batch " + batchIndex + " scheduled, executing on world thread...");
                world.execute(() -> {
                    plugin.log(Level.INFO, "[DEBUG-BATCH][" + operation + "] Batch " + batchIndex + " EXECUTING " + batch.size() + " blocks");
                    int batchSuccess = 0;
                    int batchFail = 0;
                    for (int[] pos : batch) {
                        try {
                            // "air" n'est pas un bloc valide pour setBlock, utiliser breakBlock
                            if ("air".equalsIgnoreCase(blockType)) {
                                world.breakBlock(pos[0], pos[1], pos[2], 0);
                            } else {
                                world.setBlock(pos[0], pos[1], pos[2], blockType);
                            }
                            processed[0]++;
                            batchSuccess++;
                        } catch (Exception e) {
                            failed[0]++;
                            batchFail++;
                            if (batchFail <= 3) {
                                plugin.log(Level.WARNING, "[DEBUG-BATCH][" + operation + "] setBlock FAILED at " + pos[0] + "," + pos[1] + "," + pos[2] + " type=" + blockType + " error=" + e.getMessage());
                            }
                        }
                    }
                    plugin.log(Level.INFO, "[DEBUG-BATCH][" + operation + "] Batch " + batchIndex + " done: success=" + batchSuccess + " fail=" + batchFail + " total_processed=" + processed[0] + "/" + total);

                    if (processed[0] + failed[0] >= total) {
                        if (failed[0] > 0) {
                            plugin.log(Level.WARNING, "[" + operation + "] Mine " + mineId + ": " + failed[0] + " blocks failed");
                        }
                        future.complete(processed[0]);
                    }
                });
            }, delay, TimeUnit.MILLISECONDS);
        }

        return future;
    }

    /**
     * Traite des blocs en batches (type différent par position). Exactement comme BlockOperations.
     */
    private CompletableFuture<Integer> processBlocksWithTypeInBatches(@NotNull World world,
                                                                       @NotNull List<int[]> positions,
                                                                       @NotNull List<String> blockTypes,
                                                                       @NotNull String mineId) {
        CompletableFuture<Integer> future = new CompletableFuture<>();
        int total = positions.size();
        int[] processed = {0};
        int[] failed = {0};

        // Créer les batches (indices)
        int batchCount = (total + BLOCKS_PER_BATCH - 1) / BLOCKS_PER_BATCH;
        plugin.log(Level.INFO, "[fill] Mine " + mineId + ": " + total + " blocks in " + batchCount + " batches");

        for (int i = 0; i < batchCount; i++) {
            final int startIdx = i * BLOCKS_PER_BATCH;
            final int endIdx = Math.min(startIdx + BLOCKS_PER_BATCH, total);
            int delay = i * BATCH_DELAY_MS;

            scheduler.schedule(() -> {
                world.execute(() -> {
                    for (int idx = startIdx; idx < endIdx; idx++) {
                        int[] pos = positions.get(idx);
                        String blockType = blockTypes.get(idx);
                        try {
                            // "air" n'est pas un bloc valide pour setBlock, utiliser breakBlock
                            if ("air".equalsIgnoreCase(blockType)) {
                                world.breakBlock(pos[0], pos[1], pos[2], 0);
                            } else {
                                world.setBlock(pos[0], pos[1], pos[2], blockType);
                            }
                            processed[0]++;
                        } catch (Exception e) {
                            try { world.setBlock(pos[0], pos[1], pos[2], "stone"); } catch (Exception ignored) {}
                            failed[0]++;
                        }
                    }

                    if (processed[0] + failed[0] >= total) {
                        if (failed[0] > 0) {
                            plugin.log(Level.WARNING, "[fill] Mine " + mineId + ": " + failed[0] + " blocks failed");
                        }
                        future.complete(processed[0]);
                    }
                });
            }, delay, TimeUnit.MILLISECONDS);
        }

        return future;
    }

    // ============================================
    // HELPERS pour calculer les positions de la mine
    // ============================================

    /**
     * Calcule toutes les positions (x,y,z) d'une mine (cuboid ou cylindre).
     */
    private List<int[]> computeMinePositions(@NotNull Mine mine) {
        if (mine.isCylindrical()) {
            return computeCylinderPositions(mine);
        } else {
            return computeCuboidPositions(mine);
        }
    }

    private List<int[]> computeCuboidPositions(@NotNull Mine mine) {
        ServerLocation c1 = mine.getCorner1();
        ServerLocation c2 = mine.getCorner2();
        if (c1 == null || c2 == null) return Collections.emptyList();

        int minX = (int) Math.min(c1.x(), c2.x());
        int maxX = (int) Math.max(c1.x(), c2.x());
        int minY = (int) Math.min(c1.y(), c2.y());
        int maxY = (int) Math.max(c1.y(), c2.y());
        int minZ = (int) Math.min(c1.z(), c2.z());
        int maxZ = (int) Math.max(c1.z(), c2.z());

        List<int[]> positions = new ArrayList<>();
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    positions.add(new int[]{x, y, z});
                }
            }
        }
        return positions;
    }

    private List<int[]> computeCylinderPositions(@NotNull Mine mine) {
        ServerLocation center = mine.getCenter();
        int radius = mine.getRadius();
        int height = mine.getHeight();
        if (center == null || radius <= 0 || height <= 0) return Collections.emptyList();

        int cx = (int) Math.floor(center.x());
        int cy = (int) Math.floor(center.y());
        int cz = (int) Math.floor(center.z());

        int halfW = radius;
        int halfH = radius;
        double radiusAdjust = mine.getRadiusAdjust();
        double rX = halfW + radiusAdjust;
        double rZ = halfH + radiusAdjust;
        double rXSq = rX * rX;
        double rZSq = rZ * rZ;

        List<int[]> positions = new ArrayList<>();
        for (int layerOffset = 0; layerOffset < height; layerOffset++) {
            int y = cy + layerOffset;
            for (int dz = -halfH; dz <= halfH; dz++) {
                for (int dx = -halfW; dx <= halfW; dx++) {
                    double distSq = (dx * dx) / rXSq + (dz * dz) / rZSq;
                    if (distSq < 1.0) {
                        positions.add(new int[]{cx + dx, y, cz + dz});
                    }
                }
            }
        }
        return positions;
    }

    private int computeMineHeight(@NotNull Mine mine) {
        if (mine.isCylindrical()) {
            return mine.getHeight();
        }
        ServerLocation c1 = mine.getCorner1();
        ServerLocation c2 = mine.getCorner2();
        if (c1 == null || c2 == null) return 0;
        return (int) Math.abs(c2.y() - c1.y()) + 1;
    }

    private int computeBlocksPerLayer(@NotNull Mine mine) {
        if (mine.isCylindrical()) {
            int radius = mine.getRadius();
            double radiusAdjust = mine.getRadiusAdjust();
            double rX = radius + radiusAdjust;
            double rZ = radius + radiusAdjust;
            double rXSq = rX * rX;
            double rZSq = rZ * rZ;
            int count = 0;
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    if ((dx * dx) / rXSq + (dz * dz) / rZSq < 1.0) count++;
                }
            }
            return count;
        }
        ServerLocation c1 = mine.getCorner1();
        ServerLocation c2 = mine.getCorner2();
        if (c1 == null || c2 == null) return 0;
        int widthX = (int) Math.abs(c2.x() - c1.x()) + 1;
        int widthZ = (int) Math.abs(c2.z() - c1.z()) + 1;
        return widthX * widthZ;
    }

    private int computeBaseY(@NotNull Mine mine) {
        if (mine.isCylindrical()) {
            ServerLocation center = mine.getCenter();
            return center != null ? (int) Math.floor(center.y()) : 0;
        }
        ServerLocation c1 = mine.getCorner1();
        ServerLocation c2 = mine.getCorner2();
        if (c1 == null || c2 == null) return 0;
        return (int) Math.min(c1.y(), c2.y());
    }

    /**
     * Assigne un type de bloc à chaque position selon les blocs pré-générés.
     */
    private List<String> assignBlockTypes(@NotNull Mine mine,
                                            @NotNull List<int[]> positions,
                                            @NotNull Map<Integer, List<String>> preGeneratedBlocks) {
        int baseY = computeBaseY(mine);
        List<String> blockTypes = new ArrayList<>(positions.size());

        // Compteur par layer
        Map<Integer, Integer> layerCounters = new HashMap<>();

        for (int[] pos : positions) {
            int layer = pos[1] - baseY;
            int idx = layerCounters.getOrDefault(layer, 0);
            layerCounters.put(layer, idx + 1);

            List<String> layerBlocks = preGeneratedBlocks.get(layer);
            String blockType = (layerBlocks != null && idx < layerBlocks.size())
                    ? layerBlocks.get(idx) : "stone";
            blockTypes.add(blockType);
        }

        return blockTypes;
    }

    @Nullable
    private World getMineWorld(@NotNull Mine mine) {
        plugin.log(Level.INFO, "[DEBUG-WORLD] getMineWorld for " + mine.getId() + " cylindrical=" + mine.isCylindrical());
        if (mine.isCylindrical()) {
            ServerLocation center = mine.getCenter();
            plugin.log(Level.INFO, "[DEBUG-WORLD] center=" + (center != null ? center.world() + " @ " + center.x() + "," + center.y() + "," + center.z() : "NULL"));
            World w = center != null ? getWorldFromLocation(center) : null;
            plugin.log(Level.INFO, "[DEBUG-WORLD] world result=" + (w != null ? w.toString() : "NULL"));
            return w;
        }
        ServerLocation c1 = mine.getCorner1();
        plugin.log(Level.INFO, "[DEBUG-WORLD] corner1=" + (c1 != null ? c1.world() + " @ " + c1.x() + "," + c1.y() + "," + c1.z() : "NULL"));
        World w = c1 != null ? getWorldFromLocation(c1) : null;
        plugin.log(Level.INFO, "[DEBUG-WORLD] world result=" + (w != null ? w.toString() : "NULL"));
        return w;
    }

    private <T> List<List<T>> partition(@NotNull List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }

    /**
     * Pré-génère les blocs pour toute la mine en respectant les pourcentages globaux et les limites de couches.
     * @param mine La mine
     * @param height Nombre de couches
     * @param blocksPerLayer Nombre de blocs par couche
     * @return Map layer -> liste des blocs pré-générés pour cette couche
     */
    private Map<Integer, List<String>> preGenerateBlocksForMine(Mine mine, int height, int blocksPerLayer) {
        Random random = new Random();
        Map<Integer, List<String>> result = new HashMap<>();

        plugin.log(Level.INFO, "[DEBUG-PREGEN] START height=" + height + " blocksPerLayer=" + blocksPerLayer + " total=" + (height * blocksPerLayer));

        for (int layer = 0; layer < height; layer++) {
            // Obtenir la composition filtrée pour cette couche (avec limites de couches + blocs désactivés)
            Map<String, Double> layerComp = mine.getActiveCompositionForLayer(layer);
            if (layerComp.isEmpty()) {
                layerComp = Map.of("stone", 100.0);
            }

            // Normaliser les pourcentages pour cette couche
            double totalPercent = layerComp.values().stream().mapToDouble(Double::doubleValue).sum();
            if (totalPercent <= 0) {
                totalPercent = 100.0;
            }

            // Calculer le nombre exact de blocs par type pour cette couche
            List<String> blockTypes = new ArrayList<>(layerComp.keySet());
            int[] counts = new int[blockTypes.size()];
            int assigned = 0;

            for (int i = 0; i < blockTypes.size(); i++) {
                double percent = layerComp.get(blockTypes.get(i));
                if (i == blockTypes.size() - 1) {
                    counts[i] = blocksPerLayer - assigned;
                } else {
                    counts[i] = (int) Math.round((percent / totalPercent) * blocksPerLayer);
                }
                assigned += counts[i];
            }

            // Créer la liste de blocs pour cette couche
            List<String> layerBlocks = new ArrayList<>(blocksPerLayer);
            for (int i = 0; i < blockTypes.size(); i++) {
                String blockType = blockTypes.get(i);
                for (int j = 0; j < counts[i]; j++) {
                    layerBlocks.add(blockType);
                }
            }

            // Mélanger pour randomiser la distribution dans la couche
            Collections.shuffle(layerBlocks, random);

            result.put(layer, layerBlocks);
        }

        plugin.log(Level.INFO, "[DEBUG-PREGEN] DONE layers=" + result.size());
        return result;
    }

    /**
     * Remplit une couche du cylindre (méthode legacy, gardée pour compatibilité).
     * Utilise une grille (2*radius+1) x (2*radius+1) avec formule d'ellipse ajustée.
     *
     * Pour un rayon R, la grille va de -R à +R (soit 2R+1 positions).
     * halfW = halfH = R
     * radiusAdjust = 0.40 (pour avoir 13 blocs au bord quand R=50)
     * Condition d'inclusion: (x²/(halfW+adj)²) + (z²/(halfH+adj)²) < 1
     */
    private void fillCylinderLayer(World world, Mine mine, int cx, int y, int cz, int radius, int radiusSq,
                                    int layer, Map<Integer, List<Map.Entry<String, Double>>> layerEntries, Random random) {
        List<Map.Entry<String, Double>> entries = layerEntries.computeIfAbsent(layer, l -> {
            // Utiliser getActiveComposition pour exclure les blocs désactivés
            Map<String, Double> comp = mine.getCompositionForLayer(l);
            Map<String, Double> activeComp = new HashMap<>();
            for (Map.Entry<String, Double> e : comp.entrySet()) {
                if (!mine.isBlockDisabled(e.getKey())) {
                    activeComp.put(e.getKey(), e.getValue());
                }
            }
            List<Map.Entry<String, Double>> list = new ArrayList<>(activeComp.entrySet());
            if (list.isEmpty()) {
                list.add(Map.entry("stone", 100.0));
            }
            return list;
        });

        // Grille (2*radius+1) x (2*radius+1), de -radius à +radius
        int halfW = radius;
        int halfH = radius;
        double radiusAdjust = 0.40;
        double rX = halfW + radiusAdjust;
        double rZ = halfH + radiusAdjust;
        double rXSq = rX * rX;
        double rZSq = rZ * rZ;

        boolean logged = false;
        // Parcourir la grille de -radius à +radius
        for (int dz = -halfH; dz <= halfH; dz++) {
            for (int dx = -halfW; dx <= halfW; dx++) {
                // Formule de l'ellipse: (x²/rX²) + (z²/rZ²) < 1
                double distSq = (dx * dx) / rXSq + (dz * dz) / rZSq;
                if (distSq >= 1.0) continue; // Hors du cercle

                int x = cx + dx;
                int z = cz + dz;
                String blockType = selectRandomBlock(entries, random);
                if (!logged) {
                    plugin.log(Level.INFO, "[Mine " + mine.getId() + "] Trying to set block: " + blockType);
                    logged = true;
                }
                try {
                    // "air" n'est pas un bloc valide pour setBlock, utiliser breakBlock
                    if ("air".equalsIgnoreCase(blockType)) {
                        world.breakBlock(x, y, z, 0);
                    } else {
                        world.setBlock(x, y, z, blockType);
                    }
                } catch (Exception e) {
                    try {
                        world.setBlock(x, y, z, "stone");
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    /**
     * Remplit une bande d'une couche de la mine.
     */
    private void fillLayerStrip(World world, Mine mine, int minX, int maxX, int y, int minZ, int maxZ,
                                 int layer, Map<Integer, List<Map.Entry<String, Double>>> layerEntries, Random random) {
        // Obtenir la composition pour ce layer (exclure les blocs désactivés)
        List<Map.Entry<String, Double>> entries = layerEntries.computeIfAbsent(layer, l -> {
            Map<String, Double> comp = mine.getCompositionForLayer(l);
            Map<String, Double> activeComp = new HashMap<>();
            for (Map.Entry<String, Double> e : comp.entrySet()) {
                if (!mine.isBlockDisabled(e.getKey())) {
                    activeComp.put(e.getKey(), e.getValue());
                }
            }
            List<Map.Entry<String, Double>> list = new ArrayList<>(activeComp.entrySet());
            // Si pas de composition, utiliser stone par défaut
            if (list.isEmpty()) {
                list.add(Map.entry("stone", 100.0));
            }
            return list;
        });

        boolean logged = false;
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                String blockType = selectRandomBlock(entries, random);
                if (!logged) {
                    plugin.log(Level.INFO, "[Mine " + mine.getId() + "] Trying to set block: " + blockType);
                    logged = true;
                }
                try {
                    // "air" n'est pas un bloc valide pour setBlock, utiliser breakBlock
                    if ("air".equalsIgnoreCase(blockType)) {
                        world.breakBlock(x, y, z, 0);
                    } else {
                        world.setBlock(x, y, z, blockType);
                    }
                } catch (Exception e) {
                    if (!logged) {
                        plugin.log(Level.WARNING, "[Mine " + mine.getId() + "] Failed to set " + blockType + ": " + e.getMessage());
                    }
                    // Bloc inconnu, utiliser stone par défaut
                    try {
                        world.setBlock(x, y, z, "stone");
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    /**
     * Obtient le World Hytale depuis une ServerLocation.
     */
    @Nullable
    private World getWorldFromLocation(@NotNull ServerLocation location) {
        Universe universe = Universe.get();
        if (universe == null) {
            plugin.log(Level.WARNING, "Universe is null!");
            return null;
        }

        String worldId = location.world();
        if (worldId == null) return null;

        // Essayer de trouver le monde par nom exact
        World world = universe.getWorld(worldId);
        if (world != null) {
            return world;
        }

        // Essayer de trouver le monde par UUID partiel (le worldId peut être juste l'UUID
        // mais la clé du monde peut être "instance-basic-UUID")
        for (Map.Entry<String, World> entry : universe.getWorlds().entrySet()) {
            if (entry.getKey().contains(worldId) || entry.getValue().getName().contains(worldId)) {
                plugin.log(Level.INFO, "Found world by partial match: " + entry.getKey());
                return entry.getValue();
            }
        }

        // Fallback: essayer le monde "prison" s'il existe (cas courant pour les mines)
        World prisonWorld = universe.getWorld("prison");
        if (prisonWorld != null) {
            plugin.log(Level.INFO, "World '" + worldId + "' not found, using 'prison' world as fallback");
            return prisonWorld;
        }

        // Debug si non trouvé
        plugin.log(Level.WARNING, "World '" + worldId + "' not found. Available worlds:");
        for (Map.Entry<String, World> entry : universe.getWorlds().entrySet()) {
            plugin.log(Level.WARNING, "  - " + entry.getKey() + " => " + entry.getValue().getName());
        }

        return null;
    }

    /**
     * Scanne les blocs d'une mine et retourne le compte par type de bloc.
     */
    @NotNull
    public Map<String, Integer> scanMineBlocks(@NotNull Mine mine) {
        Map<String, Integer> blockCounts = new HashMap<>();

        // Cylindre
        if (mine.isCylindrical()) {
            return scanCylinderBlocks(mine);
        }

        ServerLocation c1 = mine.getCorner1();
        ServerLocation c2 = mine.getCorner2();

        if (c1 == null || c2 == null) return blockCounts;

        World world = getWorldFromLocation(c1);
        if (world == null) {
            plugin.log(Level.WARNING, "Cannot scan mine " + mine.getId() + ": world not found");
            return blockCounts;
        }

        int minX = (int) Math.min(c1.x(), c2.x());
        int maxX = (int) Math.max(c1.x(), c2.x());
        int minY = (int) Math.min(c1.y(), c2.y());
        int maxY = (int) Math.max(c1.y(), c2.y());
        int minZ = (int) Math.min(c1.z(), c2.z());
        int maxZ = (int) Math.max(c1.z(), c2.z());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    var blockType = world.getBlockType(x, y, z);
                    if (blockType != null && blockType != com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType.EMPTY) {
                        String blockId = blockType.getId();
                        blockCounts.merge(blockId, 1, Integer::sum);
                    }
                }
            }
        }

        plugin.log(Level.INFO, "Scanned mine " + mine.getId() + ": found " + blockCounts.size() + " block types");
        return blockCounts;
    }

    /**
     * Scanne les blocs d'une mine cylindrique.
     * Utilise une grille (2*radius+1) x (2*radius+1) avec formule d'ellipse ajustée.
     */
    @NotNull
    private Map<String, Integer> scanCylinderBlocks(@NotNull Mine mine) {
        Map<String, Integer> blockCounts = new HashMap<>();

        ServerLocation center = mine.getCenter();
        int radius = mine.getRadius();
        int height = mine.getHeight();

        if (center == null) return blockCounts;

        World world = getWorldFromLocation(center);
        if (world == null) {
            plugin.log(Level.WARNING, "Cannot scan cylindrical mine " + mine.getId() + ": world not found");
            return blockCounts;
        }

        int cx = (int) Math.floor(center.x());
        int cy = (int) Math.floor(center.y());
        int cz = (int) Math.floor(center.z());

        // Grille (2*radius+1) x (2*radius+1), de -radius à +radius
        int halfW = radius;
        int halfH = radius;
        double radiusAdjust = 0.40;
        double rX = halfW + radiusAdjust;
        double rZ = halfH + radiusAdjust;
        double rXSq = rX * rX;
        double rZSq = rZ * rZ;

        for (int y = cy; y < cy + height; y++) {
            for (int dz = -halfH; dz <= halfH; dz++) {
                for (int dx = -halfW; dx <= halfW; dx++) {
                    // Formule de l'ellipse: (x²/rX²) + (z²/rZ²) < 1
                    double distSq = (dx * dx) / rXSq + (dz * dz) / rZSq;
                    if (distSq >= 1.0) continue; // Hors du cercle

                    var blockType = world.getBlockType(cx + dx, y, cz + dz);
                    if (blockType != null && blockType != com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType.EMPTY) {
                        String blockId = blockType.getId();
                        blockCounts.merge(blockId, 1, Integer::sum);
                    }
                }
            }
        }

        plugin.log(Level.INFO, "Scanned cylindrical mine " + mine.getId() + ": found " + blockCounts.size() + " block types");
        return blockCounts;
    }

    /**
     * Scanne les blocs d'une mine par layer et retourne le compte par type de bloc pour chaque couche.
     * @return Map<Layer, Map<BlockType, Count>>
     */
    @NotNull
    public Map<Integer, Map<String, Integer>> scanMineLayers(@NotNull Mine mine) {
        Map<Integer, Map<String, Integer>> layerBlockCounts = new HashMap<>();

        // Cylindre
        if (mine.isCylindrical()) {
            return scanCylinderLayers(mine);
        }

        ServerLocation c1 = mine.getCorner1();
        ServerLocation c2 = mine.getCorner2();

        if (c1 == null || c2 == null) return layerBlockCounts;

        World world = getWorldFromLocation(c1);
        if (world == null) {
            plugin.log(Level.WARNING, "Cannot scan mine layers " + mine.getId() + ": world not found");
            return layerBlockCounts;
        }

        int minX = (int) Math.min(c1.x(), c2.x());
        int maxX = (int) Math.max(c1.x(), c2.x());
        int minY = (int) Math.min(c1.y(), c2.y());
        int maxY = (int) Math.max(c1.y(), c2.y());
        int minZ = (int) Math.min(c1.z(), c2.z());
        int maxZ = (int) Math.max(c1.z(), c2.z());

        for (int y = minY; y <= maxY; y++) {
            int layer = y - minY; // Layer 0 = bas de la mine
            Map<String, Integer> layerCounts = layerBlockCounts.computeIfAbsent(layer, k -> new HashMap<>());

            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    var blockType = world.getBlockType(x, y, z);
                    if (blockType != null && blockType != com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType.EMPTY) {
                        String blockId = blockType.getId();
                        layerCounts.merge(blockId, 1, Integer::sum);
                    }
                }
            }
        }

        plugin.log(Level.INFO, "Scanned mine " + mine.getId() + " layers: found " + layerBlockCounts.size() + " layers");
        return layerBlockCounts;
    }

    /**
     * Scanne les blocs d'une mine cylindrique par layer.
     * Utilise une grille (2*radius+1) x (2*radius+1) avec formule d'ellipse ajustée.
     */
    @NotNull
    private Map<Integer, Map<String, Integer>> scanCylinderLayers(@NotNull Mine mine) {
        Map<Integer, Map<String, Integer>> layerBlockCounts = new HashMap<>();

        ServerLocation center = mine.getCenter();
        int radius = mine.getRadius();
        int height = mine.getHeight();

        if (center == null) return layerBlockCounts;

        World world = getWorldFromLocation(center);
        if (world == null) {
            plugin.log(Level.WARNING, "Cannot scan cylindrical mine layers " + mine.getId() + ": world not found");
            return layerBlockCounts;
        }

        int cx = (int) Math.floor(center.x());
        int cy = (int) Math.floor(center.y());
        int cz = (int) Math.floor(center.z());

        // Grille (2*radius+1) x (2*radius+1), de -radius à +radius
        int halfW = radius;
        int halfH = radius;
        double radiusAdjust = 0.40;
        double rX = halfW + radiusAdjust;
        double rZ = halfH + radiusAdjust;
        double rXSq = rX * rX;
        double rZSq = rZ * rZ;

        for (int layerOffset = 0; layerOffset < height; layerOffset++) {
            int y = cy + layerOffset;
            Map<String, Integer> layerCounts = layerBlockCounts.computeIfAbsent(layerOffset, k -> new HashMap<>());

            for (int dz = -halfH; dz <= halfH; dz++) {
                for (int dx = -halfW; dx <= halfW; dx++) {
                    // Formule de l'ellipse: (x²/rX²) + (z²/rZ²) < 1
                    double distSq = (dx * dx) / rXSq + (dz * dz) / rZSq;
                    if (distSq >= 1.0) continue; // Hors du cercle

                    var blockType = world.getBlockType(cx + dx, y, cz + dz);
                    if (blockType != null && blockType != com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType.EMPTY) {
                        String blockId = blockType.getId();
                        layerCounts.merge(blockId, 1, Integer::sum);
                    }
                }
            }
        }

        plugin.log(Level.INFO, "Scanned cylindrical mine " + mine.getId() + " layers: found " + layerBlockCounts.size() + " layers");
        return layerBlockCounts;
    }

    private String selectRandomBlock(List<Map.Entry<String, Double>> entries, Random random) {
        double total = entries.stream().mapToDouble(Map.Entry::getValue).sum();
        double roll = random.nextDouble() * total;

        double cumulative = 0;
        for (Map.Entry<String, Double> entry : entries) {
            cumulative += entry.getValue();
            if (roll <= cumulative) {
                return entry.getKey();
            }
        }

        return entries.isEmpty() ? "minecraft:stone" : entries.get(0).getKey();
    }

    /**
     * Téléporte tous les joueurs dans la mine (zone mine) vers le spawn de la mine.
     */
    private void teleportMinePlayers(@NotNull Mine mine) {
        if (!mine.hasSpawn()) return;

        ServerLocation spawn = mine.getSpawnPoint();
        for (IslandiumPlayer player : plugin.getCore().getPlayerManager().getOnlinePlayersLocal()) {
            ServerLocation loc = player.getLocation();
            if (loc != null && mine.contains(loc)) {
                plugin.getCore().getTeleportService().teleportWithWarmup(
                        player,
                        spawn,
                        () -> {}
                );
            }
        }
    }

    private void broadcastResetWarning(@NotNull Mine mine, int seconds) {
        String message = plugin.getConfig().getPrefixedMessage("mine.reset-warning",
                "mine", mine.getDisplayName(),
                "seconds", seconds
        );
        broadcastToMinePlayers(mine, message);
    }

    private void broadcastToMinePlayers(@NotNull Mine mine, @NotNull String message) {
        // Broadcast to all players in the mine
        for (IslandiumPlayer player : plugin.getCore().getPlayerManager().getOnlinePlayersLocal()) {
            ServerLocation loc = player.getLocation();
            if (loc != null && mine.contains(loc)) {
                player.sendMessage(message);
            }
        }
    }

    /**
     * Arrête le manager.
     */
    public void shutdown() {
        for (ScheduledFuture<?> task : resetTasks.values()) {
            task.cancel(false);
        }
        resetTasks.clear();
        scheduler.shutdown();
    }
}
