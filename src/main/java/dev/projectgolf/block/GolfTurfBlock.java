package dev.projectgolf.block;

import dev.projectgolf.golf.GolfSurface;
import net.minecraft.world.level.block.Block;

public class GolfTurfBlock extends Block {
    private final GolfSurface surface;

    public GolfTurfBlock(GolfSurface surface, Properties properties) {
        super(properties);
        this.surface = surface;
    }

    public GolfSurface golfSurface() {
        return surface;
    }
}
