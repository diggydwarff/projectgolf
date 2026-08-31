package dev.projectgolf.course;

import net.minecraft.core.BlockPos;

public record HoleDefinition(int number, int par, String dimension, BlockPos tee, BlockPos cup) {
    public HoleDefinition withTee(String dimension, BlockPos tee, int par) {
        return new HoleDefinition(number, par, dimension, tee, cup);
    }

    public HoleDefinition withCup(String dimension, BlockPos cup) {
        return new HoleDefinition(number, par, dimension, tee, cup);
    }

    public boolean complete() {
        return tee != null && cup != null && dimension != null && !dimension.isBlank();
    }
}
