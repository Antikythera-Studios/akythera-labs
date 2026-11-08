<#--
  Constructor expression for an "akythera:brain" ComponentBlueprint (BrainComponentBlueprint).
  DTO: { behavior_tree, hostile_types, friendly_types, enabled } — behavior_tree is the tree's id or an empty string,
  each *_types a list of entity type tag ids.
  Emits a single inline Java expression (no surrounding whitespace).
--><#t>
<#macro tags ids><#t>
List.of(<#list ids as id>TagKey.create(Registries.ENTITY_TYPE, Identifier.parse("${id}"))<#sep>, </#sep></#list>)<#t>
</#macro><#t>
new BrainComponentBlueprint(<#if behavior_tree?has_content>Optional.of(ResourceKey.create(AkytheraRegistries.DataPack.BEHAVIOR_TREE_KEY, Identifier.parse("${behavior_tree}")))<#else>Optional.empty()</#if>, <@tags hostile_types/>, <@tags friendly_types/>, ${enabled?c})<#t>
