package cn.enaium.fabricpvz.api.plant.mixin;

import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

import cn.enaium.fabricpvz.api.plant.BoardOverrides;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.ZombieDefinition;

/**
 * Applies the wave-level {@link BoardOverrides} to {@code BoardWaves} - the class the port keeps
 * {@code Board.cpp}'s wave composition in, and so the one that owns both "may this zombie appear on
 * this level", "what falls out of the sky" and "how many waves a level has".
 *
 * <p>The class is package-private ({@code final class BoardWaves}), which is why this Mixin names it
 * with {@code targets} instead of extending it: an injection only has to see the class, not declare it.
 *
 * <p>Every knob defaults to the game's own behaviour, so with nothing registered the two redirects hand
 * the definition's own fields back and every constant injection hands its own constant back.
 *
 * <p>The spawn multiplier is the one knob here that is a <em>condition</em> rather than a number: the
 * arm of {@code PickZombieWaves} that multiplies a wave's zombie points is armed by the same
 * little-trouble / Wall-nut Bowling test the game itself uses, and registering a factor answers that
 * test true (the patched binary's unconditional jump) while the factor itself replaces the arm's
 * constant.
 */
@Mixin(targets = "cn.enaium.fabricpvz.portable.lawn.BoardWaves")
public abstract class BoardWavesMixin {

    /**
     * Neutralises the {@code theLevel < ZombieDefinition.mStartingLevel} veto of
     * {@code BoardWaves::CanZombieSpawnOnLevel} while the relaxation is on: the definition's own
     * starting level becomes the smallest int there is, which no level is below - the same thing the
     * patched {@code jge} that jumps over the {@code return false} does.
     */
    @Redirect(method = "canZombieSpawnOnLevel(II)Z",
            at = @At(value = "FIELD",
                    target = "Lcn/enaium/fabricpvz/portable/lawn/ZombieDefinition;mStartingLevel:I",
                    opcode = Opcodes.GETFIELD))
    private static int fabricpvz$startingLevel(ZombieDefinition theDefinition) {
        return BoardOverrides.zombieSpawnRulesRelaxed() ? Integer.MIN_VALUE : theDefinition.mStartingLevel;
    }

    /**
     * Neutralises the {@code ZombieDefinition.mPickWeight == 0} veto the same way: the weight the
     * condition reads becomes 1, which is not 0, and the field is read nowhere else in the method.
     */
    @Redirect(method = "canZombieSpawnOnLevel(II)Z",
            at = @At(value = "FIELD",
                    target = "Lcn/enaium/fabricpvz/portable/lawn/ZombieDefinition;mPickWeight:I",
                    opcode = Opcodes.GETFIELD))
    private static int fabricpvz$pickWeight(ZombieDefinition theDefinition) {
        return BoardOverrides.zombieSpawnRulesRelaxed() ? 1 : theDefinition.mPickWeight;
    }

    /**
     * Replaces the game mode whose sky drop is special-cased in {@code BoardWaves::UpdateSunSpawning}:
     * the comparison's constant is the mode, so handing another one over is what "this mode is the
     * special one now, and the old one is not" means.
     */
    @ModifyConstant(method = "updateSunSpawning(Lcn/enaium/fabricpvz/portable/lawn/Board;)V",
            constant = @Constant(intValue = BoardOverrides.DEFAULT_SUN_SPAWNING_MODE))
    private static int fabricpvz$sunSpawningMode(int theGameMode) {
        return BoardOverrides.sunSpawningMode();
    }

    /**
     * Replaces the coin type that mode drops: the ternary's two arms are two constants, so the special
     * one can be moved to another coin type without touching the ordinary sun.
     */
    @ModifyConstant(method = "updateSunSpawning(Lcn/enaium/fabricpvz/portable/lawn/Board;)V",
            constant = @Constant(intValue = BoardOverrides.DEFAULT_SUN_SPAWNING_COIN_TYPE))
    private static int fabricpvz$sunSpawningCoin(int theCoinType) {
        return BoardOverrides.sunSpawningCoinType();
    }

    /**
     * Replaces the ternary's other arm - the coin an ordinary stage's sky drop is. The method's only
     * {@code COIN_SUN}.
     */
    @ModifyConstant(method = "updateSunSpawning(Lcn/enaium/fabricpvz/portable/lawn/Board;)V",
            constant = @Constant(intValue = BoardOverrides.DEFAULT_SKY_DROP_COIN_TYPE))
    private static int fabricpvz$skyDropCoin(int theCoinType) {
        return BoardOverrides.skyDropCoin();
    }

    /**
     * Replaces the wave count {@code BoardWaves::GetNumWavesPerSurvivalStage} answers: 10 for a Last
     * Stand or normal survival stage, 20 for a hard or endless one. Both are the method's own, and
     * replacing them here moves every reader of that answer with them (the level's wave count and the
     * flag bar the board draws from it).
     */
    @ModifyConstant(method = "getNumWavesPerSurvivalStage(Lcn/enaium/fabricpvz/portable/lawn/Board;)I",
            constant = @Constant(intValue = 10))
    private static int fabricpvz$survivalStageWaves(int theWaves) {
        return BoardOverrides.numWaves(theWaves);
    }

    /** ... the hard/endless half of {@link #fabricpvz$survivalStageWaves(int)}. */
    @ModifyConstant(method = "getNumWavesPerSurvivalStage(Lcn/enaium/fabricpvz/portable/lawn/Board;)I",
            constant = @Constant(intValue = 20))
    private static int fabricpvz$survivalStageWavesHard(int theWaves) {
        return BoardOverrides.numWaves(theWaves);
    }

    /**
     * Replaces the wave count of {@code BoardWaves::PickZombieWaves}' whack-a-zombie branch (the
     * method's only 12).
     */
    @ModifyConstant(method = "pickZombieWaves(Lcn/enaium/fabricpvz/portable/lawn/Board;)V",
            constant = @Constant(intValue = 12))
    private static int fabricpvz$whackAZombieWaves(int theWaves) {
        return BoardOverrides.numWaves(theWaves);
    }

    /**
     * Replaces the wave count of the branch that assigns 30 - named by ordinal because the same method
     * also compares the game mode against 30, and that comparison is the first of the two.
     */
    @ModifyConstant(method = "pickZombieWaves(Lcn/enaium/fabricpvz/portable/lawn/Board;)V",
            constant = @Constant(intValue = 30, ordinal = 1))
    private static int fabricpvz$thirtyWaveBranch(int theWaves) {
        return BoardOverrides.numWaves(theWaves);
    }

    /**
     * Replaces the wave count of {@code BoardWaves::PickZombieWaves}' final branch - the 40 every
     * remaining challenge gets, and the method's only 40.
     */
    @ModifyConstant(method = "pickZombieWaves(Lcn/enaium/fabricpvz/portable/lawn/Board;)V",
            constant = @Constant(intValue = 40))
    private static int fabricpvz$defaultWaves(int theWaves) {
        return BoardOverrides.numWaves(theWaves);
    }

    /**
     * Replaces the floor {@code BoardWaves::PickZombieWaves} raises a second-run adventure level's own
     * table value to - the first of the method's three {@code 20}s, and the same one the patched
     * binary's single {@code 20 -> 40} immediate feeds (see
     * {@link BoardOverrides#numWaves(int, int)}'s {@code 20 -> 40}).
     */
    @ModifyConstant(method = "pickZombieWaves(Lcn/enaium/fabricpvz/portable/lawn/Board;)V",
            constant = @Constant(intValue = 20, ordinal = 0))
    private static int fabricpvz$adventureWaveFloor(int theWaves) {
        return BoardOverrides.numWaves(theWaves);
    }

    /**
     * ... and the wave count of the level group that assigns 20 - the Wall-nut Bowling / Air Raid /
     * Grave Danger / High Gravity / Portal Combat / War and Peas / Invisighoul group (the method's
     * second {@code 20}; the third is {@code ZombieType.ZOMBIE_BUNGEE}, a zombie type and not a count,
     * and is deliberately left alone).
     */
    @ModifyConstant(method = "pickZombieWaves(Lcn/enaium/fabricpvz/portable/lawn/Board;)V",
            constant = @Constant(intValue = 20, ordinal = 1))
    private static int fabricpvz$twentyWaveBranches(int theWaves) {
        return BoardOverrides.numWaves(theWaves);
    }

    /**
     * Makes {@code BoardWaves::PickZombieWaves}' "certain levels multiply the zombie points" arm the
     * arm every level takes: the method's little-trouble / Wall-nut Bowling test is the second half of
     * the arm's condition, so answering true for it takes the arm wherever the game's own guards let
     * this code run - which is the same thing the patched {@code je} that jumps over the mode tests
     * does. The game's own ×6 Column arm is tested before this one and keeps its factor.
     *
     * <p>The call is the method's only {@code isWallnutBowlingLevel()} (the little-trouble /
     * Wall-nut Bowling test); the mode-group test earlier in the method asks about little trouble only
     * and is deliberately left alone.
     */
    @Redirect(method = "pickZombieWaves(Lcn/enaium/fabricpvz/portable/lawn/Board;)V",
            at = @At(value = "INVOKE",
                    target = "Lcn/enaium/fabricpvz/portable/lawn/LawnApp;isWallnutBowlingLevel()Z"))
    private static boolean fabricpvz$spawnMultiplierArm(LawnApp theApp) {
        return BoardOverrides.spawnMultiplier() > 0 || theApp.isWallnutBowlingLevel();
    }

    /**
     * Replaces the factor of that arm - the method's only 4 - with the registered one, so the arm
     * {@link #fabricpvz$spawnMultiplierArm(LawnApp)} takes on every level multiplies a wave's zombie
     * points by the mod's factor rather than the retail 4.
     */
    @ModifyConstant(method = "pickZombieWaves(Lcn/enaium/fabricpvz/portable/lawn/Board;)V",
            constant = @Constant(intValue = 4))
    private static int fabricpvz$spawnMultiplier(int theRetailMultiplier) {
        int aMultiplier = BoardOverrides.spawnMultiplier();
        return aMultiplier > 0 ? aMultiplier : theRetailMultiplier;
    }
}
