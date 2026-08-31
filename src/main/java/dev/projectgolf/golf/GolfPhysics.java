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
        Vec3 result = new Vec3(
                velocity.x * surface.rollingRetention(),
                0.0,
                velocity.z * surface.rollingRetention()
        );
        if (downhill != null) {
            result = result.add(
                    downhill.getStepX() * GolfTuning.SLOPE_ACCELERATION,
                    0.0,
                    downhill.getStepZ() * GolfTuning.SLOPE_ACCELERATION
            );
        }
        return clampSpeed(result);
    }

    public static Vec3 airborne(Vec3 velocity) {
        return new Vec3(
                velocity.x * GolfTuning.AIR_HORIZONTAL_DRAG,
                velocity.y,
                velocity.z * GolfTuning.AIR_HORIZONTAL_DRAG
        );
    }

    public static double horizontalSpeed(Vec3 velocity) {
        return Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
    }

    public static double horizontalDistance(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        return Math.sqrt(dx * dx + dz * dz);
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
