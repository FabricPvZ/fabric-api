package cn.enaium.fabricpvz.api.plant.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import cn.enaium.fabricpvz.api.plant.BoardOverrides;
import cn.enaium.fabricpvz.portable.lawn.ChallengeWeather;

/**
 * Applies {@link BoardOverrides#rainSeedInterval(int, int)} to
 * {@code ChallengeWeather::UpdateRainingSeeds}, whose whole rule is the one interval it draws the next
 * drop's pause from (the game spells the pair as {@code RandRangeInt(500, 999)}, i.e. two constants).
 */
@Mixin(ChallengeWeather.class)
public abstract class ChallengeWeatherMixin {

    /** The low end of the pause the raining-seeds challenge waits between two drops. */
    @ModifyConstant(method = "updateRainingSeeds()V",
            constant = @Constant(intValue = BoardOverrides.DEFAULT_RAIN_SEED_MIN))
    private int fabricpvz$rainSeedMinimum(int theMinimum) {
        return BoardOverrides.rainSeedIntervalMin();
    }

    /** The high end of that pause. */
    @ModifyConstant(method = "updateRainingSeeds()V",
            constant = @Constant(intValue = BoardOverrides.DEFAULT_RAIN_SEED_MAX))
    private int fabricpvz$rainSeedMaximum(int theMaximum) {
        return BoardOverrides.rainSeedIntervalMax();
    }
}
