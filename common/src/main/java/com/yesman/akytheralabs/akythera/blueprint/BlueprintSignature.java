package com.yesman.akytheralabs.akythera.blueprint;

import com.yesman.akythera.api.data.modularize.ScriptFunction;
import com.yesman.akythera.core.reflection.BlueprintLayout;
import com.yesman.akythera.core.util.helper.ParseUtils;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/// A method or function of the blueprint being edited, as its canvases draw it: the pins of its entry, call,
/// parent and return nodes.
///
/// @param returnType null when it returns nothing
public record BlueprintSignature(String name, Kind kind, List<Parameter> parameters, @Nullable Class<?> returnType) {
    public enum Kind {
        /// A [ScriptFunction.BlueprintGraphType#EVENT_GRAPH] method, entered on the Event Graph.
        EVENT,

        /// A [ScriptFunction.BlueprintGraphType#FUNCTION] method, overridden on a canvas of its own.
        OVERRIDE,

        /// A function the blueprint declares.
        FUNCTION
    }

    public record Parameter(String name, Class<?> type) {
    }

    public BlueprintSignature {
        parameters = List.copyOf(parameters);
    }

    /// `method` of the parent's [BlueprintLayout].
    public static BlueprintSignature of(final BlueprintLayout.Signature method, final Kind kind) {
        List<Parameter> parameters = new ArrayList<>();

        for (int i = 0; i < method.parameterNames().size(); i++) {
            parameters.add(new Parameter(method.parameterNames().get(i), method.parameterTypes().get(i)));
        }

        return new BlueprintSignature(method.name(), kind, parameters, method.returnsValue() ? method.returnType() : null);
    }

    /// The name as spaced words, e.g. `On Join World` for `onJoinWorld` or `on_join_world`.
    public Component title() {
        return Component.literal(name.indexOf('_') >= 0 ? ParseUtils.toDisplayName(name) : ParseUtils.camelToSpacedPascal(name));
    }
}
