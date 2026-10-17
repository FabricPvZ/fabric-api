package cn.enaium.fabricpvz.api.zombie;

import java.util.function.Consumer;

import cn.enaium.fabricpvz.portable.sexy.Rect;

/**
 * A zombie a mod registers, described with a builder.
 *
 * <pre>{@code
 * public final class MyMod implements ModInitializer {
 *     public static final ZombieType RUNNER = ZombieType.builder("runner")
 *             .name("Runner Zombie")
 *             .health(400)
 *             .bodyReanimation(ReanimationType.REANIM_ZOMBIE)
 *             .wave(2, 1, 5, 2000)       // points, starting level, first wave, pick weight
 *             .register();
 *
 *     @Override
 *     public void onInitialize() {
 *         // Board::AddZombieInRow(RUNNER.zombieType(), row, fromWave) now works
 *     }
 * }
 * }</pre>
 *
 * <p>Plain Java callbacks only: the mod's own classes implement them, so nothing on the mod's side
 * needs reflection.
 *
 * <p>Fields map one to one onto {@link CustomZombieType}, the description {@link ZombieHooks} serves
 * to the game: registration writes it as a {@code ZombieDefinition} row into {@code Zombie.gZombieDefs}
 * (which the wave composer reads) and the per-type setup is dispatched through this module's Mixins.
 */
public final class ZombieType {

    /** Builder of {@link ZombieType}; start with {@link ZombieType#builder(String)}. */
    public static final class Builder {

        private final CustomZombieType definition = new CustomZombieType();
        private final String id;
        private Consumer<cn.enaium.fabricpvz.portable.lawn.Zombie> tick;
        private Consumer<cn.enaium.fabricpvz.portable.lawn.Zombie> spawned;

        private Builder(String id) {
            this.id = id;
            definition.mStringKeyStem = id.toUpperCase(java.util.Locale.ROOT);
        }

        /**
         * Display name of the zombie, for mods that want to show one; the game's own names come from
         * the string file through the key stem, which the definition row carries.
         */
        public Builder name(String theName) {
            definition.mName = theName;
            return this;
        }

        /**
         * Description shown on the almanac's zombie page; defaults to the name, and to the game's own
         * {@code [<KEY_STEM>_DESCRIPTION]} string when that is empty too.
         */
        public Builder almanacDescription(String theAlmanacDescription) {
            definition.mAlmanacDescription = theAlmanacDescription;
            return this;
        }

        /**
         * Key stem used for the game's own string file lookups, e.g. {@code "ZOMBIE"} for the
         * {@code [ZOMBIE]} / {@code [ZOMBIE_DESCRIPTION]} keys. Defaults to the upper-cased id.
         */
        public Builder stringKeyStem(String theStem) {
            definition.mStringKeyStem = theStem;
            return this;
        }

        /** {@code ReanimationType} of the zombie's body and head; the upstream zombies use {@code REANIM_ZOMBIE}. */
        public Builder bodyReanimation(int theReanimationType) {
            definition.mBodyReanimationType = theReanimationType;
            return this;
        }

        /**
         * Attaches a second reanimation as the zombie's head, the way the peashooter-head zombies
         * work: the body's own head track is blanked and the given reanimation is attached to the
         * head anchor.
         *
         * <p>Defaults are the ones the peashooter-head zombie uses
         * ({@code anim_head1} anchor, {@code anim_head1} blanked, {@code anim_hair} hidden,
         * {@code anim_idle} played). See the {@code head*} builders to override them.
         *
         * @param theReanimationType the head's reanimation
         */
        public Builder headReanimation(int theReanimationType) {
            head().mReanimationType = theReanimationType;
            return this;
        }

        /** Layer played on the head reanimation; default {@code "anim_idle"}. */
        public Builder headTrack(String theHeadTrack) {
            head().mHeadTrack = theHeadTrack;
            return this;
        }

        /** Body track the head is anchored to; default {@code "anim_head1"}. */
        public Builder headBodyTrack(String theBodyTrack) {
            head().mBodyTrack = theBodyTrack;
            return this;
        }

        /** Body track blanked behind the head; empty to keep the body's own head. */
        public Builder headHiddenTrack(String theHiddenTrack) {
            head().mHiddenTrack = theHiddenTrack;
            return this;
        }

        /** Layer of the body hidden while the head is attached; empty to keep it. */
        public Builder headHiddenLayer(String theHiddenLayer) {
            head().mHiddenLayer = theHiddenLayer;
            return this;
        }

        /** Placement of the attached head: offsets in board pixels, rotation in radians, scale. */
        public Builder headTransform(float theOffsetX, float theOffsetY, float theRotate, float theScale) {
            CustomZombieType.HeadAttachment head = head();
            head.mOffsetX = theOffsetX;
            head.mOffsetY = theOffsetY;
            head.mRotate = theRotate;
            head.mScale = theScale;
            return this;
        }

        /** Body health; upstream plain zombies start at 270. */
        public Builder health(int theHealth) {
            definition.mBodyHealth = theHealth;
            return this;
        }

        /**
         * Head armour, see {@code Zombie#mHelmType}/{@code mHelmHealth}.
         *
         * @param theHelmType a {@code HelmType} value
         * @param theHelmHealth hit points of the armour
         */
        public Builder helm(int theHelmType, int theHelmHealth) {
            definition.mHelmType = theHelmType;
            definition.mHelmHealth = theHelmHealth;
            return this;
        }

        /**
         * Shield (screen door, ladder, newspaper, ...), see {@code Zombie#mShieldType}.
         *
         * @param theShieldType a {@code ShieldType} value
         * @param theShieldHealth hit points of the shield
         */
        public Builder shield(int theShieldType, int theShieldHealth) {
            definition.mShieldType = theShieldType;
            definition.mShieldHealth = theShieldHealth;
            return this;
        }

        /** Health of a flying accessory, such as a balloon; {@code 0} means the zombie does not fly. */
        public Builder flyingHealth(int theFlyingHealth) {
            definition.mFlyingHealth = theFlyingHealth;
            return this;
        }

        /** Collision rectangle; {@code null} (the default) keeps the plain-zombie rectangle. */
        public Builder collider(Rect theRect) {
            definition.mZombieRect = theRect == null ? null : new Rect(theRect.mX, theRect.mY, theRect.mWidth, theRect.mHeight);
            return this;
        }

        /** Eating rectangle; {@code null} (the default) keeps the plain-zombie rectangle. */
        public Builder attackRect(Rect theRect) {
            definition.mZombieAttackRect = theRect == null ? null
                    : new Rect(theRect.mX, theRect.mY, theRect.mWidth, theRect.mHeight);
            return this;
        }

        /** Render scale, e.g. {@code 0.8f} for a smaller zombie. */
        public Builder scale(float theScale) {
            definition.mScaleZombie = theScale;
            return this;
        }

        /** Whether the zombie may walk through a pool row; pool rows reject everything else by default. */
        public Builder canGoInPool(boolean theCanGoInPool) {
            definition.mCanGoInPool = theCanGoInPool;
            return this;
        }

        /**
         * Makes the zombie take part in wave composition and grave-rising picks: the definition row
         * carries the numbers below, and a non-zero pick weight is what the wave composer's
         * {@code BoardWaves::PickZombieType} looks for.
         *
         * @param thePoints {@code ZombieDefinition#mZombieValue}, what the zombie costs the wave
         *     composer
         * @param theStartingLevel first adventure level the zombie may appear on
         * @param theFirstAllowedWave first wave the zombie may appear in
         * @param thePickWeight relative spawn weight
         */
        public Builder wave(int thePoints, int theStartingLevel, int theFirstAllowedWave, int thePickWeight) {
            definition.mZombieValue = thePoints;
            definition.mStartingLevel = theStartingLevel;
            definition.mFirstAllowedWave = theFirstAllowedWave;
            definition.mPickWeight = thePickWeight;
            return this;
        }

        /** Sets only the relative spawn weight. */
        public Builder pickWeight(int thePickWeight) {
            definition.mPickWeight = thePickWeight;
            return this;
        }

        /** Sets only the first adventure level the zombie may appear on. */
        public Builder startingLevel(int theStartingLevel) {
            definition.mStartingLevel = theStartingLevel;
            return this;
        }

        /** Value the zombie costs the wave composer. */
        public Builder points(int thePoints) {
            definition.mZombieValue = thePoints;
            return this;
        }

        /** Callback run once per zombie tick, after the zombie's own update. */
        public Builder onTick(Consumer<cn.enaium.fabricpvz.portable.lawn.Zombie> theCallback) {
            tick = theCallback;
            return this;
        }

        /**
         * Callback run at the end of the zombie's initialisation.
         *
         * <p>It runs again, for every zombie of the registered type, whenever a mid-level save is
         * loaded ({@code Board::loadGame}), because the save carries the zombie's fields and
         * reanimation ids but not the per-track {@code mImageOverride} a mod painted on those
         * reanimations: the callback is what rebuilds the art after a load. It must therefore be
         * <b>idempotent</b> and must not accumulate one-shot state.
         */
        public Builder onSpawned(Consumer<cn.enaium.fabricpvz.portable.lawn.Zombie> theCallback) {
            spawned = theCallback;
            return this;
        }

        /**
         * Registers the zombie with the game and returns the finished type.
         *
         * @return the registered type, whose {@link ZombieType#zombieType()} is the {@code ZombieType}
         *     value the game uses
         */
        public ZombieType register() {
            return ZombieRegistry.register(this);
        }

        String id() {
            return id;
        }

        private CustomZombieType.HeadAttachment head() {
            if (definition.mHead == null) {
                definition.mHead = new CustomZombieType.HeadAttachment();
            }
            return definition.mHead;
        }

        CustomZombieType build() {
            definition.mBehaviour = new ZombieBehaviour() {
                @Override
                public void onUpdate(cn.enaium.fabricpvz.portable.lawn.Zombie theZombie) {
                    CustomZombieEvents.TICK.invoker().onTick(theZombie);

                    if (tick != null) {
                        tick.accept(theZombie);
                    }
                }

                @Override
                public void onSpawned(cn.enaium.fabricpvz.portable.lawn.Zombie theZombie) {
                    CustomZombieEvents.SPAWNED.invoker().onSpawned(theZombie);

                    if (spawned != null) {
                        spawned.accept(theZombie);
                    }
                }
            };
            return definition;
        }
    }

    private final String id;
    private final CustomZombieType definition;

    ZombieType(String theId, CustomZombieType theDefinition) {
        id = theId;
        definition = theDefinition;
    }

    /**
     * Starts describing a zombie.
     *
     * @param theId a stable identifier used for the string-file key stem and in diagnostics; the
     *     game's own {@code ZombieType} value is assigned at {@link Builder#register() registration}
     *     time and is available from {@link #zombieType()}
     * @return the builder
     */
    public static Builder builder(String theId) {
        if (theId == null || theId.isBlank()) {
            throw new IllegalArgumentException("A zombie needs a non-blank id");
        }
        return new Builder(theId);
    }

    /** @return the id this zombie was registered with. */
    public String id() {
        return id;
    }

    /**
     * @return the game's {@code ZombieType} value for this zombie, i.e. what
     *     {@code Board::AddZombieInRow} takes
     */
    public int zombieType() {
        return definition.mZombieType;
    }

    /** @return the game-side definition the game reads, including its {@code ZombieDefinition} row. */
    public CustomZombieType definition() {
        return definition;
    }

    @Override
    public String toString() {
        return "ZombieType[" + id + ", zombieType=" + definition.mZombieType + "]";
    }
}
