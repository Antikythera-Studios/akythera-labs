<#--
  Constructor expression for an "akythera:equipment_slot_binder" ComponentBlueprint (EquipmentSlotBinderBlueprint).
  DTO (EquipmentSlotBinderHolder#getDTO):
    { slot, display_context, model_3d, enabled }
  The slot and the context arrive as their enum constant names. Emits a single inline Java expression (no
  surrounding whitespace).
--><#t>
new EquipmentSlotBinderBlueprint(EquipmentSlot.${slot}, ItemDisplayContext.${display_context}, ${model_3d?c}, ${enabled?c})<#t>
