package dev.projectgolf.visual;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class GolfVisualEffectsTest {
    @Test
    void holeInOneAlwaysGetsAceCelebration() {
        assertEquals(GolfVisualEffects.CelebrationTier.ACE,
                GolfVisualEffects.celebrationTier(1, 3));
        assertEquals(GolfVisualEffects.CelebrationTier.ACE,
                GolfVisualEffects.celebrationTier(1, 5));
    }

    @Test
    void celebrationScalesWithScoreRelativeToPar() {
        assertEquals(GolfVisualEffects.CelebrationTier.EAGLE_OR_BETTER,
                GolfVisualEffects.celebrationTier(3, 5));
        assertEquals(GolfVisualEffects.CelebrationTier.BIRDIE,
                GolfVisualEffects.celebrationTier(3, 4));
        assertEquals(GolfVisualEffects.CelebrationTier.PAR,
                GolfVisualEffects.celebrationTier(4, 4));
        assertEquals(GolfVisualEffects.CelebrationTier.BOGEY_OR_WORSE,
                GolfVisualEffects.celebrationTier(5, 4));
    }

    @Test
    void practiceCupGetsSmallNeutralCelebration() {
        assertEquals(GolfVisualEffects.CelebrationTier.PRACTICE,
                GolfVisualEffects.celebrationTier(4, 0));
    }
}
