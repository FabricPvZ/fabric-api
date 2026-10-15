package cn.enaium.fabricpvz.api.plant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.PlantSubClass;
import cn.enaium.fabricpvz.portable.lawn.ReanimationType;
import cn.enaium.fabricpvz.portable.lawn.SeedType;
import cn.enaium.fabricpvz.portable.lawn.content.CustomPlantType;
import cn.enaium.fabricpvz.portable.lawn.content.PlantTypeRegistry;

/**
 * Tests the mod-facing plant registry: id allocation outside the upstream seed range, the definition
 * the game reads, behaviour callbacks and duplicate/invalid input handling.
 */
class PlantRegistryTest {

	@AfterEach
	void clearRegistry() {
		PlantRegistry.reset();
	}

	private static PlantType registerPeaPlus() {
		return PlantType.builder("test_peashooter_plus")
				.name("Pea Shooter+")
				.tooltip("Fires two peas.")
				.almanacDescription("A test plant.")
				.cost(125)
				.refreshTime(750)
				.reanimation(ReanimationType.REANIM_REPEATER)
				.subClass(PlantSubClass.SUBCLASS_SHOOTER)
				.launchRate(150)
				.health(400)
				.register();
	}

	@Test
	void registersOutsideTheUpstreamSeedRange() {
		PlantType type = registerPeaPlus();

		assertTrue(type.seedType() >= SeedType.CUSTOM_SEED_FIRST);
		assertTrue(type.seedType() < SeedType.MAX_SEED_TYPES);
		assertEquals("test_peashooter_plus", type.id());
		assertEquals(1, PlantRegistry.count());
		assertSame(type, PlantRegistry.get(type.seedType()).orElseThrow());
		assertSame(type, PlantRegistry.get("test_peashooter_plus").orElseThrow());
		assertTrue(PlantRegistry.isCustomSeedType(type.seedType()));
		assertFalse(PlantRegistry.isCustomSeedType(SeedType.SEED_PEASHOOTER));
	}

	@Test
	void theGameSeesTheRegisteredDefinition() {
		PlantType type = registerPeaPlus();
		CustomPlantType definition = type.definition();

		assertEquals(type.seedType(), definition.mSeedType);
		assertSame(definition, PlantTypeRegistry.get(type.seedType()));
		assertEquals(125, definition.mSeedCost);
		assertEquals(400, definition.mPlantHealth);
		assertEquals(ReanimationType.REANIM_REPEATER, definition.mReanimationType);
		assertEquals(PlantSubClass.SUBCLASS_SHOOTER, definition.mSubClass);
		assertEquals(150, definition.mLaunchRate);

		// The index-aligned table row the game hands out from Plant::GetPlantDefinition.
		assertSame(definition.mDefinition, PlantTypeRegistry.definition(type.seedType()));
		assertEquals(type.seedType(), definition.mDefinition.mSeedType);
		assertEquals(125, definition.mDefinition.mSeedCost);
		assertNotNull(definition.mBehaviour, "every registered plant gets a behaviour");
	}

	@Test
	void namesAndAlmanacTextComeFromTheRegistration() {
		PlantType type = registerPeaPlus();
		int seed = type.seedType();

		assertEquals("Pea Shooter+", PlantTypeRegistry.displayName(seed));
		assertEquals("Fires two peas.", PlantTypeRegistry.tooltip(seed));
		assertEquals("A test plant.", PlantTypeRegistry.almanacDescription(seed));

		// The game reads them through its own accessors.
		assertEquals("Pea Shooter+",
				cn.enaium.fabricpvz.portable.lawn.Plant.getNameString(seed, SeedType.SEED_NONE));
		assertEquals("Fires two peas.", cn.enaium.fabricpvz.portable.lawn.Plant.getToolTip(seed));

		// Upstream plants are unaffected.
		assertEquals(ReanimationType.REANIM_PEASHOOTER,
				cn.enaium.fabricpvz.portable.lawn.Plant.getPlantDefinition(SeedType.SEED_PEASHOOTER).mReanimationType);
	}

	@Test
	void behaviourCallbacksRunForTheRegisteredPlant() {
		List<String> calls = new ArrayList<>();
		PlantType type = PlantType.builder("test_behaviour")
				.name("Behaviour Plant")
				.onTick(plant -> calls.add("tick"))
				.onPlanted(plant -> calls.add("placed"))
				.reanimation(ReanimationType.REANIM_PEASHOOTER)
				.register();

		cn.enaium.fabricpvz.portable.lawn.Plant plant = new cn.enaium.fabricpvz.portable.lawn.Plant();
		plant.mSeedType = type.seedType();

		PlantTypeRegistry.applyToPlant(plant);
		assertEquals(List.of("placed"), calls);
		assertEquals(type.definition().mPlantHealth, plant.mPlantHealth,
				"the registered health replaces the upstream default");
		assertEquals(plant.mPlantHealth, plant.mPlantMaxHealth);

		PlantTypeRegistry.updatePlant(plant);
		assertEquals(List.of("placed", "tick"), calls);
	}

	@Test
	void registeringTheSameIdTwiceFails() {
		registerPeaPlus();

		IllegalStateException failure = assertThrows(IllegalStateException.class, PlantRegistryTest::registerPeaPlus);
		assertTrue(failure.getMessage().contains("test_peashooter_plus"), failure.getMessage());
	}

	@Test
	void incompleteRegistrationsAreRejected() {
		IllegalArgumentException blankId = assertThrows(IllegalArgumentException.class,
				() -> PlantType.builder(" "));
		assertTrue(blankId.getMessage().contains("id"), blankId.getMessage());

		// The game-side registry rejects a definition that would have no way to name the plant.
		CustomPlantType anonymous = new CustomPlantType();
		IllegalArgumentException noName = assertThrows(IllegalArgumentException.class,
				() -> PlantTypeRegistry.register(anonymous));
		assertTrue(noName.getMessage().contains("name"), noName.getMessage());

		assertThrows(NullPointerException.class, () -> PlantTypeRegistry.register(null));
	}

	@Test
	void unregisterFreesThePlantAndTheHook() {
		PlantType type = registerPeaPlus();
		assertTrue(PlantRules.isInstalled());

		assertTrue(PlantRegistry.unregister(type.seedType()));
		assertFalse(PlantRegistry.get(type.seedType()).isPresent());
		assertEquals(0, PlantRegistry.count());
		assertFalse(PlantRules.isInstalled(), "the planting-rule hook goes away with the last plant");
		assertFalse(PlantRegistry.unregister(type.seedType()));
	}

	@Test
	void idsAreAllocatedInRegistrationOrderAndNeverReused() {
		PlantType first = PlantType.builder("test_first").name("First").register();
		PlantType second = PlantType.builder("test_second").name("Second").register();

		assertEquals(SeedType.CUSTOM_SEED_FIRST, first.seedType());
		assertEquals(SeedType.CUSTOM_SEED_FIRST + 1, second.seedType());
		assertNotSame(first, second);
		assertEquals(List.of(first, second), PlantRegistry.all());

		PlantRegistry.unregister(first.seedType());
		PlantType third = PlantType.builder("test_third").name("Third").register();

		assertEquals(SeedType.CUSTOM_SEED_FIRST + 2, third.seedType(),
				"a freed slot is not reused, so old references stay valid");
	}

	@Test
	void theApiRegistryAndTheGameRegistryAgree() {
		PlantType type = registerPeaPlus();

		assertEquals(PlantTypeRegistry.count(), PlantRegistry.count());
		assertSame(type.definition(), PlantTypeRegistry.all().get(0));
		assertEquals(PlantTypeRegistry.CAPACITY, SeedType.MAX_SEED_TYPES - SeedType.CUSTOM_SEED_FIRST);
	}
}
