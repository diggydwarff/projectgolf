package dev.projectgolf.network;

import dev.projectgolf.ProjectGolf;
import dev.projectgolf.entity.GolfBallEntity;
import dev.projectgolf.item.GolfClubItem;
import dev.projectgolf.round.GolfRoundManager;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SwingPayload(float power, float accuracy, float spinX, float spinY) implements CustomPacketPayload {
    public static final Type<SwingPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ProjectGolf.MOD_ID, "swing"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SwingPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.FLOAT, SwingPayload::power,
                    ByteBufCodecs.FLOAT, SwingPayload::accuracy,
                    ByteBufCodecs.FLOAT, SwingPayload::spinX,
                    ByteBufCodecs.FLOAT, SwingPayload::spinY,
                    SwingPayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SwingPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (!(player.getMainHandItem().getItem() instanceof GolfClubItem clubItem)) return;
            if (!GolfRoundManager.canSwing(player)) return;

            float power = Mth.clamp(payload.power(), 0.0f, 1.0f);
            float accuracy = Mth.clamp(payload.accuracy(), -1.0f, 1.0f);
            if (!Float.isFinite(power) || !Float.isFinite(accuracy)) return;

            GolfBallEntity ball = GolfRoundManager.findOwnedBall(player).orElse(null);
            if (ball == null) {
                player.sendSystemMessage(Component.literal("No stationary owned golf ball within range."));
                return;
            }

            if (!ball.launchFromClub(player, clubItem.club(), power, accuracy)) {
                player.sendSystemMessage(Component.literal("Ball is still moving."));
            }
        });
    }
}
