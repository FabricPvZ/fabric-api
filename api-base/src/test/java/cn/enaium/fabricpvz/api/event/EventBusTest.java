package cn.enaium.fabricpvz.api.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

/**
 * Tests the semantics of our own Fabric-shaped event bus ({@link Event} / {@link EventFactory}) on a
 * value-chaining listener type: the pass-through empty invoker, registration order, dispatch over a
 * snapshot while a listener registers, and exception wrapping with listener context.
 */
class EventBusTest {

	/** The listener interface the tests register on. */
	interface IntListener {
		int onInt(int value);
	}

	/** The value pipeline the tests build, i.e. what {@code LawnEvents} does for its own events. */
	private static final Function<IntListener[], IntListener> CHAIN = callbacks -> value -> {
		int result = value;

		for (IntListener callback : callbacks) {
			result = callback.onInt(result);
		}

		return result;
	};

	private static Event<IntListener> newEvent() {
		return EventFactory.createArrayBacked(IntListener.class, CHAIN);
	}

	@Test
	void invokerFactoryReceivesAnArrayOfTheListenerInterface() {
		AtomicReference<Class<?>> componentType = new AtomicReference<>();

		EventFactory.createArrayBacked(IntListener.class, callbacks -> {
			componentType.set(callbacks.getClass().getComponentType());
			return value -> value;
		});

		assertEquals(IntListener.class, componentType.get(), "the array factory must get a T[] of the listener type");
	}

	@Test
	void anEventWithNoListenersIsAPassThrough() {
		Event<IntListener> event = newEvent();

		assertEquals(7, event.invoker().onInt(7), "with no listener the value must come straight back out");
		assertEquals(-3, event.invoker().onInt(-3));
	}

	@Test
	void listenersRunInRegistrationOrder() {
		Event<IntListener> event = newEvent();
		List<String> calls = new ArrayList<>();

		event.register(value -> {
			calls.add("first");
			return value + 1;
		});
		event.register(value -> {
			calls.add("second");
			return value * 10;
		});

		assertEquals(80, event.invoker().onInt(7));
		assertEquals(List.of("first", "second"), calls);
	}

	@Test
	void invokerIsRebuiltOnEveryRegistration() {
		Event<IntListener> event = newEvent();
		IntListener empty = event.invoker();
		assertSame(empty, event.invoker(), "reading the invoker twice must not rebuild it");

		event.register(value -> value + 1);
		IntListener one = event.invoker();

		assertNotSame(empty, one, "registering must build a new invoker");
		assertEquals(8, one.onInt(7));
		assertEquals(7, empty.onInt(7), "the old invoker must still see the old listener set");
	}

	@Test
	void registeringDuringDispatchDoesNotAffectTheInFlightInvocation() {
		Event<IntListener> event = newEvent();
		List<Integer> seen = new ArrayList<>();
		AtomicInteger registrations = new AtomicInteger();
		IntListener late = value -> {
			seen.add(100 + value);
			return value;
		};

		event.register(value -> {
			seen.add(value);

			// Registered while the invoker is running: the in-flight invocation must use the snapshot
			// it started with, so `late` is not called by this dispatch.
			if (registrations.getAndIncrement() == 0) {
				event.register(late);
			}

			return value;
		});

		event.invoker().onInt(1);
		assertEquals(List.of(1), seen, "a listener registered during dispatch must not run in that dispatch");

		event.invoker().onInt(2);
		assertEquals(List.of(1, 2, 102), seen, "the next dispatch must see the newly registered listener");
	}

	@Test
	void listenerExceptionIsWrappedWithTheListenerContext() {
		Event<IntListener> event = newEvent();
		AtomicInteger calls = new AtomicInteger();
		IntListener failing = value -> {
			calls.incrementAndGet();
			throw new IllegalStateException("boom " + value);
		};
		event.register(failing);

		ListenerException thrown = assertThrows(ListenerException.class, () -> event.invoker().onInt(3));

		assertEquals(IntListener.class, thrown.eventType(), "the exception must name the event");
		assertSame(failing, thrown.listener(), "the exception must name the listener that threw");
		assertEquals(IllegalStateException.class, thrown.getCause().getClass());
		assertEquals("boom 3", thrown.getCause().getMessage());
		assertEquals(1, calls.get());
	}

	@Test
	void aThrowingListenerDoesNotCorruptTheEvent() {
		Event<IntListener> event = newEvent();
		AtomicInteger calls = new AtomicInteger();
		event.register(value -> {
			calls.incrementAndGet();
			throw new IllegalStateException("still broken");
		});

		assertThrows(ListenerException.class, () -> event.invoker().onInt(1));
		// The listener is still registered and the event is still usable: dispatching again rethrows
		// the same wrapped failure instead of the event being left broken.
		assertThrows(ListenerException.class, () -> event.invoker().onInt(2));
		assertEquals(2, calls.get());
	}

	@Test
	void eventsDoNotShareListeners() {
		Event<IntListener> first = newEvent();
		Event<IntListener> second = newEvent();
		AtomicInteger calls = new AtomicInteger();

		first.register(value -> {
			calls.incrementAndGet();
			return value;
		});

		second.invoker().onInt(0);
		assertEquals(0, calls.get(), "firing one event must not fire another event's listeners");
	}

	@Test
	void factoryAndRegistrationRejectBadInput() {
		assertThrows(IllegalArgumentException.class,
				() -> EventFactory.createArrayBacked(String.class, callbacks -> "not an interface"));
		assertThrows(NullPointerException.class, () -> EventFactory.createArrayBacked(null, callbacks -> callbacks[0]));
		assertThrows(NullPointerException.class, () -> EventFactory.createArrayBacked(IntListener.class, null));
		assertThrows(NullPointerException.class, () -> newEvent().register(null));
	}
}
