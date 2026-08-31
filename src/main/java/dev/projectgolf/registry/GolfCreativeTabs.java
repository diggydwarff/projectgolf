package dev.projectgolf.registry;

import dev.projectgolf.ProjectGolf;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.DyeColor;
import dev.projectgolf.item.GolfBallItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class GolfCreativeTabs {
    private GolfCreativeTabs() {}

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ProjectGolf.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> PROJECT_GOLF =
            TABS.register("project_golf", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.projectgolf.main"))
                    .withTabsBefore(CreativeModeTabs.SPAWN_EGGS)
                    .icon(() -> GolfItems.GOLF_BALL.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        // Core play kit first.
                        // One item, all 16 vanilla dye variants. White remains first/default.
                        for (DyeColor color : DyeColor.values()) {
                            output.accept(GolfBallItem.coloredStack(color));
                        }
                        output.accept(GolfItems.GOLF_BAG.get());
                        output.accept(GolfItems.DRIVER.get());
                        output.accept(GolfItems.WOOD.get());
                        output.accept(GolfItems.IRON.get());
                        output.accept(GolfItems.WEDGE.get());
                        output.accept(GolfItems.PUTTER.get());
                        output.accept(GolfItems.GOLDEN_SPOTTER.get());

                        // Course construction surfaces.
                        output.accept(GolfItems.GRASS_SLAB.get());
                        output.accept(GolfItems.TEE_GRASS.get());
                        output.accept(GolfItems.TEE_GRASS_SLAB.get());
                        output.accept(GolfItems.FAIRWAY.get());
                        output.accept(GolfItems.FAIRWAY_SLAB.get());
                        output.accept(GolfItems.FRINGE.get());
                        output.accept(GolfItems.FRINGE_SLAB.get());
                        output.accept(GolfItems.PUTTING_GREEN.get());
                        output.accept(GolfItems.PUTTING_GREEN_SLAB.get());
                        output.accept(GolfItems.PUTTING_GREEN_LAYER.get());
                        output.accept(GolfItems.GREEN_SLOPE.get());
                        output.accept(GolfItems.ROUGH.get());
                        output.accept(GolfItems.ROUGH_SLAB.get());
                        output.accept(GolfItems.DEEP_ROUGH.get());
                        output.accept(GolfItems.DEEP_ROUGH_SLAB.get());
                        output.accept(GolfItems.BUNKER_SAND.get());
                        output.accept(GolfItems.BUNKER_SAND_SLAB.get());
                        output.accept(GolfItems.BUNKER_SLOPE.get());
                        output.accept(GolfItems.FAIRWAY_SLOPE.get());
                        output.accept(GolfItems.ROUGH_SLOPE.get());
                        output.accept(GolfItems.GOLF_CUP.get());
                        output.accept(GolfItems.TEE_MARKER.get());
                        output.accept(GolfItems.COURSE_DESIGNER.get());

                        // Development tools intentionally remain at the end while mechanics are alpha.
                        output.accept(GolfItems.DEBUG_WAND.get());
                        output.accept(GolfItems.DEBUG_OOB.get());
                    })
                    .build());
}
