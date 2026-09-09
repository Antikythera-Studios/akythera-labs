<#--
  Constructor expression for an "akythera:entity_model" ComponentBlueprint (EntityModelComponent).
  DTO (EntityModelComponentHolder#getDTO):
    { has_mesh_getter, mesh_getter, has_default_mesh, default_mesh, renderer }
  The getter arrives already rendered as a PureNode constructor expression and the default mesh as a
  MeshManager lookup; an unauthored canvas or an unset default emits null. The trailing null is the
  unbound owner — the component binds it in instantiate. Emits a single inline Java expression (no
  surrounding whitespace).
--><#t>
new EntityModelComponent(<#t>
<#if has_mesh_getter>${mesh_getter}<#else>null</#if><#t>
, <#if has_default_mesh>${default_mesh}<#else>null</#if><#t>
, EntityModelComponent.Renderer.${renderer}<#t>
, null)<#t>
