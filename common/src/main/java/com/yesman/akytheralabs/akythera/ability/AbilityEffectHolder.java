package com.yesman.akytheralabs.akythera.ability;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.data.modularize.AkytheraScriptTypes;
import com.yesman.akythera.api.data.serializable.AttributeModifierEntry;
import com.yesman.akythera.api.world.ability.AbilityEffect;
import com.yesman.akythera.api.world.ability.AbilityResourceRef;
import com.yesman.akythera.api.world.ability.Health;
import com.yesman.akythera.core.registry.AkytheraRegistries;
import com.yesman.akythera.core.util.StaticRegistryAccess;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.BlueprintSourceWriter;
import com.yesman.akytheralabs.FreemarkerExporter;
import com.yesman.akytheralabs.NodeTreeSourceRenderer;
import com.yesman.akytheralabs.ScriptNodeSourceCompiler;
import com.yesman.akytheralabs.SourceNames;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import com.yesman.akytheralabs.dataholder.DTOProvider;
import com.yesman.akytheralabs.dataholder.DataHolder;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// Modifiable data holder for [AbilityEffect]. The fields are the editor's properties panel; the
/// record is rebuilt from them on [#makeObject], named after the entry's own registry key.
public final class AbilityEffectHolder extends AbstractDataHolder<AbilityEffect> implements DTOProvider {
    public static final Codec<AbilityEffectHolder> EDITOR_CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
            ModifierHolder.CODEC.listOf().optionalFieldOf("modifiers", List.of()).forGetter(h -> h.modifiers),
            ParseUtils.enumCodec(AbilityEffect.ApplicationType.class).fieldOf("type").forGetter(h -> h.applicationType),
            Codec.INT.optionalFieldOf("duration", 0).forGetter(h -> h.duration),
            Codec.INT.optionalFieldOf("max_stack", 1).forGetter(h -> h.maxStack)
        ).apply(instance, (modifiers, applicationType, duration, maxStack) -> {
            AbilityEffectHolder holder = new AbilityEffectHolder();

            holder.modifiers.addAll(modifiers);
            holder.applicationType = applicationType;
            holder.duration = duration;
            holder.maxStack = maxStack;

            return holder;
        })
    );

    @EditorEditable(label = LangKeys.GUI_ABILITY_EFFECT_SCREEN_WIDGET_APPLICATION_TYPE, order = 0, rebuildOnWrite = true)
    private AbilityEffect.ApplicationType applicationType = AbilityEffect.ApplicationType.INSTANT;

    /// Ticks one application lasts, or the recharge period when the effect is used as a cooldown.
    @EditorEditable(label = LangKeys.GUI_ABILITY_EFFECT_SCREEN_WIDGET_DURATION, order = 1, min = 1, visibleWhen = "isDuration")
    private int duration = 1;

    /// Charges a cooldown banks.
    @EditorEditable(label = LangKeys.GUI_ABILITY_EFFECT_SCREEN_WIDGET_MAX_STACK, order = 2, min = 1, visibleWhen = "isDuration")
    private int maxStack = 1;

    /// The live modifier list, a mutable [ArrayList] that add, remove and their undo write into
    /// directly.
    @EditorEditable(label = LangKeys.GUI_ABILITY_EFFECT_SCREEN_WIDGET_MODIFIERS, order = 3, defaultElement = "defaultModifier")
    private final List<ModifierHolder> modifiers = new ArrayList<>();

    /// The `+` button's fresh row, see [EditorEditable#defaultElement].
    static ModifierHolder defaultModifier() {
        return new ModifierHolder(
            AbilityEffect.ModifierType.ATTRIBUTE, ModifierHolder.EMPTY_ATTRIBUTE, ModifierHolder.firstResource(),
            0.0D, AttributeModifier.Operation.ADD_VALUE
        );
    }

    /// Whether [#applicationType] reads [#duration] and [#maxStack], see [EditorEditable#visibleWhen].
    boolean isDuration() {
        return applicationType == AbilityEffect.ApplicationType.DURATION;
    }

    @Override
    public void readFrom(final AbilityEffect object) {
        this.applicationType = object.getApplicationType();
        this.duration = object.getDuration();
        this.maxStack = object.getMaxStack();
        this.modifiers.clear();

        for (AbilityEffect.Modifier<?> modifier : object.getModifiers()) {
            modifiers.add(ModifierHolder.from(modifier));
        }
    }

    @Override
    public AbilityEffect makeObject() {
        ResourceKey<AbilityEffect> key = getOwnResourceKey();
        Identifier id = key != null ? key.identifier() : DataHolder.EMPTY_REFERENCE;

        return new AbilityEffect(
            id,
            modifiers.stream().<AbilityEffect.Modifier<?>>map(modifier -> modifier.makeModifier(id)).toList(),
            applicationType,
            duration,
            maxStack
        );
    }

    /// Tokens for `ability_effect_template.ftl`: the application type, the duration, the max stack, and each modifier
    /// as its constructor expression, where `id` is the effect's own id.
    @Override
    public Map<String, Object> getDTO() {
        return Map.of(
            "type", "AbilityEffect.ApplicationType." + applicationType.name(),
            "duration", duration,
            "maxStack", maxStack,
            "modifiers", modifiers.stream().map(ModifierHolder::source).toList()
        );
    }

    public AbilityEffect.ApplicationType getApplicationType() {
        return applicationType;
    }

    public int getDuration() {
        return duration;
    }

    public int getMaxStack() {
        return maxStack;
    }

    public List<ModifierHolder> getModifiers() {
        return modifiers;
    }

    /// One [AbilityEffect.Modifier]. The fields of both targets are kept, and [#modifierType] picks the
    /// ones shown and saved.
    public static final class ModifierHolder {
        static final ResourceKey<Attribute> EMPTY_ATTRIBUTE = ResourceKey.create(Registries.ATTRIBUTE, DataHolder.EMPTY_REFERENCE);

        private static final Codec<ModifierHolder> ATTRIBUTE_CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                ResourceKey.codec(Registries.ATTRIBUTE).fieldOf("attribute").forGetter(m -> m.attribute),
                Codec.DOUBLE.fieldOf("amount").forGetter(m -> m.amount),
                AttributeModifier.Operation.CODEC.fieldOf("operation").forGetter(m -> m.operation)
            ).apply(instance, (attribute, amount, operation) ->
                new ModifierHolder(AbilityEffect.ModifierType.ATTRIBUTE, attribute, firstResource(), amount, operation))
        );

        private static final Codec<ModifierHolder> RESOURCE_CODEC = RecordCodecBuilder.create(
            instance -> instance.group(
                AbilityResourceRef.CODEC.fieldOf("resource").forGetter(m -> m.resource),
                Codec.FLOAT.fieldOf("amount").forGetter(m -> (float) m.amount)
            ).apply(instance, (resource, amount) -> new ModifierHolder(
                AbilityEffect.ModifierType.RESOURCE, EMPTY_ATTRIBUTE, resource, amount, AttributeModifier.Operation.ADD_VALUE))
        );

        static final Codec<ModifierHolder> CODEC = ParseUtils.enumCodec(AbilityEffect.ModifierType.class).dispatch(
            "modifier_type",
            m -> m.modifierType,
            modifierType -> switch (modifierType) {
                case ATTRIBUTE -> ATTRIBUTE_CODEC.fieldOf("attribute_modifier");
                case RESOURCE -> RESOURCE_CODEC.fieldOf("resource");
            }
        );

        @EditorEditable(label = LangKeys.GUI_ABILITY_EFFECT_SCREEN_WIDGET_MODIFIER_TYPE, order = 0, rebuildOnWrite = true)
        private AbilityEffect.ModifierType modifierType;

        @EditorEditable(label = LangKeys.GUI_ABILITY_EFFECT_SCREEN_WIDGET_ATTRIBUTE, order = 1, visibleWhen = "isAttribute")
        private ResourceKey<Attribute> attribute;

        @EditorEditable(label = LangKeys.GUI_ABILITY_EFFECT_SCREEN_WIDGET_RESOURCE, order = 1, visibleWhen = "isResource")
        private AbilityResourceRef resource;

        @EditorEditable(label = LangKeys.GUI_ABILITY_EFFECT_SCREEN_WIDGET_AMOUNT, order = 2)
        private double amount;

        @EditorEditable(label = LangKeys.GUI_ABILITY_EFFECT_SCREEN_WIDGET_OPERATION, order = 3, visibleWhen = "isAttribute")
        private AttributeModifier.Operation operation;

        ModifierHolder(
            AbilityEffect.ModifierType modifierType,
            ResourceKey<Attribute> attribute,
            AbilityResourceRef resource,
            double amount,
            AttributeModifier.Operation operation
        ) {
            this.modifierType = modifierType;
            this.attribute = attribute;
            this.resource = resource;
            this.amount = amount;
            this.operation = operation;
        }

        static ModifierHolder from(AbilityEffect.Modifier<?> modifier) {
            AttributeModifierEntry entry = modifier.asAttributeModifier();

            if (entry != null) {
                return new ModifierHolder(
                    AbilityEffect.ModifierType.ATTRIBUTE, entry.attribute().unwrapKey().orElseThrow(), firstResource(),
                    entry.modifier().amount(), entry.modifier().operation()
                );
            }

            AbilityEffect.AbilityResourceSpec spec = Objects.requireNonNull(modifier.asAbilitySource());

            return new ModifierHolder(
                AbilityEffect.ModifierType.RESOURCE, EMPTY_ATTRIBUTE, spec.resource(), spec.amount(), AttributeModifier.Operation.ADD_VALUE
            );
        }

        /// The built-in [Health] class.
        static AbilityResourceRef firstResource() {
            return new AbilityResourceRef.ByClass(AkytheraScriptTypes.HEALTH.value());
        }

        /// See [EditorEditable#visibleWhen].
        boolean isAttribute() {
            return modifierType == AbilityEffect.ModifierType.ATTRIBUTE;
        }

        /// See [EditorEditable#visibleWhen].
        boolean isResource() {
            return modifierType == AbilityEffect.ModifierType.RESOURCE;
        }

        /// This modifier as the expression that builds it, its attribute modifier named `id`. A data reference to an
        /// ability resource the open project compiles names the compiled class ([FreemarkerExporter#compiledClass]).
        String source() {
            return switch (modifierType) {
                case ATTRIBUTE -> "new AbilityEffect.Modifier<>(AbilityEffect.ModifierType.ATTRIBUTE, new AttributeModifierEntry("
                    + "BuiltInRegistries.ATTRIBUTE.getOrThrow(" + BlueprintSourceWriter.registryKey(Registries.ATTRIBUTE, attribute.identifier().toString()) + "), "
                    + "new " + SourceNames.typeName(AttributeModifier.class) + "(id, " + ScriptNodeSourceCompiler.valueLiteral(amount) + ", "
                    + SourceNames.typeName(AttributeModifier.Operation.class) + "." + operation.name() + ")))";
                case RESOURCE -> {
                    String compiled = resource instanceof AbilityResourceRef.ByData byData ? FreemarkerExporter.compiledClass(byData.key()) : null;
                    String ref = switch (resource) {
                        case AbilityResourceRef.ByData _ when compiled != null -> "new AbilityResourceRef.ByClass(ScriptType.of(" + compiled + ".class))";
                        case AbilityResourceRef.ByData byData -> "new AbilityResourceRef.ByData("
                            + BlueprintSourceWriter.registryKey(AkytheraRegistries.DataPack.ABILITY_RESOURCE_KEY, byData.key().identifier().toString()) + ")";
                        case AbilityResourceRef.ByClass byClass -> "new AbilityResourceRef.ByClass(" + NodeTreeSourceRenderer.scriptType(byClass.type()) + ")";
                    };

                    yield "new AbilityEffect.Modifier<>(AbilityEffect.ModifierType.RESOURCE, new AbilityEffect.AbilityResourceSpec("
                        + ref + ", " + ScriptNodeSourceCompiler.valueLiteral((float) amount) + "))";
                }
            };
        }

        /// The engine modifier. An attribute modifier takes `effectId` as its own id.
        AbilityEffect.Modifier<?> makeModifier(Identifier effectId) {
            return switch (modifierType) {
                case ATTRIBUTE -> new AbilityEffect.Modifier<>(
                    AbilityEffect.ModifierType.ATTRIBUTE,
                    new AttributeModifierEntry(
                        StaticRegistryAccess.getRegistryAccess().lookupOrThrow(Registries.ATTRIBUTE).getOrThrow(attribute),
                        new AttributeModifier(effectId, amount, operation)
                    )
                );
                case RESOURCE -> new AbilityEffect.Modifier<>(
                    AbilityEffect.ModifierType.RESOURCE,
                    new AbilityEffect.AbilityResourceSpec(resource, (float) amount)
                );
            };
        }
    }
}
