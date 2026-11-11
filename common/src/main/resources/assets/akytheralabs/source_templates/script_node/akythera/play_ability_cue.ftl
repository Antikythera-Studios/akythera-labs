<#-- akythera:play_ability_cue (component, cueId, source, withState, endsWithAbility) -> AbilityCue --><#t>
<#assign cueClass = compiledClass("akythera:abilities/cue", params[1])><#t>
<#if cueClass?has_content>${params[0]}.playCue(${cueClass}.class, ${params[2]}, Boolean.TRUE.equals(${params[3]}), Boolean.TRUE.equals(${params[4]}))<#t>
<#else>${params[0]}.playCue(ResourceKey.create(AkytheraRegistries.DataPack.ABILITY_CUE_KEY, Identifier.parse(${params[1]})), ${params[2]}, Boolean.TRUE.equals(${params[3]}), Boolean.TRUE.equals(${params[4]}))<#t>
</#if><#t>