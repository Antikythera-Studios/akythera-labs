package com.yesman.akytheralabs.akythera.blueprint;

import com.yesman.akythera.core.reflection.BlueprintField;
import com.yesman.akythera.core.reflection.BlueprintLayout;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.function.Function;

/// What the canvases of one blueprint know of its members, by name.
public interface BlueprintMembers {
    /// A canvas that belongs to no blueprint.
    BlueprintMembers NONE = new BlueprintMembers() {
        @Override
        public @Nullable BlueprintSignature method(final String name) {
            return null;
        }

        @Override
        public @Nullable Class<?> fieldType(final String name) {
            return null;
        }
    };

    /// The event, overridable function or declared function `name`, or null when the blueprint has none.
    @Nullable BlueprintSignature method(String name);

    /// The type of the [BlueprintField] `name`, or null when the parent declares none.
    @Nullable Class<?> fieldType(String name);

    /// These members, with `methods` answering first: what a canvas implementing one of `methods` knows.
    default BlueprintMembers with(final Map<String, BlueprintSignature> methods) {
        BlueprintMembers base = this;

        return new BlueprintMembers() {
            @Override
            public @Nullable BlueprintSignature method(final String name) {
                BlueprintSignature own = methods.get(name);

                return own != null ? own : base.method(name);
            }

            @Override
            public @Nullable Class<?> fieldType(final String name) {
                return base.fieldType(name);
            }
        };
    }

    /// The members of a blueprint over a parent laid out by `layout`, which declares the functions
    /// `declared` answers for.
    static BlueprintMembers of(final BlueprintLayout layout, final Function<String, @Nullable BlueprintSignature> declared) {
        return new BlueprintMembers() {
            @Override
            public @Nullable BlueprintSignature method(final String name) {
                for (BlueprintLayout.Signature event : layout.events()) {
                    if (event.name().equals(name)) return BlueprintSignature.of(event, BlueprintSignature.Kind.EVENT);
                }

                for (BlueprintLayout.Signature function : layout.functions()) {
                    if (function.name().equals(name)) return BlueprintSignature.of(function, BlueprintSignature.Kind.OVERRIDE);
                }

                return declared.apply(name);
            }

            @Override
            public @Nullable Class<?> fieldType(final String name) {
                BlueprintLayout.Variable field = layout.field(name);

                return field == null ? null : field.field().getType();
            }
        };
    }
}
