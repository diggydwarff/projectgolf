# Project Golf — NeoForge 1.21.1 first-pass prototype

**Name:** `Project Golf` / mod id `projectgolf`.

A mechanics-first golf addon targeting Minecraft 1.21.1 / NeoForge 21.1.228 and Ritchie's Projectile Library 2.1.2.

This alpha is intentionally built to answer one question first: **is the golf itself fun and tunable?** It implements the complete basic loop before integrations, cosmetics, recipes, or progression.

## Implemented in this pass

- Persistent, server-authoritative `GolfBallEntity` with synchronized player ownership
- RPL `precise_motion` entity tag for high-precision client movement sync
- Five clubs: Driver, Wood, Iron, Wedge, Putter
- Mario-Golf-like two-stage timing input:
  1. hold/release RMB to lock power
  2. press RMB again to lock impact accuracy
- Client-side trajectory preview while aiming/swinging
- Client-side actual ball trail
- Server-side launch validation and physics
- Shared `GolfPhysics` math layer used by authoritative movement and trajectory prediction
- Gravity, drag, collision response, bounce, roll, slope acceleration, controlled ramp step-height and stop threshold
- Lie-dependent power/accuracy and surface-dependent roll/bounce
- Tee, fairway, fringe, green, rough, deep rough and bunker surfaces
- Green/fairway/rough slope blocks for non-one-block-height terrain
- Functional cup with speed-sensitive capture
- Water and out-of-bounds penalty/reset behavior
- Per-player strokes and penalties
- Persistent named courses and hole tee/cup/par definitions
- Automatic basic hole progression and round total
- Debug development kit, forced launch tool, surface inspector and invariant self-tests
- JUnit regression tests for swing and physics math
- Per-shot tuning telemetry: total distance, carry, roll, apex, duration and final lie

## Controls

1. Place a `Golf Ball` on the ground.
2. Hold one of the five clubs in the main hand and stand within 5 blocks of your ball.
3. Aim with the normal camera. A particle trajectory preview shows the nominal path.
4. Hold **right mouse**. The power meter sweeps.
5. Release **right mouse** to lock power.
6. The accuracy marker begins sweeping. Press **right mouse** when it is near center.
7. The server validates and launches the ball.

The client handles timing responsively; the server computes the authoritative shot vector and owns physics/scoring.

## Fast development start

In an OP-enabled dev world:

```text
/golfdebug give
/golfdebug spawnball
/golfdebug selftest
/golfdebug tuning
```

Build a strip of fairway/green/rough/bunker and start hitting balls. The debug wand can be used on the ball for exact state.

Useful commands:

```text
/golfdebug ball
/golfdebug surface
/golfdebug launch <speed> <loftDegrees>
/golfdebug cleanup

/golf course create test
/golf course settee test 1 4
/golf course setcup test 1
/golf course info test
/golf start test 1
/golf status
```

`settee` and `setcup` use the block directly below the command player. Stand on the intended tee marker or cup block.

## Development status

This source tree passed its bundled JSON/resource static validator here, but it could not be Gradle-compiled in the generation environment because it does not have a working Gradle wrapper/dependency network path. The project is based on the official 1.21.1 ModDevGradle layout and exact 1.21.1 APIs were cross-checked where practical. **Treat the first local `gradlew compileJava` as the final API/mapping verification pass.**

See `DEV_TEST_PLAN.md` before putting this on a live server.

## Alpha 2 hardening pass

Before first gameplay testing, the project received a dedicated lifecycle/performance pass. Key changes include tracked active-ball UUID lookup, resting-ball sleep, synchronized in-hole state, logical flat-cup capture, death-respawn round copying, cached slope shapes, hazard telemetry reset, stationary-only swing/preview selection, and holed-ball cleanup. See `PRETEST_AUDIT.md`.
