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

/** Server-authoritative presentation data for the Mario-Golf-style hole overview/flyover. */
public record HoleViewPayload(
        String course,
        int hole,
        int par,
        BlockPos tee,
        BlockPos cup,
        List<BlockPos> guides,
        boolean flyover
) implements CustomPacketPayload {
    public static final Type<HoleViewPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ProjectGolf.MOD_ID, "hole_view"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HoleViewPayload> STREAM_CODEC =
            StreamCodec.of(HoleViewPayload::encode, HoleViewPayload::decode);

    public HoleViewPayload {
        guides = guides == null ? List.of() : List.copyOf(guides);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, HoleViewPayload payload) {
        buf.writeUtf(payload.course, 128);
        buf.writeVarInt(payload.hole);
        buf.writeVarInt(payload.par);
        buf.writeBlockPos(payload.tee);
        buf.writeBlockPos(payload.cup);
        int count = Math.min(16, payload.guides.size());
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) buf.writeBlockPos(payload.guides.get(i));
        buf.writeBoolean(payload.flyover);
    }

    private static HoleViewPayload decode(RegistryFriendlyByteBuf buf) {
        String course = buf.readUtf(128);
        int hole = buf.readVarInt();
        int par = buf.readVarInt();
        BlockPos tee = buf.readBlockPos();
        BlockPos cup = buf.readBlockPos();
        int encodedCount = Math.max(0, buf.readVarInt());
        int keptCount = Math.min(16, encodedCount);
        ArrayList<BlockPos> guides = new ArrayList<>(keptCount);
        // Consume every encoded point so the following boolean remains aligned even if a future
        // sender allows more presentation points than this client keeps.
        for (int i = 0; i < encodedCount; i++) {
            BlockPos point = buf.readBlockPos();
            if (i < keptCount) guides.add(point);
        }
        boolean flyover = buf.readBoolean();
        return new HoleViewPayload(course, hole, par, tee, cup, guides, flyover);
    }

    public static void handle(HoleViewPayload payload, IPayloadContext context) {
        ClientBridge.showHoleView(payload);
    }
}
