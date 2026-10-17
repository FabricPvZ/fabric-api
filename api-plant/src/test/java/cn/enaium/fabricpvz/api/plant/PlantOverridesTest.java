package cn.enaium.fabricpvz.api.plant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.portable.lawn.CoinType;
import cn.enaium.fabricpvz.portable.lawn.Plant;
import cn.enaium.fabricpvz.portable.lawn.PlantDefinition;
import cn.enaium.fabricpvz.portable.lawn.PlantSubClass;
import cn.enaium.fabricpvz.portable.lawn.SeedType;
import cn.enaium.fabricpvz.portable.lawn.ZombieType;

/**
 * Tests {@link PlantOverrides}: the handle lookup, the definition values landing in the row the game
 * reads, the spawn health {@link PlantOverrides#apply(Plant)} writes onto a plant, and what
 * {@link PlantOverrides#reset()} puts back.
 *
 * <p>As in {@link PlantRegistryTest}, the Mixins only run under Fabric Loader, so the behaviour is
 * asserted against the facade directly here; the real dispatch through {@code Plant::PlantInitialize}
 * is covered by the 95 Edition mod's loader end-to-end test.
 */
class PlantOverridesTest {

	@AfterEach
	void clearOverrides() {
		PlantOverrides.reset();
		PlantRegistry.reset();
	}

	@Test
	void handlesAreIdempotentAndOrdered() {
		PlantOverride tallnut = PlantOverrides.of(SeedType.SEED_TALLNUT);
		PlantOverride marigold = PlantOverrides.of(SeedType.SEED_MARIGOLD);

		assertSame(tallnut, PlantOverrides.of(SeedType.SEED_TALLNUT), "a seed type has one handle");
		assertSame(marigold, PlantOverrides.of(SeedType.SEED_MARIGOLD));
		assertNotSame(tallnut, marigold);
		assertEquals(SeedType.SEED_TALLNUT, tallnut.seedType());

		Map<Integer, PlantOverride> registered = PlantOverrides.registered();
		assertEquals(List.of(SeedType.SEED_TALLNUT, SeedType.SEED_MARIGOLD),
				List.copyOf(registered.keySet()), "first-asked-for order");
		assertThrows(UnsupportedOperationException.class, () -> registered.clear(), "a read-only snapshot");
	}

	@Test
	void onlySeedTypesAreAccepted() {
		assertThrows(IllegalArgumentException.class, () -> PlantOverrides.of(-1));
		assertThrows(IllegalArgumentException.class, () -> PlantOverrides.of(SeedType.MAX_SEED_TYPES));
		assertEquals(SeedType.SEED_PEASHOOTER, PlantOverrides.of(SeedType.SEED_PEASHOOTER).seedType(),
				"the retail peashooter is a valid target");
	}

	@Test
	void theDefinitionValuesLandOnTheTableTheGameReads() {
		PlantOverrides.of(SeedType.SEED_MARIGOLD).cost(75).refreshTime(1500).launchRate(3300);

		PlantDefinition definition = Plant.getPlantDefinition(SeedType.SEED_MARIGOLD);
		assertEquals(75, definition.mSeedCost);
		assertEquals(1500, definition.mRefreshTime);
		assertEquals(3300, definition.mLaunchRate);
		assertEquals(SeedType.SEED_MARIGOLD, definition.mSeedType, "the row stays where it was");
	}

	@Test
	void theRecordedFieldsAreReadable() {
		PlantOverride override = PlantOverrides.of(SeedType.SEED_STARFRUIT);
		assertNull(override.cost());
		assertNull(override.refreshTime());
		assertNull(override.launchRate());
		assertNull(override.bodyHealth(), "a field nobody set is null, not the game's value");

		override.cost(250).launchRate(200);
		assertEquals(250, override.cost());
		assertNull(override.refreshTime());
		assertEquals(200, override.launchRate());
		assertEquals("PlantOverride[seedType=29, cost=250, launchRate=200]", override.toString());
	}

	@Test
	void negativeDefinitionValuesAreRefused() {
		PlantOverride override = PlantOverrides.of(SeedType.SEED_CACTUS);
		assertThrows(IllegalArgumentException.class, () -> override.cost(-1));
		assertThrows(IllegalArgumentException.class, () -> override.refreshTime(-1));
		assertThrows(IllegalArgumentException.class, () -> override.launchRate(-1));
		assertNull(override.cost(), "a refused value is not recorded");
		assertEquals(125, Plant.getPlantDefinition(SeedType.SEED_CACTUS).mSeedCost, "the row is untouched");
	}

	@Test
	void zeroIsAValueNotAnUnsetField() {
		// A free seed packet is a real price (the retail explode-o-nut is 0), so 0 is recorded and
		// written like any other number; only a non-positive spawn health means "leave it alone".
		PlantOverride override = PlantOverrides.of(SeedType.SEED_GRAVEBUSTER).cost(0);
		assertEquals(0, override.cost());
		assertEquals(0, Plant.getPlantDefinition(SeedType.SEED_GRAVEBUSTER).mSeedCost);
	}

	@Test
	void aSeedTypeWithoutADefinitionRowHasNoDefinitionToOverride() {
		assertNull(Plant.getPlantDefinition(199), "the slot is empty in the retail table");

		assertThrows(IllegalStateException.class, () -> PlantOverrides.of(199).cost(50));
		assertEquals(4000, PlantOverrides.of(199).bodyHealth(4000).bodyHealth(),
				"a spawn value needs no definition row (the seed type is simply never planted)");
	}

	@Test
	void theSpawnHealthLandsOnTheBornPlant() {
		PlantOverrides.of(SeedType.SEED_SPIKEROCK).bodyHealth(16200);

		Plant plant = bornPlant(SeedType.SEED_SPIKEROCK, 450);
		PlantOverrides.apply(plant);

		assertEquals(16200, plant.mPlantHealth);
		assertEquals(16200, plant.mPlantMaxHealth, "the maximum follows the health, as in the game");
	}

	@Test
	void nonPositiveBodyHealthMeansNoChange() {
		PlantOverride override = PlantOverrides.of(SeedType.SEED_SPIKEROCK).bodyHealth(0);
		assertNull(override.bodyHealth(), "0 means 'leave the game's own value'");

		Plant plant = bornPlant(SeedType.SEED_SPIKEROCK, 450);
		PlantOverrides.apply(plant);
		assertEquals(450, plant.mPlantHealth);
		assertEquals(450, plant.mPlantMaxHealth);

		override.bodyHealth(-5);
		assertNull(override.bodyHealth());
	}

	@Test
	void aSeedNobodyOverrodeIsUntouched() {
		PlantOverrides.of(SeedType.SEED_SPIKEROCK).bodyHealth(16200);

		Plant plant = bornPlant(SeedType.SEED_PEASHOOTER, 300);
		plant.mPlantMaxHealth = 300;
		PlantOverrides.apply(plant);

		assertEquals(300, plant.mPlantHealth);
		assertEquals(300, plant.mPlantMaxHealth);
	}

	@Test
	void resetPutsTheGameValuesBack() {
		PlantOverrides.of(SeedType.SEED_POTATOMINE).cost(50);
		PlantOverrides.of(SeedType.SEED_BLOVER).refreshTime(2000);
		PlantOverrides.of(SeedType.SEED_GATLINGPEA).launchRate(150).bodyHealth(4000);
		assertEquals(50, Plant.getPlantDefinition(SeedType.SEED_POTATOMINE).mSeedCost);

		PlantOverrides.reset();

		assertEquals(25, Plant.getPlantDefinition(SeedType.SEED_POTATOMINE).mSeedCost, "25 is the retail price");
		assertEquals(750, Plant.getPlantDefinition(SeedType.SEED_BLOVER).mRefreshTime, "750 is the retail value");
		assertEquals(150, Plant.getPlantDefinition(SeedType.SEED_GATLINGPEA).mLaunchRate);
		assertTrue(PlantOverrides.registered().isEmpty());
		assertNull(PlantOverrides.get(SeedType.SEED_POTATOMINE));
	}

	@Test
	void registrationIsTheBaseAndAnOverrideTheLastWord() {
		PlantType type = PlantType.builder("test_overridden_plant")
				.cost(100)
				.subClass(PlantSubClass.SUBCLASS_NORMAL)
				.register();

		PlantOverrides.of(type.seedType()).cost(250);
		assertEquals(250, Plant.getPlantDefinition(type.seedType()).mSeedCost);

		PlantOverrides.reset();
		assertEquals(100, Plant.getPlantDefinition(type.seedType()).mSeedCost,
				"clearing the override leaves the registration's own row");
	}

	@Test
	void theFourthBatchFieldsDefaultToTheGamesOwn() {
		PlantOverride override = PlantOverrides.of(SeedType.SEED_CHOMPER);

		assertNull(override.spawnCountdown(), "a field nobody set is null, not the game's value");
		assertEquals(PlantOverride.DEFAULT_CHOMPER_DIGEST_COUNTDOWN, override.digestCountdown());
		assertEquals(PlantOverride.DEFAULT_BITE_INSTEAD_OF_EAT_ZOMBIE_TYPE, override.biteInsteadOfEatZombieType());
		assertEquals(PlantOverride.DEFAULT_SPIKEROCK_HIT_DAMAGE, override.spikeRockHitDamage());
		assertEquals(PlantOverride.DEFAULT_SPIKEROCK_HIDE_BIGSPIKE3_AT, override.spikeRockHideBigSpike3At());
		assertEquals(PlantOverride.DEFAULT_SPIKEROCK_HIDE_BIGSPIKE2_AT, override.spikeRockHideBigSpike2At());
		assertEquals(PlantOverride.DEFAULT_EXPLOSION_RADIUS, override.explosionRadius());
		assertEquals(PlantOverride.DEFAULT_EXPLOSION_ROW_RANGE, override.explosionRowRange());
		assertFalse(override.explosionBurns());
		assertEquals(PlantOverride.DEFAULT_PRODUCED_COMMON_COIN_TYPE, override.producedCommonCoinType());
		assertEquals(PlantOverride.DEFAULT_PRODUCED_RARE_COIN_TYPE, override.producedRareCoinType());
		assertEquals(PlantOverride.DEFAULT_PRODUCED_COIN_RARE_PERCENT, override.producedCoinRarePercent());

		// The read side the Mixins use answers the game's own value for every plant nobody overrode.
		assertEquals(ZombieType.ZOMBIE_BOSS, PlantOverrides.biteInsteadOfEatZombieType(SeedType.SEED_CHOMPER));
		assertEquals(4000, PlantOverrides.digestCountdown(SeedType.SEED_CHOMPER));
		assertEquals(50, PlantOverrides.spikeRockHitDamage(SeedType.SEED_SPIKEROCK));
		assertEquals(300, PlantOverrides.spikeRockHideBigSpike3At(SeedType.SEED_SPIKEROCK));
		assertEquals(150, PlantOverrides.spikeRockHideBigSpike2At(SeedType.SEED_SPIKEROCK));
		assertEquals(60, PlantOverrides.explosionRadius(SeedType.SEED_POTATOMINE));
		assertEquals(0, PlantOverrides.explosionRowRange(SeedType.SEED_POTATOMINE));
		assertFalse(PlantOverrides.explosionBurns(SeedType.SEED_POTATOMINE));
		assertEquals(10, PlantOverrides.producedCoinRarePercent(SeedType.SEED_MARIGOLD));
		assertEquals(CoinType.COIN_GOLD, PlantOverrides.producedCoin(SeedType.SEED_MARIGOLD, CoinType.COIN_GOLD));
		assertEquals(CoinType.COIN_SILVER, PlantOverrides.producedCoin(SeedType.SEED_MARIGOLD, CoinType.COIN_SILVER));
		assertEquals(CoinType.COIN_SUN, PlantOverrides.producedCoin(SeedType.SEED_MARIGOLD, CoinType.COIN_SUN),
				"a coin that is neither arm is answered unchanged");
	}

	@Test
	void theFourthBatchFieldsTakeTheValuesTheyAreSetTo() {
		PlantOverrides.of(SeedType.SEED_POTATOMINE).spawnCountdown(1000).explosionBlast(115, 1, true);
		PlantOverrides.of(SeedType.SEED_SUNSHROOM).spawnCountdown(9000);
		PlantOverrides.of(SeedType.SEED_CHOMPER).digestCountdown(2500)
				.biteInsteadOfEatZombieType(ZombieType.ZOMBIE_FOOTBALL);
		PlantOverrides.of(SeedType.SEED_SPIKEROCK).spikeRockDamage(1800, 10800, 5400);
		PlantOverrides.of(SeedType.SEED_MARIGOLD).producedCoins(CoinType.COIN_SUN, CoinType.COIN_LARGESUN, 50);

		assertEquals(1000, PlantOverrides.of(SeedType.SEED_POTATOMINE).spawnCountdown());
		assertEquals(115, PlantOverrides.explosionRadius(SeedType.SEED_POTATOMINE));
		assertEquals(1, PlantOverrides.explosionRowRange(SeedType.SEED_POTATOMINE));
		assertTrue(PlantOverrides.explosionBurns(SeedType.SEED_POTATOMINE));
		assertEquals(PlantOverride.DEFAULT_EXPLOSION_RADIUS,
				PlantOverrides.explosionRadius(SeedType.SEED_SUNSHROOM),
				"another seed type keeps the game's own blast");
		assertEquals(9000, PlantOverrides.of(SeedType.SEED_SUNSHROOM).spawnCountdown());
		assertEquals(2500, PlantOverrides.digestCountdown(SeedType.SEED_CHOMPER));
		assertEquals(ZombieType.ZOMBIE_FOOTBALL, PlantOverrides.biteInsteadOfEatZombieType(SeedType.SEED_CHOMPER));
		assertEquals(1800, PlantOverrides.spikeRockHitDamage(SeedType.SEED_SPIKEROCK));
		assertEquals(10800, PlantOverrides.spikeRockHideBigSpike3At(SeedType.SEED_SPIKEROCK));
		assertEquals(5400, PlantOverrides.spikeRockHideBigSpike2At(SeedType.SEED_SPIKEROCK));
		assertEquals(50, PlantOverrides.spikeRockHitDamage(SeedType.SEED_SPIKEWEED),
				"another seed type keeps the game's own hit");
		assertEquals(50, PlantOverrides.producedCoinRarePercent(SeedType.SEED_MARIGOLD));
		assertEquals(CoinType.COIN_LARGESUN, PlantOverrides.producedCoin(SeedType.SEED_MARIGOLD, CoinType.COIN_GOLD));
		assertEquals(CoinType.COIN_SUN,
				PlantOverrides.producedCoin(SeedType.SEED_MARIGOLD, CoinType.COIN_SILVER),
				"the game's second silver arm - the coin a Big Time level pays - answers the common coin as well");

		PlantOverrides.reset();
		assertNull(PlantOverrides.get(SeedType.SEED_CHOMPER));
		assertEquals(ZombieType.ZOMBIE_BOSS, PlantOverrides.biteInsteadOfEatZombieType(SeedType.SEED_CHOMPER));
	}

	@Test
	void badFourthBatchValuesAreRefused() {
		PlantOverride override = PlantOverrides.of(SeedType.SEED_SPIKEROCK);

		assertThrows(IllegalArgumentException.class, () -> override.spawnCountdown(-1));
		assertThrows(IllegalArgumentException.class, () -> override.digestCountdown(-1));
		assertThrows(IllegalArgumentException.class, () -> override.biteInsteadOfEatZombieType(-1));
		assertThrows(IllegalArgumentException.class,
				() -> override.biteInsteadOfEatZombieType(ZombieType.MAX_ZOMBIE_TYPES));
		assertThrows(IllegalArgumentException.class, () -> override.spikeRockDamage(0, 300, 150));
		assertThrows(IllegalArgumentException.class, () -> override.spikeRockDamage(50, 150, 300));
		assertThrows(IllegalArgumentException.class, () -> override.explosionBlast(0, 0, false));
		assertThrows(IllegalArgumentException.class, () -> override.explosionBlast(60, -1, false));
		assertThrows(IllegalArgumentException.class, () -> override.producedCoins(-1, CoinType.COIN_GOLD, 10));
		assertThrows(IllegalArgumentException.class, () -> override.producedCoins(CoinType.COIN_SILVER, 2, 101));

		assertNull(override.spawnCountdown(), "a refused value is not recorded");
		assertEquals(PlantOverride.DEFAULT_SPIKEROCK_HIT_DAMAGE, override.spikeRockHitDamage());
		assertFalse(override.explosionBurns());
	}

	@Test
	void theSpawnCountdownLandsOnTheBornPlant() {
		PlantOverrides.of(SeedType.SEED_POTATOMINE).spawnCountdown(1000);

		Plant plant = bornPlant(SeedType.SEED_POTATOMINE, 300);
		plant.mStateCountdown = 1500;
		PlantOverrides.apply(plant);

		assertEquals(1000, plant.mStateCountdown);

		Plant other = bornPlant(SeedType.SEED_SUNSHROOM, 300);
		other.mStateCountdown = 12000;
		PlantOverrides.apply(other);
		assertEquals(12000, other.mStateCountdown, "a seed type nobody overrode is untouched");
	}

	@Test
	void crushProofIsTheGamesOwnUntilSomebodySaysOtherwise() {
		assertTrue(PlantOverrides.isCrushProof(SeedType.SEED_SPIKEROCK), "the game's own exemption");
		assertFalse(PlantOverrides.isCrushProof(SeedType.SEED_TALLNUT));
		assertFalse(PlantOverrides.isCrushProof(SeedType.SEED_PEASHOOTER));

		PlantOverrides.of(SeedType.SEED_TALLNUT).crushProof(true);
		assertTrue(PlantOverrides.isCrushProof(SeedType.SEED_TALLNUT));
		assertFalse(PlantOverrides.isCrushProof(SeedType.SEED_PEASHOOTER), "the others are unchanged");
		assertEquals(Boolean.TRUE, PlantOverrides.of(SeedType.SEED_TALLNUT).crushProof());
		assertNull(PlantOverrides.of(SeedType.SEED_PEASHOOTER).crushProof(), "nobody set this one");

		PlantOverrides.of(SeedType.SEED_SPIKEROCK).crushProof(false);
		assertFalse(PlantOverrides.isCrushProof(SeedType.SEED_SPIKEROCK), "the exemption can be taken away");
	}

	@Test
	void theWholeGameRulesDefaultToTheGamesOwn() {
		assertNull(PlantOverrides.notOnGroundState(), "the game's own bungee predicate is in charge");
		assertEquals(SeedType.SEED_CATTAIL, PlantOverrides.extraFireSeedType(), "the game's own extra shot");
		assertTrue(PlantOverrides.rules().isEmpty());
	}

	@Test
	void theWholeGameRulesAreRecordedAndReset() {
		PlantOverrides.notOnGroundState(21);
		PlantOverrides.extraFireSeedType(SeedType.SEED_GATLINGPEA);

		assertEquals(21, PlantOverrides.notOnGroundState());
		assertEquals(SeedType.SEED_GATLINGPEA, PlantOverrides.extraFireSeedType());
		assertEquals(Map.of("notOnGroundState", 21, "extraFireSeedType", SeedType.SEED_GATLINGPEA),
				PlantOverrides.rules(), "the diagnostics view, in first-set order");
		assertThrows(UnsupportedOperationException.class, () -> PlantOverrides.rules().clear(),
				"a read-only snapshot");

		PlantOverrides.reset();

		assertNull(PlantOverrides.notOnGroundState());
		assertEquals(SeedType.SEED_CATTAIL, PlantOverrides.extraFireSeedType());
		assertTrue(PlantOverrides.rules().isEmpty());
	}

	@Test
	void badWholeGameValuesAreRefused() {
		assertThrows(IllegalArgumentException.class, () -> PlantOverrides.extraFireSeedType(-1));
		assertThrows(IllegalArgumentException.class, () -> PlantOverrides.extraFireSeedType(SeedType.MAX_SEED_TYPES));
	}

	/** A plant as {@code Plant::PlantInitialize} leaves one: the seed type and the health pair. */
	private static Plant bornPlant(int theSeedType, int theHealth) {
		Plant plant = new Plant();
		plant.mSeedType = theSeedType;
		plant.mPlantHealth = theHealth;
		plant.mPlantMaxHealth = theHealth;
		return plant;
	}
}
