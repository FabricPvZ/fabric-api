package cn.enaium.fabricpvz.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import net.fabricmc.api.ModInitializer;

/**
 * Tests that the base module's mod entry point flips the API initialization state.
 */
class ApiModInitializerTest {

	@BeforeEach
	@AfterEach
	void clearState() {
		ApiState.reset();
	}

	@Test
	void initializingTheApiMarksItAsInitializedAndRegistersItsVersion() {
		assertFalse(PvzFabricApi.isInitialized());
		assertNull(PvzFabricApi.getVersion());

		new ApiModInitializer().onInitialize();

		assertTrue(PvzFabricApi.isInitialized());
		assertEquals(PvzFabricApi.VERSION, PvzFabricApi.getVersion());
		assertEquals("1.2.2", PvzFabricApi.VERSION);
	}

	@Test
	void initializingTwiceKeepsTheSameState() {
		ApiModInitializer initializer = new ApiModInitializer();

		initializer.onInitialize();
		String version = PvzFabricApi.getVersion();
		initializer.onInitialize();

		assertTrue(PvzFabricApi.isInitialized());
		assertEquals(version, PvzFabricApi.getVersion());
	}

	@Test
	void apiModIdAndBaseModIdAreDistinct() {
		assertEquals("fabric-api", PvzFabricApi.MOD_ID);
		assertEquals("api-base", PvzFabricApi.BASE_MOD_ID);
	}

	@Test
	void entryPointIsReflectivelyInstantiableWithTheLoaderContract() throws Exception {
		assertTrue(ModInitializer.class.isAssignableFrom(ApiModInitializer.class));
		assertEquals(0, ApiModInitializer.class.getDeclaredConstructor().getParameterCount());

		ModInitializer instance =
				(ModInitializer) ApiModInitializer.class.getDeclaredConstructor().newInstance();
		instance.onInitialize();

		assertTrue(PvzFabricApi.isInitialized());
	}
}
