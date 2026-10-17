package cn.enaium.fabricpvz.api.zombie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Iterator;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.HelmType;
import cn.enaium.fabricpvz.portable.lawn.Zombie;

/**
 * Tests {@link ZombieOverrides}: the handle lookup, the recorded fields and what {@link
 * ZombieOverrides#apply(Zombie)} does to a zombie, plus the type replacement the Mixin reads.
 *
 * <p>As in {@link ZombieRegistryTest}, the Mixins only run under Fabric Loader, so the behaviour is
 * asserted against the facade directly here; the real dispatch through
 * {@code Zombie::ZombieInitialize} is covered by the 95 Edition mod's loader end-to-end test.
 */
class ZombieOverridesTest {

	@AfterEach
	void clearOverrides() {
		ZombieOverrides.reset();
	}

	@Test
	void handlesAreIdempotentAndOrdered() {
		ZombieOverride aPail = ZombieOverrides.of(cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_PAIL);
		ZombieOverride aFlag = ZombieOverrides.of(cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_FLAG);

		assertSame(aPail, ZombieOverrides.of(cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_PAIL),
				"asking twice gives the same handle");
		assertEquals(2, ZombieOverrides.registered().size());

		Iterator<Map.Entry<Integer, ZombieOverride>> aEntries = ZombieOverrides.registered().entrySet().iterator();
		assertEquals(cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_PAIL, aEntries.next().getKey());
		assertEquals(cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_FLAG, aEntries.next().getKey());

		ZombieOverrides.reset();
		assertTrue(ZombieOverrides.registered().isEmpty());
		assertNotSame(aFlag, ZombieOverrides.of(cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_FLAG),
				"a cleared registry hands out a fresh handle");
	}

	@Test
	void onlyZombieTypesAreAccepted() {
		assertThrows(IllegalArgumentException.class, () -> ZombieOverrides.of(-1));
		assertThrows(IllegalArgumentException.class,
				() -> ZombieOverrides.of(cn.enaium.fabricpvz.portable.lawn.ZombieType.MAX_ZOMBIE_TYPES));
		assertThrows(IllegalArgumentException.class, () -> ZombieOverrides.of(1).bodyHealth(-1));
		assertThrows(IllegalArgumentException.class, () -> ZombieOverrides.of(1).helm(HelmType.HELMTYPE_PAIL, -1));
		assertThrows(IllegalArgumentException.class, () -> ZombieOverrides.of(1).shieldHealth(-1));
		assertThrows(IllegalArgumentException.class, () -> ZombieOverrides.of(1).bodyAnimation(" "));
		assertThrows(IllegalArgumentException.class, () -> ZombieOverrides.of(1).bodyAnimation(null));
		assertThrows(IllegalArgumentException.class, () -> ZombieOverrides.of(1).spawnsAs(-1));
	}

	@Test
	void theRecordedFieldsAreReadable() {
		ZombieOverride aOverride = ZombieOverrides.of(cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_FLAG);

		assertNull(aOverride.bodyHealth());
		assertNull(aOverride.helmType());
		assertNull(aOverride.bodyAnimation());

		aOverride.bodyHealth(820).helm(HelmType.HELMTYPE_PAIL, 1100).bodyAnimation("anim_bucket");

		assertEquals(cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_FLAG, aOverride.zombieType());
		assertEquals(820, aOverride.bodyHealth());
		assertEquals(HelmType.HELMTYPE_PAIL, aOverride.helmType());
		assertEquals(1100, aOverride.helmHealth());
		assertEquals("anim_bucket", aOverride.bodyAnimation());
		assertTrue(aOverride.toString().contains("bodyHealth=820"), aOverride.toString());
		assertTrue(aOverride.toString().contains("helm=2/1100"), aOverride.toString());

		// The last value written is the one that counts.
		aOverride.bodyHealth(900);
		assertEquals(900, aOverride.bodyHealth());
	}

	@Test
	void theNumbersLandOnTheZombie() {
		ZombieOverrides.of(cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_FLAG)
				.bodyHealth(820)
				.helm(HelmType.HELMTYPE_PAIL, 1100);

		Zombie aZombie = spawnedZombie(cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_FLAG, 270, HelmType.HELMTYPE_NONE, 0);

		ZombieOverrides.apply(aZombie);

		assertEquals(820, aZombie.mBodyHealth);
		assertEquals(820, aZombie.mBodyMaxHealth, "the maximum follows a health the game derived it from");
		assertEquals(HelmType.HELMTYPE_PAIL, aZombie.mHelmType);
		assertEquals(1100, aZombie.mHelmHealth);
		assertEquals(1100, aZombie.mHelmMaxHealth);
	}

	@Test
	void aDeliberatelyDecoupledMaximumIsKept() {
		// The Zombiquarium spawns zombies whose maximum (300) says nothing about their health (200).
		ZombieOverrides.of(cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_DANCER).bodyHealth(1350);
		Zombie aZombie = spawnedZombie(cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_DANCER, 200, HelmType.HELMTYPE_NONE, 0);
		aZombie.mBodyMaxHealth = 300;

		ZombieOverrides.apply(aZombie);

		assertEquals(1350, aZombie.mBodyHealth);
		assertEquals(300, aZombie.mBodyMaxHealth, "a maximum the game decoupled on purpose stays");
	}

	@Test
	void aTypeNobodyOverrodeIsUntouched() {
		ZombieOverrides.of(cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_DANCER).bodyHealth(1350);

		Zombie aNormal = spawnedZombie(cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_NORMAL, 270, HelmType.HELMTYPE_NONE, 0);
		aNormal.mShieldHealth = 150;

		ZombieOverrides.apply(aNormal);

		assertEquals(270, aNormal.mBodyHealth);
		assertEquals(270, aNormal.mBodyMaxHealth);
		assertEquals(HelmType.HELMTYPE_NONE, aNormal.mHelmType);
		assertEquals(0, aNormal.mHelmHealth);
		assertEquals(150, aNormal.mShieldHealth);
	}

	@Test
	void phaseCounterZeroIsApplied() {
		ZombieOverrides.of(cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_BUNGEE).phaseCounter(0);
		Zombie aZombie = spawnedZombie(cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_BUNGEE, 450, HelmType.HELMTYPE_NONE, 0);
		aZombie.mPhaseCounter = 300;

		ZombieOverrides.apply(aZombie);

		assertEquals(0, aZombie.mPhaseCounter, "0 is a value, not 'unset'");
	}

	@Test
	void theTypeReplacementIsHandedToTheMixin() {
		assertEquals(cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_DOOR,
				ZombieOverrides.remapType(cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_DOOR),
				"a type nobody replaced spawns as itself");

		ZombieOverrides.of(cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_DOOR)
				.spawnsAs(cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_PAIL);

		assertEquals(cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_PAIL,
				ZombieOverrides.remapType(cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_DOOR));
		assertEquals(cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_NORMAL,
				ZombieOverrides.remapType(cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_NORMAL));

		// The replacement is only ever applied to the type the game is spawning, so the override of the
		// replacement type is the one the zombie's numbers come from.
		assertEquals(1, ZombieOverrides.registered().size());
	}

	@Test
	void aRegisteredOverrideIsASnapshot() {
		ZombieOverrides.of(cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_PAIL).helm(HelmType.HELMTYPE_PAIL, 1100);
		Map<Integer, ZombieOverride> aRegistered = ZombieOverrides.registered();

		ZombieOverrides.of(cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_NORMAL).bodyHealth(1);

		assertFalse(aRegistered.containsKey(cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_NORMAL));
		assertThrows(UnsupportedOperationException.class,
				() -> aRegistered.put(cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_NORMAL, null));
	}

	@Test
	void aBodyAnimationWithoutAnAppIsIgnored() {
		ZombieOverrides.of(cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_FLAG).bodyAnimation("anim_bucket");
		Zombie aZombie = spawnedZombie(cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_FLAG, 820, HelmType.HELMTYPE_NONE, 0);

		// No app to hold a reanimation (which is the case in this unit test): showing the animation is
		// what needs one, and the rest of the override still applies.
		ZombieOverrides.apply(aZombie);

		assertEquals(820, aZombie.mBodyHealth);
	}

	@Test
	void mindControlBodyHealthIsRecordedPerType() {
		ZombieOverride aNewspaper = ZombieOverrides.of(cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_NEWSPAPER);
		assertNull(aNewspaper.mindControlBodyHealth(), "nobody set one");

		aNewspaper.mindControlBodyHealth(920);
		assertEquals(920, aNewspaper.mindControlBodyHealth());
		assertEquals(920, ZombieOverrides.mindControlBodyHealth(
				cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_NEWSPAPER), "the hook's read side");
		assertNull(ZombieOverrides.mindControlBodyHealth(cn.enaium.fabricpvz.portable.lawn.ZombieType.ZOMBIE_NORMAL),
				"a type nobody set this on gets the boost's bonus instead");

		aNewspaper.mindControlBodyHealth(0);
		assertNull(aNewspaper.mindControlBodyHealth(), "a non-positive value means 'leave the bonus in charge'");
	}

	@Test
	void theWholeGameRulesDefaultToTheGamesOwn() {
		assertFalse(ZombieOverrides.mindControlBoosted());
		assertEquals(0, ZombieOverrides.mindControlBodyBonus());
		assertEquals(0, ZombieOverrides.mindControlHelmBonus());
		assertEquals(1.0f, ZombieOverrides.mindControlScale());
		assertEquals(0, ZombieOverrides.burnImmunityTotalHealth(), "the game's own body-health test");
		assertFalse(ZombieOverrides.isBurnImmunePhase(29));
		assertNull(ZombieOverrides.coldRemovalChilledCounter(), "the game's own clearing");
		assertFalse(ZombieOverrides.shieldAlwaysAbsorbs());
		assertTrue(ZombieOverrides.rules().isEmpty());
	}

	@Test
	void theWholeGameRulesAreRecordedAndReset() {
		ZombieOverrides.mindControlBoost(200, 200, 1.25f);
		ZombieOverrides.burnImmunity(1800, 29, 30);
		ZombieOverrides.coldRemovalChilledCounter(1000);
		ZombieOverrides.shieldAlwaysAbsorbs(true);

		assertTrue(ZombieOverrides.mindControlBoosted());
		assertEquals(200, ZombieOverrides.mindControlBodyBonus());
		assertEquals(200, ZombieOverrides.mindControlHelmBonus());
		assertEquals(1.25f, ZombieOverrides.mindControlScale());
		assertEquals(1800, ZombieOverrides.burnImmunityTotalHealth());
		assertTrue(ZombieOverrides.isBurnImmunePhase(29));
		assertTrue(ZombieOverrides.isBurnImmunePhase(30));
		assertFalse(ZombieOverrides.isBurnImmunePhase(31), "a phase nobody named is not exempt");
		assertEquals(1000, ZombieOverrides.coldRemovalChilledCounter());
		assertTrue(ZombieOverrides.shieldAlwaysAbsorbs());
		assertEquals(5, ZombieOverrides.rules().size(), "the two burn entries and three rules: "
				+ ZombieOverrides.rules());
		assertEquals("200/200/1.25", ZombieOverrides.rules().get("mindControlBoost"));
		assertEquals("29,30", ZombieOverrides.rules().get("burnImmunityPhases"));
		assertThrows(UnsupportedOperationException.class, () -> ZombieOverrides.rules().clear(),
				"a read-only snapshot");

		ZombieOverrides.reset();

		assertFalse(ZombieOverrides.mindControlBoosted());
		assertEquals(0, ZombieOverrides.burnImmunityTotalHealth());
		assertFalse(ZombieOverrides.isBurnImmunePhase(29));
		assertNull(ZombieOverrides.coldRemovalChilledCounter());
		assertFalse(ZombieOverrides.shieldAlwaysAbsorbs());
		assertTrue(ZombieOverrides.rules().isEmpty());
	}

	@Test
	void badWholeGameValuesAreRefused() {
		assertThrows(IllegalArgumentException.class, () -> ZombieOverrides.mindControlBoost(-1, 0, 1.0f));
		assertThrows(IllegalArgumentException.class, () -> ZombieOverrides.mindControlBoost(0, -1, 1.0f));
		assertThrows(IllegalArgumentException.class, () -> ZombieOverrides.mindControlBoost(0, 0, 0.0f));
		assertThrows(IllegalArgumentException.class, () -> ZombieOverrides.burnImmunity(0));
		assertThrows(IllegalArgumentException.class, () -> ZombieOverrides.coldRemovalChilledCounter(-1));
	}

	/**
	 * A zombie as {@code Zombie::ZombieInitialize} leaves one: the fields the game's per-type setup
	 * wrote, and nothing that needs a running app.
	 */
	private static Zombie spawnedZombie(int theZombieType, int theBodyHealth, int theHelmType, int theHelmHealth) {
		Zombie aZombie = new Zombie();
		aZombie.mZombieType = theZombieType;
		aZombie.mBodyHealth = theBodyHealth;
		aZombie.mBodyMaxHealth = theBodyHealth;
		aZombie.mHelmType = theHelmType;
		aZombie.mHelmHealth = theHelmHealth;
		aZombie.mHelmMaxHealth = theHelmHealth;
		aZombie.mShieldHealth = 0;
		aZombie.mShieldMaxHealth = 0;
		return aZombie;
	}
}
