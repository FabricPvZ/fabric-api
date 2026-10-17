package cn.enaium.fabricpvz.api.resource;

/**
 * Counters of the module's own bookkeeping, for diagnostics and for the end-to-end harness.
 *
 * <p>Nothing in the game depends on them; they exist because a mod's art installation is invisible
 * when it works and silent when it does not - a Mixin that did not apply, or a slot that keeps being
 * clobbered, would otherwise leave no trace. The module's entry point resets them when it starts
 * ({@link ResourceInitializer#onInitialize()}) and bumps them from the paths that install and
 * re-install the mods' images, so a report can tell "the module ran and restored the slot" apart from
 * "the module never got a chance to".
 *
 * <p>Counters are plain {@code int} fields guarded by a lock; at most one listener runs at a time and
 * reads happen after the fact, so contention is not a concern.
 */
public final class ResourceDiagnostics {

    private static final Object LOCK = new Object();

    private static int installs;
    private static int reinstalls;
    private static int groupLoadCallbacks;

    private ResourceDiagnostics() {
    }

    /** @return how often a mod put an image into one of its slots ({@link ModImageSlot#setImage}) */
    public static int installs() {
        synchronized (LOCK) {
            return installs;
        }
    }

    /** @return how often the module re-installed the claimed slots after a load or a tick */
    public static int reinstalls() {
        synchronized (LOCK) {
            return reinstalls;
        }
    }

    /**
     * @return how often the module's {@code ResourcesMixin} saw a resource group extraction; zero
     *     means the Mixin did not apply
     */
    public static int groupLoadCallbacks() {
        synchronized (LOCK) {
            return groupLoadCallbacks;
        }
    }

    /** Zeroes every counter, so a test or a harness can read the deltas of one phase. */
    public static void reset() {
        synchronized (LOCK) {
            installs = 0;
            reinstalls = 0;
            groupLoadCallbacks = 0;
        }
    }

    static void noteInstall() {
        synchronized (LOCK) {
            installs++;
        }
    }

    static void noteReinstall() {
        synchronized (LOCK) {
            reinstalls++;
        }
    }

    static void noteGroupLoad() {
        synchronized (LOCK) {
            groupLoadCallbacks++;
        }
    }
}
