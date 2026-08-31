package dev.projectgolf.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.projectgolf.course.CourseDefinition;
import dev.projectgolf.course.GolfCourseSavedData;
import dev.projectgolf.course.HoleDefinition;
import dev.projectgolf.round.GolfRoundManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class GolfCourseCommand {
    private GolfCourseCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("golf")
                .then(Commands.literal("start")
                        .then(Commands.argument("course", StringArgumentType.word())
                                .then(Commands.argument("hole", IntegerArgumentType.integer(1, 99))
                                        .executes(ctx -> start(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "course"),
                                                IntegerArgumentType.getInteger(ctx, "hole"))))))
                .then(Commands.literal("status")
                        .executes(ctx -> status(ctx.getSource())))
                .then(Commands.literal("course")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.literal("create")
                                .then(Commands.argument("name", StringArgumentType.word())
                                        .executes(ctx -> create(
                                                ctx.getSource(),
                                                StringArgumentType.getString(ctx, "name")))))
                        .then(Commands.literal("settee")
                                .then(Commands.argument("course", StringArgumentType.word())
                                        .then(Commands.argument("hole", IntegerArgumentType.integer(1, 99))
                                                .then(Commands.argument("par", IntegerArgumentType.integer(1, 10))
                                                        .executes(ctx -> setTee(
                                                                ctx.getSource(),
                                                                StringArgumentType.getString(ctx, "course"),
                                                                IntegerArgumentType.getInteger(ctx, "hole"),
                                                                IntegerArgumentType.getInteger(ctx, "par")))))))
                        .then(Commands.literal("setcup")
                                .then(Commands.argument("course", StringArgumentType.word())
                                        .then(Commands.argument("hole", IntegerArgumentType.integer(1, 99))
                                                .executes(ctx -> setCup(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "course"),
                                                        IntegerArgumentType.getInteger(ctx, "hole"))))))
                        .then(Commands.literal("info")
                                .then(Commands.argument("course", StringArgumentType.word())
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
        source.sendSuccess(() -> Component.literal("Created golf course " + name), true);
        return 1;
    }

    private static int setTee(CommandSourceStack source, String courseName, int holeNumber, int par) throws CommandSyntaxException {
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
                    "Hole already has a cup in " + old.dimension() + "; tee and cup must be in the same dimension."));
            return 0;
        }
        course.putHole(old.withTee(currentDimension, player.blockPosition().below(), par));
        data.changed();

        source.sendSuccess(() -> Component.literal(
                "Set " + courseName + " hole " + holeNumber + " tee at " + player.blockPosition().below() + " par " + par), true);
        return 1;
    }

    private static int setCup(CommandSourceStack source, String courseName, int holeNumber) throws CommandSyntaxException {
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
                    "Hole already has a tee in " + old.dimension() + "; tee and cup must be in the same dimension."));
            return 0;
        }
        course.putHole(old.withCup(currentDimension, player.blockPosition().below()));
        data.changed();

        source.sendSuccess(() -> Component.literal(
                "Set " + courseName + " hole " + holeNumber + " cup at " + player.blockPosition().below()), true);
        return 1;
    }

    private static int info(CommandSourceStack source, String courseName) {
        GolfCourseSavedData data = GolfCourseSavedData.get(source.getServer());
        CourseDefinition course = data.course(courseName).orElse(null);
        if (course == null) {
            source.sendFailure(Component.literal("Unknown course: " + courseName));
            return 0;
        }

        source.sendSuccess(() -> Component.literal("Course " + course.name() + " — " + course.holes().size() + " configured holes"), false);
        for (HoleDefinition hole : course.holes().values()) {
            source.sendSuccess(() -> Component.literal(
                    "Hole " + hole.number() + " par " + hole.par()
                            + " dim=" + hole.dimension() + " tee=" + hole.tee() + " cup=" + hole.cup()
                            + (hole.complete() ? " [ready]" : " [incomplete]")), false);
        }
        return 1;
    }

    private static int start(CommandSourceStack source, String courseName, int holeNumber) throws CommandSyntaxException {
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

    private static int status(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var hole = GolfRoundManager.currentHoleDefinition(player);
        String holeText = hole.map(h -> "hole " + h.number() + " par " + h.par()).orElse("practice");
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
