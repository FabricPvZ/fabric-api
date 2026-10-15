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
import cn.enaium.fabricpvz.portable.lawn.HelmType;
import cn.enaium.fabricpvz.portable.lawn.ReanimationType;
import cn.enaium.fabricpvz.portable.lawn.Zombie;
import cn.enaium.fabricpvz.portable.lawn.content.CustomZombieType;
import cn.enaium.fabricpvz.portable.lawn.content.ZombieTypeRegistry;

/**
 * Tests the mod-facing zombie registry: id allocation outside the upstream zombie range, the
 * definition the game and the wave composer read, the armour numbers, head attachments and invalid
 * input handling.
 */
class ZombieRegistryTest {

	@AfterEach
	void clearRegistry() {
		ZombieRegistry.reset();
	}

	private static ZombieType registerRunner() {
		return ZombieType.builder("test_runner")
				.name("Runner Zombie")
				.almanacDescription("Runs.")
				.bodyReanimation(ReanimationType.REANIM_ZOMBIE)
				.health(400)
				.helm(HelmType.HELMTYPE_TRAFFIC_CONE, 370)
				.collider(new cn.enaium.fabricpvz.portable.sexy.Rect(30, 0, 50, 115))
				.wave(3, 4, 6, 2500)
				.register();
	}

	@Test
	void registersOutsideTheUpstreamZombieRange() {
		ZombieType type = registerRunner();

		assertTrue(type.zombieType() >= cn.enaium.fabricpvz.portable.lawn.ZombieType.CUSTOM_ZOMBIE_FIRST);
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
		assertTrue(definition.mWaveEnabled);
		assertNotNull(definition.mBehaviour);

		// Zombie::GetZombieDefinition resolves it, including the numbers the wave composer reads.
		var zombieDefinition = Zombie.getZombieDefinition(type.zombieType());
		assertSame(definition.mDefinition, zombieDefinition);
		assertEquals(3, zombieDefinition.mZombieValue);
		assertEquals(4, zombieDefinition.mStartingLevel);
		assertEquals(6, zombieDefinition.mFirstAllowedWave);
		assertEquals(2500, zombieDefinition.mPickWeight);
		assertEquals(ReanimationType.REANIM_ZOMBIE, zombieDefinition.mReanimationType);

		// Upstream zombies are untouched by a registration.
		var normal = Zombie.getZombieDefinition(cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_NORMAL);
		assertEquals(cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_NORMAL, normal.mZombieType);
		assertEquals(1, normal.mZombieValue);
		assertSame(normal, Zombie.getZombieDefinition(cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_NORMAL));
	}

	@Test
	void namesComeFromTheRegistration() {
		ZombieType type = registerRunner();

		assertEquals("Runner Zombie", ZombieTypeRegistry.displayName(type.zombieType()));
		assertEquals("Runs.", ZombieTypeRegistry.almanacDescription(type.zombieType()));
		assertNull(ZombieTypeRegistry.displayName(cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_NORMAL));
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

		CustomZombieType anonymous = new CustomZombieType();
		anonymous.mBodyHealth = 100;
		assertThrows(IllegalArgumentException.class, () -> ZombieTypeRegistry.register(anonymous));

		CustomZombieType noHealth = new CustomZombieType();
		noHealth.mName = "No Health";
		noHealth.mBodyHealth = 0;
		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
				() -> ZombieTypeRegistry.register(noHealth));
		assertTrue(failure.getMessage().contains("health"), failure.getMessage());

		assertThrows(NullPointerException.class, () -> ZombieTypeRegistry.register(null));
	}

	@Test
	void unregisterFreesTheZombie() {
		ZombieType type = registerRunner();

		assertTrue(ZombieRegistry.unregister(type.zombieType()));
		assertFalse(ZombieRegistry.get(type.zombieType()).isPresent());
		assertEquals(0, ZombieRegistry.count());
		assertFalse(ZombieRegistry.unregister(type.zombieType()));
	}

	@Test
	void idsAreAllocatedInRegistrationOrderAndNeverReused() {
		ZombieType first = ZombieType.builder("test_a").name("A").register();
		ZombieType second = ZombieType.builder("test_b").name("B").register();

		assertEquals(cn.enaium.fabricpvz.portable.lawn.ZombieType.CUSTOM_ZOMBIE_FIRST, first.zombieType());
		assertEquals(cn.enaium.fabricpvz.portable.lawn.ZombieType.CUSTOM_ZOMBIE_FIRST + 1, second.zombieType());
		assertEquals(List.of(first, second), ZombieRegistry.all());

		ZombieRegistry.unregister(first.zombieType());
		ZombieType third = ZombieType.builder("test_c").name("C").register();

		assertEquals(cn.enaium.fabricpvz.portable.lawn.ZombieType.CUSTOM_ZOMBIE_FIRST + 2, third.zombieType(),
				"a freed slot is not reused, so old references stay valid");
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

		Zombie zombie = new Zombie();
		zombie.mZombieType = type.zombieType();

		// These are the exact calls Zombie::ZombieInitialize and Zombie::Update make.
		type.definition().mBehaviour.onSpawned(zombie);
		type.definition().mBehaviour.onUpdate(zombie);
		assertEquals(List.of("spawned", "tick"), calls);

		ZombieTypeRegistry.updateZombie(zombie);
		assertEquals(List.of("spawned", "tick", "tick"), calls);
	}
}
