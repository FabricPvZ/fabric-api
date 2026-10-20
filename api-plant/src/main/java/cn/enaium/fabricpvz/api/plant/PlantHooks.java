package cn.enaium.fabricpvz.api.plant;

import cn.enaium.fabricpvz.portable.lawn.Board;
import cn.enaium.fabricpvz.portable.lawn.LawnApp;
import cn.enaium.fabricpvz.portable.lawn.Plant;
import cn.enaium.fabricpvz.portable.lawn.PlantOnBungeeState;
import cn.enaium.fabricpvz.portable.lawn.PlantWeapon;
import cn.enaium.fabricpvz.portable.lawn.Projectile;
import cn.enaium.fabricpvz.portable.lawn.ReanimationID;
import cn.enaium.fabricpvz.portable.lawn.Resources;
import cn.enaium.fabricpvz.portable.lawn.SeedType;
import cn.enaium.fabricpvz.portable.pvzp.PvzpCommon;
import cn.enaium.fabricpvz.portable.pvzp.reanim.Reanimation;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

/**
 * Bridge between the game and the plants this API registered, called by this module's Mixins
 * ({@link cn.enaium.fabricpvz.api.plant.mixin}).
 *
 * <p><b>Internal.</b> This class is not part of the mod-facing API: it exists so the Mixins injected
 * into the game can live in this module instead of the game's own sources, which no longer know
 * anything about mod content. Every method answers a value that means "do not interfere" for a seed
 * type nobody registered, so an unmodded game runs exactly its own code.
 */
public final class PlantHooks {

    private PlantHooks() {
    }

    /**
     * @param seedType a seed type
     * @return whether a plant is registered under the seed type, i.e. whether it belongs to a mod
     *     rather than to the game
     */
    public static boolean isCustom(int seedType) {
        return PlantRegistry.entry(seedType) != null;
    }

    /**
     * @param plant the plant being drawn
     * @return whether the registered plant declared itself invisible, i.e. draws nothing of its own
     */
    public static boolean invisible(Plant plant) {
        CustomPlantType entry = PlantRegistry.entry(plant.mSeedType);
        return entry != null && entry.mInvisible;
    }

    /**
     * Whether {@code Plant::Draw} has to draw nothing for this plant. A registered plant has no art
     * of its own in two cases: it declared itself {@link CustomPlantType#mInvisible invisible}, or
     * it has no packet image at all (its definition's image column is empty, which is what the
     * game's static-image branch of {@code Draw} dereferences when the plant has no body reanimation
     * - {@code PvzpCommon.pvzpDrawImageCelF} would then see a {@code null} image).
     *
     * <p>Only the no-body-reanimation branch is affected: a plant drawn from a reanimation (or inside
     * an I-Zombie level) keeps the game's own drawing.
     *
     * @param plant the plant being drawn
     * @return whether the caller must cancel {@code Plant::Draw}
     */
    public static boolean suppressesDraw(Plant plant) {
        CustomPlantType entry = PlantRegistry.entry(plant.mSeedType);
        if (entry == null || plant.mBodyReanimID != ReanimationID.REANIMATIONID_NULL) {
            return false;
        }
        return entry.mInvisible || Plant.getImage(plant.mSeedType) == null;
    }

    /**
     * @param seedType a seed type
     * @return whether the registered plant sleeps during the day, or {@code null} when nothing is
     *     registered under the seed type
     */
    public static Boolean isNocturnal(int seedType) {
        CustomPlantType entry = PlantRegistry.entry(seedType);
        return entry == null ? null : entry.mNocturnal;
    }

    /**
     * @param seedType a seed type
     * @return whether the registered plant is a fungus, or {@code null} when nothing is registered
     *     under the seed type
     */
    public static Boolean isFungus(int seedType) {
        CustomPlantType entry = PlantRegistry.entry(seedType);
        return entry == null ? null : entry.mFungus;
    }

    /**
     * @param seedType a seed type
     * @return whether the registered plant is aquatic, or {@code null} when nothing is registered
     *     under the seed type
     */
    public static Boolean isAquatic(int seedType) {
        CustomPlantType entry = PlantRegistry.entry(seedType);
        return entry == null ? null : entry.mAquatic;
    }

    /**
     * @param seedType a seed type
     * @return whether the registered plant needs a flying slot, or {@code null} when nothing is
     *     registered under the seed type
     */
    public static Boolean isFlying(int seedType) {
        CustomPlantType entry = PlantRegistry.entry(seedType);
        return entry == null ? null : entry.mFlying;
    }

    /**
     * @param seedType a seed type
     * @return whether the registered plant selects the upgrade seed-packet background, or
     *     {@code null} when nothing is registered under the seed type
     */
    public static Boolean isUpgrade(int seedType) {
        CustomPlantType entry = PlantRegistry.entry(seedType);
        return entry == null ? null : entry.mUpgrade;
    }

    /**
     * The dual of {@code Plant::IsUpgradableTo} (Plant.cpp:2608-2641) for a registered seed type:
     * whether the plant already on a square may be replaced by the seed being planted on it. The
     * game's own chain only knows its hard-coded retail pairs, so a registered upgrade is never
     * plantable without this.
     *
     * @param thePlantSeedType the seed type of the plant already on the square
     * @param theUpgradedType the seed type being planted on it
     * @return {@code true} when the target registered {@code upgradeFrom} the plant's seed type, or
     *     {@code false} when it registered an {@code upgradeFrom} that does not list it; {@code null}
     *     when nothing is registered under the target's seed type, or the registration declared no
     *     {@code upgradeFrom} at all, which leaves the game on its own hard-coded pairs
     */
    public static Boolean isUpgradableTo(int thePlantSeedType, int theUpgradedType) {
        CustomPlantType entry = PlantRegistry.entry(theUpgradedType);
        if (entry == null || entry.mUpgradeFrom.length == 0) {
            return null;
        }

        for (int aSeedType : entry.mUpgradeFrom) {
            if (aSeedType == thePlantSeedType) {
                return true;
            }
        }

        return false;
    }

    /**
     * The value {@code Plant::NotOnGround} (Plant.cpp:2877) reads where the game tests
     * {@code mOnBungeeState == RISING_WITH_BUNGEE}: the game's own bungee state, or - when a mod moved
     * the predicate to a plant state with {@link PlantOverrides#notOnGroundState(int)} - the bungee
     * state the game's own comparison is fooled by for a plant in that state, and
     * {@code NOT_ON_BUNGEE} (i.e. not on the ground: no) for every other plant.
     *
     * <p>That last half is the point of the rule: the game's bungee clause stops applying to every
     * plant once a mod replaced it, exactly as replacing the comparison in the exe does.
     *
     * @param thePlant the plant being asked about
     * @return the {@code PlantOnBungeeState} the game's own test should see
     */
    public static int notOnGroundBungeeState(Plant thePlant) {
        Integer aState = PlantOverrides.notOnGroundState();
        if (aState == null) {
            return thePlant.mOnBungeeState;
        }
        return thePlant.mState == aState
                ? PlantOnBungeeState.RISING_WITH_BUNGEE : PlantOnBungeeState.NOT_ON_BUNGEE;
    }

    /**
     * The seed type the crush tests of {@code Zombie::UpdateZombieGargantuar} (Zombie.cpp:2080) and
     * {@code Zombie::SquishAllInSquare} (Zombie.cpp:6476) compare the plant against: the plant's own
     * seed type - which answers the game's own comparison, {@code SEED_SPIKEROCK} - unless a mod made
     * the plant crush-proof with {@link PlantOverride#crushProof(boolean)}, in which case the game's
     * own comparison is answered for the exempt seed type and the plant gets the exempt arm.
     *
     * <p>Both callers then call {@code Plant::SpikeRockTakeDamage} / hurt the zombie, which is what the
     * comparison selects; a plant that is not crush-proof takes the squash arm the game gives it.
     *
     * @param thePlant the plant being smashed or run over
     * @return the seed type the game's own comparison should see
     */
    public static int crushTestSeedType(Plant thePlant) {
        return PlantOverrides.isCrushProof(thePlant.mSeedType)
                ? PlantOverride.DEFAULT_CRUSH_PROOF_SEED_TYPE : thePlant.mSeedType;
    }

    /**
     * @param seedType a seed type
     * @return the display name the mod registered, or {@code null} when nothing is registered under
     *     the seed type or the registration has no name (the game then reads its string file)
     */
    public static String name(int seedType) {
        CustomPlantType entry = PlantRegistry.entry(seedType);
        return entry == null || entry.mName.isEmpty() ? null : entry.mName;
    }

    /**
     * @param seedType a seed type
     * @return the tooltip, falling back to {@link #name(int)}, or {@code null} when nothing is
     *     registered under the seed type or the registration names nothing
     */
    public static String tooltip(int seedType) {
        CustomPlantType entry = PlantRegistry.entry(seedType);
        if (entry == null) {
            return null;
        }
        return entry.mTooltip.isEmpty() ? name(seedType) : entry.mTooltip;
    }

    /**
     * @param seedType a seed type
     * @return the almanac description, falling back to {@link #tooltip(int)}, or {@code null} when
     *     nothing is registered under the seed type or the registration describes it nowhere (the game
     *     then reads its own string file)
     */
    public static String almanacDescription(int seedType) {
        CustomPlantType entry = PlantRegistry.entry(seedType);
        if (entry == null) {
            return null;
        }
        return entry.mAlmanacDescription.isEmpty() ? tooltip(seedType) : entry.mAlmanacDescription;
    }

    /**
     * Applies the registered definition to a plant that has just been initialised: the health
     * override and the behaviour's {@code onPlanted} callback. This is what
     * {@code Plant::PlantInitialize} (Plant.cpp:111) used to do for mod seed types.
     *
     * @param plant the plant that was just initialised
     */
    public static void onPlanted(Plant plant) {
        CustomPlantType entry = PlantRegistry.entry(plant.mSeedType);
        if (entry == null) {
            return;
        }

        plant.mPlantHealth = entry.mPlantHealth;
        plant.mPlantMaxHealth = entry.mPlantHealth;
        if (entry.mBehaviour != null) {
            entry.mBehaviour.onPlanted(plant);
        }
    }

    /**
     * Re-runs the behaviour's {@code onPlanted} callback for every registered plant a loaded board
     * carries, what {@link #onPlanted(Plant)} does at placement time.
     *
     * <p>A board restored by {@code Board::loadGame} never goes through {@code Plant::PlantInitialize},
     * so the placement callback is the only thing that rebuilds what the game does not save: the save
     * carries the plants' fields and their reanimation ids, but not the per-track
     * {@code mImageOverride} a mod painted on those reanimations, so the art a registered plant showed
     * before the save is gone after the load until the callback runs again.
     *
     * <p>The callback runs <b>only</b>: the health numbers are deliberately not re-applied. The save
     * carries the plant's (possibly already reduced) {@code mPlantHealth}, and overwriting it with the
     * registration's value would heal an injured plant. Callbacks must therefore be idempotent and must
     * not accumulate one-shot state.
     *
     * @param board the board whose plants were just restored
     */
    public static void onRestored(Board board) {
        for (Plant aPlant : board.mPlants) {
            if (aPlant.mDead) {
                continue;
            }

            CustomPlantType entry = PlantRegistry.entry(aPlant.mSeedType);
            if (entry != null && entry.mBehaviour != null) {
                entry.mBehaviour.onPlanted(aPlant);
            }
        }
    }

    /**
     * Fires the behaviour tick of a plant, what {@code Plant::Update} (Plant.cpp:2856) used to do for
     * mod seed types.
     *
     * @param plant the plant being updated
     */
    public static void onTick(Plant plant) {
        CustomPlantType entry = PlantRegistry.entry(plant.mSeedType);
        if (entry != null && entry.mBehaviour != null) {
            entry.mBehaviour.onUpdate(plant);
        }
    }

    /**
     * @param plant a plant
     * @return the collision rectangle the behaviour wants, or {@code null} to keep the upstream one
     */
    public static Rect plantRect(Plant plant) {
        CustomPlantType entry = PlantRegistry.entry(plant.mSeedType);
        return entry == null || entry.mBehaviour == null ? null : entry.mBehaviour.plantRect(plant);
    }

    /**
     * @param plant a plant
     * @param thePlantWeapon {@code PlantWeapon} in use
     * @return the attack rectangle the behaviour wants, or {@code null} to keep the upstream one
     */
    public static Rect plantAttackRect(Plant plant, int thePlantWeapon) {
        CustomPlantType entry = PlantRegistry.entry(plant.mSeedType);
        return entry == null || entry.mBehaviour == null ? null
                : entry.mBehaviour.plantAttackRect(plant, thePlantWeapon);
    }

    /**
     * The one answer to "may the player use this seed": {@code LawnApp::HasSeedType}
     * (LawnApp.cpp:2791) and, through it, the seed chooser and the preload loops all consult this for
     * a registered seed type. An unregistered one, a hidden one and one that is still gated behind
     * another seed answer {@code false}, which is also how a hidden plant stays out of preloading.
     *
     * @param seedType a seed type
     * @return whether the seed is available, or {@code null} when nothing is registered under the
     *     seed type (the game then runs its own switch)
     */
    public static Boolean hasSeedType(int seedType) {
        CustomPlantType entry = PlantRegistry.entry(seedType);
        if (entry == null) {
            return null;
        }

        if (!entry.mChooserVisible) {
            return false;
        }

        return entry.mUnlockSeedType == SeedType.SEED_NONE
                || (LawnApp.gLawnApp != null && LawnApp.gLawnApp.hasSeedType(entry.mUnlockSeedType));
    }

    /**
     * Draws the packet art a mod pinned to a fixed image, i.e. the branch
     * {@code SeedPacket::SeedPacketDrawSeed} (SeedPacket.cpp) used to have. The caller cancels the
     * rest of the packet draw when this returns {@code true}.
     *
     * <p>An art supplier ({@link CustomPlantType#mPacketImage}) is asked on every call: it may answer
     * {@code null} while the mod's art is not built yet, and this then returns {@code false} so the
     * game draws the packet its own way. A fixed image <em>id</em>
     * ({@link CustomPlantType#mPacketImageId}) is drawn instead when no supplier was registered, which
     * is the older path.
     *
     * @param g the graphics context
     * @param x packet x
     * @param y packet y
     * @param seedType a seed type
     * @param imitaterType the imitated seed type, unused for fixed art
     * @param offX upstream packet offset, unused for fixed art
     * @param offY upstream packet offset, unused for fixed art
     * @param scale upstream packet scale, unused for fixed art
     * @return whether the packet art was drawn
     */
    public static boolean drawPacketArt(Graphics g, float x, float y, int seedType, int imitaterType, float offX,
            float offY, float scale) {
        CustomPlantType entry = PlantRegistry.entry(seedType);
        if (entry == null) {
            return false;
        }

        if (entry.mPacketImage != null) {
            Image aImage = entry.mPacketImage.get();
            if (aImage == null) {
                // The mod's art is not buildable yet (the resources it is made of are not loaded): the
                // game draws the packet, i.e. the background and the cost.
                return false;
            }

            PvzpCommon.pvzpDrawImageScaledF(g, aImage, x, y, g.mScaleX, g.mScaleY);
            return true;
        }

        if (entry.mPacketImageId == CustomPlantType.PACKET_IMAGE_FROM_REANIM) {
            return false;
        }

        PvzpCommon.pvzpDrawImageCelScaledF(g, Resources.getImageById(entry.mPacketImageId), x, y,
                entry.mPacketCelIndex, 0, g.mScaleX, g.mScaleY);
        return true;
    }

    /**
     * The seed art {@code Plant::GetImage} answers for a registered plant: every seed-art path the
     * game has ({@code Plant::DrawSeedType}, the cursor, the packet preview) reads that one method, and
     * a registered plant has no row in the game's own image table.
     *
     * <p>The registered packet art is what the seed looks like: the mod's runtime-built image when it
     * supplied one, otherwise the fixed image id and cel it pinned. A registered plant that pinned
     * neither ({@link CustomPlantType#PACKET_IMAGE_FROM_REANIM}) and has no reanimation has no seed
     * art at all, and the answer stays {@code null} - the game's own {@code GetImage} behaviour, and
     * what {@link #suppressesDraw(Plant)} uses to skip drawing such a plant.
     *
     * @param seedType a seed type
     * @return the seed art for a registered plant, or {@code null} when nothing is registered or the
     *     plant pinned no art
     */
    public static Image image(int seedType) {
        CustomPlantType entry = PlantRegistry.entry(seedType);
        if (entry == null) {
            return null;
        }

        if (entry.mPacketImage != null) {
            Image aImage = entry.mPacketImage.get();
            if (aImage != null) {
                return aImage;
            }
        }

        if (entry.mPacketImageId != CustomPlantType.PACKET_IMAGE_FROM_REANIM) {
            return Resources.getImageById(entry.mPacketImageId);
        }

        return null;
    }

    /**
     * Fires a projectile for a registered plant, the projectile half of {@code Plant::Fire}
     * (Plant.cpp:4476-4770) after its per-seed switch. The caller cancels the rest of {@code Fire}
     * when this returns {@code true}.
     *
     * <p>A plant that declared no projectile returns {@code false}, so the game's own switch runs and
     * throws "no projectile" exactly as it does for an unknown upstream seed type: the mod's
     * {@code SUBCLASS_SHOOTER} kind is still on its own to define one.
     *
     * @param plant the firing plant
     * @param target the zombie the plant picked, unused by the pea formula
     * @param row the lane the projectile starts in
     * @param weapon {@code PlantWeapon} that fired
     * @return whether a projectile was fired
     */
    public static boolean fire(Plant plant, cn.enaium.fabricpvz.portable.lawn.Zombie target, int row, int weapon) {
        CustomPlantType entry = PlantRegistry.entry(plant.mSeedType);
        if (entry == null || entry.mProjectileType == -1) {
            return false;
        }

        plant.mApp.playFoley(cn.enaium.fabricpvz.portable.pvzp.fx.FoleyType.FOLEY_THROW);

        int[] aOffsetX = new int[2];
        int[] aOffsetY = new int[2];
        plant.getPeaHeadOffset(aOffsetX, aOffsetY);
        int aOriginX = plant.mX + aOffsetX[0] + 24;
        int aOriginY = plant.mY + aOffsetY[0] - 33;
        if (plant.mBoard.getFlowerPotAt(plant.mPlantCol, plant.mRow) != null) {
            aOriginY -= 5;
        }

        Projectile aProjectile = plant.mBoard.addProjectile(aOriginX, aOriginY, plant.mRenderOrder - 1, row,
                entry.mProjectileType);
        aProjectile.mDamageRangeFlags = plant.getDamageRangeFlags(weapon);
        return true;
    }

    /**
     * @param seedType a seed type
     * @return how many shots the registered plant fires per launch, or {@code 0} when nothing is
     *     registered under the seed type (the game then runs its own per-seed cadence)
     */
    public static int burstOf(int seedType) {
        CustomPlantType entry = PlantRegistry.entry(seedType);
        return entry == null ? 0 : entry.mBurst;
    }

    /**
     * Overwrites the shooting window {@code Plant::FindTargetAndFire} (Plant.cpp:734) just set, for a
     * registered plant that declared a burst. That method only picks the multi-shot cadence when the
     * seed type is one of its own (repeater/split pea/left peater: a 26-tick window at animation rate
     * 45; gatling pea: a 100-tick window at 38), so a registered plant would get the single-shot
     * 35/35 window.
     *
     * <p>The caller only runs this when the plant really fired: overwriting the counter of a plant
     * that found no target would desynchronise its idle reanimation.
     *
     * @param plant the plant that just fired
     */
    public static void applyBurstWindow(Plant plant) {
        int aBurst = burstOf(plant.mSeedType);
        if (aBurst < 2) {
            return;
        }

        plant.mShootingCounter = aBurst == 2 ? 26 : 100;
        Reanimation aHead = plant.mApp == null ? null : plant.mApp.reanimationTryToGet(plant.mHeadReanimID);
        if (aHead != null) {
            aHead.mAnimRate = aBurst == 2 ? 45.0f : 38.0f;
        }
    }

    /**
     * Fires the second pea {@code Plant::UpdateShooter} (Plant.cpp:945) fires for its own
     * repeater/left peater seed types when the launch counter reaches 25. The caller runs this at the
     * end of that method.
     *
     * @param plant the plant being updated
     */
    public static void updateBurstShooter(Plant plant) {
        if (burstOf(plant.mSeedType) == 2 && plant.mLaunchCounter == 25) {
            plant.fire(null, plant.mRow, PlantWeapon.WEAPON_PRIMARY);
        }
    }

    /**
     * Fires the extra peas {@code Plant::UpdateShooting} (Plant.cpp:2893) fires for its own gatling
     * pea seed type at shooting counters 68/51/35/18. The caller runs this before every {@code return}
     * of that method (it returns early on the {@code mShootingCounter != 0} path those counters hit).
     *
     * <p>The retail gatling pea never reaches the method's {@code mShootingCounter == 1} branch - the
     * per-seed chain short-circuits on its seed type first, so it fires exactly those four peas. A
     * registered plant has no such branch, so after the fourth pea this sets the counter to 1: the
     * next tick decrements it straight to 0, which runs the shared end-of-window cleanup (the head
     * reanimation back to idle) and skips the extra {@code fire} a counter value of 1 would produce.
     * That is what makes a burst of 4 exactly four projectiles per launch, at the retail counters.
     *
     * @param plant the plant being updated
     */
    public static void updateBurstShooting(Plant plant) {
        if (burstOf(plant.mSeedType) != 4) {
            return;
        }

        int aCounter = plant.mShootingCounter;
        if (aCounter == 18 || aCounter == 35 || aCounter == 51 || aCounter == 68) {
            plant.fire(null, plant.mRow, PlantWeapon.WEAPON_PRIMARY);
        }
        if (aCounter == 18) {
            plant.mShootingCounter = 1;
        }
    }
}
