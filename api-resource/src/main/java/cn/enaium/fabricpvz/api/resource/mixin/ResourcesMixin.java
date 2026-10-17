package cn.enaium.fabricpvz.api.resource.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import cn.enaium.fabricpvz.api.resource.ResourceInitializer;
import cn.enaium.fabricpvz.portable.lawn.Resources;
import cn.enaium.fabricpvz.portable.sexy.ResourceManager;

/**
 * Keeps the mods' image slots in the game's resource table.
 *
 * <p>{@code Resources::extractResourcesByName} binds every resource of a group by overwriting its id,
 * so it also overwrites the retail id a mod claimed as its own slot. There is no published
 * "resources were (re-)loaded" event, so the module (which is a mod like any other) supplies this one
 * Mixin, which re-installs the slots right after the group's own resources were bound and nothing
 * else.
 *
 * <p>The mixin config is {@code required: true}: unlike a mod that can degrade to "only the in-level
 * seed bank is covered by the tick hook", this module <em>is</em> the re-installation mechanism, and a
 * silently unapplied Mixin would mean mods' art flickers away on every load with no sign of why.
 * Failing loudly is the honest behaviour for a module whose whole job is that one injection; the
 * {@code LawnEvents.TICK} listener still covers the runs where the Mixin applies.
 */
@Mixin(Resources.class)
public class ResourcesMixin {

    /** Diagnostics hook: proves the Mixin was applied, even for a group name the game rejects. */
    @Inject(method = "extractResourcesByName", at = @At("HEAD"))
    private static void pvzResourceModule$beforeResourceGroup(ResourceManager theResourceManager, String theName,
            CallbackInfoReturnable<Boolean> cir) {
        ResourceInitializer.noteGroupLoad();
    }

    /**
     * Re-installs the claimed slots after the group's own resources were bound. {@code @At("RETURN")}
     * injects before every return of the target, including the {@code false} the game returns when a
     * resource of the group could not be loaded - {@code @At("TAIL")} would only cover the last one.
     */
    @Inject(method = "extractResourcesByName", at = @At("RETURN"))
    private static void pvzResourceModule$afterResourceGroup(ResourceManager theResourceManager, String theName,
            CallbackInfoReturnable<Boolean> cir) {
        ResourceInitializer.reinstall();
    }
}
