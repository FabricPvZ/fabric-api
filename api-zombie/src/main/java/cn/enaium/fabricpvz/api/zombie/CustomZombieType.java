package cn.enaium.fabricpvz.api.zombie;

import cn.enaium.fabricpvz.portable.lawn.HelmType;
import cn.enaium.fabricpvz.portable.lawn.ReanimationType;
import cn.enaium.fabricpvz.portable.lawn.ShieldType;
import cn.enaium.fabricpvz.portable.lawn.ZombieDefinition;
import cn.enaium.fabricpvz.portable.lawn.ZombieType;
import cn.enaium.fabricpvz.portable.sexy.Rect;

/**
 * Definition of a zombie registered by a mod.
 *
 * <p>A plain data holder with public fields, filled by this module's builders and handed to
 * {@link ZombieRegistry#register(ZombieType.Builder)}. The game reads it from the sites where it
 * reads {@link ZombieDefinition}, through {@link ZombieHooks} and this module's Zombie Mixins:
 *
 * <ul>
 *   <li>{@link #mBodyReanimationType} loads the zombie's reanimation, exactly like
 *       {@code ZombieDefinition#mReanimationType};</li>
 *   <li>{@link #mHead} optionally attaches a second reanimation (another plant, another zombie) as
 *       the zombie's head, the mechanism the peashooter-head zombies use (Zombie.cpp:720-742);</li>
 *   <li>{@link #mBodyHealth}, {@link #mHelmType}/{@link #mHelmHealth} and
 *       {@link #mShieldType}/{@link #mShieldHealth} replace the {@code mBodyHealth = 270} default and
 *       the per-type armour assignments of {@code Zombie::ZombieInitialize};</li>
 *   <li>{@link #mZombieRect} and {@link #mZombieAttackRect} replace the plain-zombie collision and
 *       eating rectangles;</li>
 *   <li>{@link #mZombieValue}, {@link #mStartingLevel}, {@link #mFirstAllowedWave} and
 *       {@link #mPickWeight} fill the {@link ZombieDefinition} row the wave composer reads, so a
 *       zombie with a non-zero {@link #mPickWeight} takes part in wave and grave-rising picks like a
 *       built-in one.</li>
 * </ul>
 *
 * <p>Fields are read when a zombie is created and whenever the wave composer consults the definition
 * table, so a registered type must be treated as immutable.
 */
public class CustomZombieType {

    /** Reanimation attached as the zombie's head, see {@link CustomZombieType#mHead}. */
    public static class HeadAttachment {

        /** {@code ReanimationType} of the head reanimation. */
        public int mReanimationType = ReanimationType.REANIM_NONE;

        /** Track of the body reanimation the head is attached to, e.g. {@code "anim_head1"}. */
        public String mBodyTrack = "anim_head1";

        /**
         * Track of the body reanimation whose cel is replaced by a blank image, so the original head
         * of the loaded zombie reanimation disappears behind the attached one. Empty to keep it.
         */
        public String mHiddenTrack = "";

        /** Animation layer played on the head reanimation, e.g. {@code "anim_head_idle"}. */
        public String mHeadTrack = "anim_idle";

        /** Layer of the body reanimation to hide while the head is attached, e.g. {@code "anim_hair"}. */
        public String mHiddenLayer = "";

        /** Head scale, applied through the attachment transform. */
        public float mScale = 1.0f;

        /** Head horizontal offset in board pixels. */
        public float mOffsetX;

        /** Head vertical offset in board pixels. */
        public float mOffsetY;

        /** Head rotation in radians. */
        public float mRotate;

        /** Head x shear. */
        public float mShearX;

        /** Head y shear. */
        public float mShearY;
    }

    /**
     * Zombie type this entry was registered under. Assigned by
     * {@link ZombieRegistry#register(ZombieType.Builder)} from {@link ZombieRegistry#FIRST_ZOMBIE_TYPE}
     * upwards; never set it by hand.
     */
    public int mZombieType = ZombieType.ZOMBIE_INVALID;

    /** Display name; when empty, the game reads its string file through the key stem. */
    public String mName = "";

    /**
     * Almanac description of the zombie, shown on the almanac's zombie page; falls back to
     * {@link #mName} when empty, and to the game's own string file when that is empty too.
     */
    public String mAlmanacDescription = "";

    /**
     * String-file key stem, e.g. {@code "ZOMBIE"}. Used when {@link #mName} is empty, in which case the
     * name is read from the game's string file exactly like an upstream zombie.
     */
    public String mStringKeyStem = "";

    /** {@code ReanimationType} of the zombie body and head; {@link ReanimationType#REANIM_NONE} for none. */
    public int mBodyReanimationType = ReanimationType.REANIM_ZOMBIE;

    /** Optional extra reanimation attached as the head; {@code null} keeps the reanimation's own head. */
    public HeadAttachment mHead;

    /** Body health; upstream plain zombies start at {@code 270}. */
    public int mBodyHealth = 270;

    /** {@code HelmType} the zombie wears, see {@code Zombie#mHelmType}. */
    public int mHelmType = HelmType.HELMTYPE_NONE;

    /** Health of the helm; {@code 0} means no armour. */
    public int mHelmHealth;

    /** {@code ShieldType} the zombie carries, see {@code Zombie#mShieldType}. */
    public int mShieldType = ShieldType.SHIELDTYPE_NONE;

    /** Health of the shield; {@code 0} means no shield. */
    public int mShieldHealth;

    /** Health of a flying accessory (balloon); {@code 0} means the zombie does not fly. */
    public int mFlyingHealth;

    /** Collision rectangle, or {@code null} for the plain-zombie rectangle. */
    public Rect mZombieRect;

    /** Eating rectangle, or {@code null} for the plain-zombie rectangle. */
    public Rect mZombieAttackRect;

    /** Render scale; upstream plain zombies use {@code 1.0}. */
    public float mScaleZombie = 1.0f;

    /** {@code ZombieDefinition#mZombieValue} — sun/point cost of the zombie in the wave composer. */
    public int mZombieValue = 1;

    /** {@code ZombieDefinition#mStartingLevel} — first adventure level the zombie may appear on. */
    public int mStartingLevel = 1;

    /** {@code ZombieDefinition#mFirstAllowedWave} — first wave the zombie may appear in. */
    public int mFirstAllowedWave = 1;

    /**
     * {@code ZombieDefinition#mPickWeight} — relative spawn weight of the zombie. {@code 0} keeps the
     * zombie out of wave composition while it can still be spawned directly through
     * {@code Board::AddZombieInRow} (for example from a plant's ability).
     */
    public int mPickWeight = 1000;

    /**
     * Whether the zombie may walk through a pool row, see {@code Zombie::ZombieTypeCanGoInPool}.
     * Pool rows reject every zombie that is not explicitly allowed, so a swimming mod zombie has to
     * set this.
     */
    public boolean mCanGoInPool;

    /** Spawn and update hooks; {@code null} means "no behaviour". */
    public ZombieBehaviour mBehaviour;

    /** The index-aligned table row written into {@code Zombie.gZombieDefs} for this zombie. */
    public ZombieDefinition mDefinition;
}
