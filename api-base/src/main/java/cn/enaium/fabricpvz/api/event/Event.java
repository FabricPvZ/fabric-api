package cn.enaium.fabricpvz.api.event;

/**
 * A typed listener registry, shaped exactly like Fabric API's {@code Event<T>} so mod code written
 * against Fabric looks and reads the same against this API.
 *
 * <p>An event is created with {@link EventFactory#createArrayBacked(Class, java.util.function.Function)},
 * which takes the listener interface and a factory that turns the current listener array into the
 * invoker the game calls. Listeners are appended with {@link #register(Object)} and the current
 * invoker is read with {@link #invoker()}.
 *
 * <p><b>Semantics.</b> Listeners are invoked in registration order. Dispatch happens over a
 * snapshot: {@link #invoker()} returns the invoker built from the listeners registered when it was
 * read, so a listener that registers another listener during a dispatch does not change the
 * invocation already in flight (the new listener is seen by the next {@link #invoker()} call).
 * An exception thrown by a listener does not damage the event; it is re-thrown wrapped in a
 * {@link ListenerException} that names the event and the failing listener.
 *
 * <p><b>Phases and ordering.</b> Fabric API's phase API ({@code addPhase}, {@code addPhaseOrdering})
 * is not implemented: nothing in this workspace uses it, and the two methods above are the whole
 * surface the API's events need.
 *
 * @param <T> the listener type (a functional interface)
 */
public interface Event<T> {

	/**
	 * Registers a listener. Listeners are called in registration order; registering the same object
	 * twice registers it twice.
	 *
	 * @param listener the listener to add, not {@code null}
	 */
	void register(T listener);

	/**
	 * Returns the invoker built from the listeners registered so far. The invoker is the object the
	 * caller-supplied factory produced; with no listeners it is the factory applied to an empty
	 * array, which is why an event nobody listens to is a pass-through.
	 *
	 * <p>The returned object is the invoker for the current listener set: call it, do not cache it
	 * across registrations (registering rebuilds it).
	 *
	 * @return the current invoker, never {@code null}
	 */
	T invoker();
}
