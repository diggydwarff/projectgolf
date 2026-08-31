package dev.projectgolf.course;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;

/**
 * Tee, cup and par are the only required gameplay data. Name and guide points are presentation
 * metadata and never create bounds or restrict play.
 */
public record HoleDefinition(
        int number,
        int par,
        String dimension,
        BlockPos tee,
        BlockPos cup,
        List<BlockPos> guidePoints,
        String name
) {
    public HoleDefinition {
        guidePoints = guidePoints == null ? List.of() : List.copyOf(guidePoints);
        name = name == null ? "" : name.trim();
    }

    /** Backwards-compatible constructor for old code/tests and SavedData. */
    public HoleDefinition(int number, int par, String dimension, BlockPos tee, BlockPos cup) {
        this(number, par, dimension, tee, cup, List.of(), "");
    }

    /** Backwards-compatible constructor for alpha.11 guide-point data. */
    public HoleDefinition(int number, int par, String dimension, BlockPos tee, BlockPos cup, List<BlockPos> guidePoints) {
        this(number, par, dimension, tee, cup, guidePoints, "");
    }

    public HoleDefinition withTee(String dimension, BlockPos tee, int par) {
        return new HoleDefinition(number, par, dimension, tee, cup, guidePoints, name);
    }

    public HoleDefinition withCup(String dimension, BlockPos cup) {
        return new HoleDefinition(number, par, dimension, tee, cup, guidePoints, name);
    }

    public HoleDefinition withName(String name) {
        return new HoleDefinition(number, par, dimension, tee, cup, guidePoints, name);
    }

    public HoleDefinition withGuidePoint(BlockPos point) {
        ArrayList<BlockPos> next = new ArrayList<>(guidePoints);
        if (!next.contains(point)) next.add(point.immutable());
        return new HoleDefinition(number, par, dimension, tee, cup, next, name);
    }

    public HoleDefinition clearGuidePoints() {
        return new HoleDefinition(number, par, dimension, tee, cup, List.of(), name);
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
