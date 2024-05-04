package dev.zanckor.cobblemonrider.mixin;


import com.cobblemon.mod.common.api.entity.PokemonSideDelegate;
import com.cobblemon.mod.common.api.scheduling.Schedulable;
import com.cobblemon.mod.common.entity.Poseable;
import com.cobblemon.mod.common.entity.pokemon.PokemonBehaviourFlag;
import com.cobblemon.mod.common.entity.pokemon.PokemonEntity;
import com.cobblemon.mod.common.pokemon.Pokemon;
import dev.zanckor.cobblemonrider.MCUtil;
import dev.zanckor.cobblemonrider.config.PokemonJsonObject;
import dev.zanckor.cobblemonrider.mixininterface.IPokemonStamina;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.animal.ShoulderRidingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Objects;

import static dev.zanckor.cobblemonrider.config.PokemonJsonObject.MountType.*;

@Mixin(PokemonEntity.class)
public abstract class PokemonMixin extends PathfinderMob implements Poseable, Schedulable, IPokemonStamina {
    private static final int MAX_STAMINA = 200;
    private PokemonJsonObject.PokemonConfigData passengerObject;
    private int stamina;
    private boolean wasSprinting;
    private float speedMultiplier;

    @Shadow
    public abstract Pokemon getPokemon();

    @Shadow
    public abstract void checkDespawn();

    @Shadow
    public abstract @NotNull PokemonSideDelegate getDelegate();

    @Shadow
    public abstract void travel(@NotNull Vec3 movementInput);


    protected PokemonMixin(EntityType<? extends ShoulderRidingEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "<init>(Lnet/minecraft/world/level/Level;Lcom/cobblemon/mod/common/pokemon/Pokemon;Lnet/minecraft/world/entity/EntityType;)V", at = @At("RETURN"))
    private void init(Level level, Pokemon pokemon, EntityType<? extends PokemonEntity> entityType, CallbackInfo ci) {
        this.setMaxUpStep(1);
        this.stamina = MAX_STAMINA;
    }

    @Inject(method = "tick", at = @At("TAIL"))
    public void tick(CallbackInfo ci) {
        if (getControllingPassenger() != null) {
            movementHandler();
            dismountHandler();

            rotateBody();
        }
    }

    @Override
    protected void positionRider(@NotNull Entity entity, @NotNull MoveFunction moveFunction) {
        if (this.hasPassenger(entity) && getPassengerObject() != null && getControllingPassenger() != null) {
            int passengerIndex = getPassengers().indexOf(entity) - 1;
            boolean isControllingPassenger = getControllingPassenger().equals(entity);
            ArrayList<Float> offSet = isControllingPassenger ? getPassengerObject().getRidingOffSet() : getPassengerObject().getPassengersOffSet().get(passengerIndex);

            setYBodyRot(getControllingPassenger().getYRot());

            float xOffset = offSet.get(2);
            float yOffset = offSet.get(1);
            float zOffset = offSet.get(0);

            Vec3 vec3 = (new Vec3(xOffset, yOffset, zOffset)).yRot(-getControllingPassenger().getYRot() * 0.017453292F);

            moveFunction.accept(entity, this.getX() + vec3.x, this.getY() + vec3.y, this.getZ() + vec3.z);
        }
    }

    private void movementHandler() {
        Player passenger = (Player) getControllingPassenger();

        if (getPassengerObject() != null && passenger != null) {
            sprintHandler();
            travelHandler();
            jumpHandler();

            if (getPassengerObject().getMountTypes().contains(SWIM)) {
                swimmingHandler();
            }

            if (getPassengerObject().getMountTypes().contains(LAVA_SWIM)) {
                lavaSwimmingHandler();
            }

            if (getPassengerObject().getMountTypes().contains(FLY)) {
                flyingHandler();
            }

            passenger.getPersistentData().putBoolean("press_space", false);
            passenger.getPersistentData().putBoolean("press_sprint", false);
            passenger.getPersistentData().putBoolean("pokemon_dismount", false);
            passenger.getPersistentData().putBoolean("press_shift", false);
            passenger.setShiftKeyDown(false);
        }
    }

    private void travelHandler() {
        if (getControllingPassenger() != null && canMove()) {
            float x = (float) getControllingPassenger().getDeltaMovement().x * 10;
            float z = (float) getControllingPassenger().getDeltaMovement().z * 10;
            setDeltaMovement(x * speedMultiplier, getDeltaMovement().y, z * speedMultiplier);

            travel(new Vec3(x, 0, z));
        }
    }

    private void rotateBody() {
        setRot(Objects.requireNonNull(getFirstPassenger()).getYRot(), 0);
    }

    private void sprintHandler() {
        if (isSprintPressed() && canSprint()) {
            speedMultiplier = getPassengerObject().getSpeedModifier() * 2.5f;
            decreaseStamina(1);
            wasSprinting = true;
        } else {
            speedMultiplier = getPassengerObject().getSpeedModifier();
            increaseStamina(1);
            wasSprinting = false;
        }
    }

    private void jumpHandler() {
        if (getPassengerObject().getMountTypes().contains(WALK) && onGround()
                && isSpacePressed() && onGround()) {

            jumpFromGround();
        }
    }

    private void swimmingHandler() {
        if (isInWater()) {
            double waterEmergeSpeed = isSpacePressed() ? 0.5 : isShiftPressed() ? -0.25 : 0.005;

            setDeltaMovement(getDeltaMovement().x, waterEmergeSpeed, getDeltaMovement().z);

            if (getDistanceToSurface(this) <= 0.5 && isShiftPressed()) {
                moveTo(getX(), getY(), getZ());
            }

            for (Entity passenger : getPassengers()) {
                setAirSupply(getMaxAirSupply());
                passenger.setAirSupply(passenger.getMaxAirSupply());
            }
        }
    }

    private void lavaSwimmingHandler() {
        if (getControllingPassenger() != null && isInLava()) {
            double lavaEmergeSpeed = isSpacePressed() ? 0.5 : isShiftPressed() ? -0.25 : 0.005;

            setDeltaMovement(getDeltaMovement().x, lavaEmergeSpeed, getDeltaMovement().z);

            if (getDistanceToSurface(this) <= 0.5 && isShiftPressed()) {
                moveTo(getX(), getY(), getZ());
            }
        }
    }

    private void flyingHandler() {
        if (getControllingPassenger() == null) return;
        boolean increaseAltitude = isSpacePressed();
        boolean decreaseAltitude = isShiftPressed();


        if (!onGround() || increaseAltitude) {
            double altitudeIncreaseValue = increaseAltitude ? 0.3 : decreaseAltitude ? -0.3 : 0.005;

            setDeltaMovement(getDeltaMovement().x, altitudeIncreaseValue, getDeltaMovement().z);
        }

        if (getPokemon().getEntity() != null) {
            getPokemon().getEntity().setBehaviourFlag(PokemonBehaviourFlag.FLYING, !onGround());
        }
    }

    private float getDistanceToSurface(Entity entity) {
        double yPos = entity.getY();
        double surfaceYPos = entity.level().getHeight(Heightmap.Types.WORLD_SURFACE, (int) (entity.getX() - entity.getEyeHeight()), (int) entity.getZ());

        return (float) (surfaceYPos - yPos);
    }

    private void dismountHandler() {
        Player passenger;
        if (!isAlive() || !isAddedToWorld() || isRemoved()) {
            ejectPassengers();
        }

        if ((passenger = (Player) getControllingPassenger()) != null && checkShouldDismount()) {
            passenger.getPersistentData().putBoolean("press_space", false);
            passenger.getPersistentData().putBoolean("press_sprint", false);
            passenger.getPersistentData().putBoolean("pokemon_dismount", false);
            passenger.getPersistentData().putBoolean("press_shift", false);

            passenger.stopRiding();
            ejectPassengers();
        }
    }

    @Inject(method = "causeFallDamage", at = @At("HEAD"), cancellable = true)
    public void causeFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource, CallbackInfoReturnable<Boolean> cir) {
        if (getControllingPassenger() != null) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    public void hurt(DamageSource source, float damage, CallbackInfoReturnable<Boolean> cir) {
        if (getControllingPassenger() != null && getPassengerObject() != null && getPassengerObject().getMountTypes().contains(LAVA_SWIM)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "mobInteract", at = @At("HEAD"))
    public void mobInteract(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        // On player interaction, if the player is not already riding the entity, add the player as a passenger
        if (canAddPassenger(player) && player.getMainHandItem().isEmpty() && getPassengerObject() != null) {
            this.setMaxUpStep(2.5F);

            if (Objects.equals(getPokemon().getOwnerPlayer(), player) || getControllingPassenger() != null) {
                player.startRiding(this);

                player.getPersistentData().putBoolean("press_space", false);
                player.getPersistentData().putBoolean("press_sprint", false);
                player.getPersistentData().putBoolean("pokemon_dismount", false);
            }
        }
    }

    @Override
    public boolean displayFireAnimation() {
        if (getPassengerObject() != null && getPassengerObject().getMountTypes().contains(LAVA_SWIM)) {
            return false;
        } else {
            return super.displayFireAnimation();
        }
    }

    @Override
    protected boolean canAddPassenger(@NotNull Entity entity) {
        int maxPassengers = getPassengerObject() != null ? getPassengerObject().getPassengersOffSet().size() + 1 : 0;

        return getPassengers().size() < maxPassengers;
    }

    private PokemonJsonObject.PokemonConfigData getPassengerObject() {
        if (passengerObject == null) {
            passengerObject = MCUtil.getPassengerObject(getPokemon().getSpecies().getName(), getPokemon().getForm().getName());
        }

        return passengerObject;
    }

    private boolean canMove() {
        return (getPassengerObject().getMountTypes().contains(SWIM) && isInWater())
                || (getPassengerObject().getMountTypes().contains(FLY) && !onGround())
                || (getPassengerObject().getMountTypes().contains(WALK));
    }

    @Override
    public boolean canSprint() {
        return isSprintPressed() && ((wasSprinting && getStamina() > 0) || (!wasSprinting && getStamina() > MAX_STAMINA * 0.2F));
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return getPassengers().isEmpty() ? null : (LivingEntity) getPassengers().get(0);
    }

    @Override
    protected float getJumpPower() {
        return 0.6f * this.getBlockJumpFactor() + this.getJumpBoostPower();
    }

    @Override
    public int getStamina() {
        return stamina;
    }

    @Override
    public int getMaxStamina() {
        return MAX_STAMINA;
    }

    @Override
    public void setStamina(int stamina) {
        this.stamina = stamina;
    }

    @Override
    public void increaseStamina(int amount) {
        setStamina(Math.min(getStamina() + amount, MAX_STAMINA));
    }

    @Override
    public void decreaseStamina(int amount) {
        setStamina(Math.max(getStamina() - amount, 0));
    }

    public boolean checkShouldDismount() {
        return ((isPokemonDismountPressed()) || (getPassengers().isEmpty()) ||
                (getPassengerObject() != null && !getPassengerObject().getMountTypes().contains(SWIM) && isInWater()));
    }

    private boolean isSpacePressed() {
        return getControllingPassenger() != null && getControllingPassenger().getPersistentData().contains("press_space") && getControllingPassenger().getPersistentData().getBoolean("press_space");
    }

    private boolean isSprintPressed() {
        return getControllingPassenger() != null && getControllingPassenger().getPersistentData().contains("press_sprint") && getControllingPassenger().getPersistentData().getBoolean("press_sprint");
    }

    private boolean isShiftPressed() {
        return getControllingPassenger() != null && getControllingPassenger().getPersistentData().contains("press_shift") && getControllingPassenger().getPersistentData().getBoolean("press_shift");
    }

    private boolean isPokemonDismountPressed() {
        return getControllingPassenger() != null && getControllingPassenger().getPersistentData().contains("pokemon_dismount") && getControllingPassenger().getPersistentData().getBoolean("pokemon_dismount");
    }
}