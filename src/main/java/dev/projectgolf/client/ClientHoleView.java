package dev.projectgolf.client;

import dev.projectgolf.network.HoleViewPayload;
import dev.projectgolf.visual.GolfVisualEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Detached client-only hole camera. The real player is never moved and no chunks are force-loaded.
 * Overview frames tee/guide points/cup from above; flyover follows the optional guide route.
 */
public final class ClientHoleView {
    private ClientHoleView() {}

    private enum Mode { OVERVIEW, FLYOVER }

    // Server-assigned entity IDs are non-negative in normal play. Keep the client-only camera
    // anchor far outside that range so ClientLevel.addEntity cannot evict a real tracked entity.
    private static final int CAMERA_ANCHOR_ID = -1_900_000_724;

    private static HoleViewPayload view;
    private static Marker cameraAnchor;
    private static Entity previousCameraEntity;
    private static CameraType previousCameraType;
    private static Mode mode = Mode.OVERVIEW;
    private static int ticks;
    private static int flyoverTicks;
    private static int flyoverDuration;

    public static void show(HoleViewPayload payload) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        stop();
        view = payload;
        mode = payload.flyover() ? Mode.FLYOVER : Mode.OVERVIEW;
        ticks = 0;
        flyoverTicks = 0;
        flyoverDuration = computeFlyoverDuration(payload);

        previousCameraEntity = mc.getCameraEntity();
        previousCameraType = mc.options.getCameraType();

        // Use a real client-level vanilla Marker as the camera anchor. The previous implementation
        // pointed Minecraft at a synthetic GolfBallEntity that was never added to ClientLevel.
        // That left the renderer/camera tracking an entity outside the normal client entity
        // lifecycle and could produce the "bugged out" V/B view. Marker is invisible, has no
        // gameplay physics, and is inserted only on this client; the real player never moves.
        mc.options.setCameraType(CameraType.FIRST_PERSON);
        cameraAnchor = new Marker(EntityType.MARKER, mc.level);
        cameraAnchor.setId(CAMERA_ANCHOR_ID);
        cameraAnchor.setInvisible(true);
        cameraAnchor.setNoGravity(true);
        cameraAnchor.moveTo(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ(),
                mc.player.getYRot(), mc.player.getXRot());
        cameraAnchor.setOldPosAndRot();
        mc.level.addEntity(cameraAnchor);
        mc.setCameraEntity(cameraAnchor);
        updateCamera();
    }

    public static boolean active() {
        return view != null && cameraAnchor != null;
    }

    public static void startFlyover() {
        if (!active()) return;
        mode = Mode.FLYOVER;
        flyoverTicks = 0;
        flyoverDuration = computeFlyoverDuration(view);
    }

    public static void stop() {
        Minecraft mc = Minecraft.getInstance();
        Marker oldAnchor = cameraAnchor;
        if (oldAnchor != null && mc.getCameraEntity() == oldAnchor) {
            Entity restore = previousCameraEntity;
            if (restore == null || restore.isRemoved() || restore.level() != mc.level) restore = mc.player;
            if (restore != null) mc.setCameraEntity(restore);
        }
        if (previousCameraType != null) mc.options.setCameraType(previousCameraType);
        if (oldAnchor != null && !oldAnchor.isRemoved()) oldAnchor.discard();

        cameraAnchor = null;
        previousCameraEntity = null;
        previousCameraType = null;
        view = null;
        ticks = 0;
        flyoverTicks = 0;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (!active()) return;
        if (mc.player == null || mc.level == null || cameraAnchor.isRemoved()
                || cameraAnchor.level() != mc.level || !cameraAnchor.isAddedToLevel()) {
            stop();
            return;
        }

        if (mc.options.getCameraType() != CameraType.FIRST_PERSON) mc.options.setCameraType(CameraType.FIRST_PERSON);
        if (mc.getCameraEntity() != cameraAnchor) mc.setCameraEntity(cameraAnchor);

        ticks++;
        if (mode == Mode.FLYOVER) {
            flyoverTicks++;
            if (flyoverTicks >= flyoverDuration) {
                mode = Mode.OVERVIEW;
                flyoverTicks = 0;
            }
        }
        updateCamera();

        // Sparse client-only route markers make tee/cup readable from altitude without becoming
        // another particle cloud. No entities and no permanent beacon blocks are created.
        if (ticks % 10 == 0) renderWorldMarkers(mc);
    }

    public static double preferredFov(double vanillaFov) {
        if (!active()) return vanillaFov;
        return mode == Mode.OVERVIEW ? Math.max(vanillaFov, 82.0) : Math.max(vanillaFov, 76.0);
    }

    public static void renderHud(GuiGraphics graphics) {
        if (!active()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.font == null) return;

        int direct = (int) Math.round(horizontalDistance(view.tee(), view.cup()));
        int route = (int) Math.round(routeLength(route(view)));
        int x = 10;
        int y = 10;
        int width = 190;
        int height = view.guides().isEmpty() ? 62 : 72;
        graphics.fill(x - 5, y - 5, x + width, y + height, 0xAA101010);
        graphics.drawString(mc.font, view.course(), x, y, 0xFFFFFFFF);
        graphics.drawString(mc.font,
                "HOLE " + view.hole() + "  |  PAR " + view.par(), x, y + 12, 0xFFFFD86A);
        graphics.drawString(mc.font,
                direct + " blocks tee-to-pin" + (route != direct ? "  |  route " + route : ""),
                x, y + 24, 0xFFE0E0E0);
        if (!view.guides().isEmpty()) {
            graphics.drawString(mc.font,
                    view.guides().size() + " guide point" + (view.guides().size() == 1 ? "" : "s") + " (presentation only)",
                    x, y + 36, 0xFFC8C8C8);
        }
        int controlsY = view.guides().isEmpty() ? y + 42 : y + 52;
        graphics.drawString(mc.font,
                mode == Mode.FLYOVER ? "B: restart flyover   V: exit" : "B: flyover   V: exit",
                x, controlsY, 0xFFB8B8B8);
    }

    private static void updateCamera() {
        if (!active()) return;
        if (mode == Mode.FLYOVER) updateFlyoverCamera();
        else updateOverviewCamera();
    }

    private static void updateOverviewCamera() {
        List<Vec3> route = route(view);
        Vec3 first = route.getFirst();
        Vec3 last = route.getLast();

        double minX = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (Vec3 point : route) {
            minX = Math.min(minX, point.x);
            maxX = Math.max(maxX, point.x);
            minZ = Math.min(minZ, point.z);
            maxZ = Math.max(maxZ, point.z);
            maxY = Math.max(maxY, point.y);
        }

        Vec3 target = new Vec3((minX + maxX) * 0.5, maxY, (minZ + maxZ) * 0.5);
        Vec3 forward = horizontalUnit(first, last);
        double span = Math.max(18.0, Math.max(maxX - minX, maxZ - minZ));
        double altitude = Mth.clamp(span * 0.68 + 28.0, 38.0, 190.0);
        Vec3 camera = target.subtract(forward.scale(span * 0.10)).add(0.0, altitude, 0.0);
        aim(camera, target);
    }

    private static void updateFlyoverCamera() {
        List<Vec3> route = route(view);
        double progress = flyoverDuration <= 1 ? 1.0 : Mth.clamp(flyoverTicks / (double) flyoverDuration, 0.0, 1.0);
        RouteSample sample = sampleRoute(route, progress);
        Vec3 target = sample.position();
        Vec3 forward = sample.forward();

        // Broadcast-style camera: above and behind the moving route point, looking ahead.
        double altitude = Mth.clamp(16.0 + routeLength(route) * 0.035, 18.0, 32.0);
        Vec3 camera = target.subtract(forward.scale(11.0)).add(0.0, altitude, 0.0);
        Vec3 lookAt = target.add(forward.scale(13.0)).add(0.0, 1.5, 0.0);
        aim(camera, lookAt);
    }

    private static void aim(Vec3 camera, Vec3 target) {
        Vec3 delta = target.subtract(camera);
        double horizontal = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-delta.x, delta.z));
        float pitch = (float) Math.toDegrees(-Math.atan2(delta.y, horizontal));
        // moveTo updates position + rotation together; then collapse old/current interpolation to
        // the same transform so fast flyover movement cannot smear or snap between stale frames.
        cameraAnchor.moveTo(camera.x, camera.y, camera.z, yaw, pitch);
        cameraAnchor.setOldPosAndRot();
    }

    private static void renderWorldMarkers(Minecraft mc) {
        addVerticalMarker(mc, view.tee(), GolfVisualEffects.WHITE_DUST, 7, 1.8);
        addVerticalMarker(mc, view.cup(), GolfVisualEffects.GOLD_DUST, 11, 2.2);
        for (BlockPos guide : view.guides()) {
            Vec3 p = Vec3.atCenterOf(guide).add(0.0, 1.1, 0.0);
            mc.level.addAlwaysVisibleParticle(GolfVisualEffects.WHITE_DUST, true,
                    p.x, p.y, p.z, 0.0, 0.0, 0.0);
        }
    }

    private static void addVerticalMarker(Minecraft mc, BlockPos pos,
                                          net.minecraft.core.particles.ParticleOptions particle,
                                          int points, double step) {
        Vec3 p = Vec3.atCenterOf(pos);
        for (int i = 0; i < points; i++) {
            mc.level.addAlwaysVisibleParticle(particle, true,
                    p.x, p.y + 1.0 + i * step, p.z, 0.0, 0.0, 0.0);
        }
    }

    private static int computeFlyoverDuration(HoleViewPayload payload) {
        double length = routeLength(route(payload));
        return Mth.clamp((int) Math.round(length * 0.9), 140, 360);
    }

    private static List<Vec3> route(HoleViewPayload payload) {
        ArrayList<Vec3> points = new ArrayList<>(payload.guides().size() + 2);
        points.add(Vec3.atCenterOf(payload.tee()).add(0.0, 1.0, 0.0));
        for (BlockPos guide : payload.guides()) {
            points.add(Vec3.atCenterOf(guide).add(0.0, 1.0, 0.0));
        }
        points.add(Vec3.atCenterOf(payload.cup()).add(0.0, 1.0, 0.0));
        return List.copyOf(points);
    }

    private static double routeLength(List<Vec3> route) {
        double total = 0.0;
        for (int i = 1; i < route.size(); i++) total += horizontalDistance(route.get(i - 1), route.get(i));
        return total;
    }

    private static double horizontalDistance(BlockPos a, BlockPos b) {
        return horizontalDistance(Vec3.atCenterOf(a), Vec3.atCenterOf(b));
    }

    private static double horizontalDistance(Vec3 a, Vec3 b) {
        double dx = b.x - a.x;
        double dz = b.z - a.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static Vec3 horizontalUnit(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0e-6) return new Vec3(0.0, 0.0, 1.0);
        return new Vec3(dx / len, 0.0, dz / len);
    }

    private static RouteSample sampleRoute(List<Vec3> route, double progress) {
        double total = routeLength(route);
        if (total < 1.0e-6) return new RouteSample(route.getFirst(), new Vec3(0.0, 0.0, 1.0));
        double wanted = total * progress;
        double seen = 0.0;
        for (int i = 1; i < route.size(); i++) {
            Vec3 a = route.get(i - 1);
            Vec3 b = route.get(i);
            double length = horizontalDistance(a, b);
            if (seen + length >= wanted || i == route.size() - 1) {
                double local = length < 1.0e-6 ? 0.0 : Mth.clamp((wanted - seen) / length, 0.0, 1.0);
                return new RouteSample(a.lerp(b, local), horizontalUnit(a, b));
            }
            seen += length;
        }
        Vec3 a = route.get(route.size() - 2);
        Vec3 b = route.getLast();
        return new RouteSample(b, horizontalUnit(a, b));
    }

    private record RouteSample(Vec3 position, Vec3 forward) {}
}
