package com.yesman.akytheralabs.akythera.entitypatch.component;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.component.TransformInheritPolicy;
import com.yesman.akythera.client.api.component.impl.SkinnedArmorRenderer;
import com.yesman.akythera.client.api.component.impl.SkinnedArmorRendererBlueprint;
import com.yesman.akytheralabs.dataholder.TransformHolder;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/// Editor holder for an `akythera:skinned_armor` ([SkinnedArmorRendererBlueprint]) — the armor piece worn in
/// one armor slot, drawn skinned.
///
/// Not a [RootAttachableComponent], so it is only reachable through the attach menu of an
/// [ArmatureComponentHolder] or a [TransformSocketHolder]. Its [#localTransform] places the piece inside the
/// armature.
public final class SkinnedArmorRendererHolder extends ComponentDataHolder<SkinnedArmorRendererBlueprint>
    implements TransformComponentHolder
{
    /// Editor persistence, in the shape [SkinnedArmorRendererBlueprint#CODEC] reads, plus the editor-only
    /// [#previewItem] that [#makeObject] never lowers.
    public static final MapCodec<SkinnedArmorRendererHolder> EDITOR_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            EquipmentSlot.CODEC.fieldOf("slot")
                .forGetter(SkinnedArmorRendererHolder::getEquipmentSlot),
            BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("preview_item", Items.AIR)
                .forGetter(SkinnedArmorRendererHolder::getPreviewItem),
            TransformHolder.CODEC.optionalFieldOf("transform")
                .forGetter(holder -> Optional.of(holder.localTransform)),
            TransformInheritPolicy.CODEC.optionalFieldOf("transform_inherit_policy", TransformInheritPolicy.LOC_ROT_SCA)
                .forGetter(TransformComponentHolder::getTransformInheritPolicy)
        ).apply(instance, (slot, previewItem, transform, transformInheritPolicy) -> new SkinnedArmorRendererHolder(
            slot, previewItem, transform.orElseGet(TransformHolder::identity), transformInheritPolicy))
    );

    /// The armor slot whose piece is drawn.
    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_EQUIPMENT_SLOT, order = 0, candidates = "armorSlots")
    private EquipmentSlot equipmentSlot;

    /// The item the 3D preview wears here. Editor state only; [Items#AIR] draws nothing and displays
    /// as `<none>`.
    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_PREVIEW_ITEM, order = 1)
    private Item previewItem;

    /// The elements of the armature's space the piece takes on.
    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_TRANSFORM_INHERIT_POLICY, order = 2)
    private TransformInheritPolicy transformInheritPolicy;

    /// Where the piece sits in the armature. A nested editor object, rendered as location / rotation / scale rows
    /// under one "Local Transform" heading.
    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_SECTION_LOCAL_TRANSFORM, order = 3)
    private TransformHolder localTransform;

    public SkinnedArmorRendererHolder(
        EquipmentSlot equipmentSlot,
        Item previewItem,
        TransformHolder localTransform,
        TransformInheritPolicy transformInheritPolicy
    ) {
        this.equipmentSlot = equipmentSlot;
        this.previewItem = previewItem;
        this.localTransform = localTransform;
        this.transformInheritPolicy = transformInheritPolicy;
    }

    /// A freshly attached armor renderer: the chest, previewing nothing.
    public static SkinnedArmorRendererHolder createDefault() {
        return new SkinnedArmorRendererHolder(
            EquipmentSlot.CHEST, Items.AIR, TransformHolder.identity(), TransformInheritPolicy.LOC_ROT_SCA
        );
    }

    public EquipmentSlot getEquipmentSlot() {
        return equipmentSlot;
    }

    public void setEquipmentSlot(EquipmentSlot equipmentSlot) {
        this.equipmentSlot = equipmentSlot;
    }

    /// The slots an armor piece is worn in, see [EquipmentSlot#isArmor].
    private List<EquipmentSlot> armorSlots() {
        return Stream.of(EquipmentSlot.values()).filter(EquipmentSlot::isArmor).toList();
    }

    public Item getPreviewItem() {
        return previewItem;
    }

    public void setPreviewItem(Item previewItem) {
        this.previewItem = previewItem;
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
    public void readFrom(SkinnedArmorRendererBlueprint object) {
        equipmentSlot = object.equipmentSlot();
        localTransform.readFrom(object.transform());
        transformInheritPolicy = object.transformInheritPolicy();
    }

    @Override
    public SkinnedArmorRendererBlueprint makeObject() {
        return new SkinnedArmorRendererBlueprint(equipmentSlot, localTransform.makeObject(), transformInheritPolicy, isEnabled());
    }

    @Override
    public Class<? extends PatchComponent> generateEventually() {
        return SkinnedArmorRenderer.class;
    }

    // ***************************************************************
    // Source export (File > Export > As Source Code)
    // ***************************************************************

    @Override
    public String sourceTemplate() {
        return "components/skinned_armor.ftl";
    }

    /// Tokens for `components/skinned_armor.ftl`: the slot and the inherit policy as their enum constant names,
    /// and the transform's [TransformHolder#sourceTokens].
    @Override
    public Map<String, Object> getDTO() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("slot", equipmentSlot.name());
        dto.put("transform", localTransform.sourceTokens());
        dto.put("transform_inherit_policy", transformInheritPolicy.name());

        return dto;
    }
}
