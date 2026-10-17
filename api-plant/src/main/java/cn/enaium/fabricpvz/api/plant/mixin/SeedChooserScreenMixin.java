package cn.enaium.fabricpvz.api.plant.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import cn.enaium.fabricpvz.api.plant.BoardOverrides;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.widget.SeedChooserScreen;

/**
 * Extends {@code SeedChooserScreen::SeedNotAllowedToPick}, the single answer the chooser, the imitater
 * dialog and the picking helpers all consult: a mode can refuse further seed types next to the ones
 * the game itself refuses (Last Stand's sun plants).
 *
 * <p>The game's own answer always stands - the override only ever adds a veto - so a seed the game
 * refuses stays refused and one it allows is refused only where a mod asked for it.
 */
@Mixin(SeedChooserScreen.class)
public abstract class SeedChooserScreenMixin {

    @Shadow
    public LawnApp mApp;

    /** The game's own veto, OR the override's. */
    @Inject(method = "seedNotAllowedToPick(I)Z", at = @At("RETURN"), cancellable = true)
    private void fabricpvz$seedNotAllowedToPick(int theSeedType, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && BoardOverrides.seedNotAllowedToPickOverride(mApp.mGameMode, theSeedType)) {
            cir.setReturnValue(true);
        }
    }
}
