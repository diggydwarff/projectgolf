package dev.projectgolf.registry;

import dev.projectgolf.ProjectGolf;
import dev.projectgolf.block.GolfCupBlock;
import dev.projectgolf.block.GolfSlopeBlock;
import dev.projectgolf.block.GolfTurfBlock;
import dev.projectgolf.block.GolfSurfaceSlabBlock;
import dev.projectgolf.block.PuttingGreenLayerBlock;
import dev.projectgolf.block.PuttingGreenSlopeBlock;
import dev.projectgolf.golf.GolfSurface;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class GolfBlocks {
    private GolfBlocks() {}

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ProjectGolf.MOD_ID);

    private static BlockBehaviour.Properties turfProperties() {
        return BlockBehaviour.Properties.of()
                .strength(0.6f)
                .sound(net.minecraft.world.level.block.SoundType.GRASS);
    }

    public static final DeferredBlock<GolfTurfBlock> TEE_GRASS =
            BLOCKS.register("tee_grass", () -> new GolfTurfBlock(GolfSurface.TEE, turfProperties()));
    public static final DeferredBlock<GolfTurfBlock> FAIRWAY =
            BLOCKS.register("fairway", () -> new GolfTurfBlock(GolfSurface.FAIRWAY, turfProperties()));
    public static final DeferredBlock<GolfTurfBlock> FRINGE =
            BLOCKS.register("fringe", () -> new GolfTurfBlock(GolfSurface.FRINGE, turfProperties()));
    public static final DeferredBlock<GolfTurfBlock> PUTTING_GREEN =
            BLOCKS.register("putting_green", () -> new GolfTurfBlock(GolfSurface.GREEN, turfProperties()));
    public static final DeferredBlock<PuttingGreenLayerBlock> PUTTING_GREEN_LAYER =
            BLOCKS.register("putting_green_layer", () -> new PuttingGreenLayerBlock(turfProperties().noOcclusion()));
    public static final DeferredBlock<GolfTurfBlock> ROUGH =
            BLOCKS.register("rough", () -> new GolfTurfBlock(GolfSurface.ROUGH, turfProperties()));
    public static final DeferredBlock<GolfTurfBlock> DEEP_ROUGH =
            BLOCKS.register("deep_rough", () -> new GolfTurfBlock(GolfSurface.DEEP_ROUGH, turfProperties()));
    public static final DeferredBlock<GolfTurfBlock> BUNKER_SAND =
            BLOCKS.register("bunker_sand", () -> new GolfTurfBlock(
                    GolfSurface.BUNKER,
                    BlockBehaviour.Properties.of().strength(0.5f).sound(net.minecraft.world.level.block.SoundType.SAND)));

    public static final DeferredBlock<PuttingGreenSlopeBlock> GREEN_SLOPE =
            BLOCKS.register("green_slope", () -> new PuttingGreenSlopeBlock(GolfSurface.GREEN, turfProperties().noOcclusion()));
    public static final DeferredBlock<PuttingGreenSlopeBlock> BUNKER_SLOPE =
            BLOCKS.register("bunker_slope", () -> new PuttingGreenSlopeBlock(
                    GolfSurface.BUNKER,
                    BlockBehaviour.Properties.of().strength(0.5f).noOcclusion()
                            .sound(net.minecraft.world.level.block.SoundType.SAND)));
    public static final DeferredBlock<GolfSlopeBlock> FAIRWAY_SLOPE =
            BLOCKS.register("fairway_slope", () -> new GolfSlopeBlock(GolfSurface.FAIRWAY, turfProperties().noOcclusion()));
    public static final DeferredBlock<GolfSlopeBlock> ROUGH_SLOPE =
            BLOCKS.register("rough_slope", () -> new GolfSlopeBlock(GolfSurface.ROUGH, turfProperties().noOcclusion()));

    // Coarse half-height landscaping for the wider course. Putting greens additionally keep their
    // precision 1/4 layers and variable slopes for fine shaping.
    public static final DeferredBlock<GolfSurfaceSlabBlock> GRASS_SLAB =
            BLOCKS.register("grass_slab", () -> new GolfSurfaceSlabBlock(GolfSurface.NATURAL_ROUGH, turfProperties().noOcclusion()));
    public static final DeferredBlock<GolfSurfaceSlabBlock> TEE_GRASS_SLAB =
            BLOCKS.register("tee_grass_slab", () -> new GolfSurfaceSlabBlock(GolfSurface.TEE, turfProperties().noOcclusion()));
    public static final DeferredBlock<GolfSurfaceSlabBlock> FAIRWAY_SLAB =
            BLOCKS.register("fairway_slab", () -> new GolfSurfaceSlabBlock(GolfSurface.FAIRWAY, turfProperties().noOcclusion()));
    public static final DeferredBlock<GolfSurfaceSlabBlock> FRINGE_SLAB =
            BLOCKS.register("fringe_slab", () -> new GolfSurfaceSlabBlock(GolfSurface.FRINGE, turfProperties().noOcclusion()));
    public static final DeferredBlock<GolfSurfaceSlabBlock> PUTTING_GREEN_SLAB =
            BLOCKS.register("putting_green_slab", () -> new GolfSurfaceSlabBlock(GolfSurface.GREEN, turfProperties().noOcclusion()));
    public static final DeferredBlock<GolfSurfaceSlabBlock> ROUGH_SLAB =
            BLOCKS.register("rough_slab", () -> new GolfSurfaceSlabBlock(GolfSurface.ROUGH, turfProperties().noOcclusion()));
    public static final DeferredBlock<GolfSurfaceSlabBlock> DEEP_ROUGH_SLAB =
            BLOCKS.register("deep_rough_slab", () -> new GolfSurfaceSlabBlock(GolfSurface.DEEP_ROUGH, turfProperties().noOcclusion()));
    public static final DeferredBlock<GolfSurfaceSlabBlock> BUNKER_SAND_SLAB =
            BLOCKS.register("bunker_sand_slab", () -> new GolfSurfaceSlabBlock(
                    GolfSurface.BUNKER,
                    BlockBehaviour.Properties.of().strength(0.5f).sound(net.minecraft.world.level.block.SoundType.SAND).noOcclusion()));

    public static final DeferredBlock<GolfCupBlock> GOLF_CUP =
            BLOCKS.register("golf_cup", () -> new GolfCupBlock(
                    BlockBehaviour.Properties.of().strength(0.5f).noOcclusion().sound(net.minecraft.world.level.block.SoundType.METAL)));

    public static final DeferredBlock<Block> TEE_MARKER =
            BLOCKS.registerSimpleBlock("tee_marker",
                    BlockBehaviour.Properties.of().strength(0.8f).sound(net.minecraft.world.level.block.SoundType.GRASS));

    /** Development-only visible stand-in for an out-of-bounds course region. */
    public static final DeferredBlock<Block> DEBUG_OOB =
            BLOCKS.registerSimpleBlock("debug_oob",
                    BlockBehaviour.Properties.of().strength(0.8f).sound(net.minecraft.world.level.block.SoundType.GRASS));
}
