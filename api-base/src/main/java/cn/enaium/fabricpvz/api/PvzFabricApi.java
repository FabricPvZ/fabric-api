package cn.enaium.fabricpvz.api;

import java.util.List;
import java.util.Optional;

import cn.enaium.fabricpvz.api.mod.ModQuery;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

/**
 * Entry facade of the PvZ Fabric API.
 *
 * <p>Mods depend on the aggregator ({@code cn.enaium.fabricpvz:fabric-api}), which pulls in this
 * module and its siblings ({@code api-events}, {@code api-plant}, {@code api-zombie}),
 * to reach the event bus ({@code cn.enaium.fabricpvz.api.event}, our own copy of the Fabric API base
 * {@code Event}/{@code EventFactory} surface, plus the events of
 * {@code cn.enaium.fabricpvz.api.lawn} and {@code .lifecycle}), the custom content registries and the
 * mod queries below.
 *
 * <p>The API is itself a set of mods: each module ships a {@code fabric.mod.json}, is discovered like
 * any other mod, and the {@code main} entry point of this module ({@link ApiModInitializer}) flips
 * {@link #isInitialized()} to {@code true}. Code that needs the API can therefore check
 * {@link #isInitialized()} to detect a missing or disabled API jar.
 */
public final class PvzFabricApi {

	/**
	 * Mod id of the API aggregator, as declared in the aggregator's {@code fabric.mod.json}. This is the
	 * id mods declare in their {@code depends} block.
	 */
	public static final String MOD_ID = "fabric-api";

	/**
	 * Mod id of the base module, which owns the {@code main} entry point that flips
	 * {@link #isInitialized()}.
	 */
	public static final String BASE_MOD_ID = "api-base";

	/**
	 * Version of this API build. Kept in sync with the {@code version} field of every module's
	 * {@code fabric.mod.json} and reported by {@link #getVersion()} once the API entry point has run.
	 */
	public static final String VERSION = "1.0.0";

	private PvzFabricApi() {
	}

	/**
	 * Returns whether the API's own {@code main} entry point has been executed.
	 *
	 * @return {@code true} once {@link ApiModInitializer#onInitialize()} has completed
	 */
	public static boolean isInitialized() {
		return ApiState.isInitialized();
	}

	/**
	 * Returns the API version registered by {@link ApiModInitializer}.
	 *
	 * @return the API version, or {@code null} before the API entry point has run
	 */
	public static String getVersion() {
		return ApiState.version();
	}

	/**
	 * Returns every mod Fabric Loader discovered and resolved, including the game and loader built-ins.
	 *
	 * @return an immutable snapshot of the loaded mod containers, in the order reported by Fabric Loader
	 */
	public static List<ModContainer> getMods() {
		return List.copyOf(FabricLoader.getInstance().getAllMods());
	}

	/**
	 * Looks up a loaded mod by id.
	 *
	 * @param id the mod id, for example {@link #MOD_ID}
	 * @return the container of the mod, or empty when no loaded mod has that id
	 * @throws NullPointerException if {@code id} is {@code null}
	 */
	public static Optional<ModContainer> getModContainer(String id) {
		return ModQuery.getModContainer(id);
	}

	/**
	 * Returns the already instantiated entry points published under a key.
	 *
	 * <p>Keys are the entry point keys of {@code fabric.mod.json} ({@code preLaunch}, {@code main},
	 * {@code client}); instances are produced once during startup, so every call returns the same
	 * objects, in mod load order.
	 *
	 * @param key the entry point key
	 * @param type the entry point interface the returned instances implement
	 * @param <T> the entry point type
	 * @return the instantiated entry points of every mod, in mod order; possibly empty
	 * @throws NullPointerException if {@code key} or {@code type} is {@code null}
	 */
	public static <T> List<T> getEntrypoints(String key, Class<T> type) {
		return FabricLoader.getInstance().getEntrypoints(key, type);
	}
}
