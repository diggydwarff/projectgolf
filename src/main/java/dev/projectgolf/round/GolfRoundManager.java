package dev.projectgolf.round;

import dev.projectgolf.course.CourseDefinition;
import dev.projectgolf.course.GolfCourseSavedData;
import dev.projectgolf.course.HoleDefinition;
import dev.projectgolf.entity.GolfBallEntity;
import dev.projectgolf.golf.GolfTuning;
import dev.projectgolf.item.GolfBallItem;
import dev.projectgolf.registry.GolfBlocks;
import dev.projectgolf.registry.GolfEntities;
import dev.projectgolf.registry.GolfItems;
import dev.projectgolf.network.RoundStatePayload;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
    private static final String PREFERRED_BALL_COLOR = "PreferredBallColor";
    private static final String ROUND_ID = "RoundId";
    private static final String ROUND_GROUP_ID = "RoundGroupId";
    private static final String ROUND_STARTED_AT = "RoundStartedAt";
    private static final String ROUND_START_WEATHER = "RoundStartWeather";
    private static final String ROUND_START_TIME = "RoundStartTime";
    private static final String ROUND_HOLES = "RoundHoles";
    private static final String ROUND_PARTICIPANTS = "RoundParticipants";

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

    public static void setPreferredBallColor(ServerPlayer player, DyeColor color) {
        golfTag(player).putInt(PREFERRED_BALL_COLOR, color.getId());
    }

    public static DyeColor preferredBallColor(ServerPlayer player) {
        CompoundTag golf = golfTag(player);
        if (!golf.contains(PREFERRED_BALL_COLOR, Tag.TAG_INT)) return DyeColor.WHITE;
        return DyeColor.byId(golf.getInt(PREFERRED_BALL_COLOR));
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
        int rawStrokes = golf.getInt(STROKES);
        int penalties = golf.getInt(PENALTIES);
        int strokes = rawStrokes + penalties;
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

        HoleDefinition hole = currentHole.get();
        appendHoleScore(golf, new RoundHoleScore(hole.number(), hole.name(), par, strokes, penalties, true));
        golf.putInt(TOTAL_STROKES, golf.getInt(TOTAL_STROKES) + strokes);
        golf.putInt(TOTAL_PAR, golf.getInt(TOTAL_PAR) + par);
        collectParticipants(player);
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
            spawnOpeningBallOnTee(player, next);
            player.sendSystemMessage(Component.literal(
                    "Next: hole " + next.number() + " (par " + next.par() + "). Opening ball placed on the tee."));
            syncRound(player);
            return;
        }

        int total = golf.getInt(TOTAL_STROKES);
        int relative = total - golf.getInt(TOTAL_PAR);
        player.sendSystemMessage(Component.literal(
                "Round complete | " + total + " strokes | " + formatRelative(relative) + "."));
        finalizeRound(player, true, false);
    }

    public static void resetHole(ServerPlayer player) {
        CompoundTag golf = golfTag(player);
        golf.putInt(STROKES, 0);
        golf.putInt(PENALTIES, 0);
    }

    public static void setRound(ServerPlayer player, String course, int hole) {
        // Switching courses finalizes the old card instead of silently throwing the round away.
        if (hasActiveRound(player)) finalizeRound(player, false, false);
        UUID groupId = findJoinableGroup(player, course, hole).orElseGet(UUID::randomUUID);
        removeActiveBall(player);
        CompoundTag golf = golfTag(player);
        golf.putString(CURRENT_COURSE, course);
        golf.putInt(CURRENT_HOLE, hole);
        golf.putInt(TOTAL_STROKES, 0);
        golf.putInt(TOTAL_PAR, 0);
        golf.putUUID(ROUND_ID, UUID.randomUUID());
        golf.putUUID(ROUND_GROUP_ID, groupId);
        golf.putLong(ROUND_STARTED_AT, System.currentTimeMillis());
        golf.putString(ROUND_START_WEATHER, weather(player.serverLevel()));
        golf.putString(ROUND_START_TIME, timeOfDay(player.serverLevel()));
        golf.put(ROUND_HOLES, new ListTag());
        golf.put(ROUND_PARTICIPANTS, new ListTag());
        addParticipant(golf, player.getGameProfile().getName());
        resetHole(player);
        HoleDefinition openingHole = currentHoleDefinition(player).orElse(null);
        if (openingHole != null) spawnOpeningBallOnTee(player, openingHole);
        syncRound(player);
    }

    /**
     * Re-establish the legal opening lie for the current hole. This is intentionally strict only
     * before the first stroke; once the hole is underway Project Golf goes back to play-it-where-it-lies.
     */
    public static boolean resetOpeningBallToTee(ServerPlayer player) {
        HoleDefinition hole = currentHoleDefinition(player).orElse(null);
        if (hole == null) return false;
        removeActiveBall(player);
        return spawnOpeningBallOnTee(player, hole);
    }

    private static boolean spawnOpeningBallOnTee(ServerPlayer player, HoleDefinition hole) {
        if (hole.tee() == null) return false;
        String playerDimension = player.level().dimension().location().toString();
        if (!hole.dimension().equals(playerDimension)) {
            player.sendSystemMessage(Component.literal(
                    "Hole " + hole.number() + " tee is in " + hole.dimension() + "; opening ball was not spawned."));
            return false;
        }

        ServerLevel level = player.serverLevel();
        BlockPos tee = hole.tee();
        if (!level.getBlockState(tee).is(GolfBlocks.TEE_MARKER.get())) {
            player.sendSystemMessage(Component.literal(
                    "Hole " + hole.number() + " Tee Marker is missing at " + tee + "."));
            return false;
        }

        GolfBallEntity ball = GolfEntities.GOLF_BALL.get().create(level);
        if (ball == null) return false;
        Vec3 lie = new Vec3(tee.getX() + 0.5, tee.getY() + 1.14, tee.getZ() + 0.5);
        ball.setPos(lie.x, lie.y, lie.z);
        ball.setItem(GolfBallItem.coloredStack(preferredBallColor(player)));
        ball.setGolfOwner(player.getUUID());
        ball.setLastSafePosition(lie);
        if (!level.addFreshEntity(ball)) return false;
        setActiveBall(player, ball);
        return true;
    }

    public static boolean hasTakenStroke(ServerPlayer player) {
        return golfTag(player).getInt(STROKES) > 0;
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
        finalizeRound(player, false, true);
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

    /** Push the authoritative active-hole state used by the persistent HUD, Spotter and group board. */
    public static void syncRound(ServerPlayer player) {
        if (!hasActiveRound(player)) {
            PacketDistributor.sendToPlayer(player, RoundStatePayload.inactive());
            return;
        }
        collectParticipants(player);
        syncCoursePlayers(player.getServer(), currentCourseName(player));
    }

    private static void syncCoursePlayers(net.minecraft.server.MinecraftServer server, String courseName) {
        if (courseName == null || courseName.isBlank()) return;
        List<ServerPlayer> coursePlayers = server.getPlayerList().getPlayers().stream()
                .filter(GolfRoundManager::hasActiveRound)
                .filter(p -> currentCourseName(p).equalsIgnoreCase(courseName))
                .toList();
        for (ServerPlayer member : coursePlayers) {
            UUID groupId = roundGroupId(member);
            List<ServerPlayer> group = coursePlayers.stream()
                    .filter(p -> roundGroupId(p).equals(groupId))
                    .toList();
            syncOne(member, group);
        }
    }

    private static UUID roundGroupId(ServerPlayer player) {
        CompoundTag golf = golfTag(player);
        return golf.hasUUID(ROUND_GROUP_ID) ? golf.getUUID(ROUND_GROUP_ID) : player.getUUID();
    }

    private static Optional<UUID> findJoinableGroup(ServerPlayer player, String course, int hole) {
        return player.getServer().getPlayerList().getPlayers().stream()
                .filter(other -> other != player)
                .filter(GolfRoundManager::hasActiveRound)
                .filter(other -> currentCourseName(other).equalsIgnoreCase(course))
                .filter(other -> currentHoleNumber(other) == hole)
                .filter(other -> other.level() == player.level())
                .filter(other -> other.distanceToSqr(player) <= 32.0 * 32.0)
                .min(Comparator.comparingDouble(other -> other.distanceToSqr(player)))
                .map(GolfRoundManager::roundGroupId);
    }

    private static void syncOne(ServerPlayer player, List<ServerPlayer> group) {
        HoleDefinition hole = currentHoleDefinition(player).orElse(null);
        if (hole == null || hole.tee() == null || hole.cup() == null) {
            PacketDistributor.sendToPlayer(player, RoundStatePayload.inactive());
            return;
        }
        CompoundTag golf = golfTag(player);
        List<RoundStatePayload.PlayerLine> lines = group.stream()
                .map(member -> new RoundStatePayload.PlayerLine(
                        member.getGameProfile().getName(),
                        currentHoleNumber(member),
                        strokes(member),
                        totalStrokes(member),
                        golfTag(member).getInt(TOTAL_PAR)))
                .sorted(Comparator.comparingInt((RoundStatePayload.PlayerLine line) -> line.totalStrokes() - line.totalPar())
                        .thenComparingInt(RoundStatePayload.PlayerLine::totalStrokes)
                        .thenComparing(RoundStatePayload.PlayerLine::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
        PacketDistributor.sendToPlayer(player, new RoundStatePayload(
                true,
                golf.getString(CURRENT_COURSE),
                hole.number(),
                hole.par(),
                strokes(player),
                golf.getInt(TOTAL_STROKES),
                golf.getInt(TOTAL_PAR),
                hole.tee(),
                hole.cup(),
                lines));
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) syncRound(player);
    }

    private static void appendHoleScore(CompoundTag golf, RoundHoleScore score) {
        ListTag holes = golf.getList(ROUND_HOLES, Tag.TAG_COMPOUND);
        holes.add(score.save());
        golf.put(ROUND_HOLES, holes);
    }

    private static List<RoundHoleScore> roundHoles(CompoundTag golf) {
        List<RoundHoleScore> result = new ArrayList<>();
        ListTag holes = golf.getList(ROUND_HOLES, Tag.TAG_COMPOUND);
        for (int i = 0; i < holes.size(); i++) result.add(RoundHoleScore.load(holes.getCompound(i)));
        return result;
    }

    private static void collectParticipants(ServerPlayer player) {
        if (!hasActiveRound(player)) return;
        UUID groupId = roundGroupId(player);
        List<ServerPlayer> group = player.getServer().getPlayerList().getPlayers().stream()
                .filter(GolfRoundManager::hasActiveRound)
                .filter(p -> roundGroupId(p).equals(groupId))
                .toList();
        for (ServerPlayer a : group) {
            CompoundTag tag = golfTag(a);
            for (ServerPlayer b : group) addParticipant(tag, b.getGameProfile().getName());
        }
    }

    private static void addParticipant(CompoundTag golf, String name) {
        Set<String> names = new LinkedHashSet<>();
        ListTag current = golf.getList(ROUND_PARTICIPANTS, Tag.TAG_STRING);
        for (int i = 0; i < current.size(); i++) names.add(current.getString(i));
        names.add(name);
        ListTag next = new ListTag();
        for (String entry : names) next.add(StringTag.valueOf(entry));
        golf.put(ROUND_PARTICIPANTS, next);
    }

    private static List<String> participants(CompoundTag golf) {
        List<String> names = new ArrayList<>();
        ListTag list = golf.getList(ROUND_PARTICIPANTS, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) names.add(list.getString(i));
        return names;
    }

    private static void finalizeRound(ServerPlayer player, boolean completed, boolean explicitExit) {
        if (!hasActiveRound(player)) return;
        collectParticipants(player);
        CompoundTag golf = golfTag(player);
        String courseName = golf.getString(CURRENT_COURSE);
        CourseDefinition course = GolfCourseSavedData.get(player.getServer()).course(courseName).orElse(null);
        List<RoundHoleScore> holes = new ArrayList<>(roundHoles(golf));

        int currentStrokes = strokes(player);
        HoleDefinition current = currentHoleDefinition(player).orElse(null);
        boolean currentAlreadyRecorded = current != null && holes.stream().anyMatch(h -> h.hole() == current.number());
        if (!completed && current != null && !currentAlreadyRecorded && currentStrokes > 0) {
            holes.add(new RoundHoleScore(
                    current.number(), current.name(), current.par(), currentStrokes, golf.getInt(PENALTIES), false));
        }

        boolean fullCourseCompleted = completed && course != null
                && holes.size() == course.completeHoleCount()
                && course.holes().values().stream().filter(HoleDefinition::complete)
                        .allMatch(def -> holes.stream().anyMatch(score -> score.hole() == def.number() && score.completed()));
        String finishReason = explicitExit ? "EXITED" : (fullCourseCompleted ? "COMPLETED" : "PARTIAL");

        ScorecardData card = new ScorecardData(
                golf.hasUUID(ROUND_ID) ? golf.getUUID(ROUND_ID) : UUID.randomUUID(),
                player.getUUID(),
                player.getGameProfile().getName(),
                courseName,
                course == null ? "" : course.author(),
                course == null ? "" : course.description(),
                course == null ? "" : course.difficulty(),
                course == null ? "" : course.location(),
                golf.getLong(ROUND_STARTED_AT),
                System.currentTimeMillis(),
                golf.getString(ROUND_START_WEATHER),
                weather(player.serverLevel()),
                golf.getString(ROUND_START_TIME),
                timeOfDay(player.serverLevel()),
                fullCourseCompleted,
                finishReason,
                participants(golf),
                holes);

        GolfRecordsSavedData.get(player.getServer()).record(card);
        ItemStack paper = dev.projectgolf.item.ScorecardItem.create(GolfItems.SCORECARD.get(), card);
        if (!player.getInventory().add(paper)) player.drop(paper, false);
        player.sendSystemMessage(Component.literal(fullCourseCompleted
                ? "Official scorecard added to your inventory."
                : "Round scorecard added to your inventory."));

        removeActiveBall(player);
        golf.putString(CURRENT_COURSE, "");
        golf.putInt(CURRENT_HOLE, 0);
        golf.putInt(TOTAL_STROKES, 0);
        golf.putInt(TOTAL_PAR, 0);
        golf.remove(ROUND_ID);
        golf.remove(ROUND_GROUP_ID);
        golf.remove(ROUND_STARTED_AT);
        golf.remove(ROUND_START_WEATHER);
        golf.remove(ROUND_START_TIME);
        golf.remove(ROUND_HOLES);
        golf.remove(ROUND_PARTICIPANTS);
        resetHole(player);
        PacketDistributor.sendToPlayer(player, RoundStatePayload.inactive());
        syncCoursePlayers(player.getServer(), courseName);
    }

    private static String weather(ServerLevel level) {
        if (level.isThundering()) return "Thunderstorm";
        if (level.isRaining()) return "Rain";
        return "Clear";
    }

    private static String timeOfDay(ServerLevel level) {
        long time = Math.floorMod(level.getDayTime(), 24000L);
        if (time < 1000L) return "Sunrise";
        if (time < 6000L) return "Morning";
        if (time < 12000L) return "Afternoon";
        if (time < 13500L) return "Sunset";
        if (time < 22000L) return "Night";
        return "Dawn";
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
