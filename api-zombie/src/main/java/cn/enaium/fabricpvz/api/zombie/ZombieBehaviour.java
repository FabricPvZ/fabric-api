package cn.enaium.fabricpvz.api.zombie;

import cn.enaium.fabricpvz.portable.lawn.Zombie;

/**
 * Behaviour callbacks of a mod-registered zombie, called by {@link ZombieHooks} from the Mixins that
 * replace the game's own custom-content branches.
 *
 * <p>The game-facing half of the zombie content extension point (see
 * {@code cn.enaium.fabricpvz.api.plant.PlantBehaviour}, in the plant module, for the same idea on
 * plants): a mod implements
 * {@link ZombieType.Builder}'s callbacks, the builder adapts them into this interface, and
 * {@link ZombieHooks} decides when they run.
 *
 * <p>Damage and death are already covered by
 * {@link cn.enaium.fabricpvz.portable.lawn.hooks.LawnHooks#onZombieDamage} and
 * {@link cn.enaium.fabricpvz.portable.lawn.hooks.LawnHooks#onZombieDeath}, which fire for every
 * zombie including mod ones, so this interface only carries the per-type hooks that need the zombie
 * itself.
 */
public interface ZombieBehaviour {

    /**
     * Called once per zombie tick while the zombie is being updated, mirroring {@code Zombie::Update}
     * (Zombie.cpp:908 onwards).
     *
     * <p>The zombie is on the board and alive when this runs; {@code zombie.mBoard} and
     * {@code zombie.mApp} are set.
     *
     * @param zombie the zombie being updated
     */
    default void onUpdate(Zombie zombie) {
    }

    /**
     * Called at the end of {@code Zombie::ZombieInitialize} (Zombie.cpp:143), after {@link ZombieHooks}
     * applied the registered stats and reanimation setup.
     *
     * <p>This is where a behaviour can start a custom animation or place the zombie in a custom
     * phase; the zombie may still be off-board (a preview or a cutscene).
     *
     * @param zombie the newly created zombie
     */
    default void onSpawned(Zombie zombie) {
    }
}
