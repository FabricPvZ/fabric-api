package cn.enaium.fabricpvz.api.event;

/**
 * Wraps an exception thrown by an event listener, so the failure names the listener instead of
 * surfacing as an anonymous exception out of an event dispatch.
 *
 * <p>The original exception is kept as the {@linkplain #getCause() cause}: the dispatch is not
 * corrupted, the event keeps its listeners, and every later dispatch still works.
 */
public class ListenerException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	private final Class<?> eventType;
	private final Object listener;

	ListenerException(Class<?> eventType, Object listener, Throwable cause) {
		super("Listener " + listener + " of event " + eventType.getName() + " threw " + cause, cause);
		this.eventType = eventType;
		this.listener = listener;
	}

	/**
	 * The listener interface of the event that was being dispatched.
	 */
	public Class<?> eventType() {
		return this.eventType;
	}

	/**
	 * The listener that threw, as registered.
	 */
	public Object listener() {
		return this.listener;
	}
}
