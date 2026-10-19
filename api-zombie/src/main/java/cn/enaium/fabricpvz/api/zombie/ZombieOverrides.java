package cn.enaium.fabricpvz.api.zombie;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.lawn.Zombie;

/**
 * Overrides the numbers a zombie type is born with - for the retail types as well, which is what a
 * rebalance of the shipped game ("95 版" and the like) needs, and not only for the types
 * {@link ZombieType#builder(String)} registers.
 *
 * <pre>{@code
 * ZombieOverrides.of(ZombieType.ZOMBIE_DOOR).spawnsAs(ZombieType.ZOMBIE_PAIL);
 * ZombieOverrides.of(ZombieType.ZOMBIE_FLAG).bodyHealth(820).helm(HelmType.HELMTYPE_PAIL, 1100);
 * }</pre>
 *
 * <p>Every type has at most one override, handed out by {@link #of(int)}, which is idempotent: asking
 * twice gives the same handle, and the last value written to a field is the one applied. Overrides are
 * looked up by zombie type, so a {@link ZombieRegistry} zombie can be overridden too; where a
 * registration and an override set the same field, the override wins (the registration is the type's
 * base, the override the mod's final word).
 *
 * <p>The game knows nothing about this: the overrides are applied by this module's
 * {@link cn.enaium.fabricpvz.api.zombie.mixin.ZombieOverridesMixin} at the two points of
 * {@code Zombie::ZombieInitialize} (Zombie.cpp:143) that matter - before the per-type setup for
 * {@link ZombieOverride#spawnsAs(int) the type replacement}, and after it for the numbers - so a type
 * nobody overrode runs exactly the game's own code.
 *
 * <p>Next to the per-type handles this class carries the <b>whole-game rules</b> a rebalance needs and
 * no single zombie type can own - {@link #mindControlBoost(int, int, float)} (and its per-type
 * exception {@link ZombieOverride#mindControlBodyHealth(int)}), {@link #burnImmunity(int, int...)},
 * {@link #coldRemovalChilledCounter(int)} and {@link #shieldAlwaysAbsorbs(boolean)}. They are answered
 * by this module's Mixins at the game's own read points ({@code Zombie::StartMindControlled},
 * {@code Zombie::ApplyBurn}, {@code Zombie::RemoveColdEffects}, {@code Zombie::TakeDamage}), each
 * defaulting to the game's own behaviour, and {@link #rules()} lists what a mod actually set.
 */
public final class ZombieOverrides {

    private static final Object LOCK = new Object();
    private static final Map<Integer, ZombieOverride> BY_ZOMBIE_TYPE = new LinkedHashMap<>();

    /**
     * The whole-game rules below, keyed by name, in the order they were first set - the diagnostics
     * view of what a mod changed (the mirror of {@link #registered()}, which lists the per-type
     * handles).
     */
    private static final Map<String, Object> RULES = new LinkedHashMap<>();

    /** The body-health bonus a mind-controlled zombie gets, or {@code null} when no rule was set. */
    private static Integer sMindControlBodyBonus;

    /** The head-health bonus a mind-controlled zombie gets (only meaningful with the rule above). */
    private static Integer sMindControlHelmBonus;

    /** The scale a mind-controlled zombie is drawn and moved at. */
    private static Float sMindControlScale;

    /** The total health (body + head + shield) at or above which the burn's clamp applies. */
    private static Integer sBurnImmunityTotalHealth;

    /** The zombie phases the burn's clamp applies to regardless of the health. */
    private static int[] sBurnImmunityPhases = new int[0];

    /** The chill counter {@code Zombie::RemoveColdEffects} writes, or {@code null} for the game's own. */
    private static Integer sColdRemovalChilledCounter;

    /** Whether damage always goes through a shield, skipping both damage-flag arms. */
    private static Boolean sShieldAlwaysAbsorbs;

    /** The ice-trap counter a butter hit writes, or {@code null} for the game's own behaviour. */
    private static Integer sButterIceTrapCounter;

    /** The chill counter a butter hit writes (only meaningful with the counter above). */
    private static Integer sButterChilledCounter;

    /** The ice-trap counter {@code Plant::BurnRow} writes in place of removing the cold effects. */
    private static Integer sBurnRowIceTrapCounter;

    /** The chill counter {@code Plant::BurnRow} writes. */
    private static Integer sBurnRowChilledCounter;

    /** The damage {@code Plant::BurnRow} adds after the burn. */
    private static Integer sBurnRowDamage;

    /** Whether the per-type update the game runs for the yeti runs for the flag zombie instead. */
    private static Boolean sYetiUpdateOnFlag;

    private ZombieOverrides() {
    }

    /**
     * Returns the override of a zombie type, creating it on first use. Retail types are as valid as
     * registered ones.
     *
     * @param theZombieType a {@code ZombieType} value
     * @return the type's override, to be filled in with the {@link ZombieOverride} methods
     * @throws IllegalArgumentException if the value is not a zombie type
     */
    public static ZombieOverride of(int theZombieType) {
        if (theZombieType < 0
                || theZombieType >= cn.enaium.fabricpvz.portable.lawn.ZombieType.MAX_ZOMBIE_TYPES) {
            throw new IllegalArgumentException("Not a zombie type: " + theZombieType);
        }
        synchronized (LOCK) {
            return BY_ZOMBIE_TYPE.computeIfAbsent(theZombieType, ZombieOverride::new);
        }
    }

    /**
     * @return every override a mod registered, keyed by zombie type, in the order the types were first
     *     asked for; for diagnostics, the map is a snapshot and cannot be written through
     */
    public static Map<Integer, ZombieOverride> registered() {
        synchronized (LOCK) {
            return Collections.unmodifiableMap(new LinkedHashMap<>(BY_ZOMBIE_TYPE));
        }
    }

    /**
     * Clears every override; used by tests and by a loader that reloads mods in-process. Zombies
     * already spawned keep the values they were born with. The whole-game rules (the mind-control
     * boost, the burn clamp, the cold-effect rewrite and the shield rule) are cleared with them.
     */
    public static void reset() {
        synchronized (LOCK) {
            BY_ZOMBIE_TYPE.clear();
            RULES.clear();
            sMindControlBodyBonus = null;
            sMindControlHelmBonus = null;
            sMindControlScale = null;
            sBurnImmunityTotalHealth = null;
            sBurnImmunityPhases = new int[0];
            sColdRemovalChilledCounter = null;
            sShieldAlwaysAbsorbs = null;
            sButterIceTrapCounter = null;
            sButterChilledCounter = null;
            sBurnRowIceTrapCounter = null;
            sBurnRowChilledCounter = null;
            sBurnRowDamage = null;
            sYetiUpdateOnFlag = null;
        }
    }

    // ------------------------------------------------------------------------------------------
    // Whole-game rules: what happens to a zombie when it is hypnotised, burned, thawed or shot at.
    // ------------------------------------------------------------------------------------------

    /**
     * What a zombie gains when it is <em>mind-controlled</em> (it ate a hypnoshroom): its chill counter
     * is cleared, its body health becomes {@code mBodyMaxHealth + theBodyBonus} (a type that set
     * {@link ZombieOverride#mindControlBodyHealth(int)} gets that flat value instead), its head-armour
     * health becomes {@code mHelmMaxHealth + theHelmBonus} - even for a zombie that wears no helmet,
     * exactly as the patch writes it - and its scale becomes {@code theScale}.
     *
     * <p>Applied by {@link ZombieHooks#onMindControlled(cn.enaium.fabricpvz.portable.lawn.Zombie)} at
     * the end of {@code Zombie::StartMindControlled} (Zombie.cpp:6984), which is the game's own
     * "this zombie is now yours" point - reached both by a hypnoshroom and by everything else that
     * mind-controls a zombie (a hypnotised gargantuar's imp, a last-stand hypnotise). Never called
     * means never applied, so a process without a rule runs exactly the game's own code.
     *
     * @param theBodyBonus the body-health bonus, {@code 0} or more
     * @param theHelmBonus the head-health bonus, {@code 0} or more
     * @param theScale     the scale the zombie takes, e.g. {@code 1.25f}
     */
    public static void mindControlBoost(int theBodyBonus, int theHelmBonus, float theScale) {
        if (theBodyBonus < 0 || theHelmBonus < 0) {
            throw new IllegalArgumentException("A mind-control bonus cannot be negative: "
                    + theBodyBonus + "/" + theHelmBonus);
        }
        if (theScale <= 0.0f) {
            throw new IllegalArgumentException("A zombie's scale must be positive: " + theScale);
        }
        synchronized (LOCK) {
            sMindControlBodyBonus = theBodyBonus;
            sMindControlHelmBonus = theHelmBonus;
            sMindControlScale = theScale;
            RULES.put("mindControlBoost", theBodyBonus + "/" + theHelmBonus + "/" + theScale);
        }
    }

    /**
     * @return whether {@link #mindControlBoost(int, int, float)} was set; a type that only set
     *     {@link ZombieOverride#mindControlBodyHealth(int)} is handled by the hook as well, so this is
     *     the "at least one rule is in place" question rather than the hook's own gate
     */
    public static boolean mindControlBoosted() {
        synchronized (LOCK) {
            return sMindControlBodyBonus != null;
        }
    }

    /** @return the mind-control body-health bonus, {@code 0} when nobody set the boost. */
    public static int mindControlBodyBonus() {
        synchronized (LOCK) {
            return sMindControlBodyBonus == null ? 0 : sMindControlBodyBonus;
        }
    }

    /** @return the mind-control head-health bonus, {@code 0} when nobody set the boost. */
    public static int mindControlHelmBonus() {
        synchronized (LOCK) {
            return sMindControlHelmBonus == null ? 0 : sMindControlHelmBonus;
        }
    }

    /** @return the mind-control scale, {@code 1.0f} when nobody set the boost. */
    public static float mindControlScale() {
        synchronized (LOCK) {
            return sMindControlScale == null ? 1.0f : sMindControlScale;
        }
    }

    /**
     * The health of the zombie type as seen by {@link #mindControlBoost(int, int, float)}'s body bonus:
     * whether the type wants a flat body health instead.
     *
     * @param theZombieType a {@code ZombieType} value
     * @return the flat body health, or {@code null} when the type wants the bonus
     */
    static Integer mindControlBodyHealth(int theZombieType) {
        ZombieOverride aOverride;
        synchronized (LOCK) {
            aOverride = BY_ZOMBIE_TYPE.get(theZombieType);
        }
        return aOverride == null ? null : aOverride.mindControlBodyHealth();
    }

    /**
     * The burn's clamp ({@code Zombie::ApplyBurn}): a zombie whose <em>total</em> health - body, head
     * armour and shield added together - is at least {@code theTotalHealth} takes a flat 1800 damage
     * instead of burning up, and so does a zombie in one of {@code theImmunePhases}, whatever its
     * health.
     *
     * <p>The game's own predicate looks at the body health alone; a rebalance in which a helmeted
     * zombie survives one ash attack replaces it with the total. A phase is a {@code ZombiePhase}
     * value - the 95 Edition names the newspaper zombie's walking and maddening phases, so a zombie
     * that is still reading burns to death like anything else.
     *
     * @param theTotalHealth the total health at or above which the clamp applies; must be positive
     * @param theImmunePhases the phases the clamp applies to regardless of health, any order
     */
    public static void burnImmunity(int theTotalHealth, int... theImmunePhases) {
        if (theTotalHealth <= 0) {
            throw new IllegalArgumentException("A burn-immunity health must be positive: " + theTotalHealth);
        }
        synchronized (LOCK) {
            sBurnImmunityTotalHealth = theTotalHealth;
            sBurnImmunityPhases = theImmunePhases.clone();
            RULES.put("burnImmunityTotalHealth", theTotalHealth);
            RULES.put("burnImmunityPhases", joined(theImmunePhases));
        }
    }

    /** @return the burn clamp's total-health threshold, {@code 0} when nobody set the rule. */
    public static int burnImmunityTotalHealth() {
        synchronized (LOCK) {
            return sBurnImmunityTotalHealth == null ? 0 : sBurnImmunityTotalHealth;
        }
    }

    /** @return whether a zombie phase the burn clamp applies to regardless of health was registered. */
    public static boolean isBurnImmunePhase(int theZombiePhase) {
        synchronized (LOCK) {
            for (int aPhase : sBurnImmunityPhases) {
                if (aPhase == theZombiePhase) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * The chill counter {@code Zombie::RemoveColdEffects} (Zombie.cpp:8647) writes: the game clears the
     * counter when it is above zero, while a rebalance may set it to a fixed value every time the effect
     * is removed.
     *
     * <p>Whatever happens to the ice trap around it is the game's own; only the counter and the
     * animation-speed update that follows it are rewritten.
     *
     * @param theChilledCounter the counter to write, {@code 0} or more
     */
    public static void coldRemovalChilledCounter(int theChilledCounter) {
        if (theChilledCounter < 0) {
            throw new IllegalArgumentException("A chill counter cannot be negative: " + theChilledCounter);
        }
        synchronized (LOCK) {
            sColdRemovalChilledCounter = theChilledCounter;
            RULES.put("coldRemovalChilledCounter", theChilledCounter);
        }
    }

    /** @return the counter {@link #coldRemovalChilledCounter(int)} registered, or {@code null}. */
    public static Integer coldRemovalChilledCounter() {
        synchronized (LOCK) {
            return sColdRemovalChilledCounter;
        }
    }

    /**
     * Whether every hit goes through a shield: {@code Zombie::TakeDamage} (Zombie.cpp:7970) then hands
     * the damage to {@code Zombie::TakeShieldDamage} whatever its damage flags say, and the
     * "hits shield and body" flag no longer gives the body its own full share of the damage afterwards.
     *
     * <p>The game's own code lets {@code DAMAGE_BYPASSES_SHIELD} walk past the shield and lets
     * {@code DAMAGE_HITS_SHIELD_AND_BODY} damage the shield and then the body in full; with this set,
     * damage always eats into the shield first and only what is left over reaches the helmet and the
     * body - which is what makes a ladder zombie untouchable until its ladder is gone.
     *
     * @param theAlwaysAbsorbs true to route every hit through the shield
     */
    public static void shieldAlwaysAbsorbs(boolean theAlwaysAbsorbs) {
        synchronized (LOCK) {
            sShieldAlwaysAbsorbs = theAlwaysAbsorbs;
            RULES.put("shieldAlwaysAbsorbs", theAlwaysAbsorbs);
        }
    }

    /** @return whether every hit routes through the shield; false until a mod says otherwise. */
    public static boolean shieldAlwaysAbsorbs() {
        synchronized (LOCK) {
            return Boolean.TRUE.equals(sShieldAlwaysAbsorbs);
        }
    }

    /**
     * The cold a butter hit carries: {@code Zombie::ApplyButter} (Zombie.cpp:8517) writes the butter
     * counter, and a rebalance may have the same hit freeze the zombie outright - through whatever
     * armour it still wears.
     *
     * <p>The counters are written where the game writes the butter counter: after the guards that keep
     * an unfreezable zombie out, and before the animation-speed update at the end of the method, so the
     * slowed animation that follows is the one the new counters imply.
     *
     * @param theIceTrapCounter the ice-trap counter to write, {@code 0} or more
     * @param theChilledCounter the chill counter to write, {@code 0} or more
     */
    public static void butterCold(int theIceTrapCounter, int theChilledCounter) {
        if (theIceTrapCounter < 0 || theChilledCounter < 0) {
            throw new IllegalArgumentException(
                    "A cold counter cannot be negative: " + theIceTrapCounter + " / " + theChilledCounter);
        }
        synchronized (LOCK) {
            sButterIceTrapCounter = theIceTrapCounter;
            sButterChilledCounter = theChilledCounter;
            RULES.put("butterIceTrapCounter", theIceTrapCounter);
            RULES.put("butterChilledCounter", theChilledCounter);
        }
    }

    /** @return the ice-trap counter a butter hit writes, or {@code null} for the game's own. */
    public static Integer butterIceTrapCounter() {
        synchronized (LOCK) {
            return sButterIceTrapCounter;
        }
    }

    /** @return the chill counter a butter hit writes, or {@code null} for the game's own. */
    public static Integer butterChilledCounter() {
        synchronized (LOCK) {
            return sButterChilledCounter;
        }
    }

    /**
     * What {@code Plant::BurnRow} (Plant.cpp:4226) does to every zombie it covers: the game removes the
     * zombie's cold effects, burns it and lets the burn's own clamp deal its 1800; a rebalance may
     * freeze it hard instead, add damage of its own, and drop the clamp's damage for this call.
     *
     * <p>With the rule set the row's zombies keep their cold effects and get the counters registered
     * here, the burn keeps its transformation (a zombie below its clamp burns away as it always did)
     * but no longer deals the clamp damage, and the damage registered here is dealt after the burn.
     *
     * @param theIceTrapCounter the ice-trap counter to write, {@code 0} or more
     * @param theChilledCounter the chill counter to write, {@code 0} or more
     * @param theDamage the damage to deal after the burn, {@code 0} or more
     */
    public static void burnRowCold(int theIceTrapCounter, int theChilledCounter, int theDamage) {
        if (theIceTrapCounter < 0 || theChilledCounter < 0 || theDamage < 0) {
            throw new IllegalArgumentException("The burn row's cold and damage cannot be negative: "
                    + theIceTrapCounter + " / " + theChilledCounter + " / " + theDamage);
        }
        synchronized (LOCK) {
            sBurnRowIceTrapCounter = theIceTrapCounter;
            sBurnRowChilledCounter = theChilledCounter;
            sBurnRowDamage = theDamage;
            RULES.put("burnRowIceTrapCounter", theIceTrapCounter);
            RULES.put("burnRowChilledCounter", theChilledCounter);
            RULES.put("burnRowDamage", theDamage);
        }
    }

    /** @return the ice-trap counter the burn row writes, or {@code null} for the game's own. */
    public static Integer burnRowIceTrapCounter() {
        synchronized (LOCK) {
            return sBurnRowIceTrapCounter;
        }
    }

    /** @return the chill counter the burn row writes, or {@code null} for the game's own. */
    public static Integer burnRowChilledCounter() {
        synchronized (LOCK) {
            return sBurnRowChilledCounter;
        }
    }

    /** @return the damage the burn row adds, or {@code null} for the game's own. */
    public static Integer burnRowDamage() {
        synchronized (LOCK) {
            return sBurnRowDamage;
        }
    }

    /**
     * Whether the per-type update the game runs for the yeti runs for the flag zombie instead:
     * {@code Zombie::UpdateActions} (Zombie.cpp:4428) dispatches on {@code mZombieType}, and moving its
     * {@code Zombie::UpdateYeti} branch from {@code ZOMBIE_YETI} to {@code ZOMBIE_FLAG} leaves the yeti
     * on the plain zombie's behaviour while the flag zombie takes the yeti's phase change.
     *
     * @param theOnFlag true to run the yeti's per-type update for the flag zombie instead of the yeti
     */
    public static void yetiUpdateOnFlag(boolean theOnFlag) {
        synchronized (LOCK) {
            sYetiUpdateOnFlag = theOnFlag;
            RULES.put("yetiUpdateOnFlag", theOnFlag);
        }
    }

    /** @return whether the yeti's per-type update runs for the flag zombie; false until a mod says so. */
    public static boolean yetiUpdateOnFlag() {
        synchronized (LOCK) {
            return Boolean.TRUE.equals(sYetiUpdateOnFlag);
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

    /**
     * The type a zombie is about to be initialised as, after {@link ZombieOverride#spawnsAs(int)}.
     *
     * <p><b>Internal.</b> Called by this module's Zombie Mixin at the head of
     * {@code Zombie::ZombieInitialize}, on the type argument, so the game initialises the zombie as the
     * replacement type from its very first line.
     *
     * @param theZombieType the type the game is spawning
     * @return the type to spawn instead, or the same value when the type is not overridden that way
     */
    public static int remapType(int theZombieType) {
        ZombieOverride aOverride;
        synchronized (LOCK) {
            aOverride = BY_ZOMBIE_TYPE.get(theZombieType);
        }
        return aOverride == null || aOverride.spawnsAs() == null ? theZombieType : aOverride.spawnsAs();
    }

    /**
     * Applies a zombie type's overrides to a zombie the game has just built.
     *
     * <p><b>Internal.</b> Called by this module's Zombie Mixin at the end of
     * {@code Zombie::ZombieInitialize}; the fields nobody overrode are left exactly as the game set
     * them. A maximum the game derived from a health (the usual case: {@code mBodyMaxHealth} and
     * friends are copied from the health at the end of the initialisation) follows the override, but a
     * maximum the game deliberately decoupled from its health - the Zombiquarium's flat 300, for
     * instance - is left alone.
     *
     * @param theZombie the zombie that finished initialising
     */
    public static void apply(Zombie theZombie) {
        ZombieOverride aOverride;
        synchronized (LOCK) {
            aOverride = BY_ZOMBIE_TYPE.get(theZombie.mZombieType);
        }
        if (aOverride == null) {
            return;
        }

        if (aOverride.bodyHealth() != null) {
            if (theZombie.mBodyMaxHealth == theZombie.mBodyHealth) {
                theZombie.mBodyMaxHealth = aOverride.bodyHealth();
            }
            theZombie.mBodyHealth = aOverride.bodyHealth();
        }
        if (aOverride.helmType() != null) {
            if (theZombie.mHelmMaxHealth == theZombie.mHelmHealth) {
                theZombie.mHelmMaxHealth = aOverride.helmHealth();
            }
            theZombie.mHelmType = aOverride.helmType();
            theZombie.mHelmHealth = aOverride.helmHealth();
        }
        if (aOverride.shieldHealth() != null) {
            if (theZombie.mShieldMaxHealth == theZombie.mShieldHealth) {
                theZombie.mShieldMaxHealth = aOverride.shieldHealth();
            }
            theZombie.mShieldHealth = aOverride.shieldHealth();
        }
        if (aOverride.phaseCounter() != null) {
            theZombie.mPhaseCounter = aOverride.phaseCounter();
        }
        if (aOverride.bodyAnimation() != null && theZombie.mApp != null) {
            theZombie.reanimShowPrefix(aOverride.bodyAnimation(), Reanimation.RENDER_GROUP_NORMAL);
        }
    }
}
