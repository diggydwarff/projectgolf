package dev.projectgolf.course;

import dev.projectgolf.registry.GolfBlocks;
import dev.projectgolf.round.GolfRoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import java.util.List;

/**
 * Lightweight normal-player entry point for a course. A linked Tee Marker starts its numbered
 * hole when right-clicked with an empty hand. Builder/block items and clubs are deliberately
 * left alone so course construction stays painless.
 */
public final class TeeMarkerInteraction {
    private TeeMarkerInteraction() {}

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!event.getLevel().getBlockState(event.getPos()).is(GolfBlocks.TEE_MARKER.get())) return;

        // Empty hand is the intentional "play this tee" gesture. Clubs keep RMB exclusively for
        // the swing meter, while builder/block items remain free to place/link normally.
        if (!event.getItemStack().isEmpty()) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (event.getLevel().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        String dimension = player.level().dimension().location().toString();
        GolfCourseSavedData data = GolfCourseSavedData.get(player.getServer());
        List<GolfCourseSavedData.TeeLink> links = data.teesAt(dimension, event.getPos());

        if (links.isEmpty()) {
            player.sendSystemMessage(Component.literal(
                    "This Tee Marker is not linked to a course hole yet."));
            return;
        }
        if (links.size() > 1) {
            player.sendSystemMessage(Component.literal(
                    "This Tee Marker is linked to multiple holes. Fix the course links before playing it."));
            return;
        }

        GolfCourseSavedData.TeeLink link = links.get(0);
        HoleDefinition hole = link.hole();
        if (!hole.complete()) {
            player.sendSystemMessage(Component.literal(
                    link.courseName() + " hole " + hole.number() + " is incomplete; link its Golf Cup first."));
            return;
        }

        if (GolfRoundManager.isPlaying(player, link.courseName(), hole.number())) {
            player.sendSystemMessage(Component.literal(
                    link.courseName() + " hole " + hole.number() + " is already active (par " + hole.par() + ")."));
            return;
        }

        GolfRoundManager.setRound(player, link.courseName(), hole.number());
        player.sendSystemMessage(Component.literal(
                "Started " + link.courseName() + " hole " + hole.number() + " (par " + hole.par() + ")."));
    }
}
