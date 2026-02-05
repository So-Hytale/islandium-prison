package com.islandium.prison.economy;

import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.Inventory;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.transaction.SlotTransaction;
import com.islandium.core.api.IslandiumAPI;
import com.islandium.core.api.economy.EconomyService;
import com.islandium.prison.PrisonPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.logging.Level;

/**
 * Service de vente centralisé.
 * Utilisé par /sell, /sellall et l'auto-sell.
 */
public class SellService {

    private final PrisonPlugin plugin;

    public SellService(@NotNull PrisonPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Vend les blocs de l'inventaire d'un joueur.
     *
     * @param uuid       UUID du joueur
     * @param player     Player Hytale
     * @param blockFilter Si non-null, ne vend que ce type de bloc. Si null, vend tout.
     * @return Résultat de la vente
     */
    @NotNull
    public SellResult sellFromInventory(@NotNull UUID uuid, @NotNull Player player, @Nullable String blockFilter) {
        Map<String, BigDecimal> blockValues = plugin.getConfig().getBlockValues();
        Map<String, Integer> soldItems = new LinkedHashMap<>();
        BigDecimal totalEarned = BigDecimal.ZERO;
        int totalBlocksSold = 0;

        // Calculer le multiplicateur total (rang + prestige + config)
        double multiplier = plugin.getRankManager().getPlayerMultiplier(uuid);
        multiplier *= plugin.getConfig().getBlockSellMultiplier();

        try {
            Inventory inv = player.getInventory();
            ItemContainer storage = inv.getStorage();

            // Parcourir tous les slots du storage
            for (short slot = 0; slot < storage.getCapacity(); slot++) {
                ItemStack stack = storage.getItemStack(slot);

                if (ItemStack.isEmpty(stack)) {
                    continue;
                }

                String itemId = stack.getItemId();

                // Si un filtre est actif, ne vendre que ce type
                if (blockFilter != null && !blockFilter.equalsIgnoreCase(itemId)) {
                    continue;
                }

                // Vérifier si ce bloc a une valeur
                BigDecimal baseValue = blockValues.get(itemId);
                if (baseValue == null || baseValue.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                int quantity = stack.getQuantity();

                // Retirer les items du slot
                SlotTransaction removeTx = storage.removeItemStackFromSlot(slot);
                if (!removeTx.succeeded()) {
                    continue;
                }

                // Calculer la valeur
                BigDecimal slotValue = baseValue.multiply(BigDecimal.valueOf(quantity));
                totalEarned = totalEarned.add(slotValue);
                totalBlocksSold += quantity;

                // Tracker les items vendus
                soldItems.merge(itemId, quantity, Integer::sum);
            }

            // Aussi parcourir le hotbar
            ItemContainer hotbar = inv.getHotbar();
            for (short hSlot = 0; hSlot < hotbar.getCapacity(); hSlot++) {
                ItemStack stack = hotbar.getItemStack(hSlot);

                if (ItemStack.isEmpty(stack)) {
                    continue;
                }

                String itemId = stack.getItemId();

                if (blockFilter != null && !blockFilter.equalsIgnoreCase(itemId)) {
                    continue;
                }

                BigDecimal baseValue = blockValues.get(itemId);
                if (baseValue == null || baseValue.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                int quantity = stack.getQuantity();

                SlotTransaction removeTx = hotbar.removeItemStackFromSlot(hSlot);
                if (!removeTx.succeeded()) continue;

                BigDecimal slotValue = baseValue.multiply(BigDecimal.valueOf(quantity));
                totalEarned = totalEarned.add(slotValue);
                totalBlocksSold += quantity;

                soldItems.merge(itemId, quantity, Integer::sum);
            }
        } catch (Exception e) {
            plugin.log(Level.WARNING, "Error during sell for " + uuid + ": " + e.getMessage());
        }

        // Appliquer le multiplicateur
        if (totalEarned.compareTo(BigDecimal.ZERO) > 0) {
            totalEarned = totalEarned.multiply(BigDecimal.valueOf(multiplier))
                    .setScale(2, RoundingMode.HALF_UP);

            // Ajouter l'argent via EconomyService
            EconomyService eco = getEconomyService();
            if (eco != null) {
                eco.addBalance(uuid, totalEarned, "Prison block sale").join();
            }

            // Tracker les stats
            plugin.getStatsManager().addMoneyEarned(uuid, totalEarned);
        }

        return new SellResult(totalEarned, totalBlocksSold, soldItems);
    }

    /**
     * Calcule la valeur d'un bloc pour un joueur (sans vendre).
     * Utile pour l'affichage.
     */
    @NotNull
    public BigDecimal calculateBlockValue(@NotNull UUID uuid, @NotNull String blockId, int count) {
        BigDecimal baseValue = plugin.getConfig().getBlockValue(blockId);
        if (baseValue.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        double multiplier = plugin.getRankManager().getPlayerMultiplier(uuid);
        multiplier *= plugin.getConfig().getBlockSellMultiplier();

        return baseValue.multiply(BigDecimal.valueOf(count))
                .multiply(BigDecimal.valueOf(multiplier))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Vend automatiquement un bloc spécifique (pour auto-sell au minage).
     * N'accède PAS à l'inventaire - directement ajoute l'argent.
     *
     * @param uuid    UUID du joueur
     * @param blockId ID du bloc miné
     * @param count   Nombre de blocs (incluant fortune bonus)
     * @return Montant gagné
     */
    @NotNull
    public BigDecimal autoSell(@NotNull UUID uuid, @NotNull String blockId, int count) {
        BigDecimal earned = calculateBlockValue(uuid, blockId, count);

        if (earned.compareTo(BigDecimal.ZERO) > 0) {
            EconomyService eco = getEconomyService();
            if (eco != null) {
                eco.addBalance(uuid, earned, "Prison auto-sell").join();
            }
            plugin.getStatsManager().addMoneyEarned(uuid, earned);
        }

        return earned;
    }

    @Nullable
    private EconomyService getEconomyService() {
        IslandiumAPI api = IslandiumAPI.get();
        return api != null ? api.getEconomyService() : null;
    }

    // === Formatting Utility ===

    /**
     * Formate un montant d'argent pour l'affichage.
     */
    @NotNull
    public static String formatMoney(@NotNull BigDecimal amount) {
        if (amount.compareTo(new BigDecimal("1000000000")) >= 0) {
            return String.format("%.2fB$", amount.doubleValue() / 1000000000);
        } else if (amount.compareTo(new BigDecimal("1000000")) >= 0) {
            return String.format("%.2fM$", amount.doubleValue() / 1000000);
        } else if (amount.compareTo(new BigDecimal("1000")) >= 0) {
            return String.format("%.2fK$", amount.doubleValue() / 1000);
        }
        return amount.setScale(2, RoundingMode.HALF_UP) + "$";
    }

    // === Result Class ===

    /**
     * Résultat d'une opération de vente.
     */
    public static class SellResult {
        private final BigDecimal totalEarned;
        private final int totalBlocksSold;
        private final Map<String, Integer> soldItems;

        public SellResult(@NotNull BigDecimal totalEarned, int totalBlocksSold, @NotNull Map<String, Integer> soldItems) {
            this.totalEarned = totalEarned;
            this.totalBlocksSold = totalBlocksSold;
            this.soldItems = soldItems;
        }

        @NotNull
        public BigDecimal getTotalEarned() {
            return totalEarned;
        }

        public int getTotalBlocksSold() {
            return totalBlocksSold;
        }

        @NotNull
        public Map<String, Integer> getSoldItems() {
            return soldItems;
        }

        public boolean isEmpty() {
            return totalBlocksSold == 0;
        }
    }
}
