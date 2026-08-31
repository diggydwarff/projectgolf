package dev.projectgolf.course;

import java.util.Map;
import java.util.TreeMap;

public final class CourseDefinition {
    private final String name;
    private final Map<Integer, HoleDefinition> holes = new TreeMap<>();

    public CourseDefinition(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public Map<Integer, HoleDefinition> holes() {
        return holes;
    }

    public HoleDefinition hole(int number) {
        return holes.get(number);
    }

    public HoleDefinition getOrCreateHole(int number) {
        return holes.computeIfAbsent(number, n -> new HoleDefinition(n, 4, "", null, null));
    }

    public void putHole(HoleDefinition hole) {
        holes.put(hole.number(), hole);
    }
}
