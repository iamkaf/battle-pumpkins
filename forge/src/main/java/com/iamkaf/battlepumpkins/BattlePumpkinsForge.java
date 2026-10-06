package com.iamkaf.battlepumpkins;

import com.iamkaf.battlepumpkins.client.BattlePumpkinsClient;
import com.iamkaf.konfig.forge.api.v1.KonfigForgeClientScreens;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLLoader;

@Mod(Constants.MOD_ID)
public class BattlePumpkinsForge {
    public BattlePumpkinsForge() {
        BattlePumpkinsMod.init();
        EntityAttributeCreationEvent.BUS.addListener(event -> BattlePumpkinsMod.registerAttributes(event::put));
        if (FMLLoader.getDist().isClient()) {
            KonfigForgeClientScreens.register(Constants.MOD_ID);
            EntityRenderersEvent.RegisterRenderers.BUS.addListener(event -> BattlePumpkinsClient.registerRenderers(new BattlePumpkinsClient.RendererSink() {
                @Override
                public <T extends Entity> void register(EntityType<? extends T> type, EntityRendererProvider<T> provider) {
                    event.registerEntityRenderer(type, provider);
                }
            }));
        }
    }
}
