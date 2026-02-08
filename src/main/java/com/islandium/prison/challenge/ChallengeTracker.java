package com.islandium.prison.challenge;

import com.islandium.core.api.IslandiumAPI;
import com.islandium.core.api.economy.EconomyService;
import com.islandium.prison.PrisonPlugin;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Traque les evenements du jeu et met a jour la progression des challenges.
 * Appele directement depuis les systemes existants (BreakBlockEventSystem, SellService, etc.).
 */
public class ChallengeTracker {

    private final PrisonPlugin plugin;
    private final ChallengeManager challengeManager;

    // Cache du rang par joueur pour eviter des lookups frequents
    private final Map<UUID, String> rankCache = new ConcurrentHashMap<>();

    public ChallengeTracker(@NotNull PrisonPlugin plugin, @NotNull ChallengeManager challengeManager) {
        this.plugin = plugin;
        this.challengeManager = challengeManager;
    }

    /**
     * Invalide le cache de rang pour un joueur (appeler apres un rankup).
     */
    public void invalidateRankCache(@NotNull UUID uuid) {
        rankCache.remove(uuid);
    }

    private String getCachedRank(@NotNull UUID uuid) {
        return rankCache.computeIfAbsent(uuid, k -> plugin.getRankManager().getPlayerRank(k));
    }

    // ===========================
    // Block Mining
    // ===========================

    /**
     * Appele quand un joueur mine un bloc.
     */
    public void onBlockMined(@NotNull UUID uuid, @NotNull String blockId) {
        String rankId = getCachedRank(uuid);
        List<ChallengeDefinition> challenges = ChallengeRegistry.getChallengesForRank(rankId);

        for (ChallengeDefinition def : challenges) {
            switch (def.getType()) {
                case MINE_BLOCKS -> challengeManager.incrementProgress(uuid, def.getId(), 1);
                case MINE_SPECIFIC -> {
                    if (blockId.equals(def.getTargetBlockId())) {
                        challengeManager.incrementProgress(uuid, def.getId(), 1);
                    }
                }
                default -> {}
            }
        }
    }

    // ===========================
    // Selling
    // ===========================

    /**
     * Appele quand un joueur vend des items (via /sell ou le menu).
     */
    public void onItemsSold(@NotNull UUID uuid, int count, @NotNull BigDecimal earned) {
        String rankId = getCachedRank(uuid);
        List<ChallengeDefinition> challenges = ChallengeRegistry.getChallengesForRank(rankId);

        for (ChallengeDefinition def : challenges) {
            switch (def.getType()) {
                case SELL_ITEMS -> challengeManager.incrementProgress(uuid, def.getId(), count);
                case EARN_MONEY -> challengeManager.incrementProgress(uuid, def.getId(), earned.longValue());
                default -> {}
            }
        }

        // Verifier aussi le challenge ACCUMULATE_BALANCE
        checkBalanceChallenge(uuid, rankId, challenges);
    }

    /**
     * Appele quand un joueur gagne de l'argent (auto-sell).
     */
    public void onMoneyEarned(@NotNull UUID uuid, @NotNull BigDecimal amount) {
        String rankId = getCachedRank(uuid);
        List<ChallengeDefinition> challenges = ChallengeRegistry.getChallengesForRank(rankId);

        for (ChallengeDefinition def : challenges) {
            if (def.getType() == ChallengeType.EARN_MONEY) {
                challengeManager.incrementProgress(uuid, def.getId(), amount.longValue());
            }
        }

        // Verifier ACCUMULATE_BALANCE
        checkBalanceChallenge(uuid, rankId, challenges);
    }

    // ===========================
    // Upgrades
    // ===========================

    /**
     * Appele quand un joueur achete un upgrade.
     */
    public void onUpgradePurchased(@NotNull UUID uuid, @NotNull ChallengeType upgradeType, int newLevel, @NotNull BigDecimal cost) {
        String rankId = getCachedRank(uuid);
        List<ChallengeDefinition> challenges = ChallengeRegistry.getChallengesForRank(rankId);

        for (ChallengeDefinition def : challenges) {
            if (def.getType() == upgradeType) {
                // Pour les upgrades, la "valeur" est le niveau atteint
                challengeManager.setProgress(uuid, def.getId(), newLevel);
            }
            if (def.getType() == ChallengeType.SPEND_MONEY) {
                challengeManager.incrementProgress(uuid, def.getId(), cost.longValue());
            }
        }
    }

    /**
     * Appele quand un joueur depense de l'argent (rankup, upgrades).
     */
    public void onMoneySpent(@NotNull UUID uuid, @NotNull BigDecimal amount) {
        String rankId = getCachedRank(uuid);
        List<ChallengeDefinition> challenges = ChallengeRegistry.getChallengesForRank(rankId);

        for (ChallengeDefinition def : challenges) {
            if (def.getType() == ChallengeType.SPEND_MONEY) {
                challengeManager.incrementProgress(uuid, def.getId(), amount.longValue());
            }
        }
    }

    // ===========================
    // Balance Check
    // ===========================

    private void checkBalanceChallenge(@NotNull UUID uuid, @NotNull String rankId,
                                        @NotNull List<ChallengeDefinition> challenges) {
        // Verifier si on a des challenges ACCUMULATE_BALANCE avant de faire l'appel eco
        boolean hasBalanceChallenge = false;
        for (ChallengeDefinition def : challenges) {
            if (def.getType() == ChallengeType.ACCUMULATE_BALANCE) {
                hasBalanceChallenge = true;
                break;
            }
        }
        if (!hasBalanceChallenge) return;

        EconomyService eco = getEconomyService();
        if (eco == null) return;

        // Non-bloquant : on ne fait PAS .join() pour ne pas bloquer le thread ECS
        try {
            eco.getBalance(uuid).thenAccept(balance -> {
                try {
                    for (ChallengeDefinition def : challenges) {
                        if (def.getType() == ChallengeType.ACCUMULATE_BALANCE) {
                            challengeManager.setProgress(uuid, def.getId(), balance.longValue());
                        }
                    }
                } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
    }

    private EconomyService getEconomyService() {
        IslandiumAPI api = IslandiumAPI.get();
        return api != null ? api.getEconomyService() : null;
    }
}
