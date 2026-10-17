package cn.enaium.fabricpvz.api.plant;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import cn.enaium.fabricpvz.portable.lawn.Plant;
import cn.enaium.fabricpvz.portable.lawn.PlantDefinition;
import cn.enaium.fabricpvz.portable.lawn.SeedType;

/**
 * Registry of the plants a mod registers.
 *
 * <p>Registration is a plain method call, usable from a mod's {@code ModInitializer} (or from a
 * static initializer) with no reflection on the mod's side:
 *
 * <pre>{@code
 * PlantType peaPlus = PlantType.builder("peashooter_plus")
 *         .name("Pea Shooter+").cost(125).reanimation(ReanimationType.REANIM_REPEATER)
 *         .register();
 * int seedType = peaPlus.seedType();
 * }</pre>
 *
 * <p>Upstream, everything the game knows about a plant lives in {@code Plant::gPlantDefs}
 * (Plant.cpp:48), a table whose index <em>is</em> the {@code SeedType}. IDs from
 * {@link #FIRST_SEED_TYPE} upwards belong to mods, and registering writes the plant's definition row
 * into that table at its id, which is where the game reads it — the game itself has no registry of
 * its own. The per-plant behaviour beyond the row (the health override, the tick, the collision
 * rectangles, the seed availability) is dispatched through {@link PlantHooks} and this module's
 * Mixins.
 *
 * <p>Id slots are allocated in registration order and never reused after
 * {@link #unregister(int)}, so a seed type handed to a mod keeps pointing at the same plant for the
 * whole process lifetime.
 *
 * <p>Registration happens while the loader runs the mods' entry points, i.e. before the game main
 * class is even loaded; reads happen on the game thread. Writes go to the table before anything reads
 * it and a registered row is treated as immutable, so a read never observes a half-registered plant.
 */
public final class PlantRegistry {

    /**
     * First seed type this API hands out. Every id from here to {@link SeedType#MAX_SEED_TYPES} is
     * outside the retail seed range, and it must stay below {@link SeedType#MAX_SEED_TYPES} for the
     * table to have a row for it.
     */
    public static final int FIRST_SEED_TYPE = 100;

    private static final Object LOCK = new Object();
    private static final Map<Integer, PlantType> BY_SEED_TYPE = new LinkedHashMap<>();
    private static final Map<String, PlantType> BY_ID = new LinkedHashMap<>();
    private static int firstFree = FIRST_SEED_TYPE;

    private PlantRegistry() {
    }

    /**
     * Registers a plant described by a builder: assigns it a seed type, writes its
     * {@link PlantDefinition} row into {@code Plant.gPlantDefs} and installs the planting-rule hook.
     *
     * @param theBuilder the builder of the plant
     * @return the registered plant
     * @throws IllegalStateException if the id is already registered, or no seed type is free
     */
    static PlantType register(PlantType.Builder theBuilder) {
        String id = theBuilder.id();

        synchronized (LOCK) {
            if (BY_ID.containsKey(id)) {
                throw new IllegalStateException("A plant with id '" + id + "' is already registered");
            }
            if (firstFree >= SeedType.MAX_SEED_TYPES) {
                throw new IllegalStateException("No free seed type: all "
                        + (SeedType.MAX_SEED_TYPES - FIRST_SEED_TYPE) + " custom seed types are registered");
            }

            CustomPlantType definition = theBuilder.build();
            int seedType = firstFree;
            firstFree = seedType + 1;
            definition.mSeedType = seedType;
            definition.mDefinition = new PlantDefinition(
                    seedType,
                    null,
                    definition.mReanimationType,
                    0,
                    definition.mSeedCost,
                    definition.mRefreshTime,
                    definition.mSubClass,
                    definition.mLaunchRate,
                    definition.mStringKeyStem.isEmpty() ? ("CUSTOM_PLANT_" + seedType) : definition.mStringKeyStem);
            Plant.gPlantDefs[seedType] = definition.mDefinition;

            PlantType type = new PlantType(id, definition, theBuilder);
            BY_SEED_TYPE.put(seedType, type);
            BY_ID.put(id, type);
            PlantRules.install();
            return type;
        }
    }

    /**
     * Unregisters a plant registered by this API.
     *
     * <p>Plants already placed keep working through the {@link CustomPlantType} instance they were
     * created from; only future lookups stop resolving the seed type.
     *
     * @param theSeedType the seed type to drop
     * @return whether a plant was registered under that seed type through this API
     */
    public static boolean unregister(int theSeedType) {
        synchronized (LOCK) {
            PlantType type = BY_SEED_TYPE.remove(theSeedType);
            if (type == null) {
                return false;
            }

            BY_ID.remove(type.id());
            Plant.gPlantDefs[theSeedType] = null;
            if (BY_SEED_TYPE.isEmpty()) {
                // No mod plant left: take the planting-rule hook out again so an unmodded game has
                // exactly the hooks it started with.
                PlantRules.uninstall();
            }
            return true;
        }
    }

    /**
     * @param theSeedType a seed type
     * @return the registered plant, empty for an upstream seed type or for one nobody registered
     */
    public static Optional<PlantType> get(int theSeedType) {
        synchronized (LOCK) {
            return Optional.ofNullable(BY_SEED_TYPE.get(theSeedType));
        }
    }

    /**
     * @param theId the id passed to {@link PlantType#builder(String)}
     * @return the registered plant, empty when no plant has that id
     */
    public static Optional<PlantType> get(String theId) {
        synchronized (LOCK) {
            return Optional.ofNullable(BY_ID.get(theId));
        }
    }

    /**
     * @return every plant registered through this API, in registration order
     */
    public static List<PlantType> all() {
        synchronized (LOCK) {
            return List.copyOf(BY_SEED_TYPE.values());
        }
    }

    /**
     * @return how many plants are registered through this API
     */
    public static int count() {
        synchronized (LOCK) {
            return BY_SEED_TYPE.size();
        }
    }

    /**
     * @param theSeedType a seed type
     * @return whether the seed type is in the range the loader reserves for mods, whether or not
     *     anything is registered under it
     */
    public static boolean isCustomSeedType(int theSeedType) {
        return theSeedType >= FIRST_SEED_TYPE && theSeedType < SeedType.MAX_SEED_TYPES;
    }

    /**
     * Whether a seed type can be placed on a board at the given square, including the planting rules
     * mods registered for their own plants.
     *
     * @param theBoard the board
     * @param theGridX the target column
     * @param theGridY the target row
     * @param theSeedType a seed type
     * @return {@code true} when the game would allow the placement
     */
    public static boolean canPlantAt(cn.enaium.fabricpvz.portable.lawn.Board theBoard, int theGridX, int theGridY,
            int theSeedType) {
        return theBoard.canPlantAt(theGridX, theGridY, theSeedType)
                == cn.enaium.fabricpvz.portable.lawn.PlantingReason.PLANTING_OK;
    }

    /**
     * Places a plant on a board without going through a seed packet; the board still runs its own
     * planting rules.
     *
     * @param theBoard the board
     * @param theGridX the target column
     * @param theGridY the target row
     * @param theSeedType a seed type, upstream or registered
     * @return the placed plant
     */
    public static Plant place(cn.enaium.fabricpvz.portable.lawn.Board theBoard, int theGridX, int theGridY,
            int theSeedType) {
        return theBoard.addPlant(theGridX, theGridY, theSeedType, SeedType.SEED_NONE);
    }

    /**
     * Clears the registry; used by tests and by a loader that reloads mods in-process.
     */
    public static void reset() {
        synchronized (LOCK) {
            for (PlantType type : BY_SEED_TYPE.values()) {
                Plant.gPlantDefs[type.seedType()] = null;
            }
            BY_SEED_TYPE.clear();
            BY_ID.clear();
            firstFree = FIRST_SEED_TYPE;
            PlantRules.uninstall();
        }
    }

    /**
     * @param theSeedType a seed type
     * @return the registered definition, or {@code null}; used by {@link PlantHooks} from the Mixins
     */
    static CustomPlantType entry(int theSeedType) {
        synchronized (LOCK) {
            PlantType type = BY_SEED_TYPE.get(theSeedType);
            return type == null ? null : type.definition();
        }
    }
}
