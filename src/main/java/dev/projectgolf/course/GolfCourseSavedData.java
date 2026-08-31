package dev.projectgolf.course;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class GolfCourseSavedData extends SavedData {
    private static final String DATA_NAME = "projectgolf_courses";
    private final Map<String, CourseDefinition> courses = new LinkedHashMap<>();

    public static GolfCourseSavedData create() {
        return new GolfCourseSavedData();
    }

    public static GolfCourseSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        GolfCourseSavedData data = create();
        ListTag courseList = tag.getList("Courses", Tag.TAG_COMPOUND);

        for (int i = 0; i < courseList.size(); i++) {
            CompoundTag courseTag = courseList.getCompound(i);
            CourseDefinition course = new CourseDefinition(courseTag.getString("Name"));

            ListTag holes = courseTag.getList("Holes", Tag.TAG_COMPOUND);
            for (int h = 0; h < holes.size(); h++) {
                CompoundTag holeTag = holes.getCompound(h);
                BlockPos tee = holeTag.getBoolean("HasTee") ? BlockPos.of(holeTag.getLong("Tee")) : null;
                BlockPos cup = holeTag.getBoolean("HasCup") ? BlockPos.of(holeTag.getLong("Cup")) : null;

                course.putHole(new HoleDefinition(
                        holeTag.getInt("Number"),
                        holeTag.getInt("Par"),
                        holeTag.getString("Dimension"),
                        tee,
                        cup));
            }

            data.courses.put(key(course.name()), course);
        }

        return data;
    }

    public static GolfCourseSavedData get(MinecraftServer server) {
        SavedData.Factory<GolfCourseSavedData> factory =
                new SavedData.Factory<>(GolfCourseSavedData::create, GolfCourseSavedData::load);
        return server.overworld().getDataStorage().computeIfAbsent(factory, DATA_NAME);
    }

    public Optional<CourseDefinition> course(String name) {
        return Optional.ofNullable(courses.get(key(name)));
    }

    public CourseDefinition createCourse(String name) {
        CourseDefinition course = new CourseDefinition(name);
        courses.put(key(name), course);
        setDirty();
        return course;
    }

    public Collection<CourseDefinition> courses() {
        return courses.values();
    }

    public void changed() {
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag courseList = new ListTag();

        for (CourseDefinition course : courses.values()) {
            CompoundTag courseTag = new CompoundTag();
            courseTag.putString("Name", course.name());

            ListTag holes = new ListTag();
            for (HoleDefinition hole : course.holes().values()) {
                CompoundTag holeTag = new CompoundTag();
                holeTag.putInt("Number", hole.number());
                holeTag.putInt("Par", hole.par());
                holeTag.putString("Dimension", hole.dimension() == null ? "" : hole.dimension());
                if (hole.tee() != null) {
                    holeTag.putBoolean("HasTee", true);
                    holeTag.putLong("Tee", hole.tee().asLong());
                }
                if (hole.cup() != null) {
                    holeTag.putBoolean("HasCup", true);
                    holeTag.putLong("Cup", hole.cup().asLong());
                }
                holes.add(holeTag);
            }
            courseTag.put("Holes", holes);
            courseList.add(courseTag);
        }

        tag.put("Courses", courseList);
        return tag;
    }

    private static String key(String name) {
        return name.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
