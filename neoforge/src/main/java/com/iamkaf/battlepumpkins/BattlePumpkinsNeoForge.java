package com.iamkaf.battlepumpkins;

import com.iamkaf.amber.util.Env;
import com.iamkaf.amber.util.EnvExecutor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

@Mod(Constants.MOD_ID)
public class BattlePumpkinsNeoForge {
    public BattlePumpkinsNeoForge(IEventBus modBus, ModContainer container) {
        BattlePumpkinsMod.init();
        modBus.addListener(EntityAttributeCreationEvent.class, event -> BattlePumpkinsMod.registerAttributes(event::put));
        EnvExecutor.runInEnv(Env.CLIENT, () -> () -> BattlePumpkinsNeoForgeClient.init(modBus, container));
    }
}
