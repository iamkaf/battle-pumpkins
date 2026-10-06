package com.iamkaf.battlepumpkins;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;

/** Fabric entry point. */
public class BattlePumpkinsFabric implements ModInitializer {
    @Override
    public void onInitialize() {
        BattlePumpkinsMod.init();
        BattlePumpkinsMod.registerAttributes(FabricDefaultAttributeRegistry::register);
    }
}
