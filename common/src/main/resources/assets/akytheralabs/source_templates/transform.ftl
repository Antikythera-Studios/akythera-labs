<#--
  Lowers the tokens TransformHolder#sourceTokens writes into a Transform constructor expression.

  @param t  the tokens: location_x/y/z, rotation_x/y/z/w (a quaternion), scale_x/y/z
--><#macro transform t><#t>
new Transform(<#t>
new Vec3(${t.location_x?c}, ${t.location_y?c}, ${t.location_z?c}), <#t>
new Quaterniond(${t.rotation_x?c}, ${t.rotation_y?c}, ${t.rotation_z?c}, ${t.rotation_w?c}), <#t>
new Vec3(${t.scale_x?c}, ${t.scale_y?c}, ${t.scale_z?c}))<#t>
</#macro>
