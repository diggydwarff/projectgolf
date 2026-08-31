package dev.projectgolf.round;

import dev.projectgolf.course.GolfCourseSavedData;
import dev.projectgolf.course.HoleDefinition;
import dev.projectgolf.entity.GolfBallEntity;
import dev.projectgolf.golf.GolfTuning;
import dev.projectgolf.network.RoundStatePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;

public final class GolfRoundManager {
    private GolfRoundManager() {}

    private static final String ROOT = "ProjectGolf";
    private static final String STROKES = "Strokes";
    private static final String PENALTIES = "Penalties";
    private static final String CURRENT_COURSE = "CurrentCourse";
    private static final String CURRENT_HOLE = "CurrentHole";
    private static final String LAST_SWING_TICK = "LastSwingTick";
    private static final String TOTAL_STROKES = "TotalStrokes";
    private static final String TOTAL_PAR = "TotalPar";
    private static final String ACTIVE_BALL = "ActiveBall";

    public static Optional<GolfBallEntity> findOwnedBall(ServerPlayer player) {
        return player.serverLevel()
                .getEntitiesOfClass(GolfBallEntity.class,
                        player.getBoundingBox().inflate(GolfTuning.SWING_BALL_RADIUS),
                        ball -> ball.isGolfOwner(player.getUUID()) && !ball.isInHole())
                .stream()
                .min(Comparator.comparingDouble(player::distanceToSqr));
    }

    /**
     * Registers the player's currently active competitive ball. This avoids whole-world entity
     * scans when replacing a ball on large servers.
     */
    public static void setActiveBall(ServerPlayer player, GolfBallEntity ball) {
        golfTag(player).putUUID(ACTIVE_BALL, ball.getUUID());
    }

    /**
     * Removes only the tracked active ball using ServerLevel's UUID lookup. At most one lookup per
     * loaded dimension is performed; no entity collections are scanned.
     */
    public static void removeActiveBall(ServerPlayer player) {
        CompoundTag golf = golfTag(player);
        if (!golf.hasUUID(ACTIVE_BALL)) return;

        UUID ballId = golf.getUUID(ACTIVE_BALL);
        for (ServerLevel level : player.getServer().getAllLevels()) {
            Entity entity = level.getEntity(ballId);
            if (entity instanceof GolfBallEntity ball && ball.isGolfOwner(player.getUUID())) {
                ball.discard();
                break;
            }
        }
        golf.remove(ACTIVE_BALL);
    }

    public static void clearActiveBall(ServerPlayer player, UUID ballId) {
        CompoundTag golf = golfTag(player);
        if (golf.hasUUID(ACTIVE_BALL) && golf.getUUID(ACTIVE_BALL).equals(ballId)) {
            golf.remove(ACTIVE_BALL);
        }
    }

    public static boolean isActiveBall(ServerPlayer player, UUID ballId) {
        CompoundTag golf = golfTag(player);
        return golf.hasUUID(ACTIVE_BALL) && golf.getUUID(ACTIVE_BALL).equals(ballId);
    }

    public static boolean canSwing(ServerPlayer player) {
        CompoundTag golf = golfTag(player);
        long now = player.level().getGameTime();
        return now - golf.getLong(LAST_SWING_TICK) >= GolfTuning.SWING_COOLDOWN_TICKS;
    }

    public static void recordStroke(ServerPlayer player) {
        CompoundTag golf = golfTag(player);
        golf.putInt(STROKES, golf.getInt(STROKES) + 1);
        golf.putLong(LAST_SWING_TICK, player.level().getGameTime());
        syncRound(player);
    }

    public static void addPenalty(ServerPlayer player, int strokes, String reason) {
        int applied = Math.max(0, strokes);
        if (applied == 0) return;
        CompoundTag golf = golfTag(player);
        golf.putInt(PENALTIES, golf.getInt(PENALTIES) + applied);
        player.sendSystemMessage(Component.literal(reason + " +" + applied + " penalty"));
        syncRound(player);
    }

    public static void finishHole(ServerPlayer player) {
        CompoundTag golf = golfTag(player);
        int strokes = golf.getInt(STROKES) + golf.getInt(PENALTIES);
        Optional<HoleDefinition> currentHole = currentHoleDefinition(player);
        int par = currentHole.map(HoleDefinition::par).orElse(0);

        String relative = par > 0 ? formatRelative(strokes - par) : "";
        player.sendSystemMessage(Component.literal(
                "Holed out in " + strokes + " stroke" + (strokes == 1 ? "" : "s")
                        + (par > 0 ? " (par " + par + ", " + relative + ")" : "") + "."));

        // Practice holes are independent attempts, not a hidden never-ending round total.
        if (currentHole.isEmpty()) {
            resetHole(player);
            return;
        }

        golf.putInt(TOTAL_STROKES, golf.getInt(TOTAL_STROKES) + strokes);
        golf.putInt(TOTAL_PAR, golf.getInt(TOTAL_PAR) + par);
        advanceRound(player, golf);
    }

    private static void advanceRound(ServerPlayer player, CompoundTag golf) {
        String courseName = golf.getString(CURRENT_COURSE);
        int currentHole = golf.getInt(CURRENT_HOLE);
        if (courseName.isBlank() || currentHole <= 0) {
            resetHole(player);
            return;
        }

        var course = GolfCourseSavedData.get(player.getServer()).course(courseName).orElse(null);
        HoleDefinition next = course == null ? null : course.nextCompleteHoleAfter(currentHole).orElse(null);
        if (next != null) {
            golf.putInt(CURRENT_HOLE, next.number());
            resetHole(player);
            player.sendSystemMessage(Component.literal(
                    "Next: hole " + next.number() + " (par " + next.par() + ")."));
            syncRound(player);
            return;
        }

        int total = golf.getInt(TOTAL_STROKES);
        int relative = total - golf.getInt(TOTAL_PAR);
        player.sendSystemMessage(Component.literal(
                "Round complete | " + total + " strokes | " + formatRelative(relative) + "."));
        golf.putString(CURRENT_COURSE, "");
        golf.putInt(CURRENT_HOLE, 0);
        resetHole(player);
        syncRound(player);
    }

    public static void resetHole(ServerPlayer player) {
        CompoundTag golf = golfTag(player);
        golf.putInt(STROKES, 0);
        golf.putInt(PENALTIES, 0);
    }

    public static void setRound(ServerPlayer player, String course, int hole) {
        // Starting/jumping to a tee is a clean start. Do not leave an old competitive ball behind.
        removeActiveBall(player);
        CompoundTag golf = golfTag(player);
        golf.putString(CURRENT_COURSE, course);
        golf.putInt(CURRENT_HOLE, hole);
        golf.putInt(TOTAL_STROKES, 0);
        golf.putInt(TOTAL_PAR, 0);
        resetHole(player);
        syncRound(player);
    }


    public static boolean hasActiveRound(ServerPlayer player) {
        CompoundTag golf = golfTag(player);
        return !golf.getString(CURRENT_COURSE).isBlank() && golf.getInt(CURRENT_HOLE) > 0;
    }

    public static String currentCourseName(ServerPlayer player) {
        return golfTag(player).getString(CURRENT_COURSE);
    }

    public static int currentHoleNumber(ServerPlayer player) {
        return golfTag(player).getInt(CURRENT_HOLE);
    }

    public static boolean isPlaying(ServerPlayer player, String course, int hole) {
        return currentCourseName(player).equalsIgnoreCase(course) && currentHoleNumber(player) == hole;
    }

    public static void leaveRound(ServerPlayer player) {
        removeActiveBall(player);
        CompoundTag golf = golfTag(player);
        golf.putString(CURRENT_COURSE, "");
        golf.putInt(CURRENT_HOLE, 0);
        golf.putInt(TOTAL_STROKES, 0);
        golf.putInt(TOTAL_PAR, 0);
        resetHole(player);
        syncRound(player);
    }

    public static Optional<HoleDefinition> currentHoleDefinition(ServerPlayer player) {
        CompoundTag golf = golfTag(player);
        String course = golf.getString(CURRENT_COURSE);
        int hole = golf.getInt(CURRENT_HOLE);
        if (course.isBlank() || hole <= 0) return Optional.empty();
        return GolfCourseSavedData.get(player.getServer())
                .course(course)
                .map(c -> c.hole(hole));
    }

    public static boolean isExpectedCup(ServerPlayer player, ResourceKey<Level> ballDimension, BlockPos cupPos) {
        var hole = currentHoleDefinition(player);
        if (hole.isEmpty()) return true; // Practice mode: any cup works.
        HoleDefinition def = hole.get();
        return def.cup() != null
                && def.dimension().equals(ballDimension.location().toString())
                && def.cup().equals(cupPos);
    }

    /** Push the authoritative active-hole state used by the persistent HUD and Golden Spotter. */
    public static void syncRound(ServerPlayer player) {
        if (!hasActiveRound(player)) {
            PacketDistributor.sendToPlayer(player, RoundStatePayload.inactive());
            return;
        }
        HoleDefinition hole = currentHoleDefinition(player).orElse(null);
        if (hole == null || hole.tee() == null || hole.cup() == null) {
            PacketDistributor.sendToPlayer(player, RoundStatePayload.inactive());
            return;
        }
        CompoundTag golf = golfTag(player);
        PacketDistributor.sendToPlayer(player, new RoundStatePayload(
                true,
                golf.getString(CURRENT_COURSE),
                hole.number(),
                hole.par(),
                strokes(player),
                golf.getInt(TOTAL_STROKES),
                golf.getInt(TOTAL_PAR),
                hole.tee(),
                hole.cup()));
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) syncRound(player);
    }

    public static int totalStrokes(ServerPlayer player) {
        return golfTag(player).getInt(TOTAL_STROKES);
    }

    public static int totalRelativeToPar(ServerPlayer player) {
        CompoundTag golf = golfTag(player);
        return golf.getInt(TOTAL_STROKES) - golf.getInt(TOTAL_PAR);
    }

    private static String formatRelative(int value) {
        if (value == 0) return "E";
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    public static int strokes(ServerPlayer player) {
        CompoundTag golf = golfTag(player);
        return golf.getInt(STROKES) + golf.getInt(PENALTIES);
    }

    /** Preserve active round/scoring state across death respawns. */
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        CompoundTag original = event.getOriginal().getPersistentData();
        if (original.contains(ROOT, Tag.TAG_COMPOUND)) {
            event.getEntity().getPersistentData().put(ROOT, original.getCompound(ROOT).copy());
        }
    }

    private static CompoundTag golfTag(ServerPlayer player) {
        CompoundTag persistent = player.getPersistentData();
        if (!persistent.contains(ROOT, Tag.TAG_COMPOUND)) {
            persistent.put(ROOT, new CompoundTag());
        }
        return persistent.getCompound(ROOT);
    }
}
