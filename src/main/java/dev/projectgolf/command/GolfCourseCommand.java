package dev.projectgolf.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.projectgolf.course.CourseBuilderManager;
import dev.projectgolf.course.CourseDefinition;
import dev.projectgolf.course.GolfCourseSavedData;
import dev.projectgolf.course.HoleDefinition;
import dev.projectgolf.round.GolfRoundManager;
import dev.projectgolf.network.HoleViewPayload;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.stream.Collectors;

public final class GolfCourseCommand {
    private GolfCourseCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("golf")
                .then(Commands.literal("start")
                        .then(Commands.argument("course", StringArgumentType.string())
                                .executes(ctx -> startFirst(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "course")))
                                .then(Commands.argument("hole", IntegerArgumentType.integer(1, 99))
                                        .executes(ctx -> startAt(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "course"),
                                                IntegerArgumentType.getInteger(ctx, "hole"))))))
                .then(Commands.literal("play")
                        .then(Commands.argument("course", StringArgumentType.string())
                                .executes(ctx -> startFirst(
                                        ctx.getSource(),
                                        StringArgumentType.getString(ctx, "course")))
                                .then(Commands.argument("hole", IntegerArgumentType.integer(1, 99))
                                        .executes(ctx -> startAt(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "course"),
                                                IntegerArgumentType.getInteger(ctx, "hole"))))))
                .then(Commands.literal("resume")
                        .executes(ctx -> resume(ctx.getSource())))
                .then(Commands.literal("leave")
                        .executes(ctx -> leave(ctx.getSource())))
                .then(Commands.literal("status")
                        .executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("courses")
                        .executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("view")
                        .executes(ctx -> viewCurrent(ctx.getSource(), false))
                        .then(Commands.argument("course", StringArgumentType.string())
                                .then(Commands.argument("hole", IntegerArgumentType.integer(1, 99))
                                        .executes(ctx -> viewAt(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "course"),
                                                IntegerArgumentType.getInteger(ctx, "hole"),
                                                false)))))
                .then(Commands.literal("flyover")
                        .executes(ctx -> viewCurrent(ctx.getSource(), true))
                        .then(Commands.argument("course", StringArgumentType.string())
                                .then(Commands.argument("hole", IntegerArgumentType.integer(1, 99))
                                        .executes(ctx -> viewAt(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "course"),
                                                IntegerArgumentType.getInteger(ctx, "hole"),
                                                true)))))
                .then(Commands.literal("course")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("create")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .executes(ctx -> create(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name")))))
                        .then(Commands.literal("list")
                                .executes(ctx -> list(ctx.getSource())))
                        .then(Commands.literal("edit")
                                .then(Commands.argument("course", StringArgumentType.string())
                                        .then(Commands.argument("hole", IntegerArgumentType.integer(1, 99))
                                                .then(Commands.argument("par", IntegerArgumentType.integer(1, 10))
                                                        .executes(ctx -> edit(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "course"),
                                                                IntegerArgumentType.getInteger(ctx, "hole"),
                                                                IntegerArgumentType.getInteger(ctx, "par")))))))
                        .then(Commands.literal("clearedit")
                                .executes(ctx -> clearEdit(ctx.getSource())))
                        .then(Commands.literal("settee")
                                .then(Commands.argument("course", StringArgumentType.string())
                                        .then(Commands.argument("hole", IntegerArgumentType.integer(1, 99))
                                                .then(Commands.argument("par", IntegerArgumentType.integer(1, 10))
                                                        .executes(ctx -> setTee(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "course"),
                                                                IntegerArgumentType.getInteger(ctx, "hole"),
                                                                IntegerArgumentType.getInteger(ctx, "par")))))))
                        .then(Commands.literal("setcup")
                                .then(Commands.argument("course", StringArgumentType.string())
                                        .then(Commands.argument("hole", IntegerArgumentType.integer(1, 99))
                                                .executes(ctx -> setCup(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "course"),
                                                        IntegerArgumentType.getInteger(ctx, "hole"))))))
                        .then(Commands.literal("addguide")
                                .then(Commands.argument("course", StringArgumentType.string())
                                        .then(Commands.argument("hole", IntegerArgumentType.integer(1, 99))
                                                .executes(ctx -> addGuide(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "course"),
                                                        IntegerArgumentType.getInteger(ctx, "hole"))))))
                        .then(Commands.literal("clearguides")
                                .then(Commands.argument("course", StringArgumentType.string())
                                        .then(Commands.argument("hole", IntegerArgumentType.integer(1, 99))
                                                .executes(ctx -> clearGuides(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "course"),
                                                        IntegerArgumentType.getInteger(ctx, "hole"))))))
                        .then(Commands.literal("validate")
                                .then(Commands.argument("course", StringArgumentType.string())
                                        .executes(ctx -> validate(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "course")))))
                        .then(Commands.literal("info")
                                .then(Commands.argument("course", StringArgumentType.string())
                                        .executes(ctx -> info(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "course")))))));
    }

    private static int create(CommandSourceStack source, String name) {
        GolfCourseSavedData data = GolfCourseSavedData.get(source.getServer());
        if (data.course(name).isPresent()) {
            source.sendFailure(Component.literal("Course already exists: " + name));
            return 0;
        }
        data.createCourse(name);
        source.sendSuccess(() -> Component.literal(
                "Created golf course " + name + ". No course bounds are required."), true);
        return 1;
    }

    private static int list(CommandSourceStack source) {
        GolfCourseSavedData data = GolfCourseSavedData.get(source.getServer());
        if (data.courses().isEmpty()) {
            source.sendSuccess(() -> Component.literal("No Project Golf courses configured."), false);
            return 1;
        }
        for (CourseDefinition course : data.courses()) {
            source.sendSuccess(() -> Component.literal(
                    course.name() + " | " + course.completeHoleCount() + " ready / "
                            + course.holes().size() + " configured holes"), false);
        }
        return 1;
    }

    private static int edit(CommandSourceStack source, String courseName, int holeNumber, int par)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        GolfCourseSavedData data = GolfCourseSavedData.get(source.getServer());
        if (data.course(courseName).isEmpty()) {
            source.sendFailure(Component.literal("Unknown course: " + courseName));
            return 0;
        }
        CourseBuilderManager.select(player, courseName, holeNumber, par);
        source.sendSuccess(() -> Component.literal(
                "Editing " + courseName + " hole " + holeNumber + " par " + par
                        + ". Use the Course Designer on its Tee Marker and Golf Cup."), false);
        return 1;
    }

    private static int clearEdit(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        CourseBuilderManager.clear(player);
        source.sendSuccess(() -> Component.literal("Cleared Project Golf course edit selection."), false);
        return 1;
    }

    private static int setTee(CommandSourceStack source, String courseName, int holeNumber, int par)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        GolfCourseSavedData data = GolfCourseSavedData.get(source.getServer());
        CourseDefinition course = data.course(courseName).orElse(null);
        if (course == null) {
            source.sendFailure(Component.literal("Unknown course: " + courseName));
            return 0;
        }

        HoleDefinition old = course.getOrCreateHole(holeNumber);
        String currentDimension = player.level().dimension().location().toString();
        if (old.cup() != null && !old.dimension().isBlank() && !old.dimension().equals(currentDimension)) {
            source.sendFailure(Component.literal(
                    "Hole already has a cup in " + old.dimension() + "; tee and cup must share a dimension."));
            return 0;
        }
        course.putHole(old.withTee(currentDimension, player.blockPosition().below(), par));
        data.changed();

        source.sendSuccess(() -> Component.literal(
                "Set " + courseName + " hole " + holeNumber + " tee at "
                        + player.blockPosition().below() + " par " + par), true);
        return 1;
    }

    private static int setCup(CommandSourceStack source, String courseName, int holeNumber)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        GolfCourseSavedData data = GolfCourseSavedData.get(source.getServer());
        CourseDefinition course = data.course(courseName).orElse(null);
        if (course == null) {
            source.sendFailure(Component.literal("Unknown course: " + courseName));
            return 0;
        }

        HoleDefinition old = course.getOrCreateHole(holeNumber);
        String currentDimension = player.level().dimension().location().toString();
        if (old.tee() != null && !old.dimension().isBlank() && !old.dimension().equals(currentDimension)) {
            source.sendFailure(Component.literal(
                    "Hole already has a tee in " + old.dimension() + "; tee and cup must share a dimension."));
            return 0;
        }
        course.putHole(old.withCup(currentDimension, player.blockPosition().below()));
        data.changed();

        source.sendSuccess(() -> Component.literal(
                "Set " + courseName + " hole " + holeNumber + " cup at " + player.blockPosition().below()), true);
        return 1;
    }

    private static int validate(CommandSourceStack source, String courseName) {
        GolfCourseSavedData data = GolfCourseSavedData.get(source.getServer());
        CourseDefinition course = data.course(courseName).orElse(null);
        if (course == null) {
            source.sendFailure(Component.literal("Unknown course: " + courseName));
            return 0;
        }

        int ready = course.completeHoleCount();
        int incomplete = course.holes().size() - ready;
        if (ready == 0) {
            source.sendFailure(Component.literal(
                    "Course " + course.name() + " has no complete holes. Each hole only needs a tee, cup, and par."));
            return 0;
        }

        String order = course.holes().values().stream()
                .filter(HoleDefinition::complete)
                .map(hole -> Integer.toString(hole.number()))
                .collect(Collectors.joining(", "));
        source.sendSuccess(() -> Component.literal(
                "Course " + course.name() + " ready: " + ready + " hole(s), play order " + order
                        + (incomplete > 0 ? " | " + incomplete + " incomplete ignored" : "")), false);
        source.sendSuccess(() -> Component.literal(
                "World terrain is playable by default. No fairway/woods/course bounds are required; OOB is explicit only."), false);
        return 1;
    }

    private static int info(CommandSourceStack source, String courseName) {
        GolfCourseSavedData data = GolfCourseSavedData.get(source.getServer());
        CourseDefinition course = data.course(courseName).orElse(null);
        if (course == null) {
            source.sendFailure(Component.literal("Unknown course: " + courseName));
            return 0;
        }

        source.sendSuccess(() -> Component.literal(
                "Course " + course.name() + " - " + course.holes().size() + " configured holes"), false);
        for (HoleDefinition hole : course.holes().values()) {
            source.sendSuccess(() -> Component.literal(
                    "Hole " + hole.number() + " par " + hole.par()
                            + " dim=" + hole.dimension() + " tee=" + hole.tee() + " cup=" + hole.cup()
                            + " guides=" + hole.guidePoints().size()
                            + (hole.complete() ? " [ready]" : " [incomplete]")), false);
        }
        return 1;
    }

    private static int addGuide(CommandSourceStack source, String courseName, int holeNumber)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        GolfCourseSavedData data = GolfCourseSavedData.get(source.getServer());
        CourseDefinition course = data.course(courseName).orElse(null);
        if (course == null) {
            source.sendFailure(Component.literal("Unknown course: " + courseName));
            return 0;
        }
        HoleDefinition hole = course.hole(holeNumber);
        if (hole == null) {
            source.sendFailure(Component.literal("Unknown hole " + holeNumber + " on " + courseName + "."));
            return 0;
        }
        String dimension = player.level().dimension().location().toString();
        if (!hole.dimension().isBlank() && !hole.dimension().equals(dimension)) {
            source.sendFailure(Component.literal("Hole is configured in " + hole.dimension() + "."));
            return 0;
        }
        if (hole.guidePoints().size() >= 16) {
            source.sendFailure(Component.literal("Hole already has the maximum 16 guide points."));
            return 0;
        }
        BlockPos point = player.blockPosition().below();
        HoleDefinition updated = hole.withGuidePoint(point);
        course.putHole(updated);
        data.changed();
        source.sendSuccess(() -> Component.literal(
                "Added presentation guide point " + updated.guidePoints().size() + " at " + point
                        + ". It does not define bounds or affect legal play."), false);
        return 1;
    }

    private static int clearGuides(CommandSourceStack source, String courseName, int holeNumber) {
        GolfCourseSavedData data = GolfCourseSavedData.get(source.getServer());
        CourseDefinition course = data.course(courseName).orElse(null);
        HoleDefinition hole = course == null ? null : course.hole(holeNumber);
        if (hole == null) {
            source.sendFailure(Component.literal("Unknown course/hole."));
            return 0;
        }
        int oldCount = hole.guidePoints().size();
        course.putHole(hole.clearGuidePoints());
        data.changed();
        source.sendSuccess(() -> Component.literal("Cleared " + oldCount + " guide point(s) from hole " + holeNumber + "."), false);
        return 1;
    }

    private static int viewCurrent(CommandSourceStack source, boolean flyover) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        String courseName = GolfRoundManager.currentCourseName(player);
        int hole = GolfRoundManager.currentHoleNumber(player);
        if (courseName.isBlank() || hole <= 0) {
            source.sendFailure(Component.literal("No active Project Golf hole. Use /golf view <course> <hole>."));
            return 0;
        }
        return viewAt(source, courseName, hole, flyover);
    }

    private static int viewAt(CommandSourceStack source, String courseName, int holeNumber, boolean flyover)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        GolfCourseSavedData data = GolfCourseSavedData.get(source.getServer());
        CourseDefinition course = data.course(courseName).orElse(null);
        HoleDefinition hole = course == null ? null : course.hole(holeNumber);
        if (hole == null || !hole.complete()) {
            source.sendFailure(Component.literal("Course/hole is missing or incomplete."));
            return 0;
        }
        String dimension = player.level().dimension().location().toString();
        if (!hole.dimension().equals(dimension)) {
            source.sendFailure(Component.literal(
                    "Hole is configured in " + hole.dimension() + ", but you are in " + dimension + "."));
            return 0;
        }

        PacketDistributor.sendToPlayer(player, new HoleViewPayload(
                course.name(), hole.number(), hole.par(), hole.tee(), hole.cup(), hole.guidePoints(), flyover));
        return 1;
    }

    private static int startFirst(CommandSourceStack source, String courseName) throws CommandSyntaxException {
        GolfCourseSavedData data = GolfCourseSavedData.get(source.getServer());
        CourseDefinition course = data.course(courseName).orElse(null);
        if (course == null) {
            source.sendFailure(Component.literal("Unknown course: " + courseName));
            return 0;
        }
        HoleDefinition first = course.firstCompleteHole().orElse(null);
        if (first == null) {
            source.sendFailure(Component.literal("Course has no complete holes."));
            return 0;
        }
        return startAt(source, courseName, first.number());
    }

    private static int startAt(CommandSourceStack source, String courseName, int holeNumber)
            throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        GolfCourseSavedData data = GolfCourseSavedData.get(source.getServer());
        CourseDefinition course = data.course(courseName).orElse(null);
        if (course == null || course.hole(holeNumber) == null || !course.hole(holeNumber).complete()) {
            source.sendFailure(Component.literal("Course/hole is missing or incomplete."));
            return 0;
        }

        HoleDefinition hole = course.hole(holeNumber);
        String currentDimension = player.level().dimension().location().toString();
        if (!hole.dimension().equals(currentDimension)) {
            source.sendFailure(Component.literal(
                    "Hole is configured in " + hole.dimension() + ", but you are in " + currentDimension + "."));
            return 0;
        }

        GolfRoundManager.setRound(player, course.name(), holeNumber);
        source.sendSuccess(() -> Component.literal(
                "Started " + course.name() + " hole " + holeNumber + " (par " + hole.par() + ")"), false);
        return 1;
    }

    private static int resume(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var hole = GolfRoundManager.currentHoleDefinition(player);
        if (hole.isEmpty()) {
            source.sendFailure(Component.literal("No active Project Golf round to resume."));
            return 0;
        }
        HoleDefinition current = hole.get();
        source.sendSuccess(() -> Component.literal(
                "Resumed " + GolfRoundManager.currentCourseName(player) + " hole " + current.number()
                        + " (par " + current.par() + ") | current " + GolfRoundManager.strokes(player)
                        + " stroke(s)."), false);
        return 1;
    }

    private static int leave(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (!GolfRoundManager.hasActiveRound(player)) {
            source.sendFailure(Component.literal("No active Project Golf round."));
            return 0;
        }
        String course = GolfRoundManager.currentCourseName(player);
        GolfRoundManager.leaveRound(player);
        source.sendSuccess(() -> Component.literal("Left " + course + "."), false);
        return 1;
    }

    private static int status(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var hole = GolfRoundManager.currentHoleDefinition(player);
        String holeText = hole.map(h -> GolfRoundManager.currentCourseName(player) + " | hole " + h.number()
                + " par " + h.par()).orElse("practice");
        source.sendSuccess(() -> Component.literal(
                holeText + " | current " + GolfRoundManager.strokes(player)
                        + " | round " + GolfRoundManager.totalStrokes(player)
                        + " (" + formatRelative(GolfRoundManager.totalRelativeToPar(player)) + ")"), false);
        return 1;
    }

    private static String formatRelative(int value) {
        if (value == 0) return "E";
        return value > 0 ? "+" + value : Integer.toString(value);
    }
}
