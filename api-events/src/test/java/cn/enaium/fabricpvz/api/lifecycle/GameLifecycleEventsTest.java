package cn.enaium.fabricpvz.api.lifecycle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.api.ApiEventsInitializer;
import cn.enaium.fabricpvz.api.event.Event;

/**
 * Tests the built-in lifecycle events: they are our own Fabric-shaped event types, they exist as
 * distinct, functional events, they dispatch in registration order and they do not share listeners.
 */
class GameLifecycleEventsTest {

	@Test
	void lifecycleEventsAreDistinctAndAreOurEventTypes() {
		assertNotNull(GameLifecycleEvents.INITIALIZED);
		assertNotNull(GameLifecycleEvents.EXITING);
		assertNotSame(GameLifecycleEvents.INITIALIZED, GameLifecycleEvents.EXITING);
		assertSame(GameLifecycleEvents.INITIALIZED, GameLifecycleEvents.INITIALIZED);

		// The events come from our own array-backed bus, not from the upstream Fabric API base jar.
		assertTrue(GameLifecycleEvents.INITIALIZED instanceof Event);
		assertTrue(Event.class.isAssignableFrom(GameLifecycleEvents.INITIALIZED.getClass()));
		assertEquals("cn.enaium.fabricpvz.api.event.ArrayBackedEvent",
				GameLifecycleEvents.INITIALIZED.getClass().getName());
		assertEquals("cn.enaium.fabricpvz.api.event", Event.class.getPackageName());
	}

	@Test
	void initializeListenersFireInRegistrationOrder() {
		List<String> calls = new ArrayList<>();
		GameLifecycleEvents.Initialize first = () -> calls.add("first");
		GameLifecycleEvents.Initialize second = () -> calls.add("second");

		GameLifecycleEvents.INITIALIZED.register(first);
		GameLifecycleEvents.INITIALIZED.register(second);

		GameLifecycleEvents.INITIALIZED.invoker().onInitialize();

		assertEquals(List.of("first", "second"), calls);
	}

	@Test
	void exitListenersReceiveTheExitCallbackInRegistrationOrder() {
		List<String> calls = new ArrayList<>();

		GameLifecycleEvents.EXITING.register(() -> calls.add("first"));
		GameLifecycleEvents.EXITING.register(() -> calls.add("second"));

		GameLifecycleEvents.EXITING.invoker().onExit();

		assertEquals(List.of("first", "second"), calls);
	}

	@Test
	void lifecycleEventsDoNotShareListeners() {
		AtomicInteger initializeCalls = new AtomicInteger();

		GameLifecycleEvents.INITIALIZED.register(initializeCalls::incrementAndGet);
		GameLifecycleEvents.EXITING.invoker().onExit();

		assertEquals(0, initializeCalls.get(), "firing EXITING must not fire INITIALIZED listeners");
	}

	@Test
	void callbacksAreFunctionalInterfaces() {
		assertTrue(GameLifecycleEvents.Initialize.class.isInterface());
		assertTrue(GameLifecycleEvents.Exit.class.isInterface());
		assertEquals(1, GameLifecycleEvents.Initialize.class.getDeclaredMethods().length,
				"Initialize must declare exactly one method");
		assertEquals(1, GameLifecycleEvents.Exit.class.getDeclaredMethods().length,
				"Exit must declare exactly one method");
	}

	@Test
	void theEntryPointInstallsTheLifecycleBridge() {
		AppHooksBridge.uninstall();
		assertFalse(AppHooksBridge.isInstalled());

		new ApiEventsInitializer().onInitialize();

		assertTrue(AppHooksBridge.isInstalled(),
				"the events module entrypoint must install the lifecycle bridge before the game starts");
		AppHooksBridge.uninstall();
	}
}
