package cn.enaium.fabricpvz.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import net.fabricmc.api.ModInitializer;

/**
 * Tests that the base module's own {@code fabric.mod.json} stays in sync with the Java constants it
 * advertises.
 */
class ApiMetadataTest {

	private static final Pattern ID = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
	private static final Pattern VERSION = Pattern.compile("\"version\"\\s*:\\s*\"([^\"]+)\"");
	private static final Pattern ENVIRONMENT = Pattern.compile("\"environment\"\\s*:\\s*\"([^\"]+)\"");
	private static final Pattern MAIN_ENTRYPOINT = Pattern.compile("\"main\"\\s*:\\s*\\[\\s*\"([^\"]+)\"");

	@Test
	void metadataDescribesTheBaseModule() throws Exception {
		String json = readMetadata();

		assertEquals(PvzFabricApi.BASE_MOD_ID, firstGroup(ID, json),
				"fabric.mod.json id must match PvzFabricApi.BASE_MOD_ID");
		assertEquals(PvzFabricApi.VERSION, firstGroup(VERSION, json),
				"fabric.mod.json version must match PvzFabricApi.VERSION");
		assertEquals(1, Integer.parseInt(firstGroup(Pattern.compile("\"schemaVersion\"\\s*:\\s*(\\d+)"), json)));
		assertEquals("*", firstGroup(ENVIRONMENT, json));
		assertTrue(json.contains("\"license\""));
		String depends = region("\"depends\"", json);
		assertTrue(depends.contains("\"fabricloader\""),
				"the base module must depend on Fabric Loader, got: " + depends);
	}

	@Test
	void mainEntrypointIsThisModulesInitializer() throws Exception {
		String entrypoint = firstGroup(MAIN_ENTRYPOINT, readMetadata());

		assertEquals(ApiModInitializer.class.getName(), entrypoint);

		Class<?> type = Class.forName(entrypoint);
		assertTrue(ModInitializer.class.isAssignableFrom(type), entrypoint + " must implement ModInitializer");
		assertNotNull(type.getDeclaredConstructor().newInstance());
	}

	private static String readMetadata() throws IOException {
		try (InputStream in = PvzFabricApi.class.getResourceAsStream("/fabric.mod.json")) {
			assertNotNull(in, "fabric.mod.json must be packaged in the base module jar root");
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static String firstGroup(Pattern pattern, String json) {
		Matcher matcher = pattern.matcher(json);
		assertTrue(matcher.find(), "fabric.mod.json must contain " + pattern.pattern());
		return matcher.group(1);
	}

	/** Returns the text of the object that starts at {@code key}, balanced on braces. */
	private static String region(String key, String json) {
		int start = json.indexOf(key);
		assertTrue(start >= 0, "fabric.mod.json must contain " + key);
		int open = json.indexOf('{', start);
		int depth = 0;

		for (int i = open; i < json.length(); i++) {
			char c = json.charAt(i);
			if (c == '{') {
				depth++;
			} else if (c == '}' && --depth == 0) {
				return json.substring(open, i + 1);
			}
		}

		throw new AssertionError("unbalanced " + key + " in fabric.mod.json");
	}
}
