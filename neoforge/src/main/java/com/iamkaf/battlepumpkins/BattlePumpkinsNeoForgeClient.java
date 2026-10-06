package com.iamkaf.battlepumpkins;

import com.iamkaf.battlepumpkins.client.BattlePumpkinsClient;
import com.iamkaf.konfig.neoforge.api.v1.KonfigNeoForgeClientScreens;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

final class BattlePumpkinsNeoForgeClient {
    private BattlePumpkinsNeoForgeClient() {
    }

    static void init(IEventBus modBus, ModContainer container) {
        KonfigNeoForgeClientScreens.register(container, Constants.MOD_ID);
        modBus.addListener(EntityRenderersEvent.RegisterRenderers.class, event -> BattlePumpkinsClient.registerRenderers(new BattlePumpkinsClient.RendererSink() {
            @Override
            public <T extends Entity> void register(EntityType<? extends T> type, EntityRendererProvider<T> provider) {
                event.registerEntityRenderer(type, provider);
            }
        }));
    }
}
