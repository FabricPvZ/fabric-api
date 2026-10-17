package cn.enaium.fabricpvz.api.plant.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import cn.enaium.fabricpvz.api.plant.BoardOverrides;
import cn.enaium.fabricpvz.api.plant.PlantHooks;
import cn.enaium.fabricpvz.portable.lawn.Board;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.NotRecommend;
import cn.enaium.fabricpvz.portable.lawn.SeedType;

/**
 * Applies the board-level {@link BoardOverrides} to the game methods that are the read points of a
 * knob: whether a stage has grave stones at all, the sun bank's ceiling, the instant-kill damage of
 * {@code Board::KillAllZombiesInRadius}, the background {@code Board::PickBackground} answers for a
 * game mode, and the "needs grave stones" bit of the seed picker's advice.
 *
 * <p>Each injection answers the game's own value when nobody set the knob, so an unmodded game runs
 * exactly its own code - the two {@code @ModifyConstant} injections hand the original constant back,
 * and the two {@code @Inject} injections leave the game's answer alone.
 *
 * <p>The board's own Mixin (the one that dispatches {@code PlantHooks} after a mid-level save is
 * restored) lives in the same class: one Mixin per target class, as the configuration expects.
 */
@Mixin(Board.class)
public abstract class BoardMixin {

    /**
     * Applies the registered plants' {@code onPlanted} callbacks at the end of a successful
     * {@code Board::loadGame}. A failed load returns {@code false} before it has restored anything, so
     * the dispatch is skipped then.
     */
    @Inject(method = "loadGame(Ljava/lang/String;)Z", at = @At("RETURN"))
    private void fabricpvz$onGameLoaded(String theFileName, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ()) {
            PlantHooks.onRestored((Board) (Object) this);
        }
    }

    /**
     * Answers {@code Board::StageHasGraveStones} from the override, which is the one place the game
     * asks "does this stage have grave stones" (grave placement, the start-of-level scene, the seed
     * picker's Grave Buster advice all go through it).
     */
    @Inject(method = "stageHasGraveStones()Z", at = @At("HEAD"), cancellable = true)
    private void fabricpvz$stageHasGraveStones(CallbackInfoReturnable<Boolean> cir) {
        Boolean aOverride = BoardOverrides.stageHasGraveStonesOverride();
        if (aOverride != null) {
            cir.setReturnValue(aOverride);
        }
    }

    /**
     * Replaces the sun bank's ceiling in {@code Board::AddSunMoney}, the only clamp on
     * {@code mSunMoney} (the same number the game checks its achievement against).
     */
    @ModifyConstant(method = "addSunMoney(I)V", constant = @Constant(intValue = BoardOverrides.DEFAULT_SUN_MONEY_CAP))
    private int fabricpvz$sunMoneyCap(int theCap) {
        return BoardOverrides.sunMoneyCap();
    }

    /**
     * Replaces the damage {@code Board::KillAllZombiesInRadius} deals to everything in its blast, i.e.
     * the instant kill of the cherry bomb, the jalapeno and the rest of that family. The radius the
     * callers pass is untouched: the number the game patched is this one, the damage, which the
     * callers' radii only bound.
     */
    @ModifyConstant(method = "killAllZombiesInRadius(IIIIIZI)I",
            constant = @Constant(intValue = BoardOverrides.DEFAULT_KILL_ALL_ZOMBIES_DAMAGE))
    private int fabricpvz$killAllZombiesDamage(int theDamage) {
        return BoardOverrides.killAllZombiesDamage();
    }

    /**
     * XORs the "needs grave stones" bit of {@code Board::SeedNotRecommendedForLevel} for Grave Buster,
     * which is what inverting that one condition means: the advice bit is set exactly when the plain
     * rule would not set it. The other seven bits and seed types are untouched.
     */
    @Inject(method = "seedNotRecommendedForLevel(I)I", at = @At("RETURN"), cancellable = true)
    private void fabricpvz$invertGraveBuster(int theSeedType, CallbackInfoReturnable<Integer> cir) {
        if (theSeedType == SeedType.SEED_GRAVEBUSTER && BoardOverrides.graveBusterNeedsGravesInverted()) {
            cir.setReturnValue(cir.getReturnValueI() ^ (1 << NotRecommend.NOT_RECOMMENDED_NEEDS_GRAVES));
        }
    }

    /**
     * Replaces the sun {@code Board::InitLevel} hands a Last Stand level. The branch is the method's
     * only 5000 - every other branch of that chain starts a level on 0, 150 or 50.
     */
    @ModifyConstant(method = "initLevel()V",
            constant = @Constant(intValue = BoardOverrides.DEFAULT_LAST_STAND_INITIAL_SUN))
    private int fabricpvz$lastStandInitialSun(int theSun) {
        return BoardOverrides.lastStandInitialSun();
    }

    /**
     * Forces {@code Board::DropLootPiece}'s "whack-a-zombie" sun branch on every level by answering the
     * level test the branch is behind. The branch's own random roll still decides whether the coins
     * fall; what the override removes is only the "is this a whack-a-zombie level" question.
     */
    @Redirect(method = "dropLootPiece(III)V",
            at = @At(value = "INVOKE",
                    target = "Lcn/enaium/fabricpvz/portable/lawn/LawnApp;isWhackAZombieLevel()Z"))
    private boolean fabricpvz$dropLootSunBranch(LawnApp theApp) {
        return BoardOverrides.dropLootSunBranch() || theApp.isWhackAZombieLevel();
    }

    /**
     * Replaces the coin of one of the three drops of {@code Board::DropLootPiece}'s sun branch. The
     * ordinals pick the branch's own three {@code Board::AddCoin} calls in the order the branch drops
     * them: the two the method makes before them (an adventure level's present coins) and the loot
     * table's own drops come after.
     */
    @ModifyArg(method = "dropLootPiece(III)V",
            at = @At(value = "INVOKE",
                    target = "Lcn/enaium/fabricpvz/portable/lawn/Board;addCoin(IIII)Lcn/enaium/fabricpvz/portable/lawn/Coin;",
                    ordinal = 2),
            index = 2)
    private int fabricpvz$dropLootCoinOne(int theCoinType) {
        return BoardOverrides.dropLootCoin(0);
    }

    /** ... the middle drop; see {@link #fabricpvz$dropLootCoinOne(int)}. */
    @ModifyArg(method = "dropLootPiece(III)V",
            at = @At(value = "INVOKE",
                    target = "Lcn/enaium/fabricpvz/portable/lawn/Board;addCoin(IIII)Lcn/enaium/fabricpvz/portable/lawn/Coin;",
                    ordinal = 3),
            index = 2)
    private int fabricpvz$dropLootCoinTwo(int theCoinType) {
        return BoardOverrides.dropLootCoin(1);
    }

    /** ... the leftmost drop; see {@link #fabricpvz$dropLootCoinOne(int)}. */
    @ModifyArg(method = "dropLootPiece(III)V",
            at = @At(value = "INVOKE",
                    target = "Lcn/enaium/fabricpvz/portable/lawn/Board;addCoin(IIII)Lcn/enaium/fabricpvz/portable/lawn/Coin;",
                    ordinal = 4),
            index = 2)
    private int fabricpvz$dropLootCoinThree(int theCoinType) {
        return BoardOverrides.dropLootCoin(2);
    }

    /**
     * Replaces the background {@code Board::PickBackground}'s game-mode-to-background table answers.
     *
     * <p>The injection is at the method's own {@code Board::loadBackgroundImages()} call: that is the
     * first thing that reads {@code mBackground} after the table filled it, so answering there is
     * answering before anything the background decides - the level's resource groups (the art), the
     * plantable rows of the pool and the roof, and the lawn's own rules - is derived from it.
     */
    @Inject(method = "pickBackground()V",
            at = @At(value = "INVOKE",
                    target = "Lcn/enaium/fabricpvz/portable/lawn/Board;loadBackgroundImages()V"))
    private void fabricpvz$modeBackground(CallbackInfo ci) {
        Board aBoard = (Board) (Object) this;
        aBoard.mBackground = BoardOverrides.modeBackgroundOverride(aBoard.mApp.mGameMode, aBoard.mBackground);
    }
}
