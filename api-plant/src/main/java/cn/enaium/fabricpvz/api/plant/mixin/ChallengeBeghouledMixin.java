package cn.enaium.fabricpvz.api.plant.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import cn.enaium.fabricpvz.api.plant.BoardOverrides;
import cn.enaium.fabricpvz.portable.lawn.ChallengeBeghouled;

/**
 * Applies {@link BoardOverrides#beghouledWinningScore(int)} to the two methods that hold the
 * Beghouled winning score.
 *
 * <p>{@code Challenge.BEGHOULED_WINNING_SCORE} is a {@code static final int}, so every read of it is
 * inlined into the bytecode and no table can be written: the score appears as the constant
 * {@code 75} in {@code ChallengeBeghouled::BeghouledScore} (the three reads: the "{SCORE}" hint, the
 * "almost there" comparison and the winning comparison plus the clamp) and once in
 * {@code ChallengeBeghouled::UpdateBeghouled} (the progress meter's upper end). Replacing those
 * constants is therefore the read point, and each handler hands the overwritten score over - the
 * game's own value when nobody set one.
 *
 * <p>The "almost there" threshold is the game's own "{@code score - 5}", so it follows the winning
 * score instead of being a second knob.
 */
@Mixin(ChallengeBeghouled.class)
public abstract class ChallengeBeghouledMixin {

    /** The winning score in {@code ChallengeBeghouled::BeghouledScore}'s three reads of the constant. */
    @ModifyConstant(method = "beghouledScore(IIII)V",
            constant = @Constant(intValue = BoardOverrides.DEFAULT_BEGHOULED_WINNING_SCORE))
    private int fabricpvz$winningScore(int theScore) {
        return BoardOverrides.beghouledWinningScore();
    }

    /** The "almost there" threshold, which the game spells as the winning score minus five. */
    @ModifyConstant(method = "beghouledScore(IIII)V",
            constant = @Constant(intValue = BoardOverrides.DEFAULT_BEGHOULED_WINNING_SCORE - 5))
    private int fabricpvz$almostThereScore(int theScore) {
        return BoardOverrides.beghouledWinningScore() - 5;
    }

    /** The winning score as the progress meter's upper end in {@code ChallengeBeghouled::UpdateBeghouled}. */
    @ModifyConstant(method = "updateBeghouled()V",
            constant = @Constant(intValue = BoardOverrides.DEFAULT_BEGHOULED_WINNING_SCORE))
    private int fabricpvz$progressMeterScore(int theScore) {
        return BoardOverrides.beghouledWinningScore();
    }
}
