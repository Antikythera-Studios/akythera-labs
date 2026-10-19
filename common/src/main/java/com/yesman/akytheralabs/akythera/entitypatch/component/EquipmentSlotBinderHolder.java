package com.yesman.akytheralabs.akythera.entitypatch.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.yesman.akythera.api.component.PatchComponent;
import com.yesman.akythera.api.component.impl.EquipmentSlotBinder;
import com.yesman.akythera.api.component.impl.EquipmentSlotBinderBlueprint;
import com.yesman.akythera.client.api.component.impl.ItemInSlotRenderer;
import com.yesman.akythera.core.util.helper.ParseUtils;
import com.yesman.akytheralabs.akythera.entitypatch.EntityPatchBlueprintHolder;
import com.yesman.akytheralabs.editor.EditorEditable;
import com.yesman.akytheralabs.generated.LangKeys;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.Items;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/// Editor holder for an `akythera:equipment_slot_binder` ([EquipmentSlotBinderBlueprint]): one equipment slot bound
/// to the socket it is attached under, its item drawn rigid there ([ItemInSlotRenderer]) and its item patch's transform
/// components attached beside it.
///
/// Not a [RootAttachableComponent], so it is only reachable through the attach menu of a [TransformSocketHolder]. A
/// 3D model sits inside the armature.
public final class EquipmentSlotBinderHolder extends ComponentDataHolder<EquipmentSlotBinderBlueprint> {
    /// Editor persistence, in the shape [EquipmentSlotBinderBlueprint#CODEC] reads, plus the editor-only
    /// [#previewItem] that [#makeObject] never lowers.
    public static final MapCodec<EquipmentSlotBinderHolder> EDITOR_CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            EquipmentSlot.CODEC.fieldOf("slot")
                .forGetter(EquipmentSlotBinderHolder::getEquipmentSlot),
            ItemDisplayContext.CODEC.fieldOf("display_context")
                .forGetter(EquipmentSlotBinderHolder::getItemDisplayContext),
            Codec.BOOL.optionalFieldOf("model_3d", false)
                .forGetter(EquipmentSlotBinderHolder::isModel3d),
            BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("preview_item", Items.AIR)
                .forGetter(EquipmentSlotBinderHolder::getPreviewItem)
        ).apply(instance, EquipmentSlotBinderHolder::new)
    );

    /// What the Display Context row holds while [#model3d] is on.
    private static final Object MODEL_3D = new Object();

    /// The slot whose item is drawn. One binder per slot: the row offers only the slots no other binder of the
    /// patch binds.
    @EditorEditable(label = LangKeys.GUI_ENTITY_PATCH_SCREEN_ROW_EQUIPMENT_SLOT, order = 0, candidates = "slotOptions")
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

    public EquipmentSlotBinderHolder(EquipmentSlot equipmentSlot, ItemDisplayContext itemDisplayContext, boolean model3d, Item previewItem) {
        this.equipmentSlot = equipmentSlot;
        this.itemDisplayContext = itemDisplayContext;
        this.model3d = model3d;
        this.displayOption = model3d ? MODEL_3D : itemDisplayContext;
        this.previewItem = previewItem;
    }

    /// A freshly attached binder of `slot` in its third-person hand pose, previewing nothing.
    public static EquipmentSlotBinderHolder createDefault(EquipmentSlot slot) {
        return new EquipmentSlotBinderHolder(
            slot,
            slot == EquipmentSlot.OFFHAND ? ItemDisplayContext.THIRD_PERSON_LEFT_HAND : ItemDisplayContext.THIRD_PERSON_RIGHT_HAND,
            false,
            Items.AIR
        );
    }

    /// The first [EquipmentSlot] no binder in the tree under `root` binds, or null when every slot is bound.
    public static @Nullable EquipmentSlot firstFreeSlot(ComponentAttachableHolder root) {
        return freeSlots(root, null).stream().findFirst().orElse(null);
    }

    /// The [EquipmentSlot]s no binder in the tree under `root` binds, `except` left out of the count.
    private static List<EquipmentSlot> freeSlots(ComponentAttachableHolder root, @Nullable EquipmentSlotBinderHolder except) {
        Set<EquipmentSlot> bound = ComponentAttachableHolder.flatten(root)
            .filter(component -> component instanceof EquipmentSlotBinderHolder && component != except)
            .map(binder -> ((EquipmentSlotBinderHolder)binder).getEquipmentSlot())
            .collect(Collectors.toSet());

        return Arrays.stream(EquipmentSlot.values()).filter(slot -> !bound.contains(slot)).toList();
    }

    /// The Slot row's choices: this binder's own slot and every slot no other binder of `root` binds.
    private List<EquipmentSlot> slotOptions(EntityPatchBlueprintHolder root) {
        return freeSlots(root, this);
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
    public void readFrom(EquipmentSlotBinderBlueprint object) {
        equipmentSlot = object.equipmentSlot();
        itemDisplayContext = object.itemDisplayContext();
        setDisplayOption(object.model3d() ? MODEL_3D : object.itemDisplayContext());
    }

    @Override
    public EquipmentSlotBinderBlueprint makeObject() {
        return new EquipmentSlotBinderBlueprint(equipmentSlot, itemDisplayContext, model3d, isEnabled());
    }

    @Override
    public Class<? extends PatchComponent> generateEventually() {
        return EquipmentSlotBinder.class;
    }

    // ***************************************************************
    // Source export (File > Export > As Source Code)
    // ***************************************************************

    @Override
    public String sourceTemplate() {
        return "components/equipment_slot_binder.ftl";
    }

    /// Tokens for `components/equipment_slot_binder.ftl`: the slot and the display context as their enum constant
    /// names, and the 3D model switch.
    @Override
    public Map<String, Object> getDTO() {
        Map<String, Object> dto = new HashMap<>();
        dto.put("slot", equipmentSlot.name());
        dto.put("display_context", itemDisplayContext.name());
        dto.put("model_3d", model3d);

        return dto;
    }
}
