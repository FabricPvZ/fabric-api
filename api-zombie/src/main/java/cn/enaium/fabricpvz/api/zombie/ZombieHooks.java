package cn.enaium.fabricpvz.api.zombie;

import cn.enaium.fabricpvz.portable.lawn.Board;
import cn.enaium.fabricpvz.portable.lawn.ImageID;
import cn.enaium.fabricpvz.portable.lawn.ReanimLoopType;
import cn.enaium.fabricpvz.portable.lawn.ReanimationID;
import cn.enaium.fabricpvz.portable.lawn.ReanimationType;
import cn.enaium.fabricpvz.portable.lawn.Resources;
import cn.enaium.fabricpvz.portable.lawn.Zombie;
import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.reanim.AttachEffect;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Attachment;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.sexy.Rect;

/**
 * Bridge between the game and the zombies this API registered, called by this module's Mixins
 * ({@link cn.enaium.fabricpvz.api.zombie.mixin}).
 *
 * <p><b>Internal.</b> This class is not part of the mod-facing API: it exists so the Mixins injected
 * into the game can live in this module instead of the game's own sources, which no longer know
 * anything about mod content. Every method answers a value that means "do not interfere" for a zombie
 * type nobody registered, so an unmodded game runs exactly its own code.
 */
public final class ZombieHooks {

    private ZombieHooks() {
    }

    /**
     * Applies the registered definition to a zombie that just finished the upstream per-type setup in
     * {@code Zombie::ZombieInitialize} (Zombie.cpp:143): the plain reanimation layers, the optional
     * attached head, the health/armour numbers, the collision rectangles and the behaviour's
     * {@code onSpawned} callback. This is what the game used to do for mod zombie types.
     *
     * @param zombie the zombie being initialised
     */
    public static void onSpawned(Zombie zombie) {
        CustomZombieType entry = ZombieRegistry.entry(zombie.mZombieType);
        if (entry == null) {
            return;
        }

        zombie.loadPlainZombieReanim();
        attachHead(zombie, entry.mHead);

        zombie.mBodyHealth = entry.mBodyHealth;
        zombie.mHelmType = entry.mHelmType;
        zombie.mHelmHealth = entry.mHelmHealth;
        zombie.mShieldType = entry.mShieldType;
        zombie.mShieldHealth = entry.mShieldHealth;
        zombie.mFlyingHealth = entry.mFlyingHealth;
        zombie.mScaleZombie = entry.mScaleZombie;

        // The game derives each max from the health it set, which ran before this RETURN injection
        // point; the game's old inline call sat before that derivation. Mirror it here so an initialised
        // mod zombie carries the same health/max pair a built-in one does.
        zombie.mBodyMaxHealth = zombie.mBodyHealth;
        zombie.mHelmMaxHealth = zombie.mHelmHealth;
        zombie.mShieldMaxHealth = zombie.mShieldHealth;
        zombie.mFlyingMaxHealth = zombie.mFlyingHealth;

        if (entry.mZombieRect != null) {
            zombie.mZombieRect = copy(entry.mZombieRect);
        }
        if (entry.mZombieAttackRect != null) {
            zombie.mZombieAttackRect = copy(entry.mZombieAttackRect);
        }
        if (entry.mShieldHealth > 0) {
            zombie.attachShield();
        }

        if (entry.mBehaviour != null) {
            entry.mBehaviour.onSpawned(zombie);
        }
    }

    /**
     * Re-runs the behaviour's {@code onSpawned} callback for every registered zombie a loaded board
     * carries, what the callback half of {@link #onSpawned(Zombie)} does at spawn time.
     *
     * <p>A board restored by {@code Board::loadGame} never goes through {@code Zombie::ZombieInitialize},
     * so this is the only place the callbacks a registered zombie registered run again. The save carries
     * the zombies' fields, armour and reanimation ids, and the loader restores their rectangles and
     * attached heads, so none of that is replayed here: replaying the definition's health/armour numbers
     * would heal a zombie that was saved injured.
     *
     * <p>Callbacks must therefore be idempotent and must not accumulate one-shot state.
     *
     * @param board the board whose zombies were just restored
     */
    public static void onRestored(Board board) {
        for (Zombie aZombie : board.mZombies) {
            if (aZombie.mDead) {
                continue;
            }

            CustomZombieType entry = ZombieRegistry.entry(aZombie.mZombieType);
            if (entry != null && entry.mBehaviour != null) {
                entry.mBehaviour.onSpawned(aZombie);
            }
        }
    }

    /**
     * @param zombieType a zombie type
     * @return the display name the mod registered, or {@code null} when nothing is registered under
     *     the zombie type or the registration has no name (the game then reads its string file)
     */
    public static String name(int zombieType) {
        CustomZombieType entry = ZombieRegistry.entry(zombieType);
        return entry == null || entry.mName.isEmpty() ? null : entry.mName;
    }

    /**
     * @param zombieType a zombie type
     * @return the almanac description, falling back to {@link #name(int)}, or {@code null} when
     *     nothing is registered under the zombie type or the registration describes it nowhere (the
     *     game then reads its own string file)
     */
    public static String almanacDescription(int zombieType) {
        CustomZombieType entry = ZombieRegistry.entry(zombieType);
        if (entry == null) {
            return null;
        }
        return entry.mAlmanacDescription.isEmpty() ? name(zombieType) : entry.mAlmanacDescription;
    }

    /**
     * Fires the behaviour tick of a zombie, what {@code Zombie::Update} (Zombie.cpp:908) used to do for
     * mod zombie types.
     *
     * @param zombie the zombie being updated
     */
    public static void onTick(Zombie zombie) {
        CustomZombieType entry = ZombieRegistry.entry(zombie.mZombieType);
        if (entry != null && entry.mBehaviour != null) {
            entry.mBehaviour.onUpdate(zombie);
        }
    }

    /**
     * What a zombie gains when the game mind-controls it - the whole-game rule
     * {@link ZombieOverrides#mindControlBoost(int, int, float)}, with the per-type flat body health
     * {@link ZombieOverride#mindControlBodyHealth(int)} where the type set one.
     *
     * <p>Called by this module's Zombie Mixin at the return of {@code Zombie::StartMindControlled}
     * (Zombie.cpp:6984) - after the game set {@code mMindControlled} and unwound whatever the zombie
     * was doing with the zombie it was attached to - and applies the patch's order: the chill counter,
     * then the body health, then the head-armour health (for every zombie, helmet or not, exactly as
     * the patched code writes it), then the scale. Nothing is applied until a mod registers a rule, so
     * an unmodded game leaves the zombie exactly as the game built it.
     *
     * @param zombie the zombie that just became mind-controlled
     */
    public static void onMindControlled(Zombie zombie) {
        Integer aFlatBody = ZombieOverrides.mindControlBodyHealth(zombie.mZombieType);
        if (aFlatBody == null && !ZombieOverrides.mindControlBoosted()) {
            return;
        }

        zombie.mChilledCounter = 0;
        zombie.mBodyHealth = aFlatBody != null
                ? aFlatBody : zombie.mBodyMaxHealth + ZombieOverrides.mindControlBodyBonus();
        zombie.mHelmHealth = zombie.mHelmMaxHealth + ZombieOverrides.mindControlHelmBonus();
        zombie.mScaleZombie = ZombieOverrides.mindControlScale();
        zombie.mMindControlled = true;
    }

    /**
     * The burn's clamp, as a mod registered it with {@link ZombieOverrides#burnImmunity(int, int...)}:
     * a zombie whose body, head armour and shield add up to at least the registered health - or that is
     * in one of the registered phases - takes a flat 1800 damage instead of burning up.
     *
     * <p>Called by this module's {@code ZombieStatusMixin} at the head of {@code Zombie::ApplyBurn}
     * (Zombie.cpp:8661); a {@code true} answer means the clamp ran and the method must not run its own
     * code. The game's own early return (dead, or already burned) is honoured here so the injection
     * cannot run the clamp on a zombie the method would have ignored - and a process without a rule
     * answers {@code false} without touching anything.
     *
     * @param zombie the zombie being burned
     * @return whether the clamp ran
     */
    public static boolean applyBurnImmunity(Zombie zombie) {
        int aTotalHealth = ZombieOverrides.burnImmunityTotalHealth();
        if (aTotalHealth <= 0 || zombie.mDead
                || zombie.mZombiePhase == cn.enaium.fabricpvz.portable.lawn.ZombiePhase.PHASE_ZOMBIE_BURNED) {
            return false;
        }

        if (zombie.mBodyHealth + zombie.mHelmHealth + zombie.mShieldHealth >= aTotalHealth
                || ZombieOverrides.isBurnImmunePhase(zombie.mZombiePhase)) {
            zombie.takeDamage(1800, 18);
            return true;
        }
        return false;
    }

    /**
     * The rewrite of {@code Zombie::RemoveColdEffects} (Zombie.cpp:8647) a mod registered with
     * {@link ZombieOverrides#coldRemovalChilledCounter(int)}: the ice trap is removed by the game's own
     * code path, and the chill counter is then written with the registered value unconditionally -
     * where the game clears it only when it was above zero.
     *
     * <p>Called by this module's {@code ZombieStatusMixin} at the head of the method; a {@code true}
     * answer means the rewrite ran and the method must not run its own code.
     *
     * @param zombie the zombie being thawed
     * @return whether the rewrite ran
     */
    public static boolean removeColdEffects(Zombie zombie) {
        Integer aCounter = ZombieOverrides.coldRemovalChilledCounter();
        if (aCounter == null) {
            return false;
        }

        if (zombie.mIceTrapCounter > 0) {
            cn.enaium.fabricpvz.portable.lawn.ZombieStatus.removeIceTrap(zombie);
        }
        zombie.mChilledCounter = aCounter;
        zombie.updateAnimSpeed();
        return true;
    }

    /**
     * The two damage-flag questions {@code Zombie::TakeDamage} (Zombie.cpp:7970) asks, with
     * {@link ZombieOverrides#shieldAlwaysAbsorbs(boolean)} applied: with the rule on, both
     * {@code DAMAGE_BYPASSES_SHIELD} and {@code DAMAGE_HITS_SHIELD_AND_BODY} answer false, so the
     * damage always goes through {@code Zombie::TakeShieldDamage} and the body never gets the flag's
     * own full share on top of it. With no rule registered the game's own {@code PvzpCommon.testBit}
     * answer is returned unchanged.
     *
     * <p>Called by this module's {@code ZombieCombatMixin} for every {@code PvzpCommon.testBit} call
     * of that method, which is exactly those two.
     *
     * @param theDamageFlags the damage flags the game passed
     * @param theFlag the flag index being tested
     * @return whether the flag is set
     */
    public static boolean damageFlagTest(int theDamageFlags, int theFlag) {
        if (ZombieOverrides.shieldAlwaysAbsorbs()
                && (theFlag == cn.enaium.fabricpvz.portable.lawn.DamageFlags.DAMAGE_BYPASSES_SHIELD
                        || theFlag == cn.enaium.fabricpvz.portable.lawn.DamageFlags.DAMAGE_HITS_SHIELD_AND_BODY)) {
            return false;
        }
        return PvzpCommon.testBit(theDamageFlags, theFlag);
    }

    /**
     * @param zombieType a zombie type
     * @return whether the registered zombie may walk through a pool row, or {@code null} when nothing
     *     is registered under the zombie type
     */
    public static Boolean canGoInPool(int zombieType) {
        CustomZombieType entry = ZombieRegistry.entry(zombieType);
        return entry == null ? null : entry.mCanGoInPool;
    }

    /**
     * Ports the "another plant as a head" setup of the peashooter-head zombies (Zombie.cpp:720-742) for
     * any mod zombie that asked for one.
     */
    private static void attachHead(Zombie zombie, CustomZombieType.HeadAttachment head) {
        if (head == null || head.mReanimationType == ReanimationType.REANIM_NONE) {
            return;
        }
        if (zombie.mBodyReanimID == ReanimationID.REANIMATIONID_NULL || zombie.mApp == null) {
            return;
        }

        Reanimation aBodyReanim = zombie.mApp.reanimationGet(zombie.mBodyReanimID);
        if (aBodyReanim == null || aBodyReanim.mTrackInstances == null) {
            return;
        }

        if (!head.mHiddenLayer.isEmpty()) {
            zombie.reanimShowPrefix(head.mHiddenLayer, Reanimation.RENDER_GROUP_HIDDEN);
        }

        if (!aBodyReanim.trackExists(head.mBodyTrack)) {
            return;
        }

        if (!head.mHiddenTrack.isEmpty() && aBodyReanim.trackExists(head.mHiddenTrack)) {
            aBodyReanim.getTrackInstanceByName(head.mHiddenTrack).mImageOverride =
                    Resources.getImageById(ImageID.IMAGE_BLANK);
        }

        Reanimation aHeadReanim = zombie.mApp.addReanimation(0.0f, 0.0f, 0, head.mReanimationType);
        aHeadReanim.playReanim(head.mHeadTrack, ReanimLoopType.REANIM_LOOP, 0, 15.0f);
        zombie.mSpecialHeadReanimID = zombie.mApp.reanimationGetID(aHeadReanim);

        AttachEffect aAttachEffect = Attachment.attachReanim(
                aBodyReanim.getTrackInstanceByName(head.mBodyTrack).mAttachmentID, aHeadReanim, 0.0f, 0.0f);
        aBodyReanim.mFrameBasePose = 0;
        PvzpCommon.pvzpScaleRotateTransformMatrix(aAttachEffect.mOffset, head.mOffsetX, head.mOffsetY,
                head.mRotate, head.mScale, head.mScale);
    }

    private static Rect copy(Rect theRect) {
        return new Rect(theRect.mX, theRect.mY, theRect.mWidth, theRect.mHeight);
    }
}
