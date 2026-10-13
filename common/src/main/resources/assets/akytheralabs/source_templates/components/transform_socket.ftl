<#--
  Constructor expression for an "akythera:transform_socket" ComponentBlueprint (TransformSocket).
  DTO (TransformSocketHolder#getDTO):
    { bone_name, transform, transform_inherit_policy, components, enabled }
  `transform` is rendered by transform.ftl and `transform_inherit_policy` arrives as its enum constant
  name. The attached components ride along as the socket's child blueprint map, each rendered by its own
  components/*.ftl. Emits a single inline Java expression (no surrounding whitespace).
--><#t>
<#import "../component_map.ftl" as tpl><#t>
<#import "../transform.ftl" as tr><#t>
new TransformSocket(<@tr.transform transform/>, TransformInheritPolicy.${transform_inherit_policy}, "${bone_name}"<#t>
, <@tpl.componentMap components "    "/>, ${enabled?c})<#t>
