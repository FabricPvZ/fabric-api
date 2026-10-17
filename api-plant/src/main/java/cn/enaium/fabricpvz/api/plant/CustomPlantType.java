package cn.enaium.fabricpvz.api.plant;

import cn.enaium.fabricpvz.portable.lawn.PlantDefinition;
import cn.enaium.fabricpvz.portable.lawn.PlantSubClass;
import cn.enaium.fabricpvz.portable.lawn.ReanimationType;
import cn.enaium.fabricpvz.portable.lawn.SeedType;

/**
 * Definition of a plant registered by a mod.
 *
 * <p>This is deliberately a plain data holder with public fields, like
 * {@link PlantDefinition} and {@link cn.enaium.fabricpvz.portable.lawn.ZombieDefinition}: the mod
 * facing builders of this module fill an instance and hand it to
 * {@link PlantRegistry#register(PlantType.Builder)}.
 *
 * <p>The game reads these fields from exactly the sites where it reads the upstream per-seed tables,
 * so a registered plant behaves like a built-in one — except that every read goes through a Mixin
 * ({@link cn.enaium.fabricpvz.api.plant.mixin}) and {@link PlantHooks}, because the game itself no
 * longer knows about mod content:
 *
 * <ul>
 *   <li>{@link #mSeedCost}, {@link #mRefreshTime}, {@link #mSubClass} and {@link #mLaunchRate} end up
 *       in the {@link PlantDefinition} row registered into {@code Plant.gPlantDefs}
 *       (Plant.cpp:4966);</li>
 *   <li>{@link #mReanimationType} makes the plant draw like any reanimation-backed plant, including
 *       its seed packet art ({@code ReanimatorCache::DrawCachedPlant});</li>
 *   <li>{@link #mPacketImageId} overrides that packet art with a fixed image/cel and
 *       {@link #mPacketImage} with an image the mod builds at runtime (for plants with no reanimation
 *       of their own);</li>
 *   <li>{@link #mName}, {@link #mTooltip} and {@link #mAlmanacDescription} replace the
 *       {@code LawnStrings} lookups, so a mod does not have to patch {@code main.pak};</li>
 *   <li>the {@code mAquatic}/{@code mFungus}/... flags feed
 *       {@code Plant::IsAquatic}/{@code IsFungus}/{@code IsNocturnal}/{@code IsFlying}/{@code IsUpgrade}
 *       (Plant.cpp:5080-5134), and {@link #mUpgradeFrom} feeds {@code Plant::IsUpgradableTo}
 *       (Plant.cpp:2608), i.e. which plants this seed may be planted on top of;</li>
 *   <li>{@link #mPlantHealth} replaces the {@code 300} default of {@code Plant::PlantInitialize}
 *       (Plant.cpp:120 area);</li>
 *   <li>{@link #mProjectileType} lets a shooter plant fire from {@code Plant::Fire}
 *       (Plant.cpp:4476-4770);</li>
 *   <li>{@link #mBehaviour} supplies the planting/collision/update hooks.</li>
 * </ul>
 *
 * <p>Fields are read when the plant is created and whenever the game consults the definition tables,
 * so a registered type must be treated as immutable.
 */
public class CustomPlantType {

    /** Value of {@link #mPacketImageId} meaning "derive the packet art from the plant reanimation". */
    public static final int PACKET_IMAGE_FROM_REANIM = 0;

    /**
     * Seed type this plant was registered under. Assigned by
     * {@link PlantRegistry#register(PlantType.Builder)} from {@link PlantRegistry#FIRST_SEED_TYPE}
     * upwards; never set it by hand.
     */
    public int mSeedType = SeedType.SEED_NONE;

    /** Display name shown on the seed packet; when empty, the game falls back to its string file. */
    public String mName = "";

    /** Seed packet tooltip; falls back to {@link #mName} when empty. */
    public String mTooltip = "";

    /**
     * Almanac description of the plant, shown on the almanac's plant page; falls back to
     * {@link #mTooltip} when empty, and to the game's own string file when that is empty too.
     */
    public String mAlmanacDescription = "";

    /**
     * String-file key stem, e.g. {@code "PEASHOOTER"}. Used when {@link #mName} is empty, in which
     * case the name is read from the game's string file exactly like an upstream plant.
     */
    public String mStringKeyStem = "";

    /** Sun cost of one seed packet. */
    public int mSeedCost;

    /** Recharge time in centiseconds, see {@code PlantDefinition#mRefreshTime}. */
    public int mRefreshTime = 750;

    /** {@code PlantSubClass} value driving which upstream ability set the plant runs. */
    public int mSubClass = PlantSubClass.SUBCLASS_NORMAL;

    /** Tick interval between the plant's launches, see {@code PlantDefinition#mLaunchRate}. */
    public int mLaunchRate;

    /**
     * Shots a shooting plant fires per launch: {@code 1} (the default), {@code 2} (like the repeater's
     * two peas) or {@code 4} (like the gatling pea's four). The game hard-codes those cadences on its
     * own seed types ({@code Plant::FindTargetAndFire}, {@code Plant::UpdateShooter},
     * {@code Plant::UpdateShooting}), so a registered plant fires exactly one pea without this; the
     * API's plant Mixin spends this many shots instead, with the retail timings of the seed the plant
     * imitates. See {@code PlantType.Builder#burst(int)}.
     */
    public int mBurst = 1;

    /** {@code ReanimationType} of the plant; {@link ReanimationType#REANIM_NONE} for a static one. */
    public int mReanimationType = ReanimationType.REANIM_NONE;

    /** Fixed packet art image id; {@link #PACKET_IMAGE_FROM_REANIM} keeps the reanimation-derived art. */
    public int mPacketImageId = PACKET_IMAGE_FROM_REANIM;

    /** Cel index used together with {@link #mPacketImageId}. */
    public int mPacketCelIndex;

    /**
     * Fixed packet art, built at runtime by the mod and handed over through a supplier;
     * {@code null} means "no supplier registered". Takes precedence over {@link #mPacketImageId}.
     *
     * <p>The supplier is asked once per packet draw, so it may answer {@code null} while the art it is
     * built from is not loaded yet - the game then keeps its own drawing - and start answering an image
     * later. That is why this is a supplier and not an image: a mod's art is built from resources that
     * load <em>after</em> the mod's entry point, and the seed chooser draws its packets before any board
     * tick ever runs.
     */
    public java.util.function.Supplier<cn.enaium.fabricpvz.portable.sexy.graphics.Image> mPacketImage;

    /** Maximum health of the plant, used instead of the upstream {@code 300} default. */
    public int mPlantHealth = 300;

    /**
     * {@code ProjectileType} a {@code SUBCLASS_SHOOTER} plant fires, or {@code -1} to leave
     * {@code Plant::Fire} to the game's own switch. A kind with no projectile throws there, exactly
     * like an unknown upstream seed type.
     */
    public int mProjectileType = -1;

    /**
     * Seed type the plant is gated behind: {@link PlantHooks#hasSeedType(int)} only answers "yes" for
     * it once {@code LawnApp::HasSeedType} returns true for this seed. {@code SeedType.SEED_NONE}
     * ungates it.
     */
    public int mUnlockSeedType = SeedType.SEED_NONE;

    /** See {@code Plant::IsAquatic}. */
    public boolean mAquatic;
    /** See {@code Plant::IsFungus}. */
    public boolean mFungus;
    /** See {@code Plant::IsNocturnal}. */
    public boolean mNocturnal;
    /** See {@code Plant::IsFlying}. */
    public boolean mFlying;
    /** See {@code Plant::IsUpgrade} (selects the upgrade seed-packet background). */
    public boolean mUpgrade;

    /**
     * Seed types this plant is the upgrade <em>of</em>, i.e. the plants it may be planted on: a
     * square holding one of them takes this seed and the plant underneath is replaced, the dual of
     * {@code Plant::IsUpgrade}. Empty (the default) means "upgrade of nothing", which is what every
     * plant that is not an upgrade wants.
     *
     * <p>{@code Plant::IsUpgradableTo} (Plant.cpp:2608-2641) is the one place the game decides this,
     * and it only knows its own hard-coded retail pairs. Its callers are
     * {@code Board::CanPlantAt} (Board.cpp:2688, {@code PLANTING_OK} /
     * {@code PLANTING_NEEDS_UPGRADE}) and the planting branch of {@code BoardInput}
     * ({@code Board.cpp:3585-3950}, where the replaced plant dies), so declaring this here is what
     * makes a registered seed plantable as an upgrade. See
     * {@code PlantType.Builder#upgradeFrom(int...)}.
     */
    public int[] mUpgradeFrom = new int[0];

    /**
     * Whether the seed may appear in the seed chooser; {@link PlantHooks#hasSeedType(int)} is the one
     * answer the game consults, so this also gates the plant's preloading.
     */
    public boolean mChooserVisible = true;

    /** Planting, collision and update hooks; {@code null} means "no behaviour". */
    public PlantBehaviour mBehaviour;

    /** The index-aligned table row written into {@code Plant.gPlantDefs} for this plant. */
    public PlantDefinition mDefinition;

    /** The reanimation type the plant draws with, {@link ReanimationType#REANIM_NONE} when static. */
    public int reanimationType() {
        return mReanimationType;
    }
}
