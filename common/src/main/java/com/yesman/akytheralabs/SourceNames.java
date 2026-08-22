package com.yesman.akytheralabs;

import com.yesman.akythera.core.util.EnumerableDataBlock;
import com.yesman.akythera.core.util.EnumerableDataBlockManager;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.dataholder.DataBlockRegistries;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akytheralabs.dataholder.ProjectObjectRegistry;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/// The Java names the source export writes: the generated key fields, and the generated
/// [EnumerableDataBlock] enum classes with their constants. The code that writes those files
/// ([FreemarkerExporter]) and the code that references them from other generated source
/// ([ScriptNodeSourceCompiler]) name them through here.
@NullMarked
public final class SourceNames {
    private SourceNames() {}

    /// `"humanoid/walk_forward"` -> `"HUMANOID_WALK_FORWARD"`: every non-alphanumeric run becomes a single
    /// underscore, then upper-cased — the SCREAMING_SNAKE_CASE convention used for the generated key fields
    /// and enum constants.
    public static String screamingSnake(String idPath) {
        return idPath.replaceAll("[^A-Za-z0-9]+", "_").toUpperCase(Locale.ROOT);
    }

    /// `<PascalCaseProjectName><BlockType>` — the enum class [FreemarkerExporter#exportDataBlockEnum]
    /// generates for one editable data block type, e.g. `EpicfightFSMAnimationState`.
    public static String dataBlockEnumClass(AkytheraProject project, Class<?> blockType) {
        return ParseUtils.pascalCase(project.getProjectName()) + blockType.getSimpleName();
    }

    /// `{ id path -> enum constant }` for every entry `project` holds of `entry`'s type, id-path sorted so
    /// the constants keep a stable order across exports.
    public static <T extends EnumerableDataBlock> Map<String, String> dataBlockConstants(
        AkytheraProject project, DataBlockRegistries.Entry<T, ?> entry
    ) {
        ProjectObjectRegistry<T, DataHolder<T>> registry = project.getModifiableRegistry(entry.registryKey());

        if (registry == null) {
            return Map.of();
        }

        List<String> paths = registry.dataHolderEntrySet().stream()
            .filter(e -> e.getValue().getFailureException() == null)
            .map(e -> e.getKey().identifier().getPath())
            .sorted()
            .toList();

        Map<String, String> constants = new LinkedHashMap<>();
        Set<String> taken = new HashSet<>();

        for (String path : paths) {
            // Two ids can flatten onto one constant name (`humanoid/idle` and `humanoid_idle`); the path
            // each constant registers under stays the id the project authored either way.
            String base = screamingSnake(path);
            String name = base;

            for (int suffix = 1; !taken.add(name); suffix++) {
                name = base + "_" + suffix;
            }

            constants.put(path, name);
        }

        return constants;
    }

    /// The expression naming `block` in generated source: the constant of the enum this project exports
    /// for `block`'s type.
    ///
    /// Null when `block` is not one of the project's entries — an engine (hardcoded or data pack) block,
    /// which generated source resolves through its [EnumerableDataBlockManager] by id instead.
    public static @Nullable String dataBlockReference(EnumerableDataBlock block) {
        AkytheraProject project = AkytheraProject.getCurrentProjectOrNull();
        if (project == null) return null;

        DataBlockRegistries.Entry<?, ?> entry = DataBlockRegistries.entryOf(block.getEnumManager().directoryKey());
        if (entry == null) return null;

        Identifier id = block.identifier();
        if (id == null) return null;

        String constant = dataBlockConstants(project, entry).get(id.getPath());
        if (constant == null) return null;

        return dataBlockEnumClass(project, entry.blockType()) + "." + constant;
    }
}
