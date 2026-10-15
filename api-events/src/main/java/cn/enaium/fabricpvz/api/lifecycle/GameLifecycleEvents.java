package cn.enaium.fabricpvz.api.lifecycle;

import cn.enaium.fabricpvz.api.lawn.LawnEvents;
import cn.enaium.fabricpvz.api.event.Event;
import cn.enaium.fabricpvz.api.event.EventFactory;

/**
 * Lifecycle events fired by the game itself.
 *
 * <p>Both events fire exactly once per process, on the main (window/render) thread, in the order
 * {@link #INITIALIZED} then {@link #EXITING}. Listeners therefore run on the same thread as the game
 * loop and must not block it.
 *
 * <p>Registration is only useful before the corresponding call point; a listener registered while
 * the game is already running still receives every later dispatch (so a listener added after
 * {@link #INITIALIZED} fired will never see it, but will see {@link #EXITING}).
 *
 * <p>Like the gameplay events of {@link LawnEvents} these are Fabric-shaped events, created with
 * {@code EventFactory.createArrayBacked} from {@code cn.enaium.fabricpvz.api.event}.
 */
public final class GameLifecycleEvents {

	/**
	 * Fired once when the game application has completed initialization.
	 *
	 * <p>This is the call point at the end of the framework startup sequence: the game has already
	 * parsed its command line, resolved the resource directory, loaded the {@code Init} resource
	 * group (images, sounds, fonts, reanimation definitions) and created the GL interface, and it is
	 * about to enter the main loop. The window and the main menu are not necessarily displayed yet.
	 *
	 * <p>Typical uses: registering custom content, preparing state that the first frame needs.
	 */
	public static final Event<Initialize> INITIALIZED = EventFactory.createArrayBacked(Initialize.class,
			callbacks -> () -> {
				for (Initialize callback : callbacks) {
					callback.onInitialize();
				}
			});

	/**
	 * Fired once when the game begins shutting down, just before resources and the window are torn
	 * down (the framework's shutdown hook runs after this event). The game may be exiting normally,
	 * be aborted by a fatal error, or be closed by the user.
	 *
	 * <p>Typical uses: flushing or saving mod state, releasing non-game resources. Listeners must not
	 * assume the game is still able to draw or play sound.
	 */
	public static final Event<Exit> EXITING = EventFactory.createArrayBacked(Exit.class,
			callbacks -> () -> {
				for (Exit callback : callbacks) {
					callback.onExit();
				}
			});

	private GameLifecycleEvents() {
	}

	/**
	 * Callback of {@link #INITIALIZED}.
	 */
	@FunctionalInterface
	public interface Initialize {

		/**
		 * Invoked once the game has finished initializing.
		 */
		void onInitialize();
	}

	/**
	 * Callback of {@link #EXITING}.
	 */
	@FunctionalInterface
	public interface Exit {

		/**
		 * Invoked once the game starts shutting down.
		 */
		void onExit();
	}
}
