package dev.projectgolf.block;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PuttingGreenSlopeBlockTest {
    @Test
    void allTenQuarterHeightTransitionsExist() {
        assertEquals(10, PuttingGreenSlopeBlock.Profile.values().length);
        for (PuttingGreenSlopeBlock.Profile profile : PuttingGreenSlopeBlock.Profile.values()) {
            assertTrue(profile.lowQuarter() >= 0 && profile.lowQuarter() <= 3);
            assertTrue(profile.highQuarter() >= 1 && profile.highQuarter() <= 4);
            assertTrue(profile.highQuarter() > profile.lowQuarter());
        }
    }

    @Test
    void builderCanRaiseUpperThenLowerEdge() {
        var start = PuttingGreenSlopeBlock.Profile.ZERO_TO_QUARTER;
        var half = start.raiseHigh();
        assertEquals(PuttingGreenSlopeBlock.Profile.ZERO_TO_HALF, half);
        assertEquals(PuttingGreenSlopeBlock.Profile.QUARTER_TO_HALF, half.raiseLow());
    }

    @Test
    void profileRiseMatchesQuarterHeights() {
        assertEquals(1, PuttingGreenSlopeBlock.Profile.ZERO_TO_QUARTER.riseQuarters());
        assertEquals(2, PuttingGreenSlopeBlock.Profile.ZERO_TO_HALF.riseQuarters());
        assertEquals(4, PuttingGreenSlopeBlock.Profile.ZERO_TO_FULL.riseQuarters());
        assertEquals(1, PuttingGreenSlopeBlock.Profile.THREE_QUARTER_TO_FULL.riseQuarters());
    }
}
