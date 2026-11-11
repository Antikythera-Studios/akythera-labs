<#--
  Constructor expression for an "akythera:armature" ComponentBlueprint (ArmatureComponentBlueprint).
  DTO (ArmatureComponentHolder#getDTO):
    { armature_namespace, armature_path, script, transform, transform_inherit_policy, components, enabled }
  The armature is rebuilt from its trimmed id via ArmatureManager.accessor (matching the holder's
  resolveArmature). `script` arrives as the ByClass / ByDataPack constructor expression, naming the
  compiled class of a project animator script. `transform` is rendered by transform.ftl and
  `transform_inherit_policy` arrives as its enum constant name. The attached components ride along as the
  blueprint's child map, each rendered by its own components/*.ftl. Emits a single inline Java expression
  (no surrounding whitespace).
--><#t>
<#import "../component_map.ftl" as tpl><#t>
<#import "../transform.ftl" as tr><#t>
new ArmatureComponentBlueprint(<#t>
ArmatureManager.accessor("${armature_namespace}", "${armature_path}", Armature::new)<#t>
, ${script}, <@tr.transform transform/>, TransformInheritPolicy.${transform_inherit_policy}<#t>
, <@tpl.componentMap components "    "/>, ${enabled?c})<#t>
