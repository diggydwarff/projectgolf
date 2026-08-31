package dev.projectgolf.network;

import net.minecraft.nbt.CompoundTag;

/**
 * Common-side bridge for optional client presentation code.
 *
 * The common networking layer never references Minecraft client classes. The physical client
 * installs its presenter from ProjectGolfClient; on a dedicated server this remains a no-op.
 */
public final class ClientBridge {
    @FunctionalInterface
    public interface ShotSummaryPresenter {
        void show(String title, String message);
    }

    @FunctionalInterface
    public interface HoleViewPresenter {
        void show(HoleViewPayload payload);
    }

    @FunctionalInterface
    public interface RoundStatePresenter {
        void show(RoundStatePayload payload);
    }

    @FunctionalInterface
    public interface ScorecardPresenter {
        void show(CompoundTag data);
    }

    @FunctionalInterface
    public interface CourseUiPresenter {
        void show(CompoundTag data);
    }

    private static ShotSummaryPresenter shotSummaryPresenter = (title, message) -> {};
    private static HoleViewPresenter holeViewPresenter = payload -> {};
    private static RoundStatePresenter roundStatePresenter = payload -> {};
    private static ScorecardPresenter scorecardPresenter = data -> {};
    private static CourseUiPresenter courseUiPresenter = data -> {};

    private ClientBridge() {}

    public static void installShotSummaryPresenter(ShotSummaryPresenter presenter) {
        shotSummaryPresenter = presenter == null ? (title, message) -> {} : presenter;
    }

    public static void showShotSummary(String title, String message) {
        shotSummaryPresenter.show(title, message);
    }

    public static void installHoleViewPresenter(HoleViewPresenter presenter) {
        holeViewPresenter = presenter == null ? payload -> {} : presenter;
    }

    public static void showHoleView(HoleViewPayload payload) {
        holeViewPresenter.show(payload);
    }

    public static void installRoundStatePresenter(RoundStatePresenter presenter) {
        roundStatePresenter = presenter == null ? payload -> {} : presenter;
    }

    public static void showRoundState(RoundStatePayload payload) {
        roundStatePresenter.show(payload);
    }

    public static void installScorecardPresenter(ScorecardPresenter presenter) {
        scorecardPresenter = presenter == null ? data -> {} : presenter;
    }

    public static void showScorecard(CompoundTag data) {
        scorecardPresenter.show(data);
    }

    public static void installCourseUiPresenter(CourseUiPresenter presenter) {
        courseUiPresenter = presenter == null ? data -> {} : presenter;
    }

    public static void showCourseUi(CompoundTag data) {
        courseUiPresenter.show(data);
    }
}
