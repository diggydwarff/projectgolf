package dev.projectgolf.client;

import dev.projectgolf.network.CourseUiActionPayload;
import dev.projectgolf.round.ScorecardData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Polished front-end for course discovery, personal history, and operator course setup. */
public final class CourseUiScreen extends Screen {
    private static final int GREEN = 0xFF173B2A;
    private static final int GREEN_2 = 0xFF24533B;
    private static final int GOLD = 0xFFD2A84A;
    private static final int CREAM = 0xFFF7F0D8;
    private static final int INK = 0xFF162019;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, uuuu");

    private static CourseUiScreen activeScreen;

    // Course chrome is deliberately split into two normal Screen renderables. Background fills
    // render before vanilla widgets; labels render after them. This keeps Project Golf text in the
    // exact same final foreground phase as vanilla button text instead of leaving it in the
    // pre-widget layer that 1.21.1 can soften.
    private boolean textPass;

    private final CompoundTag snapshot;
    private final String mode;
    private final List<CompoundTag> courses = new ArrayList<>();
    private final List<ScorecardData> rounds = new ArrayList<>();
    private int selectedCourse = 0;
    private int selectedHole = 1;
    private int selectedRound = 0;
    private int scroll = 0;
    private int holeScroll = 0;

    private EditBox courseName;
    private EditBox author;
    private EditBox difficulty;
    private EditBox location;
    private EditBox description;
    private EditBox holeNumber;
    private EditBox holePar;
    private EditBox holeName;

    public CourseUiScreen(CompoundTag snapshot) {
        super(Component.literal("Project Golf"));
        this.snapshot = snapshot.copy();
        this.mode = snapshot.getString("Mode");
        ListTag list = snapshot.getList("Courses", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) courses.add(list.getCompound(i).copy());
        ListTag history = snapshot.getList("Rounds", Tag.TAG_COMPOUND);
        for (int i = 0; i < history.size(); i++) rounds.add(ScorecardData.load(history.getCompound(i)));
        String selected = snapshot.getString("SelectedCourse");
        for (int i = 0; i < courses.size(); i++) if (courses.get(i).getString("Name").equalsIgnoreCase(selected)) selectedCourse = i;
        selectedHole = snapshot.getInt("SelectedHole") > 0 ? snapshot.getInt("SelectedHole") : firstHoleNumber(selectedCourse());
    }

    public static void open(CompoundTag data) {
        Minecraft mc = Minecraft.getInstance();
        // Presentation modes are mutually exclusive. Never leave a detached flyover camera or
        // paper scorecard active behind an interactive course menu.
        ClientHoleView.stop();
        ClientScorecardScreen.close();

        CourseUiScreen screen = new CourseUiScreen(data);
        // Set the new active reference before Minecraft removes the previous CourseUiScreen. Its
        // removed() callback can then see that it is stale and will not clear this new screen.
        activeScreen = screen;
        mc.setScreen(screen);
    }

    public static boolean active() {
        return activeScreen != null;
    }

    public static void closeOverlay() {
        CourseUiScreen screen = activeScreen;
        if (screen == null) return;

        Minecraft mc = Minecraft.getInstance();
        activeScreen = null;
        // CourseUiScreen is a real vanilla Screen. Clearing only activeScreen leaves
        // Minecraft.screen installed, which is why Play/Flyover/Select Wand could run behind a
        // menu that never disappeared. Remove the actual screen through Minecraft.
        if (mc.screen == screen) mc.setScreen(null);
        else screen.removed();

        if (mc.screen == null && mc.player != null) mc.mouseHandler.grabMouse();
    }

    public static void tickOverlay() {
        CourseUiScreen screen = activeScreen;
        if (screen == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) {
            closeOverlay();
            return;
        }
        // A HUD editor has no vanilla Screen to suppress gameplay key mappings for it. Keep
        // movement/actions released while the mouse is free and the editor has focus.
        mc.options.keyUp.setDown(false);
        mc.options.keyDown.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false);
        mc.options.keyJump.setDown(false);
        mc.options.keyShift.setDown(false);
        mc.options.keyAttack.setDown(false);
        mc.options.keyUse.setDown(false);
        screen.tick();
    }

    public static boolean handleMouseButton(int button, int action) {
        CourseUiScreen screen = activeScreen;
        if (screen == null) return false;
        double[] mouse = guiMouse();
        if (action == org.lwjgl.glfw.GLFW.GLFW_PRESS) screen.mouseClicked(mouse[0], mouse[1], button);
        else if (action == org.lwjgl.glfw.GLFW.GLFW_RELEASE) screen.mouseReleased(mouse[0], mouse[1], button);
        return true;
    }

    public static boolean handleScroll(double scrollX, double scrollY) {
        CourseUiScreen screen = activeScreen;
        if (screen == null) return false;
        double[] mouse = guiMouse();
        screen.mouseScrolled(mouse[0], mouse[1], scrollX, scrollY);
        return true;
    }

    public static void handleKey(int key, int scanCode, int action, int modifiers) {
        CourseUiScreen screen = activeScreen;
        if (screen == null || action == org.lwjgl.glfw.GLFW.GLFW_RELEASE) return;
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            closeOverlay();
            return;
        }
        boolean handled = screen.keyPressed(key, scanCode, modifiers);
        if (!handled && (action == org.lwjgl.glfw.GLFW.GLFW_PRESS || action == org.lwjgl.glfw.GLFW.GLFW_REPEAT)) {
            Character typed = printableCharacter(key, scanCode, modifiers);
            if (typed != null) screen.charTyped(typed, modifiers);
        }
    }

    private static Character printableCharacter(int key, int scanCode, int modifiers) {
        if ((modifiers & (org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL | org.lwjgl.glfw.GLFW.GLFW_MOD_ALT | org.lwjgl.glfw.GLFW.GLFW_MOD_SUPER)) != 0) return null;
        if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_SPACE) return ' ';
        String name = org.lwjgl.glfw.GLFW.glfwGetKeyName(key, scanCode);
        if (name == null || name.length() != 1) return null;
        char c = name.charAt(0);
        boolean shift = (modifiers & org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT) != 0;
        if (Character.isLetter(c)) return shift ? Character.toUpperCase(c) : Character.toLowerCase(c);
        if (!shift) return c;
        return switch (c) {
            case '1' -> '!'; case '2' -> '@'; case '3' -> '#'; case '4' -> '$'; case '5' -> '%';
            case '6' -> '^'; case '7' -> '&'; case '8' -> '*'; case '9' -> '('; case '0' -> ')';
            case '-' -> '_'; case '=' -> '+'; case '[' -> '{'; case ']' -> '}'; case '\\' -> '|';
            case ';' -> ':'; case '\'' -> '"'; case ',' -> '<'; case '.' -> '>'; case '/' -> '?'; case '`' -> '~';
            default -> c;
        };
    }

    private static double[] guiMouse() {
        Minecraft mc = Minecraft.getInstance();
        double scaleX = (double) mc.getWindow().getGuiScaledWidth() / Math.max(1, mc.getWindow().getScreenWidth());
        double scaleY = (double) mc.getWindow().getGuiScaledHeight() / Math.max(1, mc.getWindow().getScreenHeight());
        return new double[]{mc.mouseHandler.xpos() * scaleX, mc.mouseHandler.ypos() * scaleY};
    }

    @Override public void removed() {
        if (activeScreen == this) activeScreen = null;
        super.removed();
    }

    @Override protected void init() {
        // First renderable: opaque/translucent course chrome only.
        addRenderableOnly((graphics, mouseX, mouseY, partialTick) -> {
            textPass = false;
            renderCourseCanvas(graphics, mouseX, mouseY);
        });

        if (mode.equals("builder")) initBuilder();
        else initViewer();

        // Last renderable: labels only. It is intentionally registered after every vanilla
        // Button/EditBox so custom text is submitted in the same final foreground phase that is
        // visibly pixel-crisp on 1.21.1.
        addRenderableOnly((graphics, mouseX, mouseY, partialTick) -> {
            textPass = true;
            renderCourseCanvas(graphics, mouseX, mouseY);
        });
    }

    private void initViewer() {
        int y = height - 30;
        if (mode.equals("history")) {
            addRenderableWidget(Button.builder(Component.literal("Courses"), b -> request("refresh_browser")).bounds(width / 2 - 104, y, 100, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Open Scorecard"), b -> openRound()).bounds(width / 2 + 4, y, 100, 20).build());
        } else {
            addRenderableWidget(Button.builder(Component.literal("My Scorecards"), b -> request("refresh_history")).bounds(width / 2 - 154, y, 100, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Play Course"), b -> courseAction("play", firstCompleteHole())).bounds(width / 2 - 50, y, 100, 20).build());
            addRenderableWidget(Button.builder(Component.literal("Flyover"), b -> courseAction("flyover", firstCompleteHole())).bounds(width / 2 + 54, y, 100, 20).build());
            if (snapshot.getBoolean("CanBuild")) {
                addRenderableWidget(Button.builder(Component.literal("Course Design"), b -> request("refresh_builder")).bounds(width - 116, 8, 106, 20).build());
            }
        }
    }

    private void initBuilder() {
        int panelX = width / 2 - 10;
        int fieldW = Math.min(190, width / 2 - 30);
        courseName = box(panelX, 48, fieldW, "New course name");
        author = box(panelX, 88, fieldW, "Designer / author");
        difficulty = box(panelX, 118, fieldW, "Difficulty");
        location = box(panelX, 148, fieldW, "Location / clubhouse");
        description = box(panelX, 178, fieldW, "Course description");
        holeNumber = box(panelX, 232, 42, "#");
        holePar = box(panelX + 48, 232, 42, "Par");
        holeName = box(panelX + 96, 232, Math.max(70, fieldW - 96), "Hole name");
        holeNumber.setFilter(s -> s.isEmpty() || s.matches("\\d{1,2}"));
        holePar.setFilter(s -> s.isEmpty() || s.matches("\\d{1,2}"));

        addRenderableWidget(Button.builder(Component.literal("Create"), b -> createCourse()).bounds(panelX, 18, 76, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Save Course"), b -> saveMeta()).bounds(panelX, 202, 92, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Save Hole"), b -> saveHole()).bounds(panelX, 258, 76, 20).build());
        addRenderableWidget(Button.builder(Component.literal("New Hole"), b -> newHole()).bounds(panelX + 80, 258, 70, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Save + Select Wand"), b -> selectHole()).bounds(panelX + 154, 258, 118, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Clear Guides"), b -> clearGuides()).bounds(panelX, 282, 88, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Player Browser"), b -> request("refresh_browser")).bounds(width - 116, height - 28, 106, 20).build());
        loadBuilderFields();
    }

    private EditBox box(int x, int y, int w, String hint) {
        EditBox box = new EditBox(font, x, y, w, 20, Component.literal(hint));
        box.setHint(Component.literal(hint));
        addRenderableWidget(box);
        return box;
    }

    private void loadBuilderFields() {
        CompoundTag c = selectedCourse();
        if (c == null) return;
        author.setValue(c.getString("Author")); difficulty.setValue(c.getString("Difficulty"));
        location.setValue(c.getString("Location")); description.setValue(c.getString("Description"));
        int wanted = selectedHole > 0 ? selectedHole : firstHoleNumber(c);
        selectedHole = wanted;
        CompoundTag h = hole(c, wanted);
        holeNumber.setValue(Integer.toString(wanted));
        holePar.setValue(Integer.toString(h == null ? Math.max(1, snapshot.getInt("SelectedPar")) : h.getInt("Par")));
        holeName.setValue(h == null ? "" : h.getString("Name"));
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // All Project Golf chrome/labels are registered renderables now. Let Screen render the
        // complete ordered widget list in one normal foreground pipeline.
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderCourseCanvas(GuiGraphics g, int mouseX, int mouseY) {
        fill(g, 0, 0, width, height, 0xE6121C16);
        fill(g, 0, 0, width, 36, GREEN);
        fill(g, 0, 34, width, 36, GOLD);
        text(g, "PROJECT GOLF", 14, 10, 0xFFF8F2DE);
        text(g, titleText(), 14, 22, 0xFFFFD978);

        if (mode.equals("history")) renderHistory(g, mouseX, mouseY);
        else renderCourses(g, mouseX, mouseY);
        if (mode.equals("builder")) renderBuilderLabels(g);
    }

    private String titleText() {
        if (mode.equals("builder")) return "COURSE DESIGN STUDIO";
        if (mode.equals("history")) return "PLAYER SCORECARD ARCHIVE";
        return "COURSE CLUBHOUSE";
    }

    private void renderCourses(GuiGraphics g, int mouseX, int mouseY) {
        int listW = Math.min(220, width / 3);
        int x = 10, y = 46, bottom = height - 38;
        int courseBottom = mode.equals("builder") ? Math.max(y + 92, y + (bottom - y) / 2 - 3) : bottom;
        fill(g, x, y, x + listW, courseBottom, 0xFFF3E9CA);
        fill(g, x, y, x + listW, y + 20, GREEN_2);
        text(g, "COURSES", x + 8, y + 6, 0xFFFFFFFF);
        int visible = Math.max(1, (courseBottom - y - 26) / 24);
        int start = Math.min(scroll, Math.max(0, courses.size() - visible));
        for (int i = 0; i < visible && start + i < courses.size(); i++) {
            int idx = start + i, ry = y + 23 + i * 24;
            CompoundTag c = courses.get(idx);
            boolean selected = idx == selectedCourse;
            fill(g, x + 3, ry, x + listW - 3, ry + 21, selected ? 0xFFE2C978 : 0xFFFFF8E5);
            text(g, c.getString("Name"), x + 8, ry + 4, INK);
            text(g, c.getInt("CompleteHoles") + "/" + c.getInt("ConfiguredHoles") + " ready | par " + c.getInt("Par"), x + 8, ry + 13, 0xFF3B3B32);
        }

        if (mode.equals("builder")) renderBuilderHoleList(g, x, courseBottom + 6, listW, bottom);
        else renderCourseDetail(g, listW + 20, 46, width - listW - 30, height - 86);
    }

    private void renderBuilderHoleList(GuiGraphics g, int x, int y, int w, int bottom) {
        fill(g, x, y, x + w, bottom, 0xFFF3E9CA);
        fill(g, x, y, x + w, y + 20, GREEN_2);
        text(g, "HOLES", x + 8, y + 6, 0xFFFFFFFF);
        CompoundTag c = selectedCourse();
        if (c == null) {
            text(g, "Create or select a course first.", x + 8, y + 30, INK);
            return;
        }
        ListTag holes = c.getList("Holes", Tag.TAG_COMPOUND);
        int visible = Math.max(1, (bottom - y - 25) / 22);
        int start = Math.min(holeScroll, Math.max(0, holes.size() - visible));
        for (int i = 0; i < visible && start + i < holes.size(); i++) {
            CompoundTag h = holes.getCompound(start + i);
            int ry = y + 23 + i * 22;
            boolean selected = h.getInt("Number") == selectedHole;
            fill(g, x + 3, ry, x + w - 3, ry + 19, selected ? 0xFFE2C978 : 0xFFFFF8E5);
            String name = h.getString("Name");
            String left = "H" + h.getInt("Number") + "  Par " + h.getInt("Par") + (name.isBlank() ? "" : "  " + name);
            text(g, font.plainSubstrByWidth(left, w - 72), x + 8, ry + 3, INK);
            String state = h.getBoolean("Complete") ? "READY" : ((h.getBoolean("HasTee") ? "T" : "-") + (h.getBoolean("HasCup") ? "C" : "-"));
            text(g, state, x + w - font.width(state) - 8, ry + 3, h.getBoolean("Complete") ? 0xFF17613A : 0xFF7A3F2C);
            if (h.getInt("Guides") > 0) text(g, h.getInt("Guides") + " guides", x + 8, ry + 11, 0xFF4B4B40);
        }
        if (holes.isEmpty()) text(g, "No holes yet. Use New Hole.", x + 8, y + 30, INK);
    }

    private void renderCourseDetail(GuiGraphics g, int x, int y, int w, int h) {
        CompoundTag c = selectedCourse();
        fill(g, x, y, x + w, y + h, CREAM);
        if (c == null) { centeredText(g, "No courses have been created yet.", x + w / 2, y + 30, INK); return; }
        fill(g, x, y, x + w, y + 28, GREEN_2);
        text(g, c.getString("Name").toUpperCase(), x + 10, y + 8, 0xFFFFFFFF);
        int ty = y + 38;
        line(g, x + 10, ty, "Holes", Integer.toString(c.getInt("CompleteHoles"))); ty += 13;
        line(g, x + 10, ty, "Par", Integer.toString(c.getInt("Par"))); ty += 13;
        line(g, x + 10, ty, "Difficulty", blank(c.getString("Difficulty"), "Not rated")); ty += 13;
        line(g, x + 10, ty, "Location", blank(c.getString("Location"), "Not specified")); ty += 13;
        line(g, x + 10, ty, "Designer", blank(c.getString("Author"), "Unknown")); ty += 19;
        String desc = c.getString("Description");
        if (!desc.isBlank()) text(g, font.plainSubstrByWidth(desc, w - 20), x + 10, ty, 0xFF343A31);
        ty += 22;
        if (c.contains("RecordPlayer")) {
            fill(g, x + 8, ty - 4, x + w - 8, ty + 24, 0xFFE8D6A0);
            text(g, "COURSE RECORD", x + 14, ty, GREEN);
            text(g, c.getString("RecordPlayer") + "  " + relative(c.getInt("RecordScore")) + "  (" + c.getInt("RecordStrokes") + ")", x + 14, ty + 11, INK);
        }
    }

    private void renderHistory(GuiGraphics g, int mouseX, int mouseY) {
        int x = 12, y = 46, w = width - 24, bottom = height - 40;
        fill(g, x, y, x + w, bottom, CREAM);
        fill(g, x, y, x + w, y + 22, GREEN_2);
        text(g, "DATE", x + 10, y + 7, 0xFFFFFFFF);
        text(g, "COURSE", x + 100, y + 7, 0xFFFFFFFF);
        text(g, "HOLES", x + w - 145, y + 7, 0xFFFFFFFF);
        text(g, "RESULT", x + w - 85, y + 7, 0xFFFFFFFF);
        int visible = Math.max(1, (bottom - y - 28) / 22);
        int start = Math.min(scroll, Math.max(0, rounds.size() - visible));
        for (int i = 0; i < visible && start + i < rounds.size(); i++) {
            int idx = start + i, ry = y + 24 + i * 22;
            ScorecardData r = rounds.get(idx);
            fill(g, x + 4, ry, x + w - 4, ry + 20, idx == selectedRound ? 0xFFE2C978 : (i % 2 == 0 ? 0xFFFFF8E5 : 0xFFF3E9CA));
            String date = r.endedAt() > 0 ? DATE.format(Instant.ofEpochMilli(r.endedAt()).atZone(ZoneId.systemDefault())) : "Unknown";
            text(g, date, x + 10, ry + 6, INK);
            text(g, font.plainSubstrByWidth(r.course(), Math.max(60, w - 270)), x + 100, ry + 6, INK);
            text(g, Integer.toString(r.holes().size()), x + w - 135, ry + 6, INK);
            text(g, relative(r.relativeToPar()), x + w - 75, ry + 6, scoreColor(r.relativeToPar()));
        }
    }

    private void renderBuilderLabels(GuiGraphics g) {
        int x = width / 2 - 10;
        text(g, "NEW COURSE", x, 40, 0xFFFFDE7D);
        text(g, "COURSE DETAILS", x, 72, 0xFFFFDE7D);
        text(g, "Designer", x, 78, 0xFFFFFFFF);
        text(g, "Difficulty", x, 108, 0xFFFFFFFF);
        text(g, "Location", x, 138, 0xFFFFFFFF);
        text(g, "Description", x, 168, 0xFFFFFFFF);
        text(g, "HOLE SETUP", x, 222, 0xFFFFDE7D);
        text(g, "Save + Select Wand closes this screen and arms the Course Designer.", x + 94, 286, 0xFFFFFFFF);
        text(g, "Use it on Tee Marker / Cup; sneak-use terrain for flyover guide points.", x + 94, 297, 0xFFF3EBD8);
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int listW = Math.min(220, width / 3);
        int y = 46, bottom = height - 38;
        int courseBottom = mode.equals("builder") ? Math.max(y + 92, y + (bottom - y) / 2 - 3) : bottom;
        if (!mode.equals("history") && mouseX >= 13 && mouseX <= 7 + listW && mouseY >= 69 && mouseY < courseBottom) {
            int visible = Math.max(1, (courseBottom - y - 26) / 24);
            int start = Math.min(scroll, Math.max(0, courses.size() - visible));
            int idx = start + ((int) mouseY - 69) / 24;
            if (idx >= 0 && idx < courses.size()) {
                selectedCourse = idx;
                holeScroll = 0;
                selectedHole = firstHoleNumber(selectedCourse());
                if (mode.equals("builder")) loadBuilderFields();
                return true;
            }
        }
        if (mode.equals("builder") && mouseX >= 13 && mouseX <= 7 + listW && mouseY >= courseBottom + 29 && mouseY < bottom) {
            CompoundTag c = selectedCourse();
            if (c != null) {
                ListTag holes = c.getList("Holes", Tag.TAG_COMPOUND);
                int idx = holeScroll + ((int) mouseY - (courseBottom + 29)) / 22;
                if (idx >= 0 && idx < holes.size()) {
                    selectedHole = holes.getCompound(idx).getInt("Number");
                    loadBuilderFields();
                    return true;
                }
            }
        }
        if (mode.equals("history") && mouseY >= 70 && mouseY <= height - 40) {
            int visible = Math.max(1, ((height - 40) - 46 - 28) / 22);
            int start = Math.min(scroll, Math.max(0, rounds.size() - visible));
            int idx = start + ((int) mouseY - 70) / 22;
            if (idx >= 0 && idx < rounds.size()) { selectedRound = idx; return true; }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int direction = scrollY < 0 ? 1 : -1;
        if (mode.equals("builder")) {
            int listW = Math.min(220, width / 3);
            int y = 46, bottom = height - 38;
            int courseBottom = Math.max(y + 92, y + (bottom - y) / 2 - 3);
            if (mouseX >= 10 && mouseX <= 10 + listW && mouseY > courseBottom) {
                CompoundTag c = selectedCourse();
                int size = c == null ? 0 : c.getList("Holes", Tag.TAG_COMPOUND).size();
                int visible = Math.max(1, (bottom - (courseBottom + 6) - 25) / 22);
                holeScroll = Math.max(0, Math.min(Math.max(0, size - visible), holeScroll + direction));
                return true;
            }
        }
        int size = mode.equals("history") ? rounds.size() : courses.size();
        int visible;
        if (mode.equals("history")) {
            visible = Math.max(1, ((height - 40) - 46 - 28) / 22);
        } else {
            int y = 46, bottom = height - 38;
            int courseBottom = mode.equals("builder") ? Math.max(y + 92, y + (bottom - y) / 2 - 3) : bottom;
            visible = Math.max(1, (courseBottom - y - 26) / 24);
        }
        scroll = Math.max(0, Math.min(Math.max(0, size - visible), scroll + direction));
        return true;
    }

    private CompoundTag selectedCourse() { return courses.isEmpty() ? null : courses.get(Math.max(0, Math.min(selectedCourse, courses.size() - 1))); }
    private int firstCompleteHole() {
        CompoundTag c = selectedCourse(); if (c == null) return 1;
        ListTag holes = c.getList("Holes", Tag.TAG_COMPOUND);
        for (int i = 0; i < holes.size(); i++) if (holes.getCompound(i).getBoolean("Complete")) return holes.getCompound(i).getInt("Number");
        return 1;
    }
    private int firstHoleNumber(CompoundTag c) { if (c == null) return 1; ListTag h = c.getList("Holes", Tag.TAG_COMPOUND); return h.isEmpty() ? 1 : h.getCompound(0).getInt("Number"); }
    private int nextHoleNumber() { CompoundTag c=selectedCourse(); if(c==null)return 1; int max=0; for(Tag t:c.getList("Holes",Tag.TAG_COMPOUND))max=Math.max(max,((CompoundTag)t).getInt("Number")); return Math.min(99,max+1); }
    private CompoundTag hole(CompoundTag c, int number) { for (Tag t : c.getList("Holes", Tag.TAG_COMPOUND)) { CompoundTag h=(CompoundTag)t; if(h.getInt("Number")==number)return h; } return null; }

    private void request(String action) {
        CompoundTag extra = new CompoundTag();
        extra.putInt("Hole", selectedHole);
        send(action, extra);
    }
    private void courseAction(String action, int hole) {
        CompoundTag extra = new CompoundTag(); extra.putInt("Hole", hole); send(action, extra);
        closeOverlay();
    }
    private void openRound() {
        if (rounds.isEmpty()) return;
        CompoundTag e = new CompoundTag();
        e.putString("RoundId", rounds.get(selectedRound).roundId().toString());
        send("open_round", e);
        // The scorecard is a world HUD overlay, not a child of this menu. Leave the archive
        // immediately so the incoming ScorecardPayload cannot remain hidden behind Course UI.
        closeOverlay();
    }
    private void createCourse() { CompoundTag e=new CompoundTag(); e.putString("Name", courseName.getValue()); send("create_course", e); }
    private void saveMeta() { CompoundTag e=new CompoundTag(); e.putString("Author",author.getValue());e.putString("Difficulty",difficulty.getValue());e.putString("Location",location.getValue());e.putString("Description",description.getValue());e.putInt("Hole",selectedHole);e.putInt("Par",parse(holePar.getValue(),4));send("save_meta",e); }
    private void saveHole() { CompoundTag e=holeFields(); selectedHole=e.getInt("Hole"); send("save_hole",e); }
    private void newHole() { selectedHole=nextHoleNumber(); holeNumber.setValue(Integer.toString(selectedHole)); holePar.setValue("4"); holeName.setValue(""); }
    private void selectHole() { CompoundTag e=holeFields(); selectedHole=e.getInt("Hole"); send("select_hole",e); closeOverlay(); }
    private void clearGuides() { CompoundTag e=holeFields(); selectedHole=e.getInt("Hole"); send("clear_guides",e); }
    private CompoundTag holeFields() { CompoundTag e=new CompoundTag();e.putInt("Hole",parse(holeNumber.getValue(),1));e.putInt("Par",parse(holePar.getValue(),4));e.putString("Name",holeName.getValue());return e; }
    private int parse(String s,int d){try{return Integer.parseInt(s);}catch(Exception e){return d;}}
    private void send(String action, CompoundTag extra) {
        CompoundTag tag = extra == null ? new CompoundTag() : extra.copy(); tag.putString("Action", action);
        CompoundTag c = selectedCourse(); if (c != null) tag.putString("Course", c.getString("Name"));
        PacketDistributor.sendToServer(new CourseUiActionPayload(tag));
    }

    private void line(GuiGraphics g,int x,int y,String k,String v){text(g,k+":",x,y,0xFF35473A);text(g,v,x+70,y,INK);}

    private void fill(GuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        if (!textPass) g.fill(x1, y1, x2, y2, color);
    }

    /**
     * Custom labels render only in the final text pass, after vanilla widgets. Draw without a
     * shadow so each Minecraft font pixel is submitted exactly once.
     */
    private void text(GuiGraphics g, String value, int x, int y, int color) {
        if (!textPass || value == null || value.isEmpty()) return;
        g.drawString(font, value, x, y, color, false);
    }

    private void centeredText(GuiGraphics g, String value, int centerX, int y, int color) {
        if (!textPass || value == null || value.isEmpty()) return;
        g.drawString(font, value, centerX - font.width(value) / 2, y, color, false);
    }

    private static String blank(String s,String fallback){return s==null||s.isBlank()?fallback:s;}
    private static String relative(int v){return v==0?"E":v>0?"+"+v:Integer.toString(v);}
    private static int scoreColor(int v){return v<0?0xFF185536:v==0?INK:v==1?0xFF78472C:0xFF842D2D;}

    @Override public boolean isPauseScreen() { return false; }
}
