package com.islandium.prison.challenge;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Registre statique de tous les challenges par rang.
 * Chaque rang (A-Z, FREE) a 9 challenges.
 */
public class ChallengeRegistry {

    private static final Map<String, List<ChallengeDefinition>> CHALLENGES = new LinkedHashMap<>();
    private static final Map<String, ChallengeDefinition> BY_ID = new HashMap<>();

    static {
        registerRankA();
        registerRankB();
        registerRankC();
        registerRankD();
        registerRankE();
        // Rangs F-Z : generes automatiquement avec des cibles croissantes
        for (char c = 'F'; c <= 'Z'; c++) {
            registerScaledRank(String.valueOf(c), c - 'A');
        }
        registerFree();
    }

    // =============================================
    // Rang A - Debutant
    // =============================================
    private static void registerRankA() {
        List<ChallengeDefinition> list = new ArrayList<>();
        list.add(new ChallengeDefinition.Builder("A", 0, "A_1", "Mineur Debutant", ChallengeType.MINE_BLOCKS)
                .description("Mine des blocs dans les mines")
                .tier(100, 50).tier(500, 200).tier(2000, 1000).build());
        list.add(new ChallengeDefinition.Builder("A", 1, "A_2", "Chercheur de Pierre", ChallengeType.MINE_SPECIFIC)
                .description("Mine de la cobblestone")
                .targetBlock("hytale:cobblestone")
                .tier(200, 300).build());
        list.add(new ChallengeDefinition.Builder("A", 2, "A_3", "Premiers Gains", ChallengeType.EARN_MONEY)
                .description("Gagne des coins")
                .tier(500, 100).tier(2000, 400).build());
        list.add(new ChallengeDefinition.Builder("A", 3, "A_4", "Vendeur Novice", ChallengeType.SELL_ITEMS)
                .description("Vends des blocs")
                .tier(50, 75).tier(200, 300).build());
        list.add(new ChallengeDefinition.Builder("A", 4, "A_5", "Economiser", ChallengeType.ACCUMULATE_BALANCE)
                .description("Accumule 1000$ en banque")
                .tier(1000, 250).build());
        list.add(new ChallengeDefinition.Builder("A", 5, "A_6", "Fortune I", ChallengeType.BUY_FORTUNE)
                .description("Achete Fortune niveau 1")
                .tier(1, 500).build());
        list.add(new ChallengeDefinition.Builder("A", 6, "A_7", "Efficacite I", ChallengeType.BUY_EFFICIENCY)
                .description("Achete Efficacite niveau 1")
                .tier(1, 300).build());
        list.add(new ChallengeDefinition.Builder("A", 7, "A_8", "Mineur de Charbon", ChallengeType.MINE_SPECIFIC)
                .description("Mine du charbon")
                .targetBlock("hytale:coal_ore")
                .tier(50, 150).tier(200, 600).build());
        list.add(new ChallengeDefinition.Builder("A", 8, "A_9", "Depensier", ChallengeType.SPEND_MONEY)
                .description("Depense des coins")
                .tier(3000, 500).build());
        register("A", list);
    }

    // =============================================
    // Rang B
    // =============================================
    private static void registerRankB() {
        List<ChallengeDefinition> list = new ArrayList<>();
        list.add(new ChallengeDefinition.Builder("B", 0, "B_1", "Mineur Confirme", ChallengeType.MINE_BLOCKS)
                .description("Mine encore plus de blocs")
                .tier(500, 150).tier(2000, 600).tier(5000, 2000).build());
        list.add(new ChallengeDefinition.Builder("B", 1, "B_2", "Tailleur de Pierre", ChallengeType.MINE_SPECIFIC)
                .description("Mine de la stone")
                .targetBlock("hytale:stone")
                .tier(500, 500).build());
        list.add(new ChallengeDefinition.Builder("B", 2, "B_3", "Capitaliste", ChallengeType.EARN_MONEY)
                .description("Gagne plus de coins")
                .tier(2000, 300).tier(8000, 1200).build());
        list.add(new ChallengeDefinition.Builder("B", 3, "B_4", "Marchand", ChallengeType.SELL_ITEMS)
                .description("Vends plus de blocs")
                .tier(200, 200).tier(800, 800).build());
        list.add(new ChallengeDefinition.Builder("B", 4, "B_5", "Coffre-fort", ChallengeType.ACCUMULATE_BALANCE)
                .description("Accumule 5000$ en banque")
                .tier(5000, 750).build());
        list.add(new ChallengeDefinition.Builder("B", 5, "B_6", "Fortune II", ChallengeType.BUY_FORTUNE)
                .description("Achete Fortune niveau 2")
                .tier(2, 1000).build());
        list.add(new ChallengeDefinition.Builder("B", 6, "B_7", "Efficacite II", ChallengeType.BUY_EFFICIENCY)
                .description("Achete Efficacite niveau 2")
                .tier(2, 600).build());
        list.add(new ChallengeDefinition.Builder("B", 7, "B_8", "Chercheur de Fer", ChallengeType.MINE_SPECIFIC)
                .description("Mine du fer")
                .targetBlock("hytale:iron_ore")
                .tier(100, 300).tier(400, 1200).build());
        list.add(new ChallengeDefinition.Builder("B", 8, "B_9", "Gros Depensier", ChallengeType.SPEND_MONEY)
                .description("Depense encore plus")
                .tier(10000, 1500).build());
        register("B", list);
    }

    // =============================================
    // Rang C
    // =============================================
    private static void registerRankC() {
        List<ChallengeDefinition> list = new ArrayList<>();
        list.add(new ChallengeDefinition.Builder("C", 0, "C_1", "Mineur Expert", ChallengeType.MINE_BLOCKS)
                .description("Deviens un expert du minage")
                .tier(2000, 400).tier(8000, 1500).tier(20000, 5000).build());
        list.add(new ChallengeDefinition.Builder("C", 1, "C_2", "Orpailleur", ChallengeType.MINE_SPECIFIC)
                .description("Mine de l'or")
                .targetBlock("hytale:gold_ore")
                .tier(200, 800).tier(500, 2000).build());
        list.add(new ChallengeDefinition.Builder("C", 2, "C_3", "Riche", ChallengeType.EARN_MONEY)
                .description("Gagne beaucoup d'argent")
                .tier(10000, 1000).tier(30000, 3000).build());
        list.add(new ChallengeDefinition.Builder("C", 3, "C_4", "Grossiste", ChallengeType.SELL_ITEMS)
                .description("Vends en grosse quantite")
                .tier(500, 500).tier(2000, 2000).build());
        list.add(new ChallengeDefinition.Builder("C", 4, "C_5", "Banquier", ChallengeType.ACCUMULATE_BALANCE)
                .description("Accumule 20000$ en banque")
                .tier(20000, 2000).build());
        list.add(new ChallengeDefinition.Builder("C", 5, "C_6", "Fortune III", ChallengeType.BUY_FORTUNE)
                .description("Achete Fortune niveau 3")
                .tier(3, 2500).build());
        list.add(new ChallengeDefinition.Builder("C", 6, "C_7", "Efficacite III", ChallengeType.BUY_EFFICIENCY)
                .description("Achete Efficacite niveau 3")
                .tier(3, 1500).build());
        list.add(new ChallengeDefinition.Builder("C", 7, "C_8", "Diamantaire", ChallengeType.MINE_SPECIFIC)
                .description("Mine du diamant")
                .targetBlock("hytale:diamond_ore")
                .tier(50, 500).tier(200, 2000).build());
        list.add(new ChallengeDefinition.Builder("C", 8, "C_9", "Investisseur", ChallengeType.SPEND_MONEY)
                .description("Investis dans tes upgrades")
                .tier(30000, 3000).build());
        register("C", list);
    }

    // =============================================
    // Rang D
    // =============================================
    private static void registerRankD() {
        List<ChallengeDefinition> list = new ArrayList<>();
        list.add(new ChallengeDefinition.Builder("D", 0, "D_1", "Mineur Veteran", ChallengeType.MINE_BLOCKS)
                .description("Mine comme un pro")
                .tier(5000, 1000).tier(20000, 4000).tier(50000, 12000).build());
        list.add(new ChallengeDefinition.Builder("D", 1, "D_2", "Chercheur d'Emeraude", ChallengeType.MINE_SPECIFIC)
                .description("Mine des emeraudes")
                .targetBlock("hytale:emerald_ore")
                .tier(100, 1500).tier(300, 4000).build());
        list.add(new ChallengeDefinition.Builder("D", 2, "D_3", "Fortune", ChallengeType.EARN_MONEY)
                .description("Amasse une fortune")
                .tier(30000, 2500).tier(100000, 8000).build());
        list.add(new ChallengeDefinition.Builder("D", 3, "D_4", "Exportateur", ChallengeType.SELL_ITEMS)
                .description("Exporte en masse")
                .tier(2000, 1500).tier(5000, 4000).build());
        list.add(new ChallengeDefinition.Builder("D", 4, "D_5", "Millionnaire", ChallengeType.ACCUMULATE_BALANCE)
                .description("Accumule 50000$ en banque")
                .tier(50000, 5000).build());
        list.add(new ChallengeDefinition.Builder("D", 5, "D_6", "Fortune IV", ChallengeType.BUY_FORTUNE)
                .description("Achete Fortune niveau 4")
                .tier(4, 5000).build());
        list.add(new ChallengeDefinition.Builder("D", 6, "D_7", "Efficacite IV", ChallengeType.BUY_EFFICIENCY)
                .description("Achete Efficacite niveau 4")
                .tier(4, 3000).build());
        list.add(new ChallengeDefinition.Builder("D", 7, "D_8", "Auto-Vendeur", ChallengeType.BUY_AUTOSELL)
                .description("Achete l'auto-sell")
                .tier(1, 3000).build());
        list.add(new ChallengeDefinition.Builder("D", 8, "D_9", "Magnat", ChallengeType.SPEND_MONEY)
                .description("Depense comme un roi")
                .tier(80000, 8000).build());
        register("D", list);
    }

    // =============================================
    // Rang E
    // =============================================
    private static void registerRankE() {
        List<ChallengeDefinition> list = new ArrayList<>();
        list.add(new ChallengeDefinition.Builder("E", 0, "E_1", "Mineur Legendaire", ChallengeType.MINE_BLOCKS)
                .description("Deviens une legende")
                .tier(10000, 2500).tier(50000, 10000).tier(100000, 30000).build());
        list.add(new ChallengeDefinition.Builder("E", 1, "E_2", "Maitre Mineur", ChallengeType.MINE_SPECIFIC)
                .description("Mine de la lapis-lazuli")
                .targetBlock("hytale:lapis_ore")
                .tier(300, 3000).tier(800, 8000).build());
        list.add(new ChallengeDefinition.Builder("E", 2, "E_3", "Tresor", ChallengeType.EARN_MONEY)
                .description("Accumule un tresor")
                .tier(100000, 8000).tier(300000, 20000).build());
        list.add(new ChallengeDefinition.Builder("E", 3, "E_4", "Baron du Commerce", ChallengeType.SELL_ITEMS)
                .description("Deviens baron du commerce")
                .tier(5000, 4000).tier(15000, 12000).build());
        list.add(new ChallengeDefinition.Builder("E", 4, "E_5", "Multi-Millionnaire", ChallengeType.ACCUMULATE_BALANCE)
                .description("Accumule 150000$ en banque")
                .tier(150000, 15000).build());
        list.add(new ChallengeDefinition.Builder("E", 5, "E_6", "Fortune MAX", ChallengeType.BUY_FORTUNE)
                .description("Achete Fortune niveau 5")
                .tier(5, 15000).build());
        list.add(new ChallengeDefinition.Builder("E", 6, "E_7", "Efficacite MAX", ChallengeType.BUY_EFFICIENCY)
                .description("Achete Efficacite niveau 5")
                .tier(5, 10000).build());
        list.add(new ChallengeDefinition.Builder("E", 7, "E_8", "Tout Automatique", ChallengeType.BUY_AUTOSELL)
                .description("Utilise l'auto-sell")
                .tier(1, 8000).build());
        list.add(new ChallengeDefinition.Builder("E", 8, "E_9", "Dilapidateur", ChallengeType.SPEND_MONEY)
                .description("Depense sans compter")
                .tier(200000, 20000).build());
        register("E", list);
    }

    // =============================================
    // Rangs F-Z : generes avec multiplicateur
    // =============================================
    private static void registerScaledRank(String rankId, int rankIndex) {
        double scale = 1.0 + (rankIndex - 5) * 0.5; // F=1.5x, G=2x, H=2.5x, etc.
        List<ChallengeDefinition> list = new ArrayList<>();

        list.add(new ChallengeDefinition.Builder(rankId, 0, rankId + "_1", "Mineur " + rankId, ChallengeType.MINE_BLOCKS)
                .description("Mine des blocs - Rang " + rankId)
                .tier(scaled(15000, scale), scaled(3000, scale))
                .tier(scaled(60000, scale), scaled(12000, scale))
                .tier(scaled(150000, scale), scaled(35000, scale)).build());
        list.add(new ChallengeDefinition.Builder(rankId, 1, rankId + "_2", "Specialiste " + rankId, ChallengeType.MINE_SPECIFIC)
                .description("Mine un type de bloc specifique")
                .targetBlock("hytale:stone")
                .tier(scaled(500, scale), scaled(4000, scale))
                .tier(scaled(1500, scale), scaled(10000, scale)).build());
        list.add(new ChallengeDefinition.Builder(rankId, 2, rankId + "_3", "Fortune " + rankId, ChallengeType.EARN_MONEY)
                .description("Gagne des coins")
                .tier(scaled(150000, scale), scaled(10000, scale))
                .tier(scaled(500000, scale), scaled(25000, scale)).build());
        list.add(new ChallengeDefinition.Builder(rankId, 3, rankId + "_4", "Vendeur " + rankId, ChallengeType.SELL_ITEMS)
                .description("Vends des blocs")
                .tier(scaled(8000, scale), scaled(5000, scale))
                .tier(scaled(25000, scale), scaled(15000, scale)).build());
        list.add(new ChallengeDefinition.Builder(rankId, 4, rankId + "_5", "Banquier " + rankId, ChallengeType.ACCUMULATE_BALANCE)
                .description("Accumule de l'argent")
                .tier(scaled(200000, scale), scaled(20000, scale)).build());
        list.add(new ChallengeDefinition.Builder(rankId, 5, rankId + "_6", "Fortune Pioche", ChallengeType.BUY_FORTUNE)
                .description("Ameliore ta fortune")
                .tier(Math.min(5, 1 + rankIndex / 5), scaled(8000, scale)).build());
        list.add(new ChallengeDefinition.Builder(rankId, 6, rankId + "_7", "Efficacite Pioche", ChallengeType.BUY_EFFICIENCY)
                .description("Ameliore ton efficacite")
                .tier(Math.min(5, 1 + rankIndex / 5), scaled(5000, scale)).build());
        list.add(new ChallengeDefinition.Builder(rankId, 7, rankId + "_8", "Collectionneur", ChallengeType.MINE_SPECIFIC)
                .description("Mine un mineral rare")
                .targetBlock("hytale:gold_ore")
                .tier(scaled(200, scale), scaled(5000, scale))
                .tier(scaled(600, scale), scaled(12000, scale)).build());
        list.add(new ChallengeDefinition.Builder(rankId, 8, rankId + "_9", "Depensier " + rankId, ChallengeType.SPEND_MONEY)
                .description("Depense des coins")
                .tier(scaled(300000, scale), scaled(25000, scale)).build());

        register(rankId, list);
    }

    // =============================================
    // Rang FREE
    // =============================================
    private static void registerFree() {
        List<ChallengeDefinition> list = new ArrayList<>();
        list.add(new ChallengeDefinition.Builder("FREE", 0, "FREE_1", "Legende Vivante", ChallengeType.MINE_BLOCKS)
                .description("Le defi ultime du minage")
                .tier(500000, 100000).tier(1000000, 250000).tier(5000000, 1000000).build());
        list.add(new ChallengeDefinition.Builder("FREE", 1, "FREE_2", "Maitre des Pierres", ChallengeType.MINE_SPECIFIC)
                .description("Mine une quantite incroyable")
                .targetBlock("hytale:diamond_ore")
                .tier(5000, 150000).tier(20000, 500000).build());
        list.add(new ChallengeDefinition.Builder("FREE", 2, "FREE_3", "Milliardaire", ChallengeType.EARN_MONEY)
                .description("Gagne un milliard")
                .tier(5000000, 500000).tier(10000000, 1500000).build());
        list.add(new ChallengeDefinition.Builder("FREE", 3, "FREE_4", "Roi du Commerce", ChallengeType.SELL_ITEMS)
                .description("Vends une quantite folle")
                .tier(100000, 200000).tier(500000, 800000).build());
        list.add(new ChallengeDefinition.Builder("FREE", 4, "FREE_5", "Coffre-fort Ultime", ChallengeType.ACCUMULATE_BALANCE)
                .description("Accumule 10M$ en banque")
                .tier(10000000, 1000000).build());
        list.add(new ChallengeDefinition.Builder("FREE", 5, "FREE_6", "Fortune Parfaite", ChallengeType.BUY_FORTUNE)
                .description("Fortune au maximum")
                .tier(5, 200000).build());
        list.add(new ChallengeDefinition.Builder("FREE", 6, "FREE_7", "Efficacite Parfaite", ChallengeType.BUY_EFFICIENCY)
                .description("Efficacite au maximum")
                .tier(5, 150000).build());
        list.add(new ChallengeDefinition.Builder("FREE", 7, "FREE_8", "Auto-Sell Pro", ChallengeType.BUY_AUTOSELL)
                .description("Possede l'auto-sell")
                .tier(1, 100000).build());
        list.add(new ChallengeDefinition.Builder("FREE", 8, "FREE_9", "Sans Limites", ChallengeType.SPEND_MONEY)
                .description("Depense sans aucune limite")
                .tier(20000000, 2000000).build());
        register("FREE", list);
    }

    // =============================================
    // Helpers
    // =============================================

    private static long scaled(long base, double scale) {
        return Math.round(base * scale);
    }

    private static double scaled(double base, double scale) {
        return Math.round(base * scale);
    }

    private static void register(String rankId, List<ChallengeDefinition> definitions) {
        CHALLENGES.put(rankId.toUpperCase(), List.copyOf(definitions));
        for (ChallengeDefinition def : definitions) {
            BY_ID.put(def.getId(), def);
        }
    }

    // =============================================
    // Public API
    // =============================================

    @NotNull
    public static List<ChallengeDefinition> getChallengesForRank(@NotNull String rankId) {
        return CHALLENGES.getOrDefault(rankId.toUpperCase(), List.of());
    }

    @Nullable
    public static ChallengeDefinition getChallenge(@NotNull String challengeId) {
        return BY_ID.get(challengeId);
    }

    public static boolean hasRank(@NotNull String rankId) {
        return CHALLENGES.containsKey(rankId.toUpperCase());
    }
}
