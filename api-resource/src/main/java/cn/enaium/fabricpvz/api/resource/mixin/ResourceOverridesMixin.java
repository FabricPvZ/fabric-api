package cn.enaium.fabricpvz.api.resource.mixin;

import java.io.IOException;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import cn.enaium.fabricpvz.api.resource.ResourceOverrides;
import cn.enaium.fabricpvz.portable.sexy.pak.PakFileHandle;
import cn.enaium.fabricpvz.portable.sexy.pak.PakInterface;

/**
 * Makes the engine read a {@link ResourceOverrides} registration before it looks in the pak.
 *
 * <p>The engine's {@code PakInterface} resolves a name as "pak member, then filesystem", and that
 * order is what a loose-package re-skin (a directory of files shadowing {@code main.pak}) has to
 * invert. The module cannot change the engine's code, so this Mixin injects at the head of the two
 * entry points the game resolves resources through:
 *
 * <ul>
 *   <li>{@code open(String, String)} - for read mode only ({@code r} / {@code rb} / {@code rt}, the
 *       same set the engine's own lookup uses): a hit returns the override's handle, a miss returns
 *       nothing and the original body runs;</li>
 *   <li>{@code exists(String)} - a hit answers {@code true}.</li>
 * </ul>
 *
 * <p>{@code resourceNames()} is deliberately not touched: it reports the pak's table, and an override
 * is not a pak member (see {@code ResourceOverrides.overriddenNames()} for the diagnostic view).
 *
 * <p>The Mixin also records that it was applied ({@link ResourceOverrides#installed()}), from the
 * engine's constructor: an unapplied Mixin would otherwise be indistinguishable from "no overrides
 * registered".
 *
 * <p>The mixin config is {@code required: true}: the module exists to install this layer, and a
 * silently unapplied injection would leave a mod's overrides inert with no sign of why.
 */
@Mixin(PakInterface.class)
public class ResourceOverridesMixin {

    /** Records that the Mixin is live, as soon as the engine's instance exists. */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void pvzResourceOverrides$markInstalled(CallbackInfo ci) {
        ResourceOverrides.markInstalled();
    }

    /**
     * Serves an override for a read, before the engine's own pak lookup and filesystem fallback.
     */
    @Inject(method = "open", at = @At("HEAD"), cancellable = true)
    private void pvzResourceOverrides$open(String fileName, String access,
            CallbackInfoReturnable<PakFileHandle> cir) throws IOException {
        if (!isReadAccess(access)) {
            return;
        }
        ResourceOverrides.Override override = ResourceOverrides.resolveOverride(fileName);
        if (override == null) {
            return;
        }
        cir.setReturnValue(toHandle(override));
    }

    /** Reports an override as existing, before the pak lookup and the filesystem probe. */
    @Inject(method = "exists", at = @At("HEAD"), cancellable = true)
    private void pvzResourceOverrides$exists(String fileName, CallbackInfoReturnable<Boolean> cir) {
        if (ResourceOverrides.resolveOverride(fileName) != null) {
            cir.setReturnValue(true);
        }
    }

    /** The engine's own read-access set ({@code PakInterface.isReadAccess}). */
    private static boolean isReadAccess(String access) {
        return "r".equalsIgnoreCase(access) || "rb".equalsIgnoreCase(access) || "rt".equalsIgnoreCase(access);
    }

    /** The handle for a resolved override: in-memory bytes, or the file the override points at. */
    private static PakFileHandle toHandle(ResourceOverrides.Override override) throws IOException {
        byte[] bytes = override.bytes();
        if (bytes != null) {
            return PakFileHandle.ofBytes(bytes);
        }
        return PakFileHandle.ofFile(override.file());
    }
}
