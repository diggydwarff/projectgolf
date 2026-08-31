package dev.projectgolf.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import dev.projectgolf.debug.GolfSelfTest;
import dev.projectgolf.entity.GolfBallEntity;
import dev.projectgolf.golf.GolfSurface;
import dev.projectgolf.golf.GolfTuning;
import dev.projectgolf.registry.GolfEntities;
import dev.projectgolf.registry.GolfItems;
import dev.projectgolf.round.GolfRoundManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public final class GolfDebugCommand {
    private GolfDebugCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("golfdebug")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("give").executes(ctx -> give(ctx.getSource())))
                .then(Commands.literal("spawnball").executes(ctx -> spawnBall(ctx.getSource())))
                .then(Commands.literal("ball").executes(ctx -> ballInfo(ctx.getSource())))
                .then(Commands.literal("surface").executes(ctx -> surface(ctx.getSource())))
                .then(Commands.literal("cleanup").executes(ctx -> cleanup(ctx.getSource())))
                .then(Commands.literal("selftest").executes(ctx -> selfTest(ctx.getSource())))
                .then(Commands.literal("tuning").executes(ctx -> tuning(ctx.getSource())))
                .then(Commands.literal("launch")
                        .then(Commands.argument("speed", DoubleArgumentType.doubleArg(0.0, GolfTuning.MAX_BALL_SPEED))
                                .then(Commands.argument("loft", DoubleArgumentType.doubleArg(-5.0, 89.0))
                                        .executes(ctx -> launch(
                                                ctx.getSource(),
                                                DoubleArgumentType.getDouble(ctx, "speed"),
                                                DoubleArgumentType.getDouble(ctx, "loft")))))));
    }

    private static int give(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        List<ItemStack> stacks = List.of(
                new ItemStack(GolfItems.GOLF_BALL.get(), 16),
                new ItemStack(GolfItems.DRIVER.get()),
                new ItemStack(GolfItems.WOOD.get()),
                new ItemStack(GolfItems.IRON.get()),
                new ItemStack(GolfItems.WEDGE.get()),
                new ItemStack(GolfItems.PUTTER.get()),
                new ItemStack(GolfItems.COURSE_DESIGNER.get()),
                new ItemStack(GolfItems.DEBUG_WAND.get()),
                new ItemStack(GolfItems.GRASS_SLAB.get(), 64),
                new ItemStack(GolfItems.TEE_GRASS.get(), 64),
                new ItemStack(GolfItems.TEE_GRASS_SLAB.get(), 64),
                new ItemStack(GolfItems.FAIRWAY.get(), 64),
                new ItemStack(GolfItems.FAIRWAY_SLAB.get(), 64),
                new ItemStack(GolfItems.FRINGE.get(), 32),
                new ItemStack(GolfItems.FRINGE_SLAB.get(), 32),
                new ItemStack(GolfItems.PUTTING_GREEN.get(), 64),
                new ItemStack(GolfItems.PUTTING_GREEN_SLAB.get(), 64),
                new ItemStack(GolfItems.PUTTING_GREEN_LAYER.get(), 64),
                new ItemStack(GolfItems.GREEN_SLOPE.get(), 64),
                new ItemStack(GolfItems.ROUGH.get(), 64),
                new ItemStack(GolfItems.ROUGH_SLAB.get(), 64),
                new ItemStack(GolfItems.DEEP_ROUGH.get(), 32),
                new ItemStack(GolfItems.DEEP_ROUGH_SLAB.get(), 32),
                new ItemStack(GolfItems.BUNKER_SAND.get(), 32),
                new ItemStack(GolfItems.BUNKER_SAND_SLAB.get(), 32),
                new ItemStack(GolfItems.BUNKER_SLOPE.get(), 32),
                new ItemStack(GolfItems.FAIRWAY_SLOPE.get(), 32),
                new ItemStack(GolfItems.ROUGH_SLOPE.get(), 32),
                new ItemStack(GolfItems.GOLF_CUP.get(), 8),
                new ItemStack(GolfItems.TEE_MARKER.get(), 8),
                new ItemStack(GolfItems.DEBUG_OOB.get(), 32)
        );
        stacks.forEach(stack -> player.getInventory().add(stack));
        source.sendSuccess(() -> Component.literal("Gave Project Golf development kit."), false);
        return 1;
    }

    private static int spawnBall(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        GolfBallEntity ball = GolfEntities.GOLF_BALL.get().create(player.serverLevel());
        if (ball == null) return 0;

        GolfRoundManager.removeActiveBall(player);
        ball.setGolfOwner(player.getUUID());
        ball.setPos(player.getX(), player.getY() + 0.2, player.getZ());
        ball.setLastSafePosition(ball.position());
        if (!player.serverLevel().addFreshEntity(ball)) {
            source.sendFailure(Component.literal("Failed to spawn debug golf ball."));
            return 0;
        }
        GolfRoundManager.setActiveBall(player, ball);
        source.sendSuccess(() -> Component.literal("Spawned owned debug golf ball."), false);
        return 1;
    }

    private static int launch(CommandSourceStack source, double speed, double loft) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        GolfBallEntity ball = GolfRoundManager.findOwnedBall(player).orElse(null);
        if (ball == null) {
            source.sendFailure(Component.literal("No owned ball within range. Use /golfdebug spawnball."));
            return 0;
        }

        double yaw = Math.toRadians(player.getYRot());
        double loftRad = Math.toRadians(loft);
        double horizontal = speed * Math.cos(loftRad);
        Vec3 velocity = new Vec3(
                -Math.sin(yaw) * horizontal,
                speed * Math.sin(loftRad),
                Math.cos(yaw) * horizontal
        );
        ball.debugLaunch(velocity);
        source.sendSuccess(() -> Component.literal(
                String.format("Debug launch speed=%.3f loft=%.2f", speed, loft)), false);
        return 1;
    }

    private static int ballInfo(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        GolfBallEntity ball = GolfRoundManager.findOwnedBall(player).orElse(null);
        if (ball == null) {
            source.sendFailure(Component.literal("No owned ball within range."));
            return 0;
        }

        Vec3 v = ball.getDeltaMovement();
        source.sendSuccess(() -> Component.literal(String.format(
                "Ball %s pos=(%.3f %.3f %.3f) vel=(%.4f %.4f %.4f) speed=%.4f lie=%s safe=%s",
                ball.getUUID(), ball.getX(), ball.getY(), ball.getZ(),
                v.x, v.y, v.z, v.length(), ball.currentLie().displayName(), ball.getLastSafePosition())), false);
        return 1;
    }

    private static int surface(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        var state = player.level().getBlockState(player.blockPosition().below());
        GolfSurface surface = GolfSurface.from(state);
        source.sendSuccess(() -> Component.literal(
                "Surface=" + surface.displayName()
                        + " roll=" + surface.rollingRetention()
                        + " bounce=" + surface.restitution()
                        + " power=" + surface.shotPowerMultiplier()
                        + " accuracyPenalty=" + surface.accuracyPenalty()), false);
        return 1;
    }

    private static int cleanup(CommandSourceStack source) {
        int removed = 0;
        for (ServerLevel level : source.getServer().getAllLevels()) {
            List<? extends GolfBallEntity> balls = level.getEntities(GolfEntities.GOLF_BALL.get(), ball -> true);
            removed += balls.size();
            balls.forEach(GolfBallEntity::discard);
        }
        int finalRemoved = removed;
        source.sendSuccess(() -> Component.literal("Removed " + finalRemoved + " golf ball(s)."), true);
        return removed;
    }

    private static int selfTest(CommandSourceStack source) {
        List<String> failures = GolfSelfTest.run();
        if (failures.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Project Golf self-test: PASS"), true);
            return 1;
        }
        source.sendFailure(Component.literal("Project Golf self-test: FAIL (" + failures.size() + ")"));
        failures.forEach(f -> source.sendFailure(Component.literal(" - " + f)));
        return 0;
    }

    private static int tuning(CommandSourceStack source) {
        source.sendSuccess(() -> Component.literal(
                "gravity=" + GolfTuning.BALL_GRAVITY
                        + " drag=" + GolfTuning.AIR_HORIZONTAL_DRAG
                        + " stop=" + GolfTuning.STOP_HORIZONTAL_SPEED
                        + " cupRadius=" + GolfTuning.CUP_CAPTURE_RADIUS
                        + " cupMaxSpeed=" + GolfTuning.CUP_CAPTURE_MAX_HORIZONTAL_SPEED
                        + " slopeAccel=" + GolfTuning.SLOPE_ACCELERATION), false);
        return 1;
    }
}
