package dev.projectgolf.registry;

import dev.projectgolf.ProjectGolf;
import dev.projectgolf.golf.ClubType;
import dev.projectgolf.item.GolfBallItem;
import dev.projectgolf.item.GolfClubItem;
import dev.projectgolf.item.GolfDebugWandItem;
import dev.projectgolf.item.GoldenSpotterItem;
import dev.projectgolf.item.CourseDesignerItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class GolfItems {
    private GolfItems() {}

    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ProjectGolf.MOD_ID);

    public static final DeferredItem<GolfBallItem> GOLF_BALL =
            ITEMS.registerItem("golf_ball", GolfBallItem::new, new Item.Properties());

    public static final DeferredItem<GolfClubItem> DRIVER =
            ITEMS.registerItem("driver", p -> new GolfClubItem(ClubType.DRIVER, p), new Item.Properties());
    public static final DeferredItem<GolfClubItem> WOOD =
            ITEMS.registerItem("wood", p -> new GolfClubItem(ClubType.WOOD, p), new Item.Properties());
    public static final DeferredItem<GolfClubItem> IRON =
            ITEMS.registerItem("iron", p -> new GolfClubItem(ClubType.IRON, p), new Item.Properties());
    public static final DeferredItem<GolfClubItem> WEDGE =
            ITEMS.registerItem("wedge", p -> new GolfClubItem(ClubType.WEDGE, p), new Item.Properties());
    public static final DeferredItem<GolfClubItem> PUTTER =
            ITEMS.registerItem("putter", p -> new GolfClubItem(ClubType.PUTTER, p), new Item.Properties());

    public static final DeferredItem<GoldenSpotterItem> GOLDEN_SPOTTER =
            ITEMS.registerItem("golden_spotter", GoldenSpotterItem::new, new Item.Properties());

    public static final DeferredItem<CourseDesignerItem> COURSE_DESIGNER =
            ITEMS.registerItem("course_designer", CourseDesignerItem::new, new Item.Properties());

    public static final DeferredItem<GolfDebugWandItem> DEBUG_WAND =
            ITEMS.registerItem("debug_wand", GolfDebugWandItem::new, new Item.Properties());

    public static final DeferredItem<BlockItem> TEE_GRASS = blockItem("tee_grass", GolfBlocks.TEE_GRASS);
    public static final DeferredItem<BlockItem> FAIRWAY = blockItem("fairway", GolfBlocks.FAIRWAY);
    public static final DeferredItem<BlockItem> FRINGE = blockItem("fringe", GolfBlocks.FRINGE);
    public static final DeferredItem<BlockItem> PUTTING_GREEN = blockItem("putting_green", GolfBlocks.PUTTING_GREEN);
    public static final DeferredItem<BlockItem> PUTTING_GREEN_LAYER = blockItem("putting_green_layer", GolfBlocks.PUTTING_GREEN_LAYER);
    public static final DeferredItem<BlockItem> ROUGH = blockItem("rough", GolfBlocks.ROUGH);
    public static final DeferredItem<BlockItem> DEEP_ROUGH = blockItem("deep_rough", GolfBlocks.DEEP_ROUGH);
    public static final DeferredItem<BlockItem> BUNKER_SAND = blockItem("bunker_sand", GolfBlocks.BUNKER_SAND);
    public static final DeferredItem<BlockItem> GREEN_SLOPE = blockItem("green_slope", GolfBlocks.GREEN_SLOPE);
    public static final DeferredItem<BlockItem> BUNKER_SLOPE = blockItem("bunker_slope", GolfBlocks.BUNKER_SLOPE);
    public static final DeferredItem<BlockItem> FAIRWAY_SLOPE = blockItem("fairway_slope", GolfBlocks.FAIRWAY_SLOPE);
    public static final DeferredItem<BlockItem> ROUGH_SLOPE = blockItem("rough_slope", GolfBlocks.ROUGH_SLOPE);
    public static final DeferredItem<BlockItem> GRASS_SLAB = blockItem("grass_slab", GolfBlocks.GRASS_SLAB);
    public static final DeferredItem<BlockItem> TEE_GRASS_SLAB = blockItem("tee_grass_slab", GolfBlocks.TEE_GRASS_SLAB);
    public static final DeferredItem<BlockItem> FAIRWAY_SLAB = blockItem("fairway_slab", GolfBlocks.FAIRWAY_SLAB);
    public static final DeferredItem<BlockItem> FRINGE_SLAB = blockItem("fringe_slab", GolfBlocks.FRINGE_SLAB);
    public static final DeferredItem<BlockItem> PUTTING_GREEN_SLAB = blockItem("putting_green_slab", GolfBlocks.PUTTING_GREEN_SLAB);
    public static final DeferredItem<BlockItem> ROUGH_SLAB = blockItem("rough_slab", GolfBlocks.ROUGH_SLAB);
    public static final DeferredItem<BlockItem> DEEP_ROUGH_SLAB = blockItem("deep_rough_slab", GolfBlocks.DEEP_ROUGH_SLAB);
    public static final DeferredItem<BlockItem> BUNKER_SAND_SLAB = blockItem("bunker_sand_slab", GolfBlocks.BUNKER_SAND_SLAB);
    public static final DeferredItem<BlockItem> GOLF_CUP = blockItem("golf_cup", GolfBlocks.GOLF_CUP);
    public static final DeferredItem<BlockItem> TEE_MARKER = blockItem("tee_marker", GolfBlocks.TEE_MARKER);
    public static final DeferredItem<BlockItem> DEBUG_OOB = blockItem("debug_oob", GolfBlocks.DEBUG_OOB);

    private static <T extends net.minecraft.world.level.block.Block> DeferredItem<BlockItem> blockItem(
            String name, net.neoforged.neoforge.registries.DeferredBlock<T> block) {
        return ITEMS.registerSimpleBlockItem(name, block, new Item.Properties());
    }
}
