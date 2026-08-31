package dev.projectgolf.network;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class GolfNetworking {
    private GolfNetworking() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(SwingPayload.TYPE, SwingPayload.STREAM_CODEC, SwingPayload::handle);
        registrar.playToClient(ShotSummaryPayload.TYPE, ShotSummaryPayload.STREAM_CODEC, ShotSummaryPayload::handle);
        registrar.playToClient(HoleViewPayload.TYPE, HoleViewPayload.STREAM_CODEC, HoleViewPayload::handle);
        registrar.playToClient(RoundStatePayload.TYPE, RoundStatePayload.STREAM_CODEC, RoundStatePayload::handle);
        registrar.playToClient(ScorecardPayload.TYPE, ScorecardPayload.STREAM_CODEC, ScorecardPayload::handle);
    }
}
