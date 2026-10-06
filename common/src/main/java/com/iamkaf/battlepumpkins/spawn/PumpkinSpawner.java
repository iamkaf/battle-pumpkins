package com.iamkaf.battlepumpkins.spawn;

import com.iamkaf.amber.api.event.v1.events.common.ServerTickEvents;
import com.iamkaf.amber.api.event.v1.events.common.WorldEvents;
import com.iamkaf.battlepumpkins.BattlePumpkinsMod;
import com.iamkaf.battlepumpkins.pumpkin.BattlePumpkin;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * Spawns wild Battle Pumpkins on grass near players in the overworld at night. A spawner of our own, instead of a
 * biome spawn entry, puts the Halloween calendar in one place and needs no per-loader biome modifiers.
 */
public final class PumpkinSpawner {
    private static final int INTERVAL_TICKS = 200;
    private static final int MIN_DISTANCE = 24;
    private static final int MAX_DISTANCE = 40;
    private static final double CAP_RADIUS = 48.0;

    private static @Nullable ServerLevel overworld;

    private PumpkinSpawner() {
    }

    public static void init() {
        WorldEvents.WORLD_LOAD.register((server, level) -> {
            if (level instanceof ServerLevel serverLevel && serverLevel.dimension() == Level.OVERWORLD) {
                overworld = serverLevel;
            }
        });
        WorldEvents.WORLD_UNLOAD.register((server, level) -> {
            if (level == overworld) {
                overworld = null;
            }
        });
        ServerTickEvents.END_SERVER_TICK.register(PumpkinSpawner::tick);
    }

    private static void tick() {
        ServerLevel level = overworld;
        if (level == null || level.getGameTime() % INTERVAL_TICKS != 0 || !level.isDarkOutside()
                || !level.getGameRules().get(GameRules.SPAWN_MOBS)) {
            return;
        }
        HalloweenEvent.current().ifPresent(event -> {
            for (ServerPlayer player : level.players()) {
                if (!player.isSpectator() && level.getRandom().nextFloat() < event.spawnChance()) {
                    trySpawnNear(level, player, event);
                }
            }
        });
    }

    private static void trySpawnNear(ServerLevel level, ServerPlayer player, HalloweenEvent event) {
        int wildNearby = level.getEntitiesOfClass(BattlePumpkin.class, player.getBoundingBox().inflate(CAP_RADIUS), pumpkin -> !pumpkin.isTame()).size();
        if (wildNearby >= event.nearbyCap()) {
            return;
        }

        RandomSource random = level.getRandom();
        double angle = random.nextDouble() * Math.PI * 2.0;
        double distance = Mth.nextInt(random, MIN_DISTANCE, MAX_DISTANCE);
        int x = Mth.floor(player.getX() + Math.cos(angle) * distance);
        int z = Mth.floor(player.getZ() + Math.sin(angle) * distance);
        if (!level.hasChunkAt(new BlockPos(x, player.getBlockY(), z))) {
            return;
        }
        BlockPos pos = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
        if (!level.getBlockState(pos.below()).is(BlockTags.ANIMALS_SPAWNABLE_ON)) {
            return;
        }

        BattlePumpkin pumpkin = BattlePumpkinsMod.BATTLE_PUMPKIN.get().create(level, EntitySpawnReason.NATURAL);
        if (pumpkin == null) {
            return;
        }
        pumpkin.snapTo(x + 0.5, pos.getY(), z + 0.5, random.nextFloat() * 360.0F, 0.0F);
        if (!level.noCollision(pumpkin)) {
            return;
        }
        if (event == HalloweenEvent.LANTERN_NIGHT) {
            pumpkin.dressForLanternNight(random, LanternFaces.get(level.getServer().getResourceManager()));
        }
        level.addFreshEntity(pumpkin);
    }
}
