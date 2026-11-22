package com.yesman.akytheralabs.dev.hotreload;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.yesman.akytheralabs.AkytheraLabs;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/// One Gradle source set the game loads classes from, read from the compile spec its build writes
/// (`writeHotReloadSpec`, `build/akytheralabs/hotreload_spec.json`).
public final class HotReloadUnit {
    /// The compile spec file, read again on every reload.
    private final Path specFile;

    private List<SourceRoot> sources = List.of();
    private List<Path> classpath = List.of();
    private List<String> options = List.of();

    /// The folder Gradle compiles the unit's classes into. Empty until [#readSpec].
    private Path output = Path.of("");

    /// The folder Gradle processes the unit's resources into, where its mixin configs are.
    private @Nullable Path resources;

    /// The bytes of each class as the game holds them, by binary name. Read from [#output] on the first reload.
    private @Nullable Map<String, byte[]> loadedClasses;

    public HotReloadUnit(final Path specFile) {
        this.specFile = specFile;
    }

    /// Reads [#specFile] again.
    public void readSpec() throws IOException {
        JsonObject spec;

        try (Reader reader = Files.newBufferedReader(specFile)) {
            spec = JsonParser.parseReader(reader).getAsJsonObject();
        }

        classpath = strings(spec.getAsJsonArray("classpath")).stream().map(Path::of).toList();
        options = strings(spec.getAsJsonArray("options"));
        output = Path.of(spec.get("output").getAsString());
        resources = spec.has("resources") ? Path.of(spec.get("resources").getAsString()) : null;

        List<SourceRoot> roots = new ArrayList<>();

        for (JsonElement element : spec.getAsJsonArray("sources")) {
            JsonObject source = element.getAsJsonObject();
            roots.add(new SourceRoot(Path.of(source.get("dir").getAsString()), matchers(source.getAsJsonArray("includes")), matchers(source.getAsJsonArray("excludes"))));
        }

        sources = roots;
    }

    /// Every `.java` file of the unit's source roots that the roots' patterns leave in.
    public List<Path> sourceFiles() throws IOException {
        List<Path> files = new ArrayList<>();

        for (SourceRoot root : sources) {
            if (!Files.isDirectory(root.dir())) continue;

            try (Stream<Path> walk = Files.walk(root.dir())) {
                walk.filter(file -> file.toString().endsWith(".java") && Files.isRegularFile(file))
                    .filter(file -> root.accepts(root.dir().relativize(file)))
                    .forEach(files::add);
            }
        }

        return files;
    }

    /// The packages the unit's mixin configs (`*.mixins.json` in [#resources]) declare.
    public Set<String> mixinPackages() {
        Set<String> packages = new HashSet<>();

        if (resources == null || !Files.isDirectory(resources)) return packages;

        try (Stream<Path> files = Files.list(resources)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".mixins.json")).toList()) {
                try (Reader reader = Files.newBufferedReader(file)) {
                    JsonObject config = JsonParser.parseReader(reader).getAsJsonObject();

                    if (config.has("package")) packages.add(config.get("package").getAsString());
                }
            }
        } catch (IOException | RuntimeException e) {
            AkytheraLabs.LOGGER.warn("Failed to read the mixin configs in {}", resources, e);
        }

        return packages;
    }

    /// The bytes of each class as the game holds them, by binary name.
    public Map<String, byte[]> loadedClasses() throws IOException {
        if (loadedClasses == null) loadedClasses = readClasses(output);

        return loadedClasses;
    }

    public void setLoadedClasses(final Map<String, byte[]> loadedClasses) {
        this.loadedClasses = loadedClasses;
    }

    public Path specFile() {
        return specFile;
    }

    public List<Path> classpath() {
        return classpath;
    }

    public List<String> options() {
        return options;
    }

    public Path output() {
        return output;
    }

    /// Every class file under `folder`, by binary name. Empty when the folder is missing.
    public static Map<String, byte[]> readClasses(final Path folder) throws IOException {
        Map<String, byte[]> classes = new HashMap<>();

        if (!Files.isDirectory(folder)) return classes;

        try (Stream<Path> walk = Files.walk(folder)) {
            for (Path file : walk.filter(path -> path.toString().endsWith(".class")).toList()) {
                String relative = folder.relativize(file).toString().replace('\\', '/');
                classes.put(relative.substring(0, relative.length() - ".class".length()).replace('/', '.'), Files.readAllBytes(file));
            }
        }

        return classes;
    }

    private static List<String> strings(final @Nullable JsonArray array) {
        List<String> strings = new ArrayList<>();

        if (array != null) array.forEach(element -> strings.add(element.getAsString()));

        return strings;
    }

    /// Glob matchers for Gradle's Ant-style `patterns`. `**/` also matches no folder, as in Ant.
    private static List<PathMatcher> matchers(final @Nullable JsonArray patterns) {
        List<PathMatcher> matchers = new ArrayList<>();

        for (String pattern : strings(patterns)) {
            String glob = pattern.endsWith("/") ? pattern + "**" : pattern;
            matchers.add(FileSystems.getDefault().getPathMatcher("glob:" + glob));

            if (glob.startsWith("**/")) matchers.add(FileSystems.getDefault().getPathMatcher("glob:" + glob.substring(3)));
        }

        return matchers;
    }

    /// A source folder and the Ant-style patterns Gradle filters it by.
    private record SourceRoot(Path dir, List<PathMatcher> includes, List<PathMatcher> excludes) {
        private boolean accepts(final Path relative) {
            Path path = Path.of(relative.toString().replace('\\', '/'));

            return (includes.isEmpty() || includes.stream().anyMatch(matcher -> matcher.matches(path)))
                && excludes.stream().noneMatch(matcher -> matcher.matches(path));
        }
    }
}
