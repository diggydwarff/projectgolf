package dev.projectgolf.client;

import dev.projectgolf.golf.GolfTuning;
import dev.projectgolf.golf.SwingMath;
import dev.projectgolf.item.GolfClubItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

public final class GolfHud {
    private GolfHud() {}

    public static void render(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !(mc.player.getMainHandItem().getItem() instanceof GolfClubItem clubItem)) return;

        ClientSwingController.trackedBall().ifPresent(ball -> {
            double distance = Math.sqrt(mc.player.distanceToSqr(ball));
            if (distance >= GolfTuning.BALL_LOCATOR_MIN_DISTANCE || !ball.isStationary()) {
                String state = ball.isStationary() ? "LANDED" : "BALL IN FLIGHT";
                String direction = directionHint(mc, ball);
                int color = ball.isStationary() ? 0xFFFFD86A : 0xFFFFFFFF;
                graphics.drawCenteredString(mc.font,
                        state + " - " + Math.round(distance) + " blocks - " + direction,
                        graphics.guiWidth() / 2, 10, color);
            }
        });

        int width = 180;
        int height = 9;
        int x = (graphics.guiWidth() - width) / 2;
        int y = graphics.guiHeight() - 62;

        graphics.drawCenteredString(mc.font, "PROJECT GOLF - " + clubItem.club().displayName(),
                graphics.guiWidth() / 2, y - 22, 0xFFFFFFFF);

        if (ClientSwingController.showPerfectFlash()) {
            graphics.drawCenteredString(mc.font, "PERFECT!",
                    graphics.guiWidth() / 2, y - 34, 0xFFFFD54A);
        }

        long planned = Math.round(ClientSwingController.plannedDistanceBlocks());
        int targetPct = Math.round(ClientSwingController.plannedPower(clubItem.club()) * 100.0f);
        String targetText = planned > 0
                ? "Target " + planned + " blocks (" + targetPct + "%)"
                : "Target " + targetPct + "%";

        ClientSwingController.Phase phase = ClientSwingController.phase();
        if (phase == ClientSwingController.Phase.IDLE) {
            graphics.drawCenteredString(mc.font, targetText + " - Shift+Wheel adjusts",
                    graphics.guiWidth() / 2, y - 8, 0xFFD0D0D0);
            return;
        }

        if (phase == ClientSwingController.Phase.POWER) {
            float meter = ClientSwingController.meterPower();
            graphics.drawString(mc.font, "POWER " + Math.round(meter * 100) + "% - " + targetText,
                    x, y - 11, 0xFFFFFFFF);
            drawBar(graphics, x, y, width, height, meter, 0xFFEEEEEE);
        } else if (phase == ClientSwingController.Phase.ACCURACY) {
            float accuracy = ClientSwingController.currentAccuracy();
            graphics.drawString(mc.font, "IMPACT - " + SwingMath.accuracyLabel(accuracy), x, y - 11, 0xFFFFFFFF);
            graphics.fill(x, y, x + width, y + height, 0xAA101010);

            int perfectHalfWidth = Math.max(2, (int) (width * GolfTuning.PERFECT_ACCURACY_WINDOW / 2.0));
            int center = x + width / 2;
            graphics.fill(center - perfectHalfWidth, y, center + perfectHalfWidth, y + height, 0xFFFFC94A);

            int marker = x + Math.round((accuracy + 1.0f) * 0.5f * width);
            int markerColor = Math.abs(accuracy) <= GolfTuning.PERFECT_ACCURACY_WINDOW
                    ? 0xFFFFE37A : 0xFFFFFFFF;
            graphics.fill(marker - 1, y - 2, marker + 1, y + height + 2, markerColor);
        } else {
            graphics.drawCenteredString(mc.font, "SHOT SENT",
                    graphics.guiWidth() / 2, y, 0xFFFFFFFF);
        }
    }

    private static String directionHint(Minecraft mc, dev.projectgolf.entity.GolfBallEntity ball) {
        double dx = ball.getX() - mc.player.getX();
        double dz = ball.getZ() - mc.player.getZ();
        float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float delta = Mth.wrapDegrees(targetYaw - mc.player.getYRot());
        float abs = Math.abs(delta);
        if (abs <= 22.5f) return "AHEAD";
        if (abs >= 157.5f) return "BEHIND";
        return delta > 0.0f ? "RIGHT" : "LEFT";
    }

    private static void drawBar(GuiGraphics graphics, int x, int y, int width, int height, float amount, int fillColor) {
        graphics.fill(x, y, x + width, y + height, 0xAA101010);
        graphics.fill(x + 1, y + 1, x + 1 + Math.round((width - 2) * amount), y + height - 1, fillColor);
    }
}
