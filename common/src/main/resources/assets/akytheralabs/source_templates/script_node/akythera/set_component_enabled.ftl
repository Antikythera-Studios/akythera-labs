<#--
  akythera:set_component_enabled (component, enabled) -> void
  Renders an if statement; the flow compiler appends the closing semicolon.
--><#t>
if (${params[0]} != null) ${params[0]}.setEnabled(Boolean.TRUE.equals(${params[1]}))<#t>