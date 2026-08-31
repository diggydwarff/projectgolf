package dev.projectgolf.client;

import dev.projectgolf.block.GolfSlopeBlock;
import dev.projectgolf.block.PuttingGreenSlopeBlock;
import dev.projectgolf.entity.GolfBallEntity;
import dev.projectgolf.golf.ClubType;
import dev.projectgolf.golf.GolfPhysics;
import dev.projectgolf.golf.GolfSurface;
import dev.projectgolf.golf.GolfTuning;
import dev.projectgolf.golf.GolfWind;
import dev.projectgolf.golf.SwingMath;
import dev.projectgolf.registry.GolfBlocks;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

public final class TrajectoryPredictor {
    private TrajectoryPredictor() {}

    public static List<Vec3> predict(LocalPlayer player, GolfBallEntity ball, ClubType club, float power) {
        if (club == ClubType.PUTTER) {
            return predictPutter(player, ball, power);
        }
        return predictAirborne(player, ball, club, power);
    }

    private static List<Vec3> predictAirborne(LocalPlayer player, GolfBallEntity ball, ClubType club, float power) {
        List<Vec3> points = new ArrayList<>(GolfTuning.PREVIEW_POINTS);
        Vec3 pos = ball.position().add(0, 0.08, 0);
        Vec3 velocity = SwingMath.launchVector(player.getYRot(), club, ball.currentLie(), power, 0.0f);

        for (int i = 0; i < GolfTuning.PREVIEW_POINTS; i++) {
            points.add(pos);
            // Match the authoritative airborne tick order: gravity/clamp before movement, drag after.
            velocity = GolfPhysics.requestedVelocity(velocity, false);
            Vec3 next = pos.add(velocity.scale(GolfTuning.PREVIEW_STEP_TICKS));

            var hit = player.level().clip(new net.minecraft.world.level.ClipContext(
                    pos, next, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE, player));
            if (hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS) {
                points.add(hit.getLocation());
                break;
            }

            pos = next;
            velocity = GolfPhysics.airborne(velocity, GolfWind.acceleration(player.level()));
        }

        return points;
    }

    /**
     * Putter planning is a rolling problem, not a projectile problem. The old preview raycast began
     * only eight hundredths above the turf, immediately hit the ground, and left Shift+Wheel's
     * target marker almost on top of the ball. Simulate grounded roll instead, sampling the actual
     * collision surface under each step so quarter layers and variable green slopes participate.
     * This is deliberately prediction-only; the server remains authoritative for the real ball.
     */
    private static List<Vec3> predictPutter(LocalPlayer player, GolfBallEntity ball, float power) {
        List<Vec3> points = new ArrayList<>(GolfTuning.PUTTER_PREVIEW_MAX_TICKS);
        double surfaceY = ball.getY();
        Vec3 pos = new Vec3(ball.getX(), surfaceY, ball.getZ());
        Vec3 launch = SwingMath.launchVector(player.getYRot(), ClubType.PUTTER, ball.currentLie(), power, 0.0f);
        Vec3 velocity = new Vec3(launch.x, 0.0, launch.z);

        for (int tick = 0; tick < GolfTuning.PUTTER_PREVIEW_MAX_TICKS; tick++) {
            if (tick % GolfTuning.PUTTER_PREVIEW_POINT_INTERVAL_TICKS == 0) {
                points.add(pos.add(0.0, 0.075, 0.0));
            }

            SurfaceSample here = findSurface(player, pos.x, pos.z, surfaceY);
            GolfSurface surface = here == null ? ball.currentLie() : GolfSurface.from(here.state());
            Direction downhill = null;
            double rise = 0.0;
            if (here != null) {
                BlockState state = here.state();
                if (state.getBlock() instanceof PuttingGreenSlopeBlock slope) {
                    downhill = slope.downhill(state);
                    rise = slope.rise(state);
                } else if (state.getBlock() instanceof GolfSlopeBlock slope) {
                    downhill = slope.downhill(state);
                    rise = 1.0;
                }
            }

            velocity = GolfPhysics.grounded(velocity, surface, downhill, rise);
            if (GolfPhysics.horizontalSpeed(velocity) < GolfTuning.STOP_HORIZONTAL_SPEED) break;

            // A putter can move most of a block in one tick. Sampling only the final X/Z can skip
            // several collision terraces on a steep green slope, making the preview tunnel under
            // the hill even though the real ball walks the slope. Substep the horizontal roll so
            // each sample sees the changing slope height.
            double moveX = velocity.x * GolfTuning.PREVIEW_STEP_TICKS;
            double moveZ = velocity.z * GolfTuning.PREVIEW_STEP_TICKS;
            int substeps = Math.max(1, (int) Math.ceil(Math.sqrt(moveX * moveX + moveZ * moveZ) / 0.20));
            boolean blocked = false;
            for (int step = 0; step < substeps; step++) {
                double nextX = pos.x + moveX / substeps;
                double nextZ = pos.z + moveZ / substeps;
                SurfaceSample next = findSurface(player, nextX, nextZ, surfaceY);
                if (next == null) {
                    blocked = true;
                    break;
                }

                double deltaY = next.surfaceY() - surfaceY;
                double maxRise = next.state().getBlock() == GolfBlocks.GRASS_SLAB.get()
                        ? 0.55
                        : GolfTuning.BALL_MAX_UP_STEP + 0.055;
                if (deltaY > maxRise || deltaY < -0.55) {
                    blocked = true;
                    break;
                }

                surfaceY = next.surfaceY();
                pos = new Vec3(nextX, surfaceY, nextZ);
            }
            if (blocked) break;
        }

        Vec3 end = pos.add(0.0, 0.075, 0.0);
        if (points.isEmpty() || points.get(points.size() - 1).distanceToSqr(end) > 1.0e-4) {
            points.add(end);
        }
        return points;
    }

    private static SurfaceSample findSurface(LocalPlayer player, double x, double z, double expectedY) {
        int centerY = (int) Math.floor(expectedY);
        SurfaceSample best = null;
        double bestDistance = Double.MAX_VALUE;

        // A ball normally sits on the block below its Y coordinate, but quarter layers/slopes can
        // put the support surface inside the same block-space. Probe a compact vertical window.
        for (int y = centerY + 1; y >= centerY - 2; y--) {
            BlockPos blockPos = BlockPos.containing(x, y, z);
            BlockState state = player.level().getBlockState(blockPos);
            if (state.isAir()) continue;
            VoxelShape shape = state.getCollisionShape(player.level(), blockPos);
            if (shape.isEmpty()) continue;

            double localX = x - blockPos.getX();
            double localZ = z - blockPos.getZ();
            for (AABB box : shape.toAabbs()) {
                if (localX < box.minX - 1.0e-5 || localX > box.maxX + 1.0e-5
                        || localZ < box.minZ - 1.0e-5 || localZ > box.maxZ + 1.0e-5) {
                    continue;
                }
                double top = blockPos.getY() + box.maxY;
                double distance = Math.abs(top - expectedY);
                double maxRise = state.getBlock() == GolfBlocks.GRASS_SLAB.get()
                        ? 0.56
                        : GolfTuning.BALL_MAX_UP_STEP + 0.06;
                if (top <= expectedY + maxRise
                        && top >= expectedY - 0.60
                        && distance < bestDistance) {
                    best = new SurfaceSample(state, top);
                    bestDistance = distance;
                }
            }
        }
        return best;
    }

    /**
     * Places the visible putt target on top of terrain and raises its pin when the player's line of
     * sight is blocked by an uphill green. This is presentation only; the predicted stop remains
     * the physics result above.
     */
    public static MarkerPlacement putterMarker(LocalPlayer player, Vec3 predictedEnd) {
        double baseY = predictedEnd.y;
        int centerY = (int) Math.floor(predictedEnd.y);
        double highest = Double.NEGATIVE_INFINITY;
        for (int y = centerY + 3; y >= centerY - 2; y--) {
            BlockPos pos = BlockPos.containing(predictedEnd.x, y, predictedEnd.z);
            BlockState state = player.level().getBlockState(pos);
            if (state.isAir()) continue;
            VoxelShape shape = state.getCollisionShape(player.level(), pos);
            if (shape.isEmpty()) continue;
            double localX = predictedEnd.x - pos.getX();
            double localZ = predictedEnd.z - pos.getZ();
            for (AABB box : shape.toAabbs()) {
                if (localX >= box.minX - 1.0e-5 && localX <= box.maxX + 1.0e-5
                        && localZ >= box.minZ - 1.0e-5 && localZ <= box.maxZ + 1.0e-5) {
                    double top = pos.getY() + box.maxY;
                    if (top <= predictedEnd.y + 2.25) highest = Math.max(highest, top);
                }
            }
        }
        if (Double.isFinite(highest)) baseY = Math.max(baseY, highest + 0.08);

        Vec3 base = new Vec3(predictedEnd.x, baseY, predictedEnd.z);
        Vec3 eye = player.getEyePosition();
        Vec3 probe = base.add(0.0, 0.35, 0.0);
        var hit = player.level().clip(new net.minecraft.world.level.ClipContext(
                eye, probe, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        boolean occluded = hit.getType() != net.minecraft.world.phys.HitResult.Type.MISS
                && hit.getLocation().distanceToSqr(eye) + 0.20 < probe.distanceToSqr(eye);
        return new MarkerPlacement(base, occluded ? 4.75 : 1.85, occluded);
    }

    public record MarkerPlacement(Vec3 base, double height, boolean occluded) {}

    private record SurfaceSample(BlockState state, double surfaceY) {}
}
