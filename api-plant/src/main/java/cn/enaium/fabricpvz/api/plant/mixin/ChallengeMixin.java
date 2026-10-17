package cn.enaium.fabricpvz.api.plant.mixin;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import cn.enaium.fabricpvz.api.plant.BoardOverrides;
import cn.enaium.fabricpvz.portable.lawn.Board;
import cn.enaium.fabricpvz.portable.lawn.Challenge;
import cn.enaium.fabricpvz.portable.lawn.GameMode;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;

/**
 * Applies two {@link BoardOverrides}: the extra allowed zombies of a mode, and the conveyor-belt seed
 * list replacements.
 *
 * <p>The extra allowed zombies go in at the return of {@code Challenge::InitZombieWaves}, which the
 * game calls to fill {@code Board.mZombieAllowed} and then reads back when it picks the level's waves:
 * injecting here is exactly "after the game's own branch, before the picker", and it works for both
 * callers ({@code BoardWaves::InitZombieWaves} for any non-adventure mode, and the Wall-nut Bowling /
 * Whack-a-Zombie paths of {@code BoardWaves::InitZombieWavesForLevel}).
 *
 * <p>The conveyor replacements are applied where {@code Challenge::UpdateConveyorBelt} hands its pick
 * to the seed bank, and again on the field the method remembers that pick in - both read the game's
 * own picked seed type, so the replacement means what "this list entry is that list entry" means.
 */
@Mixin(Challenge.class)
public abstract class ChallengeMixin {

    @Shadow
    public LawnApp mApp;

    @Shadow
    public Board mBoard;

    /** The mode's additional allowed zombies, right after the game's own per-mode branch filled the list. */
    @Inject(method = "initZombieWaves()V", at = @At("RETURN"))
    private void fabricpvz$allowZombies(CallbackInfo ci) {
        BoardOverrides.applyZombieAllowed(mApp.mGameMode, mBoard.mZombieAllowed);
    }

    /** The seed type the belt really gets, where the game hands its own pick to the seed bank. */
    @ModifyArg(method = "updateConveyorBelt()V",
            at = @At(value = "INVOKE",
                    target = "Lcn/enaium/fabricpvz/portable/lawn/SeedBank;addSeed(IZ)V"),
            index = 0)
    private int fabricpvz$conveyorSeed(int theSeedType) {
        return BoardOverrides.conveyorSeed(mApp.mGameMode, theSeedType);
    }

    /** ... and the same replacement on the field the method remembers its last pick in, so both agree. */
    @Redirect(method = "updateConveyorBelt()V",
            at = @At(value = "FIELD",
                    target = "Lcn/enaium/fabricpvz/portable/lawn/Challenge;mLastConveyorSeedType:I",
                    opcode = Opcodes.PUTFIELD))
    private void fabricpvz$lastConveyorSeed(Challenge theChallenge, int theSeedType) {
        theChallenge.mLastConveyorSeedType = BoardOverrides.conveyorSeed(mApp.mGameMode, theSeedType);
    }

    /**
     * The mode {@code Challenge::SpawnZombieWave}'s grave branch compares: the board's own mode while
     * the grave mode runs on every level (the guard then passes wherever the branch is reached), and
     * the game's own {@code GRAVE_DANGER} constant otherwise. The method's only
     * {@code GAMEMODE_CHALLENGE_GRAVE_DANGER}, i.e. the immediate the patched binary rewrites.
     */
    @ModifyConstant(method = "spawnZombieWave()V",
            constant = @Constant(intValue = GameMode.GAMEMODE_CHALLENGE_GRAVE_DANGER))
    private int fabricpvz$graveGuardMode(int theRetailMode) {
        return BoardOverrides.graveGuardMode(theRetailMode, mApp.mGameMode);
    }

    /**
     * How late the grave branch starts growing grave stones: the game's own {@code mCurrentWave > 5},
     * and the method's only {@code 5}.
     */
    @ModifyConstant(method = "spawnZombieWave()V", constant = @Constant(intValue = 5))
    private int fabricpvz$graveStoneWaveThreshold(int theRetailWave) {
        return BoardOverrides.graveStoneWaveThreshold();
    }

    /**
     * The base count a whack-a-zombie wave rises, read where the method hands its count to the game's
     * own clamp against the grave stones it can pick from - the single place the count is used after
     * the final/double/triple arms have run, which is what the patched binary's
     * {@code aZombieCount = 1 -> 2} reaches. The method has two {@code Math.min} calls and the clamp is
     * the second (the first is the phase's own {@code min(5, …)}).
     */
    @Redirect(method = "whackAZombieSpawning()V",
            at = @At(value = "INVOKE", target = "Ljava/lang/Math;min(II)I", ordinal = 1))
    private int fabricpvz$whackAZombieCount(int theZombieCount, int theAvailableGraves) {
        return Math.min(BoardOverrides.whackAZombieZombieCountOverride(theZombieCount), theAvailableGraves);
    }
}
