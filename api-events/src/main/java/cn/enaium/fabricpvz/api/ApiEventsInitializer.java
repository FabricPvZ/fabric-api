package cn.enaium.fabricpvz.api;

import cn.enaium.fabricpvz.api.lawn.LawnHooksBridge;
import cn.enaium.fabricpvz.api.lifecycle.AppHooksBridge;
import net.fabricmc.api.ModInitializer;

/**
 * {@code main} entry point of the {@code api-events} module, referenced from its
 * {@code fabric.mod.json}.
 *
 * <p>Fabric Loader instantiates this class reflectively and calls {@link #onInitialize()} after every
 * mod has been discovered and resolved but before the game main class runs. It installs the two
 * bridges that connect the game's own hook registries to the Fabric-style events of this module:
 *
 * <ol>
 *   <li>{@link LawnHooksBridge}, so that every gameplay event the game fires afterwards reaches the
 *       listeners of {@link cn.enaium.fabricpvz.api.lawn.LawnEvents};</li>
 *   <li>{@link AppHooksBridge}, so that the framework's initialize/exit call points reach the
 *       listeners of {@link cn.enaium.fabricpvz.api.lifecycle.GameLifecycleEvents}.</li>
 * </ol>
 *
 * <p>A failure while installing a bridge propagates: Fabric Loader aborts startup rather than letting
 * the game run with gameplay events silently disconnected.
 */
public final class ApiEventsInitializer implements ModInitializer {

	@Override
	public void onInitialize() {
		LawnHooksBridge.install();
		AppHooksBridge.install();
	}
}
