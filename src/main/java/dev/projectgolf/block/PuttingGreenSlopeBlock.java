package dev.projectgolf.block;

import dev.projectgolf.golf.GolfSurface;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * Configurable putting-green ramp that bridges any two quarter-block green elevations.
 *
 * FACING points uphill. PROFILE stores the lower and upper edge in quarter-block units, so one
 * registered block supports 0->1/4, 0->1/2, 0->3/4, 0->1, 1/4->1/2, ... 3/4->1.
 * All shapes are immutable and cached once. Selection keeps a fine 16-slice silhouette while
 * collision uses four traversable terraces; golf physics applies downhill acceleration from the
 * actual rise, independent of that collision approximation.
 */
public final class PuttingGreenSlopeBlock extends Block {
    private static final int COLLISION_SLICES = 4;

    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Profile> PROFILE = EnumProperty.create("profile", Profile.class);

    // Keep a fine 16-slice outline for selection/rendering, but use a coarser four-step collision
    // ramp. Sixteen one-pixel collision lips make vanilla players repeatedly enter step-up logic
    // and feel like they are crawling uphill. Four quarter-block-or-smaller steps preserve the
    // ramp profile while walking at normal Minecraft speed. Golf-ball slope acceleration remains
    // continuous and is not derived from the number of collision slices.
    private static final Map<Direction, Map<Profile, VoxelShape>> OUTLINE_SHAPES = buildShapes(16);
    private static final Map<Direction, Map<Profile, VoxelShape>> COLLISION_SHAPES = buildShapes(COLLISION_SLICES);

    private final GolfSurface surface;

    public PuttingGreenSlopeBlock(Properties properties) {
        this(GolfSurface.GREEN, properties);
    }

    public PuttingGreenSlopeBlock(GolfSurface surface, Properties properties) {
        super(properties);
        this.surface = surface;
        // Keep the registered default as the old full-height slope so pre-alpha.10 saved slopes
        // that have no profile property deserialize conservatively. Newly placed items start at 1/4.
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.SOUTH)
                .setValue(PROFILE, Profile.ZERO_TO_FULL));
    }

    public GolfSurface golfSurface() {
        return surface;
    }

    /** FACING points uphill; downhill is its opposite. */
    public Direction downhill(BlockState state) {
        return state.getValue(FACING).getOpposite();
    }

    public double lowHeight(BlockState state) {
        return state.getValue(PROFILE).lowQuarter() * 0.25;
    }

    public double highHeight(BlockState state) {
        return state.getValue(PROFILE).highQuarter() * 0.25;
    }

    public double rise(BlockState state) {
        return state.getValue(PROFILE).riseQuarters() * 0.25;
    }

    /** Height of the first collision terrace above the block base when entering from downhill. */
    public double firstCollisionHeight(BlockState state) {
        return lowHeight(state) + rise(state) / COLLISION_SLICES;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PROFILE);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Profile profile = Profile.ZERO_TO_QUARTER;

        // A green slope may replace a partial Putting Green Layer in-place. This turns an existing
        // 1/4, 1/2 or 3/4 terrace directly into a smooth transition to the next quarter height,
        // which is the key to building real rolling greens without stacking a ramp one block high.
        if (surface == GolfSurface.GREEN) {
            BlockState existing = context.getLevel().getBlockState(context.getClickedPos());
            if (existing.getBlock() instanceof PuttingGreenLayerBlock) {
                int low = existing.getValue(PuttingGreenLayerBlock.LAYERS);
                Profile elevated = Profile.find(low, Math.min(4, low + 1));
                if (elevated != null) profile = elevated;
            }
        }

        return defaultBlockState()
                .setValue(FACING, context.getHorizontalDirection())
                .setValue(PROFILE, profile);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return OUTLINE_SHAPES.get(state.getValue(FACING)).get(state.getValue(PROFILE));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return COLLISION_SHAPES.get(state.getValue(FACING)).get(state.getValue(PROFILE));
    }

    private static Map<Direction, Map<Profile, VoxelShape>> buildShapes(int slices) {
        Map<Direction, Map<Profile, VoxelShape>> byDirection = new EnumMap<>(Direction.class);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            Map<Profile, VoxelShape> byProfile = new EnumMap<>(Profile.class);
            for (Profile profile : Profile.values()) {
                byProfile.put(profile, makeShape(direction, profile, slices));
            }
            byDirection.put(direction, Map.copyOf(byProfile));
        }
        return Map.copyOf(byDirection);
    }

    private static VoxelShape makeShape(Direction facing, Profile profile, int slices) {
        VoxelShape result = Shapes.empty();
        double lowPixels = profile.lowQuarter() * 4.0;
        double risePixels = profile.riseQuarters() * 4.0;

        double sliceWidth = 16.0 / slices;
        for (int i = 0; i < slices; i++) {
            double a0 = i * sliceWidth;
            double a1 = a0 + sliceWidth;
            double height = lowPixels + risePixels * ((i + 1.0) / slices);
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

    public enum Profile implements StringRepresentable {
        ZERO_TO_QUARTER("0_1", 0, 1),
        ZERO_TO_HALF("0_2", 0, 2),
        ZERO_TO_THREE_QUARTER("0_3", 0, 3),
        ZERO_TO_FULL("0_4", 0, 4),
        QUARTER_TO_HALF("1_2", 1, 2),
        QUARTER_TO_THREE_QUARTER("1_3", 1, 3),
        QUARTER_TO_FULL("1_4", 1, 4),
        HALF_TO_THREE_QUARTER("2_3", 2, 3),
        HALF_TO_FULL("2_4", 2, 4),
        THREE_QUARTER_TO_FULL("3_4", 3, 4);

        private final String serialized;
        private final int lowQuarter;
        private final int highQuarter;

        Profile(String serialized, int lowQuarter, int highQuarter) {
            this.serialized = serialized;
            this.lowQuarter = lowQuarter;
            this.highQuarter = highQuarter;
        }

        public int lowQuarter() {
            return lowQuarter;
        }

        public int highQuarter() {
            return highQuarter;
        }

        public int riseQuarters() {
            return highQuarter - lowQuarter;
        }

        public @Nullable Profile raiseHigh() {
            return highQuarter >= 4 ? null : find(lowQuarter, highQuarter + 1);
        }

        public @Nullable Profile raiseLow() {
            return lowQuarter + 1 >= highQuarter ? null : find(lowQuarter + 1, highQuarter);
        }

        public static @Nullable Profile find(int lowQuarter, int highQuarter) {
            for (Profile profile : values()) {
                if (profile.lowQuarter == lowQuarter && profile.highQuarter == highQuarter) return profile;
            }
            return null;
        }

        @Override
        public String getSerializedName() {
            return serialized;
        }
    }
}
