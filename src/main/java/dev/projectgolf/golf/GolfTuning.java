package dev.projectgolf.golf;

/**
 * Central mechanics tuning. Keep gameplay numbers here while the first pass is being tuned.
 * This deliberately avoids scattering magic constants across entity, input, and course code.
 */
public final class GolfTuning {
    private GolfTuning() {}

    public static final double BALL_GRAVITY = 0.035;
    public static final double AIR_HORIZONTAL_DRAG = 0.9975;
    // Tactical golf wind: enough to visibly move a long shot without turning every breeze into a
    // random-number penalty. Strength 1.0 adds this much horizontal velocity per airborne tick.
    public static final double WIND_MAX_ACCELERATION = 0.0022;
    public static final int WIND_WISP_BASE_INTERVAL_TICKS = 20;
    public static final double MAX_BALL_SPEED = 4.25;
    public static final double STOP_HORIZONTAL_SPEED = 0.012;
    // A tiny fixed rolling resistance removes the long low-speed "ice glide" tail on greens
    // without destroying the long, smooth roll that makes putting greens useful.
    public static final double GREEN_ROLLING_RESISTANCE = 0.00055;
    public static final int STOP_SETTLE_TICKS = 8;
    public static final int MAX_MOVING_TICKS = 20 * 35;
    public static final int STATIONARY_RECHECK_TICKS = 20;
    public static final int OWNERSHIP_RECHECK_TICKS = 20 * 5;

    public static final double CUP_CAPTURE_RADIUS = 0.31;
    public static final double CUP_CAPTURE_MAX_HORIZONTAL_SPEED = 0.115;
    public static final int WATER_RESET_DELAY_TICKS = 20;
    public static final int HOLED_BALL_DESPAWN_TICKS = 20 * 5;

    public static final double SLOPE_ACCELERATION = 0.0034;
    // Quarter-layer putting greens rise 0.25 blocks at a time. This clears one golf layer while
    // remaining below a normal half-slab/full terrain step.
    public static final float PUTTING_GREEN_LAYER_HEIGHT = 0.25f;
    public static final float BALL_MAX_UP_STEP = 0.26f;

    public static final int POWER_SWEEP_TICKS = 30;
    public static final int ACCURACY_SWEEP_TICKS = 20;
    public static final double PERFECT_ACCURACY_WINDOW = 0.08;

    public static final double SWING_BALL_RADIUS = 5.0;
    public static final int SWING_COOLDOWN_TICKS = 5;
    public static final int PERFECT_FLASH_TICKS = 24;

    public static final int PREVIEW_POINTS = 32;
    public static final double PREVIEW_STEP_TICKS = 1.0;
    // Putting needs a longer rolling simulation than airborne clubs. Only every few simulated
    // ticks becomes a visible preview point, keeping the marker useful without creating a carpet
    // of particles across the green.
    public static final int PUTTER_PREVIEW_MAX_TICKS = 180;
    public static final int PUTTER_PREVIEW_POINT_INTERVAL_TICKS = 4;
    // Stable guide: refresh the same fixed sample positions before their previous particles fade.
    // Unlike the old staggered groups, nothing travels/pulses down the line from frame to frame.
    public static final int PREVIEW_PARTICLE_INTERVAL_TICKS = 3;
    public static final int PREVIEW_PARTICLE_STRIDE = 5;
    public static final int PUTTER_PREVIEW_PARTICLE_STRIDE = 8;
    // Preview particles are resampled along the whole predicted polyline. These caps keep the
    // guide continuous and readable without rebuilding the old particle cloud.
    public static final int PREVIEW_GUIDE_MAX_PARTICLES = 34;
    public static final int PUTTER_GUIDE_MAX_PARTICLES = 30;
    public static final double PREVIEW_GUIDE_MIN_SPACING = 1.10;
    public static final double PUTTER_GUIDE_MIN_SPACING = 0.75;

    public static final int BALL_TRAIL_INTERVAL_TICKS = 6;
    public static final int LOW_SPEED_BALL_TRAIL_INTERVAL_TICKS = 12;
    public static final int BALL_TRAIL_ACCENT_INTERVAL_TICKS = 24;
    public static final double LOW_SPEED_TRAIL_THRESHOLD = 1.0;
    public static final int LANDING_MARKER_SHORT_TICKS = 20 * 5;
    public static final int LANDING_MARKER_MEDIUM_TICKS = 20 * 8;
    public static final int LANDING_MARKER_LONG_TICKS = 20 * 12;
    // Compatibility alias retained for development validators/tools from earlier alphas.
    public static final int LANDING_MARKER_TICKS = LANDING_MARKER_LONG_TICKS;
    public static final int LANDING_MARKER_INTERVAL_TICKS = 20;
    public static final double LANDING_MARKER_MEDIUM_DISTANCE = 30.0;
    public static final double LANDING_MARKER_LONG_DISTANCE = 80.0;
    public static final double BALL_LOCATOR_MIN_DISTANCE = 14.0;

    // Shift + mouse wheel adjusts how much of the selected club's full power is available
    // at the top of the timing meter. This is the Mario-Golf-style planned carry control.
    public static final float TARGET_POWER_MIN = 0.10f;
    public static final float TARGET_POWER_STEP = 0.05f;
    public static final float TARGET_POWER_FINE_STEP = 0.01f;

    public static int landingMarkerDurationTicks(double shotDistance) {
        if (shotDistance >= LANDING_MARKER_LONG_DISTANCE) return LANDING_MARKER_LONG_TICKS;
        if (shotDistance >= LANDING_MARKER_MEDIUM_DISTANCE) return LANDING_MARKER_MEDIUM_TICKS;
        return LANDING_MARKER_SHORT_TICKS;
    }
}
