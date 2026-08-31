package dev.projectgolf.golf;

import dev.projectgolf.block.GolfSlopeBlock;
import dev.projectgolf.block.GolfSurfaceSlabBlock;
import dev.projectgolf.block.GolfTurfBlock;
import dev.projectgolf.block.PuttingGreenSlopeBlock;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Golf lie properties. Project Golf surfaces are purpose-built, but normal world terrain is always
 * playable too: missing course bounds never invalidate a shot.
 */
public enum GolfSurface {
    TEE("Tee", 0.962, 0.32, 1.00, 1.00),
    FAIRWAY("Fairway", 0.957, 0.30, 1.00, 1.00),
    FRINGE("Fringe", 0.935, 0.26, 0.97, 1.04),
    GREEN("Green", 0.984, 0.20, 1.00, 0.96),
    ROUGH("Rough", 0.855, 0.22, 0.84, 1.25),
    DEEP_ROUGH("Deep Rough", 0.745, 0.18, 0.66, 1.55),
    BUNKER("Bunker", 0.58, 0.08, 0.58, 1.65),

    // Natural-world lies keep improvised courses and bad shots playable without region setup.
    NATURAL_ROUGH("Natural Rough", 0.91, 0.21, 0.90, 1.20),
    WOODS("Woods", 0.72, 0.15, 0.62, 1.70),
    HARD_GROUND("Hard Ground", 0.965, 0.31, 0.94, 1.18),
    LOOSE_SAND("Loose Sand", 0.62, 0.09, 0.61, 1.55),
    SNOW("Snow", 0.70, 0.10, 0.70, 1.45),
    DEFAULT("Unrated Lie", 0.90, 0.24, 0.92, 1.15);

    private final String displayName;
    private final double rollingRetention;
    private final double restitution;
    private final double shotPowerMultiplier;
    private final double accuracyPenalty;

    GolfSurface(String displayName, double rollingRetention, double restitution,
                double shotPowerMultiplier, double accuracyPenalty) {
        this.displayName = displayName;
        this.rollingRetention = rollingRetention;
        this.restitution = restitution;
        this.shotPowerMultiplier = shotPowerMultiplier;
        this.accuracyPenalty = accuracyPenalty;
    }

    public String displayName() { return displayName; }
    public double rollingRetention() { return rollingRetention; }
    public double restitution() { return restitution; }
    public double shotPowerMultiplier() { return shotPowerMultiplier; }
    public double accuracyPenalty() { return accuracyPenalty; }

    public static GolfSurface from(BlockState state) {
        // Fast path for Project Golf blocks; tags remain the compatibility path for other mods.
        if (state.getBlock() instanceof GolfTurfBlock turf) return turf.golfSurface();
        if (state.getBlock() instanceof GolfSlopeBlock slope) return slope.golfSurface();
        if (state.getBlock() instanceof GolfSurfaceSlabBlock slab) return slab.golfSurface();
        if (state.getBlock() instanceof PuttingGreenSlopeBlock slope) return slope.golfSurface();

        if (state.is(GolfTags.GREEN)) return GREEN;
        if (state.is(GolfTags.TEE)) return TEE;
        if (state.is(GolfTags.FAIRWAY)) return FAIRWAY;
        if (state.is(GolfTags.FRINGE)) return FRINGE;
        if (state.is(GolfTags.DEEP_ROUGH)) return DEEP_ROUGH;
        if (state.is(GolfTags.ROUGH)) return ROUGH;
        if (state.is(GolfTags.BUNKER)) return BUNKER;

        // Explicit OOB is handled separately by the ball. Everything else is a playable lie.
        if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)
                || state.is(Blocks.PODZOL)) {
            return WOODS;
        }

        if (state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.ROOTED_DIRT)
                || state.is(Blocks.MYCELIUM)
                || state.is(Blocks.MOSS_BLOCK)
                || state.is(Blocks.MUD)
                || state.is(Blocks.FARMLAND)
                || state.is(Blocks.SHORT_GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN)) {
            return NATURAL_ROUGH;
        }

        if (state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)) {
            return LOOSE_SAND;
        }

        if (state.is(Blocks.SNOW)
                || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.POWDER_SNOW)) {
            return SNOW;
        }

        if (state.is(Blocks.DIRT_PATH)
                || state.is(Blocks.STONE)
                || state.is(Blocks.COBBLESTONE)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.DEEPSLATE)
                || state.is(Blocks.ANDESITE)
                || state.is(Blocks.DIORITE)
                || state.is(Blocks.GRANITE)
                || state.is(Blocks.TUFF)) {
            return HARD_GROUND;
        }

        return DEFAULT;
    }
}
