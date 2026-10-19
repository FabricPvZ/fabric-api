package cn.enaium.fabricpvz.api.zombie.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import cn.enaium.fabricpvz.api.zombie.ZombieHooks;
import cn.enaium.fabricpvz.portable.lawn.Plant;
import cn.enaium.fabricpvz.portable.lawn.Zombie;

/**
 * Dispatches the two per-zombie steps of {@code Plant::BurnRow} (Plant.cpp:4226) - the cold effects the
 * row removes from every zombie it covers, and the burn it applies - to {@link ZombieHooks}, for the
 * burn-row rule of {@link cn.enaium.fabricpvz.api.zombie.ZombieOverrides#burnRowCold(int, int, int)}: a
 * row that freezes instead of unfreezing, and deals damage of its own where the burn's clamp no longer
 * does.
 *
 * <p>Both redirects answer the game's own calls until a mod registers the rule (the first hands the
 * zombie to {@code Zombie::RemoveColdEffects}, the second to {@code Zombie::ApplyBurn}), so an unmodded
 * game runs exactly its own burn row. The rules live in the zombie module because the burn does: the
 * plant is only the caller.
 */
@Mixin(Plant.class)
public abstract class PlantBurnRowMixin {

    /** Replaces the row's {@code removeColdEffects} call with the rule's cold effects. */
    @Redirect(method = "burnRow(I)V", at = @At(value = "INVOKE",
            target = "Lcn/enaium/fabricpvz/portable/lawn/Zombie;removeColdEffects()V"))
    private static void fabricpvz$burnRowCold(Zombie theZombie) {
        ZombieHooks.burnRowColdEffects(theZombie);
    }

    /** Replaces the row's {@code applyBurn} call with the burn plus the rule's damage. */
    @Redirect(method = "burnRow(I)V", at = @At(value = "INVOKE",
            target = "Lcn/enaium/fabricpvz/portable/lawn/Zombie;applyBurn()V"))
    private static void fabricpvz$burnRowBurn(Zombie theZombie) {
        ZombieHooks.burnRowApplyBurn(theZombie);
    }
}
