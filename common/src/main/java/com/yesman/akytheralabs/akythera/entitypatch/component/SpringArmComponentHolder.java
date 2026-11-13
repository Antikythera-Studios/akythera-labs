package com.yesman.akytheralabs.akythera.entitypatch.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.component.TransformInheritPolicy;
import com.yesman.akythera.api.component.impl.SpringArmComponent;
import com.yesman.akythera.api.component.impl.SpringArmComponentBlueprint;
import com.yesman.akytheralabs.dataholder.AbstractDataHolder;
import com.yesman.akytheralabs.dataholder.ComponentHolderRegistry;
import com.yesman.akytheralabs.dataholder.TransformHolder;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.function.Supplier;

/// Editor holder for an `akythera:spring_arm` ([SpringArmComponentBlueprint]) — an arm from a pivot its children hang
/// off the end of. The pivot is not scaled.
public final class SpringArmComponentHolder extends ComponentDataHolder<SpringArmComponentBlueprint>
    implements ComponentAttachableHolder, RootAttachableComponent, TransformComponentHolder
{
    public static MapCodec<SpringArmComponentHolder> editorCodec(Supplier<ComponentHolderRegistry> registry) {
        return RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                TransformHolder.CODEC.optionalFieldOf("transform").forGetter(h -> Optional.of(h.localTransform)),
                TransformInheritPolicy.CODEC.optionalFieldOf("transform_inherit_policy", TransformInheritPolicy.LOC_ROT_SCA)
                    .forGetter(TransformComponentHolder::getTransformInheritPolicy),
                Codec.DOUBLE.optionalFieldOf("length", 0.0D).forGetter(SpringArmComponentHolder::getLength),
                ComponentAttachableHolder.componentsField(registry).forGetter(ComponentAttachableHolder::componentsOf)
            ).apply(instance, (transform, transformInheritPolicy, length, components) -> {
                SpringArmComponentHolder holder = new SpringArmComponentHolder(registry);
                holder.localTransform = transform.orElseGet(TransformHolder::identity).hideScale();
                holder.transformInheritPolicy = transformInheritPolicy;
                holder.length = length;
                ComponentAttachableHolder.applyComponents(holder, components);
                return holder;
            })
        );
    }

    /// The patch kind this arm belongs to — what its children decode, attach and re-import through.
    private final Supplier<ComponentHolderRegistry> registry;

    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_TRANSFORM_INHERIT_POLICY, order = 0)
    private TransformInheritPolicy transformInheritPolicy = TransformInheritPolicy.LOC_ROT_SCA;

    /// How far the arm's end is from its pivot, along the pivot's +Z, in blocks.
    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_LENGTH, order = 1, min = 0.0F)
    private double length = 4.0D;

    /// The pivot inside the host's space.
    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_SECTION_LOCAL_TRANSFORM, order = 2)
    private TransformHolder localTransform = TransformHolder.identity().hideScale();

    private final SequencedMap<String, ComponentDataHolder<?>> components = ComponentAttachableHolder.newComponentMap();

    public SpringArmComponentHolder(Supplier<ComponentHolderRegistry> registry) {
        this.registry = registry;
    }

    @Override
    public TransformHolder getLocalTransform() {
        return localTransform;
    }

    @Override
    public TransformInheritPolicy getTransformInheritPolicy() {
        return transformInheritPolicy;
    }

    public double getLength() {
        return length;
    }

    /// What an arm hosts: a [CameraComponentHolder] and a [TransformSocketHolder] for anything else.
    @Override
    public List<Class<?>> attachableTypes() {
        return List.of(CameraComponentHolder.class, TransformSocketHolder.class);
    }

    @Override
    public SequencedMap<String, ComponentDataHolder<?>> getComponents() {
        return components;
    }

    /// The first attached component the decode guard salvaged, if any.
    @Override
    public @Nullable Exception getNestedFailureException() {
        return AbstractDataHolder.firstNestedFailure(components.values());
    }

    @Override
    public void readFrom(SpringArmComponentBlueprint object) {
        localTransform.readFrom(object.transform());
        transformInheritPolicy = object.transformInheritPolicy();
        length = object.length();

        ComponentAttachableHolder.readComponents(this, registry.get(), object.childBlueprints());
    }

    @Override
    public SpringArmComponentBlueprint makeObject() {
        return new SpringArmComponentBlueprint(
            localTransform.makeObject(), transformInheritPolicy, length, ComponentAttachableHolder.lowerComponents(components), isEnabled()
        );
    }

    @Override
    public Class<? extends PatchComponent> generateEventually() {
        return SpringArmComponent.class;
    }

    // ***************************************************************
    // Source export (File > Export > As Source Code)
    // ***************************************************************

    @Override
    public String sourceTemplate() {
        return "components/spring_arm.ftl";
    }

    @Override
    public Map<String, Object> getDTO() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("transform", localTransform.sourceTokens());
        dto.put("transform_inherit_policy", transformInheritPolicy.name());
        dto.put("length", length);

        return dto;
    }
}
