package com.yesman.akytheralabs;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/// One source export, open on the exporting thread. It keeps the qualified name of every type the output spells,
/// which [SourceImports] writes the imports with. The engine's registry lookups see the open project's entries
/// throughout ([AkytheraProject#withProjectLookups]), so a codec writes a project entry by its id.
@NullMarked
public final class SourceExport {
    private static final ThreadLocal<@Nullable SourceExport> CURRENT = new ThreadLocal<>();

    /// The base package the export writes into.
    private final String packageName;

    /// The qualified names of the types the output spelled, by simple name.
    private final Map<String, Set<String>> spelled = new HashMap<>();

    private SourceExport(final String packageName) {
        this.packageName = packageName;
    }

    /// Runs `export` inside a new export.
    public static void run(final String packageName, final Runnable export) {
        SourceExport previous = CURRENT.get();
        CURRENT.set(new SourceExport(packageName));

        try {
            AkytheraProject.withProjectLookups(() -> {
                export.run();
                return null;
            });
        } finally {
            CURRENT.set(previous);
        }
    }

    /// Records that the output spells `type` by its simple name.
    public static void spell(final Class<?> type) {
        if (type.isPrimitive() || type.isArray() || type.getEnclosingClass() != null || type.getPackageName().equals("java.lang")) return;

        spell(type.getSimpleName(), type.getName());
    }

    /// Records that the output spells the class the export writes as `simpleName` under the sub-directory `dir`.
    public static void spellGenerated(final String dir, final String simpleName) {
        SourceExport session = CURRENT.get();

        if (session != null) spell(simpleName, FreemarkerExporter.subPackage(session.packageName, dir) + "." + simpleName);
    }

    private static void spell(final String simpleName, final String qualifiedName) {
        SourceExport session = CURRENT.get();

        if (session != null) session.spelled.computeIfAbsent(simpleName, _ -> new LinkedHashSet<>()).add(qualifiedName);
    }

    /// The qualified names the open export spelled, by simple name. Empty outside an export.
    public static Map<String, Set<String>> spelled() {
        SourceExport session = CURRENT.get();

        return session == null ? Map.of() : session.spelled;
    }
}
