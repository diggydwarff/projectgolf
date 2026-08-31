package dev.projectgolf.entity;

import dev.projectgolf.block.GolfSlopeBlock;
import dev.projectgolf.golf.ClubType;
import dev.projectgolf.golf.GolfPhysics;
import dev.projectgolf.golf.GolfSurface;
import dev.projectgolf.golf.GolfTuning;
import dev.projectgolf.golf.SwingMath;
import dev.projectgolf.item.GolfDebugWandItem;
import dev.projectgolf.registry.GolfBlocks;
import dev.projectgolf.registry.GolfItems;
import dev.projectgolf.round.GolfRoundManager;
import dev.projectgolf.visual.GolfVisualEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import dev.projectgolf.network.ShotSummaryPayload;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class GolfBallEntity extends ThrowableItemProjectile {
    private static final EntityDataAccessor<java.util.Optional<UUID>> DATA_GOLF_OWNER =
            SynchedEntityData.defineId(GolfBallEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> DATA_IN_HOLE =
            SynchedEntityData.defineId(GolfBallEntity.class, EntityDataSerializers.BOOLEAN);

    private static final String NBT_GOLF_OWNER = "GolfOwner";
    private static final String NBT_SAFE_X = "SafeX";
    private static final String NBT_SAFE_Y = "SafeY";
    private static final String NBT_SAFE_Z = "SafeZ";
    private static final String NBT_HAS_SAFE = "HasSafe";
    private static final String NBT_IN_HOLE = "InHole";

    private @Nullable Vec3 lastSafePosition;
    private int movingTicks;
    private int settlingTicks;
    private int waterTicks;
    private int inHoleTicks;
    private int ownershipCheckTicks;
    private int landingMarkerTicks;
    private boolean sleeping;

    // Shot telemetry is deliberately runtime-only. A restart does not invent partial telemetry.
    private boolean shotActive;
    private boolean shotWasAirborne;
    private double shotDistance;
    private double shotCarryDistance = -1.0;
    private double shotStartY;
    private double shotMaxY;
    private int shotTicks;
    private boolean shotStopReported;
    private @Nullable ClubType shotClub;
    private float shotPower;
    private float shotAccuracy;
    private Vec3 previousShotPosition = Vec3.ZERO;

    public GolfBallEntity(EntityType<? extends GolfBallEntity> type, Level level) {
        super(type, level);
        // Competitive balls should not be destroyed by incidental combat/explosions.
        // Admin cleanup remains available through /golfdebug cleanup.
        setInvulnerable(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_GOLF_OWNER, java.util.Optional.empty());
        builder.define(DATA_IN_HOLE, false);
    }

    @Override
    protected Item getDefaultItem() {
        return GolfItems.GOLF_BALL.get();
    }

    @Override
    protected double getDefaultGravity() {
        return GolfTuning.BALL_GRAVITY;
    }

    @Override
    public float maxUpStep() {
        // Course ramps use 1/8-block collision slices. Allow those to be traversed while
        // remaining too low to auto-climb normal half-slabs or full terrain steps.
        return GolfTuning.BALL_MAX_UP_STEP;
    }

    public void setGolfOwner(@Nullable UUID owner) {
        entityData.set(DATA_GOLF_OWNER, java.util.Optional.ofNullable(owner));
    }

    public @Nullable UUID getGolfOwner() {
        return entityData.get(DATA_GOLF_OWNER).orElse(null);
    }

    public boolean isGolfOwner(UUID owner) {
        return owner.equals(getGolfOwner());
    }

    public void setLastSafePosition(Vec3 position) {
        this.lastSafePosition = position;
    }

    public @Nullable Vec3 getLastSafePosition() {
        return lastSafePosition;
    }

    public boolean isInHole() {
        return entityData.get(DATA_IN_HOLE);
    }

    private void setInHole(boolean value) {
        entityData.set(DATA_IN_HOLE, value);
    }

    public GolfSurface currentLie() {
        BlockState current = level().getBlockState(blockPosition());
        GolfSurface surface = GolfSurface.from(current);
        return surface != GolfSurface.DEFAULT
                ? surface
                : GolfSurface.from(level().getBlockState(blockPosition().below()));
    }

    public boolean isStationary() {
        return sleeping || (getDeltaMovement().lengthSqr() < GolfTuning.STOP_HORIZONTAL_SPEED * GolfTuning.STOP_HORIZONTAL_SPEED
                && onGround());
    }

    public boolean launchFromClub(ServerPlayer player, ClubType club, float power, float accuracy) {
        if (isInHole() || !isStationary()) return false;

        GolfSurface lie = currentLie();
        Vec3 launch = SwingMath.launchVector(player.getYRot(), club, lie, power, accuracy);
        launch = GolfPhysics.clampSpeed(launch);
        if (!GolfPhysics.finite(launch)) return false;

        setGolfOwner(player.getUUID());
        GolfRoundManager.setActiveBall(player, this);
        setDeltaMovement(launch);
        hasImpulse = true;
        movingTicks = 0;
        settlingTicks = 0;
        waterTicks = 0;
        sleeping = false;
        resetShotTelemetry();
        shotClub = club;
        shotPower = power;
        shotAccuracy = accuracy;

        GolfRoundManager.recordStroke(player);
        if (level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            GolfVisualEffects.clubImpact(serverLevel, position(), club);
            if (Math.abs(accuracy) <= GolfTuning.PERFECT_ACCURACY_WINDOW) {
                GolfVisualEffects.perfectSwing(serverLevel, position());
            }
        }
        return true;
    }

    public void debugLaunch(Vec3 velocity) {
        Vec3 v = GolfPhysics.clampSpeed(velocity);
        setDeltaMovement(v);
        hasImpulse = true;
        movingTicks = 0;
        settlingTicks = 0;
        waterTicks = 0;
        sleeping = false;
        resetShotTelemetry();
        shotClub = null;
        shotPower = 0.0f;
        shotAccuracy = 0.0f;
    }

    @Override
    public void tick() {
        // Reject corrupted motion before any collision/world logic sees it.
        if (!GolfPhysics.finite(position()) || !GolfPhysics.finite(getDeltaMovement())) {
            if (!level().isClientSide) discard();
            else setDeltaMovement(Vec3.ZERO);
            return;
        }

        // We intentionally own movement instead of delegating to ThrowableProjectile.tick().
        // Golf needs block-resolved rolling and onGround state; normal thrown projectiles raycast
        // through a tick and are optimized to impact/discard rather than roll over terrain.
        baseTick();

        if (!level().isClientSide && !validateActiveOwnership()) return;

        if (isInHole()) {
            setDeltaMovement(Vec3.ZERO);
            if (!level().isClientSide && ++inHoleTicks >= GolfTuning.HOLED_BALL_DESPAWN_TICKS) {
                ServerPlayer owner = ownerPlayer();
                if (owner != null) GolfRoundManager.clearActiveBall(owner, getUUID());
                discard();
            }
            return;
        }

        if (!level().isClientSide && landingMarkerTicks > 0) {
            if (landingMarkerTicks % GolfTuning.LANDING_MARKER_INTERVAL_TICKS == 0) {
                ServerPlayer markerOwner = ownerPlayer();
                if (markerOwner != null) GolfVisualEffects.landingMarker(markerOwner, position());
            }
            landingMarkerTicks--;
        }

        // Flat resting balls are the common case. Avoid running gravity, Entity.move collision
        // resolution and multiple block/tag lookups 20 times/second while nothing is happening.
        // Once per second run one normal physics tick so a removed support block still wakes/falls.
        if (sleeping && tickCount % GolfTuning.STATIONARY_RECHECK_TICKS != 0) {
            setDeltaMovement(Vec3.ZERO);
            return;
        }
        if (sleeping) sleeping = false;

        boolean stoppedThisTick = false;
        Vec3 requested = GolfPhysics.requestedVelocity(getDeltaMovement(), isNoGravity());

        Vec3 beforeMove = position();
        move(MoverType.SELF, requested);
        Vec3 actualMove = position().subtract(beforeMove);

        boolean collidedX = Math.abs(actualMove.x - requested.x) > 1.0e-5;
        boolean collidedY = Math.abs(actualMove.y - requested.y) > 1.0e-5;
        boolean collidedZ = Math.abs(actualMove.z - requested.z) > 1.0e-5;

        GolfSurface surface = currentLie();
        Vec3 velocity = requested;

        // Horizontal walls lose most energy. Ground impacts use the lie's restitution.
        if (collidedX) velocity = GolfPhysics.wallBounceX(velocity);
        if (collidedZ) velocity = GolfPhysics.wallBounceZ(velocity);
        if (collidedY) velocity = GolfPhysics.verticalCollision(velocity, surface);

        Direction downhill = null;
        if (onGround() && Math.abs(velocity.y) < 0.08) {
            downhill = downhillDirection();
            velocity = GolfPhysics.grounded(velocity, surface, downhill);
        } else {
            velocity = GolfPhysics.airborne(velocity);
        }

        if (GolfPhysics.shouldSettle(velocity, onGround())) {
            settlingTicks++;
            if (settlingTicks >= GolfTuning.STOP_SETTLE_TICKS) {
                velocity = Vec3.ZERO;
                movingTicks = 0;
                stoppedThisTick = shotActive && !shotStopReported && shotDistance > 0.001;
                if (!level().isClientSide && !isInWater() && !isOutOfBoundsSurface()) {
                    lastSafePosition = position();
                }
                // Never sleep on a known slope: repeated downhill acceleration is gameplay.
                sleeping = downhill == null;
            }
        } else {
            settlingTicks = 0;
            if (velocity.lengthSqr() > 0.0001) movingTicks++;
        }

        setDeltaMovement(GolfPhysics.clampSpeed(velocity));

        if (!level().isClientSide) {
            updateShotTelemetry();

            // Resolve gameplay outcomes before reporting a normal stop; hazards/cups override it.
            if (tryCaptureCup()) return;
            if (handleOutOfBounds()) return;
            if (handleWaterHazard()) return;
            if (stoppedThisTick) reportShotStopped();

            if (movingTicks > GolfTuning.MAX_MOVING_TICKS) {
                setDeltaMovement(Vec3.ZERO);
                movingTicks = 0;
            }

            if (!GolfPhysics.finite(position()) || !GolfPhysics.finite(getDeltaMovement())) {
                discard();
            }
        } else if (getDeltaMovement().lengthSqr() > 0.0025) {
            double speed = GolfPhysics.horizontalSpeed(getDeltaMovement());
            int trailInterval = speed < GolfTuning.LOW_SPEED_TRAIL_THRESHOLD
                    ? GolfTuning.LOW_SPEED_BALL_TRAIL_INTERVAL_TICKS
                    : GolfTuning.BALL_TRAIL_INTERVAL_TICKS;
            if (tickCount % trailInterval == 0) {
                Vec3 p = position();
                level().addAlwaysVisibleParticle(
                        GolfVisualEffects.WHITE_DUST, true, p.x, p.y + 0.06, p.z, 0, 0, 0);
                // Low-speed/putting shots intentionally have no bright accent particles.
                if (speed >= GolfTuning.LOW_SPEED_TRAIL_THRESHOLD
                        && tickCount % GolfTuning.BALL_TRAIL_ACCENT_INTERVAL_TICKS == 0) {
                    level().addAlwaysVisibleParticle(
                            ParticleTypes.END_ROD, true, p.x, p.y + 0.06, p.z, 0, 0, 0);
                }
            }
        }
    }

    private void resetShotTelemetry() {
        shotActive = true;
        shotWasAirborne = false;
        shotDistance = 0.0;
        shotCarryDistance = -1.0;
        shotStartY = getY();
        shotMaxY = getY();
        shotTicks = 0;
        shotStopReported = false;
        landingMarkerTicks = 0;
        previousShotPosition = position();
    }

    private void cancelShotTelemetry() {
        shotActive = false;
        shotWasAirborne = false;
        shotDistance = 0.0;
        shotCarryDistance = -1.0;
        shotTicks = 0;
        shotStopReported = true;
        landingMarkerTicks = 0;
        previousShotPosition = position();
    }

    private void updateShotTelemetry() {
        if (!shotActive) {
            previousShotPosition = position();
            return;
        }

        shotDistance += GolfPhysics.horizontalDistance(previousShotPosition, position());
        previousShotPosition = position();
        shotTicks++;
        shotMaxY = Math.max(shotMaxY, getY());

        if (!onGround()) {
            shotWasAirborne = true;
        } else if (shotWasAirborne && shotCarryDistance < 0.0) {
            shotCarryDistance = shotDistance;
        }
    }

    private void reportShotStopped() {
        shotStopReported = true;
        shotActive = false;
        if (shotCarryDistance < 0.0) shotCarryDistance = 0.0;
        double roll = Math.max(0.0, shotDistance - shotCarryDistance);
        ServerPlayer owner = ownerPlayer();
        if (owner != null) {
            GolfVisualEffects.landingMarker(owner, position());
            // The immediate marker counts as the first pulse; avoid re-emitting again one tick later.
            landingMarkerTicks = Math.max(0, GolfTuning.LANDING_MARKER_TICKS - 1);
            PacketDistributor.sendToPlayer(owner, ShotSummaryPayload.forStoppedShot(
                    shotClub, shotPower, shotAccuracy, shotDistance, shotCarryDistance, roll,
                    currentLie(), GolfRoundManager.strokes(owner), false));
        }
    }

    private @Nullable Direction downhillDirection() {
        BlockState state = level().getBlockState(blockPosition());
        if (!(state.getBlock() instanceof GolfSlopeBlock)) {
            state = level().getBlockState(blockPosition().below());
        }
        if (state.getBlock() instanceof GolfSlopeBlock slope) {
            return slope.downhill(state);
        }
        return null;
    }

    private boolean isOutOfBoundsSurface() {
        BlockState current = level().getBlockState(blockPosition());
        BlockState below = level().getBlockState(blockPosition().below());
        return current.is(dev.projectgolf.golf.GolfTags.OUT_OF_BOUNDS)
                || below.is(dev.projectgolf.golf.GolfTags.OUT_OF_BOUNDS);
    }

    private boolean handleOutOfBounds() {
        if (!isOutOfBoundsSurface()) return false;
        resetToLastSafe("Out of bounds.");
        return true;
    }

    private void resetToLastSafe(String reason) {
        setDeltaMovement(Vec3.ZERO);
        ServerPlayer owner = ownerPlayer();
        if (owner != null) GolfRoundManager.addPenalty(owner, 1, reason);

        if (lastSafePosition == null) {
            cancelShotTelemetry();
            if (owner != null) {
                GolfRoundManager.clearActiveBall(owner, getUUID());
                owner.sendSystemMessage(Component.literal("Ball had no recorded safe lie and was removed; place a new ball."));
            }
            discard();
            return;
        }

        setPos(lastSafePosition.x, lastSafePosition.y + 0.15, lastSafePosition.z);
        waterTicks = 0;
        movingTicks = 0;
        settlingTicks = 0;
        sleeping = false;
        cancelShotTelemetry();
    }

    private boolean tryCaptureCup() {
        // Prevent a newly placed stationary ball from auto-holing without a stroke and avoid
        // scanning a 3x2x3 cup neighborhood forever after a normal shot has already stopped.
        if (!shotActive) return false;

        Vec3 velocity = getDeltaMovement();
        double horizontalSpeed = GolfPhysics.horizontalSpeed(velocity);
        if (horizontalSpeed > GolfTuning.CUP_CAPTURE_MAX_HORIZONTAL_SPEED) return false;

        UUID ownerId = getGolfOwner();
        ServerPlayer ownerForCup = ownerPlayer();
        if (ownerId != null && ownerForCup == null) return false;

        BlockPos center = blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-1, -1, -1), center.offset(1, 0, 1))) {
            if (!level().getBlockState(pos).is(GolfBlocks.GOLF_CUP.get())) continue;
            if (ownerForCup != null && !GolfRoundManager.isExpectedCup(ownerForCup, level().dimension(), pos)) continue;

            double cupX = pos.getX() + 0.5;
            double cupZ = pos.getZ() + 0.5;
            double dx = getX() - cupX;
            double dz = getZ() - cupZ;
            if (dx * dx + dz * dz <= GolfTuning.CUP_CAPTURE_RADIUS * GolfTuning.CUP_CAPTURE_RADIUS) {
                shotActive = false;
                landingMarkerTicks = 0;
                setInHole(true);
                inHoleTicks = 0;
                setPos(cupX, pos.getY() + 1.01, cupZ);
                setDeltaMovement(Vec3.ZERO);

                if (level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    GolfVisualEffects.holed(serverLevel, position());
                }

                if (ownerForCup != null) {
                    if (shotCarryDistance < 0.0) shotCarryDistance = 0.0;
                    double roll = Math.max(0.0, shotDistance - shotCarryDistance);
                    PacketDistributor.sendToPlayer(ownerForCup, ShotSummaryPayload.forStoppedShot(
                            shotClub, shotPower, shotAccuracy, shotDistance, shotCarryDistance, roll,
                            currentLie(), GolfRoundManager.strokes(ownerForCup), true));
                    GolfRoundManager.finishHole(ownerForCup);
                }
                return true;
            }
        }
        return false;
    }

    private boolean handleWaterHazard() {
        if (!isInWater()) {
            waterTicks = 0;
            return false;
        }

        waterTicks++;
        if (waterTicks < GolfTuning.WATER_RESET_DELAY_TICKS) return false;

        resetToLastSafe("Water hazard.");
        return true;
    }

    private boolean validateActiveOwnership() {
        UUID ownerId = getGolfOwner();
        if (ownerId == null) return true;

        // Avoid a player-list/NBT lookup every tick. New/loaded balls validate immediately, then
        // only every few seconds. This catches obsolete balls that were unreachable in an unloaded
        // chunk when the player placed a replacement elsewhere.
        if (ownershipCheckTicks++ > 0 && ownershipCheckTicks < GolfTuning.OWNERSHIP_RECHECK_TICKS) {
            return true;
        }
        ownershipCheckTicks = 0;

        ServerPlayer owner = ownerPlayer();
        if (owner == null) return true; // Retry later when the owner is online.
        if (GolfRoundManager.isActiveBall(owner, getUUID())) return true;

        discard();
        return false;
    }

    private @Nullable ServerPlayer ownerPlayer() {
        UUID golfOwner = getGolfOwner();
        if (golfOwner == null || !(level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) return null;
        return serverLevel.getServer().getPlayerList().getPlayer(golfOwner);
    }

    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.getItemInHand(hand).getItem() instanceof GolfDebugWandItem) {
            if (!level().isClientSide) {
                Vec3 v = getDeltaMovement();
                player.sendSystemMessage(Component.literal(
                        String.format("Ball %s | owner=%s | pos=(%.3f %.3f %.3f) | vel=(%.4f %.4f %.4f) | speed=%.4f | lie=%s | safe=%s | inHole=%s | shotActive=%s",
                                getUUID(), getGolfOwner(), getX(), getY(), getZ(),
                                v.x, v.y, v.z, v.length(), currentLie().displayName(),
                                lastSafePosition, isInHole(), shotActive)));
            }
            return InteractionResult.sidedSuccess(level().isClientSide);
        }
        return super.interact(player, hand);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        UUID golfOwner = getGolfOwner();
        if (golfOwner != null) tag.putUUID(NBT_GOLF_OWNER, golfOwner);
        if (lastSafePosition != null) {
            tag.putBoolean(NBT_HAS_SAFE, true);
            tag.putDouble(NBT_SAFE_X, lastSafePosition.x);
            tag.putDouble(NBT_SAFE_Y, lastSafePosition.y);
            tag.putDouble(NBT_SAFE_Z, lastSafePosition.z);
        }
        tag.putBoolean(NBT_IN_HOLE, isInHole());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        setGolfOwner(tag.hasUUID(NBT_GOLF_OWNER) ? tag.getUUID(NBT_GOLF_OWNER) : null);
        lastSafePosition = tag.getBoolean(NBT_HAS_SAFE)
                ? new Vec3(tag.getDouble(NBT_SAFE_X), tag.getDouble(NBT_SAFE_Y), tag.getDouble(NBT_SAFE_Z))
                : null;
        setInHole(tag.getBoolean(NBT_IN_HOLE));
        sleeping = false;
        cancelShotTelemetry();
    }
}
