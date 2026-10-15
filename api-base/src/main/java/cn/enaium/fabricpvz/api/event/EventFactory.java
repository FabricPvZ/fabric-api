package cn.enaium.fabricpvz.api.event;

import java.util.function.Function;

/**
 * Creates {@link Event}s, with the same signature as Fabric API's {@code EventFactory}.
 *
 * <p>Only the array-backed factory is implemented, because it is the only one this API (and Fabric
 * API itself, in practice) uses: the listener interface for the array element type, and a function
 * that receives the listener array and returns the invoker the game calls.
 */
public final class EventFactory {

	private EventFactory() {
	}

	/**
	 * Creates an event backed by an array of listeners.
	 *
	 * @param type the listener interface; used as the array component type and, on registration, to
	 *     create the wrapper that adds listener context to exceptions
	 * @param invokerFactory turns the current listener array into the invoker; it is called once
	 *     when the event is created (with an empty array) and again on every registration
	 * @param <T> the listener type
	 * @return the event
	 * @throws IllegalArgumentException if {@code type} is not an interface
	 * @throws NullPointerException if {@code type} or {@code invokerFactory} is {@code null}
	 */
	public static <T> Event<T> createArrayBacked(Class<T> type, Function<T[], T> invokerFactory) {
		if (type == null) {
			throw new NullPointerException("type");
		}

		if (invokerFactory == null) {
			throw new NullPointerException("invokerFactory");
		}

		if (!type.isInterface()) {
			throw new IllegalArgumentException("The event listener type must be an interface: " + type.getName());
		}

		return new ArrayBackedEvent<>(type, invokerFactory);
	}
}
