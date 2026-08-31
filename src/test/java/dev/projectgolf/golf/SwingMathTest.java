package dev.projectgolf.golf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class SwingMathTest {
    @Test
    void metersStayInBounds() {
        for (int tick = 0; tick < 2000; tick++) {
            float power = SwingMath.powerAtTick(tick);
            float accuracy = SwingMath.accuracyAtTick(tick);
            assertTrue(Float.isFinite(power));
            assertTrue(power >= 0.0f && power <= 1.0f, "power=" + power + " tick=" + tick);
            assertTrue(Float.isFinite(accuracy));
            assertTrue(accuracy >= -1.0f && accuracy <= 1.0f, "accuracy=" + accuracy + " tick=" + tick);
        }
    }

    @Test
    void clubHierarchyIsIntentional() {
        assertTrue(ClubType.DRIVER.maxSpeed() > ClubType.WOOD.maxSpeed());
        assertTrue(ClubType.WOOD.maxSpeed() > ClubType.IRON.maxSpeed());
        assertTrue(ClubType.IRON.maxSpeed() > ClubType.WEDGE.maxSpeed());
        assertTrue(ClubType.WEDGE.maxSpeed() > ClubType.PUTTER.maxSpeed());
        assertTrue(ClubType.DRIVER.loftDegrees() < ClubType.IRON.loftDegrees());
        assertTrue(ClubType.IRON.loftDegrees() < ClubType.WEDGE.loftDegrees());
    }

    @Test
    void launchVectorsAreFiniteAcrossTuningGrid() {
        for (ClubType club : ClubType.values()) {
            for (GolfSurface lie : GolfSurface.values()) {
                for (float power : new float[]{0.0f, 0.1f, 0.25f, 0.5f, 0.75f, 1.0f}) {
                    for (float accuracy : new float[]{-1.0f, -0.25f, 0.0f, 0.25f, 1.0f}) {
                        var vector = SwingMath.launchVector(137.5f, club, lie, power, accuracy);
                        assertTrue(GolfPhysics.finite(vector), club + "/" + lie + "/" + power + "/" + accuracy);
                    }
                }
            }
        }
    }

    @Test
    void difficultLiesReducePowerAndIncreaseError() {
        assertTrue(GolfSurface.BUNKER.shotPowerMultiplier() < GolfSurface.ROUGH.shotPowerMultiplier());
        assertTrue(GolfSurface.ROUGH.shotPowerMultiplier() < GolfSurface.FAIRWAY.shotPowerMultiplier());
        assertTrue(GolfSurface.BUNKER.accuracyPenalty() > GolfSurface.ROUGH.accuracyPenalty());
        assertTrue(GolfSurface.ROUGH.accuracyPenalty() > GolfSurface.FAIRWAY.accuracyPenalty());
    }
}
