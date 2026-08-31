package dev.projectgolf.item;

import dev.projectgolf.course.CourseBuilderManager;
import dev.projectgolf.course.CourseUiService;
import dev.projectgolf.course.CourseDefinition;
import dev.projectgolf.course.GolfCourseSavedData;
import dev.projectgolf.course.HoleDefinition;
import dev.projectgolf.registry.GolfBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Builder-facing linker for course endpoints. Courses intentionally have no required bounds:
 * a hole is simply a numbered tee + cup + par in the world.
 */
public final class CourseDesignerItem extends Item {
    public CourseDesignerItem(Properties properties) {
        super(properties.stacksTo(1));
    }


    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            CourseUiService.openBuilder(serverPlayer);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }

        var selection = CourseBuilderManager.selection(player);
        if (selection.isEmpty()) {
            player.sendSystemMessage(Component.literal(
                    "No course edit selected. Right-click the Course Designer in the air to open Course Design Studio."));
            return InteractionResult.SUCCESS;
        }

        CourseBuilderManager.Selection edit = selection.get();
        GolfCourseSavedData data = GolfCourseSavedData.get(player.getServer());
        CourseDefinition course = data.course(edit.course()).orElse(null);
        if (course == null) {
            CourseBuilderManager.clear(player);
            player.sendSystemMessage(Component.literal(
                    "Selected course no longer exists: " + edit.course()));
            return InteractionResult.SUCCESS;
        }

        BlockPos pos = context.getClickedPos();
        BlockState state = context.getLevel().getBlockState(pos);
        HoleDefinition old = course.getOrCreateHole(edit.hole());
        String dimension = player.level().dimension().location().toString();

        if (state.is(GolfBlocks.TEE_MARKER.get())) {
            if (old.cup() != null && !old.dimension().isBlank() && !old.dimension().equals(dimension)) {
                player.sendSystemMessage(Component.literal(
                        "That hole's cup is in " + old.dimension() + "; tee and cup must share a dimension."));
                return InteractionResult.SUCCESS;
            }
            course.putHole(old.withTee(dimension, pos, edit.par()));
            data.changed();
            player.sendSystemMessage(Component.literal(
                    "Linked " + course.name() + " hole " + edit.hole() + " tee at " + pos
                            + " (par " + edit.par() + ")."));
            return InteractionResult.SUCCESS;
        }

        if (state.is(GolfBlocks.GOLF_CUP.get())) {
            if (old.tee() != null && !old.dimension().isBlank() && !old.dimension().equals(dimension)) {
                player.sendSystemMessage(Component.literal(
                        "That hole's tee is in " + old.dimension() + "; tee and cup must share a dimension."));
                return InteractionResult.SUCCESS;
            }
            course.putHole(old.withCup(dimension, pos));
            data.changed();
            player.sendSystemMessage(Component.literal(
                    "Linked " + course.name() + " hole " + edit.hole() + " cup at " + pos + "."));
            return InteractionResult.SUCCESS;
        }

        // Optional route hints are presentation only. Sneak-use any terrain block to append a
        // dogleg/flyover guide point without placing an artificial marker or defining a boundary.
        if (player.isShiftKeyDown()) {
            if (!old.dimension().isBlank() && !old.dimension().equals(dimension)) {
                player.sendSystemMessage(Component.literal(
                        "That hole is in " + old.dimension() + "; guide points must share its dimension."));
                return InteractionResult.SUCCESS;
            }
            if (old.guidePoints().size() >= 16) {
                player.sendSystemMessage(Component.literal(
                        "This hole already has 16 guide points. Clear or simplify the route first."));
                return InteractionResult.SUCCESS;
            }
            HoleDefinition updated = old.withGuidePoint(pos);
            course.putHole(updated);
            data.changed();
            player.sendSystemMessage(Component.literal(
                    "Added guide point " + updated.guidePoints().size() + " to " + course.name()
                            + " hole " + edit.hole() + " at " + pos + ". Guide points only affect presentation."));
            return InteractionResult.SUCCESS;
        }

        player.sendSystemMessage(Component.literal(
                "Use the Course Designer on a Tee Marker or Golf Cup. Sneak-use terrain to add an optional flyover guide point."
                        + " The world remains playable and unbounded."));
        return InteractionResult.SUCCESS;
    }
}
