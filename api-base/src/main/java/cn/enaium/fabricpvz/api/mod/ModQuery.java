package cn.enaium.fabricpvz.api.mod;

import java.util.Optional;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

/**
 * Read-only queries about the mods that Fabric Loader discovered and resolved.
 *
 * <p>These are static conveniences over the Fabric Loader API so that mods only need to depend on the
 * API module. Matching is done on the mod id as declared in {@code fabric.mod.json}; looking up an
 * unknown id is not an error, it simply yields {@link Optional#empty()} (or {@code false}).
 */
public final class ModQuery {

	private ModQuery() {
	}

	/**
	 * Returns whether a mod with the given id is currently loaded.
	 *
	 * @param id the mod id, for example {@code "fabric-api"}
	 * @return {@code true} if the mod was discovered and resolved
	 * @throws NullPointerException if {@code id} is {@code null}
	 */
	public static boolean isModLoaded(String id) {
		return FabricLoader.getInstance().isModLoaded(id);
	}

	/**
	 * Looks up a loaded mod by id.
	 *
	 * @param id the mod id
	 * @return the container of the mod, or empty when no loaded mod has that id
	 * @throws NullPointerException if {@code id} is {@code null}
	 */
	public static Optional<ModContainer> getModContainer(String id) {
		return FabricLoader.getInstance().getModContainer(id);
	}

	/**
	 * Looks up the version declared by a loaded mod.
	 *
	 * @param id the mod id
	 * @return the mod version as declared in its {@code fabric.mod.json}, or empty when the mod is not
	 *     loaded
	 * @throws NullPointerException if {@code id} is {@code null}
	 */
	public static Optional<String> getModVersion(String id) {
		return FabricLoader.getInstance()
				.getModContainer(id)
				.map(container -> container.getMetadata().getVersion().getFriendlyString());
	}
}
