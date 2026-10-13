package com.yesman.akytheralabs;

import com.google.common.reflect.TypeToken;
import com.google.gson.JsonElement;
import com.yesman.akythera.api.data.modularize.ScriptType;
import com.yesman.akythera.api.data.modularize.node.FlowNode;
import com.yesman.akythera.core.blueprint.script.BlueprintBody;
import com.yesman.akythera.core.blueprint.script.FunctionGraph;
import com.yesman.akythera.core.reflection.BlueprintLayout;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.helper.ParseUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
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

    /// The template rows of every method a class generated from `body` over `layout` declares: each with its
    /// `annotations`, its `declaration` up to the opening brace, and its `body` lines.
    ///
    /// An event is written when the blueprint implements it or it has no body to fall back on, calling the
    /// parent's first when there is one. A function override and a property are written when the blueprint
    /// sets them.
    ///
    /// @param base      the class the generated class extends or implements, against which the parent's
    ///                  generic member types resolve, see [#supertype]
    /// @param holder    where the variables live, or null when they are fields of the generated class
    /// @param prologues lines put ahead of a method's own body, by method name
    public static List<Map<String, Object>> methods(
        BlueprintLayout layout, BlueprintBody body, Class<?> base, Collection<String> memberVariables,
        @Nullable MemberHolder holder, String selfExpr, Class<?> selfType, Map<String, List<String>> prologues
    ) {
        Map<TypeVariable<?>, String> arguments = typeArguments(holder);
        TypeToken<?> baseToken = TypeToken.of(base);
        List<Map<String, Object>> methods = new ArrayList<>();

        for (BlueprintLayout.Signature event : layout.events()) {
            FlowNode flow = body.methods().get(event.key());

            if (flow == null && !event.isAbstract()) continue;

            List<String> lines = new ArrayList<>(prologues.getOrDefault(event.name(), List.of()));

            if (!event.isAbstract()) lines.add(superCall(event) + ";");

            if (flow != null) {
                lines.addAll(ScriptNodeSourceCompiler.compileMethod(
                    flow, memberVariables, event.parameterNames(), selfExpr, selfType, null, null,
                    memberOwner(event, baseToken, holder)));
            }

            methods.add(row(List.of("@Override"), declaration(event, baseToken, arguments), lines));
        }

        for (BlueprintLayout.Signature function : layout.functions()) {
            FlowNode flow = body.methods().get(function.key());

            if (flow == null) continue;

            List<String> lines = new ArrayList<>(prologues.getOrDefault(function.name(), List.of()));

            lines.addAll(ScriptNodeSourceCompiler.compileMethod(
                flow, memberVariables, function.parameterNames(), selfExpr, selfType, superCall(function),
                function.returnsValue() ? defaultLiteral(function.returnType()) : null,
                memberOwner(function, baseToken, holder)));

            methods.add(row(List.of("@Override"), declaration(function, baseToken, arguments), lines));
        }

        for (BlueprintLayout.Property property : layout.properties()) {
            JsonElement value = body.properties().get(property.key());

            if (value == null) continue;

            methods.add(row(
                List.of("@Override"),
                declaration(property.getter(), baseToken, arguments),
                List.of("return " + propertyLiteral(property.type(), value) + ";")
            ));
        }

        for (Map.Entry<String, FunctionGraph> function : body.functions().entrySet()) {
            FunctionGraph graph = function.getValue();
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

            methods.add(row(
                List.of(),
                "public " + SourceNames.typeName(returnType) + " " + function.getKey() + "(" + String.join(", ", parameters) + ")",
                ScriptNodeSourceCompiler.compileMethod(
                    graph.body(), memberVariables, parameterNames, selfExpr, selfType, null,
                    returnType == void.class ? null : defaultLiteral(returnType),
                    holder == null ? null : holder.parameterName())
            ));
        }

        return methods;
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
    /// supertype's own type parameters spelled through `arguments`.
    private static String declaration(
        BlueprintLayout.Signature signature, TypeToken<?> base, Map<TypeVariable<?>, String> arguments
    ) {
        List<Type> parameterTypes = signature.genericParameterTypes();
        List<String> parameters = new ArrayList<>();

        for (int i = 0; i < parameterTypes.size(); i++) {
            Type resolved = base.resolveType(parameterTypes.get(i)).getType();

            parameters.add("final " + SourceNames.typeName(resolved, arguments) + " " + signature.parameterNames().get(i));
        }

        Type returnType = base.resolveType(signature.method().getGenericReturnType()).getType();

        return (Modifier.isProtected(signature.method().getModifiers()) ? "protected " : "public ")
            + SourceNames.typeName(returnType, arguments) + " " + signature.name()
            + "(" + String.join(", ", parameters) + ")";
    }

    /// The call of `signature`'s parent implementation with the arguments the override received.
    private static String superCall(BlueprintLayout.Signature signature) {
        return "super." + signature.name() + "(" + String.join(", ", signature.parameterNames()) + ")";
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

        return NodeTreeSourceRenderer.scriptType(type)
            + ".parse(JsonParser.parseString(" + ScriptNodeSourceCompiler.valueLiteral(json.toString()) + "))";
    }

    /// The live registries' holder of `id` in `registry`.
    private static String registryLookup(ResourceKey<? extends Registry<?>> registry, String id) {
        return "StaticRegistryAccess.getRegistryAccess().getOrThrow(" + registryKey(registry, id) + ")";
    }

    /// The [ResourceKey] of `id` in `registry`.
    public static String registryKey(ResourceKey<? extends Registry<?>> registry, String id) {
        String registryKey = "ResourceKey.createRegistryKey(Identifier.parse("
            + ScriptNodeSourceCompiler.valueLiteral(registry.identifier().toString()) + "))";

        for (Field field : AkytheraRegistries.DataPack.class.getFields()) {
            try {
                if (Modifier.isStatic(field.getModifiers()) && registry.equals(field.get(null))) {
                    registryKey = "AkytheraRegistries.DataPack." + field.getName();
                }
            } catch (IllegalAccessException e) {
                AkytheraLabs.LOGGER.warn("Unreadable registry key field {}", field.getName(), e);
            }
        }

        return "ResourceKey.create(" + registryKey + ", Identifier.parse(" + ScriptNodeSourceCompiler.valueLiteral(id) + "))";
    }
}
