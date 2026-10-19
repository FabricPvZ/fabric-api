package cn.enaium.fabricpvz.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

/**
 * Tests the jar-in-jar packaging of the aggregator, on the artifact it publishes (the build hands the
 * test the built jar): the five feature modules nested under {@code META-INF/jars/}, the {@code jars}
 * array of the packaged {@code fabric.mod.json} naming exactly them, and every nested jar keeping its
 * own metadata - its own id and version. That last point is what the whole mechanism rests on: Fabric
 * Loader reads a nested mod's id, version and entry points from <em>its</em> {@code fabric.mod.json},
 * so the aggregate may only carry the jars, never rewrite what is inside them.
 *
 * <p>The jar is also asserted to carry nothing else in {@code META-INF/jars/}: the aggregate is the
 * five modules and no sixth jar.
 */
class AggregatorJarInJarTest {

    /** Where Fabric Loader looks for the jars a mod carries inside itself. */
    private static final String NESTED_DIRECTORY = "META-INF/jars/";

    private static final String METADATA = "fabric.mod.json";
    private static final String VERSION = "1.2.1";
    private static final List<String> MODULES = List.of("api-base", "api-events", "api-plant", "api-resource", "api-zombie");

    /** One {@code {"file": "…"}} entry of the metadata's {@code jars} array. */
    private static final Pattern JAR_FILE = Pattern.compile("\"file\"\\s*:\\s*\"([^\"]+)\"");

    @Test
    void theJarCarriesEveryModuleAndNothingElse() throws Exception {
        try (JarFile jar = new JarFile(builtJar().toFile())) {
            assertEquals(nestedJarNames(), sorted(nestedJars(jar)),
                    "the aggregate jar must carry the five modules under " + NESTED_DIRECTORY);
        }
    }

    @Test
    void thePackagedMetadataListsExactlyTheNestedJars() throws Exception {
        try (JarFile jar = new JarFile(builtJar().toFile())) {
            String metadata = metadata(jar);

            assertEquals("fabric-api", group("\"id\"\\s*:\\s*\"([^\"]+)\"", metadata),
                    "the packaged metadata stays the aggregator's own");
            assertEquals(nestedJarNames(), sorted(listed(metadata)),
                    "the jars array must name exactly the jars the aggregate carries, once each");
            assertEquals(nestedJars(jar).size(), listed(metadata).size(),
                    "the jars array must hold one entry per nested jar, no more");
        }
    }

    @Test
    void everyNestedJarKeepsItsOwnMetadata() throws Exception {
        try (JarFile jar = new JarFile(builtJar().toFile())) {
            for (String module : MODULES) {
                String nested = NESTED_DIRECTORY + module + "-" + VERSION + ".jar";
                String nestedMetadata = nestedMetadata(jar, nested);

                assertEquals(module, group("\"id\"\\s*:\\s*\"([^\"]+)\"", nestedMetadata),
                        nested + " must keep its own id: Fabric Loader loads it as a mod of its own");
                assertEquals(VERSION, group("\"version\"\\s*:\\s*\"([^\"]+)\"", nestedMetadata),
                        nested + " must keep its own version");
            }
        }
    }

    /** A copy of {@code names} in name order: the order inside the jar is Gradle's resolution order. */
    private static List<String> sorted(List<String> names) {
        return names.stream().sorted().toList();
    }

    /** The nested jars' entry names, in the order the aggregate carries them. */
    private static List<String> nestedJars(JarFile jar) {
        List<String> names = new ArrayList<>();
        for (java.util.Enumeration<JarEntry> entries = jar.entries(); entries.hasMoreElements(); ) {
            JarEntry entry = entries.nextElement();
            if (!entry.isDirectory() && entry.getName().startsWith(NESTED_DIRECTORY)) {
                names.add(entry.getName());
            }
        }
        return names;
    }

    /** What the aggregate is expected to carry: the five modules, each as its own jar. */
    private static List<String> nestedJarNames() {
        return MODULES.stream().map(module -> NESTED_DIRECTORY + module + "-" + VERSION + ".jar").toList();
    }

    /** The {@code file} values of the metadata's {@code jars} array, in the order they are listed. */
    private static List<String> listed(String metadata) {
        List<String> files = new ArrayList<>();
        Matcher matcher = JAR_FILE.matcher(metadata);
        while (matcher.find()) {
            files.add(matcher.group(1));
        }
        return files;
    }

    private static String metadata(JarFile jar) throws IOException {
        JarEntry entry = jar.getJarEntry(METADATA);
        assertNotNull(entry, "the aggregate jar must keep its " + METADATA + " in its root");
        try (InputStream in = jar.getInputStream(entry)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** The metadata of a nested jar, read from inside the aggregate jar. */
    private static String nestedMetadata(JarFile jar, String nested) throws IOException {
        JarEntry entry = jar.getJarEntry(nested);
        assertNotNull(entry, "the aggregate jar must carry " + nested);
        try (JarInputStream in = new JarInputStream(jar.getInputStream(entry))) {
            for (JarEntry nestedEntry = in.getNextJarEntry(); nestedEntry != null;
                    nestedEntry = in.getNextJarEntry()) {
                if (METADATA.equals(nestedEntry.getName())) {
                    return new String(in.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new AssertionError(nested + " must carry its own " + METADATA);
    }

    private static String group(String pattern, String json) {
        Matcher matcher = Pattern.compile(pattern).matcher(json);
        assertTrue(matcher.find(), pattern + " must be in:\n" + json);
        return matcher.group(1);
    }

    /** The jar the build handed this test: the artifact the nesting produced, not the classes directory. */
    private static Path builtJar() {
        String property = System.getProperty("fabric.api.jar", "");
        if (property.isBlank()) {
            fail("-Dfabric.api.jar is not set (run through Gradle)");
        }
        Path jar = Path.of(property);
        assertTrue(Files.isRegularFile(jar), "the aggregate jar must be built first: " + jar);
        return jar;
    }
}
