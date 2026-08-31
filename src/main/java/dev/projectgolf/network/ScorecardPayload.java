package dev.projectgolf.network;

import dev.projectgolf.ProjectGolf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Opens an immutable scorecard snapshot on the client. */
public record ScorecardPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<ScorecardPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ProjectGolf.MOD_ID, "scorecard"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ScorecardPayload> STREAM_CODEC =
            StreamCodec.of(ScorecardPayload::encode, ScorecardPayload::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, ScorecardPayload payload) {
        buf.writeNbt(payload.data);
    }

    private static ScorecardPayload decode(RegistryFriendlyByteBuf buf) {
        CompoundTag tag = buf.readNbt();
        return new ScorecardPayload(tag == null ? new CompoundTag() : tag);
    }

    public static void handle(ScorecardPayload payload, IPayloadContext context) {
        ClientBridge.showScorecard(payload.data);
    }
}
