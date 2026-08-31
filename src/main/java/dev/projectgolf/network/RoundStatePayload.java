package dev.projectgolf.network;

import dev.projectgolf.ProjectGolf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Compact server-authoritative active-round state for the HUD and held Spotter. */
public record RoundStatePayload(
        boolean active,
        String course,
        int hole,
        int par,
        int strokes,
        int totalStrokes,
        int totalPar,
        BlockPos tee,
        BlockPos cup
) implements CustomPacketPayload {
    public static final Type<RoundStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ProjectGolf.MOD_ID, "round_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RoundStatePayload> STREAM_CODEC =
            StreamCodec.of(RoundStatePayload::encode, RoundStatePayload::decode);

    public static RoundStatePayload inactive() {
        return new RoundStatePayload(false, "", 0, 0, 0, 0, 0, BlockPos.ZERO, BlockPos.ZERO);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, RoundStatePayload payload) {
        buf.writeBoolean(payload.active);
        if (!payload.active) return;
        buf.writeUtf(payload.course, 128);
        buf.writeVarInt(payload.hole);
        buf.writeVarInt(payload.par);
        buf.writeVarInt(payload.strokes);
        buf.writeVarInt(payload.totalStrokes);
        buf.writeVarInt(payload.totalPar);
        buf.writeBlockPos(payload.tee);
        buf.writeBlockPos(payload.cup);
    }

    private static RoundStatePayload decode(RegistryFriendlyByteBuf buf) {
        if (!buf.readBoolean()) return inactive();
        return new RoundStatePayload(
                true,
                buf.readUtf(128),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readBlockPos(),
                buf.readBlockPos());
    }

    public static void handle(RoundStatePayload payload, IPayloadContext context) {
        ClientBridge.showRoundState(payload);
    }
}
