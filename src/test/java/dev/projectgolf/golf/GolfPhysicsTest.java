package dev.projectgolf.golf;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class GolfPhysicsTest {
    private static final double EPS = 1.0e-9;

    @Test
    void requestedVelocityAppliesGravityBeforeMovement() {
        Vec3 v = GolfPhysics.requestedVelocity(new Vec3(1.0, 0.5, 0.0), false);
        assertEquals(1.0, v.x, EPS);
        assertEquals(0.5 - GolfTuning.BALL_GRAVITY, v.y, EPS);
    }

    @Test
    void noGravityModeLeavesVerticalVelocityAlone() {
        Vec3 v = GolfPhysics.requestedVelocity(new Vec3(0.1, 0.5, 0.0), true);
        assertEquals(0.5, v.y, EPS);
    }

    @Test
    void maxSpeedIsHardCapped() {
        Vec3 v = GolfPhysics.clampSpeed(new Vec3(50.0, 50.0, 50.0));
        assertEquals(GolfTuning.MAX_BALL_SPEED, v.length(), 1.0e-8);
    }

    @Test
    void airDragOnlyDampsHorizontalMotion() {
        Vec3 v = GolfPhysics.airborne(new Vec3(1.0, 0.2, -1.0));
        assertTrue(Math.abs(v.x) < 1.0);
        assertTrue(Math.abs(v.z) < 1.0);
        assertEquals(0.2, v.y, EPS);
    }

    @Test
    void greenRollsFartherThanRough() {
        Vec3 start = new Vec3(0.4, 0.0, 0.0);
        Vec3 green = GolfPhysics.grounded(start, GolfSurface.GREEN, null);
        Vec3 rough = GolfPhysics.grounded(start, GolfSurface.ROUGH, null);
        assertTrue(green.x > rough.x);
    }

    @Test
    void greenRollingResistanceKillsTheLowSpeedIceTail() {
        Vec3 tiny = new Vec3(GolfTuning.GREEN_ROLLING_RESISTANCE * 0.75, 0.0, 0.0);
        Vec3 result = GolfPhysics.grounded(tiny, GolfSurface.GREEN, null);
        assertEquals(0.0, result.x, EPS);
    }

    @Test
    void downhillSlopeAddsVelocityInItsDirection() {
        Vec3 flat = GolfPhysics.grounded(new Vec3(0.2, 0.0, 0.0), GolfSurface.GREEN, null);
        Vec3 east = GolfPhysics.grounded(new Vec3(0.2, 0.0, 0.0), GolfSurface.GREEN, Direction.EAST);
        assertTrue(east.x > flat.x);
        assertEquals(flat.z, east.z, EPS);
    }

    @Test
    void harderSurfaceBouncesMoreThanBunker() {
        Vec3 landing = new Vec3(0.3, -0.8, 0.0);
        Vec3 fairway = GolfPhysics.verticalCollision(landing, GolfSurface.FAIRWAY);
        Vec3 bunker = GolfPhysics.verticalCollision(landing, GolfSurface.BUNKER);
        assertTrue(fairway.y > bunker.y);
    }

    @Test
    void settleThresholdRequiresGround() {
        Vec3 tiny = new Vec3(GolfTuning.STOP_HORIZONTAL_SPEED * 0.5, 0.0, 0.0);
        assertTrue(GolfPhysics.shouldSettle(tiny, true));
        assertFalse(GolfPhysics.shouldSettle(tiny, false));
    }

    @Test
    void nonFiniteVelocityIsNeutralized() {
        Vec3 v = GolfPhysics.clampSpeed(new Vec3(Double.NaN, 1.0, 0.0));
        assertEquals(Vec3.ZERO, v);
    }

    @Test
    void telemetryDistanceIsHorizontal() {
        Vec3 from = new Vec3(0.0, 0.0, 0.0);
        Vec3 to = new Vec3(3.0, 100.0, 4.0);
        assertEquals(5.0, GolfPhysics.horizontalDistance(from, to), EPS);
    }

    @Test
    void quarterGreenUphillCostsRollingMomentum() {
        Vec3 flat = new Vec3(0.4, 0.0, 0.0);
        Vec3 uphill = GolfPhysics.greenLayerUphill(flat, GolfTuning.PUTTING_GREEN_LAYER_HEIGHT);
        assertTrue(uphill.horizontalDistanceSqr() < flat.horizontalDistanceSqr());
    }
    @Test
    void puttingSlopeForceScalesWithRise() {
        assertEquals(GolfTuning.SLOPE_ACCELERATION * 0.25,
                GolfPhysics.slopeAccelerationMagnitude(0.25), EPS);
        assertEquals(GolfTuning.SLOPE_ACCELERATION * 0.50,
                GolfPhysics.slopeAccelerationMagnitude(0.50), EPS);
        assertEquals(GolfTuning.SLOPE_ACCELERATION,
                GolfPhysics.slopeAccelerationMagnitude(1.0), EPS);
    }

    @Test
    void weakUphillPuttEventuallyRollsBackDownQuarterSlope() {
        Vec3 velocity = new Vec3(0.012, 0.0, 0.0); // east/uphill
        for (int i = 0; i < 40; i++) {
            velocity = GolfPhysics.grounded(velocity, GolfSurface.GREEN, Direction.WEST, 0.25);
        }
        assertTrue(velocity.x < 0.0, "quarter slope should reverse an under-powered uphill putt");
    }

}
