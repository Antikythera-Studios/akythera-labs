package com.yesman.akytheralabs.akythera.entitypatch.component;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.component.TransformInheritPolicy;
import com.yesman.akythera.client.api.component.impl.EquipmentComponent;
import com.yesman.akythera.client.api.component.impl.EquipmentComponentBlueprint;
import com.yesman.akytheralabs.dataholder.TransformHolder;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

/// Editor holder for an `akythera:equipment` ([EquipmentComponentBlueprint]) — the item worn or held
/// in one equipment slot, drawn wherever its host places it.
///
/// Not a [RootAttachableComponent], so it is only reachable through the attach menu of a [TransformSocketHolder].
/// Its [#localTransform] places the item inside that socket.
public final class EquipmentComponentHolder extends ComponentDataHolder<EquipmentComponentBlueprint>
    implements TransformComponentHolder
{
    /// Editor persistence, in the shape [EquipmentComponentBlueprint#CODEC] reads, plus the editor-only
    /// [#previewItem] that [#makeObject] never lowers.
    public static final MapCodec<EquipmentComponentHolder> EDITOR_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            EquipmentSlot.CODEC.fieldOf("slot")
                .forGetter(EquipmentComponentHolder::getEquipmentSlot),
            ItemDisplayContext.CODEC.fieldOf("display_context")
                .forGetter(EquipmentComponentHolder::getItemDisplayContext),
            BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("preview_item", Items.AIR)
                .forGetter(EquipmentComponentHolder::getPreviewItem),
            TransformHolder.CODEC.optionalFieldOf("transform")
                .forGetter(holder -> Optional.of(holder.localTransform)),
            TransformInheritPolicy.CODEC.optionalFieldOf("transform_inherit_policy", TransformInheritPolicy.LOC_ROT_SCA)
                .forGetter(TransformComponentHolder::getTransformInheritPolicy)
        ).apply(instance, (slot, displayContext, previewItem, transform, transformInheritPolicy) -> new EquipmentComponentHolder(
            slot, displayContext, previewItem, transform.orElseGet(TransformHolder::identity), transformInheritPolicy))
    );

    /// The slot whose item is drawn.
    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_EQUIPMENT_SLOT, order = 0)
    private EquipmentSlot equipmentSlot;

    /// The context the item model is resolved and posed in.
    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_DISPLAY_CONTEXT, order = 1)
    private ItemDisplayContext itemDisplayContext;

    /// The item the 3D preview draws here. Editor state only; [Items#AIR] draws nothing and displays
    /// as `<none>`.
    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_PREVIEW_ITEM, order = 2)
    private Item previewItem;

    /// The elements of its socket's space the item takes on.
    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_TRANSFORM_INHERIT_POLICY, order = 3)
    private TransformInheritPolicy transformInheritPolicy;

    /// Where the item sits in its socket. A nested editor object, rendered as location / rotation / scale rows under
    /// one "Local Transform" heading.
    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_SECTION_LOCAL_TRANSFORM, order = 4)
    private TransformHolder localTransform;

    public EquipmentComponentHolder(
        EquipmentSlot equipmentSlot,
        ItemDisplayContext itemDisplayContext,
        Item previewItem,
        TransformHolder localTransform,
        TransformInheritPolicy transformInheritPolicy
    ) {
        this.equipmentSlot = equipmentSlot;
        this.itemDisplayContext = itemDisplayContext;
        this.previewItem = previewItem;
        this.localTransform = localTransform;
        this.transformInheritPolicy = transformInheritPolicy;
    }

    /// A freshly attached equipment component: the main hand in its third-person pose, previewing
    /// nothing.
    public static EquipmentComponentHolder createDefault() {
        return new EquipmentComponentHolder(
            EquipmentSlot.MAINHAND,
            ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
            Items.AIR,
            TransformHolder.identity(),
            TransformInheritPolicy.LOC_ROT_SCA
        );
    }

    public EquipmentSlot getEquipmentSlot() {
        return equipmentSlot;
    }

    public ItemDisplayContext getItemDisplayContext() {
        return itemDisplayContext;
    }

    public Item getPreviewItem() {
        return previewItem;
    }

    public void setPreviewItem(Item previewItem) {
        this.previewItem = previewItem;
    }

    public void setEquipmentSlot(EquipmentSlot equipmentSlot) {
        this.equipmentSlot = equipmentSlot;
    }

    public void setItemDisplayContext(ItemDisplayContext itemDisplayContext) {
        this.itemDisplayContext = itemDisplayContext;
    }

    @Override
    public TransformHolder getLocalTransform() {
        return localTransform;
    }

    public void setLocalTransform(TransformHolder localTransform) {
        this.localTransform = localTransform;
    }

    @Override
    public TransformInheritPolicy getTransformInheritPolicy() {
        return transformInheritPolicy;
    }

    public void setTransformInheritPolicy(TransformInheritPolicy transformInheritPolicy) {
        this.transformInheritPolicy = transformInheritPolicy;
    }

    @Override
    public void readFrom(EquipmentComponentBlueprint object) {
        equipmentSlot = object.equipmentSlot();
        itemDisplayContext = object.itemDisplayContext();
        localTransform.readFrom(object.transform());
        transformInheritPolicy = object.transformInheritPolicy();
    }

    @Override
    public EquipmentComponentBlueprint makeObject() {
        return new EquipmentComponentBlueprint(equipmentSlot, itemDisplayContext, localTransform.makeObject(), transformInheritPolicy, isEnabled());
    }

    @Override
    public Class<? extends PatchComponent> generateEventually() {
        return EquipmentComponent.class;
    }

    // ***************************************************************
    // Source export (File > Export > As Source Code)
    // ***************************************************************

    @Override
    public String sourceTemplate() {
        return "components/equipment.ftl";
    }

    /// Tokens for `components/equipment.ftl`: the slot, the display context and the inherit policy as their enum
    /// constant names, and the transform's [TransformHolder#sourceTokens].
    @Override
    public Map<String, Object> getDTO() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("slot", equipmentSlot.name());
        dto.put("display_context", itemDisplayContext.name());
        dto.put("transform", localTransform.sourceTokens());
        dto.put("transform_inherit_policy", transformInheritPolicy.name());

        return dto;
    }
}
