package cn.enaium.fabricpvz.api.plant.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import cn.enaium.fabricpvz.api.plant.PlantHooks;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;

/**
 * Dispatches the seed availability of a registered plant to {@link PlantHooks}.
 *
 * <p>{@code LawnApp::HasSeedType} (LawnApp.cpp:2791) is the one answer the seed chooser and the
 * preload loops consult, so overriding it here is what makes a registered seed usable and preloaded
 * at once.
 */
@Mixin(LawnApp.class)
public abstract class LawnAppMixin {

    @Inject(method = "hasSeedType(I)Z", at = @At("HEAD"), cancellable = true)
    private void fabricpvz$hasSeedType(int theSeedType, CallbackInfoReturnable<Boolean> cir) {
        Boolean aResult = PlantHooks.hasSeedType(theSeedType);
        if (aResult != null) {
            cir.setReturnValue(aResult);
        }
    }
}
