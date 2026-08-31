package dev.projectgolf.entity;

import dev.projectgolf.block.GolfSlopeBlock;
import dev.projectgolf.block.PuttingGreenLayerBlock;
import dev.projectgolf.block.PuttingGreenSlopeBlock;
import dev.projectgolf.golf.ClubType;
import dev.projectgolf.golf.GolfPhysics;
import dev.projectgolf.golf.GolfSurface;
import dev.projectgolf.golf.GolfTuning;
import dev.projectgolf.golf.GolfWind;
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
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
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
    private int landingMarkerTotalTicks;
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
        // Quarter-height putting-green layers are the normal gentle elevation tool. Allow one
        // 1/4 transition while remaining too low to auto-climb normal half-slabs/full steps.
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
                float strength = landingMarkerTotalTicks <= 0
                        ? 0.0f
                        : landingMarkerTicks / (float) landingMarkerTotalTicks;
                if (markerOwner != null) GolfVisualEffects.landingMarker(markerOwner, position(), strength);
            }
            landingMarkerTicks--;
            if (landingMarkerTicks <= 0) landingMarkerTotalTicks = 0;
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
        SlopeEntry slopeEntry = findUphillSlopeEntry(beforeMove, requested);
        SlopeExit slopeExit = findUphillSlopeExit(beforeMove, requested);
        OpenSlopeExit openSlopeExit = findOpenUphillSlopeExit(beforeMove, requested);
        HalfSlabStep halfSlabStep = findHalfSlabStep(beforeMove, requested);
        move(MoverType.SELF, requested);
        Vec3 actualMove = position().subtract(beforeMove);

        boolean collidedX = Math.abs(actualMove.x - requested.x) > 1.0e-5;
        boolean collidedY = Math.abs(actualMove.y - requested.y) > 1.0e-5;
        boolean collidedZ = Math.abs(actualMove.z - requested.z) > 1.0e-5;

        // Entity.move can treat the first tiny voxel terrace of a slope as a horizontal wall for
        // small projectile-sized entities. That previously fed into the golf wall-bounce code and
        // literally reversed a putt at the bottom edge. If the obstacle is the legal downhill edge
        // of a configured golf slope, lift onto its first collision terrace and retry only the
        // horizontal remainder. This is a slope transition, not a wall impact.
        boolean handledSlopeLip = false;
        if ((collidedX || collidedZ) && slopeEntry != null) {
            Vec3 firstMove = actualMove;
            double lift = slopeEntry.firstSurfaceY() - getY();
            if (lift <= 0.02 && getY() >= slopeEntry.firstSurfaceY() - 0.02) {
                // Vanilla step-up already got us onto the first terrace; the tiny horizontal
                // discrepancy is not a wall and must not reverse the ball.
                handledSlopeLip = true;
            } else if (lift > 1.0e-5 && lift <= maxUpStep() + 0.03) {
                setPos(getX(), getY() + lift + 0.001, getZ());
                double remainingX = requested.x - firstMove.x;
                double remainingZ = requested.z - firstMove.z;
                move(MoverType.SELF, new Vec3(remainingX, 0.0, remainingZ));
                actualMove = position().subtract(beforeMove);
                collidedX = Math.abs(actualMove.x - requested.x) > 1.0e-5;
                collidedZ = Math.abs(actualMove.z - requested.z) > 1.0e-5;
                handledSlopeLip = true;
            }
        }

        // A half slab should work as an actual golf-course stair: flat -> bottom slab -> next
        // full block. The previous implementation only special-cased the custom Grass Slab as a
        // destination, so the second +1/2 transition hit the full block as a wall. Permit a single
        // half-block step whenever either side of the transition is a BOTTOM slab. This works for
        // every Project Golf slab and ordinary vanilla bottom slabs, while top/double slabs and
        // unrelated 1/2-height obstacles remain normal collision. The destination lie still owns
        // its normal drag (grass, green, bunker, stone/default, etc.).
        if ((collidedX || collidedZ) && !handledSlopeLip && halfSlabStep != null) {
            Vec3 firstMove = actualMove;
            double lift = halfSlabStep.surfaceY() - getY();
            if (lift > 1.0e-5 && lift <= 0.525) {
                setPos(getX(), getY() + lift + 0.001, getZ());
                double remainingX = requested.x - firstMove.x;
                double remainingZ = requested.z - firstMove.z;
                move(MoverType.SELF, new Vec3(remainingX, 0.0, remainingZ));
                actualMove = position().subtract(beforeMove);
                collidedX = Math.abs(actualMove.x - requested.x) > 1.0e-5;
                collidedZ = Math.abs(actualMove.z - requested.z) > 1.0e-5;
                handledSlopeLip = true;
            }
        }

        // The opposite seam can fail for the same reason: after climbing the last collision
        // terrace, floating-point/collision rounding can leave the ball a few hundredths below
        // the adjacent flat surface. Entity.move then sees the destination block's side as a wall
        // and the ball appears to stick at the crest. Only when we are travelling UPHILL off a
        // known golf slope, and the next surface actually lines up with that slope's high edge,
        // snap the ball onto that surface and retry the unused horizontal movement. This is a seam
        // correction only; it adds no forward speed and cannot auto-climb unrelated blocks.
        if ((collidedX || collidedZ) && slopeExit != null) {
            Vec3 firstMove = actualMove;
            double lift = slopeExit.destinationSurfaceY() - getY();
            if (lift >= -0.025 && lift <= maxUpStep() + 0.04) {
                if (lift > 1.0e-5) {
                    setPos(getX(), getY() + lift + 0.001, getZ());
                }
                double remainingX = requested.x - firstMove.x;
                double remainingZ = requested.z - firstMove.z;
                if (Math.abs(remainingX) > 1.0e-6 || Math.abs(remainingZ) > 1.0e-6) {
                    move(MoverType.SELF, new Vec3(remainingX, 0.0, remainingZ));
                }
                actualMove = position().subtract(beforeMove);
                collidedX = Math.abs(actualMove.x - requested.x) > 1.0e-5;
                collidedZ = Math.abs(actualMove.z - requested.z) > 1.0e-5;
                handledSlopeLip = true;
            }
        }

        // If the high edge opens directly into air, there is no wall to bounce from. The stepped
        // slope collision mesh still has a final vertical face, though, and small balls can catch
        // that face before their center crosses the block boundary. Move the center just beyond
        // the crest and let gravity take over. This preserves speed and produces the expected
        // roll-up -> leave-ramp -> fall arc rather than an artificial ricochet.
        if ((collidedX || collidedZ) && slopeExit == null && openSlopeExit != null) {
            setPos(openSlopeExit.x(), openSlopeExit.y(), openSlopeExit.z());
            setOnGround(false);
            actualMove = position().subtract(beforeMove);
            collidedX = false;
            collidedZ = false;
            handledSlopeLip = true;
        }

        GolfSurface surface = currentLie();
        Vec3 velocity = requested;
        boolean greenLayerStep = isPuttingGreenLayerAt(beforeMove) || isPuttingGreenLayerAt(position());

        // Horizontal walls lose most energy. Ground impacts use the lie's restitution.
        if (collidedX && !handledSlopeLip) velocity = GolfPhysics.wallBounceX(velocity);
        if (collidedZ && !handledSlopeLip) velocity = GolfPhysics.wallBounceZ(velocity);
        if (collidedY) velocity = GolfPhysics.verticalCollision(velocity, surface);

        SlopeInfo slopeInfo = null;
        if (onGround() && Math.abs(velocity.y) < 0.08) {
            slopeInfo = currentSlopeInfo();
            Direction downhill = slopeInfo == null ? null : slopeInfo.downhill();
            double slopeRise = slopeInfo == null ? 0.0 : slopeInfo.rise();
            velocity = GolfPhysics.grounded(velocity, surface, downhill, slopeRise);
            if (greenLayerStep && actualMove.y > 0.01) {
                velocity = GolfPhysics.greenLayerUphill(velocity, actualMove.y);
            }
        } else {
            velocity = GolfPhysics.airborne(velocity, GolfWind.acceleration(level()));
        }

        if (slopeInfo == null && GolfPhysics.shouldSettle(velocity, onGround())) {
            settlingTicks++;
            if (settlingTicks >= GolfTuning.STOP_SETTLE_TICKS) {
                velocity = Vec3.ZERO;
                movingTicks = 0;
                stoppedThisTick = shotActive && !shotStopReported && shotDistance > 0.001;
                if (!level().isClientSide && !isInWater() && !isOutOfBoundsSurface()) {
                    lastSafePosition = position();
                }
                // Never sleep on a known slope: repeated downhill acceleration is gameplay and
                // is what lets a weak uphill putt reverse and roll back down.
                sleeping = slopeInfo == null;
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
        landingMarkerTotalTicks = 0;
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
        landingMarkerTotalTicks = 0;
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
            int markerDuration = GolfTuning.landingMarkerDurationTicks(shotDistance);
            landingMarkerTotalTicks = markerDuration;
            GolfVisualEffects.landingMarker(owner, position(), 1.0f);
            // The immediate marker counts as the first pulse; avoid re-emitting again one tick later.
            landingMarkerTicks = Math.max(0, markerDuration - 1);
            PacketDistributor.sendToPlayer(owner, ShotSummaryPayload.forStoppedShot(
                    shotClub, shotPower, shotAccuracy, shotDistance, shotCarryDistance, roll,
                    currentLie(), GolfRoundManager.strokes(owner), false));
        }
    }

    private boolean isPuttingGreenLayerAt(Vec3 position) {
        BlockPos pos = BlockPos.containing(position);
        return level().getBlockState(pos).getBlock() instanceof PuttingGreenLayerBlock
                || level().getBlockState(pos.below()).getBlock() instanceof PuttingGreenLayerBlock;
    }

    private @Nullable SlopeEntry findUphillSlopeEntry(Vec3 from, Vec3 requested) {
        Direction movement = horizontalDirection(requested);
        if (movement == null) return null;

        BlockPos origin = BlockPos.containing(from);
        BlockPos projected = BlockPos.containing(from.x + requested.x, from.y, from.z + requested.z);
        BlockPos[] candidates = {
                projected, projected.below(),
                origin.relative(movement), origin.relative(movement).below(),
                origin, origin.below()
        };

        for (BlockPos pos : candidates) {
            BlockState state = level().getBlockState(pos);
            if (!(state.getBlock() instanceof PuttingGreenSlopeBlock slope)) continue;
            if (state.getValue(PuttingGreenSlopeBlock.FACING) != movement) continue;

            double lowSurfaceY = pos.getY() + slope.lowHeight(state);
            // Only assist a slope whose low edge actually meets the ball's current terrace. Do not
            // turn this into generic auto-climb for mismatched elevations or ordinary blocks.
            if (Math.abs(lowSurfaceY - from.y) > maxUpStep() + 0.035) continue;

            double firstSurfaceY = pos.getY() + slope.firstCollisionHeight(state);
            double requiredLift = firstSurfaceY - from.y;
            if (requiredLift < -0.02 || requiredLift > maxUpStep() + 0.035) continue;
            return new SlopeEntry(firstSurfaceY);
        }
        return null;
    }

    private @Nullable HalfSlabStep findHalfSlabStep(Vec3 from, Vec3 requested) {
        Direction movement = horizontalDirection(requested);
        if (movement == null) return null;

        boolean sourceIsBottomSlab = isBottomSlabSupporting(from);
        BlockPos origin = BlockPos.containing(from);
        BlockPos projected = BlockPos.containing(from.x + requested.x, from.y, from.z + requested.z);
        BlockPos[] candidates = {
                projected, origin.relative(movement),
                projected.below(), origin.relative(movement).below()
        };

        for (BlockPos pos : candidates) {
            BlockState state = level().getBlockState(pos);
            var shape = state.getCollisionShape(level(), pos);
            if (shape.isEmpty()) continue;

            double surfaceY = pos.getY() + shape.max(Direction.Axis.Y);
            double lift = surfaceY - from.y;
            if (lift <= maxUpStep() + 0.02 || lift > 0.525) continue;

            boolean destinationIsBottomSlab = isBottomSlab(state);
            if (sourceIsBottomSlab || destinationIsBottomSlab) {
                return new HalfSlabStep(surfaceY);
            }
        }
        return null;
    }

    private boolean isBottomSlabSupporting(Vec3 position) {
        BlockPos at = BlockPos.containing(position.x, position.y - 0.02, position.z);
        BlockPos[] candidates = { at, at.below() };
        for (BlockPos pos : candidates) {
            BlockState state = level().getBlockState(pos);
            if (!isBottomSlab(state)) continue;
            var shape = state.getCollisionShape(level(), pos);
            if (shape.isEmpty()) continue;
            double top = pos.getY() + shape.max(Direction.Axis.Y);
            if (Math.abs(top - position.y) <= 0.06) return true;
        }
        return false;
    }

    private static boolean isBottomSlab(BlockState state) {
        return state.getBlock() instanceof SlabBlock
                && state.hasProperty(SlabBlock.TYPE)
                && state.getValue(SlabBlock.TYPE) == SlabType.BOTTOM;
    }

    private record HalfSlabStep(double surfaceY) {}

    private static @Nullable Direction horizontalDirection(Vec3 velocity) {
        double ax = Math.abs(velocity.x);
        double az = Math.abs(velocity.z);
        if (Math.max(ax, az) < 1.0e-6) return null;
        if (ax > az) return velocity.x >= 0.0 ? Direction.EAST : Direction.WEST;
        return velocity.z >= 0.0 ? Direction.SOUTH : Direction.NORTH;
    }

    private record SlopeEntry(double firstSurfaceY) {}

    private @Nullable SlopeExit findUphillSlopeExit(Vec3 from, Vec3 requested) {
        Direction movement = horizontalDirection(requested);
        if (movement == null) return null;

        BlockPos origin = BlockPos.containing(from);
        BlockPos[] slopeCandidates = { origin, origin.below(), origin.relative(movement.getOpposite()), origin.relative(movement.getOpposite()).below() };

        for (BlockPos slopePos : slopeCandidates) {
            BlockState slopeState = level().getBlockState(slopePos);
            if (!(slopeState.getBlock() instanceof PuttingGreenSlopeBlock slope)) continue;
            if (slopeState.getValue(PuttingGreenSlopeBlock.FACING) != movement) continue;

            double highSurfaceY = slopePos.getY() + slope.highHeight(slopeState);
            // We only care about the crest once the ball is already near the high edge vertically.
            if (from.y < highSurfaceY - maxUpStep() - 0.05 || from.y > highSurfaceY + 0.08) continue;

            BlockPos destination = slopePos.relative(movement);
            Double destinationSurfaceY = alignedTopSurfaceY(destination, highSurfaceY);
            if (destinationSurfaceY == null) {
                destinationSurfaceY = alignedTopSurfaceY(destination.below(), highSurfaceY);
            }
            if (destinationSurfaceY == null) continue;
            return new SlopeExit(destinationSurfaceY);
        }
        return null;
    }

    private @Nullable OpenSlopeExit findOpenUphillSlopeExit(Vec3 from, Vec3 requested) {
        Direction movement = horizontalDirection(requested);
        if (movement == null) return null;

        BlockPos origin = BlockPos.containing(from);
        BlockPos[] slopeCandidates = {
                origin, origin.below(),
                origin.relative(movement.getOpposite()),
                origin.relative(movement.getOpposite()).below()
        };

        for (BlockPos slopePos : slopeCandidates) {
            BlockState slopeState = level().getBlockState(slopePos);
            if (!(slopeState.getBlock() instanceof PuttingGreenSlopeBlock slope)) continue;
            if (slopeState.getValue(PuttingGreenSlopeBlock.FACING) != movement) continue;

            double highSurfaceY = slopePos.getY() + slope.highHeight(slopeState);
            if (from.y < highSurfaceY - maxUpStep() - 0.05 || from.y > highSurfaceY + 0.08) continue;

            BlockPos destination = slopePos.relative(movement);
            BlockState destinationState = level().getBlockState(destination);
            if (!destinationState.getCollisionShape(level(), destination).isEmpty()) continue;

            // Clear the slope's vertical high-edge face by just over half the ball width.
            double clearance = getBbWidth() * 0.5 + 0.015;
            double x = getX();
            double z = getZ();
            switch (movement) {
                case EAST -> x = slopePos.getX() + 1.0 + clearance;
                case WEST -> x = slopePos.getX() - clearance;
                case SOUTH -> z = slopePos.getZ() + 1.0 + clearance;
                case NORTH -> z = slopePos.getZ() - clearance;
                default -> { continue; }
            }
            return new OpenSlopeExit(x, highSurfaceY + 0.002, z);
        }
        return null;
    }

    private record OpenSlopeExit(double x, double y, double z) {}

    private @Nullable Double alignedTopSurfaceY(BlockPos pos, double expectedY) {
        BlockState state = level().getBlockState(pos);
        if (state.isAir()) return null;
        var shape = state.getCollisionShape(level(), pos);
        if (shape.isEmpty()) return null;
        double topY = pos.getY() + shape.max(Direction.Axis.Y);
        // The helper must never become generic step-up. It is valid only for a surface whose top
        // is essentially the continuation of the configured slope's high edge.
        return Math.abs(topY - expectedY) <= 0.055 ? topY : null;
    }

    private record SlopeExit(double destinationSurfaceY) {}

    private @Nullable SlopeInfo currentSlopeInfo() {
        BlockState state = level().getBlockState(blockPosition());
        if (!(state.getBlock() instanceof GolfSlopeBlock)
                && !(state.getBlock() instanceof PuttingGreenSlopeBlock)) {
            state = level().getBlockState(blockPosition().below());
        }
        if (state.getBlock() instanceof PuttingGreenSlopeBlock slope) {
            return new SlopeInfo(slope.downhill(state), slope.rise(state));
        }
        if (state.getBlock() instanceof GolfSlopeBlock slope) {
            return new SlopeInfo(slope.downhill(state), 1.0);
        }
        return null;
    }

    private record SlopeInfo(Direction downhill, double rise) {}

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
                landingMarkerTotalTicks = 0;
                setInHole(true);
                inHoleTicks = 0;
                setPos(cupX, pos.getY() + 1.01, cupZ);
                setDeltaMovement(Vec3.ZERO);

                if (ownerForCup != null) {
                    int strokes = GolfRoundManager.strokes(ownerForCup);
                    int par = GolfRoundManager.currentHoleDefinition(ownerForCup)
                            .map(dev.projectgolf.course.HoleDefinition::par)
                            .orElse(0);
                    if (level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                        GolfVisualEffects.holed(serverLevel, position(), strokes, par);
                    }

                    if (shotCarryDistance < 0.0) shotCarryDistance = 0.0;
                    double roll = Math.max(0.0, shotDistance - shotCarryDistance);
                    PacketDistributor.sendToPlayer(ownerForCup, ShotSummaryPayload.forStoppedShot(
                            shotClub, shotPower, shotAccuracy, shotDistance, shotCarryDistance, roll,
                            currentLie(), strokes, true));
                    GolfRoundManager.finishHole(ownerForCup);
                } else if (level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                    GolfVisualEffects.holed(serverLevel, position());
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
