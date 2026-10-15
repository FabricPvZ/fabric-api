package cn.enaium.fabricpvz.api.plant;

import java.util.function.Function;

import cn.enaium.fabricpvz.portable.lawn.Plant;
import cn.enaium.fabricpvz.api.event.Event;
import cn.enaium.fabricpvz.api.event.EventFactory;

/**
 * Events fired for the plants mods registered, so a mod can observe <em>another</em> mod's content
 * without having registered it itself.
 *
 * <p>These are Fabric-shaped events ({@link Event}, created with
 * {@link EventFactory#createArrayBacked(Class, Function)}), and the game calls them from the same
 * sites as {@code Plant::Update} and {@code Plant::PlantInitialize}. They only fire for seed types
 * at or above {@code SeedType.CUSTOM_SEED_FIRST}: upstream plants never reach the mod code path.
 *
 * <p>Per-type behaviour is registered on the {@link PlantType.Builder} that owns the plant; these
 * events are the global counterpart.
 */
public final class CustomPlantEvents {

    private CustomPlantEvents() {
    }

    /**
     * One tick of a mod plant, fired from {@code Plant::Update} after the plant's own update ran and
     * before the board moves on.
     */
    public static final Event<Tick> TICK = EventFactory.createArrayBacked(Tick.class,
            callbacks -> plant -> {
                for (Tick callback : callbacks) {
                    callback.onTick(plant);
                }
            });

    @FunctionalInterface
    public interface Tick {
        void onTick(Plant plant);
    }

    /**
     * A mod plant was placed on a board, fired from {@code Plant::PlantInitialize} through
     * {@code Board::AddPlant} after the plant is in the grid and its health has been applied.
     */
    public static final Event<Placed> PLACED = EventFactory.createArrayBacked(Placed.class,
            callbacks -> (plant, gridX, gridY) -> {
                for (Placed callback : callbacks) {
                    callback.onPlaced(plant, gridX, gridY);
                }
            });

    @FunctionalInterface
    public interface Placed {
        void onPlaced(Plant plant, int gridX, int gridY);
    }
}
