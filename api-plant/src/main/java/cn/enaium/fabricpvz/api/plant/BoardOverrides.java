package cn.enaium.fabricpvz.api.plant;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import cn.enaium.fabricpvz.portable.lawn.Challenge;
import cn.enaium.fabricpvz.portable.lawn.CoinType;
import cn.enaium.fabricpvz.portable.lawn.GameConstants;
import cn.enaium.fabricpvz.portable.lawn.SeedType;
import cn.enaium.fabricpvz.portable.lawn.ZombieType;

/**
 * The board-level knobs a rebalance of the shipped game needs and the per-plant / per-zombie
 * overrides cannot carry: which zombie types a mode allows, the two vetoes in "may this zombie appear
 * on this level", the single cells of the game's allowed-levels table, and the hand-full of global
 * numbers the board works with (challenge win score, instant-kill damage, raining-seed interval,
 * grave stones, sun values, sun cap, conveyor-belt seed list, sky drop, death loot, the starting sun
 * of a Last Stand level, a coin's usable seed type, the wave counts, seed-picker vetoes), plus the
 * grave mode itself ({@link #graveModeEnabled(boolean)} / {@link #graveStoneWaveThreshold(int)}), how
 * many zombies a whack-a-zombie wave rises ({@link #whackAZombieZombieCount(int)}) and the background
 * a mode is played on ({@link #modeBackground(int, int)}).
 *
 * <pre>{@code
 * BoardOverrides.allowZombies(GameMode.GAMEMODE_CHALLENGE_SPEED, ZombieType.ZOMBIE_FOOTBALL);
 * BoardOverrides.relaxZombieSpawnRules(true);
 * BoardOverrides.allowZombieOnLevel(ZombieType.ZOMBIE_NEWSPAPER, 11, false);
 * BoardOverrides.beghouledWinningScore(100).sunMoneyCap(2_000_000_000);
 * }</pre>
 *
 * <p>Two kinds of knob live here, and which is which is visible in the javadoc of each method:
 *
 * <ul>
 *   <li><b>Immediate writes</b> go straight into the game's own data - {@code Challenge.gZombieAllowedLevels}
 *       for {@link #allowZombieOnLevel(int, int, boolean)} - exactly like {@code PlantOverrides} writes
 *       {@code Plant.gPlantDefs}: the game reads those tables itself, so no Mixin is involved and the
 *       value is in place the moment it is written. {@link #reset()} puts the retail value back.</li>
 *   <li><b>Recorded knobs</b> are answers this module hands to the game through its Mixins. Every one
 *       of them defaults to the game's own value, so a process that never calls the setter runs exactly
 *       the game's code - which is what makes the module usable by any mod and not just by a rebalance.</li>
 * </ul>
 *
 * <p>Nothing here is per-object: a board-level knob is process-wide, so the facade is static and the
 * last value written wins. Every setter is idempotent, every call is thread-safe, and
 * {@link #registered()} is the diagnostics entry that lists what a mod actually changed.
 */
public final class BoardOverrides {

    private static final Object LOCK = new Object();

    /** Every knob a mod set, keyed by name, in the order it was first set; the diagnostics view. */
    private static final Map<String, Object> SET = new LinkedHashMap<>();

    /** game mode -> the zombie types the mode allows in addition to the game's own list. */
    private static final Map<Integer, int[]> ZOMBIES_ALLOWED = new LinkedHashMap<>();

    /** game mode -> the seed types the seed picker refuses, per mode. */
    private static final Map<Integer, int[]> SEEDS_NOT_ALLOWED = new LinkedHashMap<>();

    /** game mode -> (seed type the game picked -> the seed type it becomes). */
    private static final Map<Integer, Map<Integer, Integer>> CONVEYOR_SEEDS = new LinkedHashMap<>();

    /** zombie type -> (level -> the allowed flag the table cell was set to), i.e. the writes to do. */
    private static final Map<Integer, Map<Integer, Boolean>> LEVEL_ALLOWED = new LinkedHashMap<>();

    /** The retail value of every {@code gZombieAllowedLevels} cell this module overwrote, for reset. */
    private static final Map<Integer, Map<Integer, Integer>> LEVEL_ALLOWED_RESTORE = new LinkedHashMap<>();

    /** the wave count the game's own branch assigns -> the count it assigns instead. */
    private static final Map<Integer, Integer> WAVE_COUNTS = new LinkedHashMap<>();

    /** game mode -> the background the mode is played on instead of the game's own. */
    private static final Map<Integer, Integer> MODE_BACKGROUNDS = new LinkedHashMap<>();

    private BoardOverrides() {
    }

    // -------------------------------------------------------------------------------------------
    // A. what a mode allows
    // -------------------------------------------------------------------------------------------

    /**
     * Allows zombie types in a game mode <em>in addition</em> to the ones the game's own
     * {@code Challenge::InitZombieWaves} branch set, applied right after that branch filled
     * {@code Board.mZombieAllowed} - i.e. before the wave picker reads the array, so the added types
     * really take part in the level's waves.
     *
     * <p>Idempotent per mode and additive across calls: asking twice for the same type or calling for
     * a second set of types leaves one entry per mode that holds the union.
     *
     * @param theGameMode   a {@code GameMode} value
     * @param theZombieTypes the {@code ZombieType} values to allow, any order, duplicates ignored
     * @throws IllegalArgumentException if the mode is negative or a value is not a zombie type
     */
    public static void allowZombies(int theGameMode, int... theZombieTypes) {
        if (theGameMode < 0) {
            throw new IllegalArgumentException("Not a game mode: " + theGameMode);
        }
        for (int aZombieType : theZombieTypes) {
            checkZombieType(aZombieType);
        }

        synchronized (LOCK) {
            int[] aCurrent = ZOMBIES_ALLOWED.get(theGameMode);
            int[] aMerged = merge(aCurrent, theZombieTypes);
            ZOMBIES_ALLOWED.put(theGameMode, aMerged);
            SET.put("allowZombies[" + theGameMode + ']', joined(aMerged));
        }
    }

    /**
     * @return every mode's additional allowed zombies, keyed by game mode, in the order the modes were
     *     first asked for; for diagnostics, the map is a snapshot and cannot be written through
     */
    public static Map<Integer, int[]> allowedZombies() {
        synchronized (LOCK) {
            Map<Integer, int[]> aSnapshot = new LinkedHashMap<>();
            for (Map.Entry<Integer, int[]> aEntry : ZOMBIES_ALLOWED.entrySet()) {
                aSnapshot.put(aEntry.getKey(), aEntry.getValue().clone());
            }
            return Collections.unmodifiableMap(aSnapshot);
        }
    }

    /**
     * Applies the registered additional allowed zombies of a mode to the game's own allowed array.
     *
     * <p><b>Internal.</b> Called by this module's Challenge Mixin at the return of
     * {@code Challenge::initZombieWaves} - after the game's own per-mode branch, before the wave
     * picker runs. A mode nobody registered for is left exactly as the game filled it.
     *
     * @param theGameMode the mode the board is being set up for
     * @param theZombieAllowed the board's own {@code mZombieAllowed} array
     */
    public static void applyZombieAllowed(int theGameMode, boolean[] theZombieAllowed) {
        int[] aTypes;
        synchronized (LOCK) {
            aTypes = ZOMBIES_ALLOWED.get(theGameMode);
        }
        if (aTypes == null) {
            return;
        }

        for (int aZombieType : aTypes) {
            if (aZombieType < theZombieAllowed.length) {
                theZombieAllowed[aZombieType] = true;
            }
        }
    }

    // -------------------------------------------------------------------------------------------
    // B. may this zombie appear on this level
    // -------------------------------------------------------------------------------------------

    /**
     * Drops the game's two per-zombie vetoes in "may this zombie appear on this level": the
     * {@code theLevel < ZombieDefinition.mStartingLevel} check and the
     * {@code ZombieDefinition.mPickWeight == 0} check. What is left is the
     * {@code Challenge.gZombieAllowedLevels} table alone, which then has the last word - the reason a
     * rebalance that loosens the rules has to move table cells as well.
     *
     * <p>Off by default: with no call the game decides exactly as it always did.
     *
     * @param theRelaxed true to decide by the allowed-levels table alone
     */
    public static void relaxZombieSpawnRules(boolean theRelaxed) {
        synchronized (LOCK) {
            SET.put("relaxZombieSpawnRules", theRelaxed);
        }
    }

    /** @return whether the two per-zombie vetoes are dropped; false by default (the game's own rule) */
    public static boolean zombieSpawnRulesRelaxed() {
        synchronized (LOCK) {
            return Boolean.TRUE.equals(SET.get("relaxZombieSpawnRules"));
        }
    }

    // -------------------------------------------------------------------------------------------
    // C. the allowed-levels table
    // -------------------------------------------------------------------------------------------

    /**
     * Sets one cell of {@code Challenge.gZombieAllowedLevels}, i.e. the answer for "is this zombie type
     * allowed on this level" once the two vetoes of {@link #relaxZombieSpawnRules(boolean)} are out of
     * the way (and, when a cell says no, also while they are in it).
     *
     * <p>This is an <b>immediate write</b> into the game's own table - the table the game reads - and
     * it is undoable: {@link #reset()} puts the retail cell back. The first write of a cell remembers
     * the retail value; later writes of the same cell just move it.
     *
     * @param theZombieType a {@code ZombieType} value that has a row in the table
     * @param theLevel      the level, 1-based ({@code 1}..{@link GameConstants#NUM_LEVELS})
     * @param theAllowed    the new cell value
     * @throws IllegalArgumentException if the type has no row or the level is out of range
     */
    public static void allowZombieOnLevel(int theZombieType, int theLevel, boolean theAllowed) {
        checkZombieType(theZombieType);
        if (theLevel < 1 || theLevel > GameConstants.NUM_LEVELS) {
            throw new IllegalArgumentException("Not a level: " + theLevel);
        }

        synchronized (LOCK) {
            if (theZombieType >= Challenge.gZombieAllowedLevels.length) {
                throw new IllegalArgumentException(
                        "No allowed-levels row for zombie type: " + theZombieType);
            }

            int aIndex = theLevel - 1;
            int[] aRow = Challenge.gZombieAllowedLevels[theZombieType].mAllowedOnLevel;
            LEVEL_ALLOWED_RESTORE
                    .computeIfAbsent(theZombieType, type -> new LinkedHashMap<>())
                    .putIfAbsent(theLevel, aRow[aIndex]);

            aRow[aIndex] = theAllowed ? 1 : 0;
            LEVEL_ALLOWED.computeIfAbsent(theZombieType, type -> new LinkedHashMap<>())
                    .put(theLevel, theAllowed);
            SET.put("allowZombieOnLevel[" + theZombieType + '@' + theLevel + ']', theAllowed);
        }
    }

    /**
     * @return every allowed-levels cell this module overwrote, zombie type -> (level -> new value);
     *     for diagnostics, the map is a snapshot and cannot be written through
     */
    public static Map<Integer, Map<Integer, Boolean>> levelOverrides() {
        synchronized (LOCK) {
            Map<Integer, Map<Integer, Boolean>> aSnapshot = new LinkedHashMap<>();
            for (Map.Entry<Integer, Map<Integer, Boolean>> aEntry : LEVEL_ALLOWED.entrySet()) {
                aSnapshot.put(aEntry.getKey(), Collections.unmodifiableMap(new LinkedHashMap<>(aEntry.getValue())));
            }
            return Collections.unmodifiableMap(aSnapshot);
        }
    }

    // -------------------------------------------------------------------------------------------
    // D. the global numbers
    // -------------------------------------------------------------------------------------------

    /** The retail Beghouled winning score, i.e. the value the game uses when nothing is set. */
    public static final int DEFAULT_BEGHOULED_WINNING_SCORE = Challenge.BEGHOULED_WINNING_SCORE;

    /**
     * The score Beghouled / Beghouled Twist is won at, i.e. the number the game shows in its "{SCORE}"
     * hint, stops counting at, and fills its progress meter to. The "almost there" hint stays five
     * points below it, exactly as the game's own constant does.
     *
     * @param theScore the new winning score; must be positive
     */
    public static void beghouledWinningScore(int theScore) {
        if (theScore <= 0) {
            throw new IllegalArgumentException("A winning score must be positive: " + theScore);
        }
        synchronized (LOCK) {
            SET.put("beghouledWinningScore", theScore);
        }
    }

    /** @return the winning score the game will use, its own when nothing was set */
    public static int beghouledWinningScore() {
        synchronized (LOCK) {
            Object aValue = SET.get("beghouledWinningScore");
            return aValue == null ? DEFAULT_BEGHOULED_WINNING_SCORE : (Integer) aValue;
        }
    }

    /**
     * The damage {@code Board::KillAllZombiesInRadius} applies to every zombie in the blast, i.e. the
     * instant kill behind the cherry bomb, the jalapeno and the rest of the "kills in a radius" plants.
     *
     * @param theDamage the new damage; must be positive
     */
    public static void killAllZombiesDamage(int theDamage) {
        if (theDamage <= 0) {
            throw new IllegalArgumentException("A blast damage must be positive: " + theDamage);
        }
        synchronized (LOCK) {
            SET.put("killAllZombiesDamage", theDamage);
        }
    }

    /** @return the blast damage the game will use, its own when nothing was set */
    public static int killAllZombiesDamage() {
        synchronized (LOCK) {
            Object aValue = SET.get("killAllZombiesDamage");
            return aValue == null ? DEFAULT_KILL_ALL_ZOMBIES_DAMAGE : (Integer) aValue;
        }
    }

    /** The retail blast damage, i.e. the value the game uses when nothing is set. */
    public static final int DEFAULT_KILL_ALL_ZOMBIES_DAMAGE = 1800;

    /**
     * The pause {@code Challenge::UpdateRainingSeeds} waits before it drops the next seed packet, as
     * the inclusive range the game draws from. Both ends have to be given together because the game
     * draws one number from the pair.
     *
     * @param theMinimum the shortest pause; must be positive
     * @param theMaximum the longest pause; must not be below the minimum
     */
    public static void rainSeedInterval(int theMinimum, int theMaximum) {
        if (theMinimum <= 0 || theMaximum < theMinimum) {
            throw new IllegalArgumentException("Not an interval: " + theMinimum + ".." + theMaximum);
        }
        synchronized (LOCK) {
            SET.put("rainSeedIntervalMin", theMinimum);
            SET.put("rainSeedIntervalMax", theMaximum);
        }
    }

    /** @return the shortest pause between two dropped seed packets, the game's own when nothing was set */
    public static int rainSeedIntervalMin() {
        synchronized (LOCK) {
            Object aValue = SET.get("rainSeedIntervalMin");
            return aValue == null ? DEFAULT_RAIN_SEED_MIN : (Integer) aValue;
        }
    }

    /** @return the longest pause between two dropped seed packets, the game's own when nothing was set */
    public static int rainSeedIntervalMax() {
        synchronized (LOCK) {
            Object aValue = SET.get("rainSeedIntervalMax");
            return aValue == null ? DEFAULT_RAIN_SEED_MAX : (Integer) aValue;
        }
    }

    /** The retail raining-seeds interval, i.e. the range the game draws from when nothing is set. */
    public static final int DEFAULT_RAIN_SEED_MIN = 500;
    /** The retail raining-seeds interval's upper end; see {@link #DEFAULT_RAIN_SEED_MIN}. */
    public static final int DEFAULT_RAIN_SEED_MAX = 999;

    /**
     * Forces the answer of {@code Board::StageHasGraveStones}, i.e. whether a level gets grave stones
     * at all.
     *
     * @param theHasGraveStones true to force "this stage has grave stones", false to force "it has none"
     */
    public static void stageHasGraveStones(boolean theHasGraveStones) {
        synchronized (LOCK) {
            SET.put("stageHasGraveStones", theHasGraveStones);
        }
    }

    /**
     * @return the forced answer of {@code Board::StageHasGraveStones}, or {@code null} when the game's
     *     own rule decides; for the Mixin
     */
    public static Boolean stageHasGraveStonesOverride() {
        synchronized (LOCK) {
            return (Boolean) SET.get("stageHasGraveStones");
        }
    }

    /**
     * The three sun values the game's coins carry: the normal sun, the small sun and the large sun.
     *
     * @param theSunValue      the value of a {@code COIN_SUN}
     * @param theSmallSunValue the value of a {@code COIN_SMALLSUN}
     * @param theLargeSunValue the value of a {@code COIN_LARGESUN}
     */
    public static void sunValues(int theSunValue, int theSmallSunValue, int theLargeSunValue) {
        if (theSunValue < 0 || theSmallSunValue < 0 || theLargeSunValue < 0) {
            throw new IllegalArgumentException("Sun values cannot be negative");
        }
        synchronized (LOCK) {
            SET.put("sunValue[" + CoinType.COIN_SUN + ']', theSunValue);
            SET.put("sunValue[" + CoinType.COIN_SMALLSUN + ']', theSmallSunValue);
            SET.put("sunValue[" + CoinType.COIN_LARGESUN + ']', theLargeSunValue);
        }
    }

    /**
     * The value a sun coin carries under the override.
     *
     * <p><b>Internal.</b> Called by this module's Coin Mixin at the head of {@code Coin::GetSunValue};
     * a coin type nobody overrode (or a call with no override set at all) answers {@code -1}, which
     * tells the Mixin to leave the game's own answer alone.
     *
     * @param theCoinType a {@code CoinType} value
     * @return the overridden sun value, or {@code -1} to keep the game's own
     */
    public static int sunValue(int theCoinType) {
        synchronized (LOCK) {
            Object aValue = SET.get("sunValue[" + theCoinType + ']');
            return aValue == null ? -1 : (Integer) aValue;
        }
    }

    /**
     * The ceiling {@code Board::AddSunMoney} clamps the sun bank to.
     *
     * @param theCap the new ceiling; must not be negative
     */
    public static void sunMoneyCap(int theCap) {
        if (theCap < 0) {
            throw new IllegalArgumentException("A sun cap cannot be negative: " + theCap);
        }
        synchronized (LOCK) {
            SET.put("sunMoneyCap", theCap);
        }
    }

    /** @return the sun ceiling the game will clamp to, its own when nothing was set */
    public static int sunMoneyCap() {
        synchronized (LOCK) {
            Object aValue = SET.get("sunMoneyCap");
            return aValue == null ? DEFAULT_SUN_MONEY_CAP : (Integer) aValue;
        }
    }

    /** The retail sun ceiling, i.e. the value the game clamps to when nothing is set. */
    public static final int DEFAULT_SUN_MONEY_CAP = 9990;

    /**
     * Replaces a seed type of a mode's conveyor-belt seed list: where the game's own list holds
     * {@code theFromSeedType}, the belt offers {@code theToSeedType} instead.
     *
     * <p>The replacement is a pure function of the picked seed type (weights, the game's own
     * per-seed weight adjustments included), so it means what "this entry of the list is that entry"
     * means. Idempotent per (mode, from) pair, and several pairs can be registered per mode.
     *
     * @param theGameMode     a {@code GameMode} value
     * @param theFromSeedType the seed type the game's own list holds
     * @param theToSeedType   the seed type the belt gets instead
     */
    public static void conveyorSeed(int theGameMode, int theFromSeedType, int theToSeedType) {
        if (theGameMode < 0) {
            throw new IllegalArgumentException("Not a game mode: " + theGameMode);
        }
        if (theFromSeedType < 0 || theToSeedType < 0) {
            throw new IllegalArgumentException("Not a seed type: " + theFromSeedType + " -> " + theToSeedType);
        }
        synchronized (LOCK) {
            CONVEYOR_SEEDS.computeIfAbsent(theGameMode, mode -> new LinkedHashMap<>())
                    .put(theFromSeedType, theToSeedType);
            SET.put("conveyorSeed[" + theGameMode + ']', describe(CONVEYOR_SEEDS.get(theGameMode)));
        }
    }

    /**
     * The seed type the belt gets where the game picked another one.
     *
     * <p><b>Internal.</b> Called by this module's Challenge Mixin where
     * {@code Challenge::UpdateConveyorBelt} hands its pick to the seed bank; the game's own pick is
     * answered unchanged when the mode (or the seed type) was not registered.
     *
     * @param theGameMode the mode the conveyor belongs to
     * @param theSeedType the seed type the game picked
     * @return the seed type the belt gets
     */
    public static int conveyorSeed(int theGameMode, int theSeedType) {
        synchronized (LOCK) {
            Map<Integer, Integer> aMode = CONVEYOR_SEEDS.get(theGameMode);
            if (aMode == null) {
                return theSeedType;
            }
            Integer aReplacement = aMode.get(theSeedType);
            return aReplacement == null ? theSeedType : aReplacement;
        }
    }

    /** @return every conveyor-belt seed replacement, mode -> (from -> to); a read-only snapshot */
    public static Map<Integer, Map<Integer, Integer>> conveyorSeeds() {
        synchronized (LOCK) {
            Map<Integer, Map<Integer, Integer>> aSnapshot = new LinkedHashMap<>();
            for (Map.Entry<Integer, Map<Integer, Integer>> aEntry : CONVEYOR_SEEDS.entrySet()) {
                aSnapshot.put(aEntry.getKey(), Collections.unmodifiableMap(new LinkedHashMap<>(aEntry.getValue())));
            }
            return Collections.unmodifiableMap(aSnapshot);
        }
    }

    /**
     * The game mode whose sky drop is not an ordinary sun and the coin type it drops, i.e. the two
     * numbers behind "at this mode, seed packets fall from the sky instead of sun".
     *
     * @param theGameMode the mode the sky drop special-cases
     * @param theCoinType the coin type that mode drops
     */
    public static void sunSpawning(int theGameMode, int theCoinType) {
        if (theGameMode < 0) {
            throw new IllegalArgumentException("Not a game mode: " + theGameMode);
        }
        synchronized (LOCK) {
            SET.put("sunSpawningMode", theGameMode);
            SET.put("sunSpawningCoinType", theCoinType);
        }
    }

    /** @return the mode whose sky drop is special-cased, the game's own when nothing was set */
    public static int sunSpawningMode() {
        synchronized (LOCK) {
            Object aValue = SET.get("sunSpawningMode");
            return aValue == null ? DEFAULT_SUN_SPAWNING_MODE : (Integer) aValue;
        }
    }

    /** @return the coin type the special sky drop is, the game's own when nothing was set */
    public static int sunSpawningCoinType() {
        synchronized (LOCK) {
            Object aValue = SET.get("sunSpawningCoinType");
            return aValue == null ? DEFAULT_SUN_SPAWNING_COIN_TYPE : (Integer) aValue;
        }
    }

    /** The mode the game's own sky drop special-cases, i.e. {@code GAMEMODE_CHALLENGE_SUNNY_DAY}. */
    public static final int DEFAULT_SUN_SPAWNING_MODE = cn.enaium.fabricpvz.portable.lawn.GameMode.GAMEMODE_CHALLENGE_SUNNY_DAY;
    /** The coin the game's own sky drop special-case drops, i.e. {@code COIN_LARGESUN}. */
    public static final int DEFAULT_SUN_SPAWNING_COIN_TYPE = CoinType.COIN_LARGESUN;

    /**
     * The coin an ordinary level's sky drop is: what {@code Board::UpdateSunSpawning} lets fall from the
     * sky on every stage that is not the special mode above. The two knobs are independent because they
     * are two numbers in the game's own ternary - the mode it special-cases and what falls there - and
     * the ordinary coin is what every other stage drops.
     *
     * @param theCoinType the coin ordinary stages drop, a {@code CoinType} value
     */
    public static void skyDropCoin(int theCoinType) {
        if (theCoinType < 0) {
            throw new IllegalArgumentException("Not a coin type: " + theCoinType);
        }
        synchronized (LOCK) {
            SET.put("skyDropCoinType", theCoinType);
        }
    }

    /**
     * @return the coin the ordinary sky drop is, the game's own when nothing was set
     */
    public static int skyDropCoin() {
        synchronized (LOCK) {
            Object aValue = SET.get("skyDropCoinType");
            return aValue == null ? DEFAULT_SKY_DROP_COIN_TYPE : (Integer) aValue;
        }
    }

    /** The coin the game's own ordinary sky drop is, i.e. {@code COIN_SUN}. */
    public static final int DEFAULT_SKY_DROP_COIN_TYPE = CoinType.COIN_SUN;

    /**
     * Whether {@code Board::DropLootPiece} always takes the "whack-a-zombie" sun branch - the three sun
     * coins a zombie drops when it dies - whatever the level is.
     *
     * <p>The game's own rule runs that branch only in a whack-a-zombie level
     * ({@code LawnApp::IsWhackAZombieLevel}), and the branch still falls through to the rest of the
     * loot table when its random roll misses; this only removes the level test.
     *
     * @param theAlwaysSunBranch true to run the branch on every level
     */
    public static void dropLootSunBranch(boolean theAlwaysSunBranch) {
        synchronized (LOCK) {
            SET.put("dropLootSunBranch", theAlwaysSunBranch);
        }
    }

    /** @return whether the death-loot sun branch runs on every level; false by default */
    public static boolean dropLootSunBranch() {
        synchronized (LOCK) {
            return Boolean.TRUE.equals(SET.get("dropLootSunBranch"));
        }
    }

    /**
     * The three coins {@code Board::DropLootPiece}'s sun branch drops, in the order it drops them
     * (20, 40 and 60 pixels left of the zombie, i.e. the order they appear on the board).
     *
     * @param theFirstCoinType  the coin nearest the zombie
     * @param theSecondCoinType the coin in the middle
     * @param theThirdCoinType  the coin furthest left
     */
    public static void dropLootCoins(int theFirstCoinType, int theSecondCoinType, int theThirdCoinType) {
        if (theFirstCoinType < 0 || theSecondCoinType < 0 || theThirdCoinType < 0) {
            throw new IllegalArgumentException("Not a coin type: " + theFirstCoinType + " / "
                    + theSecondCoinType + " / " + theThirdCoinType);
        }
        synchronized (LOCK) {
            SET.put("dropLootCoin[0]", theFirstCoinType);
            SET.put("dropLootCoin[1]", theSecondCoinType);
            SET.put("dropLootCoin[2]", theThirdCoinType);
        }
    }

    /**
     * The coin one of the three death-loot drops really is.
     *
     * <p><b>Internal.</b> Called by this module's Board Mixin on the three {@code Board::AddCoin} calls
     * of {@code Board::DropLootPiece}'s sun branch; an index nobody registered answers the game's own
     * coin - {@code COIN_SUN}, which is what the branch passes.
     *
     * @param theIndex {@code 0}..{@code 2}, the drop's position in the branch
     * @return the coin type that drop is
     */
    public static int dropLootCoin(int theIndex) {
        synchronized (LOCK) {
            Object aValue = SET.get("dropLootCoin[" + theIndex + ']');
            return aValue == null ? DEFAULT_DROP_LOOT_COIN_TYPE : (Integer) aValue;
        }
    }

    /** The coin the game's own death-loot drops all three are, i.e. {@code COIN_SUN}. */
    public static final int DEFAULT_DROP_LOOT_COIN_TYPE = CoinType.COIN_SUN;

    /**
     * The sun a level starts with in {@code Board::InitLevel}'s Last Stand branch.
     *
     * <p>That branch is the only one of the method that hands out a large starting bank (every other
     * branch gives 0, 150 or 50; i.e. an amount that is a starting balance for a level you build from
     * nothing), so this is the number the 95 Edition's "Last Stand starts with 8000" moves.
     *
     * @param theSun the starting sun; must not be negative
     */
    public static void lastStandInitialSun(int theSun) {
        if (theSun < 0) {
            throw new IllegalArgumentException("A starting sun cannot be negative: " + theSun);
        }
        synchronized (LOCK) {
            SET.put("lastStandInitialSun", theSun);
        }
    }

    /** @return the sun a Last Stand level starts with, the game's own when nothing was set */
    public static int lastStandInitialSun() {
        synchronized (LOCK) {
            Object aValue = SET.get("lastStandInitialSun");
            return aValue == null ? DEFAULT_LAST_STAND_INITIAL_SUN : (Integer) aValue;
        }
    }

    /** The sun the game's own Last Stand levels start with, i.e. 5000. */
    public static final int DEFAULT_LAST_STAND_INITIAL_SUN = 5000;

    /**
     * The seed type {@code Coin::CoinInitialize} gives a coin as its usable seed type, i.e. the seed a
     * "usable seed packet" coin hands the cursor when the coin itself was not given one (the air-raid
     * packets {@code ChallengeWeather} builds are given one explicitly and keep it).
     *
     * @param theSeedType a {@code SeedType} value, or {@code SEED_NONE} for the game's own "no seed"
     */
    public static void coinUsableSeedType(int theSeedType) {
        if (theSeedType < 0 || theSeedType >= SeedType.MAX_SEED_TYPES) {
            throw new IllegalArgumentException("Not a seed type: " + theSeedType);
        }
        synchronized (LOCK) {
            SET.put("coinUsableSeedType", theSeedType);
        }
    }

    /** @return the seed type a new coin carries, the game's own when nothing was set */
    public static int coinUsableSeedType() {
        synchronized (LOCK) {
            Object aValue = SET.get("coinUsableSeedType");
            return aValue == null ? DEFAULT_COIN_USABLE_SEED_TYPE : (Integer) aValue;
        }
    }

    /** The seed type the game's own {@code Coin::CoinInitialize} gives a coin, i.e. {@code SEED_NONE}. */
    public static final int DEFAULT_COIN_USABLE_SEED_TYPE = SeedType.SEED_NONE;

    /**
     * Replaces a wave count the game assigns: wherever {@code BoardWaves} would give a level
     * {@code theRetailWaves} waves it gives it {@code theWaves} instead.
     *
     * <p>The game counts waves in two places, and a rebalance that moves the counts has to move both:
     * {@code BoardWaves::GetNumWavesPerSurvivalStage}, which answers 10 for a Last Stand or normal
     * survival stage and 20 for a hard or endless one (and is also what the flag bar divides by), and
     * {@code BoardWaves::PickZombieWaves}, whose branches give 8 to an adventure whack-a-zombie level,
     * {@code Challenge.gZombieWaves} to an adventure level, 0 to the zen garden, 12 to a whack-a-zombie
     * challenge, 20 to the Wall-nut Bowling / Air Raid / Grave Danger / High Gravity / Portal Combat /
     * War and Peas / Invisighoul group, 30 to the Stormy Night / Little Trouble / Bungee Blitz / Column
     * / Shovel / War and Peas 2 / Wall-nut Bowling 2 / Pogo Party group and 40 to every other
     * challenge. A retail count nobody replaced is left exactly as the game wrote it, including the
     * counts the game computes rather than writes (an adventure level's own table value and the +10 of
     * a second run).
     *
     * @param theRetailWaves the count the game's own branch assigns; must not be negative
     * @param theWaves       what that branch assigns instead; must not be negative
     */
    public static void numWaves(int theRetailWaves, int theWaves) {
        if (theRetailWaves < 0 || theWaves < 0) {
            throw new IllegalArgumentException("A wave count cannot be negative: " + theRetailWaves
                    + " -> " + theWaves);
        }
        synchronized (LOCK) {
            WAVE_COUNTS.put(theRetailWaves, theWaves);
            SET.put("numWaves[" + theRetailWaves + ']', theWaves);
        }
    }

    /**
     * What the number of waves the game's own branch assigns becomes.
     *
     * <p><b>Internal.</b> Called by this module's BoardWaves Mixin at the wave-count constants of
     * {@code BoardWaves::GetNumWavesPerSurvivalStage} and {@code BoardWaves::PickZombieWaves}, so what
     * the game compares, stores and later reads is the replacement.
     *
     * @param theRetailWaves the count the game's own branch assigns
     * @return the count to assign instead
     */
    public static int numWaves(int theRetailWaves) {
        synchronized (LOCK) {
            Integer aReplacement = WAVE_COUNTS.get(theRetailWaves);
            return aReplacement == null ? theRetailWaves : aReplacement;
        }
    }

    /**
     * @return every replaced wave count, retail count -> the count assigned instead; for diagnostics, a
     *     read-only snapshot
     */
    public static Map<Integer, Integer> waveCounts() {
        synchronized (LOCK) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(WAVE_COUNTS));
        }
    }

    /**
     * Makes every level multiply the zombie <em>points</em> budget of each of its waves by this factor,
     * in place of the game's own per-level choice.
     *
     * <pre>{@code
     * BoardOverrides.spawnMultiplier(4);
     * }</pre>
     *
     * <p>{@code BoardWaves::PickZombieWaves} gives a wave a budget and then spends it on zombies, so
     * the budget - not the number of zombies written into the wave - is what a rebalance scales. The
     * game multiplies it by a per-level factor (×6 for Column Like You See 'Em, ×4 for Little Trouble
     * and Wall-nut Bowling, ×3 for a mini-boss or an adventure Stormy Night, ×2 for Shovel, Bungee
     * Blitz, Portal Combat and Invisighoul, ×1 for everything else); registering a factor makes the
     * ×4 arm of that chain the one every level takes, with the registered number as its factor, so a
     * level that used to pick its own factor picks this one instead. The ×6 arm keeps its own factor
     * because the game tests its mode before the arm this replaces - the same order a patched game
     * binary keeps when it makes an arm unconditional.
     *
     * <p>The default is {@link #DEFAULT_SPAWN_MULTIPLIER}, i.e. the game's own per-level factor, and
     * registering that value takes a factor back - so a test can measure a level's retail budget and
     * the budget the rule gives it in one run.
     *
     * @param theMultiplier the factor every level's wave budget is multiplied by, or
     *     {@link #DEFAULT_SPAWN_MULTIPLIER} to put the game's own per-level factor back
     * @throws IllegalArgumentException if the value is negative
     */
    public static void spawnMultiplier(int theMultiplier) {
        if (theMultiplier < 0) {
            throw new IllegalArgumentException("A spawn multiplier cannot be negative: " + theMultiplier);
        }
        synchronized (LOCK) {
            if (theMultiplier == DEFAULT_SPAWN_MULTIPLIER) {
                SET.remove("spawnMultiplier");
            } else {
                SET.put("spawnMultiplier", theMultiplier);
            }
        }
    }

    /**
     * The factor the level's wave budgets are multiplied by.
     *
     * <p><b>Internal.</b> Called by this module's BoardWaves Mixin at the arm of
     * {@code BoardWaves::PickZombieWaves} that multiplies a wave's zombie points, and at the condition
     * that arms it: a positive answer both takes that arm on every level and is the factor it applies.
     *
     * @return the registered factor, or {@link #DEFAULT_SPAWN_MULTIPLIER}
     */
    public static int spawnMultiplier() {
        synchronized (LOCK) {
            Object aValue = SET.get("spawnMultiplier");
            return aValue == null ? DEFAULT_SPAWN_MULTIPLIER : (Integer) aValue;
        }
    }

    /** The value of {@link #spawnMultiplier()} when nobody registered a factor: the game's own one. */
    public static final int DEFAULT_SPAWN_MULTIPLIER = 0;

    /**
     * Makes a game mode's seed chooser refuse seed types, in addition to the ones the game itself
     * refuses (Last Stand's sun plants).
     *
     * @param theGameMode  a {@code GameMode} value
     * @param theSeedTypes the {@code SeedType} values the picker refuses in that mode
     */
    public static void seedNotAllowedToPick(int theGameMode, int... theSeedTypes) {
        if (theGameMode < 0) {
            throw new IllegalArgumentException("Not a game mode: " + theGameMode);
        }
        for (int aSeedType : theSeedTypes) {
            if (aSeedType < 0) {
                throw new IllegalArgumentException("Not a seed type: " + aSeedType);
            }
        }
        synchronized (LOCK) {
            int[] aMerged = merge(SEEDS_NOT_ALLOWED.get(theGameMode), theSeedTypes);
            SEEDS_NOT_ALLOWED.put(theGameMode, aMerged);
            SET.put("seedNotAllowedToPick[" + theGameMode + ']', joined(aMerged));
        }
    }

    /**
     * Whether the seed picker refuses a seed type in a mode under the override.
     *
     * <p><b>Internal.</b> Called by this module's SeedChooserScreen Mixin at the return of
     * {@code SeedChooserScreen::SeedNotAllowedToPick}, which ORs it into the game's own answer. The
     * name carries the {@code Override} suffix so it cannot be confused with (or shadow) the
     * {@link #seedNotAllowedToPick(int, int...)} registration entry point.
     *
     * @param theGameMode the mode the chooser is in
     * @param theSeedType the seed type being asked about
     * @return true to refuse the seed type
     */
    public static boolean seedNotAllowedToPickOverride(int theGameMode, int theSeedType) {
        int[] aTypes;
        synchronized (LOCK) {
            aTypes = SEEDS_NOT_ALLOWED.get(theGameMode);
        }
        if (aTypes == null) {
            return false;
        }

        for (int aType : aTypes) {
            if (aType == theSeedType) {
                return true;
            }
        }
        return false;
    }

    /**
     * Inverts {@code Board::SeedNotRecommendedForLevel}'s "needs grave stones" bit, i.e. Grave Buster
     * is marked "not recommended" exactly when the stage <em>does</em> have grave stones.
     *
     * @param theInverted true to invert the bit
     */
    public static void graveBusterNeedsGravesInverted(boolean theInverted) {
        synchronized (LOCK) {
            SET.put("graveBusterNeedsGravesInverted", theInverted);
        }
    }

    /** @return whether the "needs grave stones" bit is inverted; false by default */
    public static boolean graveBusterNeedsGravesInverted() {
        synchronized (LOCK) {
            return Boolean.TRUE.equals(SET.get("graveBusterNeedsGravesInverted"));
        }
    }

    // -------------------------------------------------------------------------------------------
    // E. the grave mode
    // -------------------------------------------------------------------------------------------

    /**
     * Makes every level behave like Grave Danger's own levels: {@code Challenge::SpawnZombieWave}
     * raises its grave branch on every level instead of only on {@code GAMEMODE_CHALLENGE_GRAVE_DANGER},
     * so - on every level whose wave count is more than one flag group long - a grave stone grows after
     * the first flag wave ({@link #graveStoneWaveThreshold(int)} for "when"), and each flag wave rises a
     * zombie out of every grave stone that has finished growing.
     *
     * <p>The branch that decides this is the game's own; what the knob changes is which level it runs
     * on. The branch itself keeps its own bounds: the last wave never grows a grave, a level with a
     * single flag wave therefore grows none, the placement is the game's own
     * {@code Challenge::GraveDangerSpawnRandomGrave} (columns 4..8 of a ten-column board, only where a
     * grave stone can be added, i.e. neither the pool's water rows nor the left four columns), and the
     * flag-wave zombies are {@code Board::SpawnZombiesFromGraves} with its own rules - a War and Peas
     * level rises none at all, a pool level rises its own pool trio and a roof level its own sky trio
     * instead (which is why those two get their three zombies every flag wave once this is on).
     *
     * <p>It is also the switch the patched game keeps in a board-level byte: with it off, the branch
     * answers exactly what the game's own {@code mGameMode == GRAVE_DANGER} test answered.
     *
     * <p>Off by default: with no call every level behaves exactly as it always did.
     *
     * @param theEnabled true to run the grave branch on every level
     */
    public static void graveModeEnabled(boolean theEnabled) {
        synchronized (LOCK) {
            SET.put("graveModeEnabled", theEnabled);
        }
    }

    /** @return whether every level runs the grave branch; false by default (the game's own rule) */
    public static boolean graveModeEnabled() {
        synchronized (LOCK) {
            return Boolean.TRUE.equals(SET.get("graveModeEnabled"));
        }
    }

    /**
     * The mode {@code Challenge::SpawnZombieWave}'s grave branch compares: when the branch is meant to
     * run, the answer is the board's own mode - the comparison then passes whatever level this is -
     * and when it is not, the game's own constant goes back in and the comparison answers exactly what
     * {@code mGameMode == GAMEMODE_CHALLENGE_GRAVE_DANGER} always answered.
     *
     * <p><b>Internal.</b> Called by this module's Challenge Mixin at the comparison's constant, which is
     * the same immediate the patched binary rewrites (45 -> 22) while its injected hook enters the
     * branch body regardless.
     *
     * @param theRetailMode the mode the game's own comparison names
     * @param theCurrentMode the mode the board is in
     * @return the mode to compare against
     */
    public static int graveGuardMode(int theRetailMode, int theCurrentMode) {
        return graveModeEnabled() ? theCurrentMode : theRetailMode;
    }

    /**
     * The wave a non-flag wave's index has to be <em>above</em> for
     * {@code Challenge::SpawnZombieWave}'s grave branch to grow a grave stone, i.e. how late the grave
     * stones start.
     *
     * <p>The game's own value is {@link #DEFAULT_GRAVE_STONE_WAVE_THRESHOLD}: 5, which would start them
     * early in the first flag group. The flag waves of a level sit at the ends of ten-wave groups
     * ({@code 9}, {@code 19}, …), so a threshold of {@code 9} means "after the first flag wave" - the
     * grave stones of a 20-wave level grow on waves 10..18, nine of them.
     *
     * @param theWave the wave the index has to exceed; must not be negative
     */
    public static void graveStoneWaveThreshold(int theWave) {
        if (theWave < 0) {
            throw new IllegalArgumentException("A wave threshold cannot be negative: " + theWave);
        }
        synchronized (LOCK) {
            SET.put("graveStoneWaveThreshold", theWave);
        }
    }

    /**
     * The wave the grave branch's threshold is.
     *
     * <p><b>Internal.</b> Called by this module's Challenge Mixin at the threshold constant of
     * {@code Challenge::SpawnZombieWave} (the method's only {@code 5}).
     *
     * @return the registered threshold, or {@link #DEFAULT_GRAVE_STONE_WAVE_THRESHOLD}
     */
    public static int graveStoneWaveThreshold() {
        synchronized (LOCK) {
            Object aValue = SET.get("graveStoneWaveThreshold");
            return aValue == null ? DEFAULT_GRAVE_STONE_WAVE_THRESHOLD : (Integer) aValue;
        }
    }

    /** The threshold the game's own grave branch uses, i.e. {@code mCurrentWave > 5}. */
    public static final int DEFAULT_GRAVE_STONE_WAVE_THRESHOLD = 5;

    /**
     * How many zombies one whack-a-zombie wave rises at once, i.e. the base count
     * {@code Challenge::WhackAZombieSpawning} starts from before its own per-wave chances speak.
     *
     * <p>The game's own base is {@link #DEFAULT_WHACK_A_ZOMBIE_ZOMBIE_COUNT}, one zombie per spawn
     * (raised out of one grave stone); a final wave rises 20, a triple chance 3 and a double chance 2
     * whatever this is - the knob only moves the base, and the count is still clamped to the number of
     * grave stones the wave can pick from.
     *
     * @param theCount the base count; must be positive
     */
    public static void whackAZombieZombieCount(int theCount) {
        if (theCount <= 0) {
            throw new IllegalArgumentException("A zombie count must be positive: " + theCount);
        }
        synchronized (LOCK) {
            SET.put("whackAZombieZombieCount", theCount);
        }
    }

    /** @return the whack-a-zombie base count, the game's own when nothing was set */
    public static int whackAZombieZombieCount() {
        synchronized (LOCK) {
            Object aValue = SET.get("whackAZombieZombieCount");
            return aValue == null ? DEFAULT_WHACK_A_ZOMBIE_ZOMBIE_COUNT : (Integer) aValue;
        }
    }

    /**
     * The base one whack-a-zombie wave rises: the registered one where the game's own base is still in
     * place, and the game's own value where one of its per-wave arms has already replaced it.
     *
     * <p><b>Internal.</b> Called by this module's Challenge Mixin at the clamp
     * {@code Math.min(aZombieCount, availableGraves)} of {@code Challenge::WhackAZombieSpawning} - the
     * single place the method's count is read after its own final/double/triple arms have run, i.e. the
     * place the patched binary's {@code aZombieCount = 1} -> {@code 2} reaches the spawn loop.
     *
     * @param theGameCount the count the game's own code wants to rise
     * @return the count to rise
     */
    public static int whackAZombieZombieCountOverride(int theGameCount) {
        return theGameCount == DEFAULT_WHACK_A_ZOMBIE_ZOMBIE_COUNT ? whackAZombieZombieCount() : theGameCount;
    }

    /** The base count the game's own whack-a-zombie spawning uses, i.e. {@code aZombieCount = 1}. */
    public static final int DEFAULT_WHACK_A_ZOMBIE_ZOMBIE_COUNT = 1;

    /**
     * Replaces the background a game mode is played on: where {@code Board::InitLevel}'s
     * game-mode-to-background table answers one background for {@code theGameMode}, it answers
     * {@code theBackground} instead.
     *
     * <p>The table is what makes "a level is played on the day lawn / at night / in the pool / …"
     * true, and everything downstream follows the background the table answered: the plantable rows
     * (the pool's water rows, a roof's slope), the lawn's own rules and the art. Replacing one mode's
     * answer is therefore a level-wide change, not a paint. A mode nobody registered keeps the game's
     * own background.
     *
     * @param theGameMode   a {@code GameMode} value
     * @param theBackground a {@code BackgroundType} value
     */
    public static void modeBackground(int theGameMode, int theBackground) {
        if (theGameMode < 0) {
            throw new IllegalArgumentException("Not a game mode: " + theGameMode);
        }
        if (theBackground < 0) {
            throw new IllegalArgumentException("Not a background: " + theBackground);
        }
        synchronized (LOCK) {
            MODE_BACKGROUNDS.put(theGameMode, theBackground);
            SET.put("modeBackground[" + theGameMode + ']', theBackground);
        }
    }

    /**
     * The background a game mode is played on.
     *
     * <p><b>Internal.</b> Called by this module's Board Mixin at the end of {@code Board::InitLevel}'s
     * game-mode-to-background table, right where the method reads the answer (before it derives the
     * plantable rows from it).
     *
     * @param theGameMode         the mode the level is being initialised for
     * @param theGameBackground   the background the game's own table answered
     * @return the background to play on
     */
    public static int modeBackgroundOverride(int theGameMode, int theGameBackground) {
        synchronized (LOCK) {
            Integer aBackground = MODE_BACKGROUNDS.get(theGameMode);
            return aBackground == null ? theGameBackground : aBackground;
        }
    }

    /**
     * @return every replaced mode background, game mode -> background; for diagnostics, a read-only
     *     snapshot
     */
    public static Map<Integer, Integer> modeBackgrounds() {
        synchronized (LOCK) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(MODE_BACKGROUNDS));
        }
    }

    // -------------------------------------------------------------------------------------------
    // diagnostics and reset
    // -------------------------------------------------------------------------------------------

    /**
     * @return every knob a mod set, keyed by name, in the order the names were first set; for
     *     diagnostics, the map is a snapshot and cannot be written through
     */
    public static Map<String, Object> registered() {
        synchronized (LOCK) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(SET));
        }
    }

    /**
     * Clears every knob and puts the game's own data back: the cells this module overwrote in
     * {@code Challenge.gZombieAllowedLevels} are restored to their retail values, everything else
     * simply stops being overridden. Used by tests and by a loader that reloads mods in-process.
     */
    public static void reset() {
        synchronized (LOCK) {
            for (Map.Entry<Integer, Map<Integer, Integer>> aType : LEVEL_ALLOWED_RESTORE.entrySet()) {
                int[] aRow = Challenge.gZombieAllowedLevels[aType.getKey()].mAllowedOnLevel;
                for (Map.Entry<Integer, Integer> aCell : aType.getValue().entrySet()) {
                    aRow[aCell.getKey() - 1] = aCell.getValue();
                }
            }

            LEVEL_ALLOWED_RESTORE.clear();
            LEVEL_ALLOWED.clear();
            ZOMBIES_ALLOWED.clear();
            SEEDS_NOT_ALLOWED.clear();
            CONVEYOR_SEEDS.clear();
            WAVE_COUNTS.clear();
            MODE_BACKGROUNDS.clear();
            SET.clear();
        }
    }

    // -------------------------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------------------------

    private static void checkZombieType(int theZombieType) {
        if (theZombieType < 0 || theZombieType >= ZombieType.MAX_ZOMBIE_TYPES) {
            throw new IllegalArgumentException("Not a zombie type: " + theZombieType);
        }
    }

    private static int[] merge(int[] theCurrent, int[] theAdded) {
        if (theCurrent == null || theCurrent.length == 0) {
            return theAdded.clone();
        }

        int[] aMerged = theCurrent.clone();
        for (int aAdded : theAdded) {
            boolean aKnown = false;
            for (int aCurrent : aMerged) {
                if (aCurrent == aAdded) {
                    aKnown = true;
                    break;
                }
            }
            if (!aKnown) {
                int[] aBigger = new int[aMerged.length + 1];
                System.arraycopy(aMerged, 0, aBigger, 0, aMerged.length);
                aBigger[aMerged.length] = aAdded;
                aMerged = aBigger;
            }
        }
        return aMerged;
    }

    private static String joined(int[] theValues) {
        StringBuilder aText = new StringBuilder();
        for (int aValue : theValues) {
            if (aText.length() > 0) {
                aText.append(',');
            }
            aText.append(aValue);
        }
        return aText.toString();
    }

    private static String describe(Map<Integer, Integer> theSeeds) {
        StringBuilder aText = new StringBuilder();
        for (Map.Entry<Integer, Integer> aEntry : theSeeds.entrySet()) {
            if (aText.length() > 0) {
                aText.append(',');
            }
            aText.append(aEntry.getKey()).append("->").append(aEntry.getValue());
        }
        return aText.toString();
    }
}
