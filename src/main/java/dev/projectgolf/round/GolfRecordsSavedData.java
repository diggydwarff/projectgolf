package dev.projectgolf.round;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Persistent server-wide round history. Scorecard items are souvenirs; this is the authoritative archive. */
public final class GolfRecordsSavedData extends SavedData {
    private static final String DATA_NAME = "projectgolf_records";
    private static final int MAX_ARCHIVED_ROUNDS = 4096;
    private final List<ScorecardData> rounds = new ArrayList<>();

    public static GolfRecordsSavedData create() {
        return new GolfRecordsSavedData();
    }

    public static GolfRecordsSavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        GolfRecordsSavedData data = create();
        ListTag list = tag.getList("Rounds", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) data.rounds.add(ScorecardData.load(list.getCompound(i)));
        return data;
    }

    public static GolfRecordsSavedData get(MinecraftServer server) {
        SavedData.Factory<GolfRecordsSavedData> factory =
                new SavedData.Factory<>(GolfRecordsSavedData::create, GolfRecordsSavedData::load);
        return server.overworld().getDataStorage().computeIfAbsent(factory, DATA_NAME);
    }

    public void record(ScorecardData round) {
        if (round == null || round.holes().isEmpty()) return;
        rounds.removeIf(existing -> existing.roundId().equals(round.roundId()));
        rounds.add(round);
        while (rounds.size() > MAX_ARCHIVED_ROUNDS) rounds.remove(0);
        setDirty();
    }

    public List<ScorecardData> forPlayer(UUID playerId) {
        return rounds.stream()
                .filter(round -> round.playerId().equals(playerId))
                .sorted(Comparator.comparingLong(ScorecardData::endedAt).reversed())
                .toList();
    }

    public List<ScorecardData> forCourse(String course) {
        return rounds.stream()
                .filter(round -> round.course().equalsIgnoreCase(course))
                .sorted(Comparator.comparingLong(ScorecardData::endedAt).reversed())
                .toList();
    }

    public List<ScorecardData> completedLeaderboard(String course) {
        return rounds.stream()
                .filter(ScorecardData::completed)
                .filter(round -> round.course().equalsIgnoreCase(course))
                .sorted(Comparator.comparingInt(ScorecardData::relativeToPar)
                        .thenComparingInt(ScorecardData::totalStrokes)
                        .thenComparingLong(ScorecardData::endedAt))
                .toList();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (ScorecardData round : rounds) list.add(round.save());
        tag.put("Rounds", list);
        return tag;
    }
}
