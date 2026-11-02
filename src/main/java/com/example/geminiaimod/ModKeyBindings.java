package com.example.geminiaimod;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

public class ModKeyBindings {
    public static final String KEY_CATEGORY = "key.categories.geminiaimod";
    public static final String KEY_OPEN_CONSOLE = "key.geminiaimod.open_console";

    public static final KeyMapping OPEN_CONSOLE_KEY = new KeyMapping(
            KEY_OPEN_CONSOLE,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_L, // Standardmäßig Taste L, im Menü frei änderbar
            KEY_CATEGORY
    );
}
