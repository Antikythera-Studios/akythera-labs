<#--
  Constructor expression for an "akythera:skinned_armor" ComponentBlueprint (SkinnedArmorRendererBlueprint).
  DTO (SkinnedArmorRendererHolder#getDTO):
    { slot, transform, transform_inherit_policy, enabled }
  The slot and the inherit policy arrive as their enum constant names; `transform` is rendered by
  transform.ftl. Emits a single inline Java expression (no surrounding whitespace).
--><#t>
<#import "../transform.ftl" as tr><#t>
new SkinnedArmorRendererBlueprint(EquipmentSlot.${slot}, <@tr.transform transform/>, TransformInheritPolicy.${transform_inherit_policy}, ${enabled?c})<#t>
