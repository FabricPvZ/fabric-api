package cn.enaium.fabricpvz.api;

import net.fabricmc.api.ModInitializer;

/**
 * {@code main} entry point of the {@code api-base} module, referenced from its
 * {@code fabric.mod.json}.
 *
 * <p>Fabric Loader instantiates this class reflectively and calls {@link #onInitialize()} after every
 * mod has been discovered and resolved but before the game main class runs. It marks the API as
 * initialised and registers its version, which is what {@link PvzFabricApi#isInitialized()} and
 * {@link PvzFabricApi#getVersion()} report and what a mod's own initializer can assert on.
 *
 * <p>The gameplay and lifecycle bridges of {@code api-events} are installed by that module's own
 * entry point ({@code cn.enaium.fabricpvz.api.ApiEventsInitializer}), which Fabric Loader runs for
 * the same reason.
 */
public final class ApiModInitializer implements ModInitializer {

	@Override
	public void onInitialize() {
		ApiState.markInitialized(PvzFabricApi.VERSION);
	}
}
