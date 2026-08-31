package dev.projectgolf.client;

import dev.projectgolf.entity.GolfBallEntity;
import dev.projectgolf.network.RoundStatePayload;
import dev.projectgolf.registry.GolfItems;
import dev.projectgolf.visual.GolfVisualEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.Optional;

/** Strong owner-only navigation while the Golden Spotter is held. */
public final class ClientSpotter {
    private ClientSpotter() {}

    private static final double SEARCH_RADIUS = 512.0;
    private static final int PARTICLE_INTERVAL = 8;

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!held(mc) || mc.player == null || mc.level == null || mc.screen != null) return;
        if (mc.level.getGameTime() % PARTICLE_INTERVAL != 0) return;

        RoundStatePayload round = ClientRoundState.state();
        if (round.active()) {
            marker(mc, Vec3.atCenterOf(round.tee()), GolfVisualEffects.TEE_DUST, 13.0, false);
            marker(mc, Vec3.atCenterOf(round.cup()), GolfVisualEffects.GOLD_DUST, 19.0, true);
        }
        ownedBall(mc).ifPresent(ball -> marker(mc, ball.position(), GolfVisualEffects.BALL_DUST, 23.0, true));
    }

    public static void renderHud(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (!held(mc) || mc.player == null || mc.level == null) return;

        int x = graphics.guiWidth() / 2;
        int y = 24;
        graphics.drawCenteredString(mc.font, "GOLDEN SPOTTER", x, y, 0xFFFFD65A);
        y += 11;

        Optional<GolfBallEntity> ball = ownedBall(mc);
        if (ball.isPresent()) {
            graphics.drawCenteredString(mc.font,
                    locator(mc, "BALL", ball.get().position()), x, y, 0xFF72E8FF);
        } else {
            graphics.drawCenteredString(mc.font, "BALL - not in loaded view", x, y, 0xFF9EA7AB);
        }
        y += 10;

        RoundStatePayload round = ClientRoundState.state();
        if (round.active()) {
            graphics.drawCenteredString(mc.font,
                    locator(mc, "TEE", Vec3.atCenterOf(round.tee())), x, y, 0xFF8EEA72);
            y += 10;
            graphics.drawCenteredString(mc.font,
                    locator(mc, "PIN", Vec3.atCenterOf(round.cup())), x, y, 0xFFFFD65A);
        } else {
            graphics.drawCenteredString(mc.font, "No active course - ball locator only", x, y, 0xFFC8C8C8);
        }
    }

    private static boolean held(Minecraft mc) {
        if (mc.player == null) return false;
        return mc.player.getMainHandItem().is(GolfItems.GOLDEN_SPOTTER.get())
                || mc.player.getOffhandItem().is(GolfItems.GOLDEN_SPOTTER.get());
    }

    private static Optional<GolfBallEntity> ownedBall(Minecraft mc) {
        if (mc.player == null || mc.level == null) return Optional.empty();
        return mc.level.getEntitiesOfClass(
                        GolfBallEntity.class,
                        mc.player.getBoundingBox().inflate(SEARCH_RADIUS),
                        ball -> !ball.isInHole() && ball.isGolfOwner(mc.player.getUUID()))
                .stream()
                .min(Comparator.comparingDouble(mc.player::distanceToSqr));
    }

    private static void marker(Minecraft mc, Vec3 base,
                               net.minecraft.core.particles.ParticleOptions particle,
                               double height, boolean crown) {
        // Deliberately much more apparent than normal shot/landing effects: the player chose to
        // hold a locator item, so a tall sparse beam is preferable to hunting for subtle dots.
        int points = 11;
        for (int i = 0; i < points; i++) {
            double t = i / (double) (points - 1);
            mc.level.addAlwaysVisibleParticle(particle, true,
                    base.x, base.y + 0.35 + height * t, base.z, 0.0, 0.0, 0.0);
        }
        if (crown) {
            double y = base.y + height + 0.35;
            mc.level.addAlwaysVisibleParticle(GolfVisualEffects.WHITE_DUST, true,
                    base.x + 0.45, y, base.z, 0.0, 0.0, 0.0);
            mc.level.addAlwaysVisibleParticle(GolfVisualEffects.WHITE_DUST, true,
                    base.x - 0.45, y, base.z, 0.0, 0.0, 0.0);
            mc.level.addAlwaysVisibleParticle(GolfVisualEffects.WHITE_DUST, true,
                    base.x, y, base.z + 0.45, 0.0, 0.0, 0.0);
            mc.level.addAlwaysVisibleParticle(GolfVisualEffects.WHITE_DUST, true,
                    base.x, y, base.z - 0.45, 0.0, 0.0, 0.0);
        }
    }

    private static String locator(Minecraft mc, String label, Vec3 target) {
        double dx = target.x - mc.player.getX();
        double dz = target.z - mc.player.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float delta = Mth.wrapDegrees(targetYaw - mc.player.getYRot());
        float abs = Math.abs(delta);
        String direction;
        if (abs <= 18.0f) direction = "AHEAD";
        else if (abs >= 162.0f) direction = "BEHIND";
        else if (abs <= 67.5f) direction = delta > 0.0f ? "RIGHT" : "LEFT";
        else direction = delta > 0.0f ? "BACK-RIGHT" : "BACK-LEFT";
        return label + " - " + Math.round(distance) + " blocks - " + direction;
    }
}
