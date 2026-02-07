package com.islandium.prison.command.impl;

import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.islandium.core.api.location.ServerLocation;
import com.islandium.core.api.player.IslandiumPlayer;
import com.islandium.core.api.util.ColorUtil;
import com.islandium.prison.PrisonPlugin;
import com.islandium.prison.cell.Cell;
import com.islandium.prison.command.base.PrisonCommand;
import com.islandium.prison.mine.Mine;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Commande admin /prisonadmin - Gestion du plugin prison.
 * Utilise des sous-commandes pour une meilleure gestion des arguments.
 */
public class PrisonAdminCommand extends PrisonCommand {

    public PrisonAdminCommand(@NotNull PrisonPlugin plugin) {
        super(plugin, "prisonadmin", "Administration du plugin Prison");
        addAliases("pa", "padmin");
        requirePermission("prison.admin");

        // Mine management subcommands
        addSubCommand(new CreateMineCommand(plugin));
        addSubCommand(new DeleteMineCommand(plugin));
        addSubCommand(new MineInfoCommand(plugin));
        addSubCommand(new SetMineSpawnCommand(plugin));
        addSubCommand(new SetMineCorner1Command(plugin));
        addSubCommand(new SetMineCorner2Command(plugin));
        addSubCommand(new ResetMineCommand(plugin));
        addSubCommand(new AddBlockCommand(plugin));
        addSubCommand(new ClearBlocksCommand(plugin));
        addSubCommand(new ScanBlocksCommand(plugin));
        addSubCommand(new ScanLayersCommand(plugin));
        addSubCommand(new AddLayerBlockCommand(plugin));
        addSubCommand(new ClearLayersCommand(plugin));
        addSubCommand(new UseLayersCommand(plugin));

        // Natural mode subcommands
        addSubCommand(new NaturalModeCommand(plugin));
        addSubCommand(new SetBlockRankCommand(plugin));
        addSubCommand(new ClearBlockRanksCommand(plugin));

        // Cell management subcommands
        addSubCommand(new CreateCellCommand(plugin));
        addSubCommand(new DeleteCellCommand(plugin));
        addSubCommand(new SetCellSpawnCommand(plugin));

        // Player management subcommands
        addSubCommand(new SetRankCommand(plugin));
        addSubCommand(new SetPrestigeCommand(plugin));

        // GUI subcommands
        addSubCommand(new GuiCommand(plugin));
        addSubCommand(new SellConfigCommand(plugin));

        // General subcommands
        addSubCommand(new ReloadCommand(plugin));
        addSubCommand(new SaveCommand(plugin));
    }

    @Override
    public CompletableFuture<Void> execute(CommandContext ctx) {
        if (!hasPermission(ctx, "prison.admin")) {
            sendMessage(ctx, "&cTu n'as pas la permission!");
            return complete();
        }
        showAdminHelp(ctx);
        return complete();
    }

    private void showAdminHelp(CommandContext ctx) {
        sendMessage(ctx, "&6&l=== Prison Admin ===");
        sendMessage(ctx, "");
        sendMessage(ctx, "&e&lMines:");
        sendMessage(ctx, "&e/pa createmine <id> &8- &7Cree une mine");
        sendMessage(ctx, "&e/pa deletemine <id> &8- &7Supprime une mine");
        sendMessage(ctx, "&e/pa mine <nom> &8- &7Infos sur une mine");
        sendMessage(ctx, "&e/pa setminespawn <mine> &8- &7Definit le spawn");
        sendMessage(ctx, "&e/pa setminecorner1 <mine> &8- &7Definit corner 1");
        sendMessage(ctx, "&e/pa setminecorner2 <mine> &8- &7Definit corner 2");
        sendMessage(ctx, "&e/pa addblock <mine> <block> <%> &8- &7Ajoute un bloc");
        sendMessage(ctx, "&e/pa clearblocks <mine> &8- &7Vide la composition");
        sendMessage(ctx, "&e/pa scanblocks <mine> &8- &7Scan et copie la composition");
        sendMessage(ctx, "&e/pa resetmine <mine> &8- &7Reset une mine");
        sendMessage(ctx, "");
        sendMessage(ctx, "&e&lLayers (par couche):");
        sendMessage(ctx, "&e/pa scanlayers <mine> &8- &7Scan et copie par layer");
        sendMessage(ctx, "&e/pa addlayerblock <mine> <layer> <block> <%> &8- &7Ajoute bloc");
        sendMessage(ctx, "&e/pa clearlayers <mine> &8- &7Vide les layers");
        sendMessage(ctx, "&e/pa uselayers <mine> <true/false> &8- &7Mode layer");
        sendMessage(ctx, "");
        sendMessage(ctx, "&e&lMode Naturel (protection):");
        sendMessage(ctx, "&e/pa naturalmode <mine> <true/false> &8- &7Mode naturel");
        sendMessage(ctx, "&e/pa setblockrank <mine> <block> <rank> &8- &7Rang par bloc");
        sendMessage(ctx, "&e/pa clearblockranks <mine> &8- &7Vide les rangs par bloc");
        sendMessage(ctx, "");
        sendMessage(ctx, "&e&lCellules:");
        sendMessage(ctx, "&e/pa createcell <id> &8- &7Cree une cellule");
        sendMessage(ctx, "&e/pa deletecell <id> &8- &7Supprime une cellule");
        sendMessage(ctx, "&e/pa setcellspawn <id> &8- &7Definit le spawn");
        sendMessage(ctx, "");
        sendMessage(ctx, "&e&lJoueurs:");
        sendMessage(ctx, "&e/pa setrank <player> <rank> &8- &7Definit le rang");
        sendMessage(ctx, "&e/pa setprestige <player> <level> &8- &7Definit le prestige");
        sendMessage(ctx, "");
        sendMessage(ctx, "&e&lGeneral:");
        sendMessage(ctx, "&e/pa gui &8- &7Ouvre l'interface de gestion");
        sendMessage(ctx, "&e/pa sellconfig &8- &7Configure le sell shop (UI)");
        sendMessage(ctx, "&e/pa reload &8- &7Recharge la config");
        sendMessage(ctx, "&e/pa save &8- &7Sauvegarde les donnees");
    }

    // ============================================
    // MINE MANAGEMENT SUBCOMMANDS
    // ============================================

    private static class CreateMineCommand extends PrisonCommand {
        private final RequiredArg<String> mineIdArg;

        public CreateMineCommand(@NotNull PrisonPlugin plugin) {
            super(plugin, "createmine", "Cree une nouvelle mine");
            mineIdArg = withRequiredArg("id", "ID de la mine", ArgTypes.STRING);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            String mineId = ctx.get(mineIdArg);

            if (plugin.getMineManager().getMine(mineId) != null) {
                sendMessage(ctx, "&cLa mine &e" + mineId + "&c existe deja!");
                return complete();
            }

            Mine mine = new Mine(mineId);
            mine.setDisplayName("Mine " + mineId);
            mine.setRequiredRank(mineId.toUpperCase());
            plugin.getMineManager().addMine(mine);

            sendMessage(ctx, "&aMine &e" + mine.getId() + "&a creee!");
            sendMessage(ctx, "&7Utilise les commandes suivantes pour la configurer:");
            sendMessage(ctx, "&e/pa setminespawn " + mineId + " &8- &7Definir le spawn");
            sendMessage(ctx, "&e/pa setminecorner1 " + mineId + " &8- &7Definir coin 1");
            sendMessage(ctx, "&e/pa setminecorner2 " + mineId + " &8- &7Definir coin 2");

            return complete();
        }
    }

    private static class DeleteMineCommand extends PrisonCommand {
        private final RequiredArg<String> mineIdArg;

        public DeleteMineCommand(@NotNull PrisonPlugin plugin) {
            super(plugin, "deletemine", "Supprime une mine");
            mineIdArg = withRequiredArg("id", "ID de la mine", ArgTypes.STRING);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            String mineId = ctx.get(mineIdArg);

            if (plugin.getMineManager().getMine(mineId) == null) {
                sendMessage(ctx, "&cMine &e" + mineId + "&c introuvable!");
                return complete();
            }

            plugin.getMineManager().removeMine(mineId);
            sendMessage(ctx, "&cMine &e" + mineId + "&c supprimee!");

            return complete();
        }
    }

    private static class MineInfoCommand extends PrisonCommand {
        private final RequiredArg<String> mineIdArg;

        public MineInfoCommand(@NotNull PrisonPlugin plugin) {
            super(plugin, "mine", "Affiche les infos d'une mine");
            mineIdArg = withRequiredArg("id", "ID de la mine", ArgTypes.STRING);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            String mineId = ctx.get(mineIdArg);
            Mine mine = plugin.getMineManager().getMine(mineId);

            if (mine == null) {
                sendMessage(ctx, "&cMine &e" + mineId + "&c introuvable!");
                return complete();
            }

            sendMessage(ctx, "&6&l=== Mine: " + mine.getId() + " ===");
            sendMessage(ctx, "&7Display: &f" + mine.getDisplayName());
            sendMessage(ctx, "&7Rang requis: &e" + mine.getRequiredRank());
            sendMessage(ctx, "&7Configuree: " + (mine.isConfigured() ? "&aOui" : "&cNon"));
            sendMessage(ctx, "&7Spawn: " + (mine.hasSpawn() ? "&aOui" : "&cNon"));
            sendMessage(ctx, "&7Blocs restants: &e" + String.format("%.1f%%", mine.getRemainingPercentage()));
            sendMessage(ctx, "&7Auto-reset: " + (mine.isAutoReset() ? "&aOui" : "&cNon"));

            return complete();
        }
    }

    private static class SetMineSpawnCommand extends PrisonCommand {
        private final RequiredArg<String> mineIdArg;

        public SetMineSpawnCommand(@NotNull PrisonPlugin plugin) {
            super(plugin, "setminespawn", "Definit le spawn d'une mine");
            mineIdArg = withRequiredArg("mine", "ID de la mine", ArgTypes.STRING);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!isPlayer(ctx)) {
                sendMessage(ctx, "&cCette commande est reservee aux joueurs!");
                return complete();
            }

            String mineId = ctx.get(mineIdArg);
            Mine mine = plugin.getMineManager().getMine(mineId);

            if (mine == null) {
                sendMessage(ctx, "&cMine &e" + mineId + "&c introuvable!");
                return complete();
            }

            IslandiumPlayer player = requireIslandiumPlayer(ctx);
            ServerLocation loc = player.getLocation();

            if (loc == null) {
                sendMessage(ctx, "&cImpossible d'obtenir ta position!");
                return complete();
            }

            mine.setSpawnPoint(loc);
            plugin.getMineManager().saveAll();

            sendMessage(ctx, "&aSpawn de la mine &e" + mine.getId() + "&a defini!");
            return complete();
        }
    }

    private static class SetMineCorner1Command extends PrisonCommand {
        private final RequiredArg<String> mineIdArg;

        public SetMineCorner1Command(@NotNull PrisonPlugin plugin) {
            super(plugin, "setminecorner1", "Definit le coin 1 d'une mine");
            mineIdArg = withRequiredArg("mine", "ID de la mine", ArgTypes.STRING);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            return handleSetCorner(ctx, 1);
        }

        private CompletableFuture<Void> handleSetCorner(CommandContext ctx, int corner) {
            if (!isPlayer(ctx)) {
                sendMessage(ctx, "&cCette commande est reservee aux joueurs!");
                return complete();
            }

            String mineId = ctx.get(mineIdArg);
            Mine mine = plugin.getMineManager().getMine(mineId);

            if (mine == null) {
                sendMessage(ctx, "&cMine &e" + mineId + "&c introuvable!");
                return complete();
            }

            IslandiumPlayer player = requireIslandiumPlayer(ctx);
            ServerLocation loc = player.getLocation();

            if (loc == null) {
                sendMessage(ctx, "&cImpossible d'obtenir ta position!");
                return complete();
            }

            mine.setCorner1(loc);
            plugin.getMineManager().saveAll();

            sendMessage(ctx, "&aCorner 1 de la mine &e" + mine.getId() + "&a defini!");
            if (mine.isConfigured()) {
                sendMessage(ctx, "&7Taille: &e" + mine.getTotalBlocks() + " blocs");
            }

            return complete();
        }
    }

    private static class SetMineCorner2Command extends PrisonCommand {
        private final RequiredArg<String> mineIdArg;

        public SetMineCorner2Command(@NotNull PrisonPlugin plugin) {
            super(plugin, "setminecorner2", "Definit le coin 2 d'une mine");
            mineIdArg = withRequiredArg("mine", "ID de la mine", ArgTypes.STRING);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!isPlayer(ctx)) {
                sendMessage(ctx, "&cCette commande est reservee aux joueurs!");
                return complete();
            }

            String mineId = ctx.get(mineIdArg);
            Mine mine = plugin.getMineManager().getMine(mineId);

            if (mine == null) {
                sendMessage(ctx, "&cMine &e" + mineId + "&c introuvable!");
                return complete();
            }

            IslandiumPlayer player = requireIslandiumPlayer(ctx);
            ServerLocation loc = player.getLocation();

            if (loc == null) {
                sendMessage(ctx, "&cImpossible d'obtenir ta position!");
                return complete();
            }

            mine.setCorner2(loc);
            plugin.getMineManager().saveAll();

            sendMessage(ctx, "&aCorner 2 de la mine &e" + mine.getId() + "&a defini!");
            if (mine.isConfigured()) {
                sendMessage(ctx, "&7Taille: &e" + mine.getTotalBlocks() + " blocs");
            }

            return complete();
        }
    }

    private static class ResetMineCommand extends PrisonCommand {
        private final RequiredArg<String> mineIdArg;

        public ResetMineCommand(@NotNull PrisonPlugin plugin) {
            super(plugin, "resetmine", "Reset une mine");
            mineIdArg = withRequiredArg("mine", "ID de la mine", ArgTypes.STRING);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            String mineId = ctx.get(mineIdArg);
            Mine mine = plugin.getMineManager().getMine(mineId);

            if (mine == null) {
                sendMessage(ctx, "&cMine &e" + mineId + "&c introuvable!");
                return complete();
            }

            plugin.getMineManager().resetMine(mine);
            sendMessage(ctx, "&aMine &e" + mine.getId() + "&a reinitialisee!");

            return complete();
        }
    }

    private static class AddBlockCommand extends PrisonCommand {
        private final RequiredArg<String> mineIdArg;
        private final RequiredArg<String> blockArg;
        private final RequiredArg<Double> percentArg;

        public AddBlockCommand(@NotNull PrisonPlugin plugin) {
            super(plugin, "addblock", "Ajoute un bloc a une mine");
            mineIdArg = withRequiredArg("mine", "ID de la mine", ArgTypes.STRING);
            blockArg = withRequiredArg("block", "Type de bloc", ArgTypes.STRING);
            percentArg = withRequiredArg("percent", "Pourcentage", ArgTypes.DOUBLE);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            String mineId = ctx.get(mineIdArg);
            String blockType = ctx.get(blockArg);
            double percentage = ctx.get(percentArg);

            Mine mine = plugin.getMineManager().getMine(mineId);
            if (mine == null) {
                sendMessage(ctx, "&cMine &e" + mineId + "&c introuvable!");
                return complete();
            }

            mine.addBlock(blockType, percentage);
            plugin.getMineManager().saveAll();

            sendMessage(ctx, "&aBloc &e" + blockType + "&a ajoute a la mine &e" + mine.getId() + "&a avec &e" + percentage + "%");
            showMineComposition(ctx, mine);

            return complete();
        }

        private void showMineComposition(CommandContext ctx, Mine mine) {
            sendMessage(ctx, "&7Composition actuelle:");
            if (mine.getComposition().isEmpty()) {
                sendMessage(ctx, "&8  (vide - utilisera stone par defaut)");
            } else {
                for (var entry : mine.getComposition().entrySet()) {
                    sendMessage(ctx, "&8  - &f" + entry.getKey() + "&7: &e" + entry.getValue() + "%");
                }
            }
        }
    }

    private static class ClearBlocksCommand extends PrisonCommand {
        private final RequiredArg<String> mineIdArg;

        public ClearBlocksCommand(@NotNull PrisonPlugin plugin) {
            super(plugin, "clearblocks", "Vide la composition d'une mine");
            mineIdArg = withRequiredArg("mine", "ID de la mine", ArgTypes.STRING);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            String mineId = ctx.get(mineIdArg);
            Mine mine = plugin.getMineManager().getMine(mineId);

            if (mine == null) {
                sendMessage(ctx, "&cMine &e" + mineId + "&c introuvable!");
                return complete();
            }

            mine.getComposition().clear();
            plugin.getMineManager().saveAll();

            sendMessage(ctx, "&aComposition de la mine &e" + mine.getId() + "&a videe!");
            return complete();
        }
    }

    private static class ScanBlocksCommand extends PrisonCommand {
        private final RequiredArg<String> mineIdArg;

        public ScanBlocksCommand(@NotNull PrisonPlugin plugin) {
            super(plugin, "scanblocks", "Scan et copie la composition d'une mine");
            mineIdArg = withRequiredArg("mine", "ID de la mine", ArgTypes.STRING);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            String mineId = ctx.get(mineIdArg);
            Mine mine = plugin.getMineManager().getMine(mineId);

            if (mine == null) {
                sendMessage(ctx, "&cMine &e" + mineId + "&c introuvable!");
                return complete();
            }

            if (!mine.isConfigured()) {
                sendMessage(ctx, "&cLa mine n'est pas configuree! Definis d'abord corner1 et corner2.");
                return complete();
            }

            sendMessage(ctx, "&7Scan de la mine en cours...");

            java.util.Map<String, Integer> blockCounts = plugin.getMineManager().scanMineBlocks(mine);

            if (blockCounts.isEmpty()) {
                sendMessage(ctx, "&cAucun bloc trouve dans la zone!");
                return complete();
            }

            int totalBlocks = blockCounts.values().stream().mapToInt(Integer::intValue).sum();
            java.util.Map<String, Double> composition = new java.util.HashMap<>();

            for (var entry : blockCounts.entrySet()) {
                double percentage = (entry.getValue() * 100.0) / totalBlocks;
                composition.put(entry.getKey(), Math.round(percentage * 100.0) / 100.0);
            }

            mine.setComposition(composition);
            plugin.getMineManager().saveAll();

            sendMessage(ctx, "&aComposition scannee et appliquee a la mine &e" + mine.getId() + "&a!");
            sendMessage(ctx, "&7Total: &e" + totalBlocks + " blocs");

            return complete();
        }
    }

    private static class ScanLayersCommand extends PrisonCommand {
        private final RequiredArg<String> mineIdArg;

        public ScanLayersCommand(@NotNull PrisonPlugin plugin) {
            super(plugin, "scanlayers", "Scan et copie les layers d'une mine");
            mineIdArg = withRequiredArg("mine", "ID de la mine", ArgTypes.STRING);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            String mineId = ctx.get(mineIdArg);
            Mine mine = plugin.getMineManager().getMine(mineId);

            if (mine == null) {
                sendMessage(ctx, "&cMine &e" + mineId + "&c introuvable!");
                return complete();
            }

            if (!mine.isConfigured()) {
                sendMessage(ctx, "&cLa mine n'est pas configuree! Definis d'abord corner1 et corner2.");
                return complete();
            }

            sendMessage(ctx, "&7Scan des layers en cours...");

            java.util.Map<Integer, java.util.Map<String, Integer>> layerBlockCounts = plugin.getMineManager().scanMineLayers(mine);

            if (layerBlockCounts.isEmpty()) {
                sendMessage(ctx, "&cAucun bloc trouve dans la zone!");
                return complete();
            }

            mine.clearLayerComposition();
            int totalLayers = 0;

            for (var layerEntry : layerBlockCounts.entrySet()) {
                int layer = layerEntry.getKey();
                java.util.Map<String, Integer> counts = layerEntry.getValue();
                int layerTotal = counts.values().stream().mapToInt(Integer::intValue).sum();

                java.util.Map<String, Double> layerComp = new java.util.HashMap<>();
                for (var blockEntry : counts.entrySet()) {
                    double percentage = (blockEntry.getValue() * 100.0) / layerTotal;
                    layerComp.put(blockEntry.getKey(), Math.round(percentage * 100.0) / 100.0);
                }

                for (var entry : layerComp.entrySet()) {
                    mine.addBlockToLayer(layer, entry.getKey(), entry.getValue());
                }
                totalLayers++;
            }

            mine.setUseLayerComposition(true);
            plugin.getMineManager().saveAll();

            sendMessage(ctx, "&aComposition par layer scannee et appliquee a la mine &e" + mine.getId() + "&a!");
            sendMessage(ctx, "&7Layers: &e" + totalLayers + " couches");
            sendMessage(ctx, "&7Mode layer: &aActive");

            return complete();
        }
    }

    private static class AddLayerBlockCommand extends PrisonCommand {
        private final RequiredArg<String> mineIdArg;
        private final RequiredArg<Integer> layerArg;
        private final RequiredArg<String> blockArg;
        private final RequiredArg<Double> percentArg;

        public AddLayerBlockCommand(@NotNull PrisonPlugin plugin) {
            super(plugin, "addlayerblock", "Ajoute un bloc a un layer");
            mineIdArg = withRequiredArg("mine", "ID de la mine", ArgTypes.STRING);
            layerArg = withRequiredArg("layer", "Numero du layer", ArgTypes.INTEGER);
            blockArg = withRequiredArg("block", "Type de bloc", ArgTypes.STRING);
            percentArg = withRequiredArg("percent", "Pourcentage", ArgTypes.DOUBLE);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            String mineId = ctx.get(mineIdArg);
            int layer = ctx.get(layerArg);
            String blockType = ctx.get(blockArg);
            double percentage = ctx.get(percentArg);

            Mine mine = plugin.getMineManager().getMine(mineId);
            if (mine == null) {
                sendMessage(ctx, "&cMine &e" + mineId + "&c introuvable!");
                return complete();
            }

            mine.addBlockToLayer(layer, blockType, percentage);
            plugin.getMineManager().saveAll();

            sendMessage(ctx, "&aBloc &e" + blockType + "&a ajoute au layer &e" + layer + "&a avec &e" + percentage + "%");

            return complete();
        }
    }

    private static class ClearLayersCommand extends PrisonCommand {
        private final RequiredArg<String> mineIdArg;

        public ClearLayersCommand(@NotNull PrisonPlugin plugin) {
            super(plugin, "clearlayers", "Vide les layers d'une mine");
            mineIdArg = withRequiredArg("mine", "ID de la mine", ArgTypes.STRING);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            String mineId = ctx.get(mineIdArg);
            Mine mine = plugin.getMineManager().getMine(mineId);

            if (mine == null) {
                sendMessage(ctx, "&cMine &e" + mineId + "&c introuvable!");
                return complete();
            }

            mine.clearLayerComposition();
            mine.setUseLayerComposition(false);
            plugin.getMineManager().saveAll();

            sendMessage(ctx, "&aComposition par layers videe pour la mine &e" + mine.getId() + "&a!");
            sendMessage(ctx, "&7Mode layer: &cDesactive");
            return complete();
        }
    }

    private static class UseLayersCommand extends PrisonCommand {
        private final RequiredArg<String> mineIdArg;
        private final RequiredArg<Boolean> valueArg;

        public UseLayersCommand(@NotNull PrisonPlugin plugin) {
            super(plugin, "uselayers", "Active/desactive le mode layer");
            mineIdArg = withRequiredArg("mine", "ID de la mine", ArgTypes.STRING);
            valueArg = withRequiredArg("value", "true/false", ArgTypes.BOOLEAN);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            String mineId = ctx.get(mineIdArg);
            boolean useLayers = ctx.get(valueArg);

            Mine mine = plugin.getMineManager().getMine(mineId);
            if (mine == null) {
                sendMessage(ctx, "&cMine &e" + mineId + "&c introuvable!");
                return complete();
            }

            mine.setUseLayerComposition(useLayers);
            plugin.getMineManager().saveAll();

            if (useLayers) {
                sendMessage(ctx, "&aMode layer &eactive&a pour la mine &e" + mine.getId() + "&a!");
            } else {
                sendMessage(ctx, "&aMode layer &cdesactive&a pour la mine &e" + mine.getId() + "&a!");
            }

            return complete();
        }
    }

    // ============================================
    // NATURAL MODE SUBCOMMANDS
    // ============================================

    private static class NaturalModeCommand extends PrisonCommand {
        private final RequiredArg<String> mineIdArg;
        private final RequiredArg<Boolean> valueArg;

        public NaturalModeCommand(@NotNull PrisonPlugin plugin) {
            super(plugin, "naturalmode", "Active/desactive le mode naturel");
            mineIdArg = withRequiredArg("mine", "ID de la mine", ArgTypes.STRING);
            valueArg = withRequiredArg("value", "true/false", ArgTypes.BOOLEAN);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            String mineId = ctx.get(mineIdArg);
            boolean enable = ctx.get(valueArg);

            Mine mine = plugin.getMineManager().getMine(mineId);
            if (mine == null) {
                sendMessage(ctx, "&cMine &e" + mineId + "&c introuvable!");
                return complete();
            }

            mine.setNaturalMode(enable);
            plugin.getMineManager().saveAll();

            if (enable) {
                sendMessage(ctx, "&aMode naturel &eactive&a pour la mine &e" + mine.getId() + "&a!");
                sendMessage(ctx, "&7Les joueurs ne pourront casser que les blocs de la composition.");
                sendMessage(ctx, "&7Utilise &e/pa setblockrank&7 pour definir les rangs par bloc.");
            } else {
                sendMessage(ctx, "&aMode naturel &cdesactive&a pour la mine &e" + mine.getId() + "&a!");
            }

            return complete();
        }
    }

    private static class SetBlockRankCommand extends PrisonCommand {
        private final RequiredArg<String> mineIdArg;
        private final RequiredArg<String> blockArg;
        private final RequiredArg<String> rankArg;

        public SetBlockRankCommand(@NotNull PrisonPlugin plugin) {
            super(plugin, "setblockrank", "Definit le rang requis pour un bloc");
            mineIdArg = withRequiredArg("mine", "ID de la mine", ArgTypes.STRING);
            blockArg = withRequiredArg("block", "Type de bloc", ArgTypes.STRING);
            rankArg = withRequiredArg("rank", "Rang requis", ArgTypes.STRING);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            String mineId = ctx.get(mineIdArg);
            String blockType = ctx.get(blockArg);
            String rank = ctx.get(rankArg).toUpperCase();

            Mine mine = plugin.getMineManager().getMine(mineId);
            if (mine == null) {
                sendMessage(ctx, "&cMine &e" + mineId + "&c introuvable!");
                return complete();
            }

            mine.setBlockRankRequirement(blockType, rank);
            plugin.getMineManager().saveAll();

            sendMessage(ctx, "&aBloc &e" + blockType + "&a necessite maintenant le rang &e" + rank + "&a pour etre casse!");

            return complete();
        }
    }

    private static class ClearBlockRanksCommand extends PrisonCommand {
        private final RequiredArg<String> mineIdArg;

        public ClearBlockRanksCommand(@NotNull PrisonPlugin plugin) {
            super(plugin, "clearblockranks", "Vide les rangs par bloc");
            mineIdArg = withRequiredArg("mine", "ID de la mine", ArgTypes.STRING);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            String mineId = ctx.get(mineIdArg);
            Mine mine = plugin.getMineManager().getMine(mineId);

            if (mine == null) {
                sendMessage(ctx, "&cMine &e" + mineId + "&c introuvable!");
                return complete();
            }

            mine.clearBlockRankRequirements();
            plugin.getMineManager().saveAll();

            sendMessage(ctx, "&aRequirements de rang par bloc vides pour la mine &e" + mine.getId() + "&a!");
            return complete();
        }
    }

    // ============================================
    // CELL MANAGEMENT SUBCOMMANDS
    // ============================================

    private static class CreateCellCommand extends PrisonCommand {
        private final RequiredArg<String> cellIdArg;

        public CreateCellCommand(@NotNull PrisonPlugin plugin) {
            super(plugin, "createcell", "Cree une nouvelle cellule");
            cellIdArg = withRequiredArg("id", "ID de la cellule", ArgTypes.STRING);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            String cellId = ctx.get(cellIdArg);

            if (plugin.getCellManager().getCell(cellId) != null) {
                sendMessage(ctx, "&cLa cellule &e" + cellId + "&c existe deja!");
                return complete();
            }

            Cell cell = plugin.getCellManager().createCell(cellId);
            sendMessage(ctx, "&aCellule &e" + cell.getId() + "&a creee!");
            sendMessage(ctx, "&7Utilise &e/pa setcellspawn " + cellId + "&7 pour definir le spawn.");

            return complete();
        }
    }

    private static class DeleteCellCommand extends PrisonCommand {
        private final RequiredArg<String> cellIdArg;

        public DeleteCellCommand(@NotNull PrisonPlugin plugin) {
            super(plugin, "deletecell", "Supprime une cellule");
            cellIdArg = withRequiredArg("id", "ID de la cellule", ArgTypes.STRING);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            String cellId = ctx.get(cellIdArg);

            if (!plugin.getCellManager().deleteCell(cellId)) {
                sendMessage(ctx, "&cCellule &e" + cellId + "&c introuvable!");
                return complete();
            }

            sendMessage(ctx, "&cCellule &e" + cellId + "&c supprimee!");
            return complete();
        }
    }

    private static class SetCellSpawnCommand extends PrisonCommand {
        private final RequiredArg<String> cellIdArg;

        public SetCellSpawnCommand(@NotNull PrisonPlugin plugin) {
            super(plugin, "setcellspawn", "Definit le spawn d'une cellule");
            cellIdArg = withRequiredArg("id", "ID de la cellule", ArgTypes.STRING);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!isPlayer(ctx)) {
                sendMessage(ctx, "&cCette commande est reservee aux joueurs!");
                return complete();
            }

            String cellId = ctx.get(cellIdArg);
            Cell cell = plugin.getCellManager().getCell(cellId);

            if (cell == null) {
                sendMessage(ctx, "&cCellule &e" + cellId + "&c introuvable!");
                return complete();
            }

            IslandiumPlayer player = requireIslandiumPlayer(ctx);
            ServerLocation loc = player.getLocation();

            if (loc == null) {
                sendMessage(ctx, "&cImpossible d'obtenir ta position!");
                return complete();
            }

            cell.setSpawnPoint(loc);
            plugin.getCellManager().saveAll();

            sendMessage(ctx, "&aSpawn de la cellule &e" + cell.getId() + "&a defini!");
            return complete();
        }
    }

    // ============================================
    // PLAYER MANAGEMENT SUBCOMMANDS
    // ============================================

    private static class SetRankCommand extends PrisonCommand {
        private final RequiredArg<String> playerArg;
        private final RequiredArg<String> rankArg;

        public SetRankCommand(@NotNull PrisonPlugin plugin) {
            super(plugin, "setrank", "Definit le rang d'un joueur");
            playerArg = withRequiredArg("player", "Nom du joueur", ArgTypes.STRING);
            rankArg = withRequiredArg("rank", "ID du rang", ArgTypes.STRING);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            String playerName = ctx.get(playerArg);
            String rankId = ctx.get(rankArg).toUpperCase();

            if (plugin.getConfig().getRank(rankId) == null) {
                sendMessage(ctx, "&cRang &e" + rankId + "&c invalide!");
                return complete();
            }

            plugin.getCore().getPlayerManager().getOnlinePlayers()
                    .thenAccept(players -> {
                        for (IslandiumPlayer p : players) {
                            if (p.getName().equalsIgnoreCase(playerName)) {
                                plugin.getRankManager().setPlayerRank(p.getUniqueId(), rankId);
                                sendMessage(ctx, "&aRang de &e" + p.getName() + "&a defini a &e" + rankId + "&a!");
                                return;
                            }
                        }
                        sendMessage(ctx, "&cJoueur &e" + playerName + "&c non trouve!");
                    });

            return complete();
        }
    }

    private static class SetPrestigeCommand extends PrisonCommand {
        private final RequiredArg<String> playerArg;
        private final RequiredArg<Integer> levelArg;

        public SetPrestigeCommand(@NotNull PrisonPlugin plugin) {
            super(plugin, "setprestige", "Definit le prestige d'un joueur");
            playerArg = withRequiredArg("player", "Nom du joueur", ArgTypes.STRING);
            levelArg = withRequiredArg("level", "Niveau de prestige", ArgTypes.INTEGER);
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            String playerName = ctx.get(playerArg);
            int level = ctx.get(levelArg);

            plugin.getCore().getPlayerManager().getOnlinePlayers()
                    .thenAccept(players -> {
                        for (IslandiumPlayer p : players) {
                            if (p.getName().equalsIgnoreCase(playerName)) {
                                plugin.getRankManager().setPlayerPrestige(p.getUniqueId(), level);
                                sendMessage(ctx, "&aPrestige de &e" + p.getName() + "&a defini a &e" + level + "&a!");
                                return;
                            }
                        }
                        sendMessage(ctx, "&cJoueur &e" + playerName + "&c non trouve!");
                    });

            return complete();
        }
    }

    // ============================================
    // GUI SUBCOMMAND
    // ============================================

    private static class GuiCommand extends PrisonCommand {
        public GuiCommand(@NotNull PrisonPlugin plugin) {
            super(plugin, "gui", "Ouvre l'interface de gestion");
            addAliases("menu");
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!isPlayer(ctx)) {
                sendMessage(ctx, "&cCette commande est reservee aux joueurs!");
                return complete();
            }

            Player player = getPlayer(ctx);
            if (player == null) {
                sendMessage(ctx, "&cErreur: Impossible de recuperer le joueur!");
                return complete();
            }

            plugin.getUIManager().openMineManager(player);
            return complete();
        }
    }

    private static class SellConfigCommand extends PrisonCommand {
        public SellConfigCommand(@NotNull PrisonPlugin plugin) {
            super(plugin, "sellconfig", "Configure le sell shop");
            addAliases("sellshop", "sc");
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            if (!isPlayer(ctx)) {
                sendMessage(ctx, "&cCette commande est reservee aux joueurs!");
                return complete();
            }

            Player player = getPlayer(ctx);
            if (player == null) {
                sendMessage(ctx, "&cErreur: Impossible de recuperer le joueur!");
                return complete();
            }

            plugin.getUIManager().openSellConfig(player);
            return complete();
        }
    }

    // ============================================
    // GENERAL SUBCOMMANDS
    // ============================================

    private static class ReloadCommand extends PrisonCommand {
        public ReloadCommand(@NotNull PrisonPlugin plugin) {
            super(plugin, "reload", "Recharge la configuration");
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            try {
                plugin.getConfig().load();
                sendMessage(ctx, "&aConfiguration rechargee!");
            } catch (Exception e) {
                sendMessage(ctx, "&cErreur lors du rechargement: " + e.getMessage());
            }
            return complete();
        }
    }

    private static class SaveCommand extends PrisonCommand {
        public SaveCommand(@NotNull PrisonPlugin plugin) {
            super(plugin, "save", "Sauvegarde les donnees");
        }

        @Override
        public CompletableFuture<Void> execute(CommandContext ctx) {
            plugin.getMineManager().saveAll();
            plugin.getRankManager().saveAll();
            plugin.getCellManager().saveAll();
            sendMessage(ctx, "&aDonnees sauvegardees!");
            return complete();
        }
    }
}
