<#--
  Constructor expression for an "akythera:ability_system" ComponentBlueprint (AbilitySystemComponentBlueprint).
  DTO (AbilitySystemComponentHolder#getDTO):
    { loadout: [ { slot, ability } ], enabled }
  The slot arrives as a Java expression; the ability stays a plain ResourceKey, matching
  AbilitySystemComponentBlueprint.CODEC. Spliced as a component map value at indent 5; continuation lines
  bake absolute indents.
--><#t>
<#if loadout?size == 0>new AbilitySystemComponentBlueprint(Map.of(), ${enabled?c})<#t>
<#else>new AbilitySystemComponentBlueprint(Map.ofEntries(
<#list loadout as l>
                        Map.entry(
                            ${l.slot},
                            ResourceKey.create(AkytheraRegistries.DataPack.ABILITY_KEY, Identifier.parse("${l.ability}"))
                        )<#sep>,</#sep>
</#list>
                    ), ${enabled?c})<#t>
</#if>
