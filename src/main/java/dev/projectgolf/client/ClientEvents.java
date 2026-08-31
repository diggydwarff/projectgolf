package dev.projectgolf.client;

import dev.projectgolf.ProjectGolf;
import dev.projectgolf.client.render.GolfBallRenderer;
import dev.projectgolf.item.GolfClubItem;
import dev.projectgolf.item.GolfBallItem;
import dev.projectgolf.registry.GolfBlocks;
import dev.projectgolf.registry.GolfEntities;
import dev.projectgolf.registry.GolfItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockAndTintGetter;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

public final class ClientEvents {
    private ClientEvents() {}

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(GolfEntities.GOLF_BALL.get(), GolfBallRenderer::new);
    }

    public static void registerKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(GolfKeyMappings.HOLE_VIEW);
        event.register(GolfKeyMappings.FLYOVER);
    }

    public static void registerHud(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(ProjectGolf.MOD_ID, "golf_hud"),
                (graphics, deltaTracker) -> GolfHud.render(graphics));
    }

    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, world, pos, tintIndex) -> tintFor(state.getBlock(), world, pos),
                GolfBlocks.GRASS_SLAB.get(), GolfBlocks.TEE_MARKER.get(),
                GolfBlocks.TEE_GRASS.get(), GolfBlocks.TEE_GRASS_SLAB.get(),
                GolfBlocks.FAIRWAY.get(), GolfBlocks.FAIRWAY_SLAB.get(),
                GolfBlocks.FRINGE.get(), GolfBlocks.FRINGE_SLAB.get(),
                GolfBlocks.PUTTING_GREEN.get(), GolfBlocks.PUTTING_GREEN_SLAB.get(), GolfBlocks.PUTTING_GREEN_LAYER.get(),
                GolfBlocks.ROUGH.get(), GolfBlocks.ROUGH_SLAB.get(),
                GolfBlocks.DEEP_ROUGH.get(), GolfBlocks.DEEP_ROUGH_SLAB.get(),
                GolfBlocks.GREEN_SLOPE.get(), GolfBlocks.FAIRWAY_SLOPE.get(), GolfBlocks.ROUGH_SLOPE.get());
    }

    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        // Golf-ball variants are one item with a vanilla BASE_COLOR component. Tinting the same
        // shaded white sprite keeps all 16 colors visually consistent in inventory and in-world.
        // 1.21.x color handlers use ARGB, not the old RGB-only convention. Returning
        // 0xRRGGBB leaves alpha at 0 and makes the generated item layer effectively invisible.
        // ThrownItemRenderer renders GolfBallEntity through this same item model/color handler,
        // so keeping the tint fully opaque fixes both the inventory sprite and the world ball.
        event.register((stack, tintIndex) -> tintIndex == 0
                        ? (0xFF000000 | GolfBallItem.renderColor(stack))
                        : 0xFFFFFFFF,
                GolfItems.GOLF_BALL.get());

        event.register((stack, tintIndex) -> tintFor(itemTintBlock(stack.getItem()), null, null),
                GolfItems.GRASS_SLAB.get(), GolfItems.TEE_MARKER.get(),
                GolfItems.TEE_GRASS.get(), GolfItems.TEE_GRASS_SLAB.get(),
                GolfItems.FAIRWAY.get(), GolfItems.FAIRWAY_SLAB.get(),
                GolfItems.FRINGE.get(), GolfItems.FRINGE_SLAB.get(),
                GolfItems.PUTTING_GREEN.get(), GolfItems.PUTTING_GREEN_SLAB.get(), GolfItems.PUTTING_GREEN_LAYER.get(),
                GolfItems.ROUGH.get(), GolfItems.ROUGH_SLAB.get(),
                GolfItems.DEEP_ROUGH.get(), GolfItems.DEEP_ROUGH_SLAB.get(),
                GolfItems.GREEN_SLOPE.get(), GolfItems.FAIRWAY_SLOPE.get(), GolfItems.ROUGH_SLOPE.get());
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        ClientSwingController.tick();
        ClientHoleView.tick();
        ClientSpotter.tick();
        ClientWindEffects.tick();

        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) ClientRoundState.clear();
        if (GolfKeyMappings.HOLE_VIEW.consumeClick()) {
            if (ClientHoleView.active()) {
                ClientHoleView.stop();
            } else if (mc.player != null && mc.player.connection != null) {
                mc.player.connection.sendCommand("golf view");
            }
        }
        if (GolfKeyMappings.FLYOVER.consumeClick()) {
            if (ClientHoleView.active()) {
                ClientHoleView.startFlyover();
            } else if (mc.player != null && mc.player.connection != null) {
                mc.player.connection.sendCommand("golf flyover");
            }
        }
    }

    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (ClientHoleView.active()) event.setFOV(ClientHoleView.preferredFov(event.getFOV()));
    }

    public static void onUseInput(InputEvent.InteractionKeyMappingTriggered event) {
        if (event.isUseItem()
                && net.minecraft.client.Minecraft.getInstance().player != null
                && net.minecraft.client.Minecraft.getInstance().player.getMainHandItem().getItem() instanceof GolfClubItem) {
            event.setSwingHand(false);
            event.setCanceled(true);
        }
    }

    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        if (ClientSwingController.adjustPlannedPower(event.getScrollDeltaY())) {
            // Shift+wheel belongs to Project Golf while planning a shot; do not also change hotbar slots.
            event.setCanceled(true);
        }
    }

    private static net.minecraft.world.level.block.Block itemTintBlock(net.minecraft.world.item.Item item) {
        if (item == GolfItems.GRASS_SLAB.get()) return GolfBlocks.GRASS_SLAB.get();
        if (item == GolfItems.TEE_MARKER.get()) return GolfBlocks.TEE_MARKER.get();
        if (item == GolfItems.TEE_GRASS.get()) return GolfBlocks.TEE_GRASS.get();
        if (item == GolfItems.TEE_GRASS_SLAB.get()) return GolfBlocks.TEE_GRASS_SLAB.get();
        if (item == GolfItems.FAIRWAY.get()) return GolfBlocks.FAIRWAY.get();
        if (item == GolfItems.FAIRWAY_SLAB.get()) return GolfBlocks.FAIRWAY_SLAB.get();
        if (item == GolfItems.FRINGE.get()) return GolfBlocks.FRINGE.get();
        if (item == GolfItems.FRINGE_SLAB.get()) return GolfBlocks.FRINGE_SLAB.get();
        if (item == GolfItems.PUTTING_GREEN.get()) return GolfBlocks.PUTTING_GREEN.get();
        if (item == GolfItems.PUTTING_GREEN_SLAB.get()) return GolfBlocks.PUTTING_GREEN_SLAB.get();
        if (item == GolfItems.PUTTING_GREEN_LAYER.get()) return GolfBlocks.PUTTING_GREEN_LAYER.get();
        if (item == GolfItems.ROUGH.get()) return GolfBlocks.ROUGH.get();
        if (item == GolfItems.ROUGH_SLAB.get()) return GolfBlocks.ROUGH_SLAB.get();
        if (item == GolfItems.DEEP_ROUGH.get()) return GolfBlocks.DEEP_ROUGH.get();
        if (item == GolfItems.DEEP_ROUGH_SLAB.get()) return GolfBlocks.DEEP_ROUGH_SLAB.get();
        if (item == GolfItems.GREEN_SLOPE.get()) return GolfBlocks.GREEN_SLOPE.get();
        if (item == GolfItems.FAIRWAY_SLOPE.get()) return GolfBlocks.FAIRWAY_SLOPE.get();
        return GolfBlocks.ROUGH_SLOPE.get();
    }

    private static int tintFor(net.minecraft.world.level.block.Block block, BlockAndTintGetter world, BlockPos pos) {
        int base = (world != null && pos != null) ? BiomeColors.getAverageGrassColor(world, pos) : 0x79C05A;
        // All golf turf now uses vanilla grass_block_top as its actual pixel texture. These are
        // intentionally tiny biome-color offsets, not hard-coded greens: vanilla grass first,
        // golf surface second. This prevents the old double-tinted dark patches.
        if (block == GolfBlocks.GRASS_SLAB.get() || block == GolfBlocks.TEE_MARKER.get()) return base;
        // Still biome-driven and still the vanilla grass texture, but the surface tiers now have
        // enough separation to read from a fairway-height camera without returning to neon turf.
        if (block == GolfBlocks.TEE_GRASS.get() || block == GolfBlocks.TEE_GRASS_SLAB.get()) return adjust(base, 1.070f, 1.095f, 1.030f);
        if (block == GolfBlocks.FAIRWAY.get() || block == GolfBlocks.FAIRWAY_SLAB.get() || block == GolfBlocks.FAIRWAY_SLOPE.get()) return adjust(base, 1.035f, 1.055f, 1.015f);
        if (block == GolfBlocks.FRINGE.get() || block == GolfBlocks.FRINGE_SLAB.get()) return adjust(base, 0.965f, 0.990f, 0.955f);
        if (block == GolfBlocks.PUTTING_GREEN.get() || block == GolfBlocks.PUTTING_GREEN_SLAB.get()
                || block == GolfBlocks.PUTTING_GREEN_LAYER.get() || block == GolfBlocks.GREEN_SLOPE.get()) return adjust(base, 0.900f, 0.985f, 0.885f);
        if (block == GolfBlocks.DEEP_ROUGH.get() || block == GolfBlocks.DEEP_ROUGH_SLAB.get()) return adjust(base, 0.790f, 0.850f, 0.785f);
        return adjust(base, 0.865f, 0.920f, 0.855f);
    }

    private static int adjust(int rgb, float rMul, float gMul, float bMul) {
        int r = Math.min(255, Math.max(0, Math.round(((rgb >> 16) & 0xFF) * rMul)));
        int g = Math.min(255, Math.max(0, Math.round(((rgb >> 8) & 0xFF) * gMul)));
        int b = Math.min(255, Math.max(0, Math.round((rgb & 0xFF) * bMul)));
        return (r << 16) | (g << 8) | b;
    }
}
