package com.islandium.prison.event;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.dependency.Dependency;
import com.hypixel.hytale.component.dependency.RootDependency;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.islandium.core.api.location.ServerLocation;
import com.islandium.core.api.util.NotificationType;
import com.islandium.prison.PrisonPlugin;
import com.islandium.prison.economy.SellService;
import com.islandium.prison.mine.Mine;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Système ECS pour les cassages de blocs dans les mines.
 * Gère la protection des blocs, le comptage, les récompenses et l'auto-sell.
 *
 * Utilise EntityEventSystem pour accéder directement au Player (pas de recherche par proximité).
 */
public class BreakBlockEventSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    private static final Logger LOGGER = Logger.getLogger("Prison-BreakBlock");

    // Compteur pour limiter les logs (eviter le spam)
    private long totalEvents = 0;
    private long noPluginCount = 0;
    private long emptyBlockCount = 0;
    private long noMineCount = 0;
    private long naturalModeBlockedCount = 0;
    private long noPlayerCount = 0;
    private long rankBlockedCount = 0;
    private long successCount = 0;

    public BreakBlockEventSystem() {
        super(BreakBlockEvent.class);
        LOGGER.info("[INIT] Prison BreakBlockEventSystem created!");
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull BreakBlockEvent event) {

        totalEvents++;

        // Si l'event est deja annule (ex: protection regions), on ignore
        if (event.isCancelled()) return;

        // Log unique au premier event pour confirmer que le systeme fonctionne
        if (totalEvents == 1) {
            LOGGER.info("[PRISON] Premier BreakBlock event recu! Le systeme fonctionne.");
        }

        PrisonPlugin plugin = PrisonPlugin.get();
        if (plugin == null) {
            noPluginCount++;
            if (noPluginCount == 1) LOGGER.warning("[PRISON] PrisonPlugin.get() est NULL!");
            return;
        }

        BlockType blockType = event.getBlockType();
        if (blockType == null || blockType == BlockType.EMPTY) {
            emptyBlockCount++;
            if (emptyBlockCount == 1) LOGGER.warning("[PRISON] Premier bloc EMPTY/null ignore.");
            return;
        }

        Vector3i blockPos = event.getTargetBlock();
        if (blockPos == null) {
            if (totalEvents <= 3) LOGGER.warning("[PRISON] blockPos est NULL!");
            return;
        }

        String blockId = blockType.getId();

        // Récupérer le nom du monde depuis le store ECS
        String worldName;
        try {
            var externalData = store.getExternalData();
            if (externalData instanceof EntityStore entityStore) {
                worldName = entityStore.getWorld().getName();
            } else {
                worldName = "world";
            }
        } catch (Exception e) {
            worldName = "world";
        }

        // Log unique pour le premier bloc valide
        if (emptyBlockCount + 1 == totalEvents || totalEvents <= 3) {
            LOGGER.info("[PRISON] Bloc valide: " + blockId + " world=" + worldName + " pos=(" + blockPos.getX() + "," + blockPos.getY() + "," + blockPos.getZ() + ")");
        }

        // Créer la location du bloc
        ServerLocation blockLoc = ServerLocation.of(
                plugin.getCore().getServerName(),
                worldName,
                blockPos.getX(),
                blockPos.getY(),
                blockPos.getZ(),
                0, 0
        );

        // Trouver la mine contenant ce bloc
        Mine mine = findMineAtLocation(plugin, blockLoc);

        if (mine == null) {
            noMineCount++;
            // Log detaille au premier bloc hors-mine avec infos des mines
            if (noMineCount == 1) {
                StringBuilder sb = new StringBuilder();
                sb.append("[DEBUG] Bloc HORS mine: block=").append(blockId)
                  .append(" blockWorld=world")
                  .append(" pos=(").append(blockPos.getX()).append(",").append(blockPos.getY()).append(",").append(blockPos.getZ()).append(")");
                for (Mine m : plugin.getMineManager().getAllMines()) {
                    if (m.isConfigured()) {
                        sb.append(" | mine=").append(m.getId());
                        if (m.getCorner1() != null) {
                            sb.append(" world=").append(m.getCorner1().world())
                              .append(" c1=(").append((int)m.getCorner1().x()).append(",").append((int)m.getCorner1().y()).append(",").append((int)m.getCorner1().z()).append(")")
                              .append(" c2=(").append((int)m.getCorner2().x()).append(",").append((int)m.getCorner2().y()).append(",").append((int)m.getCorner2().z()).append(")");
                        }
                    }
                }
                LOGGER.warning(sb.toString());
            }
            return;
        }

        // Mode naturel activé - vérifier si le bloc est dans la composition
        if (mine.isNaturalMode()) {
            if (!mine.isBlockInComposition(blockId)) {
                naturalModeBlockedCount++;
                event.setCancelled(true);
                return;
            }
        }

        // Bloc autorisé - décrémenter le compteur
        mine.decrementRemainingBlocks();

        // === Récompenses et stats ===
        // Récupérer le joueur directement via ECS (plus de recherche par proximité!)
        Player player = archetypeChunk.getComponent(index, Player.getComponentType());
        if (player == null) {
            noPlayerCount++;
            if (noPlayerCount <= 3) {
                LOGGER.warning("[DEBUG] Player NULL dans mine " + mine.getId() + " - blocs decrementes mais pas comptes!");
            }
            return;
        }

        UUID uuid = player.getUuid();

        // Vérifier que le joueur a accès à cette mine
        String playerRank = plugin.getRankManager().getPlayerRank(uuid);
        String mineRank = mine.getRequiredRank();
        if (!plugin.getRankManager().isRankHigherOrEqual(playerRank, mineRank)) {
            rankBlockedCount++;
            if (rankBlockedCount <= 3) {
                LOGGER.warning("[DEBUG] Rang insuffisant: joueur=" + player.getDisplayName()
                    + " rang=" + playerRank + " mine=" + mine.getId() + " rang_requis=" + mineRank);
            }
            return;
        }

        // 1. Incrémenter les stats de blocs minés
        plugin.getStatsManager().incrementBlocksMined(uuid);
        successCount++;

        if (successCount == 1) {
            LOGGER.info("[PRISON] Premier bloc mine avec succes! joueur=" + player.getDisplayName() + " mine=" + mine.getId() + " bloc=" + blockId);
        }

        // Log periodique toutes les 100 blocs mines avec succes
        if (successCount % 100 == 0) {
            LOGGER.info("[STATS] " + player.getDisplayName() + " a mine " + successCount + " blocs (total events=" + totalEvents + ")");
        }

        // 1b. Challenge tracking
        try {
            plugin.getChallengeTracker().onBlockMined(uuid, blockId);
        } catch (Exception e) {
            LOGGER.warning("[DEBUG] Challenge tracking error: " + e.getMessage());
        }

        // 2. Calculer le fortune bonus
        int fortuneLevel = plugin.getStatsManager().getFortuneLevel(uuid);
        int dropCount = calculateFortuneDrops(fortuneLevel);

        // 3. Auto-sell ou laisser dans l'inventaire
        if (plugin.getStatsManager().isAutoSellEnabled(uuid)) {
            BigDecimal earned = plugin.getSellService().autoSell(uuid, blockId, dropCount);

            if (earned.compareTo(BigDecimal.ZERO) > 0) {
                plugin.getCore().getPlayerManager().getOnlinePlayer(uuid).ifPresent(islandiumPlayer -> {
                    islandiumPlayer.sendNotification(NotificationType.SUCCESS, "+" + SellService.formatMoney(earned) + " (auto-sell)");
                });
            }
        }

        // Log resume periodique toutes les 500 events
        if (totalEvents % 500 == 0) {
            LOGGER.info("[RESUME] events=" + totalEvents
                + " success=" + successCount
                + " noMine=" + noMineCount
                + " noPlayer=" + noPlayerCount
                + " rankBlocked=" + rankBlockedCount
                + " naturalBlocked=" + naturalModeBlockedCount
                + " emptyBlock=" + emptyBlockCount);
        }
    }

    /**
     * Calcule le nombre de drops avec le bonus fortune.
     */
    private int calculateFortuneDrops(int fortuneLevel) {
        if (fortuneLevel <= 0) {
            return 1;
        }
        // Chance d'obtenir 2x drops basée sur le niveau
        // Tier 1: 10%, Tier 2: 20%, Tier 3: 30%, Tier 4: 40%, Tier 5: 50%
        double chance = fortuneLevel * 0.10;
        if (Math.random() < chance) {
            return 2;
        }
        return 1;
    }

    /**
     * Trouve la mine contenant une position donnée.
     */
    @Nullable
    private Mine findMineAtLocation(PrisonPlugin plugin, ServerLocation location) {
        for (Mine mine : plugin.getMineManager().getAllMines()) {
            if (mine.isConfigured() && mine.contains(location)) {
                return mine;
            }
        }
        return null;
    }

    @Nullable
    @Override
    public Query<EntityStore> getQuery() {
        return PlayerRef.getComponentType();
    }

    @Nonnull
    @Override
    public Set<Dependency<EntityStore>> getDependencies() {
        return Collections.singleton(RootDependency.first());
    }
}
