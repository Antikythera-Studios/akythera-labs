<#--
  Lowers a list of { name, source } component rows into a Java Map expression — the shape a
  ComponentAttachable keys its components by. Guava's ImmutableMap keeps insertion order, which a
  host resolving the *first* matching component reads as priority order.

  @param rows       the component rows, each { name, source }
  @param indent     spaces to indent each entry line by; the closing call lands four columns left
  @param valueType  the map's value type, for the builder's explicit type arguments
--><#macro componentMap rows indent valueType="ComponentBlueprint<?, ?>"><#t>
<#if rows?size == 0>ImmutableMap.<String, ${valueType}>of()<#t>
<#else>ImmutableMap.<String, ${valueType}>builder()
<#list rows as row>
${indent}.put("${row.name}", ${row.source})
</#list>
${indent?substring(4)}.build()<#t>
</#if>
</#macro>
