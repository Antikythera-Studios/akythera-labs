<#--
  Constructor expression for an "akythera:armature" ComponentBlueprint (ArmatureComponentBlueprint).
  DTO (ArmatureComponentHolder#getDTO):
    { armature_namespace, armature_path, script_kind ("class"|"datapack"), script_id, use_root_motion,
      transform, transform_inherit_policy, components, enabled }
  The armature is rebuilt from its trimmed id via ArmatureManager.accessor (matching the holder's
  resolveArmature). The script id lowers to ByClass (a frozen SCRIPT_TYPE lookup) or ByDataPack (an
  ANIMATOR_SCRIPT_KEY resource key). `transform` is rendered by transform.ftl and
  `transform_inherit_policy` arrives as its enum constant name. The attached components ride along as the
  blueprint's child map, each rendered by its own components/*.ftl. Emits a single inline Java expression
  (no surrounding whitespace).
--><#t>
<#import "../component_map.ftl" as tpl><#t>
<#import "../transform.ftl" as tr><#t>
new ArmatureComponentBlueprint(<#t>
ArmatureManager.accessor("${armature_namespace}", "${armature_path}", Armature::new)<#t>
<#if script_kind == "class">, new ArmatureComponentBlueprint.ByClass(AkytheraRegistries.Frozen.SCRIPT_TYPE.getValue(Identifier.parse("${script_id}")))<#t>
<#else>, new ArmatureComponentBlueprint.ByDataPack(ResourceKey.create(AkytheraRegistries.DataPack.ANIMATOR_SCRIPT_KEY, Identifier.parse("${script_id}")))<#t>
</#if>, ${use_root_motion?c}, <@tr.transform transform/>, TransformInheritPolicy.${transform_inherit_policy}<#t>
, <@tpl.componentMap components "    "/>, ${enabled?c})<#t>
