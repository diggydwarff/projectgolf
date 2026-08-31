package dev.projectgolf.golf;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Pure mechanics helpers for the authoritative ball simulation and client preview.
 *
 * Keep collision/world queries in GolfBallEntity, but keep numerical transforms here so
 * tuning can be self-tested and preview/server behavior can share the same math.
 */
public final class GolfPhysics {
    private GolfPhysics() {}

    public static Vec3 requestedVelocity(Vec3 current, boolean noGravity) {
        if (!finite(current)) return Vec3.ZERO;
        Vec3 requested = noGravity ? current : current.add(0.0, -GolfTuning.BALL_GRAVITY, 0.0);
        return clampSpeed(requested);
    }

    public static Vec3 clampSpeed(Vec3 velocity) {
        if (!finite(velocity)) return Vec3.ZERO;
        double length = velocity.length();
        if (length > GolfTuning.MAX_BALL_SPEED) {
            return velocity.scale(GolfTuning.MAX_BALL_SPEED / length);
        }
        return velocity;
    }

    public static Vec3 wallBounceX(Vec3 velocity) {
        return new Vec3(-velocity.x * 0.24, velocity.y, velocity.z * 0.88);
    }

    public static Vec3 wallBounceZ(Vec3 velocity) {
        return new Vec3(velocity.x * 0.88, velocity.y, -velocity.z * 0.24);
    }

    public static Vec3 verticalCollision(Vec3 requested, GolfSurface surface) {
        if (requested.y < -0.11) {
            return new Vec3(
                    requested.x * 0.94,
                    -requested.y * surface.restitution(),
                    requested.z * 0.94
            );
        }
        return new Vec3(requested.x, 0.0, requested.z);
    }

    public static Vec3 grounded(Vec3 velocity, GolfSurface surface, @Nullable Direction downhill) {
        return grounded(velocity, surface, downhill, downhill == null ? 0.0 : 1.0);
    }

    /**
     * Applies surface roll plus gravity-like downhill acceleration scaled by the ramp's actual rise.
     * A quarter-height slope therefore exerts one quarter of the old full-height slope force, while
     * the legacy full slopes retain their established tuning. Because the force always points
     * downhill, an under-powered uphill putt naturally slows, stops, and rolls back.
     */
    public static Vec3 grounded(Vec3 velocity, GolfSurface surface, @Nullable Direction downhill, double rise) {
        Vec3 result = new Vec3(
                velocity.x * surface.rollingRetention(),
                0.0,
                velocity.z * surface.rollingRetention()
        );
        if (surface == GolfSurface.GREEN) {
            result = applyHorizontalResistance(result, GolfTuning.GREEN_ROLLING_RESISTANCE);
        }
        if (downhill != null && rise > 0.0) {
            double acceleration = slopeAccelerationMagnitude(rise);
            result = result.add(
                    downhill.getStepX() * acceleration,
                    0.0,
                    downhill.getStepZ() * acceleration
            );
        }
        return clampSpeed(result);
    }

    private static Vec3 applyHorizontalResistance(Vec3 velocity, double resistance) {
        double speed = horizontalSpeed(velocity);
        if (speed <= 0.0 || resistance <= 0.0) return velocity;
        if (speed <= resistance) return new Vec3(0.0, velocity.y, 0.0);
        double scale = (speed - resistance) / speed;
        return new Vec3(velocity.x * scale, velocity.y, velocity.z * scale);
    }

    public static double slopeAccelerationMagnitude(double rise) {
        if (!Double.isFinite(rise) || rise <= 0.0) return 0.0;
        return GolfTuning.SLOPE_ACCELERATION * Math.min(1.0, rise);
    }

    public static Vec3 airborne(Vec3 velocity) {
        return airborne(velocity, Vec3.ZERO);
    }

    /** Applies normal air drag plus the shared horizontal golf-wind acceleration. */
    public static Vec3 airborne(Vec3 velocity, Vec3 windAcceleration) {
        Vec3 wind = finite(windAcceleration) ? windAcceleration : Vec3.ZERO;
        return clampSpeed(new Vec3(
                velocity.x * GolfTuning.AIR_HORIZONTAL_DRAG + wind.x,
                velocity.y,
                velocity.z * GolfTuning.AIR_HORIZONTAL_DRAG + wind.z
        ));
    }

    public static double horizontalSpeed(Vec3 velocity) {
        return Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
    }

    public static double horizontalDistance(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * A quarter-layer climb should cost momentum; vanilla entity step-up would otherwise let a
     * rolling ball climb terraces almost for free. Only Project Golf green layers call this.
     */
    public static Vec3 greenLayerUphill(Vec3 velocity, double rise) {
        if (!finite(velocity) || rise <= 0.0) return velocity;
        double factor = Math.max(0.55, 1.0 - rise * 0.80);
        return new Vec3(velocity.x * factor, velocity.y, velocity.z * factor);
    }

    public static boolean shouldSettle(Vec3 velocity, boolean onGround) {
        return onGround
                && horizontalSpeed(velocity) < GolfTuning.STOP_HORIZONTAL_SPEED
                && Math.abs(velocity.y) < 0.05;
    }

    public static boolean finite(Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
}
