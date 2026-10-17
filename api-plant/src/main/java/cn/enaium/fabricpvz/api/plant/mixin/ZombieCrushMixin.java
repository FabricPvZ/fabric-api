package cn.enaium.fabricpvz.api.plant.mixin;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import cn.enaium.fabricpvz.api.plant.PlantHooks;
import cn.enaium.fabricpvz.portable.lawn.Plant;
import cn.enaium.fabricpvz.portable.lawn.ZombieCombat;

/**
 * Answers {@code Zombie::SquishAllInSquare}'s crush test with the crush-proof rule a mod registered
 * ({@link cn.enaium.fabricpvz.api.plant.PlantOverride#crushProof(boolean)}).
 *
 * <p>The game squashes every plant of a square except the one seed type it names
 * ({@code SEED_SPIKEROCK}, which it skips for a drive-over and refuses to squish at all); that
 * comparison is the redirect, and {@link PlantHooks#crushTestSeedType(Plant)} hands it the exempt seed
 * type for a plant a mod made crush-proof and the plant's own seed type for everybody else - so with no
 * registration the method runs exactly the game's own comparison.
 *
 * <p>The Mixin lives in this module, not in {@code api-zombie}, because what it asks is a question
 * about a <em>plant</em> ({@code PlantOverrides}) and answers it with the plant API's own predicate;
 * the game class it injects into is only where the question is asked. {@code api-plant}'s other Mixins
 * ({@code BoardMixin}, {@code CoinMixin}, {@code ChallengeMixin}) target game classes for the same
 * reason.
 */
@Mixin(ZombieCombat.class)
public abstract class ZombieCrushMixin {

    /**
     * Replaces the {@code mSeedType} {@code Zombie::SquishAllInSquare} (Zombie.cpp:6476) compares
     * against {@code SEED_SPIKEROCK} - the method's only read of that field.
     */
    @Redirect(method = "squishAllInSquare(Lcn/enaium/fabricpvz/portable/lawn/Zombie;III)V",
            at = @At(value = "FIELD", target = "Lcn/enaium/fabricpvz/portable/lawn/Plant;mSeedType:I",
                    opcode = Opcodes.GETFIELD))
    private static int fabricpvz$crushProofSeedType(Plant thePlant) {
        return PlantHooks.crushTestSeedType(thePlant);
    }
}
