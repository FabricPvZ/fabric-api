package cn.enaium.fabricpvz.api.plant.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import cn.enaium.fabricpvz.api.plant.BoardOverrides;
import cn.enaium.fabricpvz.portable.lawn.Coin;

/**
 * Applies {@link BoardOverrides#sunValues(int, int, int)} to {@code Coin::GetSunValue}, the one place
 * a sun coin's worth is decided - the pickup ({@code Board::AddSunMoney} is handed this number), the
 * "can I afford it" flash and the sun counter all read it.
 *
 * <p>The override is per coin type, so a mod may move one of the three and leave the others; a coin
 * nobody overrode (or a call with nothing registered) keeps the game's own value, which the
 * injection's early exit leaves untouched.
 */
@Mixin(Coin.class)
public abstract class CoinMixin {

    @Shadow
    public int mType;

    @Shadow
    public int mUsableSeedType;

    /** The coin's worth, from the override, or the game's own rule when there is none. */
    @Inject(method = "getSunValue()I", at = @At("HEAD"), cancellable = true)
    private void fabricpvz$sunValue(CallbackInfoReturnable<Integer> cir) {
        int aValue = BoardOverrides.sunValue(mType);
        if (aValue >= 0) {
            cir.setReturnValue(aValue);
        }
    }

    /**
     * Replaces the default {@code mUsableSeedType} {@code Coin::CoinInitialize} gives every coin - the
     * seed an air-raid "usable seed packet" coin hands the cursor when nothing gave the coin one of its
     * own. {@code Coin}'s constructor and the method's other defaults are untouched, and the injection
     * is at the method's return, so the field the patch replaces is the one the initialiser wrote.
     */
    @Inject(method = "coinInitialize(IIII)V", at = @At("RETURN"))
    private void fabricpvz$usableSeedType(int theX, int theY, int theCoinType, int theCoinMotion, CallbackInfo ci) {
        int aUsable = BoardOverrides.coinUsableSeedType();
        if (aUsable != BoardOverrides.DEFAULT_COIN_USABLE_SEED_TYPE) {
            mUsableSeedType = aUsable;
        }
    }
}
