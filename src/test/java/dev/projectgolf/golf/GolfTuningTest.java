package dev.projectgolf.golf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GolfTuningTest {
    @Test
    void longShotsKeepLandingBeaconLonger() {
        assertEquals(GolfTuning.LANDING_MARKER_SHORT_TICKS, GolfTuning.landingMarkerDurationTicks(10.0));
        assertEquals(GolfTuning.LANDING_MARKER_MEDIUM_TICKS, GolfTuning.landingMarkerDurationTicks(40.0));
        assertEquals(GolfTuning.LANDING_MARKER_LONG_TICKS, GolfTuning.landingMarkerDurationTicks(120.0));
    }

    @Test
    void ballCanStepOneQuarterGreenLayerButNotAHalfSlab() {
        assertTrue(GolfTuning.BALL_MAX_UP_STEP >= GolfTuning.PUTTING_GREEN_LAYER_HEIGHT);
        assertTrue(GolfTuning.BALL_MAX_UP_STEP < 0.5f);
    }
}
