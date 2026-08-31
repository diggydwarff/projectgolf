package dev.projectgolf.network;

import dev.projectgolf.ProjectGolf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server snapshot for the course browser, history browser, and course-builder screens. */
public record CourseUiPayload(CompoundTag data) implements CustomPacketPayload {
    public static final Type<CourseUiPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(ProjectGolf.MOD_ID, "course_ui"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CourseUiPayload> STREAM_CODEC =
            StreamCodec.of((buf, payload) -> buf.writeNbt(payload.data), buf -> {
                CompoundTag tag = buf.readNbt();
                return new CourseUiPayload(tag == null ? new CompoundTag() : tag);
            });

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(CourseUiPayload payload, IPayloadContext context) {
        ClientBridge.showCourseUi(payload.data);
    }
}
