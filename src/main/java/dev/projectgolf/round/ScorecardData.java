package dev.projectgolf.round;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Immutable round snapshot used by both the collectible scorecard item and persistent history.
 * Keeping this independent from live player NBT means a finished card never changes afterward.
 */
public record ScorecardData(
        UUID roundId,
        UUID playerId,
        String playerName,
        String course,
        String courseAuthor,
        String courseDescription,
        String courseDifficulty,
        String courseLocation,
        long startedAt,
        long endedAt,
        String startWeather,
        String endWeather,
        String startTimeOfDay,
        String endTimeOfDay,
        boolean completed,
        String finishReason,
        List<String> participants,
        List<RoundHoleScore> holes
) {
    public ScorecardData {
        roundId = roundId == null ? UUID.randomUUID() : roundId;
        participants = participants == null ? List.of() : List.copyOf(participants);
        holes = holes == null ? List.of() : List.copyOf(holes);
        playerName = safe(playerName);
        course = safe(course);
        courseAuthor = safe(courseAuthor);
        courseDescription = safe(courseDescription);
        courseDifficulty = safe(courseDifficulty);
        courseLocation = safe(courseLocation);
        startWeather = safe(startWeather);
        endWeather = safe(endWeather);
        startTimeOfDay = safe(startTimeOfDay);
        endTimeOfDay = safe(endTimeOfDay);
        finishReason = safe(finishReason);
    }

    public int totalStrokes() {
        return holes.stream().mapToInt(RoundHoleScore::strokes).sum();
    }

    public int totalPar() {
        return holes.stream().mapToInt(RoundHoleScore::par).sum();
    }

    public int relativeToPar() {
        return totalStrokes() - totalPar();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("RoundId", roundId);
        tag.putUUID("PlayerId", playerId);
        tag.putString("PlayerName", playerName);
        tag.putString("Course", course);
        tag.putString("CourseAuthor", courseAuthor);
        tag.putString("CourseDescription", courseDescription);
        tag.putString("CourseDifficulty", courseDifficulty);
        tag.putString("CourseLocation", courseLocation);
        tag.putLong("StartedAt", startedAt);
        tag.putLong("EndedAt", endedAt);
        tag.putString("StartWeather", startWeather);
        tag.putString("EndWeather", endWeather);
        tag.putString("StartTimeOfDay", startTimeOfDay);
        tag.putString("EndTimeOfDay", endTimeOfDay);
        tag.putBoolean("Completed", completed);
        tag.putString("FinishReason", finishReason);

        ListTag players = new ListTag();
        for (String participant : participants) players.add(StringTag.valueOf(participant));
        tag.put("Participants", players);

        ListTag scoreList = new ListTag();
        for (RoundHoleScore score : holes) scoreList.add(score.save());
        tag.put("Holes", scoreList);
        return tag;
    }

    public static ScorecardData load(CompoundTag tag) {
        List<String> participants = new ArrayList<>();
        ListTag players = tag.getList("Participants", Tag.TAG_STRING);
        for (int i = 0; i < players.size(); i++) participants.add(players.getString(i));

        List<RoundHoleScore> holes = new ArrayList<>();
        ListTag scores = tag.getList("Holes", Tag.TAG_COMPOUND);
        for (int i = 0; i < scores.size(); i++) holes.add(RoundHoleScore.load(scores.getCompound(i)));

        return new ScorecardData(
                tag.hasUUID("RoundId") ? tag.getUUID("RoundId") : UUID.randomUUID(),
                tag.hasUUID("PlayerId") ? tag.getUUID("PlayerId") : new UUID(0L, 0L),
                tag.getString("PlayerName"),
                tag.getString("Course"),
                tag.getString("CourseAuthor"),
                tag.getString("CourseDescription"),
                tag.getString("CourseDifficulty"),
                tag.getString("CourseLocation"),
                tag.getLong("StartedAt"),
                tag.getLong("EndedAt"),
                tag.getString("StartWeather"),
                tag.getString("EndWeather"),
                tag.getString("StartTimeOfDay"),
                tag.getString("EndTimeOfDay"),
                tag.getBoolean("Completed"),
                tag.contains("FinishReason") ? tag.getString("FinishReason") : (tag.getBoolean("Completed") ? "COMPLETED" : "EXITED"),
                participants,
                holes);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
