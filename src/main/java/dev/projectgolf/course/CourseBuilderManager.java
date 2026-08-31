package dev.projectgolf.course;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Lightweight per-player course-builder selection. The actual course remains in world SavedData;
 * this only remembers which course/hole the designer wand should edit next.
 */
public final class CourseBuilderManager {
    private CourseBuilderManager() {}

    private static final String ROOT = "ProjectGolfCourseBuilder";
    private static final String COURSE = "Course";
    private static final String HOLE = "Hole";
    private static final String PAR = "Par";

    public record Selection(String course, int hole, int par) {}

    public static void select(ServerPlayer player, String course, int hole, int par) {
        CompoundTag tag = builderTag(player);
        tag.putString(COURSE, course);
        tag.putInt(HOLE, Math.max(1, hole));
        tag.putInt(PAR, Math.max(1, par));
    }

    public static Optional<Selection> selection(ServerPlayer player) {
        CompoundTag persistent = player.getPersistentData();
        if (!persistent.contains(ROOT, Tag.TAG_COMPOUND)) return Optional.empty();
        CompoundTag tag = persistent.getCompound(ROOT);
        String course = tag.getString(COURSE);
        int hole = tag.getInt(HOLE);
        int par = tag.getInt(PAR);
        if (course.isBlank() || hole <= 0 || par <= 0) return Optional.empty();
        return Optional.of(new Selection(course, hole, par));
    }

    public static void clear(ServerPlayer player) {
        player.getPersistentData().remove(ROOT);
    }

    private static CompoundTag builderTag(ServerPlayer player) {
        CompoundTag persistent = player.getPersistentData();
        if (!persistent.contains(ROOT, Tag.TAG_COMPOUND)) {
            persistent.put(ROOT, new CompoundTag());
        }
        return persistent.getCompound(ROOT);
    }
}
