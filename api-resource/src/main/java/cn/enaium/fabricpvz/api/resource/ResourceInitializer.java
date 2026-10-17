package cn.enaium.fabricpvz.api.resource;

import cn.enaium.fabricpvz.api.lawn.LawnEvents;
import cn.enaium.fabricpvz.api.lifecycle.GameLifecycleEvents;
import net.fabricmc.api.ModInitializer;

/**
 * {@code main} entry point of the {@code api-resource} module, referenced from its
 * {@code fabric.mod.json}.
 *
 * <p>The module owns the re-installation of the mods' image slots: the game has no "resources were
 * (re-)loaded" event, so the module supplies the three call points that together cover every way a
 * slot can be taken over:
 *
 * <ol>
 *   <li>{@link GameLifecycleEvents#INITIALIZED} - the game has an application and its resource
 *       manager, so an image can be created and bound at all;</li>
 *   <li>the module's own {@code ResourcesMixin}, which calls {@link #reinstall()} after every
 *       resource group extraction - the moment the game rebinds the ids of a group, and the slot
 *       with it;</li>
 *   <li>{@link LawnEvents#TICK} - a safety net for everything else that overwrites a slot, at the
 *       price of one id comparison per claimed slot per tick.</li>
 * </ol>
 *
 * <p>{@link ResourceDiagnostics#reset()} is called first, so the counters describe this run and not
 * whatever a test did before it.
 */
public final class ResourceInitializer implements ModInitializer {

    @Override
    public void onInitialize() {
        ResourceDiagnostics.reset();
        GameLifecycleEvents.INITIALIZED.register(ResourceInitializer::reinstall);
        LawnEvents.TICK.register((board, tick) -> reinstall());
    }

    /**
     * Re-installs every claimed slot from the image its mod handed to it. Idempotent and cheap: a slot
     * that already holds its image is left untouched, so this is safe to call from a tick.
     */
    public static void reinstall() {
        ResourceDiagnostics.noteReinstall();
        for (ModImageSlot slot : ModImageSlot.all()) {
            slot.reinstall();
        }
    }

    /**
     * Records that the module's {@code ResourcesMixin} saw a resource group extraction; called by that
     * Mixin, so a report can prove it applied.
     */
    public static void noteGroupLoad() {
        ResourceDiagnostics.noteGroupLoad();
    }
}
