package cn.enaium.fabricpvz.api.lawn;

import cn.enaium.fabricpvz.portable.lawn.Board;
import cn.enaium.fabricpvz.portable.lawn.Plant;
import cn.enaium.fabricpvz.portable.lawn.Zombie;
import cn.enaium.fabricpvz.portable.lawn.hooks.LawnHooks;
import cn.enaium.fabricpvz.portable.lawn.hooks.LawnHooksRegistry;
import cn.enaium.fabricpvz.portable.sexy.graphics.Graphics;

/**
 * Bridges the game's {@link LawnHooks} into the Fabric-style {@link LawnEvents}.
 *
 * <p>Registered by {@link cn.enaium.fabricpvz.api.ApiEventsInitializer} (this module's {@code main}
 * entrypoint), which the loader runs before the game starts, so every event fired by the game from
 * then on reaches the listeners registered by mods.
 */
public final class LawnHooksBridge implements LawnHooks {

    private static LawnHooksBridge sInstance;

    private LawnHooksBridge() {
    }

    /** Installs the bridge exactly once; returns the instance. */
    public static synchronized LawnHooksBridge install() {
        if (sInstance == null) {
            sInstance = new LawnHooksBridge();
            LawnHooksRegistry.add(sInstance);
        }
        return sInstance;
    }

    public static synchronized boolean isInstalled() {
        return sInstance != null;
    }

    /**
     * Removes the bridge again; used by tests and by a loader that reloads mods in-process.
     *
     * @return whether a bridge was installed
     */
    public static synchronized boolean uninstall() {
        if (sInstance == null) {
            return false;
        }

        LawnHooksRegistry.remove(sInstance);
        sInstance = null;
        return true;
    }

    @Override
    public void onLevelStart(Board board, int level, int gameMode, int background) {
        LawnEvents.LEVEL_START.invoker().onLevelStart(board, level, gameMode, background);
    }

    @Override
    public void onLevelEnd(Board board, int result) {
        LawnEvents.LEVEL_END.invoker().onLevelEnd(board, result);
    }

    @Override
    public boolean onWaveStart(Board board, int waveIndex, int[] zombieTypes) {
        return LawnEvents.WAVE_START.invoker().onWaveStart(board, waveIndex, zombieTypes);
    }

    @Override
    public boolean onZombieSpawn(Board board, int zombieType, int row, boolean fromWave) {
        return LawnEvents.ZOMBIE_SPAWN.invoker().onZombieSpawn(board, zombieType, row, fromWave);
    }

    @Override
    public void onZombieDeath(Board board, Zombie zombie) {
        LawnEvents.ZOMBIE_DEATH.invoker().onZombieDeath(board, zombie);
    }

    @Override
    public int onZombieDamage(Board board, Zombie zombie, int amount, int damageFlags) {
        return LawnEvents.ZOMBIE_DAMAGE.invoker().onZombieDamage(board, zombie, amount, damageFlags);
    }

    @Override
    public void onPlantPlaced(Board board, Plant plant, int gridX, int gridY) {
        LawnEvents.PLANT_PLACED.invoker().onPlantPlaced(board, plant, gridX, gridY);
    }

    @Override
    public void onPlantRemoved(Board board, Plant plant, int cause) {
        LawnEvents.PLANT_REMOVED.invoker().onPlantRemoved(board, plant, cause);
    }

    @Override
    public int onPlantDamage(Board board, Plant plant, int amount) {
        return LawnEvents.PLANT_DAMAGE.invoker().onPlantDamage(board, plant, amount);
    }

    @Override
    public int onSunCollected(Board board, int amount, int source) {
        return LawnEvents.SUN_COLLECTED.invoker().onSunCollected(board, amount, source);
    }

    @Override
    public int onPlantingValidated(Board board, int gridX, int gridY, int seedType, int reason) {
        return LawnEvents.PLANTING_VALIDATED.invoker().onPlantingValidated(board, gridX, gridY, seedType, reason);
    }

    @Override
    public void onTick(Board board, int tick) {
        LawnEvents.TICK.invoker().onTick(board, tick);
    }

    @Override
    public void onPreDraw(Board board, Graphics graphics) {
        LawnEvents.PRE_DRAW.invoker().onPreDraw(board, graphics);
    }

    @Override
    public void onPostDraw(Board board, Graphics graphics) {
        LawnEvents.POST_DRAW.invoker().onPostDraw(board, graphics);
    }
}
