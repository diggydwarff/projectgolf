package dev.projectgolf.golf;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Lightweight deterministic golf wind.
 *
 * This is intentionally not a weather simulator. The same world-time/dimension function runs on
 * both logical sides, which keeps the ball, client trajectory preview, HUD and tactical wisps in
 * agreement without a per-tick network packet. A future Immersive Winds adapter can replace this
 * provider while leaving the golf mechanics and presentation code unchanged.
 */
public final class GolfWind {
    private GolfWind() {}

    /** One full broad direction drift is deliberately slow so consecutive shots feel related. */
    private static final double DIRECTION_CYCLE_TICKS = 20.0 * 60.0 * 24.0;

    public static WindSample sample(Level level) {
        int dimensionHash = level.dimension().location().toString().hashCode();
        return sample(level.getGameTime(), dimensionHash, level.isRaining(), level.isThundering());
    }

    static WindSample sample(long gameTime, int dimensionHash, boolean raining, boolean thundering) {
        double dimensionPhase = ((dimensionHash & 0x7fffffff) % 10000) / 10000.0 * Math.PI * 2.0;
        double slow = gameTime / DIRECTION_CYCLE_TICKS * Math.PI * 2.0;
        double direction = dimensionPhase
                + slow
                + 0.42 * Math.sin(gameTime / 2300.0 + dimensionPhase * 0.7)
                + 0.18 * Math.sin(gameTime / 710.0 + dimensionPhase * 1.9);

        // Keep normal play breezy rather than permanently calm. Weather gently strengthens the
        // same wind instead of changing its direction or creating a separate weather model.
        double strength = 0.18
                + 0.48 * (0.5 + 0.5 * Math.sin(gameTime / 1800.0 + dimensionPhase * 1.3))
                + 0.18 * (0.5 + 0.5 * Math.sin(gameTime / 530.0 + dimensionPhase * 2.1));
        if (raining) strength *= 1.12;
        if (thundering) strength *= 1.18;
        strength = Math.max(0.08, Math.min(1.0, strength));

        Vec3 directionVector = new Vec3(Math.cos(direction), 0.0, Math.sin(direction));
        return new WindSample(directionVector, strength);
    }

    /** Horizontal acceleration added to an airborne golf ball each tick. */
    public static Vec3 acceleration(Level level) {
        WindSample wind = sample(level);
        return wind.direction().scale(GolfTuning.WIND_MAX_ACCELERATION * wind.strength());
    }

    public static String hudText(Level level) {
        WindSample wind = sample(level);
        int strength = (int) Math.round(wind.strength() * 10.0);
        return "Wind -> " + compass(wind.direction()) + " " + strength + "/10";
    }

    /** Compass direction the air is moving toward, matching the ball deflection and wisps. */
    public static String compass(Vec3 direction) {
        double angle = Math.atan2(direction.z, direction.x);
        // Minecraft: -Z north, +X east. Convert mathematical +X angle into compass sectors.
        double degrees = Math.toDegrees(angle);
        double compassDegrees = (90.0 + degrees + 360.0) % 360.0;
        String[] names = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        int index = (int) Math.floor((compassDegrees + 22.5) / 45.0) & 7;
        return names[index];
    }

    public record WindSample(Vec3 direction, double strength) {
        public WindSample {
            if (direction == null || !GolfPhysics.finite(direction) || direction.horizontalDistanceSqr() < 1.0e-12) {
                direction = new Vec3(1.0, 0.0, 0.0);
            } else {
                direction = new Vec3(direction.x, 0.0, direction.z).normalize();
            }
            strength = Double.isFinite(strength) ? Math.max(0.0, Math.min(1.0, strength)) : 0.0;
        }
    }
}
