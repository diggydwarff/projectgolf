package dev.projectgolf.client;

import dev.projectgolf.golf.GolfTuning;
import dev.projectgolf.golf.GolfWind;
import dev.projectgolf.golf.SwingMath;
import dev.projectgolf.item.GolfClubItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

public final class GolfHud {
    private GolfHud() {}

    public static void render(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();

        // Scorecards must be drawn in the final HUD layer, after any Screen blur/post effect.
        // Stop here while one is open so normal golf HUD elements do not sit on top of it.
        ClientScorecardScreen.renderOverlay(graphics);
        if (ClientScorecardScreen.active()) return;

        ClientHoleView.renderHud(graphics);
        if (ClientHoleView.active()) return;
        if (mc.player == null) return;

        renderRoundHud(graphics, mc);
        ClientSpotter.renderHud(graphics);

        if (!(mc.player.getMainHandItem().getItem() instanceof GolfClubItem clubItem)) return;

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

    private static void renderRoundHud(GuiGraphics graphics, Minecraft mc) {
        var state = ClientRoundState.state();
        if (!state.active()) return;

        int x = 8;
        int y = 8;
        int width = 206;
        int extraPlayers = Math.min(4, Math.max(0, state.leaderboard().size() - 1));
        int height = 70 + (extraPlayers > 0 ? 8 + extraPlayers * 10 : 0);

        // Compact broadcast-style score card: restrained shadow, one warm golf accent, and
        // separate information rows. It stays readable without looking like a debug rectangle.
        graphics.fill(x + 2, y + 2, x + width + 2, y + height + 2, 0x65000000);
        graphics.fill(x, y, x + width, y + height, 0xE5121815);
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, 0xDB1B241E);
        graphics.fill(x, y, x + width, y + 2, 0xFFE0B84B);
        graphics.fill(x + 1, y + 16, x + width - 1, y + 17, 0x704B5A4D);

        String course = state.course();
        graphics.drawString(mc.font, course, x + 7, y + 6, 0xFFFFE7A0, false);

        String holeBadge = "HOLE " + state.hole();
        int badgeWidth = mc.font.width(holeBadge) + 10;
        int badgeX = x + width - badgeWidth - 5;
        graphics.fill(badgeX, y + 4, x + width - 5, y + 15, 0xD3364639);
        graphics.drawString(mc.font, holeBadge, badgeX + 5, y + 6, 0xFFFFFFFF, false);

        String score = state.totalPar() > 0
                ? formatRelative(state.totalStrokes() - state.totalPar())
                : "E";
        graphics.drawString(mc.font, "PAR " + state.par(), x + 7, y + 23, 0xFFD8D8D8, false);
        graphics.drawString(mc.font, "STROKES " + state.strokes(), x + 61, y + 23, 0xFFFFFFFF, false);
        graphics.drawString(mc.font, "ROUND " + score, x + 139, y + 23, 0xFFFFD66E, false);

        double dx = state.cup().getX() + 0.5 - mc.player.getX();
        double dz = state.cup().getZ() + 0.5 - mc.player.getZ();
        int pinDistance = (int) Math.round(Math.sqrt(dx * dx + dz * dz));
        graphics.fill(x + 7, y + 37, x + width - 7, y + 38, 0x503E4A40);
        graphics.drawString(mc.font, "PIN", x + 7, y + 43, 0xFF8FA598, false);
        graphics.drawString(mc.font, pinDistance + " blocks", x + 35, y + 43, 0xFFF0F0F0, false);

        graphics.drawString(mc.font, "WIND", x + 7, y + 57, 0xFF8FA598, false);
        graphics.drawString(mc.font, GolfWind.hudText(mc.level), x + 39, y + 57, 0xFFE7F3FF, false);
        drawWindIndicator(graphics, mc, x + width - 16, y + 59);

        if (extraPlayers > 0) {
            int ly = y + 72;
            graphics.fill(x + 7, ly - 3, x + width - 7, ly - 2, 0x503E4A40);
            graphics.drawString(mc.font, "GROUP", x + 7, ly + 1, 0xFF8FA598, false);
            int row = 0;
            for (var line : state.leaderboard()) {
                if (line.name().equals(mc.player.getGameProfile().getName())) continue;
                if (row >= 4) break;
                int rel = line.totalStrokes() - line.totalPar();
                String text = line.name() + "  H" + line.hole() + "  " + line.strokes() + " st  " + formatRelative(rel);
                graphics.drawString(mc.font, text, x + 47, ly + 1 + row * 10, 0xFFE7E7E7, false);
                row++;
            }
        }
    }

    /**
     * Small camera-relative wind vane. The compass letters remain world-absolute, but this arrow
     * physically rotates as the player turns, so a crosswind instantly reads as left/right.
     */
    private static void drawWindIndicator(GuiGraphics graphics, Minecraft mc, int centerX, int centerY) {
        var wind = GolfWind.sample(mc.level);
        var relative = GolfWind.relativeToYaw(wind.direction(), mc.player.getYRot());
        int radius = 8;
        int endX = centerX + (int) Math.round(relative.right() * radius);
        int endY = centerY - (int) Math.round(relative.forward() * radius);

        graphics.fill(centerX - 9, centerY - 9, centerX + 10, centerY + 10, 0x70101010);
        graphics.fill(centerX, centerY, centerX + 1, centerY + 1, 0xFFB8C5CC);
        drawPixelLine(graphics, centerX, centerY, endX, endY, 0xFFEAF7FF);

        // Bright 3-pixel tip so the direction is readable even at small GUI scales.
        graphics.fill(endX - 1, endY - 1, endX + 2, endY + 2, 0xFFFFFFFF);
    }

    private static void drawPixelLine(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0);
        int sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        while (true) {
            graphics.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = err * 2;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }

    private static String formatRelative(int value) {
        if (value == 0) return "E";
        return value > 0 ? "+" + value : Integer.toString(value);
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
