package dev.projectgolf.golf;

import dev.projectgolf.ProjectGolf;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

public final class GolfTags {
    private GolfTags() {}

    public static final TagKey<Block> TEE = tag("tee");
    public static final TagKey<Block> FAIRWAY = tag("fairway");
    public static final TagKey<Block> FRINGE = tag("fringe");
    public static final TagKey<Block> GREEN = tag("green");
    public static final TagKey<Block> ROUGH = tag("rough");
    public static final TagKey<Block> DEEP_ROUGH = tag("deep_rough");
    public static final TagKey<Block> BUNKER = tag("bunker");
    public static final TagKey<Block> OUT_OF_BOUNDS = tag("out_of_bounds");

    private static TagKey<Block> tag(String path) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(ProjectGolf.MOD_ID, path));
    }
}
