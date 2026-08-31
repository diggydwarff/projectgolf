package dev.projectgolf.client;

import dev.projectgolf.entity.GolfBallEntity;
import dev.projectgolf.golf.ClubType;
import dev.projectgolf.golf.GolfTuning;
import dev.projectgolf.golf.SwingMath;
import dev.projectgolf.item.GolfClubItem;
import dev.projectgolf.network.SwingPayload;
import dev.projectgolf.visual.GolfVisualEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;

public final class ClientSwingController {
    public enum Phase { IDLE, POWER, ACCURACY, COOLDOWN }

    private static final EnumMap<ClubType, Float> TARGET_POWER = new EnumMap<>(ClubType.class);

    static {
        for (ClubType club : ClubType.values()) TARGET_POWER.put(club, 1.0f);
    }

    private static Phase phase = Phase.IDLE;
    private static int phaseTicks;
    private static boolean previousUseDown;
    private static float lockedPower;
    private static float currentAccuracy;
    private static int cooldownTicks;
    private static int perfectFlashTicks;
    private static int trackedBallId = -1;
    private static ClubType swingClub;
    private static float swingTargetPower = 1.0f;
    private static double plannedDistanceBlocks;

    private ClientSwingController() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        boolean useDown = mc.options.keyUse.isDown();
        if (perfectFlashTicks > 0) perfectFlashTicks--;

        if (player == null || mc.level == null) {
            trackedBallId = -1;
            reset();
            previousUseDown = useDown;
            return;
        }

        if (mc.screen != null || player.isSpectator()) {
            reset();
            previousUseDown = useDown;
            return;
        }

        GolfClubItem heldClub = player.getMainHandItem().getItem() instanceof GolfClubItem club ? club : null;
        boolean holdingClub = heldClub != null;
        boolean pressed = useDown && !previousUseDown;
        boolean released = !useDown && previousUseDown;

        if (!holdingClub) {
            reset();
            previousUseDown = useDown;
            return;
        }

        if (phase != Phase.IDLE && swingClub != null && heldClub.club() != swingClub) {
            reset();
            previousUseDown = useDown;
            return;
        }

        switch (phase) {
            case IDLE -> {
                if (pressed) {
                    Optional<GolfBallEntity> ball = nearestSwingableBall(player);
                    if (ball.isPresent()) {
                        trackedBallId = ball.get().getId();
                        swingClub = heldClub.club();
                        swingTargetPower = plannedPower(swingClub);
                        phase = Phase.POWER;
                        phaseTicks = 0;
                    }
                }
            }
            case POWER -> {
                phaseTicks++;
                if (released) {
                    lockedPower = SwingMath.powerAtTick(phaseTicks) * swingTargetPower;
                    phase = Phase.ACCURACY;
                    phaseTicks = 0;
                }
            }
            case ACCURACY -> {
                phaseTicks++;
                currentAccuracy = SwingMath.accuracyAtTick(phaseTicks);
                if (pressed) {
                    if (Math.abs(currentAccuracy) <= GolfTuning.PERFECT_ACCURACY_WINDOW) {
                        perfectFlashTicks = GolfTuning.PERFECT_FLASH_TICKS;
                    }
                    PacketDistributor.sendToServer(new SwingPayload(lockedPower, currentAccuracy, 0.0f, 0.0f));
                    phase = Phase.COOLDOWN;
                    cooldownTicks = GolfTuning.SWING_COOLDOWN_TICKS;
                    phaseTicks = 0;
                }
            }
            case COOLDOWN -> {
                if (--cooldownTicks <= 0 && !useDown) {
                    phase = Phase.IDLE;
                    phaseTicks = 0;
                    swingClub = null;
                }
            }
        }

        if (phase != Phase.COOLDOWN
                && mc.level.getGameTime() % GolfTuning.PREVIEW_PARTICLE_INTERVAL_TICKS == 0) {
            spawnTrajectoryPreview(player);
        }

        previousUseDown = useDown;
    }

    /** Shift + wheel changes the planned maximum shot in 5% steps. */
    public static boolean adjustPlannedPower(double scrollDelta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.screen != null || phase != Phase.IDLE) return false;
        if (!player.isShiftKeyDown()) return false;
        if (!(player.getMainHandItem().getItem() instanceof GolfClubItem clubItem)) return false;
        if (nearestSwingableBall(player).isEmpty()) return false;
        if (scrollDelta == 0.0) return false;

        float step = GolfTuning.TARGET_POWER_STEP;
        float current = plannedPower(clubItem.club());
        float next = Mth.clamp(current + (scrollDelta > 0 ? step : -step),
                GolfTuning.TARGET_POWER_MIN, 1.0f);
        if (Math.abs(next - current) < 0.0001f) return true;

        TARGET_POWER.put(clubItem.club(), next);
        plannedDistanceBlocks = 0.0;
        spawnTrajectoryPreview(player);
        return true;
    }

    private static void spawnTrajectoryPreview(LocalPlayer player) {
        if (!(player.getMainHandItem().getItem() instanceof GolfClubItem clubItem)) return;
        Optional<GolfBallEntity> nearest = nearestSwingableBall(player);
        if (nearest.isEmpty()) return;
        GolfBallEntity ball = nearest.get();
        trackedBallId = ball.getId();

        float previewPower = switch (phase) {
            case POWER -> SwingMath.powerAtTick(phaseTicks) * swingTargetPower;
            case ACCURACY -> lockedPower;
            case IDLE -> plannedPower(clubItem.club());
            case COOLDOWN -> 0.0f;
        };

        List<Vec3> points = TrajectoryPredictor.predict(player, ball, clubItem.club(), previewPower);
        int stride = clubItem.club() == ClubType.PUTTER
                ? GolfTuning.PUTTER_PREVIEW_PARTICLE_STRIDE
                : GolfTuning.PREVIEW_PARTICLE_STRIDE;

        // Clean dotted guide: no continuous End Rod swarm. The target pin does the emphasis.
        for (int i = 0; i < points.size(); i += stride) {
            Vec3 p = points.get(i);
            player.level().addAlwaysVisibleParticle(
                    GolfVisualEffects.WHITE_DUST, true, p.x, p.y, p.z, 0, 0, 0);
        }

        if (!points.isEmpty()) {
            Vec3 end = points.get(points.size() - 1);
            if (phase == Phase.IDLE) {
                plannedDistanceBlocks = horizontalDistance(ball.position(), end);
            }

            if (clubItem.club() == ClubType.PUTTER) {
                player.level().addAlwaysVisibleParticle(
                        GolfVisualEffects.GOLD_DUST, true, end.x, end.y + 0.10, end.z, 0, 0, 0);
            } else {
                // Small planned-carry pin: four total particles, tall enough to read but not a cloud.
                player.level().addAlwaysVisibleParticle(
                        GolfVisualEffects.WHITE_DUST, true, end.x, end.y + 0.25, end.z, 0, 0, 0);
                player.level().addAlwaysVisibleParticle(
                        GolfVisualEffects.WHITE_DUST, true, end.x, end.y + 0.85, end.z, 0, 0, 0);
                player.level().addAlwaysVisibleParticle(
                        GolfVisualEffects.WHITE_DUST, true, end.x, end.y + 1.45, end.z, 0, 0, 0);
                player.level().addAlwaysVisibleParticle(
                        GolfVisualEffects.GOLD_DUST, true, end.x, end.y + 1.95, end.z, 0, 0, 0);
            }
        }
    }

    private static double horizontalDistance(Vec3 a, Vec3 b) {
        double dx = b.x - a.x;
        double dz = b.z - a.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static Optional<GolfBallEntity> nearestSwingableBall(LocalPlayer player) {
        return player.level()
                .getEntitiesOfClass(GolfBallEntity.class, player.getBoundingBox().inflate(GolfTuning.SWING_BALL_RADIUS))
                .stream()
                .filter(ball -> !ball.isInHole()
                        && ball.isStationary()
                        && ball.isGolfOwner(player.getUUID()))
                .min(Comparator.comparingDouble(player::distanceToSqr));
    }

    public static void reset() {
        phase = Phase.IDLE;
        phaseTicks = 0;
        lockedPower = 0;
        currentAccuracy = 0;
        cooldownTicks = 0;
        perfectFlashTicks = 0;
        previousUseDown = false;
        swingClub = null;
        swingTargetPower = 1.0f;
    }

    public static float plannedPower(ClubType club) {
        return TARGET_POWER.getOrDefault(club, 1.0f);
    }

    public static Phase phase() { return phase; }
    public static int phaseTicks() { return phaseTicks; }
    public static float lockedPower() { return lockedPower; }
    public static float meterPower() {
        return phase == Phase.POWER ? SwingMath.powerAtTick(phaseTicks) : 0.0f;
    }
    public static float currentPower() {
        return phase == Phase.POWER ? SwingMath.powerAtTick(phaseTicks) * swingTargetPower : lockedPower;
    }
    public static float currentAccuracy() {
        return phase == Phase.ACCURACY ? SwingMath.accuracyAtTick(phaseTicks) : currentAccuracy;
    }
    public static boolean showPerfectFlash() { return perfectFlashTicks > 0; }
    public static double plannedDistanceBlocks() { return plannedDistanceBlocks; }

    public static Optional<GolfBallEntity> trackedBall() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || trackedBallId < 0) return Optional.empty();
        net.minecraft.world.entity.Entity entity = mc.level.getEntity(trackedBallId);
        if (!(entity instanceof GolfBallEntity ball)) return Optional.empty();
        if (mc.player == null || !ball.isGolfOwner(mc.player.getUUID()) || ball.isInHole()) return Optional.empty();
        return Optional.of(ball);
    }
}
