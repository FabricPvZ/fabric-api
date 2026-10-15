package cn.enaium.fabricpvz.api.lawn;

import java.util.function.Function;

import cn.enaium.fabricpvz.portable.lawn.Board;
import cn.enaium.fabricpvz.portable.lawn.Plant;
import cn.enaium.fabricpvz.portable.lawn.Zombie;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;
import cn.enaium.fabricpvz.api.event.Event;
import cn.enaium.fabricpvz.api.event.EventFactory;

/**
 * Fabric-style events for gameplay. Fired by the game's
 * {@code cn.enaium.fabricpvz.portable.lawn.hooks.LawnHooks}, which the game calls at the sites
 * documented in {@code docs/research/03-board-core.md §7.4}; the bridge is installed by
 * {@link LawnHooksBridge} from this module's {@code main} entry point.
 *
 * <p>The events are Fabric-shaped ({@link Event} from {@code cn.enaium.fabricpvz.api.event}, our own
 * copy of the Fabric API base surface), created with
 * {@link EventFactory#createArrayBacked(Class, Function)}.
 *
 * <h2>Value chaining</h2>
 * A value-returning event is a <em>pipeline</em>: listeners run in registration order, each one
 * receives the value produced by the previous listener and returns the value the next one sees. With
 * no listeners at all the value passed in comes straight back out, so an API with no mods listening
 * never changes the game's behaviour. Cancellable (boolean) events are OR-ed the same way the game's
 * own {@code LawnHooksRegistry} dispatcher OR-s its hooks. A failing listener is not swallowed
 * either: it propagates out of the game hook call wrapped in a
 * {@link cn.enaium.fabricpvz.api.event.ListenerException} that names the listener.
 */
public final class LawnEvents {

    private LawnEvents() {
    }

    /** End of level initialisation; the board is fully built and the level is about to run. */
    public static final Event<LevelStart> LEVEL_START = EventFactory.createArrayBacked(LevelStart.class,
            callbacks -> (board, level, gameMode, background) -> {
                for (LevelStart callback : callbacks) {
                    callback.onLevelStart(board, level, gameMode, background);
                }
            });

    @FunctionalInterface
    public interface LevelStart {
        void onLevelStart(Board board, int level, int gameMode, int background);
    }

    /** Level finished (win or loss) before the fade-out. */
    public static final Event<LevelEnd> LEVEL_END = EventFactory.createArrayBacked(LevelEnd.class,
            callbacks -> (board, result) -> {
                for (LevelEnd callback : callbacks) {
                    callback.onLevelEnd(board, result);
                }
            });

    @FunctionalInterface
    public interface LevelEnd {
        void onLevelEnd(Board board, int result);
    }

    /** A zombie wave is about to spawn; return true to cancel it. */
    public static final Event<WaveStart> WAVE_START = EventFactory.createArrayBacked(WaveStart.class,
            callbacks -> (board, waveIndex, zombieTypes) -> {
                boolean cancelled = false;

                for (WaveStart callback : callbacks) {
                    cancelled |= callback.onWaveStart(board, waveIndex, zombieTypes);
                }

                return cancelled;
            });

    @FunctionalInterface
    public interface WaveStart {
        boolean onWaveStart(Board board, int waveIndex, int[] zombieTypes);
    }

    /** A zombie is about to be placed on the board; return true to cancel the spawn. */
    public static final Event<ZombieSpawn> ZOMBIE_SPAWN = EventFactory.createArrayBacked(ZombieSpawn.class,
            callbacks -> (board, zombieType, row, fromWave) -> {
                boolean cancelled = false;

                for (ZombieSpawn callback : callbacks) {
                    cancelled |= callback.onZombieSpawn(board, zombieType, row, fromWave);
                }

                return cancelled;
            });

    @FunctionalInterface
    public interface ZombieSpawn {
        boolean onZombieSpawn(Board board, int zombieType, int row, boolean fromWave);
    }

    /** A zombie died (loot drops already resolved by the game). */
    public static final Event<ZombieDeath> ZOMBIE_DEATH = EventFactory.createArrayBacked(ZombieDeath.class,
            callbacks -> (board, zombie) -> {
                for (ZombieDeath callback : callbacks) {
                    callback.onZombieDeath(board, zombie);
                }
            });

    @FunctionalInterface
    public interface ZombieDeath {
        void onZombieDeath(Board board, Zombie zombie);
    }

    /** Damage about to be applied to a zombie; return the amount to use instead. */
    public static final Event<ZombieDamage> ZOMBIE_DAMAGE = EventFactory.createArrayBacked(ZombieDamage.class,
            callbacks -> (board, zombie, amount, damageFlags) -> {
                int value = amount;

                for (ZombieDamage callback : callbacks) {
                    value = callback.onZombieDamage(board, zombie, value, damageFlags);
                }

                return value;
            });

    @FunctionalInterface
    public interface ZombieDamage {
        int onZombieDamage(Board board, Zombie zombie, int amount, int damageFlags);
    }

    /** A plant was placed in the grid. */
    public static final Event<PlantPlaced> PLANT_PLACED = EventFactory.createArrayBacked(PlantPlaced.class,
            callbacks -> (board, plant, gridX, gridY) -> {
                for (PlantPlaced callback : callbacks) {
                    callback.onPlantPlaced(board, plant, gridX, gridY);
                }
            });

    @FunctionalInterface
    public interface PlantPlaced {
        void onPlantPlaced(Board board, Plant plant, int gridX, int gridY);
    }

    /** A plant was removed (eaten, shoveled, exploded, ...); {@code cause} is the game's reason code. */
    public static final Event<PlantRemoved> PLANT_REMOVED = EventFactory.createArrayBacked(PlantRemoved.class,
            callbacks -> (board, plant, cause) -> {
                for (PlantRemoved callback : callbacks) {
                    callback.onPlantRemoved(board, plant, cause);
                }
            });

    @FunctionalInterface
    public interface PlantRemoved {
        void onPlantRemoved(Board board, Plant plant, int cause);
    }

    /** Damage about to be applied to a plant; return the amount to use instead. */
    public static final Event<PlantDamage> PLANT_DAMAGE = EventFactory.createArrayBacked(PlantDamage.class,
            callbacks -> (board, plant, amount) -> {
                int value = amount;

                for (PlantDamage callback : callbacks) {
                    value = callback.onPlantDamage(board, plant, value);
                }

                return value;
            });

    @FunctionalInterface
    public interface PlantDamage {
        int onPlantDamage(Board board, Plant plant, int amount);
    }

    /** Sun (or a coin value) is about to be credited; return the value to credit. */
    public static final Event<SunCollected> SUN_COLLECTED = EventFactory.createArrayBacked(SunCollected.class,
            callbacks -> (board, amount, source) -> {
                int value = amount;

                for (SunCollected callback : callbacks) {
                    value = callback.onSunCollected(board, value, source);
                }

                return value;
            });

    @FunctionalInterface
    public interface SunCollected {
        int onSunCollected(Board board, int amount, int source);
    }

    /**
     * Planting validation finished; {@code reason} is a {@code PlantingReason} value. Return
     * {@code PlantingReason_OK} to force-allow the placement or any other reason to veto it.
     */
    public static final Event<PlantingValidated> PLANTING_VALIDATED = EventFactory.createArrayBacked(PlantingValidated.class,
            callbacks -> (board, gridX, gridY, seedType, reason) -> {
                int value = reason;

                for (PlantingValidated callback : callbacks) {
                    value = callback.onPlantingValidated(board, gridX, gridY, seedType, value);
                }

                return value;
            });

    @FunctionalInterface
    public interface PlantingValidated {
        int onPlantingValidated(Board board, int gridX, int gridY, int seedType, int reason);
    }

    /** One gameplay tick (100 per second) after the board counter advanced. */
    public static final Event<Tick> TICK = EventFactory.createArrayBacked(Tick.class,
            callbacks -> (board, tick) -> {
                for (Tick callback : callbacks) {
                    callback.onTick(board, tick);
                }
            });

    @FunctionalInterface
    public interface Tick {
        void onTick(Board board, int tick);
    }

    /** Before the board's object passes are drawn. */
    public static final Event<PreDraw> PRE_DRAW = EventFactory.createArrayBacked(PreDraw.class,
            callbacks -> (board, graphics) -> {
                for (PreDraw callback : callbacks) {
                    callback.onPreDraw(board, graphics);
                }
            });

    @FunctionalInterface
    public interface PreDraw {
        void onPreDraw(Board board, Graphics graphics);
    }

    /** After the board's object passes were drawn. */
    public static final Event<PostDraw> POST_DRAW = EventFactory.createArrayBacked(PostDraw.class,
            callbacks -> (board, graphics) -> {
                for (PostDraw callback : callbacks) {
                    callback.onPostDraw(board, graphics);
                }
            });

    @FunctionalInterface
    public interface PostDraw {
        void onPostDraw(Board board, Graphics graphics);
    }
}
