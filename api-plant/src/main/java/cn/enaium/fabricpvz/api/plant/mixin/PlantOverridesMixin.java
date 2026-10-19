package cn.enaium.fabricpvz.api.plant.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

import cn.enaium.fabricpvz.api.plant.PlantOverride;
import cn.enaium.fabricpvz.api.plant.PlantOverrides;
import cn.enaium.fabricpvz.portable.lawn.Board;
import cn.enaium.fabricpvz.portable.lawn.Coin;
import cn.enaium.fabricpvz.portable.lawn.Plant;
import cn.enaium.fabricpvz.portable.sexy.Rect;

/**
 * Applies the {@link PlantOverrides} a mod registered at the game's own per-seed read points.
 *
 * <p>Two kinds of point, matching the two kinds of {@link PlantOverride} field:
 *
 * <ul>
 *   <li>the <b>spawn</b> numbers - {@link PlantOverride#bodyHealth(int)} and
 *       {@link PlantOverride#spawnCountdown(int)} - are written at the {@code RETURN} of
 *       {@code Plant::PlantInitialize} (Plant.cpp:111), after the per-seed {@code switch}, after the
 *       Big Time doubling and after the line that copies {@code mPlantHealth} into
 *       {@code mPlantMaxHealth} - so an override replaces the pair as one value, and a seed type
 *       nobody overrode runs exactly the game's own code. {@code PlantMixin} is listed before this
 *       Mixin in the module's configuration, so the registered plant's health is dispatched first and
 *       an override is the mod's final word.</li>
 *   <li>the values the game reads <b>while the plant is alive</b> - the chomper's third bite type and
 *       its digesting pause ({@code Plant::UpdateChomper}), a spikerock's hit damage and its two crack
 *       levels ({@code Plant::SpikeRockTakeDamage}), a potato mine's blast arguments (the
 *       {@code Board::KillAllZombiesInRadius} call of {@code Plant::DoSpecial}) and a marigold's coins
 *       ({@code Plant::UpdateProductionPlant}) - are handed over at those reads. Each injection
 *       answers the game's own number for a plant nobody overrode, so an unmodded run keeps the game's
 *       code.</li>
 * </ul>
 *
 * <p>The definition fields ({@code mSeedCost} / {@code mRefreshTime} / {@code mLaunchRate}) need no
 * injection at all: those are table writes ({@link PlantOverrides#of(int)}), not spawn numbers.
 *
 * <p>Each {@code @ModifyConstant} below names a constant that occurs exactly once in its method, which
 * is what makes the value-replacing form safe; the one exception is the marigold's rare roll, which is
 * the second {@code 10} of {@code UpdateProductionPlant} (the first is the Last Stand challenge state
 * the method compares its own state against), so it is named by ordinal.
 */
@Mixin(Plant.class)
public abstract class PlantOverridesMixin {

    @Shadow
    public int mSeedType;


    /**
     * Applies the registered overrides at the end of {@code Plant::PlantInitialize}, on top of
     * whatever the game set for the seed type. {@code @At("RETURN")} covers every return of the
     * method, not only its last instruction, so an early return from inside the per-seed {@code switch}
     * is covered as well.
     */
    @Inject(method = "plantInitialize(IIII)V", at = @At("RETURN"))
    private void fabricpvz$overrideNumbers(int theGridX, int theGridY, int theSeedType, int theImitaterType,
            CallbackInfo ci) {
        PlantOverrides.apply((Plant) (Object) this);
    }

    /**
     * Replaces the seed type of {@code Plant::UpdateShooter}'s extra shot - the one the game fires when
     * the launch counter reaches exactly 50, half way to the next reload - with the one a mod moved it
     * to ({@link PlantOverrides#extraFireSeedType()}). The method compares {@code mSeedType} against
     * {@code SEED_CATTAIL} exactly once, so the constant is unambiguous.
     */
    @ModifyConstant(method = "updateShooter()V",
            constant = @Constant(intValue = PlantOverride.DEFAULT_EXTRA_FIRE_SEED_TYPE))
    private int fabricpvz$extraFireSeedType(int theCattail) {
        return PlantOverrides.extraFireSeedType();
    }

    /**
     * Replaces the third type of {@code Plant::UpdateChomper}'s "bite instead of eat" test (the boss in
     * the game's own code) with the registered one. The two arms before it - the gargantuar and the
     * red-eye gargantuar - are untouched, so the set of zombies a chomper cannot swallow is the two of
     * them plus this type.
     */
    @ModifyConstant(method = "updateChomper()V",
            constant = @Constant(intValue = PlantOverride.DEFAULT_BITE_INSTEAD_OF_EAT_ZOMBIE_TYPE))
    private int fabricpvz$biteInsteadOfEat(int theThirdType) {
        return PlantOverrides.biteInsteadOfEatZombieType(mSeedType);
    }

    /**
     * Replaces the {@code mStateCountdown} of {@code Plant::UpdateChomper}'s
     * {@code STATE_CHOMPER_DIGESTING} transition - the method's only 4000.
     */
    @ModifyConstant(method = "updateChomper()V",
            constant = @Constant(intValue = PlantOverride.DEFAULT_CHOMPER_DIGEST_COUNTDOWN))
    private int fabricpvz$digestCountdown(int theCountdown) {
        return PlantOverrides.digestCountdown(mSeedType);
    }

    /** Replaces what one hit takes off a spikerock in {@code Plant::SpikeRockTakeDamage}. */
    @ModifyConstant(method = "spikeRockTakeDamage()V",
            constant = @Constant(intValue = PlantOverride.DEFAULT_SPIKEROCK_HIT_DAMAGE))
    private int fabricpvz$spikeRockDamage(int theDamage) {
        return PlantOverrides.spikeRockHitDamage(mSeedType);
    }

    /**
     * Replaces the health at which {@code Plant::SpikeRockTakeDamage} hides the {@code bigspike3}
     * track - i.e. where the first crack shows.
     */
    @ModifyConstant(method = "spikeRockTakeDamage()V",
            constant = @Constant(intValue = PlantOverride.DEFAULT_SPIKEROCK_HIDE_BIGSPIKE3_AT))
    private int fabricpvz$spikeRockCrackOne(int theHealth) {
        return PlantOverrides.spikeRockHideBigSpike3At(mSeedType);
    }

    /**
     * Replaces the health at which {@code Plant::SpikeRockTakeDamage} hides the {@code bigspike2}
     * track - i.e. where the second crack shows.
     */
    @ModifyConstant(method = "spikeRockTakeDamage()V",
            constant = @Constant(intValue = PlantOverride.DEFAULT_SPIKEROCK_HIDE_BIGSPIKE2_AT))
    private int fabricpvz$spikeRockCrackTwo(int theHealth) {
        return PlantOverrides.spikeRockHideBigSpike2At(mSeedType);
    }

    /**
     * Replaces the three arguments of the potato-mine blast in {@code Plant::DoSpecial}: the third of
     * the method's three {@code Board::KillAllZombiesInRadius} calls (the cherry bomb's and the doom
     * shroom's come first). The position arguments, which the game reads off the plant, are left alone.
     */
    @ModifyArgs(method = "doSpecial()V",
            at = @At(value = "INVOKE",
                    target = "Lcn/enaium/fabricpvz/portable/lawn/Board;killAllZombiesInRadius(IIIIIZI)I",
                    ordinal = 2))
    private void fabricpvz$explosionBlast(Args theArgs) {
        theArgs.set(3, PlantOverrides.explosionRadius(mSeedType));
        theArgs.set(4, PlantOverrides.explosionRowRange(mSeedType));
        theArgs.set(5, PlantOverrides.explosionBurns(mSeedType));
    }

    /**
     * Replaces the rare roll of {@code Plant::UpdateProductionPlant}'s marigold branch - the method's
     * second {@code 10}, after the {@code ChallengeState.STATECHALLENGE_LAST_STAND_ONSLAUGHT} the
     * method compares its state against.
     */
    @ModifyConstant(method = "updateProductionPlant()V",
            constant = @Constant(intValue = PlantOverride.DEFAULT_PRODUCED_COIN_RARE_PERCENT, ordinal = 1))
    private int fabricpvz$producedCoinRarePercent(int theRarePercent) {
        return PlantOverrides.producedCoinRarePercent(mSeedType);
    }

    /**
     * Replaces the coin of every {@code Board::AddCoin} of {@code Plant::UpdateProductionPlant}: the
     * game's own rare arm answers the override's rare coin and its common arm the override's common
     * one, which is what moves the marigold's production coin and the coin a Big Time level pays on
     * top. Every other call of the method passes a coin type that is neither arm (the sun coins of the
     * sun producers), and every plant nobody overrode answers its own coin, so those are unchanged.
     */
    @Redirect(method = "updateProductionPlant()V",
            at = @At(value = "INVOKE",
                    target = "Lcn/enaium/fabricpvz/portable/lawn/Board;addCoin(IIII)Lcn/enaium/fabricpvz/portable/lawn/Coin;"))
    private Coin fabricpvz$producedCoin(Board theBoard, int theX, int theY, int theCoinType, int theCoinMotion) {
        return theBoard.addCoin(theX, theY, PlantOverrides.producedCoin(mSeedType, theCoinType), theCoinMotion);
    }


    /**
     * The widening of {@link PlantOverrides#attackRectExtraWidth(int)}: the attack rectangle the game
     * built for this plant is widened by the registered pixels, which is what moves the plant's reach
     * (see the API for the arithmetic of one plant). With nothing registered the rectangle is left
     * exactly as the game built it.
     */
    @Inject(method = "getPlantAttackRect(I)Lcn/enaium/fabricpvz/portable/sexy/Rect;", at = @At("RETURN"))
    private void fabricpvz$attackRectExtraWidth(int thePlantWeapon, CallbackInfoReturnable<Rect> cir) {
        int aExtraWidth = PlantOverrides.attackRectExtraWidth(mSeedType);
        Rect aRect = cir.getReturnValue();
        if (aExtraWidth > 0 && aRect != null) {
            aRect.mWidth += aExtraWidth;
        }
    }
}
