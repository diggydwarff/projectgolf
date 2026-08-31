package dev.projectgolf.course;

import dev.projectgolf.network.CourseUiPayload;
import dev.projectgolf.network.HoleViewPayload;
import dev.projectgolf.network.ScorecardPayload;
import dev.projectgolf.round.GolfRecordsSavedData;
import dev.projectgolf.round.GolfRoundManager;
import dev.projectgolf.round.ScorecardData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Comparator;
import java.util.List;

/** Authoritative snapshots/actions backing the new course-management screens. */
public final class CourseUiService {
    private CourseUiService() {}

    public static void openBrowser(ServerPlayer player) { send(player, "browser"); }
    public static void openHistory(ServerPlayer player) { send(player, "history"); }
    public static void openBuilder(ServerPlayer player) {
        if (!player.hasPermissions(2)) {
            player.sendSystemMessage(Component.literal("Course Builder requires operator permission."));
            return;
        }
        send(player, "builder");
    }

    public static void handle(ServerPlayer player, CompoundTag action) {
        String type = action.getString("Action");
        String courseName = action.getString("Course").trim();
        GolfCourseSavedData courses = GolfCourseSavedData.get(player.getServer());
        CourseDefinition course = courses.course(courseName).orElse(null);

        switch (type) {
            case "refresh_browser" -> {
                // Preserve the course/hole the player was actually looking at when leaving Course
                // Design so the browser and Course Designer wand do not silently jump elsewhere.
                syncBuilderSelection(player, course, action.getInt("Hole"));
                openBrowser(player);
            }
            case "refresh_history" -> openHistory(player);
            case "refresh_builder" -> {
                // Entering Course Design from the clubhouse must edit the highlighted course, not
                // whichever course happened to be armed the last time the wand was used.
                syncBuilderSelection(player, course, action.getInt("Hole"));
                openBuilder(player);
            }
            case "play" -> {
                if (course == null) return;
                HoleDefinition hole = action.getInt("Hole") > 0 ? course.hole(action.getInt("Hole")) : course.firstCompleteHole().orElse(null);
                if (hole == null || !hole.complete()) {
                    player.sendSystemMessage(Component.literal("That course has no playable complete hole yet. Link both a Tee Marker and Golf Cup first."));
                    return;
                }
                if (!hole.dimension().equals(player.level().dimension().location().toString())) {
                    player.sendSystemMessage(Component.literal("That tee is in " + hole.dimension() + "."));
                    return;
                }
                GolfRoundManager.setRound(player, course.name(), hole.number());
            }
            case "view", "flyover" -> {
                if (course == null) return;
                HoleDefinition hole = course.hole(Math.max(1, action.getInt("Hole")));
                if (hole == null || !hole.complete()) {
                    player.sendSystemMessage(Component.literal("That hole is incomplete. Link both its Tee Marker and Golf Cup first."));
                    return;
                }
                if (!hole.dimension().equals(player.level().dimension().location().toString())) {
                    player.sendSystemMessage(Component.literal("That hole is in " + hole.dimension()
                            + ". Travel there before viewing it."));
                    return;
                }
                PacketDistributor.sendToPlayer(player, new HoleViewPayload(course.name(), hole.number(), hole.par(),
                        hole.tee(), hole.cup(), hole.guidePoints(), type.equals("flyover")));
            }
            case "open_round" -> {
                String id = action.getString("RoundId");
                for (ScorecardData round : GolfRecordsSavedData.get(player.getServer()).forPlayer(player.getUUID())) {
                    if (round.roundId().toString().equals(id)) {
                        PacketDistributor.sendToPlayer(player, new ScorecardPayload(round.save()));
                        return;
                    }
                }
            }
            default -> {
                if (!player.hasPermissions(2)) return;
                handleBuilder(player, courses, course, action, type);
            }
        }
    }

    private static void handleBuilder(ServerPlayer player, GolfCourseSavedData data, CourseDefinition course,
                                      CompoundTag action, String type) {
        switch (type) {
            case "create_course" -> {
                String name = clean(action.getString("Name"), 48);
                if (name.isBlank()) {
                    player.sendSystemMessage(Component.literal("Enter a course name first."));
                    return;
                }
                CourseDefinition existing = data.course(name).orElse(null);
                if (existing != null) {
                    player.sendSystemMessage(Component.literal("Course already exists: " + existing.name()));
                    int first = Math.max(1, existing.holes().keySet().stream().findFirst().orElse(1));
                    HoleDefinition firstHole = existing.hole(first);
                    CourseBuilderManager.select(player, existing.name(), first, firstHole == null ? 4 : firstHole.par());
                } else {
                    CourseDefinition created = data.createCourse(name);
                    created.putHole(new HoleDefinition(1, 4, "", null, null, List.of(), ""));
                    data.changed();
                    CourseBuilderManager.select(player, created.name(), 1, 4);
                    player.sendSystemMessage(Component.literal("Created " + created.name() + ". Set course details, then configure hole 1."));
                }
            }
            case "save_meta" -> {
                if (course == null) return;
                course.setAuthor(clean(action.getString("Author"), 64));
                course.setDifficulty(clean(action.getString("Difficulty"), 32));
                course.setLocation(clean(action.getString("Location"), 64));
                course.setDescription(clean(action.getString("Description"), 160));
                data.changed();
                int selected = clamp(action.getInt("Hole"), 1, 99);
                int par = clamp(action.getInt("Par"), 1, 10);
                CourseBuilderManager.select(player, course.name(), selected, par);
            }
            case "save_hole" -> {
                if (course == null) return;
                int number = clamp(action.getInt("Hole"), 1, 99);
                int par = clamp(action.getInt("Par"), 1, 10);
                HoleDefinition old = course.getOrCreateHole(number);
                course.putHole(new HoleDefinition(number, par, old.dimension(), old.tee(), old.cup(), old.guidePoints(),
                        clean(action.getString("Name"), 48)));
                data.changed();
                CourseBuilderManager.select(player, course.name(), number, par);
            }
            case "select_hole" -> {
                if (course == null) return;
                int number = clamp(action.getInt("Hole"), 1, 99);
                int par = clamp(action.getInt("Par"), 1, 10);
                HoleDefinition old = course.getOrCreateHole(number);
                course.putHole(new HoleDefinition(number, par, old.dimension(), old.tee(), old.cup(), old.guidePoints(),
                        clean(action.getString("Name"), 48)));
                data.changed();
                CourseBuilderManager.select(player, course.name(), number, par);
                player.sendSystemMessage(Component.literal("Course Designer armed for " + course.name() + " hole " + number
                        + ". Link the Tee Marker and Golf Cup; sneak-use terrain for optional guide points."));
                return; // Player is returning to the world to use the wand; do not immediately reopen the GUI.
            }
            case "clear_guides" -> {
                if (course == null) return;
                HoleDefinition old = course.hole(clamp(action.getInt("Hole"), 1, 99));
                if (old != null) {
                    course.putHole(old.clearGuidePoints());
                    data.changed();
                    CourseBuilderManager.select(player, course.name(), old.number(), old.par());
                }
            }
            default -> { return; }
        }
        openBuilder(player);
    }

    private static void send(ServerPlayer player, String mode) {
        CompoundTag root = new CompoundTag();
        root.putString("Mode", mode);
        root.putBoolean("CanBuild", player.hasPermissions(2));
        ListTag list = new ListTag();
        GolfCourseSavedData data = GolfCourseSavedData.get(player.getServer());
        for (CourseDefinition course : data.courses().stream().sorted(Comparator.comparing(CourseDefinition::name, String.CASE_INSENSITIVE_ORDER)).toList()) {
            CompoundTag c = new CompoundTag();
            c.putString("Name", course.name());
            c.putString("Author", course.author());
            c.putString("Description", course.description());
            c.putString("Difficulty", course.difficulty());
            c.putString("Location", course.location());
            c.putInt("Par", course.totalPar());
            c.putInt("CompleteHoles", course.completeHoleCount());
            c.putInt("ConfiguredHoles", course.holes().size());
            ListTag holes = new ListTag();
            for (HoleDefinition hole : course.holes().values()) {
                CompoundTag h = new CompoundTag();
                h.putInt("Number", hole.number()); h.putInt("Par", hole.par()); h.putString("Name", hole.name());
                h.putBoolean("Complete", hole.complete()); h.putBoolean("HasTee", hole.tee() != null); h.putBoolean("HasCup", hole.cup() != null);
                h.putInt("Guides", hole.guidePoints().size());
                holes.add(h);
            }
            c.put("Holes", holes);
            List<ScorecardData> board = GolfRecordsSavedData.get(player.getServer()).completedLeaderboard(course.name());
            if (!board.isEmpty()) {
                ScorecardData best = board.get(0);
                c.putString("RecordPlayer", best.playerName());
                c.putInt("RecordScore", best.relativeToPar());
                c.putInt("RecordStrokes", best.totalStrokes());
            }
            list.add(c);
        }
        root.put("Courses", list);

        if (mode.equals("history")) {
            ListTag rounds = new ListTag();
            for (ScorecardData round : GolfRecordsSavedData.get(player.getServer()).forPlayer(player.getUUID()).stream().limit(64).toList()) {
                rounds.add(round.save());
            }
            root.put("Rounds", rounds);
        }
        CourseBuilderManager.selection(player).ifPresent(sel -> {
            root.putString("SelectedCourse", sel.course()); root.putInt("SelectedHole", sel.hole()); root.putInt("SelectedPar", sel.par());
        });
        PacketDistributor.sendToPlayer(player, new CourseUiPayload(root));
    }

    private static void syncBuilderSelection(ServerPlayer player, CourseDefinition course, int requestedHole) {
        if (course == null) return;
        HoleDefinition hole = requestedHole > 0 ? course.hole(requestedHole) : null;
        if (hole == null) hole = course.holes().values().stream().findFirst().orElse(null);
        CourseBuilderManager.select(player, course.name(), hole == null ? 1 : hole.number(), hole == null ? 4 : hole.par());
    }

    private static String clean(String value, int max) {
        String s = value == null ? "" : value.trim();
        return s.length() <= max ? s : s.substring(0, max);
    }
    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
}
