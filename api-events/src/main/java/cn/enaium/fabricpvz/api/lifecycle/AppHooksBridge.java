package cn.enaium.fabricpvz.api.lifecycle;

import cn.enaium.fabricpvz.portable.sexy.hooks.AppHooks;
import cn.enaium.fabricpvz.portable.sexy.hooks.AppHooksRegistry;

/**
 * Bridges the framework's {@link AppHooks} into the Fabric-style {@link GameLifecycleEvents}.
 * Installed by {@link cn.enaium.fabricpvz.api.ApiEventsInitializer}.
 */
public final class AppHooksBridge implements AppHooks {

    private static AppHooksBridge sInstance;

    private AppHooksBridge() {
    }

    public static synchronized AppHooksBridge install() {
        if (sInstance == null) {
            sInstance = new AppHooksBridge();
            AppHooksRegistry.add(sInstance);
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

        AppHooksRegistry.remove(sInstance);
        sInstance = null;
        return true;
    }

    @Override
    public void onGameInitialized() {
        GameLifecycleEvents.INITIALIZED.invoker().onInitialize();
    }

    @Override
    public void onGameExiting() {
        GameLifecycleEvents.EXITING.invoker().onExit();
    }
}
