package com.iamkaf.battlepumpkins.pumpkin;

import com.iamkaf.battlepumpkins.network.BattlePumpkinsNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LeapAtTargetGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShearsItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

import java.util.EnumSet;
import java.util.List;

/**
 * A pumpkin that hops around and fights for its owner.
 * <p>
 * Wild pumpkins wander at night, flee from players who are not sneaking, and fall asleep at sunrise looking exactly
 * like a pumpkin block. Shears carve a wild one and make it yours. An owned pumpkin is never killed by ordinary damage:
 * at 0 health its light goes out and it falls asleep until its owner feeds it seeds.
 */
public class BattlePumpkin extends TamableAnimal {
    private static final List<EntityDataAccessor<Long>> FACE = List.of(
            SynchedEntityData.defineId(BattlePumpkin.class, EntityDataSerializers.LONG),
            SynchedEntityData.defineId(BattlePumpkin.class, EntityDataSerializers.LONG),
            SynchedEntityData.defineId(BattlePumpkin.class, EntityDataSerializers.LONG),
            SynchedEntityData.defineId(BattlePumpkin.class, EntityDataSerializers.LONG)
    );
    private static final EntityDataAccessor<Byte> LIGHT = SynchedEntityData.defineId(BattlePumpkin.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> DORMANT = SynchedEntityData.defineId(BattlePumpkin.class, EntityDataSerializers.BOOLEAN);

    private static final double WILD_HEALTH = 12.0;
    private static final double TAME_HEALTH = 40.0;
    /** Players who are not sneaking wake a sleeping wild pumpkin from this far away. */
    private static final double WAKE_DISTANCE = 6.0;
    /** A wild pumpkin only falls asleep with no player this close. */
    private static final double SLEEP_DISTANCE = 16.0;
    private static final float SEED_HEALING = 2.0F;
    private static final float WAKE_HEALTH = 10.0F;

    public BattlePumpkin(EntityType<? extends BattlePumpkin> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Animal.createAnimalAttributes()
                .add(Attributes.MAX_HEALTH, WILD_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.ATTACK_DAMAGE, 4.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        FACE.forEach(word -> builder.define(word, 0L));
        builder.define(LIGHT, (byte) Light.NONE.ordinal());
        builder.define(DORMANT, false);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(1, new DormantGoal());
        goalSelector.addGoal(2, new SitWhenOrderedToGoal(this));
        goalSelector.addGoal(3, new AvoidEntityGoal<>(this, Player.class, 8.0F, 1.2, 1.5,
                player -> !isTame() && getTarget() == null && !player.isDiscrete()));
        goalSelector.addGoal(4, new LeapAtTargetGoal(this, 0.4F));
        goalSelector.addGoal(5, new MeleeAttackGoal(this, 1.2, true));
        goalSelector.addGoal(6, new FollowOwnerGoal(this, 1.0, 10.0F, 2.0F));
        goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
        goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        targetSelector.addGoal(3, new HurtByTargetGoal(this));
    }

    public Face face() {
        return new Face(entityData.get(FACE.get(0)), entityData.get(FACE.get(1)), entityData.get(FACE.get(2)), entityData.get(FACE.get(3)));
    }

    public void setFace(Face face) {
        long[] words = face.words();
        for (int i = 0; i < words.length; i++) {
            entityData.set(FACE.get(i), words[i]);
        }
    }

    public Light light() {
        return Light.values()[entityData.get(LIGHT)];
    }

    private void setLight(Light light) {
        entityData.set(LIGHT, (byte) light.ordinal());
    }

    /** Asleep in its disguise pose: a wild pumpkin resting through the day, or an owned one knocked out. */
    public boolean isDormant() {
        return entityData.get(DORMANT);
    }

    private void setDormant(boolean dormant) {
        entityData.set(DORMANT, dormant);
    }

    public boolean isKnockedOut() {
        return isTame() && isDormant();
    }

    /** Whether the face glows right now. A sleeping pumpkin's light is out. */
    public boolean isLit() {
        return light().glows() && !isDormant();
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!isTame()) {
            if (!(stack.getItem() instanceof ShearsItem)) {
                return super.mobInteract(player, hand);
            }
            if (!level().isClientSide()) {
                catchBy(player, stack, hand);
                openCarveScreen(player);
            }
            return InteractionResult.SUCCESS;
        }

        if (!isOwnedBy(player)) {
            return InteractionResult.PASS;
        }

        if (isKnockedOut()) {
            if (!stack.is(Items.PUMPKIN_SEEDS)) {
                return InteractionResult.PASS;
            }
            if (!level().isClientSide()) {
                stack.consume(1, player);
                setDormant(false);
                setHealth(Math.max(getHealth(), WAKE_HEALTH));
                playSound(SoundEvents.FLINTANDSTEEL_USE, 1.0F, 1.0F);
            }
            return InteractionResult.SUCCESS;
        }

        Light offered = Light.fromItem(stack).orElse(light());
        if (offered != light()) {
            if (!level().isClientSide()) {
                dropLight();
                setLight(offered);
                stack.consume(1, player);
                playSound(SoundEvents.FLINTANDSTEEL_USE, 1.0F, 1.0F);
            }
            return InteractionResult.SUCCESS;
        }

        if (stack.is(Items.PUMPKIN_SEEDS) && getHealth() < getMaxHealth()) {
            if (!level().isClientSide()) {
                stack.consume(1, player);
                heal(SEED_HEALING);
                playSound(SoundEvents.GENERIC_EAT.value(), 1.0F, 1.0F);
            }
            return InteractionResult.SUCCESS;
        }

        if (player.isSecondaryUseActive()) {
            openCarveScreen(player);
            return InteractionResult.SUCCESS;
        }

        InteractionResult result = super.mobInteract(player, hand);
        if (result.consumesAction()) {
            return result;
        }
        setOrderedToSit(!isOrderedToSit());
        jumping = false;
        navigation.stop();
        setTarget(null);
        return InteractionResult.SUCCESS.withoutItem();
    }

    /** The server opens the screen, so it only appears once the server agrees the player may carve. */
    private void openCarveScreen(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            BattlePumpkinsNetwork.CHANNEL.sendToPlayer(new BattlePumpkinsNetwork.OpenCarveScreen(getId()), serverPlayer);
        }
    }

    private void catchBy(Player player, ItemStack shears, InteractionHand hand) {
        tame(player);
        if (face().isBlank()) {
            setFace(Face.VANILLA);
        }
        setDormant(false);
        setOrderedToSit(true);
        navigation.stop();
        setTarget(null);
        shears.hurtAndBreak(1, player, hand);
        level().playSound(null, this, SoundEvents.PUMPKIN_CARVE, SoundSource.NEUTRAL, 1.0F, 1.0F);
        level().broadcastEntityEvent(this, (byte) 7);
    }

    /** Applies a face carved on the screen. Only the owner may carve, from close by. */
    public void carve(Player player, Face face) {
        if (canBeEditedBy(player)) {
            setFace(face);
            playSound(SoundEvents.PUMPKIN_CARVE, 1.0F, 1.0F);
        }
    }

    public void takeOutLight(Player player) {
        if (canBeEditedBy(player) && light().glows()) {
            dropLight();
            setLight(Light.NONE);
            playSound(SoundEvents.FIRE_EXTINGUISH, 0.5F, 1.5F);
        }
    }

    private boolean canBeEditedBy(Player player) {
        return isAlive() && isOwnedBy(player) && !isKnockedOut() && distanceToSqr(player) < 64.0;
    }

    private void dropLight() {
        if (level() instanceof ServerLevel serverLevel) {
            light().item().ifPresent(item -> spawnAtLocation(serverLevel, item));
        }
    }

    /** Dresses a wild pumpkin spawned on Lantern Night: already carved, already lit. */
    public void dressForLanternNight(RandomSource random, List<Face> faces) {
        setFace(faces.isEmpty() ? Face.VANILLA : faces.get(random.nextInt(faces.size())));
        int roll = random.nextInt(20);
        setLight(roll < 5 ? Light.WITCH : roll < 8 ? Light.SOUL : Light.TORCH);
    }

    @Override
    protected void applyTamingSideEffects() {
        if (isTame()) {
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(TAME_HEALTH);
            setHealth((float) TAME_HEALTH);
        } else {
            getAttribute(Attributes.MAX_HEALTH).setBaseValue(WILD_HEALTH);
        }
    }

    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (!isTame() && tickCount % 10 == 0) {
            updateWildSleep(level);
        }
    }

    private void updateWildSleep(ServerLevel level) {
        if (isDormant()) {
            if (level.isDarkOutside() || hasPlayerNearby(level, WAKE_DISTANCE, true)) {
                setDormant(false);
            }
        } else if (level.isBrightOutside() && onGround() && getTarget() == null
                && !hasPlayerNearby(level, SLEEP_DISTANCE, false) && random.nextInt(20) == 0) {
            settle();
        }
    }

    private boolean hasPlayerNearby(ServerLevel level, double distance, boolean ignoreSneaking) {
        return level.getNearestPlayer(getX(), getY(), getZ(), distance,
                EntitySelector.NO_SPECTATORS.and(player -> !ignoreSneaking || !player.isDiscrete())) != null;
    }

    /** Lines up with the block grid and faces a cardinal direction, so a sleeping pumpkin passes for a block. */
    private void settle() {
        float yaw = Math.round(getYRot() / 90.0F) * 90.0F;
        snapTo(Mth.floor(getX()) + 0.5, getY(), Mth.floor(getZ()) + 0.5, yaw, 0.0F);
        setYHeadRot(yaw);
        setYBodyRot(yaw);
        navigation.stop();
        setDormant(true);
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (isKnockedOut() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        boolean hurt = super.hurtServer(level, source, amount);
        if (hurt && !isTame()) {
            setDormant(false);
        }
        return hurt;
    }

    @Override
    public void die(DamageSource source) {
        if (isTame() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            knockOut();
            return;
        }
        super.die(source);
    }

    private void knockOut() {
        setHealth(1.0F);
        setDormant(true);
        setOrderedToSit(false);
        setTarget(null);
        navigation.stop();
        clearFire();
    }

    @Override
    public boolean canBeSeenAsEnemy() {
        return !isKnockedOut() && super.canBeSeenAsEnemy();
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return !isTame();
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return false;
    }

    @Override
    public @Nullable AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.SLIME_HURT_SMALL;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return isTame() ? SoundEvents.FIRE_EXTINGUISH : SoundEvents.SLIME_DEATH_SMALL;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state) {
        playSound(SoundEvents.SLIME_SQUISH_SMALL, 0.15F, 1.0F);
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        long[] words = face().words();
        for (int i = 0; i < words.length; i++) {
            output.putLong("Face" + i, words[i]);
        }
        output.putString("Light", light().serializedName());
        output.putBoolean("Dormant", isDormant());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        long[] words = new long[4];
        for (int i = 0; i < words.length; i++) {
            words[i] = input.getLongOr("Face" + i, 0L);
        }
        setFace(Face.of(words));
        setLight(Light.byName(input.getStringOr("Light", Light.NONE.serializedName())));
        setDormant(input.getBooleanOr("Dormant", false));
    }

    /** Holds still and quiet while asleep, the way the sit goal does for a sitting pet. */
    private final class DormantGoal extends Goal {
        DormantGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return isDormant();
        }

        @Override
        public void start() {
            navigation.stop();
            setTarget(null);
        }
    }
}
