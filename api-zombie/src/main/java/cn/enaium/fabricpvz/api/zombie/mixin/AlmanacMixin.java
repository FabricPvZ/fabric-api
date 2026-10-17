package cn.enaium.fabricpvz.api.zombie.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import cn.enaium.fabricpvz.api.zombie.ZombieHooks;
import cn.enaium.fabricpvz.portable.lawn.ZombieDefinition;
import cn.enaium.fabricpvz.portable.lawn.widget.AlmanacDialog;

/**
 * Dispatches the almanac's zombie name and description to {@link ZombieHooks}, so a registered zombie
 * answers for itself in the almanac page the port gives it.
 *
 * <p>{@code AlmanacDialog::GetZombieNameText} and {@code AlmanacDialog::GetZombieDescriptionText} are
 * the seams the almanac reads a zombie through (AlmanacDialog.cpp:377, 381; the port turned the key
 * lookups into these methods), and both are static methods no other class overrides, so the hooks land
 * here instead of in the game. Each answers {@code null} for a zombie type nobody registered - and for
 * a registration that names/describes it nowhere - which leaves the game on its own string-file path.
 */
@Mixin(AlmanacDialog.class)
public abstract class AlmanacMixin {

    /**
     * Replaces the name of {@code AlmanacDialog::GetZombieNameText} with the registered name; an
     * unencountered zombie keeps the game's own {@code ???} (AlmanacDialog.cpp:377-378).
     */
    @Inject(method = "getZombieNameText(ILcn/enaium/fabricpvz/portable/lawn/ZombieDefinition;Z)"
            + "Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
    private static void fabricpvz$getZombieNameText(int theZombieType, ZombieDefinition theZombieDefinition,
            boolean theSilhouette, CallbackInfoReturnable<String> cir) {
        if (theSilhouette) {
            return;
        }

        String aName = ZombieHooks.name(theZombieType);
        if (aName != null) {
            cir.setReturnValue(aName);
        }
    }

    /**
     * Replaces the {@code [<ZOMBIE_NAME>_DESCRIPTION]} LawnStrings lookup of
     * {@code AlmanacDialog::GetZombieDescriptionText} with the registered description.
     */
    @Inject(method = "getZombieDescriptionText(ILcn/enaium/fabricpvz/portable/lawn/ZombieDefinition;)"
            + "Ljava/lang/String;", at = @At("HEAD"), cancellable = true)
    private static void fabricpvz$getZombieDescriptionText(int theZombieType, ZombieDefinition theZombieDefinition,
            CallbackInfoReturnable<String> cir) {
        String aDescription = ZombieHooks.almanacDescription(theZombieType);
        if (aDescription != null) {
            cir.setReturnValue(aDescription);
        }
    }
}
