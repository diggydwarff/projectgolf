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
        level.playSound(null, position.x, position.y, position.z,
                SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.9f, 1.35f);
    }

    /**
     * Owner-only, long-distance landing beacon. Re-emitted a few times by the ball so it remains
     * obvious long enough to reacquire after a drive without creating a persistent marker entity.
     */
    public static void landingMarker(ServerPlayer player, Vec3 position) {
        if (!(player.level() instanceof ServerLevel level)) return;

        // A clean, tall owner-only pin is far easier to reacquire after a long drive than a
        // particle cloud. Five fixed points stay legible without covering the landing area.
        for (int i = 0; i < 5; i++) {
            level.sendParticles(player, WHITE_DUST, true,
                    position.x, position.y + 0.22 + i * 0.55, position.z,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
        level.sendParticles(player, GOLD_DUST, true,
                position.x, position.y + 2.98, position.z,
                1, 0.0, 0.0, 0.0, 0.0);
    }
}
