package cn.enaium.fabricpvz.api.resource;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

/**
 * Loose-file overrides above the game's pak containers: a mod registers a directory (or single
 * files, or raw bytes) and the game reads those instead of the pak member of the same name.
 *
 * <p>The retail game mounts {@code main.pak} and falls back to the filesystem for names the pak does
 * not contain, i.e. the pak always wins. "95 版" and every other loose-package re-skin work the other
 * way round: a directory of {@code images/}, {@code reanim/}, {@code properties/LawnStrings.txt} …
 * shadows the pak. This class is that layer, and the module injects it into the engine's
 * {@code PakInterface} through {@link cn.enaium.fabricpvz.api.resource.mixin.ResourceOverridesMixin}
 * (see {@link #installed()}).
 *
 * <h2>Resolution order</h2>
 *
 * <ol>
 *   <li>a single file or a byte array registered for that exact pak path ({@link #addFile} /
 *       {@link #addBytes});</li>
 *   <li>each registered root directory, in registration order ({@link #addRoot});</li>
 *   <li>the pak member (the engine's own lookup);</li>
 *   <li>the filesystem relative to the resource folder (the engine's own fallback).</li>
 * </ol>
 *
 * <p>Only the first two are this class's business: {@link #resolveOverride(String)} answers
 * {@code null} for everything else and the engine's original logic runs untouched. With nothing
 * registered, the Mixin is a single volatile read away from the original code path, so an
 * installation that registers no override behaves, byte for byte, as it did before this module
 * existed.
 *
 * <h2>Keys</h2>
 *
 * <p>A pak path is compared the way the engine's {@code PakInterface.normalizePakPath} compares it:
 * case-insensitively, {@code '\\'} and {@code '/'} are the same separator, {@code .} / {@code ..}
 * segments are resolved, and the key is upper-case with {@code '/'} separators
 * ({@code "reanim\\foo.png"} and {@code "REANIM/FOO.PNG"} are the same resource). Root directories
 * are matched segment by segment, case-insensitively and with the real case of the file on disk, so
 * a posix filesystem (case-sensitive) behaves like the retail one.
 *
 * <h2>Caching</h2>
 *
 * <p>The first lookup of a pak path walks the roots and hits the filesystem; the answer (including
 * "no override") is memoized per asked-for path, so a resource opened repeatedly resolves without a
 * second path parse. Any registration and {@link #clear()} drops the cache - overrides are meant to
 * be registered once, at mod initialization, and never change afterwards.
 *
 * <p>Thread-safe and idempotent: registration is guarded by one lock and re-registering the same path
 * replaces the previous override.
 *
 * <h2>How an override is served</h2>
 *
 * <p>A single file or a root hit is opened as a file, exactly like the engine's own filesystem
 * fallback (same seeking, same {@code '\r'} handling); a byte override is served from memory, with the
 * semantics of a pak member - which is what it stands in for. Use {@link #addBytes} when the difference
 * matters, i.e. for resources the game reads as text through the pak's {@code FGetC}/{@code FGetS}.
 */
public final class ResourceOverrides {

    /** A memoized "this path is not overridden" answer, so a miss is cached like a hit. */
    private static final Override NONE = new Override(null, null);

    private static final Object LOCK = new Object();

    /** Byte overrides, keyed by the normalized pak path. */
    private static final Map<String, byte[]> sBytes = new LinkedHashMap<>();

    /** File overrides, keyed by the normalized pak path. */
    private static final Map<String, Path> sFiles = new LinkedHashMap<>();

    /** Root directories, in registration order. */
    private static final List<Path> sRoots = new ArrayList<>();

    /** Memoized answers, keyed by the path the caller asked for (see "Caching"). */
    private static final Map<String, Override> sResolved = new HashMap<>();

    /** Fast path for "nothing is registered", read on every engine lookup without the lock. */
    private static volatile boolean sEmpty = true;

    /** Set by the Mixin when it is applied (see {@link #installed()}). */
    private static volatile boolean sInstalled;

    private ResourceOverrides() {
    }

    /**
     * Registers {@code root} as a high-priority resource root: {@code root/<pak path>} (matched
     * case-insensitively) shadows the pak member of the same name.
     *
     * @param root the directory; {@code null} is rejected, a directory that does not exist yet simply
     *     never matches
     */
    public static void addRoot(Path root) {
        Objects.requireNonNull(root, "root");
        Path normalized = root.toAbsolutePath().normalize();
        synchronized (LOCK) {
            if (!sRoots.contains(normalized)) {
                sRoots.add(normalized);
            }
            sEmpty = false;
            sResolved.clear();
        }
    }

    /**
     * Registers a single file as the content of a pak path.
     *
     * @param pakPath the resource name in pak spelling ({@code "REANIM/FOO.PNG"} or
     *     {@code "reanim/foo.png"}); compared case-insensitively
     * @param file the file to serve; a file that does not exist falls through to the lower layers
     */
    public static void addFile(String pakPath, Path file) {
        String key = key(pakPath);
        Objects.requireNonNull(file, "file");
        Path normalized = file.toAbsolutePath().normalize();
        synchronized (LOCK) {
            sFiles.put(key, normalized);
            sBytes.remove(key);
            sEmpty = false;
            sResolved.clear();
        }
    }

    /**
     * Registers bytes as the content of a pak path - for the small files a mod carries inside its own
     * jar, so nothing has to be unpacked to disk.
     *
     * @param pakPath the resource name in pak spelling; compared case-insensitively
     * @param data the bytes; copied, so the caller may reuse the array
     */
    public static void addBytes(String pakPath, byte[] data) {
        String key = key(pakPath);
        Objects.requireNonNull(data, "data");
        synchronized (LOCK) {
            sBytes.put(key, data.clone());
            sFiles.remove(key);
            sEmpty = false;
            sResolved.clear();
        }
    }

    /**
     * The pak paths explicitly registered through {@link #addFile} / {@link #addBytes}, upper-case
     * with {@code '/'} separators and sorted.
     *
     * <p>Names that only a registered root directory could answer are not listed: those are not
     * known until a lookup asks for them, and this list is a diagnostic view of what was
     * registered, not an index of every file a root happens to contain.
     *
     * @return the registered names, sorted; empty when nothing was registered
     */
    public static List<String> overriddenNames() {
        synchronized (LOCK) {
            TreeSet<String> names = new TreeSet<>(sFiles.keySet());
            names.addAll(sBytes.keySet());
            return List.copyOf(names);
        }
    }

    /** Removes every root, file and byte override. For tests (and for a mod that drops a resource set). */
    public static void clear() {
        synchronized (LOCK) {
            sBytes.clear();
            sFiles.clear();
            sRoots.clear();
            sResolved.clear();
            sEmpty = true;
        }
    }

    /**
     * @return whether the module's Mixin is applied to the engine, i.e. whether overrides can take
     *     effect at all. Set when the engine's {@code PakInterface} is constructed; {@code false} in a
     *     plain unit test, or when the Mixin configuration was not applied.
     */
    public static boolean installed() {
        return sInstalled;
    }

    /**
     * Records that the module's Mixin is applied. Called by
     * {@link cn.enaium.fabricpvz.api.resource.mixin.ResourceOverridesMixin} itself and by nothing
     * else; it is public only because the Mixin lives in a different package.
     */
    public static void markInstalled() {
        sInstalled = true;
    }

    /**
     * Resolves the override of a pak path, without any engine state: the whole resolution the Mixin
     * performs, as a plain function of what has been registered.
     *
     * @param pakPath the resource name as the engine asked for it (any spelling; see "Keys")
     * @return the override, or {@code null} when the pak member / filesystem is to be used
     */
    public static Override resolveOverride(String pakPath) {
        Objects.requireNonNull(pakPath, "pakPath");
        if (sEmpty) {
            return null;
        }
        synchronized (LOCK) {
            Override cached = sResolved.get(pakPath);
            if (cached == null) {
                cached = compute(key(pakPath));
                sResolved.put(pakPath, cached);
            }
            return cached == NONE ? null : cached;
        }
    }

    /** The uncached resolution, over the normalized key. Must be called with {@link #LOCK} held. */
    private static Override compute(String key) {
        byte[] bytes = sBytes.get(key);
        if (bytes != null) {
            return new Override(bytes, null);
        }
        Path file = sFiles.get(key);
        if (file != null && Files.isRegularFile(file)) {
            return new Override(null, file);
        }
        for (Path root : sRoots) {
            Path resolved = resolveInRoot(root, key);
            if (resolved != null) {
                return new Override(null, resolved);
            }
        }
        return NONE;
    }

    /**
     * Resolves {@code key} (normalized, upper-case, {@code '/'}-separated) inside {@code root},
     * matching every segment case-insensitively against the real directory entries.
     *
     * @return the regular file, or {@code null} when any segment does not resolve to exactly one entry
     */
    private static Path resolveInRoot(Path root, String key) {
        Path current = root;
        for (String segment : key.split("/")) {
            if (segment.isEmpty()) {
                continue;
            }
            current = childIgnoreCase(current, segment);
            if (current == null) {
                return null;
            }
        }
        return current != null && Files.isRegularFile(current) ? current : null;
    }

    /**
     * The one child of {@code dir} whose name equals {@code name} ignoring case.
     *
     * @return that entry, or {@code null} when there is none or more than one (an ambiguous match is
     *     treated as "no match": picking one would be a coin toss)
     */
    private static Path childIgnoreCase(Path dir, String name) {
        Path direct = dir.resolve(name);
        if (Files.exists(direct)) {
            return direct;
        }
        if (!Files.isDirectory(dir)) {
            return null;
        }
        Path match = null;
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(dir)) {
            for (Path entry : entries) {
                if (entry.getFileName().toString().equalsIgnoreCase(name)) {
                    if (match != null) {
                        return null;
                    }
                    match = entry;
                }
            }
        } catch (IOException e) {
            return null;
        }
        return match;
    }

    /**
     * The key a pak path is stored and compared under: the equivalent of the engine's
     * {@code PakInterface.normalizePakPath} - {@code '\\'} to {@code '/'}, {@code .} / {@code ..}
     * resolved lexically, upper-case. A rooted path is not made relative to the resource folder
     * (this class has no resource folder): overrides are registered in pak-relative spelling.
     */
    static String key(String pakPath) {
        String path = pakPath.replace('\\', '/');
        path = lexicalNormal(path);
        if (path.startsWith("./")) {
            path = path.substring(2);
        }
        return path.toUpperCase(Locale.ROOT);
    }

    /** Equivalent of std::filesystem::path::lexically_normal for '/'-separated paths. */
    private static String lexicalNormal(String path) {
        if (path.isEmpty()) {
            return "";
        }
        boolean rooted = path.charAt(0) == '/';
        String[] parts = path.split("/");
        List<String> out = new ArrayList<>();
        for (String part : parts) {
            if (part.isEmpty() || part.equals(".")) {
                continue;
            }
            if (part.equals("..")) {
                if (!out.isEmpty() && !out.get(out.size() - 1).equals("..")) {
                    out.remove(out.size() - 1);
                } else if (!rooted) {
                    out.add("..");
                }
                continue;
            }
            out.add(part);
        }
        String joined = String.join("/", out);
        return rooted ? "/" + joined : joined;
    }

    /**
     * The content to serve for one pak path: exactly one of {@link #bytes()} / {@link #file()} is
     * non-{@code null} for a real override (the internal "no override" answer never leaves this
     * class).
     *
     * @param bytes the in-memory content, or {@code null}
     * @param file the file to read, or {@code null}
     */
    public record Override(byte[] bytes, Path file) {
    }
}
