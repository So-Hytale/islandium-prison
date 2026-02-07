package com.islandium.prison.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Configuration principale du plugin Prison.
 */
public class PrisonConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path path;
    private ConfigData data;

    public PrisonConfig(@NotNull Path path) {
        this.path = path;
    }

    public void load() throws IOException {
        if (Files.exists(path)) {
            String content = Files.readString(path);
            this.data = GSON.fromJson(content, ConfigData.class);
        }
        // If file doesn't exist OR was corrupted (contains "null"), create defaults
        if (this.data == null) {
            this.data = createDefault();
            save();
        }
    }

    public void save() throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, GSON.toJson(data));
    }

    private ConfigData createDefault() {
        ConfigData config = new ConfigData();

        // Mines config
        config.mines = new MinesConfig();
        config.mines.resetIntervalMinutes = 15;
        config.mines.broadcastResetWarning = true;
        config.mines.warningSecondsBeforeReset = 30;
        config.mines.autoResetPercentage = 20; // Reset quand < 20% de blocs restants

        // Ranks config (A -> Z, puis Free)
        config.ranks = new RanksConfig();
        config.ranks.ranks = new ArrayList<>();

        // Generate A-Z ranks
        char letter = 'A';
        BigDecimal price = new BigDecimal("1000");
        for (int i = 0; i < 26; i++) {
            RankInfo rank = new RankInfo();
            rank.id = String.valueOf(letter);
            rank.displayName = "Rang " + letter;
            rank.price = price;
            rank.mineName = String.valueOf(letter); // Mine associée
            rank.multiplier = 1.0 + (i * 0.1); // Multiplicateur de gains
            config.ranks.ranks.add(rank);

            letter++;
            price = price.multiply(new BigDecimal("1.5")); // Prix augmente de 50%
        }

        // Free rank (après Z)
        RankInfo freeRank = new RankInfo();
        freeRank.id = "FREE";
        freeRank.displayName = "Libre";
        freeRank.price = new BigDecimal("100000000"); // 100M
        freeRank.mineName = "FREE";
        freeRank.multiplier = 5.0;
        config.ranks.ranks.add(freeRank);

        // Cells config
        config.cells = new CellsConfig();
        config.cells.defaultCellPrice = new BigDecimal("5000");
        config.cells.maxCellsPerPlayer = 1;
        config.cells.cellRentDurationDays = 7;

        // Economy multipliers
        config.economy = new EconomyConfig();
        config.economy.blockSellMultiplier = 1.0;
        config.economy.pickaxeEfficiencyBonus = 0.1;

        // Block values (prix de vente par bloc)
        config.blockValues = new HashMap<>();
        config.blockValues.put("minecraft:cobblestone", new BigDecimal("1"));
        config.blockValues.put("minecraft:stone", new BigDecimal("2"));
        config.blockValues.put("minecraft:coal_ore", new BigDecimal("5"));
        config.blockValues.put("minecraft:iron_ore", new BigDecimal("15"));
        config.blockValues.put("minecraft:gold_ore", new BigDecimal("50"));
        config.blockValues.put("minecraft:diamond_ore", new BigDecimal("200"));
        config.blockValues.put("minecraft:emerald_ore", new BigDecimal("500"));
        config.blockValues.put("minecraft:ancient_debris", new BigDecimal("1000"));

        // Messages
        config.messages = new HashMap<>();
        config.messages.put("prefix", "&8[&6Prison&8] &f");
        config.messages.put("rankup.success", "&aTu es passé au rang &e{rank}&a!");
        config.messages.put("rankup.not-enough-money", "&cTu n'as pas assez d'argent! Il te faut &e{price}&c.");
        config.messages.put("rankup.max-rank", "&cTu as déjà atteint le rang maximum!");
        config.messages.put("mine.teleported", "&aTéléportation vers la mine &e{mine}&a...");
        config.messages.put("mine.no-access", "&cTu n'as pas accès à cette mine!");
        config.messages.put("mine.reset", "&6La mine &e{mine}&6 a été réinitialisée!");
        config.messages.put("mine.reset-warning", "&eLa mine &6{mine}&e va se réinitialiser dans &6{seconds}&e secondes!");
        config.messages.put("sell.success", "&aTu as vendu tes blocs pour &e{amount}&a!");
        config.messages.put("sell.empty", "&cTu n'as rien à vendre!");
        config.messages.put("cell.purchased", "&aTu as acheté une cellule pour &e{price}&a!");
        config.messages.put("cell.not-enough-money", "&cTu n'as pas assez d'argent pour acheter une cellule!");
        config.messages.put("cell.already-owned", "&cTu possèdes déjà une cellule!");
        config.messages.put("cell.teleported", "&aTéléportation vers ta cellule...");
        config.messages.put("cell.no-cell", "&cTu ne possèdes pas de cellule!");

        return config;
    }

    // === Getters ===

    public int getMineResetInterval() {
        return data.mines.resetIntervalMinutes;
    }

    public boolean shouldBroadcastResetWarning() {
        return data.mines.broadcastResetWarning;
    }

    public int getWarningSecondsBeforeReset() {
        return data.mines.warningSecondsBeforeReset;
    }

    public int getAutoResetPercentage() {
        return data.mines.autoResetPercentage;
    }

    @NotNull
    public List<RankInfo> getRanks() {
        return data.ranks.ranks;
    }

    public RankInfo getRank(String id) {
        return data.ranks.ranks.stream()
                .filter(r -> r.id.equalsIgnoreCase(id))
                .findFirst()
                .orElse(null);
    }

    public RankInfo getNextRank(String currentRankId) {
        List<RankInfo> ranks = data.ranks.ranks;
        for (int i = 0; i < ranks.size() - 1; i++) {
            if (ranks.get(i).id.equalsIgnoreCase(currentRankId)) {
                return ranks.get(i + 1);
            }
        }
        return null; // Already max rank
    }

    @NotNull
    public BigDecimal getDefaultCellPrice() {
        return data.cells.defaultCellPrice;
    }

    public int getMaxCellsPerPlayer() {
        return data.cells.maxCellsPerPlayer;
    }

    public int getCellRentDurationDays() {
        return data.cells.cellRentDurationDays;
    }

    public double getBlockSellMultiplier() {
        return data.economy.blockSellMultiplier;
    }

    @NotNull
    public BigDecimal getBlockValue(String blockType) {
        return data.blockValues.getOrDefault(blockType, BigDecimal.ZERO);
    }

    @NotNull
    public Map<String, BigDecimal> getBlockValues() {
        return data.blockValues;
    }

    public void setBlockValue(@NotNull String blockType, @NotNull BigDecimal value) {
        data.blockValues.put(blockType, value);
    }

    public void removeBlockValue(@NotNull String blockType) {
        data.blockValues.remove(blockType);
    }

    public void setBlockSellMultiplier(double multiplier) {
        data.economy.blockSellMultiplier = multiplier;
    }

    @NotNull
    public String getMessage(String key) {
        if (data == null || data.messages == null) {
            return key;
        }
        return data.messages.getOrDefault(key, key);
    }

    @NotNull
    public String getMessage(String key, Object... replacements) {
        String message = getMessage(key);
        for (int i = 0; i < replacements.length - 1; i += 2) {
            String placeholder = "{" + replacements[i] + "}";
            String value = String.valueOf(replacements[i + 1]);
            message = message.replace(placeholder, value);
        }
        return message;
    }

    @NotNull
    public String getPrefixedMessage(String key) {
        return getMessage("prefix") + getMessage(key);
    }

    @NotNull
    public String getPrefixedMessage(String key, Object... replacements) {
        return getMessage("prefix") + getMessage(key, replacements);
    }

    // === Inner Classes ===

    private static class ConfigData {
        MinesConfig mines;
        RanksConfig ranks;
        CellsConfig cells;
        EconomyConfig economy;
        Map<String, BigDecimal> blockValues;
        Map<String, String> messages;
    }

    private static class MinesConfig {
        int resetIntervalMinutes;
        boolean broadcastResetWarning;
        int warningSecondsBeforeReset;
        int autoResetPercentage;
    }

    private static class RanksConfig {
        List<RankInfo> ranks;
    }

    private static class CellsConfig {
        BigDecimal defaultCellPrice;
        int maxCellsPerPlayer;
        int cellRentDurationDays;
    }

    private static class EconomyConfig {
        double blockSellMultiplier;
        double pickaxeEfficiencyBonus;
    }

    public static class RankInfo {
        public String id;
        public String displayName;
        public BigDecimal price;
        public String mineName;
        public double multiplier;
    }

}
