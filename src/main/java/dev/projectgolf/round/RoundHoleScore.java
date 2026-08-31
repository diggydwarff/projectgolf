package dev.projectgolf.round;

import net.minecraft.nbt.CompoundTag;

/** Immutable score for one hole in a played round. */
public record RoundHoleScore(int hole, String name, int par, int strokes, int penalties, boolean completed) {
    public int relativeToPar() {
        return strokes - par;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("Hole", hole);
        tag.putString("Name", name == null ? "" : name);
        tag.putInt("Par", par);
        tag.putInt("Strokes", strokes);
        tag.putInt("Penalties", penalties);
        tag.putBoolean("Completed", completed);
        return tag;
    }

    public static RoundHoleScore load(CompoundTag tag) {
        return new RoundHoleScore(
                tag.getInt("Hole"),
                tag.getString("Name"),
                tag.getInt("Par"),
                tag.getInt("Strokes"),
                tag.getInt("Penalties"),
                !tag.contains("Completed") || tag.getBoolean("Completed"));
    }
}
