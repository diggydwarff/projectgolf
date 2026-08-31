package dev.projectgolf.client;

import dev.projectgolf.golf.GolfTuning;
import dev.projectgolf.golf.GolfWind;
import dev.projectgolf.item.GolfClubItem;
import dev.projectgolf.registry.GolfItems;
import dev.projectgolf.visual.GolfVisualEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/** Sparse tactical wisps that make the same wind affecting the golf ball visible to the player. */
public final class ClientWindEffects {
    private ClientWindEffects() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.screen != null || ClientHoleView.active()) return;
        if (!shouldShow(player)) return;

        GolfWind.WindSample wind = GolfWind.sample(mc.level);
        double strength = wind.strength();
        int interval = Math.max(4, (int) Math.round(GolfTuning.WIND_WISP_BASE_INTERVAL_TICKS / (0.55 + strength)));
        if (mc.level.getGameTime() % interval != 0) return;

        Vec3 forward = wind.direction();
        Vec3 side = new Vec3(-forward.z, 0.0, forward.x);
        int wisps = strength >= 0.72 ? 2 : 1;

        for (int w = 0; w < wisps; w++) {
            double sideOffset = (player.getRandom().nextDouble() - 0.5) * 9.0;
            double ahead = 2.5 + player.getRandom().nextDouble() * 5.5;
            double y = player.getY() + 0.8 + player.getRandom().nextDouble() * 2.2;

            // Start slightly upwind so the little streak visibly crosses the playable view rather
            // than spawning as a stationary puff directly on the player.
            Vec3 base = new Vec3(player.getX(), y, player.getZ())
                    .add(side.scale(sideOffset))
                    .subtract(forward.scale(ahead * 0.45));
            Vec3 particleVelocity = forward.scale(0.035 + 0.065 * strength);

            int points = strength >= 0.75 ? 4 : 3;
            for (int i = 0; i < points; i++) {
                double curve = Math.sin((i + w * 0.7) * 1.15) * 0.16;
                Vec3 p = base.add(forward.scale(i * 0.48)).add(side.scale(curve));
                mc.level.addAlwaysVisibleParticle(
                        GolfVisualEffects.WIND_DUST, true,
                        p.x, p.y, p.z,
                        particleVelocity.x, 0.0015, particleVelocity.z);
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
