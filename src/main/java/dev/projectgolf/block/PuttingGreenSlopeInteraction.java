package dev.projectgolf.block;

import dev.projectgolf.registry.GolfBlocks;
import dev.projectgolf.registry.GolfItems;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Builder interaction for the quarter-profile golf slopes.
 *
 * Green slopes consume Putting Green Layers; bunker slopes consume Bunker Sand. Normal use raises
 * the uphill edge by 1/4, sneak-use raises the downhill edge. The physical block remains one
 * registered slope with ten useful profiles instead of ten creative-tab variants.
 */
public final class PuttingGreenSlopeInteraction {
    private PuttingGreenSlopeInteraction() {}

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        BlockState state = event.getLevel().getBlockState(event.getPos());
        boolean green = state.is(GolfBlocks.GREEN_SLOPE.get());
        boolean bunker = state.is(GolfBlocks.BUNKER_SLOPE.get());
        if (!green && !bunker) return;
        if (green && !event.getItemStack().is(GolfItems.PUTTING_GREEN_LAYER.get())) return;
        if (bunker && !event.getItemStack().is(GolfItems.BUNKER_SAND.get())) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getLevel().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(state.getBlock() instanceof PuttingGreenSlopeBlock)) return;

        PuttingGreenSlopeBlock.Profile current = state.getValue(PuttingGreenSlopeBlock.PROFILE);
        boolean raiseLow = player.isShiftKeyDown();
        PuttingGreenSlopeBlock.Profile next = raiseLow ? current.raiseLow() : current.raiseHigh();

        if (next == null) {
            player.sendSystemMessage(Component.literal(raiseLow
                    ? "The lower edge cannot be raised further without flattening the slope. Raise the upper edge first."
                    : "The upper edge is already at full block height."));
            return;
        }

        event.getLevel().setBlock(event.getPos(), state.setValue(PuttingGreenSlopeBlock.PROFILE, next), 3);
        if (!player.getAbilities().instabuild) event.getItemStack().shrink(1);

        player.displayClientMessage(Component.literal(
                (green ? "Putting Green Slope: " : "Bunker Slope: ")
                        + heightLabel(next.lowQuarter()) + " -> " + heightLabel(next.highQuarter())), true);
    }

    private static String heightLabel(int quarters) {
        return switch (quarters) {
            case 0 -> "0";
            case 1 -> "1/4";
            case 2 -> "1/2";
            case 3 -> "3/4";
            default -> "1";
        };
    }
}
