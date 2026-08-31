package dev.projectgolf.client;

import dev.projectgolf.round.RoundHoleScore;
import dev.projectgolf.round.ScorecardData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** Dedicated paper-scorecard presentation for completed and exited rounds. */
public final class ClientScorecardScreen {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("MMM d, uuuu  h:mm a");
    private static ScorecardData activeCard;

    private ClientScorecardScreen() {}

    /**
     * Scorecards intentionally do NOT open a vanilla Screen. Minecraft 1.21.1 applies its menu
     * blur to the complete Screen render target on some render paths, which blurred the paper,
     * font, hotbar and world together. Keeping this as a pure HUD overlay means there is no menu
     * screen/post-process to blur in the first place. Existing scorecard items use this renderer
     * too; players do not need to obtain a new card after updating.
     */
    public static void open(CompoundTag tag) {
        activeCard = ScorecardData.load(tag);
    }

    public static boolean active() {
        return activeCard != null;
    }

    public static void close() {
        activeCard = null;
    }

    public static void renderOverlay(GuiGraphics g) {
        if (activeCard == null) return;
        renderCard(g, activeCard);
    }

    private static void renderCard(GuiGraphics g, ScorecardData card) {
        Minecraft mc = Minecraft.getInstance();
        var font = mc.font;
        // Focus the card without using Screen#renderBackground or a blur shader.
        g.fill(0, 0, g.guiWidth(), g.guiHeight(), 0xA0000000);

        int panelW = Math.min(330, g.guiWidth() - 20);
        int panelH = Math.min(226, g.guiHeight() - 20);
        int x = (g.guiWidth() - panelW) / 2;
        int y = (g.guiHeight() - panelH) / 2;

        // Warm paper with high-contrast clubhouse ink. Keep the texture restrained, but make
        // every information tier easy to read against Minecraft's busy world background.
        g.fill(x + 3, y + 4, x + panelW + 3, y + panelH + 4, 0x80000000);
        g.fill(x, y, x + panelW, y + panelH, 0xFFF0E3BD);
        g.fill(x + 2, y + 2, x + panelW - 2, y + panelH - 2, 0xFFFFF6DD);
        g.fill(x, y, x + panelW, y + 4, 0xFFB4872C);
        g.fill(x + 8, y + 31, x + panelW - 8, y + 33, 0xFF234B34);

        g.drawCenteredString(font, card.course().toUpperCase(), x + panelW / 2, y + 9, 0xFF102A1B);
        String status = card.completed() ? "OFFICIAL ROUND SCORECARD"
                : ("EXITED".equals(card.finishReason()) ? "ROUND SCORECARD - EXITED EARLY" : "PARTIAL ROUND SCORECARD");
        g.drawCenteredString(font, status, x + panelW / 2, y + 20, 0xFF4A3618);

        int left = x + 10;
        String date = card.endedAt() > 0
                ? DATE_TIME.format(Instant.ofEpochMilli(card.endedAt()).atZone(ZoneId.systemDefault()))
                : "Unknown date";
        g.drawString(font, "Player: " + card.playerName(), left, y + 38, 0xFF102A1B, false);
        g.drawString(font, date, left, y + 49, 0xFF29291F, false);
        g.drawString(font, "Weather: " + card.startWeather() + " -> " + card.endWeather(), left, y + 60, 0xFF29291F, false);
        g.drawString(font, "Time: " + card.startTimeOfDay() + " -> " + card.endTimeOfDay(), left, y + 71, 0xFF29291F, false);

        // Light rule separates round metadata from the actual score grid.
        g.fill(left, y + 82, x + panelW - 10, y + 83, 0xFFB9AA82);

        drawNine(g, font, card.holes().stream().filter(h -> h.hole() <= 9).toList(), left, y + 87, "OUT");
        drawNine(g, font, card.holes().stream().filter(h -> h.hole() > 9).toList(), left, y + 127, "IN");

        int footerY = y + 170;
        int relative = card.relativeToPar();
        g.fill(left - 3, footerY - 4, x + panelW - 10, footerY + 12, 0xFFE4D5AB);
        g.fill(left - 3, footerY - 4, left - 1, footerY + 12, 0xFF234B34);
        g.drawString(font, "TOTAL  " + card.totalStrokes() + "   PAR  " + card.totalPar()
                + "   SCORE  " + formatRelative(relative), left, footerY, 0xFF102A1B, false);

        String playing = card.participants().size() <= 1
                ? "Played solo"
                : "Group: " + String.join(", ", card.participants());
        g.drawString(font, trim(font, playing, panelW - 20), left, footerY + 14, 0xFF29291F, false);

        String meta = joinMeta(card);
        if (!meta.isBlank()) g.drawString(font, trim(font, meta, panelW - 20), left, footerY + 26, 0xFF4A3618, false);
        if (!card.courseDescription().isBlank()) {
            g.drawString(font, trim(font, card.courseDescription(), panelW - 20), left, footerY + 38, 0xFF4A3618, false);
        }

        g.fill(x + 8, y + panelH - 23, x + panelW - 8, y + panelH - 22, 0xFFC7B88F);
        g.drawCenteredString(font, "Right-click to close - right-click the scorecard to reopen", x + panelW / 2,
                y + panelH - 14, 0xFF443B28);
    }

    private static void drawNine(GuiGraphics g, net.minecraft.client.gui.Font font, List<RoundHoleScore> holes, int x, int y, String totalLabel) {
        if (holes.isEmpty()) return;
        int labelW = 34;
        int available = Math.min(286, g.guiWidth() - 40);
        int colW = Math.max(18, (available - labelW - 28) / 9);

        // A subtle three-row table makes the numbers much easier to track horizontally.
        int tableRight = x + labelW + 9 * colW + 26;
        g.fill(x - 3, y - 3, tableRight, y + 9, 0xFFE4D5AB);
        g.fill(x - 3, y + 9, tableRight, y + 20, 0xFFF4E9CC);
        g.fill(x - 3, y + 20, tableRight, y + 33, 0xFFE9DCB8);
        g.fill(x - 3, y - 3, x - 1, y + 33, 0xFF234B34);

        g.drawString(font, "HOLE", x, y, 0xFF102A1B, false);
        g.drawString(font, "PAR", x, y + 11, 0xFF263D2D, false);
        g.drawString(font, "SCORE", x, y + 22, 0xFF102A1B, false);
        int parTotal = 0;
        int scoreTotal = 0;
        for (int i = 0; i < holes.size() && i < 9; i++) {
            RoundHoleScore hole = holes.get(i);
            int cx = x + labelW + i * colW;
            g.drawCenteredString(font, Integer.toString(hole.hole()), cx + colW / 2, y, 0xFF102A1B);
            g.drawCenteredString(font, Integer.toString(hole.par()), cx + colW / 2, y + 11, 0xFF263D2D);
            String scoreText = Integer.toString(hole.strokes()) + (hole.completed() ? "" : "*");
            g.drawCenteredString(font, scoreText, cx + colW / 2, y + 22, scoreColor(hole));
            parTotal += hole.par();
            scoreTotal += hole.strokes();
        }
        int tx = x + labelW + 9 * colW;
        g.fill(tx - 3, y - 3, tx - 2, y + 33, 0xFFB59E69);
        g.drawString(font, totalLabel, tx, y, 0xFF4A3618, false);
        g.drawString(font, Integer.toString(parTotal), tx, y + 11, 0xFF4A3618, false);
        g.drawString(font, Integer.toString(scoreTotal), tx, y + 22, 0xFF4A3618, false);
    }

    private static int scoreColor(RoundHoleScore hole) {
        int relative = hole.relativeToPar();
        if (relative <= -2) return 0xFF8C4A08;
        if (relative == -1) return 0xFF175432;
        if (relative == 0) return 0xFF102A1B;
        if (relative == 1) return 0xFF70442E;
        return 0xFF762525;
    }

    private static String joinMeta(ScorecardData card) {
        StringBuilder b = new StringBuilder();
        if (!card.courseLocation().isBlank()) b.append(card.courseLocation());
        if (!card.courseDifficulty().isBlank()) {
            if (!b.isEmpty()) b.append("  |  ");
            b.append(card.courseDifficulty());
        }
        if (!card.courseAuthor().isBlank()) {
            if (!b.isEmpty()) b.append("  |  ");
            b.append("Designed by ").append(card.courseAuthor());
        }
        return b.toString();
    }

    private static String trim(net.minecraft.client.gui.Font font, String text, int maxWidth) {
        return font.width(text) <= maxWidth ? text : font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width("..."))) + "...";
    }

    private static String formatRelative(int value) {
        if (value == 0) return "E";
        return value > 0 ? "+" + value : Integer.toString(value);
    }

}
