package dev.projectgolf.registry;

import dev.projectgolf.ProjectGolf;
import dev.projectgolf.block.GolfCupBlock;
import dev.projectgolf.block.GolfSlopeBlock;
import dev.projectgolf.block.GolfTurfBlock;
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
    public static final DeferredBlock<GolfTurfBlock> ROUGH =
            BLOCKS.register("rough", () -> new GolfTurfBlock(GolfSurface.ROUGH, turfProperties()));
    public static final DeferredBlock<GolfTurfBlock> DEEP_ROUGH =
            BLOCKS.register("deep_rough", () -> new GolfTurfBlock(GolfSurface.DEEP_ROUGH, turfProperties()));
    public static final DeferredBlock<GolfTurfBlock> BUNKER_SAND =
            BLOCKS.register("bunker_sand", () -> new GolfTurfBlock(
                    GolfSurface.BUNKER,
                    BlockBehaviour.Properties.of().strength(0.5f).sound(net.minecraft.world.level.block.SoundType.SAND)));

    public static final DeferredBlock<GolfSlopeBlock> GREEN_SLOPE =
            BLOCKS.register("green_slope", () -> new GolfSlopeBlock(GolfSurface.GREEN, turfProperties().noOcclusion()));
    public static final DeferredBlock<GolfSlopeBlock> FAIRWAY_SLOPE =
            BLOCKS.register("fairway_slope", () -> new GolfSlopeBlock(GolfSurface.FAIRWAY, turfProperties().noOcclusion()));
    public static final DeferredBlock<GolfSlopeBlock> ROUGH_SLOPE =
            BLOCKS.register("rough_slope", () -> new GolfSlopeBlock(GolfSurface.ROUGH, turfProperties().noOcclusion()));

    public static final DeferredBlock<GolfCupBlock> GOLF_CUP =
            BLOCKS.register("golf_cup", () -> new GolfCupBlock(
                    BlockBehaviour.Properties.of().strength(0.5f).noOcclusion().sound(net.minecraft.world.level.block.SoundType.METAL)));

    public static final DeferredBlock<Block> TEE_MARKER =
            BLOCKS.registerSimpleBlock("tee_marker",
                    BlockBehaviour.Properties.of().strength(0.8f).sound(net.minecraft.world.level.block.SoundType.STONE));

    /** Development-only visible stand-in for an out-of-bounds course region. */
    public static final DeferredBlock<Block> DEBUG_OOB =
            BLOCKS.registerSimpleBlock("debug_oob",
                    BlockBehaviour.Properties.of().strength(0.8f).sound(net.minecraft.world.level.block.SoundType.STONE));
}
