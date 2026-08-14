<#--
  Lowers a list of { name, source } component rows into a Java Map expression — the shape a
  ComponentAttachable keys its components by. `Map.of` tops out at ten key/value pairs, so a host
  with more components than that falls back to `Map.ofEntries`.

  @param rows    the component rows, each { name, source }
  @param indent  spaces to indent each entry line by; the closing paren lands four columns left
--><#macro componentMap rows indent><#t>
<#if rows?size == 0>Map.of()<#t>
<#elseif (rows?size <= 10)>Map.of(
<#list rows as row>
${indent}"${row.name}", ${row.source}<#sep>,</#sep>
</#list>
${indent?substring(4)})<#t>
<#else>Map.ofEntries(
<#list rows as row>
${indent}Map.entry("${row.name}", ${row.source})<#sep>,</#sep>
</#list>
${indent?substring(4)})<#t>
</#if>
</#macro>
