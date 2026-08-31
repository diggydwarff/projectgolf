package dev.projectgolf.visual;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** Server-authoritative gameplay celebrations/markers. No particles are entities. */
public final class GolfVisualEffects {
    private GolfVisualEffects() {}

    public static final DustParticleOptions WHITE_DUST =
            new DustParticleOptions(new Vector3f(1.0f, 1.0f, 1.0f), 1.35f);
    public static final DustParticleOptions GOLD_DUST =
            new DustParticleOptions(new Vector3f(1.0f, 0.72f, 0.08f), 1.45f);
    // Smaller, cooler guide dots are intentionally distinct from celebration/locator particles.
    // They are placed deterministically along the predicted polyline, never scattered randomly.
    public static final DustParticleOptions GUIDE_DUST =
            new DustParticleOptions(new Vector3f(0.88f, 0.96f, 1.0f), 0.36f);
    public static final DustParticleOptions RED_DUST =
            new DustParticleOptions(new Vector3f(1.0f, 0.16f, 0.12f), 1.55f);
    public static final DustParticleOptions BLUE_DUST =
            new DustParticleOptions(new Vector3f(0.18f, 0.48f, 1.0f), 1.55f);
    public static final DustParticleOptions GREEN_DUST =
            new DustParticleOptions(new Vector3f(0.18f, 1.0f, 0.32f), 1.55f);
    public static final DustParticleOptions PURPLE_DUST =
            new DustParticleOptions(new Vector3f(0.78f, 0.28f, 1.0f), 1.55f);
    public static final DustParticleOptions BALL_DUST =
            new DustParticleOptions(new Vector3f(0.20f, 0.88f, 1.0f), 1.55f);
    public static final DustParticleOptions TEE_DUST =
            new DustParticleOptions(new Vector3f(0.45f, 1.0f, 0.30f), 1.45f);
    public static final DustParticleOptions WIND_DUST =
            new DustParticleOptions(new Vector3f(0.90f, 0.96f, 1.0f), 0.72f);

    public static void perfectSwing(ServerLevel level, Vec3 position) {
        level.sendParticles(GOLD_DUST,
                position.x, position.y + 0.22, position.z,
                5, 0.18, 0.10, 0.18, 0.012);
        level.sendParticles(ParticleTypes.END_ROD,
                position.x, position.y + 0.25, position.z,
                2, 0.10, 0.08, 0.10, 0.012);
        level.playSound(null, position.x, position.y, position.z,
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.75f, 1.35f);
    }

    public static void clubImpact(ServerLevel level, Vec3 position, dev.projectgolf.golf.ClubType club) {
        float volume = club == dev.projectgolf.golf.ClubType.DRIVER ? 0.95f
                : club == dev.projectgolf.golf.ClubType.WOOD ? 0.85f : 0.70f;
        float pitch = switch (club) {
            case DRIVER -> 0.82f;
            case WOOD -> 0.92f;
            case IRON -> 1.02f;
            case WEDGE -> 1.12f;
            case PUTTER -> 1.28f;
        };
        level.playSound(null, position.x, position.y, position.z,
                SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, volume, pitch);
    }

    public static void holed(ServerLevel level, Vec3 position) {
        holed(level, position, 0, 0);
    }

    /**
     * Scaled cup celebration. A hole-in-one is deliberately spectacular; eagle/birdie/par step
     * down through progressively calmer bursts, while bogey-or-worse keeps a small satisfying cup
     * sparkle instead of shaming casual players. Particles are broadcast around the cup so friends
     * watching the shot see the result too.
     */
    public static void holed(ServerLevel level, Vec3 position, int strokes, int par) {
        CelebrationTier tier = celebrationTier(strokes, par);

        // Every made putt gets the unmistakable cup sound.
        level.playSound(null, position.x, position.y, position.z,
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.9f, 1.35f);

        switch (tier) {
            case ACE -> {
                // Hole-in-one is intentionally in a class of its own: a vertical launch from the
                // cup followed by several colorful firework-like bursts. No other score gets the
                // rainbow treatment.
                level.sendParticles(ParticleTypes.FIREWORK, position.x, position.y + 0.45, position.z,
                        42, 0.42, 1.55, 0.42, 0.085);
                colorfulBurst(level, position.add(0.0, 2.2, 0.0), RED_DUST);
                colorfulBurst(level, position.add(0.65, 3.05, 0.30), BLUE_DUST);
                colorfulBurst(level, position.add(-0.55, 3.75, -0.35), GREEN_DUST);
                colorfulBurst(level, position.add(0.15, 4.55, -0.55), PURPLE_DUST);
                level.sendParticles(GOLD_DUST, position.x, position.y + 1.15, position.z,
                        66, 1.10, 1.35, 1.10, 0.085);
                level.sendParticles(ParticleTypes.END_ROD, position.x, position.y + 1.4, position.z,
                        30, 1.0, 1.1, 1.0, 0.065);
                level.playSound(null, position.x, position.y, position.z,
                        SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.0f, 1.05f);
                level.playSound(null, position.x, position.y + 2.5, position.z,
                        SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 1.0f, 1.0f);
                level.playSound(null, position.x, position.y + 3.5, position.z,
                        SoundEvents.FIREWORK_ROCKET_TWINKLE, SoundSource.PLAYERS, 0.95f, 1.18f);
                level.playSound(null, position.x, position.y, position.z,
                        SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.20f);
            }
            case EAGLE_OR_BETTER -> {
                level.sendParticles(GOLD_DUST, position.x, position.y + 0.75, position.z,
                        54, 0.95, 0.90, 0.95, 0.072);
                level.sendParticles(WHITE_DUST, position.x, position.y + 1.05, position.z,
                        28, 0.90, 0.80, 0.90, 0.045);
                level.sendParticles(ParticleTypes.END_ROD, position.x, position.y + 0.95, position.z,
                        18, 0.72, 0.72, 0.72, 0.050);
                level.playSound(null, position.x, position.y, position.z,
                        SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.85f, 1.30f);
            }
            case BIRDIE -> {
                // Roughly the old alpha.11.1 ace-scale gold celebration: special and obvious, but
                // the true hole-in-one now towers above it with fireworks.
                level.sendParticles(GOLD_DUST, position.x, position.y + 0.65, position.z,
                        44, 0.88, 0.72, 0.88, 0.065);
                level.sendParticles(WHITE_DUST, position.x, position.y + 1.00, position.z,
                        24, 0.90, 0.72, 0.90, 0.040);
                level.sendParticles(ParticleTypes.END_ROD, position.x, position.y + 0.82, position.z,
                        14, 0.62, 0.62, 0.62, 0.045);
                level.playSound(null, position.x, position.y, position.z,
                        SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.85f, 1.46f);
            }
            case PAR -> {
                level.sendParticles(GOLD_DUST, position.x, position.y + 0.45, position.z,
                        14, 0.42, 0.32, 0.42, 0.035);
                level.sendParticles(WHITE_DUST, position.x, position.y + 0.55, position.z,
                        8, 0.32, 0.26, 0.32, 0.022);
                level.playSound(null, position.x, position.y, position.z,
                        SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.52f, 1.28f);
            }
            case BOGEY_OR_WORSE, PRACTICE -> {
                level.sendParticles(WHITE_DUST, position.x, position.y + 0.35, position.z,
                        6, 0.24, 0.18, 0.24, 0.018);
            }
        }
    }

    private static void colorfulBurst(ServerLevel level, Vec3 center, DustParticleOptions color) {
        level.sendParticles(color, center.x, center.y, center.z,
                28, 0.72, 0.58, 0.72, 0.085);
        level.sendParticles(ParticleTypes.FIREWORK, center.x, center.y, center.z,
                12, 0.62, 0.48, 0.62, 0.055);
    }

    public static CelebrationTier celebrationTier(int strokes, int par) {
        if (strokes == 1) return CelebrationTier.ACE;
        if (strokes <= 0 || par <= 0) return CelebrationTier.PRACTICE;
        int relative = strokes - par;
        if (relative <= -2) return CelebrationTier.EAGLE_OR_BETTER;
        if (relative == -1) return CelebrationTier.BIRDIE;
        if (relative == 0) return CelebrationTier.PAR;
        return CelebrationTier.BOGEY_OR_WORSE;
    }

    public enum CelebrationTier {
        ACE,
        EAGLE_OR_BETTER,
        BIRDIE,
        PAR,
        BOGEY_OR_WORSE,
        PRACTICE
    }

    /**
     * Owner-only, long-distance landing beacon. It starts tall and obvious, then becomes shorter
     * and sparser as the remaining lifetime falls. This gives a drive a real reacquisition beacon
     * without filling the player's view with a particle cloud.
     */
    public static void landingMarker(ServerPlayer player, Vec3 position, float strength) {
        if (!(player.level() instanceof ServerLevel level)) return;

        float clamped = Math.max(0.0f, Math.min(1.0f, strength));
        int points = clamped > 0.66f ? 7 : clamped > 0.33f ? 5 : 3;
        double height = 3.5 + 6.5 * clamped;

        for (int i = 0; i < points; i++) {
            double t = points <= 1 ? 0.0 : i / (double) (points - 1);
            level.sendParticles(player, WHITE_DUST, true,
                    position.x, position.y + 0.28 + height * t, position.z,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
        level.sendParticles(player, GOLD_DUST, true,
                position.x, position.y + 0.28 + height, position.z,
                1, 0.0, 0.0, 0.0, 0.0);
        if (clamped > 0.70f) {
            level.sendParticles(player, ParticleTypes.END_ROD, true,
                    position.x, position.y + 0.40 + height, position.z,
                    1, 0.0, 0.02, 0.0, 0.0);
        }
    }
}
