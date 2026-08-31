package dev.projectgolf.network;

import dev.projectgolf.ProjectGolf;
import dev.projectgolf.course.CourseUiService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Small validated client -> server actions from Project Golf course screens. */
public record CourseUiActionPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<CourseUiActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ProjectGolf.MOD_ID, "course_ui_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CourseUiActionPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> buf.writeNbt(payload.data), buf -> {
                CompoundTag tag = buf.readNbt();
                return new CourseUiActionPayload(tag == null ? new CompoundTag() : tag);
            });

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(CourseUiActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) CourseUiService.handle(player, payload.data);
        });
    }
}
