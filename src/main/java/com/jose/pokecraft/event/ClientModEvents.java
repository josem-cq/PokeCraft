package com.jose.pokecraft.event;

import com.jose.pokecraft.PokeCraftMod;
import com.jose.pokecraft.client.renderer.MobJarRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = PokeCraftMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class ClientModEvents {

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(PokeCraftMod.MOB_JAR_BE.get(), MobJarRenderer::new);
    }
}