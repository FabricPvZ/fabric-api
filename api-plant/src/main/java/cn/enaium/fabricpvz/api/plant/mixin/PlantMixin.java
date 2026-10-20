package cn.enaium.fabricpvz.api.plant.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.objectweb.asm.Opcodes;

import cn.enaium.fabricpvz.api.plant.PlantHooks;
import cn.enaium.fabricpvz.portable.lawn.Plant;
import cn.enaium.fabricpvz.portable.lawn.Zombie;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

/**
 * Dispatches {@code Plant}'s per-seed reads to {@link PlantHooks}.
 *
 * <p>The game used to branch on {@code SeedType.CUSTOM_SEED_FIRST} at each of these sites and call
 * the content registry; the registry moved into this module, so the branches became injections. Every
 * hook answers "do not interfere" for a seed type nobody registered, which leaves the game on its own
 * code path for upstream plants.
 */
@Mixin(Plant.class)
public abstract class PlantMixin {

    /**
     * Skips {@code Plant::Draw} for a registered plant that has no art of its own: one that declared
     * itself invisible, or one with no packet image and no body reanimation (the game's static-image
     * branch would dereference a {@code null} image). The almanac and the board draw plants through
     * this method; a registered plant with a reanimation keeps the game's drawing.
     */
    @Inject(method = "draw(Lcn/enaium/fabricpvz/portable/sexy/graphics/Graphics;)V", at = @At("HEAD"),
            cancellable = true)
    private void fabricpvz$draw(Graphics g, CallbackInfo ci) {
        if (PlantHooks.suppressesDraw((Plant) (Object) this)) {
            ci.cancel();
        }
    }

    /**
     * Replaces {@code Plant::GetImage} (Plant.cpp:3803) for a registered plant with the packet art the
     * mod pinned: the game's own image table has no row for a mod seed, so every seed-art path
     * ({@code Plant::DrawSeedType}, {@code CursorObject::Draw}, the packet preview) would read
     * {@code null}. Upstream seed types keep the game's own table.
     */
    @Inject(method = "getImage(I)Lcn/enaium/fabricpvz/portable/sexy/graphics/Image;", at = @At("HEAD"),
            cancellable = true)
    private static void fabricpvz$getImage(int theSeedType, CallbackInfoReturnable<Image> cir) {
        Image aImage = PlantHooks.image(theSeedType);
        if (aImage != null) {
            cir.setReturnValue(aImage);
        }
    }

    /**
     * Ticks the registered behaviour, replacing the {@code PlantTypeRegistry.updatePlant(this)} call
     * the game used to make at the end of {@code Plant::Update}'s update block (Plant.cpp:2856). The
     * injection point is the {@code updateReanim()} call inside {@code if (doUpdate)}, so the tick
     * runs exactly where it did before.
     */
    @Inject(method = "update()V", at = @At(value = "INVOKE",
            target = "Lcn/enaium/fabricpvz/portable/lawn/Plant;updateReanim()V", shift = At.Shift.AFTER))
    private void fabricpvz$onTick(CallbackInfo ci) {
        PlantHooks.onTick((Plant) (Object) this);
    }

    /**
     * Fires a mod plant's projectile before {@code Plant::Fire}'s per-seed switch (Plant.cpp:4476),
     * which the plant replaced with {@code mProjectileType}. A plant with no projectile is left to
     * that switch, i.e. it throws "no projectile" like an unknown upstream seed type.
     */
    @Inject(method = "fire(Lcn/enaium/fabricpvz/portable/lawn/Zombie;II)V", at = @At("HEAD"), cancellable = true)
    private void fabricpvz$fire(Zombie theTargetZombie, int theRow, int thePlantWeapon, CallbackInfo ci) {
        if (PlantHooks.fire((Plant) (Object) this, theTargetZombie, theRow, thePlantWeapon)) {
            ci.cancel();
        }
    }

    /** Replaces {@code Plant::GetPlantRect} (Plant.cpp:5136) with the behaviour's rectangle. */
    @Inject(method = "getPlantRect()Lcn/enaium/fabricpvz/portable/sexy/Rect;", at = @At("HEAD"), cancellable = true)
    private void fabricpvz$plantRect(CallbackInfoReturnable<Rect> cir) {
        Rect aRect = PlantHooks.plantRect((Plant) (Object) this);
        if (aRect != null) {
            cir.setReturnValue(aRect);
        }
    }

    /** Replaces {@code Plant::GetPlantAttackRect} (Plant.cpp:5160) with the behaviour's rectangle. */
    @Inject(method = "getPlantAttackRect(I)Lcn/enaium/fabricpvz/portable/sexy/Rect;", at = @At("HEAD"),
            cancellable = true)
    private void fabricpvz$plantAttackRect(int thePlantWeapon, CallbackInfoReturnable<Rect> cir) {
        Rect aRect = PlantHooks.plantAttackRect((Plant) (Object) this, thePlantWeapon);
        if (aRect != null) {
            cir.setReturnValue(aRect);
        }
    }

    /**
     * Applies the registered health and the {@code onPlanted} callback at the end of
     * {@code Plant::PlantInitialize} (Plant.cpp:111), where the game used to call
     * {@code PlantTypeRegistry.applyToPlant(this)}.
     */
    @Inject(method = "plantInitialize(IIII)V", at = @At("RETURN"))
    private void fabricpvz$onPlanted(int theGridX, int theGridY, int theSeedType, int theImitaterType,
            CallbackInfo ci) {
        PlantHooks.onPlanted((Plant) (Object) this);
    }

    /** Replaces {@code Plant::IsNocturnal} (Plant.cpp:5080) for a registered seed type. */
    @Inject(method = "isNocturnal(I)Z", at = @At("HEAD"), cancellable = true)
    private static void fabricpvz$isNocturnal(int theSeedType, CallbackInfoReturnable<Boolean> cir) {
        Boolean aResult = PlantHooks.isNocturnal(theSeedType);
        if (aResult != null) {
            cir.setReturnValue(aResult);
        }
    }

    /** Replaces {@code Plant::IsFungus} (Plant.cpp:5095) for a registered seed type. */
    @Inject(method = "isFungus(I)Z", at = @At("HEAD"), cancellable = true)
    private static void fabricpvz$isFungus(int theSeedType, CallbackInfoReturnable<Boolean> cir) {
        Boolean aResult = PlantHooks.isFungus(theSeedType);
        if (aResult != null) {
            cir.setReturnValue(aResult);
        }
    }

    /** Replaces {@code Plant::IsAquatic} (Plant.cpp:5109) for a registered seed type. */
    @Inject(method = "isAquatic(I)Z", at = @At("HEAD"), cancellable = true)
    private static void fabricpvz$isAquatic(int theSeedType, CallbackInfoReturnable<Boolean> cir) {
        Boolean aResult = PlantHooks.isAquatic(theSeedType);
        if (aResult != null) {
            cir.setReturnValue(aResult);
        }
    }

    /** Replaces {@code Plant::IsFlying} (Plant.cpp:5117) for a registered seed type. */
    @Inject(method = "isFlying(I)Z", at = @At("HEAD"), cancellable = true)
    private static void fabricpvz$isFlying(int theSeedType, CallbackInfoReturnable<Boolean> cir) {
        Boolean aResult = PlantHooks.isFlying(theSeedType);
        if (aResult != null) {
            cir.setReturnValue(aResult);
        }
    }

    /** Replaces {@code Plant::IsUpgrade} (Plant.cpp:5124) for a registered seed type. */
    @Inject(method = "isUpgrade(I)Z", at = @At("HEAD"), cancellable = true)
    private static void fabricpvz$isUpgrade(int theSeedType, CallbackInfoReturnable<Boolean> cir) {
        Boolean aResult = PlantHooks.isUpgrade(theSeedType);
        if (aResult != null) {
            cir.setReturnValue(aResult);
        }
    }

    /**
     * Replaces {@code Plant::IsUpgradableTo} (Plant.cpp:2608) for a seed type whose upgrade a mod
     * declared with {@code PlantType.Builder#upgradeFrom}. The game's chain only knows its hard-coded
     * retail pairs, so a registered upgrade would never be plantable on anything: this is what makes
     * {@code Board::CanPlantAt} answer {@code PLANTING_OK} and the planting branch of
     * {@code BoardInput} replace the plant underneath.
     */
    @Inject(method = "isUpgradableTo(I)Z", at = @At("HEAD"), cancellable = true)
    private void fabricpvz$isUpgradableTo(int theUpgradedType, CallbackInfoReturnable<Boolean> cir) {
        Boolean aResult = PlantHooks.isUpgradableTo(((Plant) (Object) this).mSeedType, theUpgradedType);
        if (aResult != null) {
            cir.setReturnValue(aResult);
        }
    }

    /** Replaces {@code Plant::GetNameString} (Plant.cpp:5039) for a registered seed type. */
    @Inject(method = "getNameString(II)Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
    private static void fabricpvz$getNameString(int theSeedType, int theImitaterType, CallbackInfoReturnable<String> cir) {
        String aName = PlantHooks.name(theSeedType);
        if (aName != null) {
            cir.setReturnValue(aName);
        }
    }

    /** Replaces {@code Plant::GetToolTip} (Plant.cpp:5051) for a registered seed type. */
    @Inject(method = "getToolTip(I)Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
    private static void fabricpvz$getToolTip(int theSeedType, CallbackInfoReturnable<String> cir) {
        String aTooltip = PlantHooks.tooltip(theSeedType);
        if (aTooltip != null) {
            cir.setReturnValue(aTooltip);
        }
    }

    /**
     * Reproduces the retail multi-shot window for a registered plant with a declared burst:
     * {@code Plant::FindTargetAndFire} (Plant.cpp:734) picks the repeater/gatling cadence only for its
     * own seed types, so a registered shooting plant would get the single-shot 35/35 window. Only runs
     * when the plant really fired, so a plant that found no target keeps its idle timing.
     */
    @Inject(method = "findTargetAndFire(II)Z", at = @At("RETURN"))
    private void fabricpvz$burstWindow(int theRow, int thePlantWeapon, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            PlantHooks.applyBurstWindow((Plant) (Object) this);
        }
    }

    /** Adds the repeater-style second shot to {@code Plant::UpdateShooter} (Plant.cpp:945). */
    @Inject(method = "updateShooter()V", at = @At("TAIL"))
    private void fabricpvz$burstShooter(CallbackInfo ci) {
        PlantHooks.updateBurstShooter((Plant) (Object) this);
    }

    /**
     * Adds the gatling-style extra shots to {@code Plant::UpdateShooting} (Plant.cpp:2893). The point
     * is {@code RETURN}, not {@code TAIL}: that method returns early - most importantly on the
     * {@code mShootingCounter != 0} path the extra shots are fired on - while TAIL would only reach
     * the method's very last return.
     */
    @Inject(method = "updateShooting()V", at = @At("RETURN"))
    private void fabricpvz$burstShooting(CallbackInfo ci) {
        PlantHooks.updateBurstShooting((Plant) (Object) this);
    }

    /**
     * Replaces the {@code mOnBungeeState} {@code Plant::NotOnGround} (Plant.cpp:2877) tests against
     * {@code RISING_WITH_BUNGEE} with the state a mod registered
     * ({@link cn.enaium.fabricpvz.api.plant.PlantOverrides#notOnGroundState(int)}): the game's own
     * bungee state for every plant until a mod sets one, and, once it did, the bungee state its own
     * comparison is fooled by for a plant in the registered state and "not rising" for everything else.
     * {@link PlantHooks#notOnGroundBungeeState(Plant)} documents both halves.
     *
     * <p>The method reads the field once, so the redirect needs no ordinal.
     */
    @Redirect(method = "notOnGround()Z",
            at = @At(value = "FIELD", target = "Lcn/enaium/fabricpvz/portable/lawn/Plant;mOnBungeeState:I",
                    opcode = Opcodes.GETFIELD))
    private int fabricpvz$notOnGroundBungee(Plant thePlant) {
        return PlantHooks.notOnGroundBungeeState(thePlant);
    }
}
