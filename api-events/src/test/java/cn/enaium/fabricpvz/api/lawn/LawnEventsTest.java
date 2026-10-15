package cn.enaium.fabricpvz.api.lawn;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import cn.enaium.fabricpvz.api.ApiEventsInitializer;
import cn.enaium.fabricpvz.portable.lawn.hooks.LawnHooksRegistry;
import cn.enaium.fabricpvz.api.event.Event;

/**
 * Tests the gameplay events on the values the game depends on: an event with no listener must return
 * what it was given, listeners must chain in registration order, and the bridge must connect the
 * game's hook dispatcher to them.
 */
class LawnEventsTest {

	@Test
	void valueEventsArePassThroughWithoutListeners() {
		// The whole game must be unmodified when no mod listens to an event: the input comes back.
		assertEquals(40, LawnEvents.ZOMBIE_DAMAGE.invoker().onZombieDamage(null, null, 40, 0));
		assertEquals(60, LawnEvents.PLANT_DAMAGE.invoker().onPlantDamage(null, null, 60));
		assertEquals(3, LawnEvents.PLANTING_VALIDATED.invoker().onPlantingValidated(null, 0, 0, 0, 3));
		assertEquals(90, LawnEvents.PLANT_DAMAGE.invoker().onPlantDamage(null, null, 90));
	}

	@Test
	void cancellingEventsDoNotCancelWithoutListeners() {
		assertFalse(LawnEvents.WAVE_START.invoker().onWaveStart(null, 0, new int[0]));
		assertFalse(LawnEvents.ZOMBIE_SPAWN.invoker().onZombieSpawn(null, 0, 0, false));
	}

	@Test
	void valueEventsChainThroughEveryListener() {
		List<Integer> seen = new ArrayList<>();
		LawnEvents.SunCollected doubler = (board, amount, source) -> {
			seen.add(amount);
			return amount * 2;
		};
		LawnEvents.SunCollected addTen = (board, amount, source) -> {
			seen.add(amount);
			return amount + 10;
		};

		LawnEvents.SUN_COLLECTED.register(doubler);
		LawnEvents.SUN_COLLECTED.register(addTen);

		assertEquals(60, LawnEvents.SUN_COLLECTED.invoker().onSunCollected(null, 25, 0));
		assertEquals(List.of(25, 50), seen, "each listener must see what the previous one returned");
	}

	@Test
	void cancellingEventsAreOrEd() {
		List<String> calls = new ArrayList<>();

		LawnEvents.ZOMBIE_SPAWN.register((board, zombieType, row, fromWave) -> {
			calls.add("first");
			return false;
		});
		LawnEvents.ZOMBIE_SPAWN.register((board, zombieType, row, fromWave) -> {
			calls.add("second");
			return true;
		});

		assertTrue(LawnEvents.ZOMBIE_SPAWN.invoker().onZombieSpawn(null, 0, 0, true));
		assertEquals(List.of("first", "second"), calls, "every listener must run even after a cancel");
	}

	@Test
	void eventsAreOurFabricShapedEventTypes() {
		assertTrue(LawnEvents.TICK instanceof Event);
		assertTrue(Event.class.isAssignableFrom(LawnEvents.TICK.getClass()));
		assertEquals("cn.enaium.fabricpvz.api.event", Event.class.getPackageName());
		assertEquals("cn.enaium.fabricpvz.api.event.ArrayBackedEvent",
				LawnEvents.TICK.getClass().getName());
	}

	@Test
	void theEntryPointInstallsTheGameplayBridge() {
		LawnHooksBridge.uninstall();
		assertFalse(LawnHooksBridge.isInstalled());

		new ApiEventsInitializer().onInitialize();

		assertTrue(LawnHooksBridge.isInstalled(),
				"the events module entrypoint must install the gameplay bridge before the game starts");
		assertEquals(1, LawnHooksRegistry.count(), "exactly one bridge must be registered");
		LawnHooksBridge.uninstall();
		assertEquals(0, LawnHooksRegistry.count());
	}
}
