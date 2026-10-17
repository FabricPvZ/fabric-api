package cn.enaium.fabricpvz.api.zombie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.Board;
import cn.enaium.fabricpvz.portable.lawn.HelmType;
import cn.enaium.fabricpvz.portable.lawn.ImageID;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.ReanimationType;
import cn.enaium.fabricpvz.portable.lawn.Resources;
import cn.enaium.fabricpvz.portable.lawn.Zombie;
import cn.enaium.fabricpvz.portable.pvzp.fx.EffectSystem;
import cn.enaium.fabricpvz.portable.sexy.graphics.MemoryImage;

/**
 * Tests the mod-facing zombie registry: id allocation outside the upstream zombie range, the definition
 * row the game and the wave composer read out of {@code Zombie.gZombieDefs}, the armour numbers, head
 * attachments and the {@link ZombieHooks} the Mixins dispatch to.
 *
 * <p>The Mixins themselves only run under Fabric Loader, so the behaviour is asserted against
 * {@link ZombieHooks} directly here; the real dispatch is covered by the game's loader end-to-end test.
 */
class ZombieRegistryTest {

	@AfterEach
	void clearRegistry() {
		ZombieRegistry.reset();
	}

	private static ZombieType registerRunner() {
		return ZombieType.builder("test_runner")
				.name("Runner Zombie")
				.bodyReanimation(ReanimationType.REANIM_ZOMBIE)
				.health(400)
				.helm(HelmType.HELMTYPE_TRAFFIC_CONE, 370)
				.collider(new cn.enaium.fabricpvz.portable.sexy.Rect(30, 0, 50, 115))
				.wave(3, 4, 6, 2500)
				.register();
	}

	/**
	 * A zombie whose reanimation work is stubbed out: this test has no game app to load a reanimation
	 * with, so the two {@link ZombieHooks#onSpawned} calls that reach into the app are no-ops here.
	 */
	private static Zombie offBoardZombie(int theZombieType) {
		Zombie zombie = new Zombie() {
			@Override
			public void loadPlainZombieReanim() {
			}

			@Override
			public void attachShield() {
			}
		};
		zombie.mZombieType = theZombieType;
		return zombie;
	}

	@Test
	void registersOutsideTheUpstreamZombieRange() {
		ZombieType type = registerRunner();

		assertTrue(type.zombieType() >= ZombieRegistry.FIRST_ZOMBIE_TYPE);
		assertTrue(type.zombieType() < cn.enaium.fabricpvz.portable.lawn.ZombieType.MAX_ZOMBIE_TYPES);
		assertEquals("test_runner", type.id());
		assertEquals(1, ZombieRegistry.count());
		assertSame(type, ZombieRegistry.get(type.zombieType()).orElseThrow());
		assertSame(type, ZombieRegistry.get("test_runner").orElseThrow());
		assertTrue(ZombieRegistry.isCustomZombieType(type.zombieType()));
		assertFalse(ZombieRegistry.isCustomZombieType(cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_NORMAL));
	}

	@Test
	void theGameSeesTheRegisteredDefinition() {
		ZombieType type = registerRunner();
		CustomZombieType definition = type.definition();

		assertEquals(400, definition.mBodyHealth);
		assertEquals(HelmType.HELMTYPE_TRAFFIC_CONE, definition.mHelmType);
		assertEquals(370, definition.mHelmHealth);
		assertEquals(30, definition.mZombieRect.mX);
		assertNotNull(definition.mBehaviour);

		// The row written into Zombie.gZombieDefs is what Zombie::GetZombieDefinition hands out, and
		// what the wave composer reads.
		assertSame(definition.mDefinition, Zombie.gZombieDefs[type.zombieType()]);
		assertSame(definition.mDefinition, Zombie.getZombieDefinition(type.zombieType()));
		assertEquals(3, definition.mDefinition.mZombieValue);
		assertEquals(4, definition.mDefinition.mStartingLevel);
		assertEquals(6, definition.mDefinition.mFirstAllowedWave);
		assertEquals(2500, definition.mDefinition.mPickWeight);
		assertEquals(ReanimationType.REANIM_ZOMBIE, definition.mDefinition.mReanimationType);

		// Upstream zombies are untouched by a registration.
		var normal = Zombie.getZombieDefinition(cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_NORMAL);
		assertEquals(cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_NORMAL, normal.mZombieType);
		assertEquals(1, normal.mZombieValue);
		assertSame(normal, Zombie.getZombieDefinition(cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_NORMAL));

		// A type nobody registered answers "not ours".
		assertNull(ZombieHooks.canGoInPool(cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_NORMAL));
	}

	@Test
	void armourAndHeadAreAppliedByTheGame() {
		ZombieType type = ZombieType.builder("test_headed")
				.name("Headed")
				.bodyReanimation(ReanimationType.REANIM_ZOMBIE)
				.health(500)
				.helm(HelmType.HELMTYPE_PAIL, 1100)
				.shield(cn.enaium.fabricpvz.portable.lawn.ShieldType.SHIELDTYPE_DOOR, 900)
				.flyingHealth(20)
				.headReanimation(ReanimationType.REANIM_PEASHOOTER)
				.headBodyTrack("anim_head1")
				.headHiddenTrack("anim_head1")
				.headHiddenLayer("anim_hair")
				.headTransform(65.0f, -5.0f, 0.2f, 1.0f)
				.register();

		CustomZombieType definition = type.definition();
		assertEquals(500, definition.mBodyHealth);
		assertEquals(1100, definition.mHelmHealth);
		assertEquals(900, definition.mShieldHealth);
		assertEquals(20, definition.mFlyingHealth);

		assertNotNull(definition.mHead);
		assertEquals(ReanimationType.REANIM_PEASHOOTER, definition.mHead.mReanimationType);
		assertEquals("anim_head1", definition.mHead.mBodyTrack);
		assertEquals("anim_hair", definition.mHead.mHiddenLayer);
		assertEquals(65.0f, definition.mHead.mOffsetX);

		// ZombieHooks.onSpawned is what Zombie::ZombieInitialize calls; the health and armour numbers
		// land on the zombie from the definition.
		Zombie zombie = offBoardZombie(type.zombieType());
		ZombieHooks.onSpawned(zombie);
		assertEquals(500, zombie.mBodyHealth);
		assertEquals(HelmType.HELMTYPE_PAIL, zombie.mHelmType);
		assertEquals(1100, zombie.mHelmHealth);
		assertEquals(20, zombie.mFlyingHealth);
	}

	@Test
	void almanacNamesAndDescriptionsComeFromTheRegistration() {
		// A registration that describes itself: the almanac gets that text under that name.
		ZombieType type = ZombieType.builder("test_described")
				.name("Runner Zombie")
				.bodyReanimation(ReanimationType.REANIM_ZOMBIE)
				.almanacDescription("Runs at the plants.")
				.register();
		assertEquals("Runner Zombie", ZombieHooks.name(type.zombieType()));
		assertEquals("Runs at the plants.", ZombieHooks.almanacDescription(type.zombieType()));
		assertEquals("Runs at the plants.", type.definition().mAlmanacDescription);

		// No description: the name stands in.
		assertEquals("Runner Zombie", ZombieHooks.almanacDescription(registerRunner().zombieType()));

		// Nothing to say anywhere, and upstream zombie types: the game keeps its own string file.
		ZombieType silent = ZombieType.builder("test_silent").register();
		assertNull(ZombieHooks.name(silent.zombieType()));
		assertNull(ZombieHooks.almanacDescription(silent.zombieType()));
		assertNull(ZombieHooks.almanacDescription(cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_NORMAL));
	}

	@Test
	void canGoInPoolComesFromTheRegistration() {
		ZombieType type = ZombieType.builder("test_pool")
				.name("Pool")
				.bodyReanimation(ReanimationType.REANIM_ZOMBIE)
				.canGoInPool(true)
				.register();

		assertEquals(Boolean.TRUE, ZombieHooks.canGoInPool(type.zombieType()));
	}

	@Test
	void behaviourCallbacksRunForTheRegisteredZombie() {
		List<String> calls = new ArrayList<>();
		ZombieType type = ZombieType.builder("test_behaviour")
				.name("Behaviour")
				.bodyReanimation(ReanimationType.REANIM_ZOMBIE)
				.onSpawned(zombie -> calls.add("spawned"))
				.onTick(zombie -> calls.add("tick"))
				.register();

		Zombie zombie = offBoardZombie(type.zombieType());

		// These are the exact calls Zombie::ZombieInitialize and Zombie::Update make through the Mixins.
		ZombieHooks.onSpawned(zombie);
		ZombieHooks.onTick(zombie);
		assertEquals(List.of("spawned", "tick"), calls);
	}

	@Test
	void spawningUsesTheBoardsOwnPath() {
		ZombieType type = registerRunner();
		assertEquals(type.zombieType(), ZombieRegistry.get(type.zombieType()).orElseThrow().zombieType());
		assertEquals(-3, ZombieRegistry.WAVE_UI);
	}

	@Test
	void registeringTheSameIdTwiceFails() {
		registerRunner();

		IllegalStateException failure = assertThrows(IllegalStateException.class, ZombieRegistryTest::registerRunner);
		assertTrue(failure.getMessage().contains("test_runner"), failure.getMessage());
	}

	@Test
	void incompleteRegistrationsAreRejected() {
		IllegalArgumentException blankId = assertThrows(IllegalArgumentException.class,
				() -> ZombieType.builder(""));
		assertTrue(blankId.getMessage().contains("id"), blankId.getMessage());
	}

	@Test
	void unregisterFreesTheZombie() {
		ZombieType type = registerRunner();

		assertTrue(ZombieRegistry.unregister(type.zombieType()));
		assertFalse(ZombieRegistry.get(type.zombieType()).isPresent());
		assertEquals(0, ZombieRegistry.count());
		assertNull(Zombie.gZombieDefs[type.zombieType()], "the definition row goes away with the zombie");
		assertNull(Zombie.getZombieDefinition(type.zombieType()));
		assertFalse(ZombieRegistry.unregister(type.zombieType()));
	}

	@Test
	void idsAreAllocatedInRegistrationOrderAndNeverReused() {
		ZombieType first = ZombieType.builder("test_a").name("A").register();
		ZombieType second = ZombieType.builder("test_b").name("B").register();

		assertEquals(ZombieRegistry.FIRST_ZOMBIE_TYPE, first.zombieType());
		assertEquals(ZombieRegistry.FIRST_ZOMBIE_TYPE + 1, second.zombieType());
		assertEquals(List.of(first, second), ZombieRegistry.all());

		ZombieRegistry.unregister(first.zombieType());
		ZombieType third = ZombieType.builder("test_c").name("C").register();

		assertEquals(ZombieRegistry.FIRST_ZOMBIE_TYPE + 2, third.zombieType(),
				"a freed slot is not reused, so old references stay valid");
	}

	// ------------------------------------------------------------------------------------------
	// Board restore dispatch (ZombieHooks::onRestored)
	// ------------------------------------------------------------------------------------------

	/**
	 * A board whose zombie array can be filled by hand. {@code Board}'s constructor is the only way to
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

	private static Zombie zombieOn(Board theBoard, int theZombieType) {
		Zombie aZombie = theBoard.mZombies.dataArrayAlloc();
		aZombie.mZombieType = theZombieType;
		return aZombie;
	}

	@Test
	void restoringABoardRerunsOnSpawnedAndKeepsTheSavedDamage() {
		List<Zombie> restored = new ArrayList<>();
		ZombieType type = ZombieType.builder("test_restored")
				.name("Restored")
				.bodyReanimation(ReanimationType.REANIM_ZOMBIE)
				.health(400)
				.helm(HelmType.HELMTYPE_TRAFFIC_CONE, 370)
				.onSpawned(restored::add)
				.register();

		Board board = stubBoard();
		Zombie injured = zombieOn(board, type.zombieType());
		injured.mBodyHealth = 123;
		injured.mBodyMaxHealth = 400;
		injured.mHelmType = HelmType.HELMTYPE_TRAFFIC_CONE;
		injured.mHelmHealth = 20;
		injured.mHelmMaxHealth = 370;

		// Neither a zombie of an unregistered type nor a dead one of the registered type may call back.
		zombieOn(board, cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_NORMAL);
		Zombie dead = zombieOn(board, type.zombieType());
		dead.mDead = true;

		ZombieHooks.onRestored(board);

		assertEquals(List.of(injured), restored, "only the live registered zombie is restored");
		assertEquals(123, injured.mBodyHealth, "a load must not heal an injured zombie");
		assertEquals(400, injured.mBodyMaxHealth, "the saved maximum is left alone as well");
		assertEquals(HelmType.HELMTYPE_TRAFFIC_CONE, injured.mHelmType,
				"the armour the save carried is left alone");
		assertEquals(20, injured.mHelmHealth);
		assertEquals(370, injured.mHelmMaxHealth);
	}

	@Test
	void restoringSkipsAZombieWhoseRegistrationHasNoBehaviour() {
		Board board = stubBoard();
		ZombieType plain = ZombieType.builder("test_plain_restored")
				.name("Plain")
				.bodyReanimation(ReanimationType.REANIM_ZOMBIE)
				.register();
		zombieOn(board, plain.zombieType());

		// An empty board and a registered zombie without a behaviour: neither may throw.
		ZombieHooks.onRestored(board);
		ZombieHooks.onRestored(stubBoard());
	}
}
