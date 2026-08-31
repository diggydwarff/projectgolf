package dev.projectgolf.client;

import dev.projectgolf.ProjectGolf;
import dev.projectgolf.network.ClientBridge;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = ProjectGolf.MOD_ID, dist = Dist.CLIENT)
public final class ProjectGolfClient {
    public ProjectGolfClient(IEventBus modBus) {
        ClientBridge.installShotSummaryPresenter(ClientShotSummary::show);
        ClientBridge.installHoleViewPresenter(ClientHoleView::show);
        ClientBridge.installRoundStatePresenter(ClientRoundState::accept);
        modBus.addListener(ClientEvents::registerRenderers);
        modBus.addListener(ClientEvents::registerHud);
        modBus.addListener(ClientEvents::registerKeyMappings);
        modBus.addListener(ClientEvents::registerBlockColors);
        modBus.addListener(ClientEvents::registerItemColors);

        NeoForge.EVENT_BUS.addListener(ClientEvents::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onUseInput);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onMouseScroll);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onComputeFov);
    }
}
