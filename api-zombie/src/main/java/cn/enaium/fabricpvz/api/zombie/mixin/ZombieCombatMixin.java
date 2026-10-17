package cn.enaium.fabricpvz.api.zombie.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import cn.enaium.fabricpvz.api.zombie.ZombieHooks;
import cn.enaium.fabricpvz.portable.lawn.ZombieCombat;

/**
 * Answers {@code Zombie::TakeDamage}'s two damage-flag tests with the shield rule a mod registered
 * ({@link cn.enaium.fabricpvz.api.zombie.ZombieOverrides#shieldAlwaysAbsorbs(boolean)}).
 *
 * <p>The method asks {@code PvzpCommon.testBit} twice - once for {@code DAMAGE_BYPASSES_SHIELD}, to
 * decide whether the hit may skip the shield, and once for {@code DAMAGE_HITS_SHIELD_AND_BODY}, to
 * decide whether the body gets the full damage on top of the shield - and both calls are redirected to
 * {@link ZombieHooks#damageFlagTest(int, int)}, which answers the game's own {@code testBit} result
 * until a mod turns the rule on. Two calls, no ordinal: the redirect covers exactly the flags the
 * method asks about.
 *
 * <p>The other half of {@code TakeDamage} - how much damage the shield, the helmet and the body each
 * take - is the game's own code and is not touched; only the two flags that steer it are.
 */
@Mixin(ZombieCombat.class)
public abstract class ZombieCombatMixin {

    /** Redirects every {@code PvzpCommon.testBit} of {@code Zombie::TakeDamage} to the flag hook. */
    @Redirect(method = "takeDamage(Lcn/enaium/fabricpvz/portable/lawn/Zombie;II)V",
            at = @At(value = "INVOKE",
                    target = "Lcn/enaium/fabricpvz/portable/pvzp/PvzpCommon;testBit(II)Z"))
    private static boolean fabricpvz$damageFlags(int theDamageFlags, int theFlag) {
        return ZombieHooks.damageFlagTest(theDamageFlags, theFlag);
    }
}
