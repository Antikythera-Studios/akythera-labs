package com.yesman.akytheralabs;

import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.core.util.EnumerableDataBlock;
import com.yesman.akythera.core.util.EnumerableDataBlockManager;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.dataholder.DataBlockRegistries;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akytheralabs.dataholder.ProjectObjectRegistry;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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

    /// `type` as generated source spells it, leaving the imports to the IDE: a nested class behind its
    /// outer class (`SystematicAbility.Tags`), type arguments and wildcards kept, and a type variable read as
    /// its bound.
    public static String typeName(Type type) {
        return typeName(type, Map.of());
    }

    /// As [#typeName(Type)], but a type variable `arguments` holds is spelled as its entry.
    public static String typeName(Type type, Map<TypeVariable<?>, String> arguments) {
        return switch (type) {
            case Class<?> c when c.isArray() -> typeName(c.getComponentType(), arguments) + "[]";
            case Class<?> c when c.getEnclosingClass() != null && !c.isAnonymousClass() ->
                typeName(c.getEnclosingClass(), arguments) + "." + c.getSimpleName();
            case Class<?> c -> c.getSimpleName();
            case ParameterizedType p -> typeName(p.getRawType(), arguments) + "<"
                + Arrays.stream(p.getActualTypeArguments())
                    .map(argument -> typeName(argument, arguments))
                    .collect(Collectors.joining(", "))
                + ">";
            case WildcardType w when w.getLowerBounds().length > 0 ->
                "? super " + typeName(w.getLowerBounds()[0], arguments);
            case WildcardType w when w.getUpperBounds().length > 0 && w.getUpperBounds()[0] != Object.class ->
                "? extends " + typeName(w.getUpperBounds()[0], arguments);
            case WildcardType _ -> "?";
            case TypeVariable<?> v when arguments.containsKey(v) -> arguments.get(v);
            case TypeVariable<?> v -> typeName(v.getBounds()[0], arguments);
            default -> type.getTypeName();
        };
    }

    /// A variable of `type` as generated source spells it: a single value by its unboxed class, and a list,
    /// set or map by its boxed parts, e.g. `Map<String, List<Integer>>`.
    public static String typeName(ScriptType type) {
        return switch (type.container()) {
            case SINGLE -> typeName(ParseUtils.unbox(type.type()));
            case LIST -> "List<" + typeArgument(type.base()) + ">";
            case SET -> "Set<" + typeArgument(type.base()) + ">";
            case MAP -> "Map<" + typeArgument(type.base()) + ", " + typeArgument(Objects.requireNonNull(type.element())) + ">";
        };
    }

    /// `type` as a type argument of [#typeName(ScriptType)]: a single value by its boxed class.
    private static String typeArgument(ScriptType type) {
        return type.container() == ScriptType.Container.SINGLE ? typeName(ParseUtils.box(type.type())) : typeName(type);
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
