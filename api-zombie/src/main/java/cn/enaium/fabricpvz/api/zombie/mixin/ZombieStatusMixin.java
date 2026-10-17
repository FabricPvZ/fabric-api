package cn.enaium.fabricpvz.api.zombie.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import cn.enaium.fabricpvz.api.zombie.ZombieHooks;
import cn.enaium.fabricpvz.portable.lawn.ZombieStatus;

/**
 * Dispatches the two zombie rules that are rewrites of a whole method rather than values -
 * {@code Zombie::ApplyBurn} and {@code Zombie::RemoveColdEffects} - to {@link ZombieHooks}.
 *
 * <p>Both hooks answer {@code false} until a mod registers the matching rule, and a {@code false}
 * answer leaves the game's own method untouched: the injection is at the head and only cancels when the
 * hook says it took over. That is what keeps an unmodded game on its own code for these two, which is
 * the same contract every other injection of this module follows.
 */
@Mixin(ZombieStatus.class)
public abstract class ZombieStatusMixin {

    /**
     * Hands {@code Zombie::ApplyBurn} (Zombie.cpp:8661) to the burn clamp of
     * {@link cn.enaium.fabricpvz.api.zombie.ZombieOverrides#burnImmunity(int, int...)}: a zombie the
     * clamp covers takes the flat 1800 damage and never reaches the burn.
     */
    @Inject(method = "applyBurn(Lcn/enaium/fabricpvz/portable/lawn/Zombie;)V", at = @At("HEAD"),
            cancellable = true)
    private static void fabricpvz$burnImmunity(cn.enaium.fabricpvz.portable.lawn.Zombie theZombie,
            CallbackInfo ci) {
        if (ZombieHooks.applyBurnImmunity(theZombie)) {
            ci.cancel();
        }
    }

    /**
     * Hands {@code Zombie::RemoveColdEffects} (Zombie.cpp:8647) to the rewrite of
     * {@link cn.enaium.fabricpvz.api.zombie.ZombieOverrides#coldRemovalChilledCounter(int)}: the chill
     * counter is written with the registered value instead of being cleared.
     */
    @Inject(method = "removeColdEffects(Lcn/enaium/fabricpvz/portable/lawn/Zombie;)V", at = @At("HEAD"),
            cancellable = true)
    private static void fabricpvz$coldRemoval(cn.enaium.fabricpvz.portable.lawn.Zombie theZombie,
            CallbackInfo ci) {
        if (ZombieHooks.removeColdEffects(theZombie)) {
            ci.cancel();
        }
    }
}
