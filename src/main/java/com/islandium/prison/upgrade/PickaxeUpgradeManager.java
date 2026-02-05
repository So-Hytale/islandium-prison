package com.islandium.prison.upgrade;

import com.islandium.core.api.IslandiumAPI;
import com.islandium.core.api.economy.EconomyService;
import com.islandium.prison.PrisonPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Gestionnaire des upgrades de pioche.
 * Types : FORTUNE, EFFICIENCY, AUTO_SELL
 */
public class PickaxeUpgradeManager {

    private final PrisonPlugin plugin;

    // Fortune tiers: prix pour passer au niveau suivant (5 niveaux)
    private static final BigDecimal[] FORTUNE_PRICES = {
            new BigDecimal("5000"),     // Tier 0 -> 1 : 10% chance 2x
            new BigDecimal("15000"),    // Tier 1 -> 2 : 20% chance 2x
            new BigDecimal("50000"),    // Tier 2 -> 3 : 30% chance 2x
            new BigDecimal("150000"),   // Tier 3 -> 4 : 40% chance 2x
            new BigDecimal("500000")    // Tier 4 -> 5 : 50% chance 2x
    };

    // Efficiency tiers: prix pour passer au niveau suivant (5 niveaux)
    private static final BigDecimal[] EFFICIENCY_PRICES = {
            new BigDecimal("3000"),     // Tier 0 -> 1
            new BigDecimal("10000"),    // Tier 1 -> 2
            new BigDecimal("30000"),    // Tier 2 -> 3
            new BigDecimal("100000"),   // Tier 3 -> 4
            new BigDecimal("300000")    // Tier 4 -> 5
    };

    // Auto-sell: achat unique
    private static final BigDecimal AUTO_SELL_PRICE = new BigDecimal("100000");

    public static final int MAX_FORTUNE_LEVEL = 5;
    public static final int MAX_EFFICIENCY_LEVEL = 5;

    public PickaxeUpgradeManager(@NotNull PrisonPlugin plugin) {
        this.plugin = plugin;
    }

    // ===========================
    // Fortune
    // ===========================

    /**
     * Obtient le prix pour le prochain tier de fortune.
     * @return Le prix, ou null si déjà au max.
     */
    @Nullable
    public BigDecimal getFortuneNextPrice(@NotNull UUID uuid) {
        int level = plugin.getStatsManager().getFortuneLevel(uuid);
        if (level >= MAX_FORTUNE_LEVEL) {
            return null;
        }
        return FORTUNE_PRICES[level];
    }

    /**
     * Obtient la description du tier de fortune.
     */
    @NotNull
    public String getFortuneDescription(int level) {
        if (level <= 0) return "&7Pas d'enchantement";
        return "&a" + (level * 10) + "% &7chance de &e2x drops";
    }

    /**
     * Achète le prochain tier de fortune.
     */
    @NotNull
    public UpgradeResult purchaseFortune(@NotNull UUID uuid) {
        int level = plugin.getStatsManager().getFortuneLevel(uuid);

        if (level >= MAX_FORTUNE_LEVEL) {
            return UpgradeResult.MAX_LEVEL;
        }

        BigDecimal price = FORTUNE_PRICES[level];
        if (!deductBalance(uuid, price)) {
            return UpgradeResult.NOT_ENOUGH_MONEY;
        }

        plugin.getStatsManager().setFortuneLevel(uuid, level + 1);
        return UpgradeResult.SUCCESS;
    }

    // ===========================
    // Efficiency
    // ===========================

    /**
     * Obtient le prix pour le prochain tier d'efficacité.
     */
    @Nullable
    public BigDecimal getEfficiencyNextPrice(@NotNull UUID uuid) {
        int level = plugin.getStatsManager().getEfficiencyLevel(uuid);
        if (level >= MAX_EFFICIENCY_LEVEL) {
            return null;
        }
        return EFFICIENCY_PRICES[level];
    }

    /**
     * Obtient la description du tier d'efficacité.
     */
    @NotNull
    public String getEfficiencyDescription(int level) {
        if (level <= 0) return "&7Pas d'enchantement";
        return "&aVitesse +" + (level * 20) + "%";
    }

    /**
     * Achète le prochain tier d'efficacité.
     */
    @NotNull
    public UpgradeResult purchaseEfficiency(@NotNull UUID uuid) {
        int level = plugin.getStatsManager().getEfficiencyLevel(uuid);

        if (level >= MAX_EFFICIENCY_LEVEL) {
            return UpgradeResult.MAX_LEVEL;
        }

        BigDecimal price = EFFICIENCY_PRICES[level];
        if (!deductBalance(uuid, price)) {
            return UpgradeResult.NOT_ENOUGH_MONEY;
        }

        plugin.getStatsManager().setEfficiencyLevel(uuid, level + 1);
        return UpgradeResult.SUCCESS;
    }

    // ===========================
    // Auto-Sell
    // ===========================

    /**
     * Obtient le prix de l'auto-sell.
     * @return Le prix, ou null si déjà acheté.
     */
    @Nullable
    public BigDecimal getAutoSellPrice(@NotNull UUID uuid) {
        if (plugin.getStatsManager().hasAutoSell(uuid)) {
            return null; // Déjà acheté
        }
        return AUTO_SELL_PRICE;
    }

    /**
     * Achète l'auto-sell.
     */
    @NotNull
    public UpgradeResult purchaseAutoSell(@NotNull UUID uuid) {
        if (plugin.getStatsManager().hasAutoSell(uuid)) {
            return UpgradeResult.ALREADY_OWNED;
        }

        if (!deductBalance(uuid, AUTO_SELL_PRICE)) {
            return UpgradeResult.NOT_ENOUGH_MONEY;
        }

        plugin.getStatsManager().setAutoSellLevel(uuid, 1);
        plugin.getStatsManager().toggleAutoSell(uuid); // Activer par défaut
        return UpgradeResult.SUCCESS;
    }

    /**
     * Toggle l'auto-sell.
     * @return true si maintenant activé, false si désactivé.
     */
    public boolean toggleAutoSell(@NotNull UUID uuid) {
        return plugin.getStatsManager().toggleAutoSell(uuid);
    }

    // ===========================
    // Utility
    // ===========================

    /**
     * Déduit un montant du solde du joueur.
     */
    private boolean deductBalance(@NotNull UUID uuid, @NotNull BigDecimal amount) {
        EconomyService eco = getEconomyService();
        if (eco == null) {
            plugin.log(Level.WARNING, "EconomyService not available for upgrade purchase");
            return false;
        }

        try {
            BigDecimal balance = eco.getBalance(uuid).join();
            if (balance.compareTo(amount) < 0) {
                return false;
            }

            eco.removeBalance(uuid, amount, "Prison upgrade purchase").join();
            return true;
        } catch (Exception e) {
            plugin.log(Level.WARNING, "Failed to deduct balance for upgrade: " + e.getMessage());
            return false;
        }
    }

    @Nullable
    private EconomyService getEconomyService() {
        IslandiumAPI api = IslandiumAPI.get();
        return api != null ? api.getEconomyService() : null;
    }

    // ===========================
    // Price formatters (static)
    // ===========================

    @NotNull
    public static BigDecimal[] getFortunePrices() {
        return FORTUNE_PRICES;
    }

    @NotNull
    public static BigDecimal[] getEfficiencyPrices() {
        return EFFICIENCY_PRICES;
    }

    @NotNull
    public static BigDecimal getAutoSellBasePrice() {
        return AUTO_SELL_PRICE;
    }

    // ===========================
    // Result Enum
    // ===========================

    public enum UpgradeResult {
        SUCCESS,
        NOT_ENOUGH_MONEY,
        MAX_LEVEL,
        ALREADY_OWNED
    }
}
