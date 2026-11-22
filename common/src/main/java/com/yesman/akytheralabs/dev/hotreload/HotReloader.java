package com.yesman.akytheralabs.dev.hotreload;

import com.sun.management.HotSpotDiagnosticMXBean;
import com.yesman.akythera.core.reflection.ScriptModularizeHelper;
import com.yesman.akytheralabs.AkytheraLabs;
import com.yesman.akytheralabs.AkytheraProject;
import com.yesman.akytheralabs.FileSystemHelper;
import com.yesman.akytheralabs.dev.ProjectReloader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassDefinition;
import java.lang.instrument.Instrumentation;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.invoke.MethodHandles;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/// Compiles the source code of the reload units ([HotReloadUnit]) in game and swaps the bytes of the classes the
/// game runs ([Instrumentation#redefineClasses]), then reloads the project.
///
/// Covers added and removed fields and methods, and method body changes. Needs the JetBrains Runtime with
/// `-XX:+AllowEnhancedClassRedefinition` for anything beyond method body changes, `-javaagent:<AkytheraLabs jar>`
/// ([HotReloadAgent]) and the compile specs in [#SPECS_PROPERTY]. Static initializers do not run again, so registry
/// entries keep what they registered at launch, and objects keep their fields.
public final class HotReloader {
    /// The system property listing the compile spec files, separated by [File#pathSeparator].
    public static final String SPECS_PROPERTY = "akytheralabs.hotreload.specs";

    /// The reload units, one per [#SPECS_PROPERTY] file, in its order.
    private static final List<HotReloadUnit> UNITS = Arrays.stream(System.getProperty(SPECS_PROPERTY, "").split(File.pathSeparator))
        .filter(path -> !path.isBlank())
        .map(path -> new HotReloadUnit(Path.of(path)))
        .toList();

    private HotReloader() {}

    /// The setup pieces the game runs without, empty when hot reload can run.
    public static List<String> missingSetup() {
        List<String> missing = new ArrayList<>();

        if (HotReloadAgent.instrumentation() == null) missing.add("-javaagent:<AkytheraLabs jar>");

        try {
            HotSpotDiagnosticMXBean diagnostics = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);

            if (!"true".equals(diagnostics.getVMOption("AllowEnhancedClassRedefinition").getValue())) missing.add("-XX:+AllowEnhancedClassRedefinition");
        } catch (IllegalArgumentException e) {
            missing.add("JetBrains Runtime (JBR) 25 with -XX:+AllowEnhancedClassRedefinition");
        }

        if (UNITS.isEmpty()) missing.add("-D" + SPECS_PROPERTY + "=<compile spec files>");
        if (ToolProvider.getSystemJavaCompiler() == null) missing.add("A JDK, for javax.tools.JavaCompiler");

        return missing;
    }

    /// Compiles every unit off the render thread, swaps the classes behind the integrated server, and reloads the
    /// project. Completes on the render thread once the world has reloaded. Call it on the render thread with only
    /// the main editor screen open.
    public static CompletableFuture<HotReloadResult> reload() {
        List<HotReloadUnit> units = new ArrayList<>();

        for (HotReloadUnit unit : UNITS) {
            try {
                unit.readSpec();
                units.add(unit);
            } catch (IOException | RuntimeException e) {
                AkytheraLabs.LOGGER.warn("Skipping the hot reload of {}: its compile spec can't be read", unit.specFile(), e);
            }
        }

        if (units.isEmpty()) return CompletableFuture.completedFuture(HotReloadResult.failed("No compile spec can be read: " + UNITS.stream().map(HotReloadUnit::specFile).toList()));

        Minecraft minecraft = Minecraft.getInstance();
        Path scratch = minecraft.gameDirectory.toPath().resolve("akytheralabs").resolve("hot_reload");

        return CompletableFuture.supplyAsync(() -> compile(units, scratch), Util.backgroundExecutor())
            .thenComposeAsync(compilation -> {
                if (!compilation.errors().isEmpty()) return CompletableFuture.completedFuture(HotReloadResult.compileFailed(compilation.errors()));

                HotReloadResult result = apply(units, compilation);
                AkytheraProject project = AkytheraProject.getCurrentProjectOrNull();

                if (!result.succeeded() || project == null || minecraft.getSingleplayerServer() == null) return CompletableFuture.completedFuture(result);

                project.reloadAllEntriesFromDisk();

                return ProjectReloader.reload(project).thenApply(_ -> result);
            }, minecraft)
            .exceptionally(error -> {
                AkytheraLabs.LOGGER.error("Hot reload failed", error);

                return HotReloadResult.failed(error.toString());
            });
    }

    /// Compiles each unit into its own folder under `scratch`, in order. A unit compiles against the units before
    /// it, so a change in Akythera reaches the project compiled after it. Stops at the first unit that fails.
    public static Compilation compile(final List<HotReloadUnit> units, final Path scratch) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

        if (compiler == null) return new Compilation(List.of("No Java compiler in this runtime; run the game on a JDK"), List.of());

        List<String> errors = new ArrayList<>();
        List<Map<String, byte[]>> classes = new ArrayList<>();
        List<Path> previousOutputs = new ArrayList<>();

        for (int i = 0; i < units.size(); i++) {
            HotReloadUnit unit = units.get(i);
            Path output = scratch.resolve(Integer.toString(i));
            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();

            try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
                FileSystemHelper.deleteRecursive(output);
                Files.createDirectories(output);

                List<Path> classpath = new ArrayList<>(previousOutputs);
                classpath.addAll(unit.classpath());
                files.setLocationFromPaths(StandardLocation.CLASS_OUTPUT, List.of(output));
                files.setLocationFromPaths(StandardLocation.CLASS_PATH, classpath);

                List<String> options = new ArrayList<>(unit.options());
                options.add("-proc:none");
                options.add("-nowarn");

                boolean compiled = compiler.getTask(null, files, diagnostics, options, null, files.getJavaFileObjectsFromPaths(unit.sourceFiles())).call();

                for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                    if (diagnostic.getKind() != Diagnostic.Kind.ERROR) continue;

                    String source = diagnostic.getSource() == null ? unit.specFile().toString() : diagnostic.getSource().getName();
                    errors.add(source + ":" + diagnostic.getLineNumber() + ": " + diagnostic.getMessage(Locale.ROOT));
                }

                if (!compiled) {
                    if (errors.isEmpty()) errors.add("The compiler failed on " + unit.specFile() + " without an error message");
                    break;
                }

                classes.add(HotReloadUnit.readClasses(output));
                previousOutputs.add(output);
            } catch (IOException | RuntimeException e) {
                errors.add(unit.specFile() + ": " + e);
                break;
            }
        }

        return new Compilation(errors, classes);
    }

    /// Swaps in what `compilation` compiled for `units`, while the integrated server waits for it.
    ///
    /// A changed class the game has loaded is redefined. One it has not loaded yet is written into the unit's
    /// output folder; when the class loader would still not read those bytes (the unit is a jar), it is defined
    /// through a lookup of a loaded class of its package. A class under a mixin package is refused.
    public static HotReloadResult apply(final List<HotReloadUnit> units, final Compilation compilation) {
        Instrumentation instrumentation = HotReloadAgent.instrumentation();

        if (instrumentation == null) return HotReloadResult.failed("The game runs without -javaagent:<AkytheraLabs jar>");

        Set<String> compiledNames = new LinkedHashSet<>();
        compilation.classes().forEach(classes -> compiledNames.addAll(classes.keySet()));

        Map<String, List<Class<?>>> loaded = new HashMap<>();
        Map<String, Class<?>> packageNeighbors = new HashMap<>();

        for (Class<?> loadedClass : instrumentation.getAllLoadedClasses()) {
            if (loadedClass.isHidden() || loadedClass.isArray() || loadedClass.isPrimitive()) continue;

            if (compiledNames.contains(loadedClass.getName())) loaded.computeIfAbsent(loadedClass.getName(), _ -> new ArrayList<>()).add(loadedClass);

            packageNeighbors.putIfAbsent(System.identityHashCode(loadedClass.getClassLoader()) + "/" + loadedClass.getPackageName(), loadedClass);
        }

        List<String> redefined = new ArrayList<>();
        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        List<String> refused = new ArrayList<>();
        List<ClassDefinition> definitions = new ArrayList<>();
        List<Runnable> afterSwap = new ArrayList<>();

        try {
            for (int i = 0; i < units.size(); i++) {
                HotReloadUnit unit = units.get(i);
                Map<String, byte[]> before = unit.loadedClasses();
                Map<String, byte[]> after = compilation.classes().get(i);
                Map<String, byte[]> unloaded = new LinkedHashMap<>();
                Map<String, byte[]> swapped = new HashMap<>(after);
                Set<String> mixinPackages = unit.mixinPackages();
                ClassLoader loader = loaded.values().stream().flatMap(List::stream)
                    .filter(loadedClass -> after.containsKey(loadedClass.getName()))
                    .findFirst()
                    .<ClassLoader>map(Class::getClassLoader)
                    .orElse(HotReloader.class.getClassLoader());

                for (Map.Entry<String, byte[]> entry : after.entrySet()) {
                    String name = entry.getKey();
                    byte[] previous = before.get(name);

                    if (Arrays.equals(previous, entry.getValue())) continue;

                    if (mixinPackages.stream().anyMatch(mixinPackage -> name.startsWith(mixinPackage + "."))) {
                        refused.add(name + " (mixin package)");
                        if (previous == null) swapped.remove(name); else swapped.put(name, previous);
                        continue;
                    }

                    if (previous == null) added.add(name);

                    List<Class<?>> loadedCopies = loaded.get(name);

                    if (loadedCopies == null) {
                        unloaded.put(name, entry.getValue());
                    } else {
                        loadedCopies.forEach(loadedClass -> definitions.add(new ClassDefinition(loadedClass, entry.getValue())));
                        redefined.add(name);
                    }

                    Path file = unit.output().resolve(name.replace('.', '/') + ".class");
                    Files.createDirectories(file.getParent());
                    Files.write(file, entry.getValue());
                }

                for (String name : before.keySet()) {
                    if (after.containsKey(name)) continue;

                    removed.add(name);
                    afterSwap.add(() -> FileSystemHelper.deleteRecursive(unit.output().resolve(name.replace('.', '/') + ".class")));
                }

                // Defined in passes, so a superclass the same reload adds is defined before its subclasses
                boolean progressed = true;

                while (!unloaded.isEmpty() && progressed) {
                    progressed = false;

                    for (Map.Entry<String, byte[]> entry : List.copyOf(unloaded.entrySet())) {
                        String path = entry.getKey().replace('.', '/') + ".class";

                        try (InputStream stream = loader.getResourceAsStream(path)) {
                            if (stream != null && Arrays.equals(stream.readAllBytes(), entry.getValue())) {
                                unloaded.remove(entry.getKey());
                                progressed = true;
                                continue;
                            }
                        }

                        String packageName = entry.getKey().contains(".") ? entry.getKey().substring(0, entry.getKey().lastIndexOf('.')) : "";
                        Class<?> neighbor = packageNeighbors.get(System.identityHashCode(loader) + "/" + packageName);

                        if (neighbor == null) continue;

                        try {
                            MethodHandles.privateLookupIn(neighbor, MethodHandles.lookup()).defineClass(entry.getValue());
                            unloaded.remove(entry.getKey());
                            progressed = true;
                        } catch (NoClassDefFoundError e) {
                            // Its superclass is defined by a later pass
                        }
                    }
                }

                for (String name : unloaded.keySet()) {
                    refused.add(name + " (can't be defined: no loaded class in its package)");
                    if (before.containsKey(name)) swapped.put(name, before.get(name)); else swapped.remove(name);
                }

                afterSwap.add(() -> unit.setLoadedClasses(swapped));
            }
        } catch (IOException | ReflectiveOperationException | LinkageError | SecurityException e) {
            AkytheraLabs.LOGGER.error("Failed to prepare the hot reload", e);

            return HotReloadResult.failed("Failed to prepare the swap: " + e);
        }

        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        Runnable swap = () -> {
            try {
                if (!definitions.isEmpty()) instrumentation.redefineClasses(definitions.toArray(ClassDefinition[]::new));
            } catch (UnsupportedOperationException e) {
                throw new IllegalStateException("The JVM refused a class shape change (added or removed fields or methods). Run the game on the JetBrains Runtime with -XX:+AllowEnhancedClassRedefinition", e);
            } catch (ClassNotFoundException | UnmodifiableClassException e) {
                throw new IllegalStateException("The JVM refused to redefine a class: " + e, e);
            }

            afterSwap.forEach(Runnable::run);
            ScriptModularizeHelper.clearBlueprintLayouts();
        };

        try {
            if (server == null) swap.run(); else server.submit(swap).join();
        } catch (RuntimeException | LinkageError e) {
            AkytheraLabs.LOGGER.error("Hot reload failed to swap the classes", e);
            Throwable cause = e instanceof CompletionException && e.getCause() != null ? e.getCause() : e;

            return HotReloadResult.failed(cause.getMessage() == null ? cause.toString() : cause.getMessage());
        }

        AkytheraLabs.LOGGER.info("Hot reload swapped {} classes, added {}, removed {}, refused {}", redefined.size(), added.size(), removed.size(), refused.size());

        return new HotReloadResult(List.of(), redefined, added, removed, refused, null);
    }

    /// What [#compile] produced.
    ///
    /// @param errors  the compile errors, `file:line: message`
    /// @param classes the class files of each unit that compiled, by binary name, in unit order
    public record Compilation(List<String> errors, List<Map<String, byte[]>> classes) {}
}
