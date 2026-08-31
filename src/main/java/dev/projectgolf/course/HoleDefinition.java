package dev.projectgolf.course;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal hole definition. Tee, cup and par are the only required gameplay data. Guide points are
 * optional presentation hints for doglegs/flyovers; they never create bounds or restrict play.
 */
public record HoleDefinition(
        int number,
        int par,
        String dimension,
        BlockPos tee,
        BlockPos cup,
        List<BlockPos> guidePoints
) {
    public HoleDefinition {
        guidePoints = guidePoints == null ? List.of() : List.copyOf(guidePoints);
    }

    /** Backwards-compatible constructor for alpha.10-era code/tests and old SavedData. */
    public HoleDefinition(int number, int par, String dimension, BlockPos tee, BlockPos cup) {
        this(number, par, dimension, tee, cup, List.of());
    }

    public HoleDefinition withTee(String dimension, BlockPos tee, int par) {
        return new HoleDefinition(number, par, dimension, tee, cup, guidePoints);
    }

    public HoleDefinition withCup(String dimension, BlockPos cup) {
        return new HoleDefinition(number, par, dimension, tee, cup, guidePoints);
    }

    public HoleDefinition withGuidePoint(BlockPos point) {
        ArrayList<BlockPos> next = new ArrayList<>(guidePoints);
        if (!next.contains(point)) next.add(point.immutable());
        return new HoleDefinition(number, par, dimension, tee, cup, next);
    }

    public HoleDefinition clearGuidePoints() {
        return new HoleDefinition(number, par, dimension, tee, cup, List.of());
    }

    public boolean complete() {
        return tee != null && cup != null && dimension != null && !dimension.isBlank();
    }

    /** Ordered presentation route: tee -> optional guide points -> cup. */
    public List<BlockPos> routePoints() {
        ArrayList<BlockPos> route = new ArrayList<>(guidePoints.size() + 2);
        if (tee != null) route.add(tee);
        route.addAll(guidePoints);
        if (cup != null) route.add(cup);
        return List.copyOf(route);
    }
}
