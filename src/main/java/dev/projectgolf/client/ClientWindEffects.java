package dev.projectgolf.client;

import dev.projectgolf.golf.GolfTuning;
import dev.projectgolf.golf.GolfWind;
import dev.projectgolf.item.GolfClubItem;
import dev.projectgolf.registry.GolfItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

/** Sparse tactical wind cues that stay well out in the course instead of crossing the crosshair. */
public final class ClientWindEffects {
    private ClientWindEffects() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.screen != null || ClientHoleView.active()) return;
        if (!shouldShow(player)) return;

        GolfWind.WindSample wind = GolfWind.sample(mc.level);
        double strength = wind.strength();
        int interval = Math.max(12, (int) Math.round(
                GolfTuning.WIND_WISP_BASE_INTERVAL_TICKS / (0.70 + strength * 0.55)));
        if (mc.level.getGameTime() % interval != 0) return;

        Vec3 windDir = wind.direction();
        Vec3 windSide = new Vec3(-windDir.z, 0.0, windDir.x);

        Vec3 look = player.getLookAngle();
        Vec3 viewForward = new Vec3(look.x, 0.0, look.z);
        if (viewForward.horizontalDistanceSqr() < 1.0e-6) viewForward = new Vec3(0.0, 0.0, 1.0);
        else viewForward = viewForward.normalize();
        Vec3 viewSide = new Vec3(-viewForward.z, 0.0, viewForward.x);

        // Keep wind cues out on the course. They should read as distant moving air, never as
        // clouds floating directly in front of the player's face or crosshair.
        int wisps = strength >= 0.82 ? 2 : 1;
        for (int w = 0; w < wisps; w++) {
            double ahead = 11.0 + player.getRandom().nextDouble() * 9.0;
            double lateral = (player.getRandom().nextDouble() - 0.5) * 14.0;
            double y = player.getEyeY() - 1.2 + player.getRandom().nextDouble() * 4.8;
            Vec3 center = new Vec3(player.getX(), y, player.getZ())
                    .add(viewForward.scale(ahead))
                    .add(viewSide.scale(lateral));

            // SMALL_GUST already has Minecraft's animated wind-charge swirl texture. A short,
            // widely-spaced chain looks much more like a passing wisp than the old cloud puffs.
            // Motion uses the exact authoritative golf-wind direction.
            Vec3 velocity = windDir.scale(0.035 + 0.055 * strength);
            int points = strength >= 0.66 ? 3 : 2;
            for (int i = 0; i < points; i++) {
                double along = (i - (points - 1) * 0.5) * 1.15;
                double curve = Math.sin(i * 1.4 + w * 0.9) * 0.28;
                Vec3 p = center.add(windDir.scale(along)).add(windSide.scale(curve));
                mc.level.addAlwaysVisibleParticle(
                        ParticleTypes.SMALL_GUST, true,
                        p.x, p.y, p.z,
                        velocity.x, 0.0, velocity.z);
            }
        }
    }

    private static boolean shouldShow(LocalPlayer player) {
        if (ClientRoundState.active()) return true;
        if (player.getMainHandItem().getItem() instanceof GolfClubItem
                || player.getOffhandItem().getItem() instanceof GolfClubItem) return true;
        return player.getMainHandItem().is(GolfItems.GOLDEN_SPOTTER.get())
                || player.getOffhandItem().is(GolfItems.GOLDEN_SPOTTER.get());
    }
}
