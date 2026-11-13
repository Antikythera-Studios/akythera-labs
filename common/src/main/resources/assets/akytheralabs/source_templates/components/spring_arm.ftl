<#--
  Constructor expression for an "akythera:spring_arm" ComponentBlueprint (SpringArmComponentBlueprint).
  DTO (SpringArmComponentHolder#getDTO):
    { transform, transform_inherit_policy, length, components, enabled }
  `transform` is rendered by transform.ftl and `transform_inherit_policy` arrives as its enum constant name. The
  attached components ride along as the arm's child blueprint map, each rendered by its own components/*.ftl.
  Emits a single inline Java expression (no surrounding whitespace).
--><#t>
<#import "../component_map.ftl" as tpl><#t>
<#import "../transform.ftl" as tr><#t>
new SpringArmComponentBlueprint(<@tr.transform transform/>, TransformInheritPolicy.${transform_inherit_policy}, ${length?c}D<#t>
, <@tpl.componentMap components "    "/>, ${enabled?c})<#t>
