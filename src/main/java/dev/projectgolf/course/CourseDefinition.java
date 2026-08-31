package dev.projectgolf.course;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

public final class CourseDefinition {
    private final String name;
    private final Map<Integer, HoleDefinition> holes = new TreeMap<>();
    private String author = "";
    private String description = "";
    private String difficulty = "";
    private String location = "";

    public CourseDefinition(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public String author() {
        return author;
    }

    public String description() {
        return description;
    }

    public String difficulty() {
        return difficulty;
    }

    public String location() {
        return location;
    }

    public void setAuthor(String author) {
        this.author = clean(author);
    }

    public void setDescription(String description) {
        this.description = clean(description);
    }

    public void setDifficulty(String difficulty) {
        this.difficulty = clean(difficulty);
    }

    public void setLocation(String location) {
        this.location = clean(location);
    }

    public int totalPar() {
        return holes.values().stream().filter(HoleDefinition::complete).mapToInt(HoleDefinition::par).sum();
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

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
