package cn.enaium.fabricpvz.api.event;

import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Function;

/**
 * The array-backed {@link Event} implementation.
 *
 * <p>Listeners live in a {@code T[]} whose component type is the listener interface, exactly like
 * Fabric API's {@code ArrayBackedEvent}, so the caller-supplied factory can cast the array to
 * {@code T[]} and loop over it without any bytecode generation: the invoker is whatever that factory
 * returns, and this class never dispatches by itself. No ASM, no generated invoker classes.
 *
 * <p>Every listener is wrapped in a {@link Proxy} of the listener interface when it is registered,
 * purely so a thrown exception can be re-thrown as a {@link ListenerException} carrying the event
 * and listener context. The invoker sees the wrappers, so dispatch is unchanged apart from that.
 *
 * <p>Each {@link #register(Object)} call appends to the array, rebuilds the invoker from the new
 * array and only then publishes both; {@link #invoker()} reads the published invoker once, so a
 * dispatch runs over the snapshot it started with even if a listener registers during it.
 */
final class ArrayBackedEvent<T> implements Event<T> {

	private final Class<T> type;
	private final Function<T[], T> invokerFactory;
	private T[] listeners;
	private T invoker;

	ArrayBackedEvent(Class<T> type, Function<T[], T> invokerFactory) {
		this.type = type;
		this.invokerFactory = invokerFactory;
		this.listeners = newArray(0);
		this.invoker = invokerFactory.apply(this.listeners);
	}

	@Override
	public void register(T listener) {
		if (listener == null) {
			throw new NullPointerException("listener");
		}

		synchronized (this) {
			T[] current = this.listeners;
			T[] next = newArray(current.length + 1);
			System.arraycopy(current, 0, next, 0, current.length);
			next[current.length] = wrap(listener);

			// Build the new invoker before publishing either, so invoker() never observes a half
			// registered state.
			T nextInvoker = this.invokerFactory.apply(next);
			this.listeners = next;
			this.invoker = nextInvoker;
		}
	}

	@Override
	public T invoker() {
		return this.invoker;
	}

	private T wrap(T listener) {
		InvocationHandler handler = new ListenerInvocationHandler(this.type, listener);
		Object proxy = Proxy.newProxyInstance(this.type.getClassLoader(), new Class<?>[] { this.type }, handler);
		return this.type.cast(proxy);
	}

	@SuppressWarnings("unchecked")
	private T[] newArray(int size) {
		return (T[]) Array.newInstance(this.type, size);
	}

	/**
	 * Calls the real listener and re-throws anything it throws as a {@link ListenerException}.
	 */
	private static final class ListenerInvocationHandler implements InvocationHandler {

		private final Class<?> type;
		private final Object listener;

		ListenerInvocationHandler(Class<?> type, Object listener) {
			this.type = type;
			this.listener = listener;
		}

		@Override
		public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
			try {
				return method.invoke(this.listener, args);
			} catch (InvocationTargetException e) {
				throw new ListenerException(this.type, this.listener, e.getCause());
			} catch (ReflectiveOperationException e) {
				throw new ListenerException(this.type, this.listener, e);
			}
		}
	}
}
