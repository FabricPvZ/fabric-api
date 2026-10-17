package cn.enaium.fabricpvz.api.zombie;

/**
 * One zombie type's spawn-time overrides, handed out by {@link ZombieOverrides#of(int)}.
 *
 * <p>Every method only records a value; nothing reaches the game until a zombie of that type is born,
 * which is when {@link ZombieOverrides} applies the recorded fields to the zombie the game built. A
 * field nobody set keeps whatever the game itself gave the zombie, so an override touches exactly the
 * numbers a mod wrote.
 *
 * <pre>{@code
 * ZombieOverrides.of(ZombieType.ZOMBIE_FLAG)
 *         .bodyHealth(820)
 *         .helm(HelmType.HELMTYPE_PAIL, 1100)
 *         .bodyAnimation("anim_bucket");
 * }</pre>
 *
 * <p>The read accessors below exist for diagnostics - a launcher or a test can print what a mod
 * registered - and answer {@code null} for a field nobody set.
 */
public final class ZombieOverride {

    private final int mZombieType;
    private Integer mBodyHealth;
    private Integer mHelmType;
    private Integer mHelmHealth;
    private Integer mShieldHealth;
    private Integer mPhaseCounter;
    private Integer mSpawnsAs;
    private Integer mMindControlBodyHealth;
    private String mBodyAnimation;

    /** An override handle; only {@link ZombieOverrides#of(int)} creates one. */
    ZombieOverride(int theZombieType) {
        mZombieType = theZombieType;
    }

    /**
     * Body health the zombie is born with, i.e. {@code Zombie#mBodyHealth} (and its maximum, which the
     * game derives from it at spawn).
     *
     * @param theHealth the hit points, {@code 0} or more
     */
    public ZombieOverride bodyHealth(int theHealth) {
        mBodyHealth = requireNonNegative(theHealth, "body health");
        return this;
    }

    /**
     * Head armour ({@code Zombie#mHelmType} / {@code mHelmHealth}), for example a pail on a type that
     * does not wear one in the retail game.
     *
     * @param theHelmType a {@code HelmType} value, e.g. {@code HelmType.HELMTYPE_PAIL}
     * @param theHelmHealth the hit points, {@code 0} or more
     */
    public ZombieOverride helm(int theHelmType, int theHelmHealth) {
        mHelmType = theHelmType;
        mHelmHealth = requireNonNegative(theHelmHealth, "helm health");
        return this;
    }

    /**
     * Health of the zombie's shield ({@code Zombie#mShieldHealth}), the screen door / ladder /
     * newspaper a type already carries: a shield is not attached here, only its hit points change.
     *
     * @param theHealth the hit points, {@code 0} or more
     */
    public ZombieOverride shieldHealth(int theHealth) {
        mShieldHealth = requireNonNegative(theHealth, "shield health");
        return this;
    }

    /**
     * Body health the zombie gets when it is <em>mind-controlled</em> - the body health
     * {@link ZombieOverrides#mindControlBoost(int, int, float)}'s bonus would otherwise give it, for a
     * zombie type whose boosted health is a fixed number rather than "its maximum plus the bonus".
     *
     * <p>The 95 Edition needs it for the newspaper zombie: the patch gives every mind-controlled zombie
     * {@code mBodyMaxHealth + 200} but writes a flat 920 for that one type. A type nobody set this on
     * gets the bonus.
     *
     * @param theHealth the hit points the zombie has while mind-controlled; {@code 0} or less means
     *     "leave the bonus in charge", the same as never calling this method
     */
    public ZombieOverride mindControlBodyHealth(int theHealth) {
        mMindControlBodyHealth = theHealth > 0 ? theHealth : null;
        return this;
    }

    /**
     * {@code Zombie#mPhaseCounter} the zombie is born with, including {@code 0}.
     *
     * @param theCounter the counter
     */
    public ZombieOverride phaseCounter(int theCounter) {
        mPhaseCounter = theCounter;
        return this;
    }

    /**
     * Makes the game spawn another zombie type in this type's place: the type is replaced before the
     * game sets the zombie up, so the zombie really <em>is</em> that type - its spawn numbers, its art,
     * its animation and every later behaviour branch - exactly as if {@code Board::AddZombieInRow} had
     * been given the other type. The 95 Edition uses it to turn its {@code ZOMBIE_DOOR} into a
     * {@code ZOMBIE_PAIL}: it has no screen-door zombie at all.
     *
     * @param theZombieType the type to spawn instead
     */
    public ZombieOverride spawnsAs(int theZombieType) {
        if (theZombieType < 0
                || theZombieType >= cn.enaium.fabricpvz.portable.lawn.ZombieType.MAX_ZOMBIE_TYPES) {
            throw new IllegalArgumentException("Not a zombie type: " + theZombieType);
        }
        mSpawnsAs = theZombieType;
        return this;
    }

    /**
     * Shows an animation on the zombie's body right after the game built it, the way the game's own
     * {@code Zombie::ReanimShowPrefix} switches a zombie's body art: every track whose name starts with
     * the given name is put in the body's normal render group, and tracks the game hid for the type
     * stay hidden. The 95 Edition uses it to give its flag zombie the bucket zombie's
     * {@code "anim_bucket"} body art on top of the pail it wears.
     *
     * @param theAnimationName the animation's track prefix, e.g. {@code "anim_bucket"}
     */
    public ZombieOverride bodyAnimation(String theAnimationName) {
        if (theAnimationName == null || theAnimationName.isBlank()) {
            throw new IllegalArgumentException("An animation name is required");
        }
        mBodyAnimation = theAnimationName;
        return this;
    }

    /** @return the zombie type this override belongs to. */
    public int zombieType() {
        return mZombieType;
    }

    /** @return the overridden body health, or {@code null} when nobody set one. */
    public Integer bodyHealth() {
        return mBodyHealth;
    }

    /** @return the overridden {@code HelmType}, or {@code null} when nobody set one. */
    public Integer helmType() {
        return mHelmType;
    }

    /** @return the overridden head-armour health, or {@code null} when nobody set one. */
    public Integer helmHealth() {
        return mHelmHealth;
    }

    /** @return the overridden shield health, or {@code null} when nobody set one. */
    public Integer shieldHealth() {
        return mShieldHealth;
    }

    /** @return the overridden {@code mPhaseCounter}, or {@code null} when nobody set one. */
    public Integer phaseCounter() {
        return mPhaseCounter;
    }

    /**
     * @return the body health the zombie gets when it is mind-controlled, or {@code null} when nobody
     *     set one (the boost's bonus is then what the zombie gets)
     */
    public Integer mindControlBodyHealth() {
        return mMindControlBodyHealth;
    }

    /** @return the zombie type this one spawns as, or {@code null} when it spawns as itself. */
    public Integer spawnsAs() {
        return mSpawnsAs;
    }

    /** @return the body animation to show, or {@code null} when nobody set one. */
    public String bodyAnimation() {
        return mBodyAnimation;
    }

    @Override
    public String toString() {
        StringBuilder aText = new StringBuilder("ZombieOverride[zombieType=").append(mZombieType);
        if (mSpawnsAs != null) {
            aText.append(", spawnsAs=").append(mSpawnsAs);
        }
        if (mBodyHealth != null) {
            aText.append(", bodyHealth=").append(mBodyHealth);
        }
        if (mHelmType != null) {
            aText.append(", helm=").append(mHelmType).append('/').append(mHelmHealth);
        }
        if (mShieldHealth != null) {
            aText.append(", shieldHealth=").append(mShieldHealth);
        }
        if (mPhaseCounter != null) {
            aText.append(", phaseCounter=").append(mPhaseCounter);
        }
        if (mMindControlBodyHealth != null) {
            aText.append(", mindControlBodyHealth=").append(mMindControlBodyHealth);
        }
        if (mBodyAnimation != null) {
            aText.append(", bodyAnimation=").append(mBodyAnimation);
        }
        return aText.append(']').toString();
    }

    private static int requireNonNegative(int theValue, String theField) {
        if (theValue < 0) {
            throw new IllegalArgumentException("A zombie's " + theField + " cannot be negative: " + theValue);
        }
        return theValue;
    }
}
