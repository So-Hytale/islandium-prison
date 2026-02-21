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
 * Positionne a gauche de l'ecran, max 5 slots.
 *
 * Le Height est calcule dynamiquement au build() via appendInline.
 * Pour changer le nombre de slots, il faut detruire et recreer le HUD
 * (via PrisonUIManager.refreshChallengeHud qui fait hide+show).
 * Les mises a jour de texte (progression) utilisent update(false, cmd).
 */
public class ChallengeHud extends CustomUIHud {

    private static final String SLOT_TEMPLATE = "Pages/Prison/ChallengeHudSlot.ui";
    private static final int HEADER_HEIGHT = 22;
    private static final int SEP_HEIGHT = 7; // 1px + 4 top + 2 bottom
    private static final int PADDING = 12; // 6 top + 6 bottom
    private static final int SLOT_HEIGHT = 49; // 16+14+14+3=47 content + 2 top margin

    private final PrisonPlugin plugin;
    private final UUID playerUuid;
    private final Player player;
    private int slotCount;

    public ChallengeHud(@NotNull PlayerRef playerRef, @NotNull Player player, @NotNull PrisonPlugin plugin) {
        super(playerRef);
        this.plugin = plugin;
        this.playerUuid = playerRef.getUuid();
        this.player = player;
    }

    @Override
    protected void build(UICommandBuilder cmd) {
        Set<String> pinnedIds = plugin.getChallengeManager().getPinnedChallenges(playerUuid);

        // Count valid pinned challenges
        slotCount = 0;
        for (String id : pinnedIds) {
            if (slotCount >= 5) break;
            if (ChallengeRegistry.getChallenge(id) != null) slotCount++;
        }

        if (slotCount == 0) return;

        int height = HEADER_HEIGHT + SEP_HEIGHT + PADDING + slotCount * SLOT_HEIGHT;

        // Append base wrapper (just a positioned Group)
        cmd.append("Pages/Prison/ChallengeHudBase.ui");

        // Build main HUD with computed height via appendInline
        StringBuilder sb = new StringBuilder();
        sb.append("Group #ChallengeHud { LayoutMode: Top; Background: (Color: #0a0f1acc); ");
        sb.append("Anchor: (Width: 220, Height: ").append(height).append("); ");
        sb.append("Padding: (Left: 10, Right: 10, Top: 6, Bottom: 6); ");

        // Header
        sb.append("Group #HeaderRow { LayoutMode: Left; Anchor: (Height: 22, Left: -10, Right: -10, Top: -6); ");
        sb.append("Background: (Color: #151d2a); Padding: (Horizontal: 10); ");
        sb.append("Label #Header { FlexWeight: 1; Text: \"DEFIS SUIVIS\"; ");
        sb.append("Style: (FontSize: 11, TextColor: #ff9800, RenderBold: true, HorizontalAlignment: Center, VerticalAlignment: Center); } } ");

        // Separator
        sb.append("Group #Sep { Background: (Color: #2a3a5c); Anchor: (Height: 1, Top: 4, Bottom: 2); } ");

        // Slots container
        sb.append("Group #Slots { LayoutMode: Top; } ");

        sb.append("}");

        cmd.appendInline("#CHRoot", sb.toString());

        // Append slot templates and populate data
        int slotIdx = 0;
        for (String challengeId : pinnedIds) {
            if (slotIdx >= 5) break;

            ChallengeDefinition def = ChallengeRegistry.getChallenge(challengeId);
            if (def == null) continue;

            cmd.append("#Slots", SLOT_TEMPLATE);
            populateSlot(cmd, slotIdx, challengeId, def, true);
            slotIdx++;
        }
    }

    /**
     * Rafraichit les textes du HUD sans le reconstruire.
     * Ne change PAS le nombre de slots (pour ca, utiliser hide+show via PrisonUIManager).
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
            Set<String> pinnedIds = plugin.getChallengeManager().getPinnedChallenges(playerUuid);

            if (pinnedIds.isEmpty()) {
                UICommandBuilder cmd = new UICommandBuilder();
                cmd.set("#ChallengeHud.Visible", false);
                update(false, cmd);
                return;
            }

            UICommandBuilder cmd = new UICommandBuilder();
            int slotIdx = 0;
            for (String challengeId : pinnedIds) {
                if (slotIdx >= slotCount) break;

                ChallengeDefinition def = ChallengeRegistry.getChallenge(challengeId);
                if (def == null) continue;

                populateSlot(cmd, slotIdx, challengeId, def, false);
                slotIdx++;
            }
            update(false, cmd);
        } catch (Exception ignored) {}
    }

    private void populateSlot(UICommandBuilder cmd, int slotIdx, String challengeId,
                              ChallengeDefinition def, boolean isBuild) {
        PlayerChallengeProgress.ChallengeProgressData data =
            plugin.getChallengeManager().getProgressData(playerUuid, challengeId);

        boolean isComplete = data.completedTier >= def.getTierCount();
        String sel = "#Slots[" + slotIdx + "]";

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
        String nameColor = isComplete ? "#66bb6a" : "#ffd700";

        if (isBuild) {
            cmd.set(sel + " #SName.Text", nameText);
            cmd.set(sel + " #SBar.Text", bar);
            cmd.set(sel + " #SProg.Text", progressText);
            cmd.set(sel + " #SRew.Text", rewardText);
        } else {
            cmd.set(sel + " #SName.TextSpans", Message.raw(nameText));
            cmd.set(sel + " #SBar.TextSpans", Message.raw(bar));
            cmd.set(sel + " #SProg.TextSpans", Message.raw(progressText));
            cmd.set(sel + " #SRew.TextSpans", Message.raw(rewardText));
        }
        cmd.set(sel + " #SName.Style.TextColor", nameColor);
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
