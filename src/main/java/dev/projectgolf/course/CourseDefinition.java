package dev.projectgolf.course;

import java.util.Map;
import java.util.TreeMap;
import java.util.Optional;

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

    public int completeHoleCount() {
        return (int) holes.values().stream().filter(HoleDefinition::complete).count();
    }

    public Optional<HoleDefinition> firstCompleteHole() {
        return holes.values().stream().filter(HoleDefinition::complete).findFirst();
    }

    public Optional<HoleDefinition> nextCompleteHoleAfter(int number) {
        return holes.values().stream()
                .filter(HoleDefinition::complete)
                .filter(hole -> hole.number() > number)
                .findFirst();
    }
}
