package cn.enaium.fabricpvz.api.plant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import cn.enaium.fabricpvz.portable.lawn.BackgroundType;
import cn.enaium.fabricpvz.portable.lawn.Challenge;
import cn.enaium.fabricpvz.portable.lawn.CoinType;
import cn.enaium.fabricpvz.portable.lawn.GameMode;
import cn.enaium.fabricpvz.portable.lawn.SeedType;
import cn.enaium.fabricpvz.portable.lawn.ZombieType;

/**
 * Tests {@link BoardOverrides}: the two kinds of knob (the immediate writes into
 * {@code Challenge.gZombieAllowedLevels} and the recorded values the Mixins hand to the game), the
 * defaults that keep an unmodded game on its own numbers, and {@link BoardOverrides#reset()} putting
 * the game's own table back.
 */
class BoardOverridesTest {

	@AfterEach
	void clearOverrides() {
		BoardOverrides.reset();
	}

	@Test
	void allowedZombiesAreMergedPerMode() {
		BoardOverrides.allowZombies(GameMode.GAMEMODE_CHALLENGE_SPEED, ZombieType.ZOMBIE_FOOTBALL);
		BoardOverrides.allowZombies(GameMode.GAMEMODE_CHALLENGE_SPEED,
				ZombieType.ZOMBIE_JACK_IN_THE_BOX, ZombieType.ZOMBIE_FOOTBALL);

		assertArrayEquals(new int[] { ZombieType.ZOMBIE_FOOTBALL, ZombieType.ZOMBIE_JACK_IN_THE_BOX },
				BoardOverrides.allowedZombies().get(GameMode.GAMEMODE_CHALLENGE_SPEED),
				"the same type asked for twice must appear once");
		assertEquals(1, BoardOverrides.allowedZombies().size());
	}

	@Test
	void allowedZombiesAreAppliedToTheBoardArray() {
		BoardOverrides.allowZombies(GameMode.GAMEMODE_CHALLENGE_SPEED, ZombieType.ZOMBIE_LADDER);

		boolean[] aAllowed = new boolean[ZombieType.MAX_ZOMBIE_TYPES];
		BoardOverrides.applyZombieAllowed(GameMode.GAMEMODE_CHALLENGE_SPEED, aAllowed);
		assertTrue(aAllowed[ZombieType.ZOMBIE_LADDER]);
		assertFalse(aAllowed[ZombieType.ZOMBIE_GARGANTUAR], "a type nobody allowed stays as the game left it");

		boolean[] aOther = new boolean[ZombieType.MAX_ZOMBIE_TYPES];
		BoardOverrides.applyZombieAllowed(GameMode.GAMEMODE_CHALLENGE_ICE, aOther);
		assertFalse(aOther[ZombieType.ZOMBIE_LADDER], "another mode is untouched");
	}

	@Test
	void onlyModesAndZombieTypesAreAccepted() {
		assertThrows(IllegalArgumentException.class, () -> BoardOverrides.allowZombies(-1));
		assertThrows(IllegalArgumentException.class,
				() -> BoardOverrides.allowZombies(GameMode.GAMEMODE_CHALLENGE_SPEED, -1));
		assertThrows(IllegalArgumentException.class, () -> BoardOverrides.allowZombies(
				GameMode.GAMEMODE_CHALLENGE_SPEED, ZombieType.MAX_ZOMBIE_TYPES));
	}

	@Test
	void spawnRulesAreNotRelaxedByDefault() {
		assertFalse(BoardOverrides.zombieSpawnRulesRelaxed());

		BoardOverrides.relaxZombieSpawnRules(true);
		assertTrue(BoardOverrides.zombieSpawnRulesRelaxed());

		BoardOverrides.relaxZombieSpawnRules(false);
		assertFalse(BoardOverrides.zombieSpawnRulesRelaxed());
	}

	@Test
	void allowedLevelsAreWrittenIntoTheGamesTable() {
		assertEquals(0, Challenge.gZombieAllowedLevels[ZombieType.ZOMBIE_POLEVAULTER].mAllowedOnLevel[31],
				"the retail table does not allow the pole vaulter on level 32");

		BoardOverrides.allowZombieOnLevel(ZombieType.ZOMBIE_POLEVAULTER, 32, true);
		assertEquals(1, Challenge.gZombieAllowedLevels[ZombieType.ZOMBIE_POLEVAULTER].mAllowedOnLevel[31]);
		assertEquals(1, Challenge.gZombieAllowedLevels[ZombieType.ZOMBIE_POLEVAULTER].mAllowedOnLevel[5],
				"the other cells of the row are untouched");

		BoardOverrides.reset();
		assertEquals(0, Challenge.gZombieAllowedLevels[ZombieType.ZOMBIE_POLEVAULTER].mAllowedOnLevel[31],
				"reset() puts the retail cell back");
		assertTrue(BoardOverrides.levelOverrides().isEmpty());
	}

	@Test
	void resetPutsTheRetailCellBackEvenAfterSeveralWrites() {
		BoardOverrides.allowZombieOnLevel(ZombieType.ZOMBIE_NEWSPAPER, 11, false);
		BoardOverrides.allowZombieOnLevel(ZombieType.ZOMBIE_NEWSPAPER, 11, true);

		assertEquals(1, Challenge.gZombieAllowedLevels[ZombieType.ZOMBIE_NEWSPAPER].mAllowedOnLevel[10]);

		BoardOverrides.reset();
		assertEquals(1, Challenge.gZombieAllowedLevels[ZombieType.ZOMBIE_NEWSPAPER].mAllowedOnLevel[10],
				"the cell's original value is what reset() restores, not the first write's");
	}

	@Test
	void onlyTableRowsAndLevelsAreAccepted() {
		assertThrows(IllegalArgumentException.class, () -> BoardOverrides.allowZombieOnLevel(
				Challenge.gZombieAllowedLevels.length, 1, true));
		assertThrows(IllegalArgumentException.class,
				() -> BoardOverrides.allowZombieOnLevel(ZombieType.ZOMBIE_NORMAL, 0, true));
		assertThrows(IllegalArgumentException.class,
				() -> BoardOverrides.allowZombieOnLevel(ZombieType.ZOMBIE_NORMAL, 51, true));
	}

	@Test
	void theGlobalNumbersDefaultToTheGamesOwn() {
		assertEquals(75, BoardOverrides.beghouledWinningScore());
		assertEquals(75, BoardOverrides.DEFAULT_BEGHOULED_WINNING_SCORE);
		assertEquals(1800, BoardOverrides.killAllZombiesDamage());
		assertEquals(500, BoardOverrides.rainSeedIntervalMin());
		assertEquals(999, BoardOverrides.rainSeedIntervalMax());
		assertNull(BoardOverrides.stageHasGraveStonesOverride());
		assertEquals(-1, BoardOverrides.sunValue(CoinType.COIN_SUN));
		assertEquals(9990, BoardOverrides.sunMoneyCap());
		assertEquals(GameMode.GAMEMODE_CHALLENGE_SUNNY_DAY, BoardOverrides.sunSpawningMode());
		assertEquals(CoinType.COIN_LARGESUN, BoardOverrides.sunSpawningCoinType());
		assertFalse(BoardOverrides.graveBusterNeedsGravesInverted());
		assertEquals(SeedType.SEED_PEASHOOTER,
				BoardOverrides.conveyorSeed(GameMode.GAMEMODE_CHALLENGE_PORTAL_COMBAT, SeedType.SEED_PEASHOOTER),
				"an unregistered pick is answered unchanged");
		assertTrue(BoardOverrides.registered().isEmpty());
	}

	@Test
	void theGlobalNumbersTakeTheValuesTheyAreSetTo() {
		BoardOverrides.beghouledWinningScore(100);
		BoardOverrides.killAllZombiesDamage(2400);
		BoardOverrides.rainSeedInterval(200, 699);
		BoardOverrides.stageHasGraveStones(false);
		BoardOverrides.sunValues(50, 25, 75);
		BoardOverrides.sunMoneyCap(2_000_000_000);
		BoardOverrides.sunSpawning(GameMode.GAMEMODE_CHALLENGE_AIR_RAID, CoinType.COIN_USABLE_SEED_PACKET);
		BoardOverrides.graveBusterNeedsGravesInverted(true);

		assertEquals(100, BoardOverrides.beghouledWinningScore());
		assertEquals(2400, BoardOverrides.killAllZombiesDamage());
		assertEquals(200, BoardOverrides.rainSeedIntervalMin());
		assertEquals(699, BoardOverrides.rainSeedIntervalMax());
		assertEquals(Boolean.FALSE, BoardOverrides.stageHasGraveStonesOverride());
		assertEquals(50, BoardOverrides.sunValue(CoinType.COIN_SUN));
		assertEquals(25, BoardOverrides.sunValue(CoinType.COIN_SMALLSUN));
		assertEquals(75, BoardOverrides.sunValue(CoinType.COIN_LARGESUN));
		assertEquals(-1, BoardOverrides.sunValue(CoinType.COIN_GOLD), "a coin that is not a sun stays the game's");
		assertEquals(2_000_000_000, BoardOverrides.sunMoneyCap());
		assertEquals(GameMode.GAMEMODE_CHALLENGE_AIR_RAID, BoardOverrides.sunSpawningMode());
		assertEquals(CoinType.COIN_USABLE_SEED_PACKET, BoardOverrides.sunSpawningCoinType());
		assertTrue(BoardOverrides.graveBusterNeedsGravesInverted());

		BoardOverrides.reset();
		assertEquals(75, BoardOverrides.beghouledWinningScore());
		assertEquals(-1, BoardOverrides.sunValue(CoinType.COIN_SUN));
		assertNull(BoardOverrides.stageHasGraveStonesOverride());
	}

	@Test
	void badGlobalNumbersAreRefused() {
		assertThrows(IllegalArgumentException.class, () -> BoardOverrides.beghouledWinningScore(0));
		assertThrows(IllegalArgumentException.class, () -> BoardOverrides.killAllZombiesDamage(0));
		assertThrows(IllegalArgumentException.class, () -> BoardOverrides.rainSeedInterval(700, 200));
		assertThrows(IllegalArgumentException.class, () -> BoardOverrides.sunValues(-1, 20, 75));
		assertThrows(IllegalArgumentException.class, () -> BoardOverrides.sunMoneyCap(-1));
	}

	@Test
	void conveyorSeedReplacementsArePureFunctions() {
		BoardOverrides.conveyorSeed(GameMode.GAMEMODE_CHALLENGE_PORTAL_COMBAT, SeedType.SEED_PEASHOOTER,
				SeedType.SEED_THREEPEATER);
		BoardOverrides.conveyorSeed(GameMode.GAMEMODE_CHALLENGE_PORTAL_COMBAT, SeedType.SEED_WALLNUT,
				SeedType.SEED_EXPLODE_O_NUT);

		assertEquals(SeedType.SEED_THREEPEATER,
				BoardOverrides.conveyorSeed(GameMode.GAMEMODE_CHALLENGE_PORTAL_COMBAT, SeedType.SEED_PEASHOOTER));
		assertEquals(SeedType.SEED_EXPLODE_O_NUT,
				BoardOverrides.conveyorSeed(GameMode.GAMEMODE_CHALLENGE_PORTAL_COMBAT, SeedType.SEED_WALLNUT));
		assertEquals(SeedType.SEED_REPEATER,
				BoardOverrides.conveyorSeed(GameMode.GAMEMODE_CHALLENGE_PORTAL_COMBAT, SeedType.SEED_REPEATER));
		assertEquals(SeedType.SEED_PEASHOOTER,
				BoardOverrides.conveyorSeed(GameMode.GAMEMODE_CHALLENGE_INVISIGHOUL, SeedType.SEED_PEASHOOTER),
				"another mode keeps its own list");

		Map<Integer, Map<Integer, Integer>> aRegistered = BoardOverrides.conveyorSeeds();
		assertEquals(2, aRegistered.get(GameMode.GAMEMODE_CHALLENGE_PORTAL_COMBAT).size());

		BoardOverrides.conveyorSeed(GameMode.GAMEMODE_CHALLENGE_PORTAL_COMBAT, SeedType.SEED_PEASHOOTER,
				SeedType.SEED_GATLINGPEA);
		assertEquals(SeedType.SEED_GATLINGPEA,
				BoardOverrides.conveyorSeed(GameMode.GAMEMODE_CHALLENGE_PORTAL_COMBAT, SeedType.SEED_PEASHOOTER),
				"the last value written is the one applied");
		assertEquals(2, BoardOverrides.conveyorSeeds().get(GameMode.GAMEMODE_CHALLENGE_PORTAL_COMBAT).size());
	}

	@Test
	void seedPickerVetoesArePerMode() {
		assertFalse(BoardOverrides.seedNotAllowedToPickOverride(GameMode.GAMEMODE_CHALLENGE_AIR_RAID, SeedType.SEED_MARIGOLD));

		BoardOverrides.seedNotAllowedToPick(GameMode.GAMEMODE_CHALLENGE_AIR_RAID, SeedType.SEED_MARIGOLD);

		assertTrue(BoardOverrides.seedNotAllowedToPickOverride(GameMode.GAMEMODE_CHALLENGE_AIR_RAID, SeedType.SEED_MARIGOLD));
		assertFalse(BoardOverrides.seedNotAllowedToPickOverride(GameMode.GAMEMODE_CHALLENGE_AIR_RAID, SeedType.SEED_PEASHOOTER));
		assertFalse(BoardOverrides.seedNotAllowedToPickOverride(GameMode.GAMEMODE_ADVENTURE, SeedType.SEED_MARIGOLD));
	}

	@Test
	void registeredReportsWhatWasSet() {
		BoardOverrides.relaxZombieSpawnRules(true);
		BoardOverrides.sunMoneyCap(100);
		BoardOverrides.allowZombies(GameMode.GAMEMODE_CHALLENGE_SPEED, ZombieType.ZOMBIE_FOOTBALL,
				ZombieType.ZOMBIE_LADDER);

		Map<String, Object> aRegistered = BoardOverrides.registered();
		assertEquals(Boolean.TRUE, aRegistered.get("relaxZombieSpawnRules"));
		assertEquals(100, aRegistered.get("sunMoneyCap"));
		assertEquals("7,21", aRegistered.get("allowZombies[" + GameMode.GAMEMODE_CHALLENGE_SPEED + "]"));

		BoardOverrides.reset();
		assertTrue(BoardOverrides.registered().isEmpty());
	}

	@Test
	void theSnapshotsCannotBeWrittenThrough() {
		BoardOverrides.allowZombies(GameMode.GAMEMODE_CHALLENGE_SPEED, ZombieType.ZOMBIE_FOOTBALL);
		assertThrows(UnsupportedOperationException.class,
				() -> BoardOverrides.allowedZombies().put(1, new int[0]));
		assertThrows(UnsupportedOperationException.class,
				() -> BoardOverrides.registered().put("x", 1));

		int[] aTypes = BoardOverrides.allowedZombies().get(GameMode.GAMEMODE_CHALLENGE_SPEED);
		aTypes[0] = 99;
		assertEquals(ZombieType.ZOMBIE_FOOTBALL,
				BoardOverrides.allowedZombies().get(GameMode.GAMEMODE_CHALLENGE_SPEED)[0],
				"the snapshot's array is a copy");
	}

	@Test
	void theFourthBatchKnobsDefaultToTheGamesOwn() {
		assertEquals(CoinType.COIN_SUN, BoardOverrides.skyDropCoin());
		assertEquals(CoinType.COIN_SUN, BoardOverrides.DEFAULT_SKY_DROP_COIN_TYPE);
		assertFalse(BoardOverrides.dropLootSunBranch());
		assertEquals(CoinType.COIN_SUN, BoardOverrides.dropLootCoin(0));
		assertEquals(CoinType.COIN_SUN, BoardOverrides.dropLootCoin(1));
		assertEquals(CoinType.COIN_SUN, BoardOverrides.dropLootCoin(2));
		assertEquals(5000, BoardOverrides.lastStandInitialSun());
		assertEquals(SeedType.SEED_NONE, BoardOverrides.coinUsableSeedType());
		assertEquals(SeedType.SEED_NONE, BoardOverrides.DEFAULT_COIN_USABLE_SEED_TYPE);
		assertTrue(BoardOverrides.waveCounts().isEmpty());
		assertEquals(10, BoardOverrides.numWaves(10), "a count nobody replaced is answered unchanged");
		assertEquals(20, BoardOverrides.numWaves(20));
		assertEquals(8, BoardOverrides.numWaves(8), "the adventure counts are only replaced where set");
	}

	@Test
	void theFourthBatchKnobsTakeTheValuesTheyAreSetTo() {
		BoardOverrides.skyDropCoin(CoinType.COIN_SMALLSUN);
		BoardOverrides.dropLootSunBranch(true);
		BoardOverrides.dropLootCoins(CoinType.COIN_SMALLSUN, CoinType.COIN_LARGESUN, CoinType.COIN_SUN);
		BoardOverrides.lastStandInitialSun(8000);
		BoardOverrides.coinUsableSeedType(SeedType.SEED_BLOVER);
		BoardOverrides.numWaves(10, 20);
		BoardOverrides.numWaves(20, 40);
		BoardOverrides.numWaves(12, 20);

		assertEquals(CoinType.COIN_SMALLSUN, BoardOverrides.skyDropCoin());
		assertTrue(BoardOverrides.dropLootSunBranch());
		assertEquals(CoinType.COIN_SMALLSUN, BoardOverrides.dropLootCoin(0));
		assertEquals(CoinType.COIN_LARGESUN, BoardOverrides.dropLootCoin(1));
		assertEquals(CoinType.COIN_SUN, BoardOverrides.dropLootCoin(2));
		assertEquals(8000, BoardOverrides.lastStandInitialSun());
		assertEquals(SeedType.SEED_BLOVER, BoardOverrides.coinUsableSeedType());
		assertEquals(20, BoardOverrides.numWaves(10));
		assertEquals(40, BoardOverrides.numWaves(20));
		assertEquals(20, BoardOverrides.numWaves(12));
		assertEquals(30, BoardOverrides.numWaves(30), "a count nobody replaces keeps its own value");
		assertEquals(Map.of(10, 20, 20, 40, 12, 20), BoardOverrides.waveCounts());

		BoardOverrides.reset();
		assertTrue(BoardOverrides.waveCounts().isEmpty());
		assertEquals(12, BoardOverrides.numWaves(12));
		assertEquals(CoinType.COIN_SUN, BoardOverrides.skyDropCoin());
		assertFalse(BoardOverrides.dropLootSunBranch());
		assertEquals(5000, BoardOverrides.lastStandInitialSun());
		assertEquals(SeedType.SEED_NONE, BoardOverrides.coinUsableSeedType());
	}

	@Test
	void badFourthBatchValuesAreRefused() {
		assertThrows(IllegalArgumentException.class, () -> BoardOverrides.skyDropCoin(-1));
		assertThrows(IllegalArgumentException.class, () -> BoardOverrides.lastStandInitialSun(-1));
		assertThrows(IllegalArgumentException.class, () -> BoardOverrides.coinUsableSeedType(-1));
		assertThrows(IllegalArgumentException.class, () -> BoardOverrides.coinUsableSeedType(SeedType.MAX_SEED_TYPES));
		assertThrows(IllegalArgumentException.class, () -> BoardOverrides.dropLootCoins(-1, 6, 4));
		assertThrows(IllegalArgumentException.class, () -> BoardOverrides.numWaves(-1, 20));
		assertThrows(IllegalArgumentException.class, () -> BoardOverrides.numWaves(10, -1));
		assertTrue(BoardOverrides.waveCounts().isEmpty(), "a refused value is not recorded");
	}

	@Test
	void theSpawnMultiplierDefaultsToTheGamesOwn() {
		assertEquals(0, BoardOverrides.DEFAULT_SPAWN_MULTIPLIER);
		assertEquals(BoardOverrides.DEFAULT_SPAWN_MULTIPLIER, BoardOverrides.spawnMultiplier());
		assertFalse(BoardOverrides.registered().containsKey("spawnMultiplier"),
				"nothing registered leaves the game's own per-level factor");
	}

	@Test
	void theSpawnMultiplierTakesTheValueItIsSetTo() {
		BoardOverrides.spawnMultiplier(4);

		assertEquals(4, BoardOverrides.spawnMultiplier());
		assertEquals(4, BoardOverrides.registered().get("spawnMultiplier"));

		BoardOverrides.spawnMultiplier(2);

		assertEquals(2, BoardOverrides.spawnMultiplier(), "the last value written is the one applied");

		BoardOverrides.spawnMultiplier(BoardOverrides.DEFAULT_SPAWN_MULTIPLIER);

		assertEquals(BoardOverrides.DEFAULT_SPAWN_MULTIPLIER, BoardOverrides.spawnMultiplier(),
				"the default value takes the factor back");
		assertFalse(BoardOverrides.registered().containsKey("spawnMultiplier"));

		BoardOverrides.spawnMultiplier(4);
		BoardOverrides.reset();

		assertEquals(BoardOverrides.DEFAULT_SPAWN_MULTIPLIER, BoardOverrides.spawnMultiplier());
		assertThrows(IllegalArgumentException.class, () -> BoardOverrides.spawnMultiplier(-4));
		assertFalse(BoardOverrides.registered().containsKey("spawnMultiplier"), "a refused value is not recorded");
	}

	@Test
	void theGraveModeDefaultsToTheGamesOwn() {
		assertFalse(BoardOverrides.graveModeEnabled());
		assertEquals(5, BoardOverrides.graveStoneWaveThreshold());
		assertEquals(5, BoardOverrides.DEFAULT_GRAVE_STONE_WAVE_THRESHOLD);
		assertEquals(1, BoardOverrides.whackAZombieZombieCount());
		assertEquals(1, BoardOverrides.DEFAULT_WHACK_A_ZOMBIE_ZOMBIE_COUNT);
		assertTrue(BoardOverrides.modeBackgrounds().isEmpty());

		assertEquals(GameMode.GAMEMODE_CHALLENGE_GRAVE_DANGER,
				BoardOverrides.graveGuardMode(GameMode.GAMEMODE_CHALLENGE_GRAVE_DANGER,
						GameMode.GAMEMODE_ADVENTURE),
				"with nothing registered the guard is the game's own mode comparison");
		assertEquals(BackgroundType.BACKGROUND_4_FOG,
				BoardOverrides.modeBackgroundOverride(GameMode.GAMEMODE_CHALLENGE_AIR_RAID,
						BackgroundType.BACKGROUND_4_FOG),
				"a mode nobody registered keeps the game's own background");
		assertEquals(2, BoardOverrides.whackAZombieZombieCountOverride(2),
				"a count the game's own arms already replaced is left alone");
		assertEquals(20, BoardOverrides.whackAZombieZombieCountOverride(20));
	}

	@Test
	void theGraveModeTakesTheValuesItIsSetTo() {
		BoardOverrides.graveModeEnabled(true);
		BoardOverrides.graveStoneWaveThreshold(9);
		BoardOverrides.whackAZombieZombieCount(2);
		BoardOverrides.modeBackground(GameMode.GAMEMODE_CHALLENGE_AIR_RAID, BackgroundType.BACKGROUND_1_DAY);
		BoardOverrides.modeBackground(GameMode.GAMEMODE_CHALLENGE_ICE, BackgroundType.BACKGROUND_3_POOL);

		assertTrue(BoardOverrides.graveModeEnabled());
		assertEquals(9, BoardOverrides.graveStoneWaveThreshold());
		assertEquals(2, BoardOverrides.whackAZombieZombieCount());
		assertEquals(GameMode.GAMEMODE_ADVENTURE,
				BoardOverrides.graveGuardMode(GameMode.GAMEMODE_CHALLENGE_GRAVE_DANGER,
						GameMode.GAMEMODE_ADVENTURE),
				"with the grave mode on the guard passes on every level");
		assertEquals(2, BoardOverrides.whackAZombieZombieCountOverride(1),
				"the base the game starts from is the one the knob moves");
		assertEquals(3, BoardOverrides.whackAZombieZombieCountOverride(3),
				"the game's own triple arm still speaks for itself");
		assertEquals(20, BoardOverrides.whackAZombieZombieCountOverride(20),
				"and so does the final wave");
		assertEquals(BackgroundType.BACKGROUND_1_DAY,
				BoardOverrides.modeBackgroundOverride(GameMode.GAMEMODE_CHALLENGE_AIR_RAID,
						BackgroundType.BACKGROUND_4_FOG));
		assertEquals(BackgroundType.BACKGROUND_3_POOL,
				BoardOverrides.modeBackgroundOverride(GameMode.GAMEMODE_CHALLENGE_ICE,
						BackgroundType.BACKGROUND_1_DAY));
		assertEquals(BackgroundType.BACKGROUND_2_NIGHT,
				BoardOverrides.modeBackgroundOverride(GameMode.GAMEMODE_ADVENTURE, BackgroundType.BACKGROUND_2_NIGHT),
				"a mode nobody registered keeps the game's own background");
		assertEquals(Map.of(GameMode.GAMEMODE_CHALLENGE_AIR_RAID, BackgroundType.BACKGROUND_1_DAY,
				GameMode.GAMEMODE_CHALLENGE_ICE, BackgroundType.BACKGROUND_3_POOL), BoardOverrides.modeBackgrounds());

		BoardOverrides.reset();
		assertFalse(BoardOverrides.graveModeEnabled());
		assertEquals(5, BoardOverrides.graveStoneWaveThreshold());
		assertEquals(1, BoardOverrides.whackAZombieZombieCount());
		assertTrue(BoardOverrides.modeBackgrounds().isEmpty());
	}

	@Test
	void badGraveModeValuesAreRefused() {
		assertThrows(IllegalArgumentException.class, () -> BoardOverrides.graveStoneWaveThreshold(-1));
		assertThrows(IllegalArgumentException.class, () -> BoardOverrides.whackAZombieZombieCount(0));
		assertThrows(IllegalArgumentException.class, () -> BoardOverrides.modeBackground(-1, BackgroundType.BACKGROUND_1_DAY));
		assertThrows(IllegalArgumentException.class,
				() -> BoardOverrides.modeBackground(GameMode.GAMEMODE_CHALLENGE_ICE, -1));
		assertEquals(5, BoardOverrides.graveStoneWaveThreshold(), "a refused value is not recorded");
		assertEquals(1, BoardOverrides.whackAZombieZombieCount());
		assertTrue(BoardOverrides.modeBackgrounds().isEmpty());
	}
}
