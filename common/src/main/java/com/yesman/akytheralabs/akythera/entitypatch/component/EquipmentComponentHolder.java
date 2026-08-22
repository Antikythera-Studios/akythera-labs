package com.yesman.akytheralabs.akythera.entitypatch.component;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.client.api.component.impl.EquipmentComponent;
import com.yesman.akythera.core.asset.armature.BoneSocket;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;

/// Editor holder for an `akythera:equipment` ([EquipmentComponent]) — the item worn or held in one
/// equipment slot, drawn wherever its host places it.
///
/// Not a [RootAttachableComponent] — the component carries no placement of its own, so it is only
/// reachable through the attach menu of a [BoneSocketHolder].
public final class EquipmentComponentHolder extends ComponentDataHolder<EquipmentComponent> {
    /// Editor persistence, in the shape [EquipmentComponent#CODEC] reads, plus the editor-only
    /// [#previewItem] that [#makeObject] never lowers.
    public static final MapCodec<EquipmentComponentHolder> EDITOR_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            EquipmentSlot.CODEC.fieldOf("slot")
                .forGetter(EquipmentComponentHolder::getEquipmentSlot),
            ItemDisplayContext.CODEC.fieldOf("display_context")
                .forGetter(EquipmentComponentHolder::getItemDisplayContext),
            BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("preview_item", Items.AIR)
                .forGetter(EquipmentComponentHolder::getPreviewItem)
        ).apply(instance, EquipmentComponentHolder::new)
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

    public EquipmentComponentHolder(
        EquipmentSlot equipmentSlot,
        ItemDisplayContext itemDisplayContext,
        Item previewItem
    ) {
        this.equipmentSlot = equipmentSlot;
        this.itemDisplayContext = itemDisplayContext;
        this.previewItem = previewItem;
    }

    /// A freshly attached equipment component: the main hand in its third-person pose, previewing
    /// nothing.
    public static EquipmentComponentHolder createDefault() {
        return new EquipmentComponentHolder(
            EquipmentSlot.MAINHAND,
            ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
            Items.AIR
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
    public void readFrom(EquipmentComponent object) {
        equipmentSlot = object.equipmentSlot();
        itemDisplayContext = object.itemDisplayContext();
    }

    @Override
    public EquipmentComponent makeObject() {
        return new EquipmentComponent(equipmentSlot, itemDisplayContext);
    }

    /// An [EquipmentComponent] is its own blueprint — it builds itself.
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

    /// Tokens for `components/equipment.ftl`: the slot and display context as their enum constant
    /// names.
    @Override
    public Map<String, Object> getDTO() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("slot", equipmentSlot.name());
        dto.put("display_context", itemDisplayContext.name());

        return dto;
    }
}
