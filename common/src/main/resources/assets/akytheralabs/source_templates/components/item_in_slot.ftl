<#--
  Constructor expression for an "akythera:item_in_slot" ComponentBlueprint (ItemInSlotRendererBlueprint).
  DTO (ItemInSlotRendererHolder#getDTO):
    { slot, display_context, model_3d, transform, transform_inherit_policy, enabled }
  The slot, the context and the inherit policy arrive as their enum constant names; `transform` is rendered by
  transform.ftl. Emits a single inline Java expression (no surrounding whitespace).
--><#t>
<#import "../transform.ftl" as tr><#t>
new ItemInSlotRendererBlueprint(EquipmentSlot.${slot}, ItemDisplayContext.${display_context}, ${model_3d?c}, <@tr.transform transform/>, TransformInheritPolicy.${transform_inherit_policy}, ${enabled?c})<#t>
