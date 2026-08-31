package dev.projectgolf.network;

import dev.projectgolf.ProjectGolf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/** Compact server-authoritative active-round state for the HUD, Spotter and shared group leaderboard. */
public record RoundStatePayload(
        boolean active,
        String course,
        int hole,
        int par,
        int strokes,
        int totalStrokes,
        int totalPar,
        BlockPos tee,
        BlockPos cup,
        List<PlayerLine> leaderboard
) implements CustomPacketPayload {
    public record PlayerLine(String name, int hole, int strokes, int totalStrokes, int totalPar) {}

    public static final Type<RoundStatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ProjectGolf.MOD_ID, "round_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RoundStatePayload> STREAM_CODEC =
            StreamCodec.of(RoundStatePayload::encode, RoundStatePayload::decode);

    public RoundStatePayload {
        leaderboard = leaderboard == null ? List.of() : List.copyOf(leaderboard);
    }

    public static RoundStatePayload inactive() {
        return new RoundStatePayload(false, "", 0, 0, 0, 0, 0, BlockPos.ZERO, BlockPos.ZERO, List.of());
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
        buf.writeVarInt(payload.leaderboard.size());
        for (PlayerLine line : payload.leaderboard) {
            buf.writeUtf(line.name, 64);
            buf.writeVarInt(line.hole);
            buf.writeVarInt(line.strokes);
            buf.writeVarInt(line.totalStrokes);
            buf.writeVarInt(line.totalPar);
        }
    }

    private static RoundStatePayload decode(RegistryFriendlyByteBuf buf) {
        if (!buf.readBoolean()) return inactive();
        String course = buf.readUtf(128);
        int hole = buf.readVarInt();
        int par = buf.readVarInt();
        int strokes = buf.readVarInt();
        int totalStrokes = buf.readVarInt();
        int totalPar = buf.readVarInt();
        BlockPos tee = buf.readBlockPos();
        BlockPos cup = buf.readBlockPos();
        int count = Math.min(32, Math.max(0, buf.readVarInt()));
        List<PlayerLine> leaderboard = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            leaderboard.add(new PlayerLine(
                    buf.readUtf(64),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt(),
                    buf.readVarInt()));
        }
        return new RoundStatePayload(true, course, hole, par, strokes, totalStrokes, totalPar, tee, cup, leaderboard);
    }

    public static void handle(RoundStatePayload payload, IPayloadContext context) {
        ClientBridge.showRoundState(payload);
    }
}
