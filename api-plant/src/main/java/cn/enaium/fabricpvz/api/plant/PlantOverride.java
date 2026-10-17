package cn.enaium.fabricpvz.api.plant;

import cn.enaium.fabricpvz.portable.lawn.CoinType;
import cn.enaium.fabricpvz.portable.lawn.Plant;
import cn.enaium.fabricpvz.portable.lawn.PlantDefinition;
import cn.enaium.fabricpvz.portable.lawn.SeedType;
import cn.enaium.fabricpvz.portable.lawn.ZombieType;

/**
 * One plant's overrides, handed out by {@link PlantOverrides#of(int)}.
 *
 * <p>The fields fall into two groups, and they reach the game at two different times:
 *
 * <ul>
 *   <li>the <b>definition</b> fields - {@link #cost(int)}, {@link #refreshTime(int)},
 *       {@link #launchRate(int)} - live in the plant's {@link PlantDefinition} row of
 *       {@code Plant.gPlantDefs}, which is the table the game reads the seed-packet price, the
 *       recharge and the shooting cadence from. They are therefore written <b>immediately</b>, by the
 *       setter rather than at some later application point, and that fixes when a mod may call them:
 *       before the game reads the table, i.e. from the mod entry point the loader runs ahead of the
 *       game's own main class (the same contract {@link PlantRegistry#register} writes its new rows
 *       under). A value written after the fact is not wrong, only late - the game reads the fields
 *       again every time it uses them (each seed packet, each shot).</li>
 *   <li>the <b>spawn</b> fields - {@link #bodyHealth(int)} - are values {@code Plant::PlantInitialize}
 *       sets per seed type (its {@code switch} assigns {@code mPlantHealth}, and the method's last
 *       lines copy it into {@code mPlantMaxHealth}). They are recorded here and applied by this
 *       module's Mixin through {@link PlantOverrides#apply(Plant)} at the end of the initialisation,
 *       so the game's own per-seed setup runs first and an override is the mod's final word.</li>
 *   <li>the <b>combat</b> fields - {@link #crushProof(boolean)} - are read where the game decides
 *       whether a plant survives being run over or smashed, and are answered by this module's Mixins
 *       at those reads (so, like the definition fields, they take effect the moment they are set).</li>
 * </ul>
 *
 * <pre>{@code
 * PlantOverrides.of(SeedType.SEED_MARIGOLD).cost(75).refreshTime(1500).launchRate(3300);
 * PlantOverrides.of(SeedType.SEED_SPIKEROCK).bodyHealth(16200);
 * }</pre>
 *
 * <p>The read accessors exist for diagnostics - a launcher or a test can print what a mod registered -
 * and answer {@code null} for a field nobody set.
 */
public final class PlantOverride {

    /** The pause the game's own {@code Plant::UpdateChomper} gives a chomper, in 10ms ticks. */
    public static final int DEFAULT_CHOMPER_DIGEST_COUNTDOWN = 4000;
    /** The zombie type whose arm of the chomper's bite test the game ships ({@code ZOMBIE_BOSS}). */
    public static final int DEFAULT_BITE_INSTEAD_OF_EAT_ZOMBIE_TYPE = ZombieType.ZOMBIE_BOSS;
    /** The damage the game's own {@code Plant::SpikeRockTakeDamage} takes off a spikerock. */
    public static final int DEFAULT_SPIKEROCK_HIT_DAMAGE = 50;
    /** The health at which the game's own spikerock hides its {@code bigspike3} track. */
    public static final int DEFAULT_SPIKEROCK_HIDE_BIGSPIKE3_AT = 300;
    /** The health at which the game's own spikerock hides its {@code bigspike2} track. */
    public static final int DEFAULT_SPIKEROCK_HIDE_BIGSPIKE2_AT = 150;
    /** The radius of the game's own potato-mine blast. */
    public static final int DEFAULT_EXPLOSION_RADIUS = 60;
    /** The row range of the game's own potato-mine blast (0 = its own row only). */
    public static final int DEFAULT_EXPLOSION_ROW_RANGE = 0;
    /** The common coin the game's own marigold drops ({@code COIN_SILVER}). */
    public static final int DEFAULT_PRODUCED_COMMON_COIN_TYPE = CoinType.COIN_SILVER;
    /** The rare coin the game's own marigold drops ({@code COIN_GOLD}). */
    public static final int DEFAULT_PRODUCED_RARE_COIN_TYPE = CoinType.COIN_GOLD;
    /** How often, out of 100, the game's own marigold drops its rare coin. */
    public static final int DEFAULT_PRODUCED_COIN_RARE_PERCENT = 10;
    /**
     * The one seed type the game's own crush test treats specially ({@code SEED_SPIKEROCK}): the
     * spikerock a gargantuar bounce off and a vehicle drives over without squashing it.
     */
    public static final int DEFAULT_CRUSH_PROOF_SEED_TYPE = SeedType.SEED_SPIKEROCK;
    /** The seed type the game's own {@code Plant::UpdateShooter} fires the extra shot for. */
    public static final int DEFAULT_EXTRA_FIRE_SEED_TYPE = SeedType.SEED_CATTAIL;

    private final int mSeedType;
    private Integer mCost;
    private Integer mRefreshTime;
    private Integer mLaunchRate;
    private Integer mBodyHealth;
    private Integer mSpawnCountdown;
    private Integer mBiteInsteadOfEatZombieType;
    private Integer mDigestCountdown;
    private Integer mSpikeRockHitDamage;
    private Integer mSpikeRockHideBigSpike3At;
    private Integer mSpikeRockHideBigSpike2At;
    private Integer mExplosionRadius;
    private Integer mExplosionRowRange;
    private Boolean mExplosionBurns;
    private Integer mProducedCommonCoinType;
    private Integer mProducedRareCoinType;
    private Integer mProducedRarePercent;
    private Boolean mCrushProof;
    private Integer mCostBefore;
    private Integer mRefreshTimeBefore;
    private Integer mLaunchRateBefore;

    /** An override handle; only {@link PlantOverrides#of(int)} creates one. */
    PlantOverride(int theSeedType) {
        mSeedType = theSeedType;
    }

    /**
     * Sun cost of the seed packet, i.e. {@code mSeedCost} of the plant's {@link PlantDefinition} row
     * (the price {@code Plant::GetCost} and the seed packet draw).
     *
     * <p>Written into the table right away, so call it from a mod entry point; see the class javadoc
     * for that ordering contract.
     *
     * @param theSunCost the price in sun, {@code 0} or more ({@code 0} is a price, not "unset")
     */
    public PlantOverride cost(int theSunCost) {
        requireNonNegative(theSunCost, "sun cost");
        PlantDefinition aDefinition = definitionRow();
        if (mCost == null) {
            mCostBefore = aDefinition.mSeedCost;
        }
        mCost = theSunCost;
        aDefinition.mSeedCost = theSunCost;
        return this;
    }

    /**
     * Seed-packet recharge, i.e. {@code mRefreshTime} of the definition row, in centiseconds
     * ("10ms", the unit the retail table uses: the 95 Edition's tall nut is 2000 = 20s).
     *
     * @param theCentiseconds the recharge, {@code 0} or more
     */
    public PlantOverride refreshTime(int theCentiseconds) {
        requireNonNegative(theCentiseconds, "recharge time");
        PlantDefinition aDefinition = definitionRow();
        if (mRefreshTime == null) {
            mRefreshTimeBefore = aDefinition.mRefreshTime;
        }
        mRefreshTime = theCentiseconds;
        aDefinition.mRefreshTime = theCentiseconds;
        return this;
    }

    /**
     * Shooting cadence, i.e. {@code mLaunchRate} of the definition row, in ticks of the plant's
     * launch counter ({@code Plant::UpdateShooter} reloads it with this value).
     *
     * @param theTicks the cadence in ticks, {@code 0} or more
     */
    public PlantOverride launchRate(int theTicks) {
        requireNonNegative(theTicks, "launch rate");
        PlantDefinition aDefinition = definitionRow();
        if (mLaunchRate == null) {
            mLaunchRateBefore = aDefinition.mLaunchRate;
        }
        mLaunchRate = theTicks;
        aDefinition.mLaunchRate = theTicks;
        return this;
    }

    /**
     * Health the plant is born with: {@code Plant#mPlantHealth} and its maximum
     * {@code Plant#mPlantMaxHealth}, the pair {@code Plant::PlantInitialize} sets per seed type (and
     * whose maximum it always derives from the health, so both are written - unlike a zombie, a plant
     * has no second value the game deliberately keeps apart).
     *
     * <p>Applied at the end of the plant's initialisation, on top of the game's own per-seed numbers.
     *
     * @param theHealth the hit points; {@code 0} or less means "leave the game's own value", the same
     *     as never calling this method
     */
    public PlantOverride bodyHealth(int theHealth) {
        mBodyHealth = theHealth > 0 ? theHealth : null;
        return this;
    }

    /**
     * The state counter the plant is born with: {@code Plant::mStateCountdown} as
     * {@code Plant::PlantInitialize}'s per-seed branch leaves it (the potato mine's arming countdown,
     * the sun-shroom's growth countdown), in centiseconds ("10ms", the unit the game's own counters
     * use).
     *
     * <p>Applied at the end of the plant's initialisation, on top of the game's own per-seed numbers.
     * The game writes this counter once, for every branch of its per-seed {@code switch}, so the
     * override replaces the value whichever branch ran.
     *
     * @param theCentiseconds the counter, {@code 0} or more
     */
    public PlantOverride spawnCountdown(int theCentiseconds) {
        requireNonNegative(theCentiseconds, "spawn countdown");
        mSpawnCountdown = theCentiseconds;
        return this;
    }

    /**
     * The zombie type a chomper <em>bites</em> (40 damage, then it is ready again) instead of eating.
     *
     * <p>{@code Plant::UpdateChomper} tests the zombie in front of it against three types - the
     * gargantuar, the red-eye gargantuar and one more - and this replaces that third one, so the set of
     * zombies a chomper cannot swallow becomes {@code {gargantuar, red-eye gargantuar, theZombieType}}.
     *
     * @param theZombieType a {@code ZombieType} value
     */
    public PlantOverride biteInsteadOfEatZombieType(int theZombieType) {
        if (theZombieType < 0 || theZombieType >= ZombieType.MAX_ZOMBIE_TYPES) {
            throw new IllegalArgumentException("Not a zombie type: " + theZombieType);
        }
        mBiteInsteadOfEatZombieType = theZombieType;
        return this;
    }

    /**
     * The pause a chomper takes to digest what it swallowed: the {@code mStateCountdown} of
     * {@code STATE_CHOMPER_DIGESTING}, in centiseconds.
     *
     * @param theCentiseconds the pause, {@code 0} or more
     */
    public PlantOverride digestCountdown(int theCentiseconds) {
        requireNonNegative(theCentiseconds, "digest countdown");
        mDigestCountdown = theCentiseconds;
        return this;
    }

    /**
     * What one hit takes off a spikerock and where its spikes crack:
     * {@code Plant::SpikeRockTakeDamage} subtracts the damage and hides the {@code bigspike3} track
     * once the health is at or below the first level and the {@code bigspike2} track at or below the
     * second (and dies at 0 or less). Give all three together - they only mean anything relative to
     * each other and to the plant's {@link #bodyHealth(int)}.
     *
     * @param theDamage            the hit points one hit takes off; must be positive
     * @param theHideBigSpike3At   the health at which the first spike art disappears
     * @param theHideBigSpike2At   the health at which the second does; not above the first
     */
    public PlantOverride spikeRockDamage(int theDamage, int theHideBigSpike3At, int theHideBigSpike2At) {
        if (theDamage <= 0) {
            throw new IllegalArgumentException("A hit damage must be positive: " + theDamage);
        }
        if (theHideBigSpike2At > theHideBigSpike3At) {
            throw new IllegalArgumentException("The second crack cannot come before the first: "
                    + theHideBigSpike3At + " then " + theHideBigSpike2At);
        }
        mSpikeRockHitDamage = theDamage;
        mSpikeRockHideBigSpike3At = theHideBigSpike3At;
        mSpikeRockHideBigSpike2At = theHideBigSpike2At;
        return this;
    }

    /**
     * The three arguments of the blast this plant sets off in {@code Plant::DoSpecial} - the potato
     * mine's {@code Board::KillAllZombiesInRadius(row, x, y, radius, rowRange, burn, flags)}.
     *
     * <p>The position stays the plant's own (the potato mine aims at its cell's centre, 20 pixels
     * left of the middle of the sprite); what is set here is how far the blast reaches, how many rows
     * each way, and whether it burns what it hits.
     *
     * @param theRadius   the radius in pixels; must be positive
     * @param theRowRange how many rows either side of the plant the blast reaches; not negative
     * @param theBurn     true to burn (the game's own ash effect) rather than merely damage
     */
    public PlantOverride explosionBlast(int theRadius, int theRowRange, boolean theBurn) {
        if (theRadius <= 0) {
            throw new IllegalArgumentException("A blast radius must be positive: " + theRadius);
        }
        if (theRowRange < 0) {
            throw new IllegalArgumentException("A blast cannot reach a negative number of rows: " + theRowRange);
        }
        mExplosionRadius = theRadius;
        mExplosionRowRange = theRowRange;
        mExplosionBurns = theBurn;
        return this;
    }

    /**
     * The coins a coin-producing plant drops: the common one by default and the rare one instead on
     * the game's own rare roll, which is a draw out of 100 that this also sets.
     *
     * <p>{@code Plant::UpdateProductionPlant}'s marigold branch draws
     * {@code Common.rand(100) < 10} and drops the rare coin (the gold one) when it hits, the common
     * one (the silver one) otherwise; the coin a Big Time level pays a marigold on top goes through
     * the common coin as well. So this one call moves all three of those numbers.
     *
     * @param theCommonCoinType the coin dropped by default, a {@code CoinType} value
     * @param theRareCoinType   the coin the rare roll drops, a {@code CoinType} value
     * @param theRarePercent    how often, out of 100, the rare coin replaces the common one
     */
    public PlantOverride producedCoins(int theCommonCoinType, int theRareCoinType, int theRarePercent) {
        if (theCommonCoinType < 0 || theRareCoinType < 0) {
            throw new IllegalArgumentException("Not a coin type: " + theCommonCoinType + " / " + theRareCoinType);
        }
        if (theRarePercent < 0 || theRarePercent > 100) {
            throw new IllegalArgumentException("A share out of 100 is not a percentage: " + theRarePercent);
        }
        mProducedCommonCoinType = theCommonCoinType;
        mProducedRareCoinType = theRareCoinType;
        mProducedRarePercent = theRarePercent;
        return this;
    }

    /**
     * Whether this plant is crush-proof: a gargantuar smashing it and a vehicle (the zamboni, the
     * catapult) driving over it bounce off it instead of squashing it - the plant hurts the zombie the
     * way the spikerock does, and survives the square-squash that kills every other plant.
     *
     * <p>{@code Zombie::UpdateZombieGargantuar} and {@code Zombie::SquishAllInSquare} both single out
     * one seed type ({@code SEED_SPIKEROCK}) for that treatment; this replaces the seed type those two
     * comparisons answer to. Registering it for a plant the game already treats that way (the
     * spikerock) leaves the game's own behaviour in place.
     *
     * @param theCrushProof true to make the plant crush-proof, false to take the exemption away from a
     *     plant the game would have treated that way
     */
    public PlantOverride crushProof(boolean theCrushProof) {
        mCrushProof = theCrushProof;
        return this;
    }

    /** @return the seed type this override belongs to. */
    public int seedType() {
        return mSeedType;
    }

    /** @return the overridden sun cost, or {@code null} when nobody set one. */
    public Integer cost() {
        return mCost;
    }

    /** @return the overridden recharge, or {@code null} when nobody set one. */
    public Integer refreshTime() {
        return mRefreshTime;
    }

    /** @return the overridden launch rate, or {@code null} when nobody set one. */
    public Integer launchRate() {
        return mLaunchRate;
    }

    /** @return the overridden spawn health, or {@code null} when nobody set one. */
    public Integer bodyHealth() {
        return mBodyHealth;
    }

    /**
     * @return the countdown the plant is born with, or {@code null} when the game's own per-seed
     *     initialisation decides (the read side of {@link #spawnCountdown(int)})
     */
    public Integer spawnCountdown() {
        return mSpawnCountdown;
    }

    /** @return the zombie type the chomper bites rather than eats, the game's own when nobody set one. */
    public int biteInsteadOfEatZombieType() {
        return mBiteInsteadOfEatZombieType == null ? DEFAULT_BITE_INSTEAD_OF_EAT_ZOMBIE_TYPE
                : mBiteInsteadOfEatZombieType;
    }

    /** @return the chomper's digesting pause, the game's own when nobody set one. */
    public int digestCountdown() {
        return mDigestCountdown == null ? DEFAULT_CHOMPER_DIGEST_COUNTDOWN : mDigestCountdown;
    }

    /** @return what one hit takes off the spikerock, the game's own when nobody set one. */
    public int spikeRockHitDamage() {
        return mSpikeRockHitDamage == null ? DEFAULT_SPIKEROCK_HIT_DAMAGE : mSpikeRockHitDamage;
    }

    /** @return the health at which the spikerock's first spike art goes, the game's own by default. */
    public int spikeRockHideBigSpike3At() {
        return mSpikeRockHideBigSpike3At == null ? DEFAULT_SPIKEROCK_HIDE_BIGSPIKE3_AT : mSpikeRockHideBigSpike3At;
    }

    /** @return the health at which the spikerock's second spike art goes, the game's own by default. */
    public int spikeRockHideBigSpike2At() {
        return mSpikeRockHideBigSpike2At == null ? DEFAULT_SPIKEROCK_HIDE_BIGSPIKE2_AT : mSpikeRockHideBigSpike2At;
    }

    /** @return the blast radius, the game's own when nobody set one. */
    public int explosionRadius() {
        return mExplosionRadius == null ? DEFAULT_EXPLOSION_RADIUS : mExplosionRadius;
    }

    /** @return how many rows either side of the plant the blast reaches, the game's own by default. */
    public int explosionRowRange() {
        return mExplosionRowRange == null ? DEFAULT_EXPLOSION_ROW_RANGE : mExplosionRowRange;
    }

    /** @return whether the blast burns what it hits; false by default (the game's own damage-only blast) */
    public boolean explosionBurns() {
        return Boolean.TRUE.equals(mExplosionBurns);
    }

    /** @return the coin the plant drops by default, the game's own when nobody set one. */
    public int producedCommonCoinType() {
        return mProducedCommonCoinType == null ? DEFAULT_PRODUCED_COMMON_COIN_TYPE : mProducedCommonCoinType;
    }

    /** @return the coin the rare roll drops, the game's own when nobody set one. */
    public int producedRareCoinType() {
        return mProducedRareCoinType == null ? DEFAULT_PRODUCED_RARE_COIN_TYPE : mProducedRareCoinType;
    }

    /** @return how often, out of 100, the rare coin replaces the common one; the game's own by default. */
    public int producedCoinRarePercent() {
        return mProducedRarePercent == null ? DEFAULT_PRODUCED_COIN_RARE_PERCENT : mProducedRarePercent;
    }

    /**
     * @return the crush-proof flag a mod registered, or {@code null} when nobody set one - the game's
     *     own answer (crush-proof exactly for {@link #DEFAULT_CRUSH_PROOF_SEED_TYPE}) is then what
     *     {@link PlantOverrides#isCrushProof(int)} resolves it to
     */
    public Boolean crushProof() {
        return mCrushProof;
    }

    @Override
    public String toString() {
        StringBuilder aText = new StringBuilder("PlantOverride[seedType=").append(mSeedType);
        if (mCost != null) {
            aText.append(", cost=").append(mCost);
        }
        if (mRefreshTime != null) {
            aText.append(", refreshTime=").append(mRefreshTime);
        }
        if (mLaunchRate != null) {
            aText.append(", launchRate=").append(mLaunchRate);
        }
        if (mBodyHealth != null) {
            aText.append(", bodyHealth=").append(mBodyHealth);
        }
        if (mSpawnCountdown != null) {
            aText.append(", spawnCountdown=").append(mSpawnCountdown);
        }
        if (mBiteInsteadOfEatZombieType != null) {
            aText.append(", biteInsteadOfEat=").append(mBiteInsteadOfEatZombieType);
        }
        if (mDigestCountdown != null) {
            aText.append(", digestCountdown=").append(mDigestCountdown);
        }
        if (mSpikeRockHitDamage != null) {
            aText.append(", spikeRockDamage=").append(mSpikeRockHitDamage)
                    .append('/').append(mSpikeRockHideBigSpike3At)
                    .append('/').append(mSpikeRockHideBigSpike2At);
        }
        if (mExplosionRadius != null) {
            aText.append(", explosionBlast=").append(mExplosionRadius)
                    .append('/').append(mExplosionRowRange)
                    .append('/').append(Boolean.TRUE.equals(mExplosionBurns));
        }
        if (mProducedCommonCoinType != null) {
            aText.append(", producedCoins=").append(mProducedCommonCoinType)
                    .append('/').append(mProducedRareCoinType)
                    .append('/').append(mProducedRarePercent);
        }
        if (mCrushProof != null) {
            aText.append(", crushProof=").append(mCrushProof);
        }
        return aText.append(']').toString();
    }

    /**
     * Puts the definition values this handle wrote back to what the game had, and forgets everything.
     *
     * <p><b>Internal.</b> Called by {@link PlantOverrides#reset()}; a row that disappeared since (an
     * unregistered custom seed type) is skipped, and the recorded spawn health is dropped without
     * touching any plant - plants already born keep the health they were born with, exactly like
     * zombies do.
     */
    void restore() {
        PlantDefinition aDefinition = definitionRowOrNull();
        if (aDefinition != null) {
            if (mCostBefore != null) {
                aDefinition.mSeedCost = mCostBefore;
            }
            if (mRefreshTimeBefore != null) {
                aDefinition.mRefreshTime = mRefreshTimeBefore;
            }
            if (mLaunchRateBefore != null) {
                aDefinition.mLaunchRate = mLaunchRateBefore;
            }
        }

        mCost = null;
        mRefreshTime = null;
        mLaunchRate = null;
        mBodyHealth = null;
        mSpawnCountdown = null;
        mBiteInsteadOfEatZombieType = null;
        mDigestCountdown = null;
        mSpikeRockHitDamage = null;
        mSpikeRockHideBigSpike3At = null;
        mSpikeRockHideBigSpike2At = null;
        mExplosionRadius = null;
        mExplosionRowRange = null;
        mExplosionBurns = null;
        mProducedCommonCoinType = null;
        mProducedRareCoinType = null;
        mProducedRarePercent = null;
        mCrushProof = null;
        mCostBefore = null;
        mRefreshTimeBefore = null;
        mLaunchRateBefore = null;
    }

    private PlantDefinition definitionRow() {
        PlantDefinition aDefinition = definitionRowOrNull();
        if (aDefinition == null) {
            throw new IllegalStateException("Seed type " + mSeedType + " has no plant definition row: only a"
                    + " plant the game or PlantRegistry defined has a definition to override");
        }
        return aDefinition;
    }

    private PlantDefinition definitionRowOrNull() {
        return Plant.getPlantDefinition(mSeedType);
    }

    private static int requireNonNegative(int theValue, String theField) {
        if (theValue < 0) {
            throw new IllegalArgumentException("A plant's " + theField + " cannot be negative: " + theValue);
        }
        return theValue;
    }
}
