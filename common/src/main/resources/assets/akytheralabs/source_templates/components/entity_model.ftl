<#--
  Constructor expression for an "akythera:entity_model" ComponentBlueprint (EntityModelComponent).
  DTO (EntityModelComponentHolder#getDTO):
    { has_mesh_getter, mesh_getter, renderer }
  The getter arrives already rendered as a PureNode constructor expression and is wrapped in an
  unbound MeshGetter — the component binds its owner in instantiate. An unauthored canvas emits a
  null tree. Emits a single inline Java expression (no surrounding whitespace).
--><#t>
new EntityModelComponent(<#t>
new MeshGetter(<#if has_mesh_getter>${mesh_getter}<#else>null</#if>, null)<#t>
, EntityModelComponent.Renderer.${renderer})<#t>
