package com.example.geminiaimod;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod(GeminiAiMod.MODID)
public class GeminiAiMod {
    public static final String MODID = "geminiaimod";

    public GeminiAiMod() {
        MinecraftForge.EVENT_BUS.register(this);
        GeminiService.loadConfig();
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onKeyRegister(RegisterKeyMappingsEvent event) {
            event.register(ModKeyBindings.OPEN_CONSOLE_KEY);
        }
    }

    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
    public static class ClientForgeEvents {
        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                while (ModKeyBindings.OPEN_CONSOLE_KEY.consumeClick()) {
                    Minecraft mc = Minecraft.getInstance();
                    if (mc.screen == null) {
                        mc.setScreen(new AiConsoleScreen());
                    }
                }
            }
        }
    }
}
