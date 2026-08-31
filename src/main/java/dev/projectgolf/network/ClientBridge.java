package dev.projectgolf.network;

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

    private static ShotSummaryPresenter shotSummaryPresenter = (title, message) -> {};
    private static HoleViewPresenter holeViewPresenter = payload -> {};
    private static RoundStatePresenter roundStatePresenter = payload -> {};

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
}
