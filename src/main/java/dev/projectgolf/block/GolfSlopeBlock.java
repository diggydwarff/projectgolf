package dev.projectgolf.block;

import dev.projectgolf.golf.GolfSurface;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class GolfSlopeBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    // VoxelShapes are immutable. Cache all orientations once instead of rebuilding eight boxes
    // during every collision/shape query on a course full of slopes.
    private static final VoxelShape SOUTH_SHAPE = makeShape(Direction.SOUTH, 8);
    private static final VoxelShape NORTH_SHAPE = makeShape(Direction.NORTH, 8);
    private static final VoxelShape EAST_SHAPE = makeShape(Direction.EAST, 8);
    private static final VoxelShape WEST_SHAPE = makeShape(Direction.WEST, 8);

    // Use only four collision terraces, matching the precision slope implementation. The visible
    // outline remains finer, but the ball/player no longer has to resolve eight tiny vertical lips
    // that can be mistaken for walls and produce an artificial ricochet.
    private static final VoxelShape SOUTH_COLLISION = makeShape(Direction.SOUTH, 4);
    private static final VoxelShape NORTH_COLLISION = makeShape(Direction.NORTH, 4);
    private static final VoxelShape EAST_COLLISION = makeShape(Direction.EAST, 4);
    private static final VoxelShape WEST_COLLISION = makeShape(Direction.WEST, 4);

    private final GolfSurface surface;

    public GolfSlopeBlock(GolfSurface surface, Properties properties) {
        super(properties);
        this.surface = surface;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.SOUTH));
    }

    public GolfSurface golfSurface() {
        return surface;
    }

    /** FACING always points uphill; downhill is FACING.getOpposite(). */
    public Direction downhill(BlockState state) {
        return state.getValue(FACING).getOpposite();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> NORTH_SHAPE;
            case EAST -> EAST_SHAPE;
            case WEST -> WEST_SHAPE;
            default -> SOUTH_SHAPE;
        };
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case NORTH -> NORTH_COLLISION;
            case EAST -> EAST_COLLISION;
            case WEST -> WEST_COLLISION;
            default -> SOUTH_COLLISION;
        };
    }

    private static VoxelShape makeShape(Direction facing, int slices) {
        VoxelShape result = Shapes.empty();
        double slice = 16.0 / slices;
        for (int i = 0; i < slices; i++) {
            double a0 = i * slice;
            double a1 = a0 + slice;
            double height = (i + 1) * slice;
            result = switch (facing) {
                case SOUTH -> Shapes.or(result, box(0, 0, a0, 16, height, a1));
                case NORTH -> Shapes.or(result, box(0, 0, 16 - a1, 16, height, 16 - a0));
                case EAST -> Shapes.or(result, box(a0, 0, 0, a1, height, 16));
                case WEST -> Shapes.or(result, box(16 - a1, 0, 0, 16 - a0, height, 16));
                default -> result;
            };
        }
        return result;
    }
}
