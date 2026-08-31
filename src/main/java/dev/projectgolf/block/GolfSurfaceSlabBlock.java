package dev.projectgolf.block;

import dev.projectgolf.golf.GolfSurface;
import net.minecraft.world.level.block.SlabBlock;

/**
 * Half/double slab variant of a playable golf surface.
 *
 * The slab's material determines the lie exactly like the corresponding full block. This is kept
 * deliberately separate from the quarter-height putting-green layer: half slabs are a coarse
 * landscaping tool for the wider course, while green layers remain the precision green builder.
 */
public final class GolfSurfaceSlabBlock extends SlabBlock {
    private final GolfSurface surface;

    public GolfSurfaceSlabBlock(GolfSurface surface, Properties properties) {
        super(properties);
        this.surface = surface;
    }

    public GolfSurface golfSurface() {
        return surface;
    }
}
