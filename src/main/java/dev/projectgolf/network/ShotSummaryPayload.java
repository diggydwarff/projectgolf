package dev.projectgolf.network;

import dev.projectgolf.ProjectGolf;
import dev.projectgolf.golf.ClubType;
import dev.projectgolf.golf.GolfSurface;
import dev.projectgolf.golf.SwingMath;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** Compact server-authoritative shot summary delivered to the vanilla toast area. */
public record ShotSummaryPayload(String title, String message) implements CustomPacketPayload {
    public static final Type<ShotSummaryPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ProjectGolf.MOD_ID, "shot_summary"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShotSummaryPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(128), ShotSummaryPayload::title,
                    ByteBufCodecs.stringUtf8(256), ShotSummaryPayload::message,
                    ShotSummaryPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ShotSummaryPayload payload, IPayloadContext context) {
        // The bridge is a no-op on a dedicated server and is installed only by ProjectGolfClient.
        ClientBridge.showShotSummary(payload.title(), payload.message());
    }

    public static ShotSummaryPayload forStoppedShot(
            @Nullable ClubType club,
            float power,
            float accuracy,
            double total,
            double carry,
            double roll,
            GolfSurface finalLie,
            int stroke,
            boolean holed
    ) {
        String clubName = club == null ? "Golf Shot" : club.displayName();
        String accuracyLabel = club == null ? "" : SwingMath.accuracyLabel(accuracy);
        String title = holed
                ? "HOLED - " + clubName + (accuracyLabel.isBlank() ? "" : " - " + accuracyLabel)
                : clubName + (accuracyLabel.isBlank() ? "" : " - " + accuracyLabel)
                    + " - " + Math.round(power * 100.0f) + "%";

        String message = String.format(Locale.ROOT,
                "%.1f blocks | Carry %.1f | Roll %.1f | %s | Stroke %d",
                total, carry, roll, holed ? "In the cup" : finalLie.displayName(), Math.max(0, stroke));
        return new ShotSummaryPayload(title, message);
    }
}
