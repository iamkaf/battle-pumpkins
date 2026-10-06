package com.iamkaf.battlepumpkins;

import com.iamkaf.battlepumpkins.client.BattlePumpkinsClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/** Fabric client entry point. */
public class BattlePumpkinsFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BattlePumpkinsClient.registerRenderers(new BattlePumpkinsClient.RendererSink() {
            @Override
            public <T extends Entity> void register(EntityType<? extends T> type, EntityRendererProvider<T> provider) {
                EntityRendererRegistry.register(type, provider);
            }
        });
    }
}
