package com.jose.pokecraft.event;

import com.jose.pokecraft.PokeCraftMod;
import com.jose.pokecraft.client.hud.ModeStatusOverlay;
import com.jose.pokecraft.client.renderer.MobJarRenderer;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

@EventBusSubscriber(modid = PokeCraftMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class ClientModEvents {

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(PokeCraftMod.MOB_JAR_BE.get(), MobJarRenderer::new);
    }

    /** Capa de HUD del indicador de dos líneas (modo + estado PVP/PVE) de los aliados. */
    @SubscribeEvent
    public static void registerGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(PokeCraftMod.MOD_ID, "mode_status"),
                ModeStatusOverlay::render);
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemBlockRenderTypes.setRenderLayer(PokeCraftMod.MOB_JAR_BLOCK.get(), RenderType.translucent());
        });
    }
}