package cn.enaium.fabricpvz.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Tests the aggregator's metadata: the {@code fabric-api} artifact carries no code of its own, it
 * declares itself as a mod that depends on the five feature modules, and it does not re-declare their
 * entry points.
 */
class AggregatorMetadataTest {

	private static final String MOD_ID = "fabric-api";
	private static final String VERSION = "1.2.2";
	private static final List<String> MODULES =
			List.of("api-base", "api-events", "api-plant", "api-resource", "api-zombie");

	@Test
	void theAggregatorIsAModThatDependsOnEveryModule() throws Exception {
		String json = readMetadata();

		assertEquals(MOD_ID, group("\"id\"\\s*:\\s*\"([^\"]+)\"", json));
		assertEquals(VERSION, group("\"version\"\\s*:\\s*\"([^\"]+)\"", json));
		assertEquals("*", group("\"environment\"\\s*:\\s*\"([^\"]+)\"", json));
		assertTrue(json.contains("\"license\""));

		String depends = region("\"depends\"", json);
		for (String module : MODULES) {
			assertTrue(depends.contains("\"" + module + "\""),
					"the aggregator must depend on " + module + ", got: " + depends);
		}
	}

	@Test
	void theAggregatorDeclaresNoEntryPoints() throws Exception {
		String json = readMetadata();

		assertFalse(json.contains("\"entrypoints\""),
				"the aggregator is glue only; entry points belong to the modules");
	}

	@Test
	void everyModuleIsInTheBuild() {
		// The modules are Gradle subprojects of this build; their presence is what makes
		// `./gradlew build` produce all six artifacts.
		for (String module : MODULES) {
			assertTrue(java.nio.file.Files.isDirectory(java.nio.file.Path.of(module)),
					"missing module directory " + module);
			assertTrue(java.nio.file.Files.isRegularFile(
							java.nio.file.Path.of(module, "src", "main", "resources", "fabric.mod.json")),
					module + " must ship its own fabric.mod.json");
		}
	}

	private static String readMetadata() throws IOException {
		try (InputStream in = AggregatorMetadataTest.class.getResourceAsStream("/fabric.mod.json")) {
			assertNotNull(in, "fabric.mod.json must be packaged in the aggregator jar root");
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static String group(String pattern, String json) {
		Matcher matcher = Pattern.compile(pattern).matcher(json);
		assertTrue(matcher.find(), "fabric.mod.json must contain " + pattern);
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
