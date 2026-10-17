package cn.enaium.fabricpvz.api.zombie.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import cn.enaium.fabricpvz.api.zombie.ZombieOverrides;
import cn.enaium.fabricpvz.portable.lawn.Zombie;

/**
 * Applies the {@link ZombieOverrides} a mod registered to the zombies the game spawns, at the two
 * points of {@code Zombie::ZombieInitialize} (Zombie.cpp:143) the game reads its per-type numbers at.
 *
 * <p>The type replacement comes first and is applied to the method's <em>type argument</em>, so the
 * whole initialisation - the per-type switch, the art, the animation, the health/armour numbers and
 * everything the game derives from them - runs as if the game had been asked to spawn the replacement
 * type in the first place. The numbers are applied at the end, on top of whatever the game set, which
 * is also what makes an override win over a {@code ZombieRegistry} definition ({@link ZombieMixin} is
 * listed before this Mixin in the configuration, so it dispatches the definition first).
 *
 * <p>Both injections answer "do not interfere" for a zombie type nobody overrode, so an unmodded game
 * runs exactly its own code.
 */
@Mixin(Zombie.class)
public abstract class ZombieOverridesMixin {

    /**
     * Replaces the zombie type the game is about to spawn, the second {@code int} argument of
     * {@code Zombie::ZombieInitialize} ({@code theRow} is the first). Changing the argument rather than
     * the field keeps every later read - the type stored in {@code mZombieType}, the {@code switch}
     * over it, the definition row, the render setup - on the replacement type, which is what
     * {@link cn.enaium.fabricpvz.api.zombie.ZombieOverride#spawnsAs(int)} promises.
     */
    @ModifyVariable(method = "zombieInitialize(IIZLcn/enaium/fabricpvz/portable/lawn/Zombie;I)V",
            at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int fabricpvz$overrideSpawnType(int theZombieType) {
        return ZombieOverrides.remapType(theZombieType);
    }

    /**
     * Applies the registered overrides at the end of {@code Zombie::ZombieInitialize}, after the
     * game's per-type setup (including the early return of the Bungee case, which returns from the
     * middle of the method and so has to be covered as well) set the fields the overrides replace.
     */
    @Inject(method = "zombieInitialize(IIZLcn/enaium/fabricpvz/portable/lawn/Zombie;I)V", at = @At("RETURN"))
    private void fabricpvz$overrideNumbers(int theRow, int theType, boolean theVariant, Zombie theParentZombie,
            int theFromWave, CallbackInfo ci) {
        ZombieOverrides.apply((Zombie) (Object) this);
    }
}
