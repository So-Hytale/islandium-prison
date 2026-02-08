package com.islandium.prison.challenge;

import java.util.HashMap;
import java.util.Map;

/**
 * Progression des challenges d'un joueur.
 */
public class PlayerChallengeProgress {

    public Map<String, ChallengeProgressData> challenges = new HashMap<>();

    /**
     * Obtient la progression pour un challenge specifique.
     * Cree une entree vide si elle n'existe pas.
     */
    public ChallengeProgressData getOrCreate(String challengeId) {
        return challenges.computeIfAbsent(challengeId, k -> new ChallengeProgressData());
    }

    /**
     * Donnees de progression pour un challenge individuel.
     */
    public static class ChallengeProgressData {
        public long currentValue = 0;
        public int completedTier = 0;
    }
}
