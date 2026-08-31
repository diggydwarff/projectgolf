package dev.projectgolf.debug;

import dev.projectgolf.golf.ClubType;
import dev.projectgolf.golf.GolfSurface;
import dev.projectgolf.golf.GolfPhysics;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import dev.projectgolf.golf.SwingMath;

import java.util.ArrayList;
import java.util.List;

public final class GolfSelfTest {
    private GolfSelfTest() {}

    public static List<String> run() {
        List<String> failures = new ArrayList<>();

        for (int t = 0; t < 500; t++) {
            float power = SwingMath.powerAtTick(t);
            float accuracy = SwingMath.accuracyAtTick(t);
            if (!Float.isFinite(power) || power < 0 || power > 1) {
                failures.add("Power meter escaped [0,1] at tick " + t + ": " + power);
                break;
            }
            if (!Float.isFinite(accuracy) || accuracy < -1 || accuracy > 1) {
                failures.add("Accuracy meter escaped [-1,1] at tick " + t + ": " + accuracy);
                break;
            }
        }

        if (!(ClubType.DRIVER.maxSpeed() > ClubType.WOOD.maxSpeed()
                && ClubType.WOOD.maxSpeed() > ClubType.IRON.maxSpeed()
                && ClubType.IRON.maxSpeed() > ClubType.WEDGE.maxSpeed()
                && ClubType.WEDGE.maxSpeed() > ClubType.PUTTER.maxSpeed())) {
            failures.add("Club speed ordering is invalid.");
        }

        if (!(ClubType.DRIVER.loftDegrees() < ClubType.IRON.loftDegrees()
                && ClubType.IRON.loftDegrees() < ClubType.WEDGE.loftDegrees())) {
            failures.add("Club loft ordering is invalid.");
        }

        if (!(GolfSurface.GREEN.rollingRetention() > GolfSurface.FAIRWAY.rollingRetention()
                && GolfSurface.FAIRWAY.rollingRetention() > GolfSurface.ROUGH.rollingRetention()
                && GolfSurface.ROUGH.rollingRetention() > GolfSurface.BUNKER.rollingRetention())) {
            failures.add("Surface rolling-retention ordering is invalid.");
        }

        if (!(GolfSurface.BUNKER.shotPowerMultiplier() < GolfSurface.ROUGH.shotPowerMultiplier()
                && GolfSurface.ROUGH.shotPowerMultiplier() < GolfSurface.FAIRWAY.shotPowerMultiplier())) {
            failures.add("Lie power penalties are invalid.");
        }

        for (ClubType club : ClubType.values()) {
            for (GolfSurface lie : GolfSurface.values()) {
                for (float power : new float[]{0f, 0.25f, 0.5f, 0.75f, 1f}) {
                    var v = SwingMath.launchVector(0, club, lie, power, 0);
                    if (!Double.isFinite(v.x) || !Double.isFinite(v.y) || !Double.isFinite(v.z)) {
                        failures.add("Non-finite launch vector for " + club + "/" + lie + "/" + power);
                        return failures;
                    }
                }
            }
        }

        // Numerical physics invariants: finite output, max-speed clamp, slope acceleration, and drag.
        Vec3 tooFast = GolfPhysics.clampSpeed(new Vec3(100.0, 0.0, 0.0));
        if (!GolfPhysics.finite(tooFast) || tooFast.length() > dev.projectgolf.golf.GolfTuning.MAX_BALL_SPEED + 1.0e-9) {
            failures.add("Physics max-speed clamp failed.");
        }
        Vec3 flatRoll = GolfPhysics.grounded(new Vec3(0.25, 0.0, 0.0), GolfSurface.GREEN, null);
        Vec3 downhillRoll = GolfPhysics.grounded(new Vec3(0.25, 0.0, 0.0), GolfSurface.GREEN, Direction.EAST);
        if (!(downhillRoll.x > flatRoll.x)) {
            failures.add("Downhill slope acceleration failed.");
        }
        Vec3 airborne = GolfPhysics.airborne(new Vec3(1.0, 0.2, 0.0));
        if (!(airborne.x < 1.0) || airborne.y != 0.2) {
            failures.add("Air drag transform failed.");
        }

        return failures;
    }
}
