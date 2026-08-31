package dev.projectgolf.command;

import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class GolfCommands {
    private GolfCommands() {}

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        GolfCourseCommand.register(event.getDispatcher());
        GolfDebugCommand.register(event.getDispatcher());
    }
}
