package dev.projectgolf.client;

import dev.projectgolf.entity.GolfBallEntity;
import dev.projectgolf.golf.ClubType;
import dev.projectgolf.golf.GolfTuning;
import dev.projectgolf.golf.GolfPhysics;
import dev.projectgolf.golf.SwingMath;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class TrajectoryPredictor {
    private TrajectoryPredictor() {}

    public static List<Vec3> predict(LocalPlayer player, GolfBallEntity ball, ClubType club, float power) {
        List<Vec3> points = new ArrayList<>(GolfTuning.PREVIEW_POINTS);
        Vec3 pos = ball.position().add(0, 0.08, 0);
        Vec3 velocity = SwingMath.launchVector(player.getYRot(), club, ball.currentLie(), power, 0.0f);

        for (int i = 0; i < GolfTuning.PREVIEW_POINTS; i++) {
            points.add(pos);
            // Match the authoritative airborne tick order: gravity/clamp before movement, drag after.
            velocity = GolfPhysics.requestedVelocity(velocity, false);
            Vec3 next = pos.add(velocity.scale(GolfTuning.PREVIEW_STEP_TICKS));

            HitResult hit = player.level().clip(new ClipContext(
                    pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.MISS) {
                points.add(hit.getLocation());
                break;
            }

            pos = next;
            velocity = GolfPhysics.airborne(velocity);
        }

        return points;
    }
}
