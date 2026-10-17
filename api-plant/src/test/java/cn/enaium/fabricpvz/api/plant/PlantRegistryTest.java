package cn.enaium.fabricpvz.api.plant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.Board;
import cn.enaium.fabricpvz.portable.lawn.ImageID;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.Plant;
import cn.enaium.fabricpvz.portable.lawn.PlantSubClass;
import cn.enaium.fabricpvz.portable.lawn.ReanimationType;
import cn.enaium.fabricpvz.portable.lawn.Resources;
import cn.enaium.fabricpvz.portable.lawn.SeedType;
import cn.enaium.fabricpvz.portable.pvzp.fx.EffectSystem;
import cn.enaium.fabricpvz.portable.sexy.graphics.MemoryImage;

/**
 * Tests the mod-facing plant registry: id allocation outside the upstream seed range, the definition
 * row the game reads out of {@code Plant.gPlantDefs}, the {@link PlantHooks} the Mixins dispatch to,
 * and duplicate/invalid input handling.
 *
 * <p>The Mixins themselves only run under Fabric Loader, so the behaviour is asserted against
 * {@link PlantHooks} directly here; the real dispatch is covered by the game's loader end-to-end test.
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

		assertTrue(type.seedType() >= PlantRegistry.FIRST_SEED_TYPE);
		assertTrue(type.seedType() < SeedType.MAX_SEED_TYPES);
		assertEquals("test_peashooter_plus", type.id());
		assertEquals(1, PlantRegistry.count());
		assertSame(type, PlantRegistry.get(type.seedType()).orElseThrow());
		assertSame(type, PlantRegistry.get("test_peashooter_plus").orElseThrow());
		assertTrue(PlantRegistry.isCustomSeedType(type.seedType()));
		assertFalse(PlantRegistry.isCustomSeedType(SeedType.SEED_PEASHOOTER));
	}

	@Test
	void burstIsTwoOrFourAndDefaultsToOne() {
		// Default: one shot, and burstOf answers 0 for anything nobody registered.
		PlantType plain = registerPeaPlus();
		assertEquals(1, plain.definition().mBurst);
		assertEquals(1, PlantHooks.burstOf(plain.seedType()));
		assertEquals(0, PlantHooks.burstOf(SeedType.SEED_PEASHOOTER));
		assertEquals(0, PlantHooks.burstOf(SeedType.SEED_REPEATER));

		PlantType repeater = PlantType.builder("test_repeater")
				.subClass(PlantSubClass.SUBCLASS_SHOOTER).burst(2).register();
		assertEquals(2, PlantHooks.burstOf(repeater.seedType()));

		PlantType gatling = PlantType.builder("test_gatling")
				.subClass(PlantSubClass.SUBCLASS_SHOOTER).burst(4).register();
		assertEquals(4, PlantHooks.burstOf(gatling.seedType()));

		// Only 2 and 4 are a burst: 1 is the default (no call) and anything else is a mistake.
		assertThrows(IllegalArgumentException.class, () -> PlantType.builder("test_bad_one").burst(1));
		assertThrows(IllegalArgumentException.class, () -> PlantType.builder("test_bad_three").burst(3));
		assertThrows(IllegalArgumentException.class, () -> PlantType.builder("test_bad_zero").burst(0));
	}

	@Test
	void theGameSeesTheRegisteredDefinition() {
		PlantType type = registerPeaPlus();
		CustomPlantType definition = type.definition();

		assertEquals(type.seedType(), definition.mSeedType);
		assertEquals(125, definition.mSeedCost);
		assertEquals(400, definition.mPlantHealth);
		assertEquals(ReanimationType.REANIM_REPEATER, definition.mReanimationType);
		assertEquals(PlantSubClass.SUBCLASS_SHOOTER, definition.mSubClass);
		assertEquals(150, definition.mLaunchRate);
		assertNotNull(definition.mBehaviour, "every registered plant gets a behaviour");

		// The row written into Plant.gPlantDefs is what Plant::GetPlantDefinition hands out.
		assertSame(definition.mDefinition, Plant.gPlantDefs[type.seedType()]);
		assertSame(definition.mDefinition, Plant.getPlantDefinition(type.seedType()));
		assertEquals(type.seedType(), definition.mDefinition.mSeedType);
		assertEquals(125, definition.mDefinition.mSeedCost);
		assertEquals("TEST_PEASHOOTER_PLUS", definition.mDefinition.mPlantName);

		// Cost and recharge the game reads come from that row.
		assertEquals(125, definition.mDefinition.mSeedCost);
		assertEquals(750, Plant.getRefreshTime(type.seedType(), SeedType.SEED_NONE));

		// Upstream plants are unaffected.
		assertEquals(ReanimationType.REANIM_PEASHOOTER,
				Plant.getPlantDefinition(SeedType.SEED_PEASHOOTER).mReanimationType);
	}

	@Test
	void namesAndTooltipsComeFromTheRegistration() {
		PlantType type = registerPeaPlus();
		int seed = type.seedType();

		assertEquals("Pea Shooter+", PlantHooks.name(seed));
		assertEquals("Fires two peas.", PlantHooks.tooltip(seed));

		// Upstream seed types and unregistered ids answer "not ours", so the game keeps its own path.
		assertNull(PlantHooks.name(SeedType.SEED_PEASHOOTER));
		assertNull(PlantHooks.tooltip(SeedType.SEED_PEASHOOTER));

		// An unnamed registration falls back to the string file (name null), and tooltip to the name.
		PlantType unnamed = PlantType.builder("test_unnamed").register();
		assertNull(PlantHooks.name(unnamed.seedType()));
		assertNull(PlantHooks.tooltip(unnamed.seedType()));
	}

	@Test
	void almanacDescriptionsComeFromTheRegistration() {
		// A registration that describes itself: the almanac gets that text.
		PlantType type = PlantType.builder("test_described")
				.name("Described")
				.tooltip("Fires two peas.")
				.almanacDescription("Fires two peas at once.")
				.register();
		int seed = type.seedType();
		assertEquals("Fires two peas at once.", PlantHooks.almanacDescription(seed));
		assertEquals("Fires two peas at once.", type.definition().mAlmanacDescription);

		// No description: the tooltip (here the name, since the tooltip is empty too) stands in.
		PlantType noDescription = PlantType.builder("test_no_description").name("No Description").register();
		assertEquals("No Description", PlantHooks.almanacDescription(noDescription.seedType()));

		PlantType tooltipOnly = PlantType.builder("test_tooltip_only")
				.name("Tooltip Only").tooltip("Tooltip stands in.")
				.register();
		assertEquals("Tooltip stands in.", PlantHooks.almanacDescription(tooltipOnly.seedType()));

		// Nothing to say anywhere, and upstream seed types: the game keeps its own string file.
		PlantType silent = PlantType.builder("test_silent").register();
		assertNull(PlantHooks.almanacDescription(silent.seedType()));
		assertNull(PlantHooks.almanacDescription(SeedType.SEED_PEASHOOTER));
	}

	@Test
	void theRegisteredFlagsAreServedToTheGame() {
		PlantType type = PlantType.builder("test_flags")
				.name("Flags")
				.nocturnal(true)
				.fungus(true)
				.aquatic(true)
				.flying(true)
				.upgrade(true)
				.chooserVisible(true)
				.register();
		int seed = type.seedType();

		assertEquals(Boolean.TRUE, PlantHooks.isNocturnal(seed));
		assertEquals(Boolean.TRUE, PlantHooks.isFungus(seed));
		assertEquals(Boolean.TRUE, PlantHooks.isAquatic(seed));
		assertEquals(Boolean.TRUE, PlantHooks.isFlying(seed));
		assertEquals(Boolean.TRUE, PlantHooks.isUpgrade(seed));

		// Nothing registered under an upstream seed type: the game decides.
		assertNull(PlantHooks.isNocturnal(SeedType.SEED_PEASHOOTER));
		assertNull(PlantHooks.isAquatic(SeedType.SEED_PEASHOOTER));
	}

	@Test
	void upgradeFromIsServedToTheGameAndDeduplicated() {
		PlantType type = PlantType.builder("test_upgrade")
				.name("Upgrade")
				.upgrade(true)
				.upgradeFrom(SeedType.SEED_REPEATER, SeedType.SEED_MELONPULT, SeedType.SEED_REPEATER)
				.register();
		int seed = type.seedType();

		// The two seed types it declared, once each: the repeated one is not listed twice.
		assertEquals(2, type.definition().mUpgradeFrom.length);
		assertEquals(SeedType.SEED_REPEATER, type.definition().mUpgradeFrom[0]);
		assertEquals(SeedType.SEED_MELONPULT, type.definition().mUpgradeFrom[1]);

		// The dual of Plant::IsUpgradableTo: the plant on the square is asked about the seed being
		// planted, i.e. the declaration of the *target* is what decides.
		assertEquals(Boolean.TRUE, PlantHooks.isUpgradableTo(SeedType.SEED_REPEATER, seed));
		assertEquals(Boolean.TRUE, PlantHooks.isUpgradableTo(SeedType.SEED_MELONPULT, seed));
		assertEquals(Boolean.FALSE, PlantHooks.isUpgradableTo(SeedType.SEED_SUNFLOWER, seed),
				"a declared upgradeFrom that does not list the plant answers false, not null");

		// A registered target that declared nothing, and an upstream target: both leave the game on
		// its own hard-coded pairs.
		PlantType plain = PlantType.builder("test_plain").name("Plain").register();
		assertNull(PlantHooks.isUpgradableTo(SeedType.SEED_REPEATER, plain.seedType()));
		assertNull(PlantHooks.isUpgradableTo(SeedType.SEED_REPEATER, SeedType.SEED_GATLINGPEA));
	}

	@Test
	void theSeedIsAvailableOnlyWhenThePlantSaysSo() {
		PlantType type = registerPeaPlus();
		assertEquals(Boolean.TRUE, PlantHooks.hasSeedType(type.seedType()));

		PlantType hidden = PlantType.builder("test_hidden").name("Hidden").chooserVisible(false).register();
		assertEquals(Boolean.FALSE, PlantHooks.hasSeedType(hidden.seedType()));

		assertNull(PlantHooks.hasSeedType(SeedType.SEED_PEASHOOTER));
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

		Plant plant = new Plant();
		plant.mSeedType = type.seedType();

		PlantHooks.onPlanted(plant);
		assertEquals(List.of("placed"), calls);
		assertEquals(type.definition().mPlantHealth, plant.mPlantHealth,
				"the registered health replaces the upstream default");
		assertEquals(plant.mPlantHealth, plant.mPlantMaxHealth);

		PlantHooks.onTick(plant);
		assertEquals(List.of("placed", "tick"), calls);
	}

	@Test
	void theBehaviourCanReplaceTheCollisionRectangles() {
		PlantType type = PlantType.builder("test_rects")
				.name("Rects")
				.plantRect(plant -> new cn.enaium.fabricpvz.portable.sexy.Rect(1, 2, 3, 4))
				.plantAttackRect((plant, weapon) -> new cn.enaium.fabricpvz.portable.sexy.Rect(5, 6, 7, 8))
				.register();
		Plant plant = new Plant();
		plant.mSeedType = type.seedType();

		assertEquals(1, PlantHooks.plantRect(plant).mX);
		assertEquals(5, PlantHooks.plantAttackRect(plant, 0).mX);

		// A plant without the callbacks keeps the upstream rectangles.
		Plant plain = new Plant();
		plain.mSeedType = registerPeaPlus().seedType();
		assertNull(PlantHooks.plantRect(plain));
		assertNull(PlantHooks.plantAttackRect(plain, 0));
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
	}

	@Test
	void unregisterFreesThePlantAndTheHook() {
		PlantType type = registerPeaPlus();
		assertTrue(PlantRules.isInstalled());

		assertTrue(PlantRegistry.unregister(type.seedType()));
		assertFalse(PlantRegistry.get(type.seedType()).isPresent());
		assertEquals(0, PlantRegistry.count());
		assertNull(Plant.gPlantDefs[type.seedType()], "the definition row goes away with the plant");
		assertNull(Plant.getPlantDefinition(type.seedType()));
		assertFalse(PlantRules.isInstalled(), "the planting-rule hook goes away with the last plant");
		assertFalse(PlantRegistry.unregister(type.seedType()));
	}

	@Test
	void idsAreAllocatedInRegistrationOrderAndNeverReused() {
		PlantType first = PlantType.builder("test_first").name("First").register();
		PlantType second = PlantType.builder("test_second").name("Second").register();

		assertEquals(PlantRegistry.FIRST_SEED_TYPE, first.seedType());
		assertEquals(PlantRegistry.FIRST_SEED_TYPE + 1, second.seedType());
		assertNotSame(first, second);
		assertEquals(List.of(first, second), PlantRegistry.all());

		PlantRegistry.unregister(first.seedType());
		PlantType third = PlantType.builder("test_third").name("Third").register();

		assertEquals(PlantRegistry.FIRST_SEED_TYPE + 2, third.seedType(),
				"a freed slot is not reused, so old references stay valid");
	}

	// ------------------------------------------------------------------------------------------
	// Board restore dispatch (PlantHooks::onRestored)
	// ------------------------------------------------------------------------------------------

	/**
	 * A board whose plant array can be filled by hand. {@code Board}'s constructor is the only way to
	 * get one and it reads the app's effect system plus the seed-bank image, so those two are stubbed.
	 */
	private static Board stubBoard() {
		LawnApp aApp = new LawnApp();
		aApp.mEffectSystem = new EffectSystem();
		aApp.mEffectSystem.effectSystemInitialize();

		MemoryImage aStub = new MemoryImage(aApp);
		aStub.create(1, 1);
		Resources.setImageById(ImageID.IMAGE_SEEDBANK, aStub);

		return new Board(aApp);
	}

	private static Plant plantOn(Board theBoard, int theSeedType) {
		Plant aPlant = theBoard.mPlants.dataArrayAlloc();
		aPlant.mSeedType = theSeedType;
		return aPlant;
	}

	@Test
	void restoringABoardRerunsOnPlantedAndKeepsTheSavedHealth() {
		List<Plant> restored = new ArrayList<>();
		PlantType type = PlantType.builder("test_restored")
				.name("Restored")
				.reanimation(ReanimationType.REANIM_PEASHOOTER)
				.health(400)
				.onPlanted(restored::add)
				.register();

		Board board = stubBoard();
		Plant injured = plantOn(board, type.seedType());
		injured.mPlantHealth = 42;
		injured.mPlantMaxHealth = 275;

		// Neither a plant of an unregistered seed type nor a dead one of the registered type may call
		// back.
		plantOn(board, SeedType.SEED_PEASHOOTER);
		Plant dead = plantOn(board, type.seedType());
		dead.mDead = true;

		PlantHooks.onRestored(board);

		assertEquals(List.of(injured), restored, "only the live registered plant is restored");
		assertEquals(42, injured.mPlantHealth,
				"a load must not re-apply the registration's health over an injured plant");
		assertEquals(275, injured.mPlantMaxHealth, "the saved maximum is left alone as well");
	}

	@Test
	void restoringSkipsAPlantWhoseRegistrationHasNoBehaviour() {
		Board board = stubBoard();
		PlantType plain = PlantType.builder("test_plain_restored")
				.name("Plain")
				.reanimation(ReanimationType.REANIM_PEASHOOTER)
				.register();
		plantOn(board, plain.seedType());

		// An empty board and a registered plant without a behaviour: neither may throw.
		PlantHooks.onRestored(board);
		PlantHooks.onRestored(stubBoard());
	}
}
