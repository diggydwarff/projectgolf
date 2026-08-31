package dev.projectgolf.golf;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class SwingMath {
    private SwingMath() {}

    /**
     * Triangle wave from 0..1..0, giving a timing-based power meter rather than a simple charge bar.
     */
    public static float powerAtTick(int tick) {
        int period = Math.max(2, GolfTuning.POWER_SWEEP_TICKS * 2);
        int t = Math.floorMod(tick, period);
        return (float) (t <= GolfTuning.POWER_SWEEP_TICKS
                ? t / (double) GolfTuning.POWER_SWEEP_TICKS
                : (period - t) / (double) GolfTuning.POWER_SWEEP_TICKS);
    }

    /**
     * Accuracy marker sweeps from -1 to +1 and back. Zero is perfect impact.
     */
    public static float accuracyAtTick(int tick) {
        int half = Math.max(2, GolfTuning.ACCURACY_SWEEP_TICKS);
        int period = half * 2;
        int t = Math.floorMod(tick, period);
        double unit = t <= half ? t / (double) half : (period - t) / (double) half;
        return (float) (unit * 2.0 - 1.0);
    }

    public static Vec3 launchVector(float playerYawDegrees, ClubType club, GolfSurface lie,
                                    float rawPower, float rawAccuracy) {
        double power = Mth.clamp(rawPower, 0.0f, 1.0f);
        double accuracy = Mth.clamp(rawAccuracy, -1.0f, 1.0f);

        // Make low power controllable while still rewarding a well-timed peak.
        double shapedPower = power * power * 0.30 + power * 0.70;
        double speed = club.maxSpeed() * shapedPower * lie.shotPowerMultiplier();

        double missDegrees = accuracy * club.missDegrees() * lie.accuracyPenalty();
        double yaw = Math.toRadians(playerYawDegrees + missDegrees);
        double loft = Math.toRadians(club.loftDegrees());

        double horizontal = speed * Math.cos(loft);
        double x = -Math.sin(yaw) * horizontal;
        double z = Math.cos(yaw) * horizontal;
        double y = speed * Math.sin(loft);

        return new Vec3(x, y, z);
    }

    public static String accuracyLabel(float accuracy) {
        float a = Math.abs(accuracy);
        if (a <= GolfTuning.PERFECT_ACCURACY_WINDOW) return "PERFECT";
        if (a <= 0.25f) return accuracy < 0 ? "EARLY" : "LATE";
        if (a <= 0.60f) return accuracy < 0 ? "EARLY!" : "LATE!";
        return accuracy < 0 ? "SHANK LEFT" : "SHANK RIGHT";
    }
}
