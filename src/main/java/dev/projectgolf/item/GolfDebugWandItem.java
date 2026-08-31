package dev.projectgolf.item;

import dev.projectgolf.block.GolfSlopeBlock;
import dev.projectgolf.block.PuttingGreenSlopeBlock;
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
            String slope;
            if (state.getBlock() instanceof PuttingGreenSlopeBlock greenSlope) {
                PuttingGreenSlopeBlock.Profile profile = state.getValue(PuttingGreenSlopeBlock.PROFILE);
                slope = " | downhill=" + greenSlope.downhill(state).getName()
                        + " | profile=" + profile.getSerializedName()
                        + " | rise=" + greenSlope.rise(state);
            } else if (state.getBlock() instanceof GolfSlopeBlock golfSlope) {
                slope = " | downhill=" + golfSlope.downhill(state).getName() + " | rise=1.0";
            } else {
                slope = "";
            }
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
