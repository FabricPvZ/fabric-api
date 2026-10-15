package cn.enaium.fabricpvz.api.plant;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import cn.enaium.fabricpvz.portable.lawn.Plant;
import cn.enaium.fabricpvz.portable.lawn.SeedType;
import cn.enaium.fabricpvz.portable.lawn.content.CustomPlantType;
import cn.enaium.fabricpvz.portable.lawn.content.PlantTypeRegistry;

/**
 * Registry of the plants this build of the game knows about: upstream {@code SeedType} rows plus
 * whatever mods registered through {@link PlantType#builder(String)}.
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
 * <p>This class is the mod-facing view of
 * {@link cn.enaium.fabricpvz.portable.lawn.content.PlantTypeRegistry}, the game-side table the game
 * itself reads. Both exist because the game must not depend on the mod loader: the game owns the
 * data, this module owns the API shape.
 */
public final class PlantRegistry {

    private static final Object LOCK = new Object();
    private static final Map<Integer, PlantType> BY_SEED_TYPE = new LinkedHashMap<>();
    private static final Map<String, PlantType> BY_ID = new LinkedHashMap<>();

    private PlantRegistry() {
    }

    /**
     * Registers a plant described by a builder.
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

            CustomPlantType definition = theBuilder.build();
            int seedType = PlantTypeRegistry.register(definition);
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
     * <p>Plants already placed keep working; only future lookups stop resolving the seed type.
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
            PlantTypeRegistry.unregister(theSeedType);
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
        return theSeedType >= SeedType.CUSTOM_SEED_FIRST && theSeedType < SeedType.MAX_SEED_TYPES;
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
            BY_SEED_TYPE.clear();
            BY_ID.clear();
            PlantTypeRegistry.reset();
            PlantRules.uninstall();
        }
    }
}
