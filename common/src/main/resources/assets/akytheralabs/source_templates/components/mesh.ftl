<#--
  Constructor expression for an "akythera:mesh" ComponentBlueprint (MeshComponentBlueprint).
  DTO (MeshComponentHolder#getDTO):
    { has_mesh_getter, mesh_getter }
  The getter arrives already rendered as a PureNode constructor expression; an unauthored canvas
  emits null, which the blueprint's nullable getter takes. Emits a single inline Java expression (no
  surrounding whitespace).
--><#t>
new MeshComponentBlueprint(<#if has_mesh_getter>${mesh_getter}<#else>null</#if>)<#t>