package com.islandium.prison.listener;

import com.hypixel.hytale.event.EventRegistry;
import com.hypixel.hytale.math.vector.Vector3d;
import com.hypixel.hytale.math.vector.Vector3i;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.ecs.BreakBlockEvent;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.islandium.core.api.IslandiumAPI;
import com.islandium.core.api.location.ServerLocation;
import com.islandium.core.api.player.IslandiumPlayer;
import com.islandium.prison.PrisonPlugin;
import com.islandium.prison.economy.SellService;
import com.islandium.prison.mine.Mine;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Listener pour les cassages de blocs dans les mines.
 * Gère la protection des blocs, le comptage, les récompenses et l'auto-sell.
 *
 * NOTE: BreakBlockEvent ne fournit pas d'accès direct au joueur.
 * Le joueur le plus proche du bloc cassé est identifié par proximity.
 */
public class BlockBreakListener extends PrisonListener {

    private static final double MAX_BREAK_DISTANCE = 8.0; // Distance max pour associer un joueur

    public BlockBreakListener(@NotNull PrisonPlugin plugin) {
        super(plugin);
    }

    @Override
    public void register(@NotNull EventRegistry registry) {
        registry.register(BreakBlockEvent.class, this::onBlockBreak);
    }

    private void onBlockBreak(BreakBlockEvent event) {
        Vector3i blockPos = event.getTargetBlock();
        if (blockPos == null) return;

        BlockType blockType = event.getBlockType();
        if (blockType == null || blockType == BlockType.EMPTY) return;

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
        Mine mine = findMineAtLocation(blockLoc);

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
        // Trouver le joueur qui a cassé ce bloc (par proximité)
        Player nearestPlayer = findNearestPlayer(blockPos);
        if (nearestPlayer == null) {
            return;
        }

        UUID uuid = nearestPlayer.getUuid();

        // Vérifier que le joueur a accès à cette mine
        String playerRank = plugin.getRankManager().getPlayerRank(uuid);
        String mineRank = mine.getRequiredRank();
        if (!plugin.getRankManager().isRankHigherOrEqual(playerRank, mineRank)) {
            // Le joueur n'a pas accès - normalement bloqué par d'autres moyens
            return;
        }

        // 1. Incrémenter les stats de blocs minés
        plugin.getStatsManager().incrementBlocksMined(uuid);

        // 2. Calculer le fortune bonus
        int fortuneLevel = plugin.getStatsManager().getFortuneLevel(uuid);
        int dropCount = calculateFortuneDrops(fortuneLevel);

        // 3. Auto-sell ou laisser dans l'inventaire
        if (plugin.getStatsManager().isAutoSellEnabled(uuid)) {
            // Auto-sell: vendre directement sans passer par l'inventaire
            BigDecimal earned = plugin.getSellService().autoSell(uuid, blockId, dropCount);

            if (earned.compareTo(BigDecimal.ZERO) > 0) {
                // Afficher un message discret en actionbar
                plugin.getCore().getPlayerManager().getOnlinePlayer(uuid).ifPresent(islandiumPlayer -> {
                    islandiumPlayer.sendMessage("&a+" + SellService.formatMoney(earned) + " &7(auto-sell)");
                });
            }
        }
        // Note: Si auto-sell désactivé, les blocs vont naturellement dans l'inventaire via le jeu
        // Le fortune bonus en mode non-auto-sell sera géré par le drop naturel du jeu
    }

    /**
     * Calcule le nombre de drops avec le bonus fortune.
     *
     * @param fortuneLevel Niveau de fortune (0-5)
     * @return Nombre de drops (1 ou 2)
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
     * Trouve le joueur le plus proche d'une position de bloc.
     * Utilise la liste des joueurs du monde.
     */
    @Nullable
    private Player findNearestPlayer(Vector3i blockPos) {
        try {
            // Obtenir le monde depuis le Universe
            World world = Universe.get().getWorlds().values().stream()
                    .findFirst()
                    .orElse(null);

            if (world == null) {
                return null;
            }

            List<Player> players = world.getPlayers();
            if (players == null || players.isEmpty()) {
                return null;
            }

            Player nearest = null;
            double nearestDist = Double.MAX_VALUE;

            for (Player player : players) {
                try {
                    PlayerRef playerRef = player.getPlayerRef();
                    if (playerRef == null) continue;

                    Transform transform = playerRef.getTransform();
                    if (transform == null) continue;

                    Vector3d pos = transform.getPosition();
                    if (pos == null) continue;

                    double dx = pos.getX() - blockPos.getX();
                    double dy = pos.getY() - blockPos.getY();
                    double dz = pos.getZ() - blockPos.getZ();
                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

                    if (dist < nearestDist && dist <= MAX_BREAK_DISTANCE) {
                        nearest = player;
                        nearestDist = dist;
                    }
                } catch (Exception ignored) {
                    // Skip players with invalid state
                }
            }

            return nearest;
        } catch (Exception e) {
            plugin.log(Level.FINE, "Error finding nearest player: " + e.getMessage());
            return null;
        }
    }

    /**
     * Trouve la mine contenant une position donnée.
     */
    @Nullable
    private Mine findMineAtLocation(ServerLocation location) {
        for (Mine mine : plugin.getMineManager().getAllMines()) {
            if (mine.isConfigured() && mine.contains(location)) {
                return mine;
            }
        }
        return null;
    }
}
