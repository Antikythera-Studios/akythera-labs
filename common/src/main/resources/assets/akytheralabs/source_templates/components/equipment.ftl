<#--
  Constructor expression for an "akythera:equipment" ComponentBlueprint (EquipmentComponentBlueprint).
  DTO (EquipmentComponentHolder#getDTO):
    { slot, display_context, transform, transform_inherit_policy, enabled }
  The slot, the context and the inherit policy arrive as their enum constant names; `transform` is rendered by
  transform.ftl. Emits a single inline Java expression (no surrounding whitespace).
--><#t>
<#import "../transform.ftl" as tr><#t>
new EquipmentComponentBlueprint(EquipmentSlot.${slot}, ItemDisplayContext.${display_context}, <@tr.transform transform/>, TransformInheritPolicy.${transform_inherit_policy}, ${enabled?c})<#t>
