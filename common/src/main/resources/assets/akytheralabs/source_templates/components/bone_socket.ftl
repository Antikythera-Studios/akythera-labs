<#--
  Constructor expression for an "akythera:bone_socket" ComponentBlueprint (BoneSocket).
  DTO (BoneSocketHolder#getDTO):
    { bone_name, location_x/y/z, rotation_x/y/z/w, scale_x/y/z, components }
  The rotation arrives as quaternion components (the holder converts from whatever the editor
  authored), so this stays a plain constructor expression. The attached components ride along as the
  socket's child blueprint map, each rendered by its own components/*.ftl; a socket with none takes
  the two-argument constructor. Emits a single inline Java expression (no surrounding whitespace).
--><#t>
<#import "../component_map.ftl" as tpl><#t>
new BoneSocket(new Transform(<#t>
new Vec3(${location_x?c}, ${location_y?c}, ${location_z?c}), <#t>
new Quaterniond(${rotation_x?c}, ${rotation_y?c}, ${rotation_z?c}, ${rotation_w?c}), <#t>
new Vec3(${scale_x?c}, ${scale_y?c}, ${scale_z?c})), "${bone_name}"<#t>
<#if (components?size > 0)>, <@tpl.componentMap components "    "/></#if>)<#t>
