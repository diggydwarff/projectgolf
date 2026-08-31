package dev.projectgolf.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/** Client controls for course presentation. */
public final class GolfKeyMappings {
    private GolfKeyMappings() {}

    public static final KeyMapping HOLE_VIEW = new KeyMapping(
            "key.projectgolf.hole_view",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "key.categories.projectgolf");

    public static final KeyMapping FLYOVER = new KeyMapping(
            "key.projectgolf.flyover",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_B,
            "key.categories.projectgolf");
}
