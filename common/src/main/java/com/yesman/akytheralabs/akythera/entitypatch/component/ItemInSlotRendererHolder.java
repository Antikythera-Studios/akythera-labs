package com.yesman.akytheralabs.akythera.entitypatch.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.component.TransformInheritPolicy;
import com.yesman.akythera.client.api.component.impl.ItemInSlotRenderer;
import com.yesman.akythera.client.api.component.impl.ItemInSlotRendererBlueprint;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.dataholder.TransformHolder;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/// Editor holder for an `akythera:item_in_slot` ([ItemInSlotRendererBlueprint]) — the item in one equipment
/// slot, drawn rigid.
///
/// Not a [RootAttachableComponent], so it is only reachable through the attach menu of a
/// [TransformSocketHolder]. Its [#localTransform] places the item inside that socket, and a 3D model inside
/// the armature.
public final class ItemInSlotRendererHolder extends ComponentDataHolder<ItemInSlotRendererBlueprint>
    implements TransformComponentHolder
{
    /// Editor persistence, in the shape [ItemInSlotRendererBlueprint#CODEC] reads, plus the editor-only
    /// [#previewItem] that [#makeObject] never lowers.
    public static final MapCodec<ItemInSlotRendererHolder> EDITOR_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            EquipmentSlot.CODEC.fieldOf("slot")
                .forGetter(ItemInSlotRendererHolder::getEquipmentSlot),
            ItemDisplayContext.CODEC.fieldOf("display_context")
                .forGetter(ItemInSlotRendererHolder::getItemDisplayContext),
            Codec.BOOL.optionalFieldOf("model_3d", false)
                .forGetter(ItemInSlotRendererHolder::isModel3d),
            BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("preview_item", Items.AIR)
                .forGetter(ItemInSlotRendererHolder::getPreviewItem),
            TransformHolder.CODEC.optionalFieldOf("transform")
                .forGetter(holder -> Optional.of(holder.localTransform)),
            TransformInheritPolicy.CODEC.optionalFieldOf("transform_inherit_policy", TransformInheritPolicy.LOC_ROT_SCA)
                .forGetter(TransformComponentHolder::getTransformInheritPolicy)
        ).apply(instance, (slot, displayContext, model3d, previewItem, transform, transformInheritPolicy) -> new ItemInSlotRendererHolder(
            slot, displayContext, model3d, previewItem, transform.orElseGet(TransformHolder::identity), transformInheritPolicy))
    );

    /// What the Display Context row holds while [#model3d] is on.
    private static final Object MODEL_3D = new Object();

    /// The slot whose item is drawn.
    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_EQUIPMENT_SLOT, order = 0)
    private EquipmentSlot equipmentSlot;

    /// What the Display Context row shows: [#itemDisplayContext], or [#MODEL_3D] while [#model3d] is on.
    @EditorEditable(
        label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_DISPLAY_CONTEXT, order = 1,
        candidates = "displayOptions", candidateLabel = "displayOptionLabel"
    )
    private Object displayOption;

    /// The context the item model is resolved and posed in.
    private ItemDisplayContext itemDisplayContext;

    /// Whether the item is drawn as the 3D model it is worn with, see [ItemInSlotRenderer].
    private boolean model3d;

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

    public ItemInSlotRendererHolder(
        EquipmentSlot equipmentSlot,
        ItemDisplayContext itemDisplayContext,
        boolean model3d,
        Item previewItem,
        TransformHolder localTransform,
        TransformInheritPolicy transformInheritPolicy
    ) {
        this.equipmentSlot = equipmentSlot;
        this.itemDisplayContext = itemDisplayContext;
        this.model3d = model3d;
        this.displayOption = model3d ? MODEL_3D : itemDisplayContext;
        this.previewItem = previewItem;
        this.localTransform = localTransform;
        this.transformInheritPolicy = transformInheritPolicy;
    }

    /// A freshly attached item renderer: the main hand in its third-person pose, previewing nothing.
    public static ItemInSlotRendererHolder createDefault() {
        return new ItemInSlotRendererHolder(
            EquipmentSlot.MAINHAND,
            ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
            false,
            Items.AIR,
            TransformHolder.identity(),
            TransformInheritPolicy.LOC_ROT_SCA
        );
    }

    public EquipmentSlot getEquipmentSlot() {
        return equipmentSlot;
    }

    public void setEquipmentSlot(EquipmentSlot equipmentSlot) {
        this.equipmentSlot = equipmentSlot;
    }

    public ItemDisplayContext getItemDisplayContext() {
        return itemDisplayContext;
    }

    public boolean isModel3d() {
        return model3d;
    }

    /// Takes a pick of the Display Context row: [#MODEL_3D] turns [#model3d] on and keeps the context, an
    /// [ItemDisplayContext] turns it off.
    public void setDisplayOption(Object displayOption) {
        this.displayOption = displayOption;
        model3d = displayOption == MODEL_3D;

        if (displayOption instanceof ItemDisplayContext context) itemDisplayContext = context;
    }

    /// Every [ItemDisplayContext], then [#MODEL_3D].
    private List<Object> displayOptions() {
        List<Object> options = new ArrayList<>(List.of(ItemDisplayContext.values()));

        options.add(MODEL_3D);

        return options;
    }

    private Component displayOptionLabel(Object displayOption) {
        return displayOption instanceof ItemDisplayContext context
            ? Component.literal(ParseUtils.toDisplayName(context.name()))
            : Component.translatable(LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_DISPLAY_CONTEXT_MODEL_3D);
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
    public void readFrom(ItemInSlotRendererBlueprint object) {
        equipmentSlot = object.equipmentSlot();
        itemDisplayContext = object.itemDisplayContext();
        setDisplayOption(object.model3d() ? MODEL_3D : object.itemDisplayContext());
        localTransform.readFrom(object.transform());
        transformInheritPolicy = object.transformInheritPolicy();
    }

    @Override
    public ItemInSlotRendererBlueprint makeObject() {
        return new ItemInSlotRendererBlueprint(
            equipmentSlot, itemDisplayContext, model3d, localTransform.makeObject(), transformInheritPolicy, isEnabled()
        );
    }

    @Override
    public Class<? extends PatchComponent> generateEventually() {
        return ItemInSlotRenderer.class;
    }

    // ***************************************************************
    // Source export (File > Export > As Source Code)
    // ***************************************************************

    @Override
    public String sourceTemplate() {
        return "components/item_in_slot.ftl";
    }

    /// Tokens for `components/item_in_slot.ftl`: the slot, the display context and the inherit policy as their enum
    /// constant names, the 3D model switch, and the transform's [TransformHolder#sourceTokens].
    @Override
    public Map<String, Object> getDTO() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("slot", equipmentSlot.name());
        dto.put("display_context", itemDisplayContext.name());
        dto.put("model_3d", model3d);
        dto.put("transform", localTransform.sourceTokens());
        dto.put("transform_inherit_policy", transformInheritPolicy.name());

        return dto;
    }
}
