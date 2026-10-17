package cn.enaium.fabricpvz.api.plant.mixin;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import cn.enaium.fabricpvz.api.plant.PlantHooks;
import cn.enaium.fabricpvz.portable.lawn.Plant;
import cn.enaium.fabricpvz.portable.lawn.ZombieBehaviourA;

/**
 * Answers {@code Zombie::UpdateZombieGargantuar}'s smash test with the crush-proof rule a mod
 * registered ({@link cn.enaium.fabricpvz.api.plant.PlantOverride#crushProof(boolean)}).
 *
 * <p>When a gargantuar's smash animation reaches its hit frame the game looks at the plant in front of
 * it: the one seed type it names ({@code SEED_SPIKEROCK}) is not squashed but hurts - the gargantuar
 * takes its little bounce damage and the plant takes a spikerock hit - and every other plant is
 * squashed with its whole square. The redirect is that comparison, and
 * {@link PlantHooks#crushTestSeedType(Plant)} hands it the exempt seed type for a plant a mod made
 * crush-proof and the plant's own seed type for everybody else, so with no registration the method
 * runs exactly the game's own comparison.
 *
 * <p>Its sibling {@link ZombieCrushMixin} does the same for the other comparison the game makes about
 * the same seed type ({@code Zombie::SquishAllInSquare}); both are needed, because a plant that is not
 * squashed on the smash frame would still be squashed by the square-wide pass a drive-over or a squash
 * makes. The Mixin lives in this module for the reason {@link ZombieCrushMixin} documents.
 */
@Mixin(ZombieBehaviourA.class)
public abstract class ZombieSmashMixin {

    /**
     * Replaces the {@code mSeedType} {@code Zombie::UpdateZombieGargantuar} (Zombie.cpp:2080) compares
     * against {@code SEED_SPIKEROCK} - the method's only read of that field.
     */
    @Redirect(method = "updateZombieGargantuar(Lcn/enaium/fabricpvz/portable/lawn/Zombie;)V",
            at = @At(value = "FIELD", target = "Lcn/enaium/fabricpvz/portable/lawn/Plant;mSeedType:I",
                    opcode = Opcodes.GETFIELD))
    private static int fabricpvz$smashCrushProofSeedType(Plant thePlant) {
        return PlantHooks.crushTestSeedType(thePlant);
    }
}
