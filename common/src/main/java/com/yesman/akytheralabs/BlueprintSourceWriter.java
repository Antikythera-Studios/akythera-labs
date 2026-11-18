package com.yesman.akytheralabs;

import com.google.common.reflect.TypeToken;
import com.google.gson.JsonElement;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.data.modularize.node.FlowNode;
import com.yesman.akythera.api.world.ability.AbilityTag;
import com.yesman.akythera.api.world.ability.AbilityTagSet;
import com.yesman.akythera.api.world.ability.SystematicAbility;
import com.yesman.akythera.core.blueprint.script.BlueprintBody;
import com.yesman.akythera.core.blueprint.script.FunctionGraph;
import com.yesman.akythera.core.reflection.BlueprintLayout;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.helper.ParseUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/// Writes the methods of a class generated from a data-driven blueprint: the parent's events and overridden
/// functions and the property values as `@Override` methods, then the functions the blueprint declares. Every
/// body comes from [ScriptNodeSourceCompiler], so every graph becomes real statements.
@NullMarked
public final class BlueprintSourceWriter {
    private BlueprintSourceWriter() {}

    /// A class the generated class declares to keep the blueprint's variables in, in place of fields of its
    /// own, e.g. an ability's per-instance state.
    ///
    /// Each overriding method reaches the variables through its parameter of type `slot`. A function the
    /// blueprint declares receives the holder as a leading parameter named `parameterName`.
    ///
    /// @param slot      the type parameter of the supertype the holder class binds
    /// @param className the holder class's name as the generated class spells it
    public record MemberHolder(TypeVariable<?> slot, String className, String parameterName) {}

    /// The parent script a generated class is laid over, which the Parent node of its root graphs calls.
    ///
    /// @param expression what the calls are made on: a field holding the script, or `super`
    /// @param inherits   whether the generated class extends the parent script's class, so an event it leaves out
    ///                   is the parent's already
    /// @param functions  the declared functions the parent script's class has, which a declared function of the
    ///                   same name overrides
    public record ParentScript(String expression, boolean inherits, Collection<String> functions) {}

    /// How a class whose subclasses replace its event graphs writes an event: the event runs the parent class's,
    /// then a protected [#GRAPH_SUFFIX] method holding the graph, which a subclass overrides.
    ///
    /// @param inherited the [BlueprintLayout.Signature#key] of the events a superclass declares that method for
    public record GraphMethods(Collection<String> inherited) {}

    /// What follows an event's name in the method holding its graph, see [GraphMethods].
    public static final String GRAPH_SUFFIX = "$graph";

    /// `base` as the generated class names it after `extends` or `implements`: the type parameter `holder`
    /// binds spelled as its class, every other bound to its bound, e.g. `SystematicAbility<AbilityState>`.
    public static String supertype(Class<?> base, @Nullable MemberHolder holder) {
        TypeVariable<?>[] parameters = base.getTypeParameters();
        Map<TypeVariable<?>, String> arguments = typeArguments(holder);

        return parameters.length == 0
            ? SourceNames.typeName(base)
            : SourceNames.typeName(base) + "<"
                + Arrays.stream(parameters)
                    .map(parameter -> SourceNames.typeName(parameter, arguments))
                    .collect(Collectors.joining(", "))
                + ">";
    }

    /// The template rows of every method a class generated from `chain` over `layout` declares: each with its
    /// `annotations`, its `declaration` up to the opening brace, and its `body` lines.
    ///
    /// An event is written when a body implements it, it has no body to fall back on, or the class holds its parent
    /// script in a field. Over a parent class it calls the parent's first when no graph calls it itself; over a
    /// parent script it calls the script's only where a graph does, or when no graph implements it. A function
    /// override, a property, a declared function and a variable are each the nearest body's. A data parent's graph
    /// of an event or an override is written as a private method, which the graph laid over it calls as its parent.
    ///
    /// @param chain        the blueprint's body, then the body of each data parent up to the root
    /// @param base         the class the generated class extends or implements, against which the parent's
    ///                     generic member types resolve, see [#supertype]
    /// @param holder       where the variables live, or null when they are fields of the generated class
    /// @param prologues    lines put ahead of a method's own body, by method name
    /// @param parentScript the parent script the class is laid over, or null for a class laid over none
    /// @param graphMethods how the class writes its events for its subclasses, or null for a class that writes
    ///                     each graph into the event itself. With it, only the nearest body of `chain` is written
    public static List<Map<String, Object>> methods(
        BlueprintLayout layout, List<BlueprintBody> chain, Class<?> base, Collection<String> memberVariables,
        @Nullable MemberHolder holder, String selfExpr, Class<?> selfType, Map<String, List<String>> prologues,
        @Nullable ParentScript parentScript, @Nullable GraphMethods graphMethods
    ) {
        Map<TypeVariable<?>, String> arguments = typeArguments(holder);
        TypeToken<?> baseToken = TypeToken.of(base);
        BlueprintBody body = BlueprintBody.fold(chain);
        List<Map<String, Object>> methods = new ArrayList<>();

        for (BlueprintLayout.Signature event : layout.events()) {
            List<FlowNode> flows = chain.stream().map(link -> link.methods().get(event.key())).filter(Objects::nonNull).toList();

            if (graphMethods != null) {
                if (flows.isEmpty()) continue;

                boolean overrides = graphMethods.inherited().contains(event.key());
                String call = event.name() + GRAPH_SUFFIX + "(" + String.join(", ", event.parameterNames()) + ")";
                String declared = declaration(event, 0, baseToken, arguments);

                if (!overrides) {
                    List<String> lines = new ArrayList<>(prologues.getOrDefault(event.name(), List.of()));

                    if (!event.isAbstract()) lines.add(superCall(event, base) + ";");

                    lines.add(call + ";");
                    methods.add(row(List.of("@Override"), declared, lines));
                }

                methods.add(row(
                    overrides ? List.of("@Override") : List.of(),
                    "protected " + declared.substring(declared.indexOf(' ') + 1).replace(" " + event.name() + "(", " " + event.name() + GRAPH_SUFFIX + "("),
                    ScriptNodeSourceCompiler.compileMethod(
                        flows.getFirst(), memberVariables, event.parameterNames(), selfExpr, selfType, overrides ? "super." + call : null, null,
                        memberOwner(event, baseToken, holder))));

                continue;
            }

            if (flows.isEmpty() && (parentScript == null ? !event.isAbstract() : parentScript.inherits())) continue;

            String parentCall = parentScript != null
                ? parentScript.expression() + "." + event.name() + "(" + String.join(", ", event.parameterNames()) + ")"
                : event.isAbstract() ? null : superCall(event, base);

            methods.addAll(implementations(
                event, flows, parentCall, parentScript == null, prologues.getOrDefault(event.name(), List.of()), memberVariables, selfExpr, selfType,
                baseToken, arguments, holder));
        }

        for (BlueprintLayout.Signature function : layout.functions()) {
            List<FlowNode> flows = chain.stream().map(link -> link.methods().get(function.key())).filter(Objects::nonNull).toList();

            if (flows.isEmpty()) continue;

            methods.addAll(implementations(
                function, flows, superCall(function, base), false, prologues.getOrDefault(function.name(), List.of()), memberVariables,
                selfExpr, selfType, baseToken, arguments, holder));
        }

        for (BlueprintLayout.Property property : layout.properties()) {
            JsonElement value = body.properties().get(property.key());

            if (value == null) continue;

            ScriptType type = property.type();
            String literal = propertyLiteral(type, value);
            List<String> annotations = List.of("@Override");
            String declaration = declaration(property.getter(), 0, baseToken, arguments);

            // An object value is built once, at construction. A registry entry is read each time, since a reload replaces it.
            boolean cached = type.registry() == null && type.base().registry() == null && (type.element() == null || type.element().registry() == null)
                && !ParseUtils.unbox(type.type()).isPrimitive() && type.type() != String.class && !type.type().isEnum();

            if (!cached) {
                methods.add(row(annotations, declaration, List.of("return " + literal + ";")));
                continue;
            }

            String field = property.key() + "Property";
            Map<String, Object> row = row(annotations, declaration, List.of("return this." + field + ";"));
            row.put("field", "private final " + SourceNames.typeName(baseToken.resolveType(property.getter().method().getGenericReturnType()).getType(), arguments)
                + " " + field + " = " + literal + ";");
            methods.add(row);
        }

        for (Map.Entry<String, FunctionGraph> function : body.functions().entrySet()) {
            FunctionGraph graph = function.getValue();
            List<FunctionGraph> graphs = chain.stream().map(link -> link.functions().get(function.getKey())).filter(Objects::nonNull).toList();
            Class<?> returnType = graph.returnType().<Class<?>>map(type -> ParseUtils.unbox(type.type())).orElse(void.class);
            List<String> parameters = new ArrayList<>();
            List<String> parameterNames = new ArrayList<>();

            if (holder != null) {
                parameters.add("final " + holder.className() + " " + holder.parameterName());
                parameterNames.add(holder.parameterName());
            }

            for (FunctionGraph.Parameter parameter : graph.parameters()) {
                parameters.add("final " + SourceNames.typeName(ParseUtils.unbox(parameter.type().type())) + " " + parameter.name());
                parameterNames.add(parameter.name());
            }

            // The nearest body's graph is the function, and a data parent's graph of the same name a private method
            for (int level = 0; level < graphs.size(); level++) {
                String parentCall = level + 1 < graphs.size()
                    ? inheritedName(function.getKey(), level + 1) + "(" + String.join(", ", parameterNames) + ")"
                    : parentScript != null && parentScript.functions().contains(function.getKey())
                        ? parentScript.expression() + "." + function.getKey() + "(" + String.join(", ", parameterNames) + ")"
                        : null;

                methods.add(row(
                    List.of(),
                    (level > 0 ? "private " : "public ") + SourceNames.typeName(returnType) + " " + inheritedName(function.getKey(), level)
                        + "(" + String.join(", ", parameters) + ")",
                    ScriptNodeSourceCompiler.compileMethod(
                        graphs.get(level).body(), memberVariables, parameterNames, selfExpr, selfType, parentCall,
                        returnType == void.class ? null : defaultLiteral(returnType),
                        holder == null ? null : holder.parameterName())
                ));
            }
        }

        return methods;
    }

    /// The rows of `signature` implemented by `flows`, the nearest body's graph first: the override, then one
    /// private method per data parent's graph, see [#inheritedName].
    ///
    /// @param parentCall       the call of what the root graph overrides, or null when it overrides nothing
    /// @param callsParentFirst whether the override starts with `parentCall` when no graph calls it. With no graph
    ///                         at all, the override is `parentCall` alone
    private static List<Map<String, Object>> implementations(
        BlueprintLayout.Signature signature, List<FlowNode> flows, @Nullable String parentCall, boolean callsParentFirst,
        List<String> prologue, Collection<String> memberVariables, String selfExpr, Class<?> selfType,
        TypeToken<?> base, Map<TypeVariable<?>, String> arguments, @Nullable MemberHolder holder
    ) {
        List<List<String>> bodies = new ArrayList<>();

        for (int level = 0; level < flows.size(); level++) {
            String levelParent = level + 1 < flows.size()
                ? inheritedName(signature.name(), level + 1) + "(" + String.join(", ", signature.parameterNames()) + ")"
                : parentCall;

            bodies.add(ScriptNodeSourceCompiler.compileMethod(
                flows.get(level), memberVariables, signature.parameterNames(), selfExpr, selfType, levelParent,
                signature.returnsValue() ? defaultLiteral(signature.returnType()) : null,
                memberOwner(signature, base, holder)));
        }

        List<String> lines = new ArrayList<>(prologue);

        if ((callsParentFirst || bodies.isEmpty()) && parentCall != null && bodies.stream().flatMap(List::stream).noneMatch(line -> line.contains(parentCall))) {
            lines.add(parentCall + ";");
        }

        if (!bodies.isEmpty()) lines.addAll(bodies.getFirst());

        List<Map<String, Object>> rows = new ArrayList<>();

        rows.add(row(List.of("@Override"), declaration(signature, 0, base, arguments), lines));

        for (int level = 1; level < bodies.size(); level++) {
            rows.add(row(List.of(), declaration(signature, level, base, arguments), bodies.get(level)));
        }

        return rows;
    }

    /// The name of the method holding the graph the data parent `level` bodies up the chain implements `name`
    /// with. Level 0 is the override itself.
    private static String inheritedName(String name, int level) {
        return level == 0 ? name : name + "$parent" + level;
    }

    private static Map<TypeVariable<?>, String> typeArguments(@Nullable MemberHolder holder) {
        return holder == null ? Map.of() : Map.of(holder.slot(), holder.className());
    }

    /// The parameter `signature`'s body reaches `holder` through, or null when there is no holder or the
    /// method takes none.
    private static @Nullable String memberOwner(
        BlueprintLayout.Signature signature, TypeToken<?> base, @Nullable MemberHolder holder
    ) {
        if (holder == null) return null;

        List<Type> parameterTypes = signature.genericParameterTypes();

        for (int i = 0; i < parameterTypes.size(); i++) {
            if (holder.slot().equals(base.resolveType(parameterTypes.get(i)).getType())) {
                return signature.parameterNames().get(i);
            }
        }

        return null;
    }

    /// `signature`'s declaration, its member types resolved against the generated class's supertype and the
    /// supertype's own type parameters spelled through `arguments`. Past level 0 it declares the private method
    /// [#inheritedName] names.
    private static String declaration(
        BlueprintLayout.Signature signature, int level, TypeToken<?> base, Map<TypeVariable<?>, String> arguments
    ) {
        List<Type> parameterTypes = signature.genericParameterTypes();
        List<String> parameters = new ArrayList<>();

        for (int i = 0; i < parameterTypes.size(); i++) {
            Type resolved = base.resolveType(parameterTypes.get(i)).getType();

            parameters.add("final " + SourceNames.typeName(resolved, arguments) + " " + signature.parameterNames().get(i));
        }

        Type returnType = base.resolveType(signature.method().getGenericReturnType()).getType();

        return (level > 0 ? "private " : Modifier.isProtected(signature.method().getModifiers()) ? "protected " : "public ")
            + SourceNames.typeName(returnType, arguments) + " " + inheritedName(signature.name(), level)
            + "(" + String.join(", ", parameters) + ")";
    }

    /// The call of `signature`'s parent implementation with the arguments the override received, through `base`
    /// itself when it is an interface.
    private static String superCall(BlueprintLayout.Signature signature, Class<?> base) {
        return (base.isInterface() ? SourceNames.typeName(base) + ".super." : "super.") + signature.name() + "(" + String.join(", ", signature.parameterNames()) + ")";
    }

    private static Map<String, Object> row(List<String> annotations, String declaration, List<String> body) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("annotations", annotations);
        row.put("declaration", declaration);
        row.put("body", body);

        return row;
    }

    /// What a method returning `type` answers when its graph never reaches a return node.
    public static String defaultLiteral(Class<?> type) {
        if (type == boolean.class) return "false";
        if (type == char.class) return "'\\0'";
        if (type == float.class) return "0.0F";
        if (type == double.class) return "0.0D";
        if (type == long.class) return "0L";
        if (type == int.class || type == short.class || type == byte.class) return "0";

        return "null";
    }

    /// A property value, held as its codec's JSON, as a Java expression of the property's type: a registry
    /// entry or holder looked up by id, a key made from its id, a list, set or map of those, an enum constant,
    /// a primitive or string literal, and anything else decoded through its [ScriptType] at run time.
    public static String propertyLiteral(ScriptType type, JsonElement json) {
        if (type.registry() != null) {
            return registryLookup(type.registry(), json.getAsString()) + ".value()";
        }

        ScriptType element = type.element();

        if (element != null && type.type() == Holder.class && element.registry() != null) {
            return registryLookup(element.registry(), json.getAsString());
        }

        if (element != null && type.type() == ResourceKey.class && element.registry() != null) {
            return registryKey(element.registry(), json.getAsString());
        }

        if (type.container() == ScriptType.Container.MAP && element != null && json.isJsonArray()) {
            String entries = json.getAsJsonArray().asList().stream()
                .map(entry -> "Map.entry("
                    + propertyLiteral(type.base(), entry.getAsJsonObject().get("key")) + ", "
                    + propertyLiteral(element, entry.getAsJsonObject().get("value")) + ")")
                .collect(Collectors.joining(", "));

            return "Map.ofEntries(" + entries + ")";
        }

        if (element != null && json.isJsonArray()) {
            String elements = json.getAsJsonArray().asList().stream()
                .map(value -> propertyLiteral(element, value))
                .collect(Collectors.joining(", "));

            return (type.type() == List.class ? "List.of(" : "Set.of(") + elements + ")";
        }

        if (type.type().isEnum() && json.isJsonPrimitive()) {
            for (Object constant : type.type().getEnumConstants()) {
                if (((Enum<?>) constant).name().equalsIgnoreCase(json.getAsString())) {
                    return SourceNames.typeName(type.type()) + "." + ((Enum<?>) constant).name();
                }
            }
        }

        if (json.isJsonPrimitive() && (ParseUtils.unbox(type.type()).isPrimitive() || type.type() == String.class)) {
            return ScriptNodeSourceCompiler.valueLiteral(type.parse(json));
        }

        if (type.type() == AbilityTagSet.class && json.isJsonArray()) {
            return json.getAsJsonArray().isEmpty()
                ? SourceNames.typeName(AbilityTagSet.class) + ".EMPTY"
                : SourceNames.typeName(AbilityTagSet.class) + ".of(List.of(" + abilityTags(json) + "))";
        }

        if (type.type() == SystematicAbility.Tags.class && json.isJsonObject()) {
            StringBuilder builder = new StringBuilder(SourceNames.typeName(SystematicAbility.Tags.class) + ".builder()");

            json.getAsJsonObject().entrySet().forEach(set -> {
                if (!set.getValue().getAsJsonArray().isEmpty()) {
                    builder.append(".").append(ParseUtils.snakeToCamel(set.getKey())).append("(").append(abilityTags(set.getValue())).append(")");
                }
            });

            return builder.append(".build()").toString();
        }

        return NodeTreeSourceRenderer.scriptType(type)
            + ".parse(JsonParser.parseString(" + ScriptNodeSourceCompiler.valueLiteral(json.toString()) + "))";
    }

    /// The ability tags a JSON array names, each as its project enum constant or its manager lookup.
    private static String abilityTags(JsonElement ids) {
        return ids.getAsJsonArray().asList().stream()
            .map(id -> ScriptNodeSourceCompiler.dataBlockLookup(SourceNames.typeName(AbilityTag.class), AbilityTag.ENUM_MANAGER, Identifier.parse(id.getAsString())))
            .collect(Collectors.joining(", "));
    }

    /// The live registries' holder of `id` in `registry`.
    private static String registryLookup(ResourceKey<? extends Registry<?>> registry, String id) {
        return "StaticRegistryAccess.getRegistryAccess().getOrThrow(" + registryKey(registry, id) + ")";
    }

    /// The [ResourceKey] of `id` in `registry`.
    public static String registryKey(ResourceKey<? extends Registry<?>> registry, String id) {
        return "ResourceKey.create(" + registry(registry) + ", Identifier.parse(" + ScriptNodeSourceCompiler.valueLiteral(id) + "))";
    }

    /// `registry` itself: its [AkytheraRegistries.DataPack] constant, or a key created from its id.
    public static String registry(ResourceKey<? extends Registry<?>> registry) {
        for (Field field : AkytheraRegistries.DataPack.class.getFields()) {
            try {
                if (Modifier.isStatic(field.getModifiers()) && registry.equals(field.get(null))) {
                    return "AkytheraRegistries.DataPack." + field.getName();
                }
            } catch (IllegalAccessException e) {
                AkytheraLabs.LOGGER.warn("Unreadable registry key field {}", field.getName(), e);
            }
        }

        return "ResourceKey.createRegistryKey(Identifier.parse(" + ScriptNodeSourceCompiler.valueLiteral(registry.identifier().toString()) + "))";
    }
}
