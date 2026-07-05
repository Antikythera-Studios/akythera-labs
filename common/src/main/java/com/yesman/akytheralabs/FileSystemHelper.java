package com.yesman.akytheralabs;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/// One stop for project file I/O. Wraps `java.nio.file.Files` so that:
/// 1. callers don't have to remember `createDirectories(parent)` before every write,
/// 2. failures funnel into one logger,
/// 3. path-string conversion (id `/` ↔ OS separator) lives in one place,
/// 4. recursive delete uses one shape (reverse-order walk).
///
/// Registry / accessor bookkeeping stays in [AkytheraProject] — this helper
/// only touches the disk.
@NullMarked
public final class FileSystemHelper {
    private FileSystemHelper() {}

    private static final Gson GSON_PRETTY = new GsonBuilder().setPrettyPrinting().create();

    // ---------- path-string conversion ----------

    /// OS separator → forward-slash. Identifiers always store `/`; Windows
    /// `Path.toString()` gives back `\`.
    public static String toIdString(String os) {
        return os.replace(File.separatorChar, '/');
    }

    public static String toIdString(Path p) {
        return p.toString().replace(File.separatorChar, '/');
    }

    /// Forward-slash → OS separator. Use when an identifier path
    /// must be resolved against a real directory.
    public static String toOsString(String id) {
        return id.replace('/', File.separatorChar);
    }

    /// Relative path of `file` under `root`, joined with `/`. Empty when
    /// `file` IS `root`.
    public static String relativeIdPath(Path root, Path file) {
        return toIdString(root.relativize(file));
    }

    // ---------- name + extension ----------

    /// Stem (everything before the last `.`). `"foo.json"` → `"foo"`,
    /// `"folder"` → `"folder"`.
    public static String stem(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(0, dot) : fileName;
    }

    /// Extension WITH the leading dot, or empty. `"foo.json"` → `".json"`.
    public static String extension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot) : "";
    }

    /// `parent/<newStem><.ext>` where `.ext` matches `original`'s extension.
    public static Path withNewStem(Path original, String newStem) {
        Path parent = original.getParent();
        String name = newStem + extension(original.getFileName().toString());
        return parent == null ? Path.of(name) : parent.resolve(name);
    }

    /// Returns `baseName` if no existing file `directory/<baseName><extension>`
    /// is present; otherwise `baseName_N` with the smallest free `N`.
    ///
    /// `extension` includes the leading dot, or is empty for directories.
    public static String findNextAvailableName(Path directory, String baseName, String extension) {
        if (!Files.exists(directory.resolve(baseName + extension))) return baseName;

        int n = 1;
        while (Files.exists(directory.resolve(baseName + "_" + n + extension))) n++;

        return baseName + "_" + n;
    }

    // ---------- write / read / copy ----------

    /// Ensure parent exists, then write bytes. Returns `false` on IO failure
    /// (and logs).
    public static boolean writeBytes(Path file, byte[] bytes) {
        try {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.write(file, bytes);

            return true;
        } catch (IOException e) {
            AkytheraLabs.LOGGER.error("Failed to write {}", file, e);
            return false;
        }
    }

    public static boolean writeString(Path file, String content) {
        try {
            Path parent = file.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.writeString(file, content);

            return true;
        } catch (IOException e) {
            AkytheraLabs.LOGGER.error("Failed to write {}", file, e);
            return false;
        }
    }

    /// Encode `value` via `codec` to pretty-printed JSON and write at `file`.
    /// Replaces the old `createDataObject(Codec, T...)` stub — that signature
    /// can't actually write because it has no path.
    public static <T> boolean writeJson(Path file, Codec<T> codec, T value) {
        try {
            JsonElement json = codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
            return writeString(file, GSON_PRETTY.toJson(json));
        } catch (Exception e) {
            AkytheraLabs.LOGGER.error("Failed to encode {}", file, e);
            return false;
        }
    }

    public static byte @Nullable [] readBytes(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            AkytheraLabs.LOGGER.warn("Failed to read {}", file, e);
            return null;
        }
    }

    public static boolean copyReplace(Path src, Path dst) {
        try {
            Path parent = dst.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);

            return true;
        } catch (IOException e) {
            AkytheraLabs.LOGGER.error("Failed to copy {} -> {}", src, dst, e);
            return false;
        }
    }

    // ---------- move ----------

    /// `Files.move` after ensuring the destination's parent exists. Returns
    /// `false` on failure (logged).
    public static boolean move(Path from, Path to) {
        try {
            Path parent = to.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.move(from, to);

            return true;
        } catch (IOException e) {
            AkytheraLabs.LOGGER.error("Failed to move {} -> {}", from, to, e);
            return false;
        }
    }

    /// Like [#move] but uses [StandardCopyOption#REPLACE_EXISTING].
    public static boolean moveReplace(Path from, Path to) {
        try {
            Path parent = to.getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);

            return true;
        } catch (IOException e) {
            AkytheraLabs.LOGGER.error("Failed to move {} -> {}", from, to, e);
            return false;
        }
    }

    /// Bulk move into a destination directory. Each `file` lands at
    /// `to.resolve(file.getFileName())`. Returns the list of
    /// `(src, dst)` pairs that actually moved — drop-in for history callers
    /// that need to record what they did so they can undo it.
    ///
    /// Returns [com.mojang.datafixers.util.Pair] per the project convention
    /// for ad-hoc two-value tuples — destructure with `.getFirst()` /
    /// `.getSecond()`.
    public static List<Pair<Path, Path>> moveInto(Path to, Path... files) {
        List<Pair<Path, Path>> moved = new ArrayList<>(files.length);

        try {
            Files.createDirectories(to);
        } catch (IOException e) {
            AkytheraLabs.LOGGER.error("Failed to mkdir {}", to, e);

            return moved;
        }

        for (Path src : files) {
            Path dst = to.resolve(src.getFileName());
            if (move(src, dst)) moved.add(Pair.of(src, dst));
        }

        return moved;
    }

    // ---------- overwrite rollback ----------

    /// Move an existing `file` to a unique sibling backup so an overwrite can be undone.
    /// Returns the backup path, or `null` when `file` doesn't exist (nothing to snapshot).
    /// The backup stays in the same directory so the move never crosses a filesystem.
    public static @Nullable Path moveAside(Path file) {
        if (!Files.exists(file)) return null;

        Path backup = file.resolveSibling(file.getFileName() + ".bak-" + System.nanoTime());
        return move(file, backup) ? backup : null;
    }

    /// Undo a write guarded by [#moveAside]: restore the snapshot taken beforehand, or —
    /// when there was none (a fresh file) — delete whatever was written.
    public static void rollback(@Nullable Path backup, Path file) {
        if (backup != null) {
            moveReplace(backup, file);
        } else {
            deleteRecursive(file);
        }
    }

    /// Discard a snapshot taken by [#moveAside] once the write has succeeded.
    public static void commit(@Nullable Path backup) {
        if (backup != null) deleteRecursive(backup);
    }

    // ---------- zip ----------

    /// Write a zip archive at `zipFile` from a set of directory trees plus
    /// literal entries.
    ///
    /// Every regular file under each `(prefix, sourceDir)` pair in `trees`
    /// becomes a zip entry at `<prefix>/<path relative to sourceDir>`
    /// (always forward-slash, per the zip spec). `literalEntries` are written
    /// verbatim at their key path — used for the generated `pack.mcmeta`.
    /// Missing source directories are skipped. Parent dirs of `zipFile` are
    /// created. Returns `false` on IO failure (logged).
    public static boolean zip(
        Path zipFile,
        Map<String, byte[]> literalEntries,
        List<Pair<String, Path>> trees
    ) {
        try {
            Path parent = zipFile.getParent();
            if (parent != null) Files.createDirectories(parent);

            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
                for (Map.Entry<String, byte[]> entry : literalEntries.entrySet()) {
                    zos.putNextEntry(new ZipEntry(entry.getKey()));
                    zos.write(entry.getValue());
                    zos.closeEntry();
                }

                for (Pair<String, Path> tree : trees) {
                    String prefix = tree.getFirst();
                    Path dir = tree.getSecond();
                    if (!Files.isDirectory(dir)) continue;

                    List<Path> files;
                    try (Stream<Path> walk = Files.walk(dir)) {
                        files = walk.filter(Files::isRegularFile).toList();
                    }

                    for (Path file : files) {
                        zos.putNextEntry(new ZipEntry(prefix + "/" + relativeIdPath(dir, file)));
                        zos.write(Files.readAllBytes(file));
                        zos.closeEntry();
                    }
                }
            }

            return true;
        } catch (IOException e) {
            AkytheraLabs.LOGGER.error("Failed to write zip {}", zipFile, e);
            return false;
        }
    }

    // ---------- delete ----------

    /// Reverse-order recursive delete. Missing paths are no-ops.
    /// Per-entry failures are logged and skipped (best-effort).
    ///
    /// Does NOT touch registries / accessors — see
    /// [AkytheraProject#deletePathRecursive] for the registry-aware wrapper.
    public static void deleteRecursive(Path path) {
        if (!Files.exists(path)) return;

        try (Stream<Path> walk = Files.walk(path)) {
            walk.sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        } catch (IOException e) {
            AkytheraLabs.LOGGER.error("Failed to delete {}", path, e);
        }
    }

    /// Bulk delete — calls [#deleteRecursive] per arg.
    public static void deleteRecursive(Path... paths) {
        for (Path p : paths) deleteRecursive(p);
    }

    // ---------- walk ----------

    /// Walk regular files under `dir` and apply `handler`. Silent if `dir`
    /// is missing or not a directory.
    public static void forEachFile(Path dir, Consumer<Path> handler) {
        if (!Files.isDirectory(dir)) return;

        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile).forEach(handler);
        } catch (IOException e) {
            AkytheraLabs.LOGGER.warn("Failed to walk {}", dir, e);
        }
    }

    /// Walk regular files matching `filter` under `dir`.
    public static void forEachFile(Path dir, Predicate<Path> filter, Consumer<Path> handler) {
        if (!Files.isDirectory(dir)) return;

        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile).filter(filter).forEach(handler);
        } catch (IOException e) {
            AkytheraLabs.LOGGER.warn("Failed to walk {}", dir, e);
        }
    }

    /// Pre-order walk of EVERY entry (dirs + files). Used by the restore
    /// builder so dirs come before their contents.
    public static void forEachEntryPreOrder(Path dir, Consumer<Path> handler) {
        if (!Files.exists(dir)) return;

        try (Stream<Path> walk = Files.walk(dir)) {
            walk.forEach(handler);
        } catch (IOException e) {
            AkytheraLabs.LOGGER.warn("Failed to walk {}", dir, e);
        }
    }

    /// Count regular files under `dir`. 0 when missing.
    public static int countFiles(@Nullable Path dir) {
        if (dir == null || !Files.isDirectory(dir)) return 0;

        try (Stream<Path> s = Files.walk(dir)) {
            return (int) s.filter(Files::isRegularFile).count();
        } catch (IOException e) {
            return 0;
        }
    }
}