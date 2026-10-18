package cn.enaium.fabricpvz.api.plant;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import cn.enaium.fabricpvz.portable.lawn.Board;
import cn.enaium.fabricpvz.portable.lawn.Plant;
import cn.enaium.fabricpvz.portable.lawn.PlantSubClass;
import cn.enaium.fabricpvz.portable.lawn.ReanimationType;
import cn.enaium.fabricpvz.portable.sexy.Rect;
import cn.enaium.fabricpvz.portable.sexy.graphics.Image;

/**
 * A plant a mod registers, described with a builder.
 *
 * <pre>{@code
 * public final class MyMod implements ModInitializer {
 *     public static final PlantType PEASHOOTER_PLUS = PlantType.builder("peashooter_plus")
 *             .name("Pea Shooter+")
 *             .tooltip("Fires two peas. Costs a little more.")
 *             .cost(125)
 *             .reanimation(ReanimationType.REANIM_REPEATER)
 *             .subClass(PlantSubClass.SUBCLASS_SHOOTER)
 *             .launchRate(150)
 *             .health(400)
 *             .register();
 *
 *     @Override
 *     public void onInitialize() {
 *         // the plant is usable from here on: SeedType value is PEASHOOTER_PLUS.seedType()
 *     }
 * }
 * }</pre>
 *
 * <p>All of this is plain Java: the mod's own classes implement the callbacks, so nothing on the
 * mod's side needs reflection, an annotation processor or a generated accessor.
 *
 * <p>Fields map one to one onto {@link CustomPlantType}, the description {@link PlantHooks} serves
 * to the game: registration writes it as a {@code PlantDefinition} row into {@code Plant.gPlantDefs}
 * and the rest (names, flags, behaviour, packet art) is dispatched through the Mixins of this module.
 */
public final class PlantType {

    /**
     * Custom planting rule, consulted from {@code Board::CanPlantAt} (Board.cpp:2688) for this seed
     * type only.
     */
    @FunctionalInterface
    public interface PlantingRule {

        /**
         * @param board the board the plant is being placed on
         * @param gridX the target column
         * @param gridY the target row
         * @param reason the {@code PlantingReason} the game computed so far
         * @return the {@code PlantingReason} to use; return {@code PlantingReason.PLANTING_OK} to
         *     allow the placement, or any other reason to veto it
         */
        int test(Board board, int gridX, int gridY, int reason);
    }

    /** Builder of {@link PlantType}; start with {@link PlantType#builder(String)}. */
    public static final class Builder {

        private final CustomPlantType definition = new CustomPlantType();
        private final String id;
        private Consumer<Plant> tick;
        private Consumer<Plant> placed;
        private Function<Plant, Rect> plantRect;
        private BiFunction<Plant, Integer, Rect> plantAttackRect;
        private PlantingRule plantingRule;

        private Builder(String id) {
            this.id = id;
            definition.mStringKeyStem = id.toUpperCase(java.util.Locale.ROOT);
        }

        /** Display name shown on the seed packet. */
        public Builder name(String theName) {
            definition.mName = theName;
            return this;
        }

        /** Seed packet tooltip; defaults to the name. */
        public Builder tooltip(String theTooltip) {
            definition.mTooltip = theTooltip;
            return this;
        }

        /**
         * Description shown on the almanac's plant page; defaults to the tooltip, and to the game's
         * own {@code [<KEY_STEM>_DESCRIPTION]} string when that is empty too.
         */
        public Builder almanacDescription(String theAlmanacDescription) {
            definition.mAlmanacDescription = theAlmanacDescription;
            return this;
        }

        /**
         * Key stem used for the game's own string file lookups, e.g. {@code "PEASHOOTER"} for the
         * {@code [PEASHOOTER]} / {@code [PEASHOOTER_TOOLTIP]} keys. Defaults to the upper-cased id.
         */
        public Builder stringKeyStem(String theStem) {
            definition.mStringKeyStem = theStem;
            return this;
        }

        /** Sun cost of one seed packet. */
        public Builder cost(int theCost) {
            definition.mSeedCost = theCost;
            return this;
        }

        /** Recharge time in centiseconds (750 = short, 3000 = long, anything else = very long). */
        public Builder refreshTime(int theRefreshTime) {
            definition.mRefreshTime = theRefreshTime;
            return this;
        }

        /** {@code ReanimationType} the plant draws with; {@code REANIM_NONE} for a static plant. */
        public Builder reanimation(int theReanimationType) {
            definition.mReanimationType = theReanimationType;
            return this;
        }

        /** Family of upstream abilities the plant runs, see {@code PlantSubClass}. */
        public Builder subClass(int theSubClass) {
            definition.mSubClass = theSubClass;
            return this;
        }

        /** Tick interval between the plant's launches, see {@code PlantDefinition#mLaunchRate}. */
        public Builder launchRate(int theLaunchRate) {
            definition.mLaunchRate = theLaunchRate;
            return this;
        }

        /**
         * Shots the plant fires per launch: {@code 2} (like the repeater's two peas) or {@code 4}
         * (like the gatling pea's four). Anything else is rejected - a plant that fires one pea needs
         * no call, that is the default.
         *
         * <p>The game hard-codes the multi-shot cadences on seed types, so a registered shooting plant
         * would otherwise fire exactly one pea:
         * {@code Plant::FindTargetAndFire} (Plant.cpp:734) gives a repeater a 26-tick shooting window
         * at animation rate 45 and a gatling pea a 100-tick one at 38; {@code Plant::UpdateShooter}
         * (Plant.cpp:945) fires a repeater's second pea when the launch counter reaches 25; and
         * {@code Plant::UpdateShooting} (Plant.cpp:2893) fires a gatling pea's peas at shooting
         * counters 68/51/35/18. The API's plant Mixin reproduces exactly those numbers for a registered
         * plant that declared a burst.
         *
         * @param theShots {@code 2} or {@code 4}
         * @return this builder
         * @throws IllegalArgumentException for any other value
         */
        public Builder burst(int theShots) {
            if (theShots != 2 && theShots != 4) {
                throw new IllegalArgumentException("A plant's burst is 2 or 4 shots, got " + theShots);
            }
            definition.mBurst = theShots;
            return this;
        }

        /** Maximum health; upstream plants default to 300. */
        public Builder health(int theHealth) {
            definition.mPlantHealth = theHealth;
            return this;
        }

        /**
         * Projectile the plant fires when it shoots, e.g.
         * {@code ProjectileType.PROJECTILE_FIREBALL}. A {@code SUBCLASS_SHOOTER} plant needs one or
         * {@code Plant::Fire} throws ("no projectile") like it does for an unknown upstream seed type.
         *
         * <p>Every type the game knows can be fired, including the ones upstream only ever produced by
         * converting another projectile: a fire pea is drawn from the flame reanimation the torchwood
         * conversion attaches, and the game gives a directly fired one the same body (see
         * {@code ../verification/VERIFICATION.md} §46).
         *
         * @param theProjectileType a {@code ProjectileType} value
         */
        public Builder projectile(int theProjectileType) {
            definition.mProjectileType = theProjectileType;
            return this;
        }

        /**
         * Seed type the plant is gated behind: it only appears in the seed chooser, and is only
         * preloaded, once {@code LawnApp::HasSeedType} returns true for the given seed (e.g.
         * {@code SeedType.SEED_SNOWPEA} to release the plant with the Snow Pea's award).
         *
         * @param theUnlockSeedType a {@code SeedType} value, or {@code SeedType.SEED_NONE} to ungate
         */
        public Builder unlockAfter(int theUnlockSeedType) {
            definition.mUnlockSeedType = theUnlockSeedType;
            return this;
        }

        /** Packet art taken from the plant's reanimation (the upstream path). */
        public Builder packetArtFromReanimation() {
            definition.mPacketImageId = CustomPlantType.PACKET_IMAGE_FROM_REANIM;
            return this;
        }

        /**
         * Packet art pinned to a fixed image and cel, for plants with no reanimation of their own.
         *
         * @param theImageId an {@code ImageID} value
         * @param theCelIndex cel index inside that image
         */
        public Builder packetArt(int theImageId, int theCelIndex) {
            definition.mPacketImageId = theImageId;
            definition.mPacketCelIndex = theCelIndex;
            return this;
        }

        /**
         * Packet art pinned to an image the mod <b>builds at runtime</b>, handed over as a
         * <b>lazy</b> supplier the game asks once per packet draw.
         *
         * <p>A supplier that answers {@code null} means "not built yet": the packet is drawn the way
         * the game draws it (the packet background and the cost), and the next draw asks again. An
         * image is drawn 1:1 at the packet's own origin (a {@code SEED_PACKET_WIDTH} x
         * {@code SEED_PACKET_HEIGHT} image fills the packet), and cancels the game's own plant
         * drawing for that packet.
         *
         * <p>Use this rather than {@link #packetArt(int, int)} whenever the image is made at runtime
         * (e.g. rendered from the retail art by the game's own painters, see
         * {@code GameArt#packetArt(int)}): a fixed image <em>id</em> would have to be one the game's
         * resource table owns, and a resource group load overwrites those with the retail file, wiping
         * the mod's image. The id route also opens a window in which the mod's image does not exist
         * yet - and the seed chooser draws its packets before any board tick runs, so a mod that only
         * retried from a tick hook would show the retail image (or nothing) exactly when the player
         * picks seeds.
         *
         * @param theImage the supplier of this plant's packet art, asked once per draw; it may answer
         *     {@code null} until the art is buildable
         */
        public Builder packetArt(java.util.function.Supplier<Image> theImage) {
            if (theImage == null) {
                throw new IllegalArgumentException("A packet art supplier is required; use "
                        + "packetArtFromReanimation() to keep the game's own art");
            }
            definition.mPacketImage = theImage;
            return this;
        }

        /** See {@code Plant::IsAquatic}. */
        public Builder aquatic(boolean theAquatic) {
            definition.mAquatic = theAquatic;
            return this;
        }

        /** See {@code Plant::IsFungus}. */
        public Builder fungus(boolean theFungus) {
            definition.mFungus = theFungus;
            return this;
        }

        /** See {@code Plant::IsNocturnal} (the plant sleeps during the day). */
        public Builder nocturnal(boolean theNocturnal) {
            definition.mNocturnal = theNocturnal;
            return this;
        }

        /** See {@code Plant::IsFlying} (the plant needs a flying slot such as the coffee bean). */
        public Builder flying(boolean theFlying) {
            definition.mFlying = theFlying;
            return this;
        }

        /** See {@code Plant::IsUpgrade} (selects the upgrade seed-packet background). */
        public Builder upgrade(boolean theUpgrade) {
            definition.mUpgrade = theUpgrade;
            return this;
        }

        /**
         * Declares this plant the upgrade of the given seed types: dragging this seed onto a square
         * holding one of them replaces that plant and plants this one in its place - what the retail
         * gatling pea does to the repeater, i.e. the dual of {@code Plant::IsUpgradableTo}
         * (Plant.cpp:2608-2641), which the game only knows its own hard-coded pairs of.
         *
         * <p>This is <b>not</b> {@link #upgrade(boolean)}: that one only picks the purple (upgrade)
         * seed-packet background for this seed, while this one is about the plants this seed may be
         * planted on. A plant may declare either, both or neither.
         *
         * <p>May be called repeatedly and with repeated values: the seed types are de-duplicated, and
         * calling it with none leaves the plant an upgrade of nothing.
         *
         * @param theSeedTypes the seed types this plant upgrades - usually retail plants, but a seed
         *     type another mod registered works as well
         */
        public Builder upgradeFrom(int... theSeedTypes) {
            for (int aSeedType : theSeedTypes) {
                boolean aKnown = false;
                for (int anExisting : definition.mUpgradeFrom) {
                    if (anExisting == aSeedType) {
                        aKnown = true;
                        break;
                    }
                }

                if (!aKnown) {
                    int[] aGrown = java.util.Arrays.copyOf(definition.mUpgradeFrom,
                            definition.mUpgradeFrom.length + 1);
                    aGrown[aGrown.length - 1] = aSeedType;
                    definition.mUpgradeFrom = aGrown;
                }
            }

            return this;
        }

        /** Whether the seed may appear in the seed chooser; default {@code true}. */
        public Builder chooserVisible(boolean theVisible) {
            definition.mChooserVisible = theVisible;
            return this;
        }

        /** Callback run once per plant tick, after the plant's own update. */
        public Builder onTick(Consumer<Plant> theCallback) {
            tick = theCallback;
            return this;
        }

        /**
         * Callback run right after the plant has been placed on a board.
         *
         * <p>It runs again, for every plant of the registered seed type, whenever a mid-level save is
         * loaded ({@code Board::loadGame}), because the save carries the plant's fields and
         * reanimation ids but not the per-track {@code mImageOverride} a mod painted on those
         * reanimations: the callback is what rebuilds the art after a load. It must therefore be
         * <b>idempotent</b> and must not accumulate one-shot state.
         */
        public Builder onPlanted(Consumer<Plant> theCallback) {
            placed = theCallback;
            return this;
        }

        /** Collision rectangle of the plant; return {@code null} to keep the upstream one. */
        public Builder plantRect(Function<Plant, Rect> theProvider) {
            plantRect = theProvider;
            return this;
        }

        /** Attack rectangle of the plant; return {@code null} to keep the upstream one. */
        public Builder plantAttackRect(BiFunction<Plant, Integer, Rect> theProvider) {
            plantAttackRect = theProvider;
            return this;
        }

        /** Planting rule consulted by {@code Board::CanPlantAt} for this seed type. */
        public Builder plantingRule(PlantingRule theRule) {
            plantingRule = theRule;
            return this;
        }

        /**
         * Registers the plant with the game and returns the finished type.
         *
         * @return the registered type, whose {@link PlantType#seedType()} is the {@code SeedType}
         *     value the game uses
         * @throws IllegalArgumentException if the definition is incomplete
         * @throws IllegalStateException if the loader cannot offer another seed type
         */
        public PlantType register() {
            return PlantRegistry.register(this);
        }

        String id() {
            return id;
        }

        PlantingRule plantingRule() {
            return plantingRule;
        }

        CustomPlantType build() {
            definition.mBehaviour = new PlantBehaviour() {
                @Override
                public void onUpdate(Plant thePlant) {
                    CustomPlantEvents.TICK.invoker().onTick(thePlant);

                    if (tick != null) {
                        tick.accept(thePlant);
                    }
                }

                @Override
                public void onPlanted(Plant thePlant) {
                    CustomPlantEvents.PLACED.invoker().onPlaced(thePlant, thePlant.mPlantCol, thePlant.mRow);

                    if (placed != null) {
                        placed.accept(thePlant);
                    }
                }

                @Override
                public Rect plantRect(Plant thePlant) {
                    return plantRect == null ? null : plantRect.apply(thePlant);
                }

                @Override
                public Rect plantAttackRect(Plant thePlant, int thePlantWeapon) {
                    return plantAttackRect == null ? null : plantAttackRect.apply(thePlant, thePlantWeapon);
                }
            };
            return definition;
        }
    }

    private final String id;
    private final CustomPlantType definition;
    private final Builder builder;

    PlantType(String theId, CustomPlantType theDefinition, Builder theBuilder) {
        id = theId;
        definition = theDefinition;
        builder = theBuilder;
    }

    /**
     * Starts describing a plant.
     *
     * @param theId a stable identifier used for the string-file key stem and in diagnostics; the
     *     game's own {@code SeedType} value is assigned at {@link Builder#register() registration}
     *     time and is available from {@link #seedType()}
     * @return the builder
     */
    public static Builder builder(String theId) {
        if (theId == null || theId.isBlank()) {
            throw new IllegalArgumentException("A plant needs a non-blank id");
        }
        return new Builder(theId);
    }

    /** @return the id this plant was registered with. */
    public String id() {
        return id;
    }

    /**
     * @return the game's {@code SeedType} value for this plant, i.e. what {@code Board::AddPlant} and
     *     the seed packet take
     */
    public int seedType() {
        return definition.mSeedType;
    }

    /** @return the game-side definition the game reads, including its {@code PlantDefinition} row. */
    public CustomPlantType definition() {
        return definition;
    }

    /** @return the planting rule of this plant, or {@code null} when it has none. */
    PlantingRule plantingRule() {
        return builder.plantingRule();
    }

    /** @return the builder that produced this type, used by {@link PlantRegistry} to re-register. */
    Builder builder() {
        return builder;
    }

    @Override
    public String toString() {
        return "PlantType[" + id + ", seedType=" + definition.mSeedType + "]";
    }
}
