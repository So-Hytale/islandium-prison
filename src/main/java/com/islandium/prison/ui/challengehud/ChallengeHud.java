package com.islandium.prison.ui.challengehud;

import com.islandium.prison.PrisonPlugin;
import com.islandium.prison.challenge.ChallengeDefinition;
import com.islandium.prison.challenge.ChallengeRegistry;
import com.islandium.prison.challenge.ChallengeType;
import com.islandium.prison.challenge.PlayerChallengeProgress;
import com.islandium.prison.economy.SellService;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.hud.CustomUIHud;
import com.hypixel.hytale.server.core.ui.builder.UICommandBuilder;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * HUD affichant les defis epingles (suivis) par le joueur.
 * Positionne a gauche de l'ecran, max 3 slots.
 */
public class ChallengeHud extends CustomUIHud {

    private final PrisonPlugin plugin;
    private final UUID playerUuid;
    private final Player player;

    public ChallengeHud(@NotNull PlayerRef playerRef, @NotNull Player player, @NotNull PrisonPlugin plugin) {
        super(playerRef);
        this.plugin = plugin;
        this.playerUuid = playerRef.getUuid();
        this.player = player;
    }

    @Override
    protected void build(UICommandBuilder cmd) {
        cmd.append("Pages/Prison/ChallengeHud.ui");
        populateData(cmd, true);
    }

    /**
     * Rafraichit les donnees du HUD en temps reel.
     */
    public void refreshData() {
        try {
            var ref = player.getReference();
            if (ref == null || !ref.isValid()) return;

            var store = ref.getStore();
            var world = store.getExternalData().getWorld();

            CompletableFuture.runAsync(() -> {
                try {
                    doRefresh();
                } catch (Exception ignored) {}
            }, world);
        } catch (Exception ignored) {}
    }

    private void doRefresh() {
        try {
            UICommandBuilder cmd = new UICommandBuilder();
            populateData(cmd, false);
            update(false, cmd);
        } catch (Exception ignored) {}
    }

    private void populateData(UICommandBuilder cmd, boolean isBuild) {
        Set<String> pinnedIds = plugin.getChallengeManager().getPinnedChallenges(playerUuid);

        if (pinnedIds.isEmpty()) {
            cmd.set("#ChallengeHud.Visible", false);
            return;
        }

        cmd.set("#ChallengeHud.Visible", true);

        // Compter le nombre de slots valides pour ajuster la hauteur
        int validCount = 0;
        for (String cid : pinnedIds) {
            if (validCount >= 3) break;
            if (ChallengeRegistry.getChallenge(cid) != null) validCount++;
        }
        // Header(22) + sep(7) + padding(12) + slots * 51
        int hudHeight = 41 + validCount * 51;
        cmd.set("#ChallengeHud.Anchor.Height", hudHeight);

        int slotIdx = 0;
        for (String challengeId : pinnedIds) {
            if (slotIdx >= 3) break;

            ChallengeDefinition def = ChallengeRegistry.getChallenge(challengeId);
            if (def == null) continue;

            PlayerChallengeProgress.ChallengeProgressData data =
                plugin.getChallengeManager().getProgressData(playerUuid, challengeId);

            boolean isComplete = data.completedTier >= def.getTierCount();
            String slotSelector = "#Slot" + slotIdx;

            cmd.set(slotSelector + ".Visible", true);

            // Nom + tier info
            String tierInfo = def.getTierCount() > 1
                ? " (" + Math.min(data.completedTier + 1, def.getTierCount()) + "/" + def.getTierCount() + ")"
                : "";
            String nameText = def.getDisplayName() + tierInfo;

            // Progression
            long currentValue = data.currentValue;
            long target;
            String progressText;
            String rewardText;

            if (isComplete) {
                target = def.getFinalTarget();
                progressText = "COMPLETE";
                rewardText = "";
            } else if (def.getType() == ChallengeType.SUBMIT_ITEMS) {
                // SUBMIT_ITEMS: pas de barre de progression classique
                target = 1;
                progressText = "Items requis";
                ChallengeDefinition.ChallengeTier tier = def.getTiers().get(data.completedTier);
                rewardText = "-> " + SellService.formatMoney(tier.reward());
            } else {
                ChallengeDefinition.ChallengeTier tier = def.getTiers().get(data.completedTier);
                target = tier.target();
                progressText = formatNumber(currentValue) + "/" + formatNumber(target);
                rewardText = "-> " + SellService.formatMoney(tier.reward());
            }

            String bar = buildProgressBar(currentValue, target, isComplete);

            // Couleurs
            String nameColor = isComplete ? "#66bb6a" : "#ffd700";

            if (isBuild) {
                cmd.set(slotSelector + " #Name.Text", nameText);
                cmd.set(slotSelector + " #Bar.Text", bar);
                cmd.set(slotSelector + " #Prog.Text", progressText);
                cmd.set(slotSelector + " #Rew.Text", rewardText);
            } else {
                cmd.set(slotSelector + " #Name.TextSpans", Message.raw(nameText));
                cmd.set(slotSelector + " #Bar.TextSpans", Message.raw(bar));
                cmd.set(slotSelector + " #Prog.TextSpans", Message.raw(progressText));
                cmd.set(slotSelector + " #Rew.TextSpans", Message.raw(rewardText));
            }
            cmd.set(slotSelector + " #Name.Style.TextColor", nameColor);

            slotIdx++;
        }

        // Masquer les slots inutilises
        for (int i = slotIdx; i < 3; i++) {
            cmd.set("#Slot" + i + ".Visible", false);
        }
    }

    // === Helpers ===

    private String buildProgressBar(long current, long target, boolean complete) {
        if (target <= 0) target = 1;
        double ratio = complete ? 1.0 : Math.min(1.0, (double) current / target);
        int filled = (int) (ratio * 20);
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < 20; i++) {
            sb.append(i < filled ? '#' : '-');
        }
        sb.append("]");
        return sb.toString();
    }

    private String formatNumber(long number) {
        if (number >= 1_000_000_000) {
            return String.format("%.2fB", number / 1_000_000_000.0);
        } else if (number >= 1_000_000) {
            return String.format("%.2fM", number / 1_000_000.0);
        } else if (number >= 1_000) {
            return String.format("%.1fK", number / 1_000.0);
        }
        return String.valueOf(number);
    }
}
