package dev.projectgolf.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/** Presents shot telemetry in Minecraft's unobtrusive top-right toast area instead of chat. */
public final class ClientShotSummary {
    private static final SystemToast.SystemToastId SHOT_SUMMARY = new SystemToast.SystemToastId(4000L);

    private ClientShotSummary() {}

    public static void show(String title, String message) {
        Minecraft minecraft = Minecraft.getInstance();
        SystemToast.addOrUpdate(
                minecraft.getToasts(),
                SHOT_SUMMARY,
                Component.literal(title),
                Component.literal(message)
        );
    }
}
