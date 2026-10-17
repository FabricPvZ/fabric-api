package cn.enaium.fabricpvz.api.resource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests the override registry on its own: key normalization, the resolution order (single file /
 * bytes, then roots, then "not overridden"), the fall-back when a root holds no such file, and the
 * cache.
 *
 * <p>What this cannot cover is the engine: whether {@code PakInterface.open} actually returns the
 * override is the Mixin's job, and that is what the game's loader end-to-end test and the 95-edition
 * mod's e2e exercise. Here {@link ResourceOverrides#resolveOverride(String)} is the whole resolution,
 * called directly.
 */
class ResourceOverridesTest {

    @TempDir
    Path tmp;

    @AfterEach
    void reset() {
        ResourceOverrides.clear();
    }

    // ---------------------------------------------------------------- keys

    @Test
    void keysAreNormalizedLikeTheEngine() {
        assertEquals("REANIM/FOO.PNG", ResourceOverrides.key("reanim/foo.png"));
        assertEquals("REANIM/FOO.PNG", ResourceOverrides.key("ReAnim\\Foo.PNG"));
        assertEquals("REANIM/FOO.PNG", ResourceOverrides.key("./reanim/./foo.png"));
        assertEquals("REANIM/FOO.PNG", ResourceOverrides.key("reanim/other/../foo.png"));
    }

    @Test
    void aRegisteredNameResolvesThroughAnySpelling() {
        byte[] data = "peashooter".getBytes(StandardCharsets.UTF_8);
        ResourceOverrides.addBytes("reanim/foo.png", data);

        ResourceOverrides.Override byBackslash = ResourceOverrides.resolveOverride("REANIM\\FOO.PNG");
        ResourceOverrides.Override byDottedPath = ResourceOverrides.resolveOverride("./reanim/foo.png");

        assertNotNull(byBackslash, "the spelling does not change the resource");
        assertArrayEquals(data, byBackslash.bytes());
        assertNotNull(byDottedPath);
    }

    @Test
    void bytesAreCopied() {
        byte[] data = {1, 2, 3};
        ResourceOverrides.addBytes("foo.bin", data);
        data[0] = 9;

        assertArrayEquals(new byte[] {1, 2, 3}, ResourceOverrides.resolveOverride("foo.bin").bytes(),
                "a caller may reuse its array after registering it");
    }

    // ---------------------------------------------------------------- priority

    @Test
    void aSingleFileBeatsARoot() throws IOException {
        Path root = Files.createDirectories(tmp.resolve("root/reanim"));
        Files.writeString(root.resolve("foo.png"), "from-root");
        Path single = tmp.resolve("single.png");
        Files.writeString(single, "from-single");

        ResourceOverrides.addRoot(tmp.resolve("root"));
        ResourceOverrides.addFile("REANIM/FOO.PNG", single);

        assertEquals(single, ResourceOverrides.resolveOverride("REANIM/FOO.PNG").file());
    }

    @Test
    void bytesBeatAFile() throws IOException {
        Path single = tmp.resolve("single.png");
        Files.writeString(single, "from-file");
        ResourceOverrides.addFile("REANIM/FOO.PNG", single);
        ResourceOverrides.addBytes("REANIM/FOO.PNG", "from-bytes".getBytes(StandardCharsets.UTF_8));

        ResourceOverrides.Override override = ResourceOverrides.resolveOverride("REANIM/FOO.PNG");

        assertNotNull(override.bytes());
        assertNull(override.file());
    }

    @Test
    void theFirstRootWins() throws IOException {
        Path a = Files.createDirectories(tmp.resolve("first/reanim"));
        Files.writeString(a.resolve("foo.png"), "first");
        Path b = Files.createDirectories(tmp.resolve("second/reanim"));
        Files.writeString(b.resolve("foo.png"), "second");

        ResourceOverrides.addRoot(tmp.resolve("first"));
        ResourceOverrides.addRoot(tmp.resolve("second"));

        assertTrue(Files.isSameFile(a.resolve("foo.png"), ResourceOverrides.resolveOverride("reanim/foo.png").file()),
                "the first registered root is the one that answers");
        assertFalse(Files.isSameFile(b.resolve("foo.png"), ResourceOverrides.resolveOverride("reanim/foo.png").file()));
    }

    // ---------------------------------------------------------------- roots

    @Test
    void aRootResolvesCaseInsensitivelyWithTheRealCase() throws IOException {
        Path dir = Files.createDirectories(tmp.resolve("pack/properties"));
        Path lawnStrings = Files.writeString(dir.resolve("LawnStrings.txt"), "hello");

        ResourceOverrides.addRoot(tmp.resolve("pack"));

        ResourceOverrides.Override override = ResourceOverrides.resolveOverride("PROPERTIES/LAWNSTRINGS.TXT");

        assertNotNull(override, "an upper-case pak key must find a mixed-case file on disk");
        assertTrue(Files.isSameFile(lawnStrings, override.file()),
                "the resolved path must be the file on disk");
    }

    @Test
    void aRootMissesFallThroughToThePak() throws IOException {
        Files.createDirectories(tmp.resolve("pack/properties"));
        ResourceOverrides.addRoot(tmp.resolve("pack"));

        assertNull(ResourceOverrides.resolveOverride("PROPERTIES/LAWNSTRINGS.TXT"),
                "no such file in the root: the engine's own pak lookup has to run");
    }

    @Test
    void aRootDoesNotMatchADirectory() throws IOException {
        Files.createDirectories(tmp.resolve("pack/properties/lawnstrings.txt"));
        ResourceOverrides.addRoot(tmp.resolve("pack"));

        assertNull(ResourceOverrides.resolveOverride("PROPERTIES/LAWNSTRINGS.TXT"),
                "the resource must be a file, not a directory");
    }

    @Test
    void anAmbiguousRootMatchIsNotAnOverride() throws IOException {
        Path dir = Files.createDirectories(tmp.resolve("pack/reanim"));
        Files.writeString(dir.resolve("Foo.png"), "one");
        Files.writeString(dir.resolve("foo.png"), "two");
        ResourceOverrides.addRoot(tmp.resolve("pack"));

        // Only meaningful on a case-sensitive filesystem; on a case-insensitive one the two names are
        // the same file and the direct hit wins, which is also a single answer.
        ResourceOverrides.Override override = ResourceOverrides.resolveOverride("REANIM/FOO.PNG");
        assertTrue(override == null || override.file().getFileName().toString().equalsIgnoreCase("foo.png"));
    }

    // ---------------------------------------------------------------- files

    @Test
    void aMissingSingleFileFallsThrough() {
        ResourceOverrides.addFile("REANIM/FOO.PNG", tmp.resolve("does-not-exist.png"));

        assertNull(ResourceOverrides.resolveOverride("REANIM/FOO.PNG"));
    }

    // ---------------------------------------------------------------- registry

    @Test
    void overriddenNamesAreTheExplicitKeysInUppercaseSorted() throws IOException {
        Path single = Files.writeString(tmp.resolve("foo.png"), "x");
        ResourceOverrides.addFile("reanim/zzz.png", single);
        ResourceOverrides.addBytes("a/aaa.txt", new byte[] {1});

        assertEquals(List.of("A/AAA.TXT", "REANIM/ZZZ.PNG"), ResourceOverrides.overriddenNames());
    }

    @Test
    void clearRemovesEverything() throws IOException {
        Path root = Files.createDirectories(tmp.resolve("root/reanim"));
        Files.writeString(root.resolve("foo.png"), "x");
        ResourceOverrides.addRoot(tmp.resolve("root"));
        ResourceOverrides.addBytes("a.bin", new byte[] {1});
        assertNotNull(ResourceOverrides.resolveOverride("a.bin"));

        ResourceOverrides.clear();

        assertNull(ResourceOverrides.resolveOverride("a.bin"));
        assertNull(ResourceOverrides.resolveOverride("reanim/foo.png"));
        assertTrue(ResourceOverrides.overriddenNames().isEmpty());
    }

    @Test
    void theCacheFollowsReRegistration() {
        ResourceOverrides.addBytes("a.bin", new byte[] {1});
        assertArrayEquals(new byte[] {1}, ResourceOverrides.resolveOverride("a.bin").bytes());

        ResourceOverrides.addBytes("a.bin", new byte[] {2});

        assertArrayEquals(new byte[] {2}, ResourceOverrides.resolveOverride("a.bin").bytes(),
                "re-registering a path must drop the memoized answer");
    }

    // ---------------------------------------------------------------- contract

    @Test
    void installedIsFalseWithoutTheMixin() {
        assertFalse(ResourceOverrides.installed());
    }

    @Test
    void markInstalledIsWhatTheMixinCalls() {
        ResourceOverrides.markInstalled();

        assertTrue(ResourceOverrides.installed());
    }

    @Test
    void rejectsNull() {
        assertThrows(NullPointerException.class, () -> ResourceOverrides.addRoot(null));
        assertThrows(NullPointerException.class, () -> ResourceOverrides.addFile("a.bin", null));
        assertThrows(NullPointerException.class, () -> ResourceOverrides.addBytes("a.bin", null));
        assertThrows(NullPointerException.class, () -> ResourceOverrides.resolveOverride(null));
    }
}
