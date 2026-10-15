package cn.enaium.fabricpvz.api;

/**
 * Mutable initialization state of the API mod, written once by {@link ApiModInitializer} when the
 * loader invokes the API's {@code main} entry point and read through {@link PvzFabricApi}.
 *
 * <p>Package-private on purpose: the only supported view of this state is {@link PvzFabricApi}.
 */
final class ApiState {

	private static volatile boolean initialized;
	private static volatile String version;

	private ApiState() {
	}

	/**
	 * Records that the API entry point ran and which version it reported.
	 *
	 * @param apiVersion the API version being initialized
	 */
	static void markInitialized(String apiVersion) {
		version = apiVersion;
		initialized = true;
	}

	/**
	 * @return whether {@link #markInitialized(String)} has been called
	 */
	static boolean isInitialized() {
		return initialized;
	}

	/**
	 * @return the registered API version, or {@code null} when the API is not initialized
	 */
	static String version() {
		return version;
	}

	/**
	 * Clears the state, so that tests can exercise the entry point in isolation.
	 */
	static void reset() {
		version = null;
		initialized = false;
	}
}
