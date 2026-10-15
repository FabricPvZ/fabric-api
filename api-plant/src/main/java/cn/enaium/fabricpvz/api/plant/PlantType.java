package cn.enaium.fabricpvz.api.plant;

import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

import cn.enaium.fabricpvz.portable.lawn.Board;
import cn.enaium.fabricpvz.portable.lawn.Plant;
import cn.enaium.fabricpvz.portable.lawn.PlantSubClass;
import cn.enaium.fabricpvz.portable.lawn.ReanimationType;
import cn.enaium.fabricpvz.portable.lawn.SeedType;
import cn.enaium.fabricpvz.portable.lawn.content.CustomPlantType;
import cn.enaium.fabricpvz.portable.lawn.content.PlantBehaviour;
import cn.enaium.fabricpvz.portable.sexy.Rect;

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
 * <p>Fields map one to one onto {@link CustomPlantType}, the game's own description of a mod plant,
 * which is what the game reads from {@code Plant::GetPlantDefinition}, the planting rules, the seed
 * packet and the almanac.
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

        /** Display name shown on the seed packet and in the almanac. */
        public Builder name(String theName) {
            definition.mName = theName;
            return this;
        }

        /** Seed packet tooltip; defaults to the name. */
        public Builder tooltip(String theTooltip) {
            definition.mTooltip = theTooltip;
            return this;
        }

        /** Almanac body text; defaults to the tooltip. */
        public Builder almanacDescription(String theDescription) {
            definition.mAlmanacDescription = theDescription;
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

        /** Maximum health; upstream plants default to 300. */
        public Builder health(int theHealth) {
            definition.mPlantHealth = theHealth;
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

        /** Whether the seed may appear in seed-chooser/almanac UIs; default {@code true}. */
        public Builder chooserVisible(boolean theVisible) {
            definition.mChooserVisible = theVisible;
            return this;
        }

        /** Callback run once per plant tick, after the plant's own update. */
        public Builder onTick(Consumer<Plant> theCallback) {
            tick = theCallback;
            return this;
        }

        /** Callback run right after the plant has been placed on a board. */
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
