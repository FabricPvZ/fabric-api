package cn.enaium.fabricpvz.api.zombie.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import cn.enaium.fabricpvz.api.zombie.ZombieHooks;
import cn.enaium.fabricpvz.portable.lawn.Zombie;

/**
 * Dispatches {@code Zombie}'s per-type setup and behaviour to {@link ZombieHooks}.
 *
 * <p>The game used to branch on {@code ZombieType.CUSTOM_ZOMBIE_FIRST} at each of these sites and call
 * the content registry; the registry moved into this module, so the branches became injections. Every
 * hook answers "do not interfere" for a zombie type nobody registered, which leaves the game on its
 * own code path for upstream zombies.
 */
@Mixin(Zombie.class)
public abstract class ZombieMixin {

    /**
     * Applies the registered definition at the end of {@code Zombie::ZombieInitialize}
     * (Zombie.cpp:143), where the game used to call {@code ZombieTypeRegistry.applyToZombie(this)}:
     * the plain reanimation, the optional attached head, the health/armour numbers and the behaviour's
     * {@code onSpawned} callback.
     */
    @Inject(method = "zombieInitialize(IIZLcn/enaium/fabricpvz/portable/lawn/Zombie;I)V", at = @At("RETURN"))
    private void fabricpvz$onSpawned(int theRow, int theType, boolean theVariant, Zombie theParentZombie,
            int theFromWave, CallbackInfo ci) {
        ZombieHooks.onSpawned((Zombie) (Object) this);
    }

    /**
     * Ticks the registered behaviour, replacing the {@code ZombieTypeRegistry.updateZombie(this)} call
     * the game used to make at the end of {@code Zombie::Update}'s update block (Zombie.cpp:908). The
     * injection point is the {@code updateReanim()} call, so the tick runs exactly where it did before.
     */
    @Inject(method = "update()V", at = @At(value = "INVOKE",
            target = "Lcn/enaium/fabricpvz/portable/lawn/Zombie;updateReanim()V", shift = At.Shift.AFTER))
    private void fabricpvz$onTick(CallbackInfo ci) {
        ZombieHooks.onTick((Zombie) (Object) this);
    }

    /** Replaces {@code Zombie::ZombieTypeCanGoInPool} (Zombie.cpp:8277) for a registered type. */
    @Inject(method = "zombieTypeCanGoInPool(I)Z", at = @At("HEAD"), cancellable = true)
    private static void fabricpvz$canGoInPool(int theZombieType, CallbackInfoReturnable<Boolean> cir) {
        Boolean aResult = ZombieHooks.canGoInPool(theZombieType);
        if (aResult != null) {
            cir.setReturnValue(aResult);
        }
    }

    /**
     * Applies the mind-control rule at the end of {@code Zombie::StartMindControlled}
     * (Zombie.cpp:6984): the game has just set {@code mMindControlled} and unwound the zombie's
     * attachment to whoever controlled it, and {@link ZombieHooks#onMindControlled(Zombie)} then gives
     * the zombie the health, scale and cleared chill a mod registered - nothing at all until one did.
     */
    @Inject(method = "startMindControlled()V", at = @At("RETURN"))
    private void fabricpvz$onMindControlled(CallbackInfo ci) {
        ZombieHooks.onMindControlled((Zombie) (Object) this);
    }

    /**
     * The yeti's branch of {@code Zombie::UpdateActions} (Zombie.cpp:4428): the game calls
     * {@code Zombie::UpdateYeti} for a yeti, and {@link ZombieHooks#updateYeti(Zombie)} drops that call
     * where a mod moved it to the flag zombie (ZOMBIE_YETI(19) becomes ZOMBIE_FLAG(1) at the comparison
     * 95 patched in the dispatch).
     */
    @Redirect(method = "updateActions()V", at = @At(value = "INVOKE",
            target = "Lcn/enaium/fabricpvz/portable/lawn/Zombie;updateYeti()V"))
    private void fabricpvz$yetiDispatch(Zombie theZombie) {
        ZombieHooks.updateYeti(theZombie);
    }

    /**
     * The other end of the same dispatch: where a mod moved the yeti's per-type update to the flag
     * zombie, the dispatch has no branch for it, so it runs here, after the game's own.
     */
    @Inject(method = "updateActions()V", at = @At("TAIL"))
    private void fabricpvz$yetiForFlag(CallbackInfo ci) {
        ZombieHooks.updateYetiForFlag((Zombie) (Object) this);
    }
}
