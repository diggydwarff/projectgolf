package dev.projectgolf.course;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CourseDefinitionTest {
    @Test
    void completeHolesAreOrderedWithoutRequiringContiguousNumbers() {
        CourseDefinition course = new CourseDefinition("test");
        course.putHole(new HoleDefinition(1, 4, "minecraft:overworld", new BlockPos(0, 64, 0), new BlockPos(20, 64, 0)));
        course.putHole(new HoleDefinition(2, 4, "minecraft:overworld", new BlockPos(30, 64, 0), null));
        course.putHole(new HoleDefinition(4, 3, "minecraft:overworld", new BlockPos(60, 64, 0), new BlockPos(75, 64, 0)));

        assertEquals(2, course.completeHoleCount());
        assertEquals(1, course.firstCompleteHole().orElseThrow().number());
        assertEquals(4, course.nextCompleteHoleAfter(1).orElseThrow().number());
        assertTrue(course.nextCompleteHoleAfter(4).isEmpty());
    }

    @Test
    void holeCompletenessNeedsOnlyTeeCupAndDimension() {
        HoleDefinition hole = new HoleDefinition(
                7, 5, "minecraft:overworld", new BlockPos(10, 70, 10), new BlockPos(120, 66, -40));
        assertTrue(hole.complete());
    }
    @Test
    void guidePointsArePresentationOnlyAndRouteOrderIsStable() {
        BlockPos tee = new BlockPos(0, 64, 0);
        BlockPos guide1 = new BlockPos(40, 66, 10);
        BlockPos guide2 = new BlockPos(75, 63, 35);
        BlockPos cup = new BlockPos(110, 64, 50);
        HoleDefinition hole = new HoleDefinition(3, 4, "minecraft:overworld", tee, cup)
                .withGuidePoint(guide1)
                .withGuidePoint(guide2);

        assertTrue(hole.complete());
        assertEquals(java.util.List.of(tee, guide1, guide2, cup), hole.routePoints());
        assertEquals(2, hole.guidePoints().size());
        assertTrue(hole.clearGuidePoints().complete());
        assertTrue(hole.clearGuidePoints().guidePoints().isEmpty());
    }

}
