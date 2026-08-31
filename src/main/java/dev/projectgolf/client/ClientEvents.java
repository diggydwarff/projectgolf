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

public final class ClientEvents {
    private ClientEvents() {}

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(GolfEntities.GOLF_BALL.get(), GolfBallRenderer::new);
    }

    public static void registerHud(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(ProjectGolf.MOD_ID, "golf_hud"),
                (graphics, deltaTracker) -> GolfHud.render(graphics));
    }

    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register((state, world, pos, tintIndex) -> tintFor(state.getBlock()),
                GolfBlocks.TEE_GRASS.get(), GolfBlocks.FAIRWAY.get(), GolfBlocks.FRINGE.get(),
                GolfBlocks.PUTTING_GREEN.get(), GolfBlocks.ROUGH.get(), GolfBlocks.DEEP_ROUGH.get(),
                GolfBlocks.GREEN_SLOPE.get(), GolfBlocks.FAIRWAY_SLOPE.get(), GolfBlocks.ROUGH_SLOPE.get());
    }

    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> tintFor(
                        stack.getItem() == GolfItems.TEE_GRASS.get() ? GolfBlocks.TEE_GRASS.get()
                        : stack.getItem() == GolfItems.FAIRWAY.get() ? GolfBlocks.FAIRWAY.get()
                        : stack.getItem() == GolfItems.FRINGE.get() ? GolfBlocks.FRINGE.get()
                        : stack.getItem() == GolfItems.PUTTING_GREEN.get() ? GolfBlocks.PUTTING_GREEN.get()
                        : stack.getItem() == GolfItems.ROUGH.get() ? GolfBlocks.ROUGH.get()
                        : stack.getItem() == GolfItems.DEEP_ROUGH.get() ? GolfBlocks.DEEP_ROUGH.get()
                        : stack.getItem() == GolfItems.GREEN_SLOPE.get() ? GolfBlocks.GREEN_SLOPE.get()
                        : stack.getItem() == GolfItems.FAIRWAY_SLOPE.get() ? GolfBlocks.FAIRWAY_SLOPE.get()
                        : GolfBlocks.ROUGH_SLOPE.get()),
                GolfItems.TEE_GRASS.get(), GolfItems.FAIRWAY.get(), GolfItems.FRINGE.get(),
                GolfItems.PUTTING_GREEN.get(), GolfItems.ROUGH.get(), GolfItems.DEEP_ROUGH.get(),
                GolfItems.GREEN_SLOPE.get(), GolfItems.FAIRWAY_SLOPE.get(), GolfItems.ROUGH_SLOPE.get());
    }

    public static void onClientTick(ClientTickEvent.Post event) {
        ClientSwingController.tick();
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

    private static int tintFor(net.minecraft.world.level.block.Block block) {
        if (block == GolfBlocks.PUTTING_GREEN.get() || block == GolfBlocks.GREEN_SLOPE.get()) return 0x4F9E42;
        if (block == GolfBlocks.TEE_GRASS.get()) return 0x62A94F;
        if (block == GolfBlocks.FAIRWAY.get() || block == GolfBlocks.FAIRWAY_SLOPE.get()) return 0x68A957;
        if (block == GolfBlocks.FRINGE.get()) return 0x5D954D;
        if (block == GolfBlocks.DEEP_ROUGH.get()) return 0x3F7038;
        return 0x4E843F;
    }
}
