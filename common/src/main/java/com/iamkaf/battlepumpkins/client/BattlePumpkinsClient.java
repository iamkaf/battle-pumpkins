package com.iamkaf.battlepumpkins.client;

import com.iamkaf.battlepumpkins.BattlePumpkinsMod;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

/** Client setup shared by every loader. */
public final class BattlePumpkinsClient {
    private BattlePumpkinsClient() {
    }

    public static void registerRenderers(RendererSink sink) {
        sink.register(BattlePumpkinsMod.BATTLE_PUMPKIN.get(), BattlePumpkinRenderer::new);
    }

    /** Each loader's way to register an entity renderer. */
    public interface RendererSink {
        <T extends Entity> void register(EntityType<? extends T> type, EntityRendererProvider<T> provider);
    }
}
