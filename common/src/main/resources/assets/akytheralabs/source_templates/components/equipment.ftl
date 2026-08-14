<#--
  Constructor expression for an "akythera:equipment" ComponentBlueprint (EquipmentComponent).
  DTO (EquipmentComponentHolder#getDTO):
    { slot, display_context }
  Both arrive as their enum constant names. Emits a single inline Java expression (no surrounding
  whitespace).
--><#t>
new EquipmentComponent(EquipmentSlot.${slot}, ItemDisplayContext.${display_context})<#t>
