package com.iamkaf.battlepumpkins;

import com.iamkaf.amber.api.core.v2.AmberInitializer;
import com.iamkaf.amber.api.registry.v1.DeferredRegister;
import com.iamkaf.amber.api.registry.v1.RegistrySupplier;
import com.iamkaf.battlepumpkins.config.BattlePumpkinsConfig;
import com.iamkaf.battlepumpkins.network.BattlePumpkinsNetwork;
import com.iamkaf.battlepumpkins.pumpkin.BattlePumpkin;
import com.iamkaf.battlepumpkins.spawn.PumpkinSpawner;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;

import java.util.function.BiConsumer;

/** Common entry point for Battle Pumpkins. */
public final class BattlePumpkinsMod {
    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Constants.MOD_ID, Registries.ENTITY_TYPE);

    // MISC keeps wild pumpkins out of the animal mob cap; PumpkinSpawner decides how many there are.
    public static final RegistrySupplier<EntityType<BattlePumpkin>> BATTLE_PUMPKIN = ENTITY_TYPES.register(
            "battle_pumpkin",
            key -> EntityType.Builder.<BattlePumpkin>of(BattlePumpkin::new, MobCategory.MISC)
                    .sized(0.98F, 0.98F)
                    .eyeHeight(0.6F)
                    .clientTrackingRange(10)
                    .build(key)
    );

    private BattlePumpkinsMod() {
    }

    public static void init() {
        AmberInitializer.initialize(Constants.MOD_ID);
        BattlePumpkinsConfig.init();
        ENTITY_TYPES.register();
        BattlePumpkinsNetwork.init();
        PumpkinSpawner.init();
    }

    /** Hands each entity's attributes to the loader's registration hook. */
    public static void registerAttributes(BiConsumer<EntityType<? extends LivingEntity>, AttributeSupplier> sink) {
        sink.accept(BATTLE_PUMPKIN.get(), BattlePumpkin.createAttributes().build());
    }
}
