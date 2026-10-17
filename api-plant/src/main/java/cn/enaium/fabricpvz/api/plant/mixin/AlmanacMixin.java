package cn.enaium.fabricpvz.api.plant.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import cn.enaium.fabricpvz.api.plant.PlantHooks;
import cn.enaium.fabricpvz.portable.lawn.PlantDefinition;
import cn.enaium.fabricpvz.portable.lawn.widget.AlmanacDialog;

/**
 * Dispatches the almanac's plant description to {@link PlantHooks}, so a registered plant describes
 * itself in the almanac the way it names itself on the seed packet.
 *
 * <p>{@code AlmanacDialog::GetPlantDescriptionText} is the seam the almanac reads a plant's
 * description through (AlmanacDialog.cpp:337; the port turned the key lookup into this method), and it
 * is a static method that no other class overrides, so the hook lands here instead of in the game.
 * The hook answers {@code null} for a seed type nobody registered - and for a registration that
 * describes the plant nowhere - which leaves the game on its own string-file path for upstream plants
 * and for a mod that only wants the retail description format.
 */
@Mixin(AlmanacDialog.class)
public abstract class AlmanacMixin {

    /**
     * Replaces the {@code [<PLANT_NAME>_DESCRIPTION]} LawnStrings lookup of
     * {@code AlmanacDialog::GetPlantDescriptionText} with the registered description.
     */
    @Inject(method = "getPlantDescriptionText(ILcn/enaium/fabricpvz/portable/lawn/PlantDefinition;)"
            + "Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
    private static void fabricpvz$getPlantDescriptionText(int theSeedType, PlantDefinition thePlantDefinition,
            CallbackInfoReturnable<String> cir) {
        String aDescription = PlantHooks.almanacDescription(theSeedType);
        if (aDescription != null) {
            cir.setReturnValue(aDescription);
        }
    }
}
