package dev.projectgolf.client;

import dev.projectgolf.ProjectGolf;
import dev.projectgolf.client.render.GolfBallRenderer;
import dev.projectgolf.item.GolfClubItem;
import dev.projectgolf.registry.GolfBlocks;
import dev.projectgolf.registry.GolfEntities;
import dev.projectgolf.registry.GolfItems;
import net.minecraft.resources.ResourceLocation;
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
        event.register((state, world, pos, tintIndex) -> tintFor(state.getBlock()),
                GolfBlocks.GRASS_SLAB.get(),
                GolfBlocks.TEE_GRASS.get(), GolfBlocks.TEE_GRASS_SLAB.get(),
                GolfBlocks.FAIRWAY.get(), GolfBlocks.FAIRWAY_SLAB.get(),
                GolfBlocks.FRINGE.get(), GolfBlocks.FRINGE_SLAB.get(),
                GolfBlocks.PUTTING_GREEN.get(), GolfBlocks.PUTTING_GREEN_SLAB.get(), GolfBlocks.PUTTING_GREEN_LAYER.get(),
                GolfBlocks.ROUGH.get(), GolfBlocks.ROUGH_SLAB.get(),
                GolfBlocks.DEEP_ROUGH.get(), GolfBlocks.DEEP_ROUGH_SLAB.get(),
                GolfBlocks.GREEN_SLOPE.get(), GolfBlocks.FAIRWAY_SLOPE.get(), GolfBlocks.ROUGH_SLOPE.get());
    }

    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> tintFor(itemTintBlock(stack.getItem())),
                GolfItems.GRASS_SLAB.get(),
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

        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
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

    private static int tintFor(net.minecraft.world.level.block.Block block) {
        if (block == GolfBlocks.GRASS_SLAB.get()) return 0x71A74F;
        if (block == GolfBlocks.PUTTING_GREEN.get() || block == GolfBlocks.PUTTING_GREEN_SLAB.get()
                || block == GolfBlocks.PUTTING_GREEN_LAYER.get() || block == GolfBlocks.GREEN_SLOPE.get()) return 0x4F9E42;
        if (block == GolfBlocks.TEE_GRASS.get() || block == GolfBlocks.TEE_GRASS_SLAB.get()) return 0x62A94F;
        if (block == GolfBlocks.FAIRWAY.get() || block == GolfBlocks.FAIRWAY_SLAB.get() || block == GolfBlocks.FAIRWAY_SLOPE.get()) return 0x68A957;
        if (block == GolfBlocks.FRINGE.get() || block == GolfBlocks.FRINGE_SLAB.get()) return 0x5D954D;
        if (block == GolfBlocks.DEEP_ROUGH.get() || block == GolfBlocks.DEEP_ROUGH_SLAB.get()) return 0x3F7038;
        return 0x4E843F;
    }
}
