package cn.enaium.fabricpvz.api.zombie;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import cn.enaium.fabricpvz.portable.lawn.Board;
import cn.enaium.fabricpvz.portable.lawn.Zombie;
import cn.enaium.fabricpvz.portable.lawn.ZombieDefinition;

/**
 * Registry of the zombies this build of the game knows about: upstream {@code ZombieType} rows plus
 * whatever mods registered through {@link ZombieType#builder(String)}.
 *
 * <pre>{@code
 * ZombieType runner = ZombieType.builder("runner")
 *         .name("Runner Zombie").health(400)
 *         .wave(2, 1, 5, 2000)
 *         .register();
 *
 * Zombie zombie = ZombieRegistry.spawn(board, runner.zombieType(), 2, 0);
 * }</pre>
 *
 * <p>Upstream, everything the game knows about a zombie lives in {@code Zombie::gZombieDefs}
 * (Zombie.cpp:80) plus a very long {@code switch} in {@code Zombie::ZombieInitialize}
 * (Zombie.cpp:143). IDs from {@link #FIRST_ZOMBIE_TYPE} upwards belong to mods, and registering writes
 * the definition row into that table at its id, which is where the wave composer reads it; the
 * per-type setup and the behaviour are dispatched through {@link ZombieHooks} and this module's
 * Zombie Mixins.
 *
 * <p>Id slots are allocated in registration order and never reused after
 * {@link #unregister(int)}, so a zombie type handed to a mod keeps pointing at the same zombie for the
 * whole process lifetime.
 */
public final class ZombieRegistry {

    /**
     * First zombie type this API hands out. Every id from here to
     * {@link cn.enaium.fabricpvz.portable.lawn.ZombieType#MAX_ZOMBIE_TYPES} is outside the retail
     * zombie range, and it must stay below that constant for the table to have a row for it.
     */
    public static final int FIRST_ZOMBIE_TYPE = 64;

    private static final Object LOCK = new Object();
    private static final Map<Integer, ZombieType> BY_ZOMBIE_TYPE = new LinkedHashMap<>();
    private static final Map<String, ZombieType> BY_ID = new LinkedHashMap<>();
    private static int firstFree = FIRST_ZOMBIE_TYPE;

    private ZombieRegistry() {
    }

    /**
     * Registers a zombie described by a builder: assigns it a zombie type and writes its
     * {@link ZombieDefinition} row into {@code Zombie.gZombieDefs}.
     *
     * @param theBuilder the builder of the zombie
     * @return the registered zombie
     * @throws IllegalStateException if the id is already registered, or no zombie type is free
     */
    static ZombieType register(ZombieType.Builder theBuilder) {
        String id = theBuilder.id();

        synchronized (LOCK) {
            if (BY_ID.containsKey(id)) {
                throw new IllegalStateException("A zombie with id '" + id + "' is already registered");
            }
            if (firstFree >= cn.enaium.fabricpvz.portable.lawn.ZombieType.MAX_ZOMBIE_TYPES) {
                throw new IllegalStateException("No free zombie type: all "
                        + (cn.enaium.fabricpvz.portable.lawn.ZombieType.MAX_ZOMBIE_TYPES - FIRST_ZOMBIE_TYPE)
                        + " custom zombie types are registered");
            }

            CustomZombieType definition = theBuilder.build();
            int zombieType = firstFree;
            firstFree = zombieType + 1;
            definition.mZombieType = zombieType;
            definition.mDefinition = new ZombieDefinition(
                    zombieType,
                    definition.mBodyReanimationType,
                    definition.mZombieValue,
                    definition.mStartingLevel,
                    definition.mFirstAllowedWave,
                    definition.mPickWeight,
                    definition.mStringKeyStem.isEmpty() ? ("CUSTOM_ZOMBIE_" + zombieType) : definition.mStringKeyStem);
            Zombie.gZombieDefs[zombieType] = definition.mDefinition;

            ZombieType type = new ZombieType(id, definition);
            BY_ZOMBIE_TYPE.put(zombieType, type);
            BY_ID.put(id, type);
            return type;
        }
    }

    /**
     * Unregisters a zombie registered by this API.
     *
     * <p>Zombies already spawned keep working through the {@link CustomZombieType} instance they were
     * created from; only future lookups stop resolving the type.
     *
     * @param theZombieType the zombie type to drop
     * @return whether a zombie was registered under that type through this API
     */
    public static boolean unregister(int theZombieType) {
        synchronized (LOCK) {
            ZombieType type = BY_ZOMBIE_TYPE.remove(theZombieType);
            if (type == null) {
                return false;
            }

            BY_ID.remove(type.id());
            Zombie.gZombieDefs[theZombieType] = null;
            return true;
        }
    }

    /**
     * @param theZombieType a zombie type
     * @return the registered zombie, empty for an upstream type or for one nobody registered
     */
    public static Optional<ZombieType> get(int theZombieType) {
        synchronized (LOCK) {
            return Optional.ofNullable(BY_ZOMBIE_TYPE.get(theZombieType));
        }
    }

    /**
     * @param theId the id passed to {@link ZombieType#builder(String)}
     * @return the registered zombie, empty when no zombie has that id
     */
    public static Optional<ZombieType> get(String theId) {
        synchronized (LOCK) {
            return Optional.ofNullable(BY_ID.get(theId));
        }
    }

    /**
     * @return every zombie registered through this API, in registration order
     */
    public static List<ZombieType> all() {
        synchronized (LOCK) {
            return List.copyOf(BY_ZOMBIE_TYPE.values());
        }
    }

    /**
     * @return how many zombies are registered through this API
     */
    public static int count() {
        synchronized (LOCK) {
            return BY_ZOMBIE_TYPE.size();
        }
    }

    /**
     * @param theZombieType a zombie type
     * @return whether the zombie type is in the range the loader reserves for mods, whether or not
     *     anything is registered under it
     */
    public static boolean isCustomZombieType(int theZombieType) {
        return theZombieType >= FIRST_ZOMBIE_TYPE
                && theZombieType < cn.enaium.fabricpvz.portable.lawn.ZombieType.MAX_ZOMBIE_TYPES;
    }

    /**
     * Spawns a zombie of any type on a board, going through the same path the game's wave spawner
     * uses ({@code Board::AddZombieInRow}, Board.cpp:2609).
     *
     * @param theBoard the board
     * @param theZombieType a zombie type, upstream or registered
     * @param theRow the lane to spawn in
     * @param theFromWave the wave the zombie belongs to, or {@code Zombie.ZOMBIE_WAVE_UI} for a
     *     spawn that is not part of a wave
     * @return the spawned zombie, or {@code null} when the board is full or a listener cancelled it
     */
    public static Zombie spawn(Board theBoard, int theZombieType, int theRow, int theFromWave) {
        return theBoard.addZombieInRow(theZombieType, theRow, theFromWave);
    }

    /** Wave constant for a spawn that does not belong to a wave; see {@code Zombie.ZOMBIE_WAVE_UI}. */
    public static final int WAVE_UI = Zombie.ZOMBIE_WAVE_UI;

    /**
     * Clears the registry; used by tests and by a loader that reloads mods in-process.
     */
    public static void reset() {
        synchronized (LOCK) {
            for (ZombieType type : BY_ZOMBIE_TYPE.values()) {
                Zombie.gZombieDefs[type.zombieType()] = null;
            }
            BY_ZOMBIE_TYPE.clear();
            BY_ID.clear();
            firstFree = FIRST_ZOMBIE_TYPE;
        }
    }

    /**
     * @param theZombieType a zombie type
     * @return the registered definition, or {@code null}; used by {@link ZombieHooks} from the Mixins
     */
    static CustomZombieType entry(int theZombieType) {
        synchronized (LOCK) {
            ZombieType type = BY_ZOMBIE_TYPE.get(theZombieType);
            return type == null ? null : type.definition();
        }
    }
}
