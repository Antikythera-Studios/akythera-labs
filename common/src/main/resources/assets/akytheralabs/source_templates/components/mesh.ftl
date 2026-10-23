<#--
  Constructor expression for an "akythera:mesh" ComponentBlueprint (MeshComponentBlueprint).
  DTO (MeshComponentHolder#getDTO):
    { has_mesh, mesh, has_render_type_getter, render_type_getter, render_type_lambda, transform,
      transform_inherit_policy, enabled, hardcoded }
  The mesh arrives as a MeshManager lookup and the render type getter as a PureNode.of lambda for a
  hardcoded script, else as a PureNode constructor expression; an unpicked mesh or an unauthored canvas
  emits null, which the blueprint's nullable fields take. `transform` is rendered by transform.ftl and
  `transform_inherit_policy` arrives as its enum constant name. Emits a single inline Java expression (no
  surrounding whitespace).
--><#t>
<#import "../transform.ftl" as tr><#t>
new MeshComponentBlueprint(<#if has_mesh>${mesh}<#else>null</#if>, <#if !has_render_type_getter>null<#elseif hardcoded>${render_type_lambda}<#else>${render_type_getter}</#if>, <@tr.transform transform/>, TransformInheritPolicy.${transform_inherit_policy}, ${enabled?c})<#t>
