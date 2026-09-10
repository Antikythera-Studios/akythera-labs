<#--
  Constructor expression for an "akythera:collider" ComponentBlueprint (Collider).
  DTO (ColliderComponentHolder#getDTO):
    { collider_type, location_x/y/z, rotation_x/y/z/w, scale_x/y/z, arguments }
  `collider_type` is the shape's Collider subclass and `arguments` its own dimensions, both rendered
  by the shape holder. Emits a single inline Java expression (no surrounding whitespace).
--><#t>
new ${collider_type}(new Transform(<#t>
new Vec3(${location_x?c}, ${location_y?c}, ${location_z?c}), <#t>
new Quaterniond(${rotation_x?c}, ${rotation_y?c}, ${rotation_z?c}, ${rotation_w?c}), <#t>
new Vec3(${scale_x?c}, ${scale_y?c}, ${scale_z?c})), ${arguments})<#t>
