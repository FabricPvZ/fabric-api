package cn.enaium.fabricpvz.api.plant;

import cn.enaium.fabricpvz.portable.lawn.Board;
import cn.enaium.fabricpvz.portable.lawn.hooks.LawnHooks;
import cn.enaium.fabricpvz.portable.lawn.hooks.LawnHooksRegistry;

/**
 * Connects the planting rules of registered plants to the game.
 *
 * <p>{@code Board::CanPlantAt} (Board.cpp:2688) already asks
 * {@link LawnHooksRegistry#dispatcher()} for a {@code PlantingReason} before it runs the built-in
 * rules, so this module registers one {@link LawnHooks} implementation that forwards that call to
 * the rule of the seed type being planted. Upstream seed types and mod plants without a rule are
 * returned unchanged, which keeps the game on its own code path.
 *
 * <p>Installed on the first {@link PlantRegistry#register} call, so a build with no plant mods never
 * adds a hook at all.
 */
final class PlantRules implements LawnHooks {

    private static PlantRules sInstance;

    private PlantRules() {
    }

    /** Installs the rule hook exactly once; safe to call from any thread. */
    static synchronized void install() {
        if (sInstance == null) {
            sInstance = new PlantRules();
            LawnHooksRegistry.add(sInstance);
        }
    }

    /** @return whether the hook is installed; used by tests. */
    static synchronized boolean isInstalled() {
        return sInstance != null;
    }

    /** Removes the hook again; used by tests that need a clean registry. */
    static synchronized void uninstall() {
        if (sInstance != null) {
            LawnHooksRegistry.remove(sInstance);
            sInstance = null;
        }
    }

    @Override
    public int onPlantingValidated(Board board, int gridX, int gridY, int seedType, int reason) {
        PlantType type = PlantRegistry.get(seedType).orElse(null);
        if (type == null) {
            return reason;
        }

        PlantType.PlantingRule rule = type.plantingRule();
        return rule == null ? reason : rule.test(board, gridX, gridY, reason);
    }
}
