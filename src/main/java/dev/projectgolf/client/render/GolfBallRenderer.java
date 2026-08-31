package dev.projectgolf.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.projectgolf.entity.GolfBallEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.util.Mth;

/**
 * Keeps the physical ball small while making its rendered silhouette progressively easier to
 * follow at driving distance. The multiplier only affects rendering, never hitboxes or physics.
 */
public final class GolfBallRenderer extends ThrownItemRenderer<GolfBallEntity> {
    public GolfBallRenderer(EntityRendererProvider.Context context) {
        super(context, 0.58f, true);
    }

    @Override
    public void render(GolfBallEntity ball, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        Minecraft mc = Minecraft.getInstance();
        double distance = mc.player == null ? 0.0 : Math.sqrt(mc.player.distanceToSqr(ball));
        float distanceScale = 1.0f + Mth.clamp((float) ((distance - 16.0) / 100.0), 0.0f, 1.0f) * 1.75f;

        poseStack.pushPose();
        poseStack.scale(distanceScale, distanceScale, distanceScale);
        super.render(ball, entityYaw, partialTicks, poseStack, buffer, packedLight);
        poseStack.popPose();
    }
}
