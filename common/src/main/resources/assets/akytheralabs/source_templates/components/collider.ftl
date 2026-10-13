<#--
  Constructor expression for an "akythera:collider" ComponentBlueprint (Collider).
  DTO (ColliderComponentHolder#getDTO):
    { collider_type, transform, transform_inherit_policy, arguments, enabled }
  `collider_type` is the shape's Collider subclass and `arguments` its own dimensions, both rendered
  by the shape holder; `transform` is the pivot, rendered by transform.ftl, and
  `transform_inherit_policy` arrives as its enum constant name. Emits a single inline Java
  expression (no surrounding whitespace).
--><#t>
<#import "../transform.ftl" as tr><#t>
new ${collider_type}(<@tr.transform transform/>, TransformInheritPolicy.${transform_inherit_policy}, ${arguments}, ${enabled?c})<#t>
