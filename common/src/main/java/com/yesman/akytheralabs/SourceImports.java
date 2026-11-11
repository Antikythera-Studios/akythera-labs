package com.yesman.akytheralabs;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/// Writes the imports of a generated source file. A type the code spells by its qualified name is imported and
/// shortened to its simple name, which is how a template names one of two classes sharing a simple name. Every other
/// simple name is resolved through the names the open [SourceExport] spelled, then [#PREFERRED], then the indexed
/// classes of [#ROOTS].
@NullMarked
public final class SourceImports {
    private SourceImports() {}

    /// The indexed class sets, in the order a simple name several of them hold prefers them: the built-in ones, then
    /// the modules [#registerModule] adds.
    private static final List<Root> ROOTS = new CopyOnWriteArrayList<>(List.of(
        Root.builtIn("com.yesman.akythera.core.Akythera", "com/yesman/akythera"),
        Root.builtIn("java.lang.Object", "java/util"),
        Root.builtIn("com.mojang.serialization.Codec", "com/mojang/serialization"),
        Root.builtIn("com.mojang.datafixers.util.Either", "com/mojang/datafixers"),
        Root.builtIn("com.google.common.collect.ImmutableMap", "com/google/common"),
        Root.builtIn("net.minecraft.resources.Identifier", "net/minecraft"),
        Root.builtIn("net.minecraft.resources.Identifier", "com/mojang"),
        Root.builtIn("org.joml.Quaterniond", "org/joml"),
        Root.builtIn("org.jspecify.annotations.Nullable", "org/jspecify")
    ));

    /// The class a simple name means when the engine holds two classes of that name.
    private static final Map<String, String> PREFERRED = Map.of(
        "ScriptType", "com.yesman.akythera.api.data.modularize.ScriptType",
        "ScriptFunction", "com.yesman.akythera.api.data.modularize.ScriptFunction",
        "SequenceNode", "com.yesman.akythera.api.data.modularize.node.SequenceNode"
    );

    /// A simple name not preceded by a `.`, an identifier character or a `$`.
    private static final Pattern SIMPLE_NAME = Pattern.compile("(?<![\\w$.])([A-Z][A-Za-z0-9_]*)");
    /// A qualified class name: two or more lower-case package segments, then the class.
    private static final Pattern QUALIFIED_NAME = Pattern.compile("(?<![\\w$.])((?:[a-z_][A-Za-z0-9_]*\\.){2,})([A-Z][A-Za-z0-9_]*)");
    private static final Pattern DECLARATION = Pattern.compile("\\b(?:class|interface|enum|record)\\s+([A-Z][A-Za-z0-9_]*)");
    private static final Pattern PACKAGE = Pattern.compile("(?m)^package\\s+([\\w.]+)\\s*;[ \\t]*\\R?");

    /// Simple name -> qualified names, one map per root of [#ROOTS]. Built on first use.
    private static @Nullable List<Map<String, List<String>>> index;

    /// Indexes every class under the package of `anchorClass`, read from the jar or directory it is loaded from. A
    /// mod calls this so the classes its source templates and component holders spell are imported.
    public static synchronized void registerModule(final Class<?> anchorClass) {
        ROOTS.add(new Root(anchorClass.getName(), anchorClass.getPackageName().replace('.', '/'), anchorClass.getClassLoader()));
        index = null;
    }

    /// `source` with its imports written under the package line. A simple name that resolves to no class, or to
    /// several, is listed in an `Unresolved imports` comment for the IDE to finish.
    public static String insert(String source) {
        Matcher packageLine = PACKAGE.matcher(source);
        if (!packageLine.find()) return source;

        String packageName = packageLine.group(1);
        String code = withoutCommentsAndLiterals(source);
        Set<String> declared = new HashSet<>();
        Set<String> imports = new TreeSet<>();
        Set<String> unresolved = new TreeSet<>();

        for (Matcher declaration = DECLARATION.matcher(code); declaration.find(); ) {
            declared.add(declaration.group(1));
        }

        // Qualified names, by simple name. A simple name spelled by two of them, or declared here, stays qualified.
        Map<String, Set<String>> qualified = new HashMap<>();

        for (Matcher name = QUALIFIED_NAME.matcher(code); name.find(); ) {
            String qualifiedName = name.group(1) + name.group(2);

            if (isClass(qualifiedName)) qualified.computeIfAbsent(name.group(2), _ -> new HashSet<>()).add(qualifiedName);
        }

        Map<String, String> explicit = new HashMap<>();
        qualified.forEach((simpleName, names) -> {
            if (names.size() == 1 && !declared.contains(simpleName)) explicit.put(simpleName, names.iterator().next());
        });

        if (!explicit.isEmpty()) {
            StringBuilder shortened = new StringBuilder(source.length());
            int copied = 0;

            for (Matcher name = QUALIFIED_NAME.matcher(code); name.find(); ) {
                if (!(name.group(1) + name.group(2)).equals(explicit.get(name.group(2)))) continue;

                shortened.append(source, copied, name.start()).append(name.group(2));
                copied = name.end();
            }

            source = shortened.append(source, copied, source.length()).toString();
            code = withoutCommentsAndLiterals(source);
            packageLine = PACKAGE.matcher(source);
            packageLine.find();
        }

        for (Matcher name = SIMPLE_NAME.matcher(code); name.find(); ) {
            String simpleName = name.group(1);

            if (simpleName.length() == 1 || declared.contains(simpleName) || inJavaLang(simpleName)) continue;

            String qualifiedName = explicit.containsKey(simpleName) ? explicit.get(simpleName) : resolve(simpleName);

            // An all-caps name is a constant unless a class carries it (`RGBA`), so only a resolved one counts.
            if (qualifiedName == null) {
                if (!simpleName.equals(simpleName.toUpperCase())) unresolved.add(simpleName);
            } else if (!qualifiedName.substring(0, qualifiedName.lastIndexOf('.')).equals(packageName)) {
                imports.add(qualifiedName);
            }
        }

        StringBuilder block = new StringBuilder();
        imports.stream().filter(name -> !name.startsWith("java.")).forEach(name -> block.append("import ").append(name).append(";\n"));

        List<String> javaImports = imports.stream().filter(name -> name.startsWith("java.")).toList();

        if (!javaImports.isEmpty()) {
            if (!block.isEmpty()) block.append('\n');
            javaImports.forEach(name -> block.append("import ").append(name).append(";\n"));
        }

        if (!unresolved.isEmpty()) {
            if (!block.isEmpty()) block.append('\n');
            block.append("// Unresolved imports: ").append(String.join(", ", unresolved)).append('\n');
        }

        String head = source.substring(0, packageLine.end());
        String rest = source.substring(packageLine.end()).replaceFirst("^(\\R)+", "");

        return block.isEmpty() ? head + "\n" + rest : head + "\n" + block + "\n" + rest;
    }

    /// The qualified name `simpleName` means, or null when no class or several carry it.
    private static @Nullable String resolve(final String simpleName) {
        Set<String> spelled = SourceExport.spelled().get(simpleName);

        if (spelled != null && spelled.size() == 1) return spelled.iterator().next();
        if (PREFERRED.containsKey(simpleName)) return PREFERRED.get(simpleName);

        for (Map<String, List<String>> classes : index()) {
            List<String> candidates = classes.get(simpleName);

            if (candidates != null) return candidates.size() == 1 ? candidates.getFirst() : null;
        }

        return null;
    }

    private static boolean inJavaLang(final String simpleName) {
        return isClass("java.lang." + simpleName);
    }

    /// Whether `qualifiedName` names an indexed or loadable class, or a class the open [SourceExport] writes.
    private static boolean isClass(final String qualifiedName) {
        if (SourceExport.spelled().values().stream().anyMatch(names -> names.contains(qualifiedName))) return true;

        String simpleName = qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);

        if (index().stream().anyMatch(classes -> classes.getOrDefault(simpleName, List.of()).contains(qualifiedName))) return true;

        try {
            Class.forName(qualifiedName, false, SourceImports.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    private static synchronized List<Map<String, List<String>>> index() {
        if (index == null) {
            List<Map<String, List<String>>> built = new ArrayList<>();

            for (Root root : ROOTS) {
                Map<String, List<String>> classes = new HashMap<>();

                try {
                    root.qualifiedNames().forEach(name ->
                        classes.computeIfAbsent(name.substring(name.lastIndexOf('.') + 1), _ -> new ArrayList<>()).add(name));
                } catch (IOException | URISyntaxException | ClassNotFoundException | RuntimeException | LinkageError e) {
                    AkytheraLabs.LOGGER.warn("Could not index {} classes for source imports", root.prefix(), e);
                }

                built.add(classes);
            }

            index = built;
        }

        return index;
    }

    /// `source` with every comment, string and character literal blanked to spaces, its line breaks and offsets kept.
    private static String withoutCommentsAndLiterals(final String source) {
        char[] code = source.toCharArray();
        int i = 0;

        while (i < code.length) {
            char c = code[i];
            int end;

            if (c == '/' && i + 1 < code.length && code[i + 1] == '/') {
                end = source.indexOf('\n', i);
                if (end < 0) end = code.length;
            } else if (c == '/' && i + 1 < code.length && code[i + 1] == '*') {
                end = source.indexOf("*/", i + 2);
                end = end < 0 ? code.length : end + 2;
            } else if (c == '"' || c == '\'') {
                end = i + 1;

                while (end < code.length && source.charAt(end) != c && source.charAt(end) != '\n') {
                    end += source.charAt(end) == '\\' ? 2 : 1;
                }

                end = Math.min(end + 1, code.length);
            } else {
                i++;
                continue;
            }

            for (int j = i; j < end; j++) {
                if (code[j] != '\n') code[j] = ' ';
            }

            i = end;
        }

        return new String(code);
    }

    /// The top-level classes under `prefix` of the class container the class `anchorName` is loaded from by `loader`.
    private record Root(String anchorName, String prefix, @Nullable ClassLoader loader) {
        static Root builtIn(final String anchorName, final String prefix) {
            return new Root(anchorName, prefix, SourceImports.class.getClassLoader());
        }

        Stream<String> qualifiedNames() throws IOException, URISyntaxException, ClassNotFoundException {
            Class<?> anchor = Class.forName(anchorName, false, loader);
            URL url = anchor.getResource(anchor.getSimpleName() + ".class");
            if (url == null) return Stream.empty();

            URI uri = url.toURI();
            Path classFile;

            try {
                classFile = Path.of(uri);
            } catch (FileSystemNotFoundException e) {
                try {
                    FileSystems.newFileSystem(uri, Map.of());
                } catch (FileSystemAlreadyExistsException ignored) {
                    // Opened by another thread in between.
                }

                classFile = Path.of(uri);
            }

            Path containerRoot = classFile.getParent();

            for (int segments = anchor.getPackageName().split("\\.").length; segments > 0; segments--) {
                containerRoot = containerRoot.getParent();
            }

            Path packageRoot = containerRoot.resolve(prefix);
            if (!Files.isDirectory(packageRoot)) return Stream.empty();

            Path base = containerRoot;

            try (Stream<Path> files = Files.walk(packageRoot)) {
                return files
                    .map(path -> base.relativize(path).toString().replace('\\', '/'))
                    .filter(path -> path.endsWith(".class") && !path.contains("$") && !path.endsWith("package-info.class") && !path.endsWith("module-info.class"))
                    .map(path -> path.substring(0, path.length() - ".class".length()).replace('/', '.'))
                    .toList()
                    .stream();
            }
        }
    }
}
