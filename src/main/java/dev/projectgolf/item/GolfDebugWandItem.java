package dev.projectgolf.item;

import dev.projectgolf.block.GolfSlopeBlock;
import dev.projectgolf.golf.GolfSurface;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;

public class GolfDebugWandItem extends Item {
    public GolfDebugWandItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        BlockPos pos = context.getClickedPos();
        BlockState state = context.getLevel().getBlockState(pos);
        GolfSurface surface = GolfSurface.from(state);

        if (context.getPlayer() != null) {
            String slope = state.getBlock() instanceof GolfSlopeBlock golfSlope
                    ? " | downhill=" + golfSlope.downhill(state).getName()
                    : "";
            context.getPlayer().displayClientMessage(Component.literal(
                    "Golf debug: " + state.getBlock() + " | surface=" + surface.displayName()
                            + " | roll=" + surface.rollingRetention()
                            + " | bounce=" + surface.restitution()
                            + " | power=" + surface.shotPowerMultiplier()
                            + " | accuracyPenalty=" + surface.accuracyPenalty()
                            + slope), false);
        }
        return InteractionResult.SUCCESS;
    }
}
