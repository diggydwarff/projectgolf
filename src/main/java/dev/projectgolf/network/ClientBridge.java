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

    private static ShotSummaryPresenter shotSummaryPresenter = (title, message) -> {};

    private ClientBridge() {}

    public static void installShotSummaryPresenter(ShotSummaryPresenter presenter) {
        shotSummaryPresenter = presenter == null ? (title, message) -> {} : presenter;
    }

    public static void showShotSummary(String title, String message) {
        shotSummaryPresenter.show(title, message);
    }
}
