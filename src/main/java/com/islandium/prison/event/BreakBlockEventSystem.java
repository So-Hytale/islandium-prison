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

/**
 * Système ECS pour les cassages de blocs dans les mines.
 * Gère la protection des blocs, le comptage, les récompenses et l'auto-sell.
 *
 * Utilise EntityEventSystem pour accéder directement au Player (pas de recherche par proximité).
 */
public class BreakBlockEventSystem extends EntityEventSystem<EntityStore, BreakBlockEvent> {

    public BreakBlockEventSystem() {
        super(BreakBlockEvent.class);
    }

    @Override
    public void handle(int index, @Nonnull ArchetypeChunk<EntityStore> archetypeChunk,
                       @Nonnull Store<EntityStore> store, @Nonnull CommandBuffer<EntityStore> commandBuffer,
                       @Nonnull BreakBlockEvent event) {

        PrisonPlugin plugin = PrisonPlugin.get();
        if (plugin == null) return;

        BlockType blockType = event.getBlockType();
        if (blockType == null || blockType == BlockType.EMPTY) return;

        Vector3i blockPos = event.getTargetBlock();
        if (blockPos == null) return;

        String blockId = blockType.getId();

        // Créer la location du bloc
        ServerLocation blockLoc = ServerLocation.of(
                plugin.getCore().getServerName(),
                "world",
                blockPos.getX(),
                blockPos.getY(),
                blockPos.getZ(),
                0, 0
        );

        // Trouver la mine contenant ce bloc
        Mine mine = findMineAtLocation(plugin, blockLoc);

        if (mine == null) {
            // Pas dans une mine - laisser passer
            return;
        }

        // Mode naturel activé - vérifier si le bloc est dans la composition
        if (mine.isNaturalMode()) {
            if (!mine.isBlockInComposition(blockId)) {
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
            return;
        }

        UUID uuid = player.getUuid();

        // Vérifier que le joueur a accès à cette mine
        String playerRank = plugin.getRankManager().getPlayerRank(uuid);
        String mineRank = mine.getRequiredRank();
        if (!plugin.getRankManager().isRankHigherOrEqual(playerRank, mineRank)) {
            return;
        }

        // 1. Incrémenter les stats de blocs minés
        plugin.getStatsManager().incrementBlocksMined(uuid);

        // 1b. Challenge tracking
        try {
            plugin.getChallengeTracker().onBlockMined(uuid, blockId);
        } catch (Exception e) {
            // Ne jamais laisser le challenge tracking casser le flux principal
        }

        // 2. Calculer le fortune bonus
        int fortuneLevel = plugin.getStatsManager().getFortuneLevel(uuid);
        int dropCount = calculateFortuneDrops(fortuneLevel);

        // 3. Auto-sell ou laisser dans l'inventaire
        if (plugin.getStatsManager().isAutoSellEnabled(uuid)) {
            BigDecimal earned = plugin.getSellService().autoSell(uuid, blockId, dropCount);

            if (earned.compareTo(BigDecimal.ZERO) > 0) {
                plugin.getCore().getPlayerManager().getOnlinePlayer(uuid).ifPresent(islandiumPlayer -> {
                    islandiumPlayer.sendMessage("&a+" + SellService.formatMoney(earned) + " &7(auto-sell)");
                });
            }
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
