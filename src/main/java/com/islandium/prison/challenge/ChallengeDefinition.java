package com.islandium.prison.challenge;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Definition d'un challenge (immutable apres construction).
 */
public class ChallengeDefinition {

    private final String id;
    private final String rankId;
    private final int index;
    private final ChallengeType type;
    private final String displayName;
    private final String description;
    private final String targetBlockId;
    private final List<ChallengeTier> tiers;

    private ChallengeDefinition(Builder builder) {
        this.id = builder.id;
        this.rankId = builder.rankId;
        this.index = builder.index;
        this.type = builder.type;
        this.displayName = builder.displayName;
        this.description = builder.description;
        this.targetBlockId = builder.targetBlockId;
        this.tiers = List.copyOf(builder.tiers);
    }

    @NotNull public String getId() { return id; }
    @NotNull public String getRankId() { return rankId; }
    public int getIndex() { return index; }
    @NotNull public ChallengeType getType() { return type; }
    @NotNull public String getDisplayName() { return displayName; }
    @NotNull public String getDescription() { return description; }
    @Nullable public String getTargetBlockId() { return targetBlockId; }
    @NotNull public List<ChallengeTier> getTiers() { return tiers; }

    public int getTierCount() { return tiers.size(); }

    /**
     * Retourne la cible du dernier palier (= objectif final pour completion).
     */
    public long getFinalTarget() {
        return tiers.isEmpty() ? 0 : tiers.get(tiers.size() - 1).target();
    }

    /**
     * Un palier d'un challenge.
     */
    public record ChallengeTier(long target, BigDecimal reward) {}

    /**
     * Builder pour construire un ChallengeDefinition.
     */
    public static class Builder {
        private String id;
        private String rankId;
        private int index;
        private ChallengeType type;
        private String displayName;
        private String description = "";
        private String targetBlockId;
        private final List<ChallengeTier> tiers = new ArrayList<>();

        public Builder(String rankId, int index, String id, String displayName, ChallengeType type) {
            this.rankId = rankId;
            this.index = index;
            this.id = id;
            this.displayName = displayName;
            this.type = type;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder targetBlock(String blockId) {
            this.targetBlockId = blockId;
            return this;
        }

        public Builder tier(long target, double reward) {
            this.tiers.add(new ChallengeTier(target, BigDecimal.valueOf(reward)));
            return this;
        }

        public Builder tiers(List<ChallengeTier> tiers) {
            this.tiers.addAll(tiers);
            return this;
        }

        public ChallengeDefinition build() {
            return new ChallengeDefinition(this);
        }
    }
}
