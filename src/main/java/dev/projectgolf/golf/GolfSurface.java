package dev.projectgolf.golf;

import dev.projectgolf.block.GolfSlopeBlock;
import dev.projectgolf.block.GolfTurfBlock;
import net.minecraft.world.level.block.state.BlockState;

public enum GolfSurface {
    TEE("Tee", 0.962, 0.32, 1.00, 1.00),
    FAIRWAY("Fairway", 0.957, 0.30, 1.00, 1.00),
    FRINGE("Fringe", 0.935, 0.26, 0.97, 1.04),
    GREEN("Green", 0.984, 0.20, 1.00, 0.96),
    ROUGH("Rough", 0.855, 0.22, 0.84, 1.25),
    DEEP_ROUGH("Deep Rough", 0.745, 0.18, 0.66, 1.55),
    BUNKER("Bunker", 0.58, 0.08, 0.58, 1.65),
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

        if (state.is(GolfTags.GREEN)) return GREEN;
        if (state.is(GolfTags.TEE)) return TEE;
        if (state.is(GolfTags.FAIRWAY)) return FAIRWAY;
        if (state.is(GolfTags.FRINGE)) return FRINGE;
        if (state.is(GolfTags.DEEP_ROUGH)) return DEEP_ROUGH;
        if (state.is(GolfTags.ROUGH)) return ROUGH;
        if (state.is(GolfTags.BUNKER)) return BUNKER;
        return DEFAULT;
    }
}
