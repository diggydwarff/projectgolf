package dev.projectgolf.block;

import dev.projectgolf.golf.GolfSurface;
import dev.projectgolf.registry.GolfItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Stackable putting-green quarter layers. One item adds 1/4 block of height; four layers make a
 * full block. The golf ball's 0.26-block step height is intentionally just enough to traverse a
 * single quarter terrace. Use Putting Green Slopes between layers when you want continuous
 * uphill/downhill gravity and rollback instead of a stepped terrace.
 */
public final class PuttingGreenLayerBlock extends GolfTurfBlock {
    public static final IntegerProperty LAYERS = IntegerProperty.create("layers", 1, 4);

    private static final VoxelShape[] SHAPES = new VoxelShape[] {
            box(0, 0, 0, 16, 4, 16),
            box(0, 0, 0, 16, 8, 16),
            box(0, 0, 0, 16, 12, 16),
            box(0, 0, 0, 16, 16, 16)
    };

    public PuttingGreenLayerBlock(Properties properties) {
        super(GolfSurface.GREEN, properties);
        registerDefaultState(stateDefinition.any().setValue(LAYERS, 1));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LAYERS);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState existing = context.getLevel().getBlockState(context.getClickedPos());
        if (existing.is(this)) {
            return existing.setValue(LAYERS, Math.min(4, existing.getValue(LAYERS) + 1));
        }
        return defaultBlockState();
    }

    @Override
    protected boolean canBeReplaced(BlockState state, BlockPlaceContext context) {
        if (context.getItemInHand().is(asItem())) {
            return state.getValue(LAYERS) < 4;
        }
        // Placing a green slope directly into a partial layer replaces that terrace while the
        // slope reads its existing height and starts at low -> low+1/4.
        return context.getItemInHand().is(GolfItems.GREEN_SLOPE.get())
                && state.getValue(LAYERS) < 4;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(LAYERS) - 1];
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[state.getValue(LAYERS) - 1];
    }

    public static double height(BlockState state) {
        return state.getValue(LAYERS) * 0.25;
    }
}
