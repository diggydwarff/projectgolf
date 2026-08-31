package dev.projectgolf.client;

import dev.projectgolf.network.RoundStatePayload;

/** Latest server-authoritative round state. Cleared when the server says the round ended. */
public final class ClientRoundState {
    private ClientRoundState() {}

    private static RoundStatePayload state = RoundStatePayload.inactive();

    public static void accept(RoundStatePayload payload) {
        state = payload == null ? RoundStatePayload.inactive() : payload;
    }

    public static RoundStatePayload state() {
        return state;
    }

    public static boolean active() {
        return state.active();
    }

    public static void clear() {
        state = RoundStatePayload.inactive();
    }
}
