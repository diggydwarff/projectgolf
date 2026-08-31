package dev.projectgolf;

import com.mojang.logging.LogUtils;
import dev.projectgolf.command.GolfCommands;
import dev.projectgolf.block.PuttingGreenSlopeInteraction;
import dev.projectgolf.course.TeeMarkerInteraction;
import dev.projectgolf.network.GolfNetworking;
import dev.projectgolf.registry.GolfBlocks;
import dev.projectgolf.registry.GolfEntities;
import dev.projectgolf.registry.GolfCreativeTabs;
import dev.projectgolf.registry.GolfItems;
import dev.projectgolf.round.GolfRoundManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(ProjectGolf.MOD_ID)
public final class ProjectGolf {
    public static final String MOD_ID = "projectgolf";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ProjectGolf(IEventBus modBus) {
        GolfBlocks.BLOCKS.register(modBus);
        GolfItems.ITEMS.register(modBus);
        GolfEntities.ENTITIES.register(modBus);
        GolfCreativeTabs.TABS.register(modBus);

        modBus.addListener(GolfNetworking::register);

        NeoForge.EVENT_BUS.addListener(GolfCommands::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(GolfRoundManager::onPlayerClone);
        NeoForge.EVENT_BUS.addListener(GolfRoundManager::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(TeeMarkerInteraction::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(PuttingGreenSlopeInteraction::onRightClickBlock);
    }
}
