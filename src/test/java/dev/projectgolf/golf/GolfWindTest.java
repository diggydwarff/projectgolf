package dev.projectgolf.golf;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class GolfWindTest {
    @Test
    void internalWindIsFiniteNormalizedAndBounded() {
        for (long tick : new long[]{0, 100, 1200, 12000, 50000, 500000}) {
            GolfWind.WindSample wind = GolfWind.sample(tick, 123456, false, false);
            assertTrue(GolfPhysics.finite(wind.direction()));
            assertEquals(1.0, wind.direction().horizontalDistance(), 1.0e-9);
            assertTrue(wind.strength() >= 0.0 && wind.strength() <= 1.0);
        }
    }

    @Test
    void windChangesSmoothlyInsteadOfSnappingEveryTick() {
        GolfWind.WindSample a = GolfWind.sample(10000, 42, false, false);
        GolfWind.WindSample b = GolfWind.sample(10001, 42, false, false);
        assertTrue(a.direction().distanceTo(b.direction()) < 0.01);
        assertTrue(Math.abs(a.strength() - b.strength()) < 0.01);
    }

    @Test
    void weatherOnlyStrengthensTheSameUnderlyingWind() {
        GolfWind.WindSample clear = GolfWind.sample(25000, 99, false, false);
        GolfWind.WindSample rain = GolfWind.sample(25000, 99, true, false);
        assertTrue(rain.strength() >= clear.strength());
        assertEquals(clear.direction().x, rain.direction().x, 1.0e-9);
        assertEquals(clear.direction().z, rain.direction().z, 1.0e-9);
    }

    @Test
    void compassMatchesMinecraftCardinals() {
        assertEquals("E", GolfWind.compass(new net.minecraft.world.phys.Vec3(1, 0, 0)));
        assertEquals("W", GolfWind.compass(new net.minecraft.world.phys.Vec3(-1, 0, 0)));
        assertEquals("S", GolfWind.compass(new net.minecraft.world.phys.Vec3(0, 0, 1)));
        assertEquals("N", GolfWind.compass(new net.minecraft.world.phys.Vec3(0, 0, -1)));
    }
}
