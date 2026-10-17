package cn.enaium.fabricpvz.api.plant;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import cn.enaium.fabricpvz.portable.lawn.Plant;
import cn.enaium.fabricpvz.portable.lawn.SeedType;

/**
 * Overrides the numbers a plant type is defined and born with - for the retail types as well, which
 * is what a rebalance of the shipped game ("95 版" and the like) needs, and not only for the types
 * {@link PlantType#builder(String)} registers.
 *
 * <pre>{@code
 * PlantOverrides.of(SeedType.SEED_MARIGOLD).cost(75).refreshTime(1500).launchRate(3300);
 * PlantOverrides.of(SeedType.SEED_SPIKEROCK).bodyHealth(16200);
 * }</pre>
 *
 * <p>Every seed type has at most one override, handed out by {@link #of(int)}, which is idempotent:
 * asking twice gives the same handle, and the last value written to a field is the one applied.
 * Overrides are looked up by seed type, so a {@link PlantRegistry} plant can be overridden too; where
 * a registration and an override set the same field, the override wins (the registration is the
 * type's base, the override the mod's final word - the same rule {@code api-zombie}'s
 * {@code ZombieOverrides} follows).
 *
 * <p>The game knows nothing about this. The definition fields are ordinary writes into
 * {@code Plant.gPlantDefs}, the table the game reads them from; the spawn health and the spawn
 * countdown are applied by this module's
 * {@link cn.enaium.fabricpvz.api.plant.mixin.PlantOverridesMixin} at the return of
 * {@code Plant::PlantInitialize} (Plant.cpp:111), after the game's own per-seed setup - so a seed type
 * nobody overrode runs exactly the game's own code. The remaining fields - a chomper's bite type and
 * digesting pause, a spikerock's hit damage and crack levels, a potato mine's blast and a marigold's
 * coins - are values the game reads while the plant is alive, and the same Mixin hands them to the
 * game at those read points (the comparison, the counter, the blast's arguments, the coin the plant
 * drops), each answering the game's own number when nobody overrode the plant.
 *
 * <p>Next to the per-seed handles this class also carries the handful of <b>whole-game rules</b> a
 * rebalance needs and no single seed type can own: {@link #notOnGroundState(int)}, which replaces the
 * game's own "a plant being carried up by a bungee cannot be touched" test, and
 * {@link #extraFireSeedType(int)}, which moves the mid-reload extra shot {@code Plant::UpdateShooter}
 * fires at launch counter 50. Both are answered by this module's Mixins, both default to the game's
 * own value, and {@link #rules()} lists what a mod actually set.
 */
public final class PlantOverrides {

    private static final Object LOCK = new Object();
    private static final Map<Integer, PlantOverride> BY_SEED_TYPE = new LinkedHashMap<>();

    /**
     * The whole-game rules below, keyed by name, in the order they were first set - the diagnostics
     * view of what a mod changed (the mirror of {@link #registered()}, which lists the per-seed
     * handles).
     */
    private static final Map<String, Object> RULES = new LinkedHashMap<>();

    /** The plant state that replaces "rising with a bungee" in {@code Plant::NotOnGround}, or null. */
    private static Integer sNotOnGroundState;

    /** The seed type {@code Plant::UpdateShooter} fires its {@code mLaunchCounter == 50} shot for. */
    private static Integer sExtraFireSeedType;

    private PlantOverrides() {
    }

    /**
     * Returns the override of a seed type, creating it on first use. Retail seed types are as valid as
     * registered ones.
     *
     * @param theSeedType a {@code SeedType} value
     * @return the seed type's override, to be filled in with the {@link PlantOverride} methods
     * @throws IllegalArgumentException if the value is not a seed type
     */
    public static PlantOverride of(int theSeedType) {
        if (theSeedType < 0 || theSeedType >= SeedType.MAX_SEED_TYPES) {
            throw new IllegalArgumentException("Not a seed type: " + theSeedType);
        }
        synchronized (LOCK) {
            return BY_SEED_TYPE.computeIfAbsent(theSeedType, PlantOverride::new);
        }
    }

    /**
     * @return every override a mod registered, keyed by seed type, in the order the types were first
     *     asked for; for diagnostics, the map is a snapshot and cannot be written through
     */
    public static Map<Integer, PlantOverride> registered() {
        synchronized (LOCK) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(BY_SEED_TYPE));
        }
    }

    /**
     * Clears every override and puts the definition values back to what the game had; used by tests
     * and by a loader that reloads mods in-process. Plants already on a board keep the health they
     * were born with, and the seed types nobody overrode are untouched. The whole-game rules
     * ({@link #notOnGroundState(int)}, {@link #extraFireSeedType(int)}) are cleared with them.
     */
    public static void reset() {
        synchronized (LOCK) {
            for (PlantOverride aOverride : BY_SEED_TYPE.values()) {
                aOverride.restore();
            }
            BY_SEED_TYPE.clear();
            RULES.clear();
            sNotOnGroundState = null;
            sExtraFireSeedType = null;
        }
    }

    // ------------------------------------------------------------------------------------------
    // Whole-game rules: what a plant counts as, and when the shooter fires - not per seed type.
    // ------------------------------------------------------------------------------------------

    /**
     * The plant state that makes a plant count as <em>not on the ground</em>, replacing the game's own
     * "rising with a bungee" test.
     *
     * <p>{@code Plant::NotOnGround} is the predicate the zombie side asks before it lets a plant be
     * eaten, vaulted or squashed: the game refuses a plant that is {@code mSquished}, dead, or being
     * carried up by a bungee ({@code mOnBungeeState == RISING_WITH_BUNGEE}). A rebalance that wants a
     * plant's own {@code mState} to decide that - the 95 Edition makes a hiding scary-shroom
     * untouchable - replaces the bungee clause with this one: a plant whose {@code mState} is
     * {@code thePlantState} counts as not on the ground, and the bungee clause no longer applies to
     * any plant.
     *
     * <p>Registered with {@link cn.enaium.fabricpvz.api.plant.mixin.PlantMixin}'s field redirect, so it
     * answers {@code false} (the game's own test) until a mod sets it.
     *
     * @param thePlantState a {@code PlantState} value, e.g. {@code STATE_SCAREDYSHROOM_SCARED}
     */
    public static void notOnGroundState(int thePlantState) {
        synchronized (LOCK) {
            sNotOnGroundState = thePlantState;
            RULES.put("notOnGroundState", thePlantState);
        }
    }

    /** @return the state {@link #notOnGroundState(int)} registered, or {@code null} when nobody did. */
    public static Integer notOnGroundState() {
        synchronized (LOCK) {
            return sNotOnGroundState;
        }
    }

    /**
     * The seed type {@code Plant::UpdateShooter} fires its <em>extra</em> shot for: the shot it makes
     * when the plant's launch counter reaches exactly 50, half way to the next reload.
     *
     * <p>The game fires it for the cattail only ({@code mSeedType == SEED_CATTAIL}); this replaces that
     * seed type, which moves the extra shot to another plant and takes it away from the cattail - the
     * 95 Edition gives it to the gatling pea, whose own per-burst chain (four peas, at shooting
     * counters 68/51/35/18) is untouched by the move.
     *
     * @param theSeedType a {@code SeedType} value, e.g. {@code SEED_GATLINGPEA}
     */
    public static void extraFireSeedType(int theSeedType) {
        if (theSeedType < 0 || theSeedType >= SeedType.MAX_SEED_TYPES) {
            throw new IllegalArgumentException("Not a seed type: " + theSeedType);
        }
        synchronized (LOCK) {
            sExtraFireSeedType = theSeedType;
            RULES.put("extraFireSeedType", theSeedType);
        }
    }

    /**
     * @return the seed type {@link #extraFireSeedType(int)} registered, or the game's own
     *     ({@link PlantOverride#DEFAULT_EXTRA_FIRE_SEED_TYPE}) when nobody did
     */
    public static int extraFireSeedType() {
        synchronized (LOCK) {
            return sExtraFireSeedType == null ? PlantOverride.DEFAULT_EXTRA_FIRE_SEED_TYPE : sExtraFireSeedType;
        }
    }

    /**
     * @return the whole-game rules a mod registered, keyed by name, in the order they were first set;
     *     for diagnostics, the map is a snapshot and cannot be written through
     */
    public static Map<String, Object> rules() {
        synchronized (LOCK) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(RULES));
        }
    }

    /**
     * Whether a plant of this seed type is crush-proof: a gargantuar smashing it, and a vehicle driving
     * over its square, bounce off it instead of squashing it - {@link PlantOverride#crushProof(boolean)}.
     *
     * <p><b>Internal.</b> Called by this module's Mixins at the two {@code SEED_SPIKEROCK} comparisons
     * of {@code Zombie::UpdateZombieGargantuar} and {@code Zombie::SquishAllInSquare}, which is why the
     * game's own exemption is part of the answer: a seed type nobody overrode is crush-proof exactly
     * when the game's own comparison says so.
     *
     * @param theSeedType a {@code SeedType} value
     * @return whether the plant survives being run over or smashed
     */
    public static boolean isCrushProof(int theSeedType) {
        PlantOverride aOverride = get(theSeedType);
        if (aOverride != null && aOverride.crushProof() != null) {
            return aOverride.crushProof();
        }
        return theSeedType == PlantOverride.DEFAULT_CRUSH_PROOF_SEED_TYPE;
    }

    /**
     * The override of a seed type, or {@code null} when nobody registered one.
     *
     * <p><b>Internal.</b> The read side of the table {@link #of(int)} writes; there is no lock held
     * over the returned handle, which is immutable once a mod has finished its entry point.
     *
     * @param theSeedType a {@code SeedType} value
     * @return the seed type's override, or {@code null}
     */
    public static PlantOverride get(int theSeedType) {
        synchronized (LOCK) {
            return BY_SEED_TYPE.get(theSeedType);
        }
    }

    /**
     * Applies a seed type's overrides to a plant the game has just built.
     *
     * <p><b>Internal.</b> Called by this module's Plant Mixin at the return of
     * {@code Plant::PlantInitialize}, where the game's own per-seed setup has already written
     * {@code mPlantHealth} and its maximum; a seed type nobody overrode is left exactly as the game
     * set it. The plant's type is read from {@code mSeedType} - the field every later read uses (an
     * Imitater copy carries its own seed type and its morph plants a fresh plant of the imitated type,
     * which initialises and inherits the override like any other).
     *
     * @param thePlant the plant that finished initialising
     */
    public static void apply(Plant thePlant) {
        PlantOverride aOverride = get(thePlant.mSeedType);
        if (aOverride == null) {
            return;
        }

        if (aOverride.bodyHealth() != null) {
            thePlant.mPlantHealth = aOverride.bodyHealth();
            thePlant.mPlantMaxHealth = aOverride.bodyHealth();
        }
        if (aOverride.spawnCountdown() != null) {
            thePlant.mStateCountdown = aOverride.spawnCountdown();
        }
    }

    /**
     * The zombie type this seed type's chomper bites rather than eats, the game's own when nobody
     * overrode the plant.
     *
     * <p><b>Internal.</b> Called by this module's Plant Mixin where {@code Plant::UpdateChomper} tests
     * the zombie in front of the chomper against its third type.
     *
     * @param theSeedType a {@code SeedType} value
     * @return the zombie type the game compares the target against
     */
    public static int biteInsteadOfEatZombieType(int theSeedType) {
        PlantOverride aOverride = get(theSeedType);
        return aOverride == null ? PlantOverride.DEFAULT_BITE_INSTEAD_OF_EAT_ZOMBIE_TYPE
                : aOverride.biteInsteadOfEatZombieType();
    }

    /**
     * The pause this seed type's chomper takes to digest, the game's own when nobody overrode the
     * plant.
     *
     * <p><b>Internal.</b> Called by this module's Plant Mixin at the {@code mStateCountdown} the
     * {@code STATE_CHOMPER_DIGESTING} transition of {@code Plant::UpdateChomper} writes.
     *
     * @param theSeedType a {@code SeedType} value
     * @return the digesting pause in centiseconds
     */
    public static int digestCountdown(int theSeedType) {
        PlantOverride aOverride = get(theSeedType);
        return aOverride == null ? PlantOverride.DEFAULT_CHOMPER_DIGEST_COUNTDOWN : aOverride.digestCountdown();
    }

    /**
     * What one hit takes off this seed type's spikerock, the game's own when nobody overrode the plant.
     *
     * <p><b>Internal.</b> Called by this module's Plant Mixin inside {@code Plant::SpikeRockTakeDamage}.
     *
     * @param theSeedType a {@code SeedType} value
     * @return the hit points one hit takes off
     */
    public static int spikeRockHitDamage(int theSeedType) {
        PlantOverride aOverride = get(theSeedType);
        return aOverride == null ? PlantOverride.DEFAULT_SPIKEROCK_HIT_DAMAGE : aOverride.spikeRockHitDamage();
    }

    /**
     * The health at which this seed type's spikerock hides its {@code bigspike3} track, the game's own
     * when nobody overrode the plant.
     *
     * <p><b>Internal.</b> See {@link #spikeRockHitDamage(int)}.
     *
     * @param theSeedType a {@code SeedType} value
     * @return the first crack's health
     */
    public static int spikeRockHideBigSpike3At(int theSeedType) {
        PlantOverride aOverride = get(theSeedType);
        return aOverride == null ? PlantOverride.DEFAULT_SPIKEROCK_HIDE_BIGSPIKE3_AT
                : aOverride.spikeRockHideBigSpike3At();
    }

    /**
     * The health at which this seed type's spikerock hides its {@code bigspike2} track, the game's own
     * when nobody overrode the plant.
     *
     * <p><b>Internal.</b> See {@link #spikeRockHitDamage(int)}.
     *
     * @param theSeedType a {@code SeedType} value
     * @return the second crack's health
     */
    public static int spikeRockHideBigSpike2At(int theSeedType) {
        PlantOverride aOverride = get(theSeedType);
        return aOverride == null ? PlantOverride.DEFAULT_SPIKEROCK_HIDE_BIGSPIKE2_AT
                : aOverride.spikeRockHideBigSpike2At();
    }

    /**
     * The radius of this seed type's blast, the game's own when nobody overrode the plant.
     *
     * <p><b>Internal.</b> Called by this module's Plant Mixin on the {@code Board::KillAllZombiesInRadius}
     * call of {@code Plant::DoSpecial}'s potato-mine branch.
     *
     * @param theSeedType a {@code SeedType} value
     * @return the blast radius in pixels
     */
    public static int explosionRadius(int theSeedType) {
        PlantOverride aOverride = get(theSeedType);
        return aOverride == null ? PlantOverride.DEFAULT_EXPLOSION_RADIUS : aOverride.explosionRadius();
    }

    /**
     * How many rows either side of the plant this seed type's blast reaches, the game's own when nobody
     * overrode the plant.
     *
     * <p><b>Internal.</b> See {@link #explosionRadius(int)}.
     *
     * @param theSeedType a {@code SeedType} value
     * @return the blast's row range
     */
    public static int explosionRowRange(int theSeedType) {
        PlantOverride aOverride = get(theSeedType);
        return aOverride == null ? PlantOverride.DEFAULT_EXPLOSION_ROW_RANGE : aOverride.explosionRowRange();
    }

    /**
     * Whether this seed type's blast burns what it hits, the game's own when nobody overrode the plant.
     *
     * <p><b>Internal.</b> See {@link #explosionRadius(int)}.
     *
     * @param theSeedType a {@code SeedType} value
     * @return true for the game's own burn path
     */
    public static boolean explosionBurns(int theSeedType) {
        PlantOverride aOverride = get(theSeedType);
        return aOverride != null && aOverride.explosionBurns();
    }

    /**
     * The coin a coin-producing plant of this seed type really drops, given the coin the game's own
     * branch chose.
     *
     * <p><b>Internal.</b> Called by this module's Plant Mixin on every {@code Board::AddCoin} of
     * {@code Plant::UpdateProductionPlant}: the game's own rare arm (the gold coin) answers the
     * override's rare coin and its common arm (the silver coin) the override's common one, so the
     * coin a Big Time level pays on top - which the game also drops as a silver coin - answers the
     * common coin as well. A seed type nobody overrode, and a coin type that is neither arm, answer
     * the game's own coin unchanged.
     *
     * @param theSeedType a {@code SeedType} value
     * @param theCoinType the {@code CoinType} the game's own branch chose
     * @return the coin the plant drops
     */
    public static int producedCoin(int theSeedType, int theCoinType) {
        PlantOverride aOverride = get(theSeedType);
        if (aOverride == null) {
            return theCoinType;
        }
        if (theCoinType == PlantOverride.DEFAULT_PRODUCED_RARE_COIN_TYPE) {
            return aOverride.producedRareCoinType();
        }
        if (theCoinType == PlantOverride.DEFAULT_PRODUCED_COMMON_COIN_TYPE) {
            return aOverride.producedCommonCoinType();
        }
        return theCoinType;
    }

    /**
     * How often, out of 100, this seed type's plant drops its rare coin, the game's own when nobody
     * overrode the plant.
     *
     * <p><b>Internal.</b> Called by this module's Plant Mixin at the {@code Common.rand(100) < 10} of
     * {@code Plant::UpdateProductionPlant}'s marigold branch.
     *
     * @param theSeedType a {@code SeedType} value
     * @return the rare coin's share out of 100
     */
    public static int producedCoinRarePercent(int theSeedType) {
        PlantOverride aOverride = get(theSeedType);
        return aOverride == null ? PlantOverride.DEFAULT_PRODUCED_COIN_RARE_PERCENT
                : aOverride.producedCoinRarePercent();
    }
}
