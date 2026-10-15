package cn.enaium.fabricpvz.api.zombie;

import java.util.function.Function;

import cn.enaium.fabricpvz.portable.lawn.Zombie;
import cn.enaium.fabricpvz.api.event.Event;
import cn.enaium.fabricpvz.api.event.EventFactory;

/**
 * Events fired for the zombies mods registered, so a mod can observe <em>another</em> mod's content
 * without having registered it itself.
 *
 * <p>These are Fabric-shaped events ({@link Event}, created with
 * {@link EventFactory#createArrayBacked(Class, Function)}), fired from the same sites as
 * {@code Zombie::ZombieInitialize} and {@code Zombie::Update}. They only fire for zombie types at or
 * above {@code ZombieType.CUSTOM_ZOMBIE_FIRST}: upstream zombies never reach the mod code path.
 *
 * <p>Damage and death of every zombie, mod ones included, are reported by
 * {@code LawnEvents.ZOMBIE_DAMAGE} and {@code LawnEvents.ZOMBIE_DEATH} in {@code api-events}.
 */
public final class CustomZombieEvents {

    private CustomZombieEvents() {
    }

    /**
     * One tick of a mod zombie, fired from {@code Zombie::Update} after the zombie's own update ran.
     */
    public static final Event<Tick> TICK = EventFactory.createArrayBacked(Tick.class,
            callbacks -> zombie -> {
                for (Tick callback : callbacks) {
                    callback.onTick(zombie);
                }
            });

    @FunctionalInterface
    public interface Tick {
        void onTick(Zombie zombie);
    }

    /**
     * A mod zombie finished initialising, fired from {@code Zombie::ZombieInitialize} after the
     * registered stats and reanimation setup were applied. The zombie may still be off-board (a
     * preview or a cutscene).
     */
    public static final Event<Spawned> SPAWNED = EventFactory.createArrayBacked(Spawned.class,
            callbacks -> zombie -> {
                for (Spawned callback : callbacks) {
                    callback.onSpawned(zombie);
                }
            });

    @FunctionalInterface
    public interface Spawned {
        void onSpawned(Zombie zombie);
    }
}
